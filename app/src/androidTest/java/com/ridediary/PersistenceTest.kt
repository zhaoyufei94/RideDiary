package com.ridediary

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ridediary.core.*
import com.ridediary.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceTest {
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name="persistence-test.db"
    private fun open()=Room.databaseBuilder(context,RideDatabase::class.java,name).build()
    @After fun cleanup() { context.deleteDatabase(name) }
    @Test fun reopensRawPointsRoutesSegmentsAndRebuildsAfterCorrection()=runBlocking {
        context.deleteDatabase(name)
        var db=open(); var repository=RideRepository(db)
        val id=repository.start(1_000)
        Replay.points().forEach { repository.update(id,it.t,1_000+it.t,it) }
        repository.update(id,220_000,221_000,finish=true)
        repository.createRoute(id,"Test","WORK",100.0,70.0)
        val before=repository.dao.ride(id)!!
        assertEquals(111,before.points.size); assertTrue(before.segments.isNotEmpty())
        db.close()
        db=open(); repository=RideRepository(db)
        val reopened=repository.dao.ride(id)!!
        assertEquals(before,reopened)
        assertEquals(1,repository.dao.routes().size)
        assertTrue(Statistics.valid(reopened.sample()))
        repository.classify(id,null,"HOME",true,"Unusual stop")
        val edited=repository.dao.ride(id)!!
        assertFalse(Statistics.valid(edited.sample()))
        assertEquals(111,edited.points.size)
        repository.boundaries(id,20_000,180_000)
        assertEquals(160_000,repository.dao.ride(id)!!.sample().metrics.elapsed)
        assertEquals(111,repository.dao.ride(id)!!.points.size)
        val partialId=repository.start(1_000_000)
        Replay.points().take(40).forEach { repository.update(partialId,it.t,1_000_000+it.t,it) }
        repository.update(partialId,80_000,1_080_000,finish=true)
        assertTrue(repository.dao.ride(partialId)!!.ride.excluded)
        assertFalse(Statistics.valid(repository.dao.ride(partialId)!!.sample()))
        db.close()
    }
    @Test fun duplicateStartReturnsSameRideAndRecoveryCreatesUnknownGap()=runBlocking {
        val db=open(); val repository=RideRepository(db)
        val id=repository.start(1_000)
        assertEquals(id,repository.start(2_000))
        repository.update(id,10_000,11_000,Replay.points()[5])
        repository.recoverInterrupted(71_000)
        val data=repository.dao.ride(id)!!
        assertEquals("INTERRUPTED",data.ride.state)
        assertEquals(70_000,data.ride.elapsed)
        assertTrue(data.sample().metrics.unknown>=60_000)
        assertFalse(Statistics.valid(data.sample()))
        assertTrue(repository.dao.active().isEmpty())
        db.close()
    }
}
