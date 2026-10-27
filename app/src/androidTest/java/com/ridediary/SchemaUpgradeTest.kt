package com.ridediary

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ridediary.core.Replay
import com.ridediary.data.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Synthetic disposable files only: never opens the application's ridediary.db. */
@RunWith(AndroidJUnit4::class)
class SchemaUpgradeTest {
    private val context=ApplicationProvider.getApplicationContext<android.content.Context>()
    private val name="schema-upgrade-test.db"
    private var database: RideDatabase?=null
    @Before fun before() { context.deleteDatabase(name) }
    @After fun after() { database?.close(); context.deleteDatabase(name) }
    private fun fixture(version: Int,mode: Boolean) {
        val file=context.getDatabasePath(name); file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file,null).use { old ->
            old.execSQL("CREATE TABLE rides (id TEXT NOT NULL PRIMARY KEY,startUtc INTEGER NOT NULL,zone TEXT NOT NULL,state TEXT NOT NULL,endUtc INTEGER,elapsed INTEGER NOT NULL,lastSavedUtc INTEGER NOT NULL,routeId TEXT,direction TEXT NOT NULL,excluded INTEGER NOT NULL,exclusionReason TEXT NOT NULL,algorithmVersion TEXT NOT NULL,boundaryStart INTEGER NOT NULL,boundaryEnd INTEGER,arrivalConfirmed INTEGER NOT NULL,arrivalSuggested INTEGER NOT NULL DEFAULT 0)")
            old.execSQL("CREATE TABLE points (rideId TEXT NOT NULL,sequence INTEGER NOT NULL,t INTEGER NOT NULL,utc INTEGER NOT NULL,lat REAL NOT NULL,lon REAL NOT NULL,accuracy REAL NOT NULL,speed REAL,speedAccuracy REAL,quality TEXT NOT NULL,coordinateSystem TEXT NOT NULL,PRIMARY KEY(rideId,sequence),FOREIGN KEY(rideId) REFERENCES rides(id) ON DELETE CASCADE)")
            old.execSQL("CREATE TABLE segments (rideId TEXT NOT NULL,sequence INTEGER NOT NULL,`from` INTEGER NOT NULL,`to` INTEGER NOT NULL,motion TEXT NOT NULL,distance REAL NOT NULL,PRIMARY KEY(rideId,sequence),FOREIGN KEY(rideId) REFERENCES rides(id) ON DELETE CASCADE)")
            val planColumns=if(version>=3) ",planDays INTEGER NOT NULL DEFAULT 31,planMinute INTEGER" else ""
            val modeColumn=if(mode) ",planMode TEXT NOT NULL DEFAULT 'WORKDAYS'" else ""
            old.execSQL("CREATE TABLE routes (id TEXT NOT NULL PRIMARY KEY,name TEXT NOT NULL,direction TEXT NOT NULL,sourceRideId TEXT NOT NULL,shape TEXT NOT NULL,startRadius REAL NOT NULL,endRadius REAL NOT NULL,version INTEGER NOT NULL,groupId TEXT NOT NULL DEFAULT ''$planColumns$modeColumn)")
            old.execSQL("INSERT INTO rides VALUES ('trip',1000,'Asia/Shanghai','COMPLETED',221000,220000,221000,'work','WORK',1,'kept','v1',1000,219000,1,1)")
            old.execSQL("INSERT INTO points VALUES ('trip',0,0,1000,30,110,4,4,NULL,'GOOD','WGS84')")
            old.execSQL("INSERT INTO segments VALUES ('trip',0,0,2000,'MOVING',8)")
            listOf("work","home").forEach { id ->
                old.execSQL("INSERT INTO routes (id,name,direction,sourceRideId,shape,startRadius,endRadius,version,groupId) VALUES (?,?,?,?,?,100,70,2,'group')",
                    arrayOf(id,"Synthetic route",if(id=="work") "WORK" else "HOME","trip",RouteRecord.encode(Replay.route().points)))
            }
            if(version>=3) old.execSQL("UPDATE routes SET planDays=66,planMinute=1155 WHERE id='home'")
            if(mode) old.execSQL("UPDATE routes SET planMode='CUSTOM' WHERE id='home'")
            if(version==3) {
                old.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
                val identity=if(mode) "57588671c5091b76b2e49e8771f924ea" else "0e1f63cb9de2d5b48b5230e9fa77bc62"
                old.execSQL("INSERT INTO room_master_table VALUES (42,?)",arrayOf(identity))
            }
            old.version=version
        }
    }
    private fun open(): RideRepository {
        database=Room.databaseBuilder(context,RideDatabase::class.java,name).addMigrations(
            RideDatabase.MIGRATION_1_2,RideDatabase.MIGRATION_2_3,RideDatabase.MIGRATION_3_4,RideDatabase.MIGRATION_4_5).build()
        return RideRepository(database!!)
    }
    private suspend fun verify(version: Int,mode: Boolean) {
        fixture(version,mode)
        var repo=open()
        val ride=repo.dao.ride("trip")!!
        val routes=repo.dao.routes()
        assertEquals(5,database!!.openHelper.readableDatabase.version)
        assertEquals(1,ride.points.size); assertEquals(1,ride.segments.size)
        assertEquals(30.0,ride.points.single().lat,0.0)
        assertEquals("work",ride.ride.routeId)
        assertEquals("COMPLETED",ride.ride.state)
        assertTrue(ride.ride.excluded); assertEquals("kept",ride.ride.exclusionReason)
        assertEquals(1000L,ride.ride.boundaryStart); assertEquals(219000L,ride.ride.boundaryEnd)
        assertTrue(ride.ride.arrivalConfirmed); assertTrue(ride.ride.arrivalSuggested)
        assertNull(ride.ride.mergedInto); assertEquals(0,ride.ride.mergedCount)
        assertFalse(ride.points.single().breakBefore)
        assertEquals(2,routes.size)
        val work=routes.single { it.id=="work" }; val home=routes.single { it.id=="home" }
        assertEquals(if(version==3 && !mode) "CUSTOM" else "WORKDAYS",work.planMode)
        assertEquals(if(version==2) "WORKDAYS" else "CUSTOM",home.planMode)
        assertEquals(if(version==2) 31 else 66,home.planDays)
        assertEquals(if(version==2) null else 1155,home.planMinute)
        assertEquals("group",home.groupKey); assertEquals(2,home.version)
        assertEquals(RouteRecord.encode(Replay.route().points),home.shape)
        database!!.close(); repo=open()
        assertEquals(ride,repo.dao.ride("trip"))
        assertEquals(routes,repo.dao.routes())
    }
    @Test fun earlyVersionThreeWithoutModeUpgradesAndRetainsWeeklyPlans()=runBlocking { verify(3,false) }
    @Test fun finalVersionThreeRetainsBothPlanModes()=runBlocking { verify(3,true) }
    @Test fun versionTwoStillUpgradesWithWorkdayDefaults()=runBlocking { verify(2,false) }
    @Test fun versionFourAddsMergeDefaultsAndPreservesEveryRecord()=runBlocking { verify(4,true) }
}
