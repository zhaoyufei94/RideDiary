package com.ridediary.weather

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.*
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.resume

class WeatherViewModel(application: Application): AndroidViewModel(application) {
    private val settings=WeatherSettings(application)
    private val client=WeatherClient(application)
    val workCalendar=com.ridediary.calendar.WorkCalendarClient(application)
    val config=MutableStateFlow(WeatherConfig(enabled=false))
    val loaded=MutableStateFlow(false)
    val location=MutableStateFlow<WeatherPoint?>(null)
    val locating=MutableStateFlow(false)
    val locationFailed=MutableStateFlow(false)
    init { viewModelScope.launch { config.value=withContext(Dispatchers.IO) { settings.load() }; loaded.value=true } }
    suspend fun save(value: WeatherConfig): Boolean = viewModelScope.async { try {
        withContext(Dispatchers.IO) { settings.save(value); client.clear() }
        config.value=value.copy(host=WeatherConfig.normalizeHost(value.host),apiKey=value.apiKey.trim()); true
    } catch(e: CancellationException) { throw e } catch(_: Exception) { false } }.await()
    suspend fun fetch(point: WeatherPoint,language: String,force: Boolean=false,freshAfter: Long=0)=client.fetch(config.value,point,language,force,freshAfter)
    /** Called only from the visible page. No background location or weather work. */
    suspend fun locate() {
        if(locating.value) return
        locating.value=true; locationFailed.value=false; location.value=null
        try {
            val found=withTimeoutOrNull(10_000) { currentLocation(getApplication()) }
            location.value=found?.let { WeatherPoint.of(it.latitude,it.longitude) }
            locationFailed.value=found==null
        } catch(e: CancellationException) { throw e }
        catch(_: Exception) { locationFailed.value=true }
        finally { locating.value=false }
    }
}
@Suppress("DEPRECATION")
private suspend fun currentLocation(context: Context): Location? {
    val fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    val coarse=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED
    if(!fine && !coarse) return null
    val manager=context.getSystemService(LocationManager::class.java)
    val providers=listOf(LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER).filter {
        (fine || it!=LocationManager.GPS_PROVIDER) && runCatching { manager.isProviderEnabled(it) }.getOrDefault(false)
    }
    providers.mapNotNull { manager.getLastKnownLocation(it) }.filter {
        val age=android.os.SystemClock.elapsedRealtimeNanos()-it.elapsedRealtimeNanos
        age in 0..900_000_000_000L
    }.minByOrNull { it.accuracy }?.let { return it }
    if(providers.isEmpty()) return null
    return suspendCancellableCoroutine { continuation ->
        val listener=object: LocationListener {
            override fun onLocationChanged(location: Location) {
                manager.removeUpdates(this)
                if(continuation.isActive) continuation.resume(location)
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            override fun onStatusChanged(provider: String?,status: Int,extras: Bundle?) {}
        }
        continuation.invokeOnCancellation { manager.removeUpdates(listener) }
        try { providers.forEach { manager.requestSingleUpdate(it,listener,Looper.getMainLooper()) } }
        catch(_: SecurityException) { manager.removeUpdates(listener); if(continuation.isActive) continuation.resume(null) }
    }
}
