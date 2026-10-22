package com.ridediary.data

import androidx.room.withTransaction
import com.ridediary.core.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import java.util.UUID

class RideRepository(val db: RideDatabase) {
    val dao=db.dao()
    private val mutex=Mutex()
    val rides=dao.observeRides()
    val routes=dao.observeRoutes()
    suspend fun recoverInterrupted(now: Long) = mutex.withLock {
        db.withTransaction {
            dao.active().forEach { data ->
                val elapsed=data.ride.elapsed+(now-data.ride.lastSavedUtc).coerceAtLeast(0)
                val recovered=data.ride.copy(state="INTERRUPTED",endUtc=now,elapsed=elapsed,lastSavedUtc=now)
                persist(recovered,data.fixes())
            }
        }
    }
    suspend fun start(now: Long): String = mutex.withLock {
        db.withTransaction {
            dao.active().firstOrNull()?.ride?.id ?: UUID.randomUUID().toString().also {
                dao.saveRide(RideRecord(it,now,ZoneId.systemDefault().id))
            }
        }
    }
    private suspend fun persist(ride: RideRecord, fixes: List<Fix>) {
        dao.saveRide(ride)
        dao.clearSegments(ride.id)
        dao.segments(Analyzer.analyze(fixes,ride.elapsed).segments.mapIndexed { index,s ->
            SegmentRecord(ride.id,index,s.from,s.to,s.motion.name,s.distance)
        })
    }
    suspend fun update(id: String, elapsed: Long, now: Long, fix: Fix? = null, finish: Boolean = false): RideRecord? = mutex.withLock {
        db.withTransaction {
            val data=dao.ride(id) ?: return@withTransaction null
            if (data.ride.state != "RECORDING") return@withTransaction data.ride
            val points=data.fixes().toMutableList()
            if (fix != null && (points.lastOrNull()?.t ?: -1) < fix.t) {
                val previousGood=points.lastOrNull(::good)
                val dt=previousGood?.let { fix.t-it.t } ?: 0
                val tagged=if (good(fix) && previousGood!=null && dt in 1..Policy.MAX_GAP_MS &&
                    meters(previousGood,fix)*1000/dt > Policy.MAX_SPEED) fix.copy(quality="JUMP") else fix
                dao.insertPoint(PointRecord(id,points.size,tagged.t,tagged.utc,tagged.lat,tagged.lon,tagged.accuracy,tagged.speed,tagged.speedAccuracy,tagged.quality))
                points += tagged
            }
            val shapes=dao.routes().map { it.route() }
            val match=if (data.ride.routeId == null) Routing.recognize(points,shapes)
                else shapes.firstOrNull { it.id == data.ride.routeId }?.let { Routing.follow(points,it) }
            val arrived=fix != null && match != null && Routing.arrived(points,match,elapsed)
            val partialOrDeviated=finish && data.ride.routeId!=null && (match==null || match.deviated || match.progress<Policy.ARRIVAL_PROGRESS)
            val ride=data.ride.copy(elapsed=elapsed.coerceAtLeast(data.ride.elapsed),lastSavedUtc=now,
                routeId=data.ride.routeId ?: match?.route?.id, direction=match?.route?.direction ?: data.ride.direction,
                state=if (finish || arrived) "COMPLETED" else "RECORDING",endUtc=if (finish || arrived) now else null,
                arrivalConfirmed=arrived,
                excluded=data.ride.excluded || partialOrDeviated,
                exclusionReason=if(partialOrDeviated && !data.ride.excluded) "AUTO_ROUTE_CHECK" else data.ride.exclusionReason)
            persist(ride,points)
            ride
        }
    }
    suspend fun classify(id: String, route: RouteRecord?, direction: String, excluded: Boolean, reason: String) = mutex.withLock {
        db.withTransaction {
            dao.ride(id)?.ride?.let { dao.saveRide(it.copy(routeId=route?.id,direction=route?.direction ?: direction,
                excluded=excluded,exclusionReason=reason)) }
        }
    }
    suspend fun boundaries(id: String, start: Long, end: Long) = mutex.withLock {
        val data=dao.ride(id) ?: return@withLock
        require(data.ride.state != "RECORDING" && start >= 0 && end > start && end <= data.ride.elapsed)
        dao.saveRide(data.ride.copy(boundaryStart=start,boundaryEnd=end))
    }
    suspend fun createRoute(id: String, name: String, direction: String, startRadius: Double, endRadius: Double) = mutex.withLock {
        db.withTransaction {
            val data=dao.ride(id) ?: return@withTransaction
            val fixes=data.sample().points.filter(::good)
            require(fixes.size >= 4 && Analyzer.analyze(fixes,data.sample().metrics.elapsed).distance >= Policy.MIN_ROUTE_DISTANCE_M)
            val route=RouteRecord(UUID.randomUUID().toString(),name.trim(),direction,id,RouteRecord.encode(fixes),startRadius,endRadius)
            dao.saveRoute(route)
            dao.saveRide(data.ride.copy(routeId=route.id,direction=direction))
        }
    }
}
