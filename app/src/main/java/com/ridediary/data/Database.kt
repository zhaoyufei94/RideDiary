package com.ridediary.data

import androidx.room.*
import com.ridediary.core.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray

@Entity(tableName="rides")
data class RideRecord(
    @PrimaryKey val id: String,
    val startUtc: Long,
    val zone: String,
    val state: String = "RECORDING",
    val endUtc: Long? = null,
    val elapsed: Long = 0,
    val lastSavedUtc: Long = startUtc,
    val routeId: String? = null,
    val direction: String = "OTHER",
    val excluded: Boolean = false,
    val exclusionReason: String = "",
    val algorithmVersion: String = Policy.VERSION,
    val boundaryStart: Long = 0,
    val boundaryEnd: Long? = null,
    val arrivalConfirmed: Boolean = false
)
@Entity(tableName="points", primaryKeys=["rideId","sequence"],
    foreignKeys=[ForeignKey(entity=RideRecord::class,parentColumns=["id"],childColumns=["rideId"],onDelete=ForeignKey.CASCADE)])
data class PointRecord(val rideId: String, val sequence: Int, val t: Long, val utc: Long,
    val lat: Double, val lon: Double, val accuracy: Double, val speed: Double?,
    val speedAccuracy: Double?, val quality: String, val coordinateSystem: String = "WGS84") {
    fun fix() = Fix(t,lat,lon,accuracy,speed,utc,quality,speedAccuracy)
}
@Entity(tableName="segments", primaryKeys=["rideId","sequence"],
    foreignKeys=[ForeignKey(entity=RideRecord::class,parentColumns=["id"],childColumns=["rideId"],onDelete=ForeignKey.CASCADE)])
data class SegmentRecord(val rideId: String, val sequence: Int, val from: Long, val to: Long, val motion: String, val distance: Double)
@Entity(tableName="routes")
data class RouteRecord(@PrimaryKey val id: String, val name: String, val direction: String,
    val sourceRideId: String, val shape: String, val startRadius: Double, val endRadius: Double, val version: Int = 1) {
    fun route() = RouteShape(id,name,direction,decode(shape),startRadius,endRadius)
    companion object {
        fun encode(points: List<Fix>) = JSONArray().apply { points.forEach { put(JSONArray(listOf(it.t,it.lat,it.lon,it.accuracy,it.speed))) } }.toString()
        fun decode(json: String): List<Fix> = JSONArray(json).let { a -> (0 until a.length()).map { i ->
            val p=a.getJSONArray(i); Fix(p.getLong(0),p.getDouble(1),p.getDouble(2),p.getDouble(3),if (p.isNull(4)) null else p.getDouble(4))
        } }
    }
}
data class RideData(@Embedded val ride: RideRecord,
    @Relation(parentColumn="id",entityColumn="rideId") val points: List<PointRecord>,
    @Relation(parentColumn="id",entityColumn="rideId") val segments: List<SegmentRecord>) {
    fun fixes() = points.sortedBy { it.sequence }.map { it.fix() }
    fun sample(): Sample {
        val start=ride.boundaryStart
        val end=ride.boundaryEnd ?: ride.elapsed
        val selected=fixes().filter { it.t in start..end }.map { it.copy(t=it.t-start) }
        val metrics=Analyzer.analyze(selected,(end-start).coerceAtLeast(0))
        return Sample(ride.id,ride.startUtc+start,ride.zone,ride.state,ride.routeId,ride.direction,ride.excluded,metrics,selected,
            if (ride.boundaryEnd == null) ride.endUtc ?: (ride.startUtc+end) else ride.startUtc+end)
    }
}
@Dao
interface RideDao {
    @Transaction @Query("SELECT * FROM rides ORDER BY startUtc DESC") fun observeRides(): Flow<List<RideData>>
    @Query("SELECT * FROM routes ORDER BY name") fun observeRoutes(): Flow<List<RouteRecord>>
    @Transaction @Query("SELECT * FROM rides WHERE id = :id") suspend fun ride(id: String): RideData?
    @Transaction @Query("SELECT * FROM rides WHERE state = 'RECORDING'") suspend fun active(): List<RideData>
    @Query("SELECT * FROM routes") suspend fun routes(): List<RouteRecord>
    @Upsert suspend fun saveRide(ride: RideRecord)
    @Insert suspend fun insertPoint(point: PointRecord)
    @Query("DELETE FROM segments WHERE rideId = :id") suspend fun clearSegments(id: String)
    @Insert suspend fun segments(segments: List<SegmentRecord>)
    @Upsert suspend fun saveRoute(route: RouteRecord)
}
@Database(entities=[RideRecord::class,PointRecord::class,SegmentRecord::class,RouteRecord::class],version=1,exportSchema=false)
abstract class RideDatabase : RoomDatabase() { abstract fun dao(): RideDao }
