package com.ridediary.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import android.os.*
import androidx.core.content.ContextCompat
import com.ridediary.core.Fix
import com.ridediary.core.FixSelector

interface LocationSource { fun start(startElapsed: Long, onFix: (Fix)->Unit, onState: (String)->Unit); fun stop() }

/** System GNSS/network only. No last-known location, map key, GMS, or coordinate logging. */
class AndroidLocationSource(private val context: Context): LocationSource, LocationListener {
    private val manager=context.getSystemService(LocationManager::class.java)
    private var started=0L
    private var emit: (Fix)->Unit = {}
    private var state: (String)->Unit = {}
    private val selector=FixSelector()
    override fun start(startElapsed: Long, onFix: (Fix)->Unit, onState: (String)->Unit) {
        started=startElapsed; emit=onFix; state=onState
        if (ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { state("PRECISE_REQUIRED"); return }
        try {
            val providers=listOf(LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER).filter { it in manager.allProviders }
            // Register disabled providers too, so enabling location mid-ride can recover.
            providers.forEach { manager.requestLocationUpdates(it,2_000L,0f,this,Looper.getMainLooper()) }
            state(if(providers.none { manager.isProviderEnabled(it) }) "LOCATION_OFF" else "SEARCHING")
        } catch (_: SecurityException) { state("PERMISSION_LOST") }
    }
    override fun onLocationChanged(location: Location) {
        val now=SystemClock.elapsedRealtime()
        val pointTime=location.elapsedRealtimeNanos/1_000_000
        val age=now-pointTime
        val quality=if (age !in 0..10_000 || pointTime < started) "STALE" else if (location.accuracy > 35 || !location.hasAccuracy()) "POOR" else "OK"
        val fix=Fix((pointTime-started).coerceAtLeast(0),location.latitude,location.longitude,
            if (location.hasAccuracy()) location.accuracy.toDouble() else 999.0,
            if (location.hasSpeed()) location.speed.toDouble() else null,location.time,quality,
            if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond.toDouble() else null)
        if(selector.accept(location.provider==LocationManager.GPS_PROVIDER,now,fix)) {
            state(if (quality == "OK") "GOOD" else quality)
            emit(fix)
        }
    }
    override fun onProviderDisabled(provider: String) { state("LOCATION_OFF") }
    override fun onProviderEnabled(provider: String) { state("SEARCHING") }
    @Deprecated("Legacy callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun stop() { manager.removeUpdates(this) }
}
