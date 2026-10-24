package com.ridediary.recording

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ridediary.*
import com.ridediary.data.RouteUnavailableException
import com.ridediary.core.RecordingBuffer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow

class RecordingService: Service() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(localized(newBase)) }
    private val app get() = application as RideDiaryApp
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var source: LocationSource? = null
    private var rideId: String? = null
    private var boundRouteId: String? = null
    private var starting=false
    private var arrivalReview=false
    private var origin=0L
    private var ticker: Job? = null
    private var worker: Job? = null
    private var buffer=RecordingBuffer()
    private val wakeups=Channel<Unit>(Channel.CONFLATED)
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,getString(R.string.recording_channel),NotificationManager.IMPORTANCE_LOW))
    }
    private fun finalizeRecording(discard: Boolean) {
        buffer.stop(elapsed(),System.currentTimeMillis(),discard)
        stopPending.value=true; saving.value=true
        source?.stop(); ticker?.cancel()
        wakeups.trySend(Unit)
    }
    private suspend fun flush() {
        val batch=buffer.batch()
        val id=rideId ?: return
        if(batch.cutoff?.discard==true) app.repository.discardActive(id)
        else {
            val ride=app.repository.update(id,batch.elapsed,batch.utc,finish=batch.cutoff!=null,
                fixedRouteId=boundRouteId,additionalFixes=batch.fixes)
            if(ride?.arrivalSuggested==true && !arrivalReview) {
                arrivalReview=true
                getSystemService(NotificationManager::class.java).notify(1,notification())
            }
        }
        buffer.acknowledge(batch)
        if(batch.cutoff!=null) close()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || intent?.action == DISCARD) {
            if(starting || rideId!=null) finalizeRecording(intent.action==DISCARD)
            else scope.launch { app.ready.await(); close() }
            return START_NOT_STICKY
        }
        if (starting || rideId != null) return START_NOT_STICKY
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            quality.value="PRECISE_REQUIRED"; stopSelf(); return START_NOT_STICKY
        }
        boundRouteId=intent?.getStringExtra(EXTRA_ROUTE_ID)
        starting=true
        origin=SystemClock.elapsedRealtime()
        val startUtc=System.currentTimeMillis()
        buffer=RecordingBuffer().also { it.checkpoint(0,startUtc) }
        quality.value="SEARCHING"; latestFixRealtime.value=null; latestFix.value=null; stopPending.value=false; saving.value=false
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(1,notification())
        } catch (_: SecurityException) { quality.value="PERMISSION_LOST"; stopSelf(); return START_NOT_STICKY }
        scope.launch {
            try {
                app.ready.await()
                try { rideId=app.repository.start(startUtc,boundRouteId) }
                catch (_: RouteUnavailableException) { quality.value="ROUTE_UNAVAILABLE"; close(); return@launch }
                active.value=true
                worker=scope.launch {
                    for(signal in wakeups) {
                        try { flush() }
                        catch(e: CancellationException) { throw e }
                        catch(_: Exception) { quality.value="STORAGE_ERROR"; saving.value=false }
                    }
                }
                if(buffer.cutoff==null) {
                    source=AndroidLocationSource(this@RecordingService).also { location ->
                        location.start(origin,{ fix ->
                            if(buffer.cutoff==null) {
                                latestFixRealtime.value=origin+fix.t
                                latestFix.value=fix
                                buffer.checkpoint(elapsed(),System.currentTimeMillis(),fix)
                                wakeups.trySend(Unit)
                            }
                        },{ quality.value=it })
                    }
                    ticker=scope.launch {
                        while(isActive) {
                            delay(2_000)
                            if(ContextCompat.checkSelfPermission(this@RecordingService,Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
                                quality.value="PERMISSION_LOST"; source?.stop()
                            }
                            buffer.checkpoint(elapsed(),System.currentTimeMillis())
                            wakeups.trySend(Unit)
                        }
                    }
                } else wakeups.trySend(Unit)
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { quality.value="STORAGE_ERROR"; close() }
        }
        return START_NOT_STICKY
    }
    private fun elapsed()=SystemClock.elapsedRealtime()-origin
    private fun close() {
        source?.stop(); ticker?.cancel(); rideId=null; active.value=false; latestFix.value=null; saving.value=false; stopPending.value=false
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    private fun notification(): Notification {
        val reviewIntent=Intent(this,MainActivity::class.java).setAction(REVIEW_STOP).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val stop=PendingIntent.getActivity(this,2,reviewIntent,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val open=if(arrivalReview) stop else PendingIntent.getActivity(this,1,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_ridediary_notification).setContentTitle(getString(if(arrivalReview) R.string.arrival_review_title else R.string.recording_title))
            .setContentText(getString(if(arrivalReview) R.string.arrival_review_body else R.string.recording_body)).setOngoing(true).setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,getString(R.string.finish),stop).build()
    }
    override fun onDestroy() {
        source?.stop(); active.value=false; latestFix.value=null; saving.value=false; stopPending.value=false; wakeups.close(); scope.cancel(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val EXTRA_ROUTE_ID="com.ridediary.ROUTE_ID"
        const val CHANNEL="ride_recording"
        const val STOP="com.ridediary.STOP"
        const val DISCARD="com.ridediary.DISCARD"
        const val REVIEW_STOP="com.ridediary.REVIEW_STOP"
        val quality=MutableStateFlow("SEARCHING")
        val active=MutableStateFlow(false)
        val saving=MutableStateFlow(false)
        val stopPending=MutableStateFlow(false)
        val latestFixRealtime=MutableStateFlow<Long?>(null)
        val latestFix=MutableStateFlow<com.ridediary.core.Fix?>(null)
    }
}
