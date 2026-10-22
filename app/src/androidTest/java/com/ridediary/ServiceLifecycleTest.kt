package com.ridediary

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.ridediary.data.RideDatabase
import com.ridediary.data.RideRepository
import com.ridediary.recording.RecordingService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real service/provider lifecycle; every sampled fix stays in a disposable test database. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion=28)
class ServiceLifecycleTest {
    private suspend fun awaitCondition(message: String, condition: suspend () -> Boolean) {
        val deadline=SystemClock.elapsedRealtime()+12_000
        while(!condition()) {
            check(SystemClock.elapsedRealtime()<deadline) { message }
            delay(100)
        }
    }

    @Test fun duplicateStartsRecreationBackgroundCheckpointsAndNotificationStop()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as RideDiaryApp
        app.ready.await()
        check(!RecordingService.active.value) { "An existing recording must not be interrupted by this test" }
        val original=app.repository
        val originalRides=original.rides.first()
        val name="service-lifecycle-test.db"
        app.deleteDatabase(name)
        val database=Room.databaseBuilder(app,RideDatabase::class.java,name).build()
        val isolated=RideRepository(database)
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            // Standard app-scoped instrumentation grants; no background location or battery changes.
            for(permission in listOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)) {
                instrumentation.uiAutomation.grantRuntimePermission(app.packageName,permission)
            }
            if(Build.VERSION.SDK_INT>=33) instrumentation.uiAutomation.grantRuntimePermission(app.packageName,Manifest.permission.POST_NOTIFICATIONS)
            app.repository=isolated
            scenario=ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { activity ->
                repeat(3) { ContextCompat.startForegroundService(activity,Intent(activity,RecordingService::class.java)) }
            }
            awaitCondition("Service did not become active") { RecordingService.active.value && isolated.dao.active().size==1 }
            val id=isolated.dao.active().single().ride.id
            scenario.recreate()
            awaitCondition("No incremental checkpoint was persisted") { (isolated.dao.ride(id)?.ride?.elapsed ?: 0)>=2_000 }
            val beforeBackground=isolated.dao.ride(id)!!.ride.elapsed
            scenario.onActivity { it.moveTaskToBack(true) }
            awaitCondition("Timer stopped when activity went to the background") { (isolated.dao.ride(id)?.ride?.elapsed ?: 0)>=beforeBackground+4_000 }
            assertEquals(id,isolated.dao.active().single().ride.id)
            assertEquals(1,isolated.rides.first().size)
            val manager=app.getSystemService(NotificationManager::class.java)
            val notification=manager.activeNotifications.single { it.id==1 && it.notification.channelId==RecordingService.CHANNEL }
            notification.notification.actions.single().actionIntent.send()
            awaitCondition("Notification stop did not complete the ride") {
                !RecordingService.active.value && isolated.dao.ride(id)?.ride?.state=="COMPLETED"
            }
            val saved=isolated.dao.ride(id)!!
            assertTrue(saved.ride.elapsed>=6_000)
            assertTrue(saved.segments.isNotEmpty())
            assertEquals(saved.ride.elapsed,saved.sample().metrics.let { it.moving+it.waiting+it.unknown })
            assertTrue(isolated.dao.active().isEmpty())
            awaitCondition("Recording notification remained after stop") { manager.activeNotifications.none { it.id==1 } }
        } finally {
            app.stopService(Intent(app,RecordingService::class.java))
            awaitCondition("Service cleanup did not stop") { !RecordingService.active.value }
            instrumentation.waitForIdleSync()
            scenario?.close()
            app.repository=original
            database.close()
            app.deleteDatabase(name)
        }
        assertEquals(originalRides,original.rides.first())
    }
}
