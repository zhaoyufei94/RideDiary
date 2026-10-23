package com.ridediary

import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ridediary.core.*
import com.ridediary.data.*
import com.ridediary.recording.RecordingService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StopSaveTest {
    private suspend fun await(message: String,condition: suspend ()->Boolean) {
        withTimeout(15_000) { while(!condition()) delay(50) }
    }
    @Test fun hourLongBatchSavesEveryFixAtRequestedCutoff(): Unit=runBlocking {
        val app=ApplicationProvider.getApplicationContext<RideDiaryApp>()
        val db=Room.inMemoryDatabaseBuilder(app,RideDatabase::class.java).build()
        try {
            val repo=RideRepository(db)
            val start=1_000_000L; val id=repo.start(start)
            val points=(0 until 1800).map { Fix(it*2000L,30.0,120+it*.00001,5.0,4.0,start+it*2000L) }
            val route=RouteRecord("test-route","Test","WORK",id,RouteRecord.encode(points),100.0,70.0)
            repo.dao.saveRoute(route)
            val timer=SystemClock.elapsedRealtime()
            repo.update(id,3_600_000,start+3_600_000,finish=true,additionalFixes=points)
            val ms=SystemClock.elapsedRealtime()-timer
            val saved=repo.dao.ride(id)!!
            assertEquals(1800,saved.points.size)
            assertEquals(points,saved.fixes())
            assertEquals("COMPLETED",saved.ride.state)
            assertEquals(3_600_000,saved.ride.elapsed)
            assertEquals(start+3_600_000,saved.ride.endUtc)
            assertEquals(saved.ride.elapsed,saved.sample().metrics.let { it.moving+it.waiting+it.unknown })
            android.util.Log.i("StopSaveTest","hourLongBatchMs=$ms points=${saved.points.size}")
        } finally { db.close() }
    }
    @Test fun slowDatabaseDoesNotDelayStopCutoffOrChangeDailyHistory(): Unit=runBlocking {
        val app=ApplicationProvider.getApplicationContext<RideDiaryApp>()
        app.ready.await()
        check(!RecordingService.active.value)
        val original=app.repository
        val before=original.rides.first(); val routesBefore=original.dao.routes()
        check(before.none { it.ride.state=="RECORDING" })
        val db=Room.inMemoryDatabaseBuilder(app,RideDatabase::class.java).build()
        val repo=RideRepository(db)
        val release=CompletableDeferred<Unit>()
        var scenario: ActivityScenario<MainActivity>?=null
        try {
            app.repository=repo
            scenario=ActivityScenario.launch(MainActivity::class.java)
            scenario.onActivity { ContextCompat.startForegroundService(it,Intent(it,RecordingService::class.java)) }
            await("service start") { RecordingService.active.value && repo.dao.active().size==1 }
            val id=repo.dao.active().single().ride.id
            val locked=CompletableDeferred<Unit>()
            val blocker=launch(Dispatchers.IO) { db.withTransaction { locked.complete(Unit); release.await() } }
            locked.await()
            val beforeStop=System.currentTimeMillis()
            scenario.onActivity { it.startService(Intent(it,RecordingService::class.java).setAction(RecordingService.STOP)) }
            await("saving state") { RecordingService.saving.value }
            val afterStop=System.currentTimeMillis()
            delay(1600) // Deliberately hold the DB after the user has finished.
            scenario.onActivity { it.startService(Intent(it,RecordingService::class.java).setAction(RecordingService.STOP)) }
            release.complete(Unit); blocker.join()
            await("stop completed") { !RecordingService.active.value }
            val saved=repo.dao.ride(id)!!
            assertEquals("COMPLETED",saved.ride.state)
            assertTrue(saved.ride.endUtc!! in beforeStop..afterStop)
            assertTrue(System.currentTimeMillis()-saved.ride.endUtc!!>=1500)
            assertFalse(RecordingService.saving.value)
            assertFalse(RecordingService.stopPending.value)
        } finally {
            release.complete(Unit)
            app.stopService(Intent(app,RecordingService::class.java))
            await("cleanup") { !RecordingService.active.value }
            scenario?.close()
            app.repository=original
            db.close()
        }
        assertEquals(before,original.rides.first())
        assertEquals(routesBefore,original.dao.routes())
    }
}
