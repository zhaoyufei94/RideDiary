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
    private suspend fun completed(repository: RideRepository): String {
        val id=repository.start(1_000)
        Replay.points().forEach { repository.update(id,it.t,1_000+it.t,it) }
        repository.update(id,220_000,221_000,finish=true)
        return id
    }
    @Test fun deletingRideCascadesPointsAndSegmentsButPreservesRouteAcrossReopen()=runBlocking {
        var db=open(); var repository=RideRepository(db)
        val id=completed(repository)
        repository.createRoute(id,"Saved route","WORK",100.0,70.0)
        val route=repository.dao.routes().single()
        repository.deleteRide(id)
        assertNull(repository.dao.ride(id))
        for(table in listOf("points","segments")) {
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table WHERE rideId = ?",arrayOf(id)).use {
                assertTrue(it.moveToFirst()); assertEquals(0,it.getInt(0))
            }
        }
        assertEquals(listOf(route),repository.dao.routes())
        db.close()
        db=open(); repository=RideRepository(db)
        assertNull(repository.dao.ride(id))
        assertEquals(route,repository.dao.routes().single())
        assertTrue(route.route().length>=Policy.MIN_ROUTE_DISTANCE_M)
        db.close()
    }
    @Test fun routeEditsPersistAndDeletionUnlinksRidesWithoutLosingTracks()=runBlocking {
        var db=open(); var repository=RideRepository(db)
        val id=completed(repository)
        repository.createRoute(id,"Morning","WORK",100.0,70.0)
        val original=repository.dao.routes().single()
        val before=repository.dao.ride(id)!!
        repository.dao.saveRide(before.ride.copy(id="linked"))
        repository.editRoute(original.id," Evening ","HOME",120.0,80.0)
        db.close()
        db=open(); repository=RideRepository(db)
        val edited=repository.dao.routes().single()
        assertEquals("Evening",edited.name)
        assertEquals("HOME",edited.direction)
        assertEquals(120.0,edited.startRadius,0.0)
        assertEquals(80.0,edited.endRadius,0.0)
        assertEquals(original.version+1,edited.version)
        assertEquals(original.shape,edited.shape)
        assertEquals("HOME",repository.dao.ride("linked")!!.ride.direction)
        assertEquals("HOME",repository.dao.ride(id)!!.ride.direction)
        assertEquals(before.points,repository.dao.ride(id)!!.points)
        assertTrue(Statistics.valid(repository.dao.ride(id)!!.sample()))
        repository.deleteRoute(original.id)
        assertTrue(repository.dao.routes().isEmpty())
        for(rideId in listOf(id,"linked")) {
            val ride=repository.dao.ride(rideId)!!.ride
            assertNull(ride.routeId); assertEquals("HOME",ride.direction)
        }
        assertEquals(before.points,repository.dao.ride(id)!!.points)
        assertEquals(before.segments,repository.dao.ride(id)!!.segments)
        assertFalse(Statistics.valid(repository.dao.ride(id)!!.sample()))
        db.close()
        db=open()
        assertNull(db.dao().ride(id)!!.ride.routeId)
        assertTrue(db.dao().routes().isEmpty())
        db.close()
    }
    @Test fun activeRideAndItsRouteAreProtectedAndInvalidEditsDoNotPersist()=runBlocking {
        val db=open(); val repository=RideRepository(db)
        val id=repository.start(1_000)
        val route=RouteRecord("protected","Original","WORK",id,RouteRecord.encode(Replay.route().points),100.0,70.0)
        repository.dao.saveRoute(route)
        repository.classify(id,route,"WORK",false,"")
        assertTrue(runCatching { repository.deleteRide(id) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repository.deleteRoute(route.id) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repository.editRoute(route.id,"Changed","HOME",120.0,80.0) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(route,repository.dao.routes().single())
        assertEquals("RECORDING",repository.dao.ride(id)!!.ride.state)
        repository.update(id,1_000,2_000,finish=true)
        assertTrue(runCatching { repository.editRoute(route.id," ","HOME",120.0,80.0) }.isFailure)
        assertTrue(runCatching { repository.editRoute(route.id,"Changed","HOME",Double.NaN,80.0) }.isFailure)
        assertTrue(runCatching { repository.editRoute(route.id,"Changed","HOME",19.0,501.0) }.isFailure)
        assertEquals(route,repository.dao.routes().single())
        db.close()
    }
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
