package com.ridediary

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ridediary.core.*
import com.ridediary.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Disposable database only. No test opens, merges or deletes the user's real trips. */
@RunWith(AndroidJUnit4::class)
class TripMergePersistenceTest {
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name="trip-merge-test.db"
    private lateinit var db: RideDatabase
    private lateinit var repo: RideRepository
    private fun open() { db=Room.databaseBuilder(context,RideDatabase::class.java,name).build(); repo=RideRepository(db) }
    @Before fun before() { context.deleteDatabase(name); open() }
    @After fun after() { db.close(); context.deleteDatabase(name) }
    private suspend fun seed(id: String,start: Long,x: Double,elapsed: Long=10_000): RideData {
        val ride=RideRecord(id,start,"Asia/Shanghai",state="COMPLETED",endUtc=start+elapsed,elapsed=elapsed)
        repo.dao.saveRide(ride)
        repo.dao.insertPoints((0..5).map { i -> PointRecord(id,i,i*2_000L,start+i*2_000,
            30.0,110.0+(x+i*10)/(111_195*kotlin.math.cos(Math.toRadians(30.0))),4.0,5.0,0.5,"OK") })
        return repo.dao.ride(id)!!
    }
    @Test fun mergeReopenAndUndoPreserveSourcesAndRawFields()=runBlocking {
        val a=seed("a",1_000,0.0); val b=seed("b",16_000,60.0)
        val id=repo.mergeRides(listOf("b","a"))
        val merged=repo.dao.ride(id)!!
        assertEquals(listOf(id),repo.dao.observeRides().first().map { it.ride.id })
        assertEquals(a.copy(ride=a.ride.copy(mergedInto=id)),repo.dao.ride("a"))
        assertEquals(b.copy(ride=b.ride.copy(mergedInto=id)),repo.dao.ride("b"))
        assertEquals(2,merged.ride.mergedCount); assertEquals(25_000L,merged.ride.elapsed)
        val points=merged.points.sortedBy { it.sequence }
        assertEquals((a.points+b.points).map { Triple(it.utc,it.lon,it.speedAccuracy) },points.map { Triple(it.utc,it.lon,it.speedAccuracy) })
        assertEquals(15_000L,points[6].t); assertTrue(points[6].breakBefore)
        assertEquals(5_000L,merged.sample().metrics.unknown)
        assertEquals(100.0,merged.sample().metrics.distance,0.1)
        assertEquals(5_000L,merged.segments.filter { it.motion=="UNKNOWN" }.sumOf { it.to-it.from })
        db.close(); open()
        assertEquals(merged,repo.dao.ride(id))
        repo.undoMerge(id)
        assertNull(repo.dao.ride(id)); assertEquals(a,repo.dao.ride("a")); assertEquals(b,repo.dao.ride("b"))
        assertEquals(2,repo.dao.visibleRides().size)
    }
    @Test fun nestedMergeUndoAndPermanentDeleteHaveNoGhostTrips()=runBlocking {
        seed("a",1_000,0.0); seed("b",16_000,60.0); seed("c",31_000,120.0)
        val first=repo.mergeRides(listOf("a","b"))
        val route=RouteRecord("saved","Office","WORK","a",RouteRecord.encode(repo.dao.ride(first)!!.fixes()),100.0,70.0)
        repo.dao.saveRoute(route)
        val second=repo.mergeRides(listOf(first,"c"))
        assertEquals(3,repo.dao.ride(second)!!.ride.mergedCount)
        assertEquals(2,repo.dao.ride(second)!!.fixes().count { it.breakBefore })
        repo.undoMerge(second)
        assertEquals(setOf(first,"c"),repo.dao.visibleRides().map { it.ride.id }.toSet())
        assertEquals(first,repo.dao.ride("a")!!.ride.mergedInto)
        val again=repo.mergeRides(listOf(first,"c"))
        repo.deleteRide(again)
        assertTrue(repo.dao.visibleRides().isEmpty())
        listOf("a","b","c",first,again).forEach { assertNull(repo.dao.ride(it)) }
        assertEquals(route,repo.dao.routes().single())
        for(table in listOf("rides","points","segments")) db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use {
            assertTrue(it.moveToFirst()); assertEquals(0,it.getInt(0))
        }
    }
    @Test fun staleRequestsAndInvalidJoinsLeaveVisibleRecordsUnchanged()=runBlocking {
        val a=seed("a",1_000,0.0); val b=seed("b",16_000,1_000.0)
        val rejected=runCatching { repo.mergeRides(listOf("a","b")) }.exceptionOrNull()
        assertTrue(rejected is MergeRejectedException)
        assertEquals(listOf(a,b),repo.dao.visibleRides())
        repo.deleteRide("b"); seed("b",16_000,60.0)
        val merged=repo.mergeRides(listOf("a","b"))
        assertTrue(runCatching { repo.mergeRides(listOf("a","b")) }.exceptionOrNull() is MergeRejectedException)
        assertTrue(runCatching { repo.deleteRide("a") }.isFailure)
        assertTrue(runCatching { repo.boundaries("a",0,5_000) }.isFailure)
        assertEquals(listOf(merged),repo.dao.visibleRides().map { it.ride.id })
        val active=repo.start(50_000)
        assertTrue(runCatching { repo.undoMerge(merged) }.isFailure)
        repo.discardActive(active); repo.undoMerge(merged)
        assertEquals(2,repo.dao.visibleRides().size)
    }
    @Test fun trimsInterruptedRecoveryAndManualExclusionAreRespected()=runBlocking {
        val a=seed("a",1_000,0.0,50_000)
        // Recovery added trailing time; the next recording actually starts at 16 s.
        repo.dao.saveRide(a.ride.copy(state="INTERRUPTED",excluded=true,exclusionReason="manual"))
        val b=seed("b",16_000,60.0)
        repo.boundaries("b",2_000,8_000)
        val id=repo.mergeRides(listOf("a","b")); val merged=repo.dao.ride(id)!!
        assertEquals(23_000L,merged.ride.elapsed)
        assertTrue(merged.ride.excluded); assertEquals("manual",merged.ride.exclusionReason)
        assertEquals(10,merged.points.size)
        assertEquals(7_000L,merged.sample().metrics.unknown)
        repo.undoMerge(id)
        assertEquals("INTERRUPTED",repo.dao.ride("a")!!.ride.state)
        assertEquals(b.points,repo.dao.ride("b")!!.points)
        assertEquals(2_000L,repo.dao.ride("b")!!.ride.boundaryStart)
    }
    @Test fun wholeMergedTraceCanRecoverAutomaticPartialRouteExclusion()=runBlocking {
        val points=Replay.points(); val split=points.size/2
        val route=RouteRecord("route","Office","WORK","reference",RouteRecord.encode(points),100.0,70.0,groupId="office")
        repo.dao.saveRoute(route)
        val first=points.take(split); val last=points.drop(split)
        for((id,fixes) in listOf("a" to first,"b" to last)) {
            val start=1_000+fixes.first().t; val duration=fixes.last().t-fixes.first().t
            repo.dao.saveRide(RideRecord(id,start,"Asia/Shanghai",state="COMPLETED",endUtc=start+duration,elapsed=duration,
                routeId="route",direction="WORK",excluded=true,exclusionReason="AUTO_ROUTE_CHECK"))
            repo.dao.insertPoints(fixes.mapIndexed { index,f -> PointRecord(id,index,f.t-fixes.first().t,1_000+f.t,f.lat,f.lon,f.accuracy,f.speed,f.speedAccuracy,f.quality) })
        }
        val id=repo.mergeRides(listOf("b","a")); val merged=repo.dao.ride(id)!!
        assertEquals("route",merged.ride.routeId)
        assertFalse(merged.ride.excluded)
        assertFalse(merged.ride.arrivalConfirmed)
        assertTrue(repo.dao.ride("a")!!.ride.excluded)
        val decoded=RouteRecord.decode(RouteRecord.encode(merged.fixes()))
        assertEquals(1,decoded.count { it.breakBefore })
    }
}
