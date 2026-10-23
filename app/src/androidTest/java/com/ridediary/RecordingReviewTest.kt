package com.ridediary

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ridediary.data.*
import com.ridediary.recording.RecordingService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import android.os.SystemClock
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Tests only notification/finish operations; no display-awake assertions. */
@RunWith(AndroidJUnit4::class)
class RecordingReviewTest {
    @Test fun notificationRequiresReviewAndServiceHandlesSaveAndDiscard()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<RideDiaryApp>()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val trace=java.io.File(app.cacheDir,"recording-review-test.txt")
        trace.writeText("START\n")
        fun stage(value: String) { trace.appendText(value+"\n") }
        app.ready.await()
        check(!RecordingService.active.value) { "Finish the existing recording before testing" }
        val original=app.repository; val before=original.rides.first()
        val db=Room.inMemoryDatabaseBuilder(app,RideDatabase::class.java).build()
        val repo=RideRepository(db)
        var activity: MainActivity?=null
        suspend fun await(condition: suspend ()->Boolean) {
            val deadline=SystemClock.elapsedRealtime()+12_000
            while(!condition()) {
                check(SystemClock.elapsedRealtime()<deadline) { "Recording review condition timed out" }
                delay(100)
            }
        }
        try {
            for(permission in listOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)+
                if(Build.VERSION.SDK_INT>=33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()) {
                if(ContextCompat.checkSelfPermission(app,permission)!=PackageManager.PERMISSION_GRANTED)
                    instrumentation.uiAutomation.grantRuntimePermission(app.packageName,permission)
            }
            stage("permissions granted")
            app.repository=repo
            activity=instrumentation.startActivitySync(Intent(app,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            stage("activity launched")
            val manager=app.getSystemService(NotificationManager::class.java)
            fun start() { instrumentation.runOnMainSync { ContextCompat.startForegroundService(activity!!,Intent(activity,RecordingService::class.java)) } }
            fun review() {
                manager.activeNotifications.single { it.id==1 }.notification.actions.single().actionIntent.send()
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync { assertEquals(RecordingService.REVIEW_STOP,activity!!.intent.action) }
                stage("notification delivered to review Activity")
            }
            fun command(action: String) { instrumentation.runOnMainSync {
                activity!!.startService(Intent(activity,RecordingService::class.java).setAction(action))
            } }
            start()
            await { RecordingService.active.value && (repo.dao.active().size==1) }
            stage("service active")
            val saved=repo.dao.active().single().ride.id
            review()
            stage("review requested")
            assertEquals("RECORDING",repo.dao.ride(saved)!!.ride.state)
            // Dismissing/ignoring the review sends no finish command.
            stage("no finish command sent")
            assertTrue(RecordingService.active.value)
            review()
            stage("review requested")
            command(RecordingService.STOP)
            stage("save command sent")
            await { !RecordingService.active.value && (repo.dao.ride(saved)?.ride?.state=="COMPLETED") }
            await { manager.activeNotifications.none { it.id==1 } }
            stage("service saved and notification removed")
            start()
            await { RecordingService.active.value && (repo.dao.active().size==1) }
            val discarded=repo.dao.active().single().ride.id
            stage("second service active")
            review(); command(RecordingService.DISCARD)
            stage("discard command sent")
            await { !RecordingService.active.value && (repo.dao.ride(discarded)==null) }
            stage("discard completed")
            assertNotNull(repo.dao.ride(saved))
            assertEquals(1,repo.rides.first().size)
        } catch(failure: Throwable) {
            stage("FAILED: "+failure.toString()); throw failure
        } finally {
            app.stopService(Intent(app,RecordingService::class.java))
            await { !RecordingService.active.value }
            instrumentation.runOnMainSync { activity?.finish() }
            await { activity?.isDestroyed != false }
            app.repository=original; db.close()
        }
        assertEquals(before,original.rides.first())
        stage("PASS: original history unchanged")
    }
}
