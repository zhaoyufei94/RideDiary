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
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow

class RecordingService: Service() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(localized(newBase)) }
    private val app get() = application as RideDiaryApp
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var source: LocationSource? = null
    private var rideId: String? = null
    private var starting=false
    private var arrivalReview=false
    private var origin=0L
    private var ticker: Job? = null
    private var worker: Job? = null
    private val operations=Channel<suspend ()->Unit>(Channel.UNLIMITED)
    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,getString(R.string.recording_channel),NotificationManager.IMPORTANCE_LOW))
        worker=scope.launch {
            for (operation in operations) {
                try { operation() }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) {
                    quality.value="STORAGE_ERROR"
                    // Keep the service/timer and stop action alive; a later successful checkpoint
                    // can recover after temporary storage failure. Raw points stay unmodified.
                }
            }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || intent?.action == DISCARD) {
            operations.trySend {
                app.ready.await()
                if (rideId == null) {
                    // A recreated stop-only service must finalize the saved session without
                    // pretending it continued collecting locations.
                    app.repository.recoverInterrupted(System.currentTimeMillis())
                    close()
                } else if(intent.action==DISCARD) {
                    app.repository.discardActive(rideId!!)
                    close()
                } else finish()
            }
            return START_NOT_STICKY
        }
        if (starting || rideId != null) return START_NOT_STICKY
        if (ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            quality.value="PRECISE_REQUIRED"; stopSelf(); return START_NOT_STICKY
        }
        starting=true
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(1,notification(),ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(1,notification())
        } catch (_: SecurityException) { quality.value="PERMISSION_LOST"; stopSelf(); return START_NOT_STICKY }
        operations.trySend {
            app.ready.await()
            origin=SystemClock.elapsedRealtime()
            rideId=app.repository.start(System.currentTimeMillis())
            active.value=true
            source=AndroidLocationSource(this@RecordingService).also { location ->
                location.start(origin,{ fix ->
                    operations.trySend {
                        val id=rideId ?: return@trySend
                        val ride=app.repository.update(id,elapsed(),System.currentTimeMillis(),fix)
                        if(ride?.arrivalSuggested==true && !arrivalReview) {
                            arrivalReview=true
                            getSystemService(NotificationManager::class.java).notify(1,notification())
                        }
                        if (ride?.state == "COMPLETED") close()
                    }
                },{ quality.value=it })
            }
            ticker=scope.launch {
                while (isActive) {
                    delay(2_000)
                    operations.send {
                        val id=rideId
                        if (id != null) {
                            if (ContextCompat.checkSelfPermission(this@RecordingService,Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                quality.value="PERMISSION_LOST"; source?.stop()
                            }
                            app.repository.update(id,elapsed(),System.currentTimeMillis())
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }
    private fun elapsed()=SystemClock.elapsedRealtime()-origin
    private suspend fun finish() {
        rideId?.let { app.repository.update(it,elapsed(),System.currentTimeMillis(),finish=true) }
        close()
    }
    private fun close() {
        source?.stop(); ticker?.cancel(); rideId=null; active.value=false
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
        source?.stop(); active.value=false; operations.close(); scope.cancel(); super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
    companion object {
        const val CHANNEL="ride_recording"
        const val STOP="com.ridediary.STOP"
        const val DISCARD="com.ridediary.DISCARD"
        const val REVIEW_STOP="com.ridediary.REVIEW_STOP"
        val quality=MutableStateFlow("SEARCHING")
        val active=MutableStateFlow(false)
    }
}
