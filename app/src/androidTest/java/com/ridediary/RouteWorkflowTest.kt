package com.ridediary

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ridediary.core.*
import com.ridediary.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Independent disposable files; never reads or rewrites the user's route history. */
@RunWith(AndroidJUnit4::class)
class RouteWorkflowTest {
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name="route-workflow-test.db"
    private var db: RideDatabase?=null
    @Before fun before() { context.deleteDatabase(name) }
    @After fun after() { db?.close(); context.deleteDatabase(name) }
    private fun open(): RideRepository {
        db=Room.databaseBuilder(context,RideDatabase::class.java,name).addMigrations(RideDatabase.MIGRATION_1_2,RideDatabase.MIGRATION_2_3,RideDatabase.MIGRATION_3_4,RideDatabase.MIGRATION_4_5).build()
        return RideRepository(db!!)
    }
    private suspend fun record(repo: RideRepository,points: List<Fix>,start: Long): String {
        val id=repo.start(start)
        points.forEach { repo.update(id,it.t,start+it.t,it) }
        return id
    }
    @Test fun arrivalWaitsForSaveAndDiscardCascadesOnlyCurrentRide()=runBlocking {
        val repo=open()
        val source=record(repo,Replay.points(),1_000)
        repo.update(source,220_000,221_000,finish=true)
        repo.createRoute(source,"互联网金融中心","WORK",100.0,70.0)
        val route=repo.dao.routes().single()
        val id=record(repo,Replay.points(),1_000_000)
        val pending=repo.dao.ride(id)!!
        assertEquals("RECORDING",pending.ride.state)
        assertTrue(pending.ride.arrivalSuggested)
        assertFalse(pending.ride.arrivalConfirmed)
        assertNull(pending.ride.endUtc)
        repo.update(id,222_000,1_222_000)
        assertEquals("RECORDING",repo.dao.ride(id)!!.ride.state)
        repo.discardActive(id)
        assertNull(repo.dao.ride(id))
        for(table in listOf("points","segments")) db!!.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table WHERE rideId=?",arrayOf(id)).use {
            assertTrue(it.moveToFirst()); assertEquals(0,it.getInt(0))
        }
        assertEquals(route,repo.dao.routes().single())
        assertEquals(111,repo.dao.ride(source)!!.points.size)
        val saved=record(repo,Replay.points(),2_000_000)
        repo.update(saved,220_000,2_220_000,finish=true)
        assertEquals("COMPLETED",repo.dao.ride(saved)!!.ride.state)
        assertEquals(route.id,repo.dao.ride(saved)!!.ride.routeId)
        assertTrue(repo.dao.ride(saved)!!.ride.arrivalConfirmed)
        repo.discardActive(saved) // A delayed discard cannot delete an already saved ride.
        assertNotNull(repo.dao.ride(saved))
    }
    @Test fun returnTripCreatesActualGeometryWithinGroupAndGroupDeletionKeepsHistory()=runBlocking {
        val repo=open()
        val work=record(repo,Replay.points(),1_000)
        repo.update(work,220_000,221_000,finish=true)
        repo.createRoute(work,"互联网金融中心（上班）","WORK",100.0,70.0)
        val original=repo.dao.routes().single()
        val reversed=RouteIdentity.reversed(Replay.route()).points
        val home=record(repo,reversed,1_000_000)
        repo.update(home,reversed.last().t,1_000_000+reversed.last().t,finish=true)
        val routes=repo.dao.routes()
        assertEquals(2,routes.size)
        assertEquals(1,routeGroups(routes).size)
        val returning=routes.single { it.direction=="HOME" }
        assertEquals(original.groupKey,returning.groupKey)
        assertEquals(home,returning.sourceRideId)
        assertEquals(repo.dao.ride(home)!!.fixes().filter(::good).map { it.lat to it.lon },returning.route().points.map { it.lat to it.lon })
        assertEquals(returning.id,repo.dao.ride(home)!!.ride.routeId)
        repo.editRoute(original.id,"金融中心","WORK",110.0,80.0)
        assertEquals(setOf("金融中心"),repo.dao.routes().map { it.name }.toSet())
        assertTrue(runCatching { repo.createRoute(home,"金融中心","HOME",100.0,70.0) }.isFailure)
        val before=repo.dao.ride(home)!!
        repo.deleteRouteGroup(original.groupKey)
        assertTrue(repo.dao.routes().isEmpty())
        assertEquals(before.points,repo.dao.ride(home)!!.points)
        assertNull(repo.dao.ride(home)!!.ride.routeId)
        assertNull(repo.dao.ride(work)!!.ride.routeId)
    }
    @Test fun manualCorrectionCanCreateMissingDirectionOrNewNamedRoute()=runBlocking {
        val repo=open(); val points=Replay.points()
        val first=record(repo,points,1_000)
        repo.update(first,220_000,221_000,finish=true)
        repo.createRoute(first,"Office","WORK",100.0,70.0)
        val group=repo.dao.routes().single().groupKey
        val second=record(repo,points,1_000_000)
        repo.update(second,220_000,1_220_000,finish=true)
        repo.createRoute(second,"Office","HOME",70.0,100.0,groupId=group)
        val home=repo.dao.routes().single { it.direction=="HOME" }
        assertEquals(group,home.groupKey)
        assertEquals("HOME",repo.dao.ride(second)!!.ride.direction)
        repo.createRoute(second,"Alternate","HOME",80.0,80.0)
        assertEquals(2,routeGroups(repo.dao.routes()).size)
        repo.classify(second,home,"HOME",false,"")
        assertEquals(home.id,repo.dao.ride(second)!!.ride.routeId)
        assertEquals(111,repo.dao.ride(second)!!.points.size)
    }
    @Test fun versionOneMigrationGroupsNamesAndPreservesEveryReferenceAndRawPoint()=runBlocking {
        val file=context.getDatabasePath(name); file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file,null).use { old ->
            old.execSQL("CREATE TABLE rides (id TEXT NOT NULL PRIMARY KEY,startUtc INTEGER NOT NULL,zone TEXT NOT NULL,state TEXT NOT NULL,endUtc INTEGER,elapsed INTEGER NOT NULL,lastSavedUtc INTEGER NOT NULL,routeId TEXT,direction TEXT NOT NULL,excluded INTEGER NOT NULL,exclusionReason TEXT NOT NULL,algorithmVersion TEXT NOT NULL,boundaryStart INTEGER NOT NULL,boundaryEnd INTEGER,arrivalConfirmed INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE points (rideId TEXT NOT NULL,sequence INTEGER NOT NULL,t INTEGER NOT NULL,utc INTEGER NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL,accuracy REAL NOT NULL,speed REAL,speedAccuracy REAL,quality TEXT NOT NULL,coordinateSystem TEXT NOT NULL,PRIMARY KEY(rideId,sequence),FOREIGN KEY(rideId) REFERENCES rides(id) ON DELETE CASCADE)")
            old.execSQL("CREATE TABLE segments (rideId TEXT NOT NULL,sequence INTEGER NOT NULL,`from` INTEGER NOT NULL,`to` INTEGER NOT NULL,motion TEXT NOT NULL,distance REAL NOT NULL,PRIMARY KEY(rideId,sequence),FOREIGN KEY(rideId) REFERENCES rides(id) ON DELETE CASCADE)")
            old.execSQL("CREATE TABLE routes (id TEXT NOT NULL PRIMARY KEY,name TEXT NOT NULL,direction TEXT NOT NULL,sourceRideId TEXT NOT NULL,shape TEXT NOT NULL,startRadius REAL NOT NULL,endRadius REAL NOT NULL,version INTEGER NOT NULL)")
            old.execSQL("INSERT INTO rides VALUES ('old',1000,'Asia/Shanghai','COMPLETED',221000,220000,221000,'work','WORK',0,'','v1',0,NULL,1)")
            old.execSQL("INSERT INTO points VALUES ('old',0,0,1000,30,110,4,4,NULL,'GOOD','WGS84')")
            old.execSQL("INSERT INTO segments VALUES ('old',0,0,2000,'MOVING',8)")
            listOf(Triple("work","互联网金融中心（上班）","WORK"),Triple("home","互联网金融中心 - 下班","HOME"),Triple("other","另一条路线","WORK")).forEach { (id,label,dir) ->
                old.execSQL("INSERT INTO routes VALUES (?,?,?,?,?,100,70,1)",arrayOf(id,label,dir,"old",RouteRecord.encode(Replay.route().points)))
            }
            old.version=1
        }
        var repo=open()
        val routes=repo.dao.routes()
        assertEquals(setOf("work","home","other"),routes.map { it.id }.toSet())
        assertEquals(routes.first { it.id=="work" }.groupKey,routes.first { it.id=="home" }.groupKey)
        assertEquals(2,routeGroups(routes).size)
        assertTrue(routes.all { it.planMode=="WORKDAYS" && it.planDays==31 && it.planMinute==null })
        assertEquals(5,db!!.openHelper.readableDatabase.version)
        assertEquals("work",repo.dao.ride("old")!!.ride.routeId)
        assertEquals(1,repo.dao.ride("old")!!.points.size)
        assertEquals(1,repo.dao.ride("old")!!.segments.size)
        assertFalse(repo.dao.ride("old")!!.ride.arrivalSuggested)
        db!!.close(); repo=open()
        assertEquals(routes,repo.dao.routes())
        assertEquals(30.0,repo.dao.ride("old")!!.points.single().lat,0.0)
        repo.updatePlan("home",(1 shl 1) or (1 shl 6),19*60+15)
        db!!.close(); repo=open()
        val planned=repo.dao.routes().single { it.id=="home" }
        assertEquals("CUSTOM",planned.planMode)
        assertEquals(66,planned.planDays)
        assertEquals(1155,planned.planMinute)
        assertEquals(31,repo.dao.routes().single { it.id=="work" }.planDays)
        assertEquals(1,repo.dao.ride("old")!!.points.size)
        assertEquals(1,repo.dao.ride("old")!!.segments.size)
    }
}
