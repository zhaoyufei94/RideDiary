package com.ridediary

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ridediary.core.*
import com.ridediary.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchingCadenceTest {
    @Test fun matchingIsThrottledButRawFixesPersistAndFinishForcesAReview(): Unit=runBlocking {
        val app=ApplicationProvider.getApplicationContext<RideDiaryApp>()
        val db=Room.inMemoryDatabaseBuilder(app,RideDatabase::class.java).build()
        try {
            val repo=RideRepository(db); val start=100_000L; val id=repo.start(start)
            val reference=Replay.route()
            repo.dao.saveRoute(RouteRecord("reference","Commute","WORK",id,RouteRecord.encode(reference.points),100.0,70.0))
            val trace=Replay.points()
            repo.update(id,40_000,start+40_000,additionalFixes=trace.filter { it.t<=40_000 })
            val first=repo.matching.value!!
            assertEquals(id,first.rideId)
            assertTrue(first.report.candidates.isNotEmpty())
            assertTrue(first.intervalMs in 5_000..15_000)
            repo.update(id,42_000,start+42_000,fix=trace.first { it.t==42_000L })
            assertSame(first,repo.matching.value)
            assertEquals(22,repo.dao.ride(id)!!.points.size)
            val nextElapsed=40_000+first.intervalMs
            val newFix=trace.first { it.t>=nextElapsed }
            repo.update(id,newFix.t,start+newFix.t,fix=newFix)
            val second=repo.matching.value!!
            assertNotSame(first,second)
            repo.update(id,newFix.t+1000,start+newFix.t+1000,finish=true)
            assertNotSame(second,repo.matching.value)
            assertEquals(newFix.t+1000,repo.matching.value!!.computedElapsed)
            assertEquals("COMPLETED",repo.dao.ride(id)!!.ride.state)
        } finally { db.close() }
    }
}
