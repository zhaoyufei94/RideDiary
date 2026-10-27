package com.ridediary.data

import androidx.room.withTransaction
import com.ridediary.core.*
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import java.util.UUID

class RouteUnavailableException: IllegalStateException()

class RideRepository(val db: RideDatabase) {
    val dao=db.dao()
    private val mutex=Mutex()
    val rides=dao.observeRides()
    val routes=dao.observeRoutes()
    private val liveMatching=MutableStateFlow<LiveRouteMatch?>(null)
    val matching=liveMatching.asStateFlow()
    private data class MatchingCache(val rideId: String,val pointCount: Int,val routes: List<RouteRecord>,
        val fixedId: String?,val snapshot: LiveRouteMatch,val fixedMatch: Match?)
    private var matchingCache: MatchingCache?=null
    suspend fun recoverInterrupted(now: Long) = mutex.withLock {
        db.withTransaction {
            dao.active().forEach { data ->
                val elapsed=data.ride.elapsed+(now-data.ride.lastSavedUtc).coerceAtLeast(0)
                val recovered=data.ride.copy(state="INTERRUPTED",endUtc=now,elapsed=elapsed,lastSavedUtc=now)
                persist(recovered,data.fixes())
            }
        }
    }
    suspend fun start(now: Long,routeId: String?=null): String = mutex.withLock {
        db.withTransaction {
            dao.active().firstOrNull()?.ride?.id ?: UUID.randomUUID().toString().also {
                val route=routeId?.let { id -> dao.routes().firstOrNull { it.id==id } ?: throw RouteUnavailableException() }
                dao.saveRide(RideRecord(it,now,ZoneId.systemDefault().id,routeId=route?.id,direction=route?.direction ?: "OTHER"))
                matchingCache=null; liveMatching.value=null
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
    suspend fun update(id: String, elapsed: Long, now: Long, fix: Fix? = null, finish: Boolean = false,fixedRouteId: String? = null, additionalFixes: List<Fix> = emptyList()): RideRecord? = mutex.withLock {
        var computed: MatchingCache?=null
        val result=db.withTransaction {
            val data=dao.ride(id) ?: return@withTransaction null
            if (data.ride.state != "RECORDING") return@withTransaction data.ride
            val points=data.fixes().toMutableList()
            val inserted=mutableListOf<PointRecord>()
            for (incoming in additionalFixes + listOfNotNull(fix)) {
                if ((points.lastOrNull()?.t ?: -1) >= incoming.t) continue
                val fix=incoming
                val previousGood=points.lastOrNull(::good)
                val dt=previousGood?.let { fix.t-it.t } ?: 0
                val tagged=if (good(fix) && previousGood!=null && dt in 1..Policy.MAX_GAP_MS &&
                    meters(previousGood,fix)*1000/dt > Policy.MAX_SPEED) fix.copy(quality="JUMP") else fix
                inserted += PointRecord(id,points.size,tagged.t,tagged.utc,tagged.lat,tagged.lon,tagged.accuracy,tagged.speed,tagged.speedAccuracy,tagged.quality)
                points += tagged
            }
            if(inserted.isNotEmpty()) dao.insertPoints(inserted)
            val routes=dao.routes()
            val fixed=routes.firstOrNull { it.id==fixedRouteId }
            val reverseSources=if(finish && fixed==null) routes.filter { source ->
                routes.none { it.groupKey==source.groupKey && it.direction==RouteIdentity.opposite(source.direction) }
            }.associateBy { "reverse:${it.id}" } else emptyMap()
            val groupKeys=routes.associate { it.id to it.groupKey }+reverseSources.mapValues { it.value.groupKey }
            val cached=matchingCache
            val refresh=finish || cached==null || cached.rideId!=id || cached.routes!=routes || cached.fixedId!=fixedRouteId ||
                (cached.pointCount!=points.size && elapsed-cached.snapshot.computedElapsed>=cached.snapshot.intervalMs)
            val evaluated=if(refresh) {
                val begin=SystemClock.elapsedRealtime()
                val shapes=routes.map { it.route() }+reverseSources.values.map { RouteIdentity.reversed(it.route()) }
                val report=RouteIdentity.report(points,shapes,groupKeys)
                val fixedCandidate=fixed?.let { reference -> report.candidates.firstOrNull { it.route.id==reference.id }
                    ?: Routing.evaluate(points,reference.route()) }
                val cost=SystemClock.elapsedRealtime()-begin
                val interval=(cost*4).coerceIn(5_000L,15_000L)
                MatchingCache(id,points.size,routes,fixedRouteId,
                    LiveRouteMatch(id,report,elapsed,SystemClock.elapsedRealtime(),interval,cost,fixedRouteId),
                    fixedCandidate?.takeIf { it.eligible }?.match)
            } else cached!!
            var match=if(fixed!=null) evaluated.fixedMatch else evaluated.snapshot.report.match
            reverseSources[match?.route?.id]?.let { source ->
                // A reversed reference is only evidence of the group. Save the actual return trace.
                if(match!!.progress>=Policy.ARRIVAL_PROGRESS && points.count(::good)>=4 && Analyzer.analyze(points,elapsed).distance>=Policy.MIN_ROUTE_DISTANCE_M) {
                    val variant=RouteRecord(UUID.randomUUID().toString(),RouteIdentity.name(source.name),RouteIdentity.opposite(source.direction),
                        id,RouteRecord.encode(points.filter(::good)),source.endRadius,source.startRadius,groupId=source.groupKey)
                    dao.saveRoute(variant)
                    match=Routing.follow(points,variant.route())
                } else match=null
            }
            val arrived=match!=null && Routing.arrived(points,match!!,elapsed)
            val selectedRoute=fixed?.id ?: if(finish) match?.route?.id else data.ride.routeId ?: match?.route?.id
            val partialOrDeviated=finish && selectedRoute!=null && (match==null || match!!.deviated || match!!.progress<Policy.ARRIVAL_PROGRESS)
            val ride=data.ride.copy(elapsed=elapsed.coerceAtLeast(data.ride.elapsed),lastSavedUtc=now,
                routeId=selectedRoute, direction=fixed?.direction ?: match?.route?.direction ?: if(finish) "OTHER" else data.ride.direction,
                state=if(finish) "COMPLETED" else "RECORDING",endUtc=if(finish) now else null,
                arrivalConfirmed=finish && (arrived || data.ride.arrivalSuggested) && match!=null && !match!!.deviated && match!!.progress>=Policy.ARRIVAL_PROGRESS,
                arrivalSuggested=data.ride.arrivalSuggested || arrived,
                excluded=data.ride.excluded || partialOrDeviated,
                exclusionReason=if(partialOrDeviated && !data.ride.excluded) "AUTO_ROUTE_CHECK" else data.ride.exclusionReason)
            persist(ride,points)
            computed=evaluated
            ride
        }
        // A rollback must not publish diagnostics or advance the computation throttle.
        computed?.let { matchingCache=if(finish) null else it; liveMatching.value=it.snapshot }
        result
    }
    suspend fun classify(id: String, route: RouteRecord?, direction: String, excluded: Boolean, reason: String) = mutex.withLock {
        db.withTransaction {
            dao.ride(id)?.ride?.let {
                require(it.mergedInto==null)
                dao.saveRide(it.copy(routeId=route?.id,direction=route?.direction ?: direction,excluded=excluded,exclusionReason=reason))
            }
        }
    }
    suspend fun boundaries(id: String, start: Long, end: Long) = mutex.withLock {
        val data=dao.ride(id) ?: return@withLock
        require(data.ride.state != "RECORDING" && data.ride.mergedInto==null && start >= 0 && end > start && end <= data.ride.elapsed)
        dao.saveRide(data.ride.copy(boundaryStart=start,boundaryEnd=end))
    }
    suspend fun createRoute(id: String, name: String, direction: String, startRadius: Double, endRadius: Double,
        groupId: String?=null,excluded: Boolean?=null,reason: String?=null) = mutex.withLock {
        db.withTransaction {
            validateRoute(name,direction,startRadius,endRadius)
            val data=dao.ride(id) ?: return@withTransaction
            require(data.ride.state != "RECORDING" && data.ride.mergedInto==null)
            val fixes=data.sample().points.filter(::good)
            require(fixes.size >= 4 && Analyzer.analyze(fixes,data.sample().metrics.elapsed).distance >= Policy.MIN_ROUTE_DISTANCE_M)
            val groups=routeGroups(dao.routes())
            val group=groupId?.let { key -> groups.firstOrNull { it.id==key } ?: error("Route group no longer exists") }
                ?: groups.firstOrNull { RouteIdentity.key(it.name)==RouteIdentity.key(name) }
            require(group?.direction(direction).isNullOrEmpty()) { "This direction already exists; select it from the route list" }
            val route=RouteRecord(UUID.randomUUID().toString(),group?.name ?: RouteIdentity.name(name),direction,id,RouteRecord.encode(fixes),
                startRadius,endRadius,groupId=group?.id ?: UUID.randomUUID().toString())
            dao.saveRoute(route)
            dao.saveRide(data.ride.copy(routeId=route.id,direction=direction,
                excluded=excluded ?: data.ride.excluded,exclusionReason=reason ?: data.ride.exclusionReason))
        }
    }
    suspend fun deleteRide(id: String) = mutex.withLock {
        db.withTransaction {
            val ride=dao.ride(id)?.ride ?: return@withTransaction
            require(ride.state != "RECORDING" && ride.mergedInto==null)
            // Points and segments cascade; saved routes keep their independent geometry.
            deleteWithSources(id)
        }
    }
    private suspend fun deleteWithSources(id: String) {
        val sources=dao.mergedSources(id)
        sources.forEach { deleteWithSources(it.ride.id) }
        dao.deleteRide(id)
    }
    suspend fun mergeRides(ids: List<String>): String = mutex.withLock {
        db.withTransaction {
            val routes=dao.routes()
            val visible=dao.visibleRides()
            val hidden=ids.distinct().filter { id -> visible.none { it.ride.id==id } }.mapNotNull { dao.ride(it) }
            val all=visible+hidden
            val plan=TripMerge.plan(all.map { it.mergeInput(routes) },ids)
            if(!plan.valid) throw MergeRejectedException(plan.problems)
            val sources=plan.parts.map { part -> all.first { it.ride.id==part.id } }
            val original=sources.first().ride
            val id=UUID.randomUUID().toString()
            val report=RouteIdentity.report(plan.points,routes.map { it.route() },routes.associate { it.id to it.groupKey })
            val match=report.match?.takeIf { !it.deviated && it.progress>=Policy.ARRIVAL_PROGRESS }
            val known=sources.mapNotNull { data -> routes.firstOrNull { it.id==data.ride.routeId } }.firstOrNull()
            val chosen=known ?: match?.route?.id?.let { key -> routes.firstOrNull { it.id==key } }
            val manualExclusion=sources.firstOrNull { it.ride.excluded && it.ride.exclusionReason!="AUTO_ROUTE_CHECK" }?.ride
            val needsReview=chosen!=null && (match==null || routes.firstOrNull { it.id==match.route.id }?.groupKey!=chosen.groupKey || match.route.direction!=chosen.direction)
            val ride=RideRecord(id,plan.startUtc,original.zone,state="COMPLETED",endUtc=plan.startUtc+plan.elapsed,
                elapsed=plan.elapsed,lastSavedUtc=System.currentTimeMillis(),routeId=chosen?.id,
                direction=chosen?.direction ?: plan.parts.mapNotNull { it.direction?.takeUnless { direction -> direction=="OTHER" } }.firstOrNull() ?: "OTHER",
                excluded=manualExclusion!=null || needsReview,exclusionReason=manualExclusion?.exclusionReason ?: if(needsReview) "AUTO_ROUTE_CHECK" else "",
                arrivalConfirmed=match!=null && !needsReview && sources.last().ride.arrivalConfirmed,mergedCount=plan.parts.sumOf { it.sourceCount })
            dao.saveRide(ride)
            val copied=sources.flatMapIndexed { partIndex,data ->
                val input=plan.parts[partIndex]
                val start=data.ride.boundaryStart
                val selected=data.points.sortedBy { it.sequence }.filter { it.t in start..(start+input.elapsed) }
                val firstGood=selected.indexOfFirst { good(it.fix()) }
                selected.mapIndexed { pointIndex,point ->
                    point.copy(rideId=id,t=input.startUtc-plan.startUtc+point.t-start,
                        breakBefore=point.breakBefore || (partIndex>0 && (pointIndex==0 || pointIndex==firstGood)))
                }
            }.mapIndexed { index,point -> point.copy(sequence=index) }
            dao.insertPoints(copied)
            persist(ride,plan.points)
            sources.forEach { dao.saveRide(it.ride.copy(mergedInto=id)) }
            id
        }
    }
    suspend fun undoMerge(id: String) = mutex.withLock {
        db.withTransaction {
            val ride=dao.ride(id)?.ride ?: throw MergeRejectedException(setOf(MergeProblem.MISSING))
            if(dao.active().isNotEmpty()) throw MergeRejectedException(setOf(MergeProblem.ACTIVE))
            require(ride.mergedInto==null && ride.state!="RECORDING" && ride.mergedCount>0)
            val sources=dao.mergedSources(id)
            require(sources.isNotEmpty())
            sources.forEach { dao.saveRide(it.ride.copy(mergedInto=null)) }
            dao.deleteRide(id)
        }
    }
    suspend fun editRoute(id: String, name: String, direction: String, startRadius: Double, endRadius: Double) = mutex.withLock {
        db.withTransaction {
            validateRoute(name,direction,startRadius,endRadius)
            require(dao.active().none { it.ride.routeId==id })
            val route=dao.routes().firstOrNull { it.id==id } ?: return@withTransaction
            val siblings=dao.routes().filter { it.groupKey==route.groupKey }
            require(dao.active().none { it.ride.routeId in siblings.map { variant -> variant.id } })
            require(direction==route.direction || siblings.none { it.id!=id && it.direction==direction })
            require(dao.routes().none { it.groupKey!=route.groupKey && RouteIdentity.key(it.name)==RouteIdentity.key(name) })
            siblings.forEach { variant ->
                dao.saveRoute(if(variant.id==id) variant.copy(name=RouteIdentity.name(name),direction=direction,startRadius=startRadius,endRadius=endRadius,version=variant.version+1)
                    else variant.copy(name=RouteIdentity.name(name),version=variant.version+1))
            }
            dao.updateRouteDirection(id,direction)
        }
    }
    suspend fun updatePlan(id: String,days: Int,minute: Int?,mode: String="CUSTOM") = mutex.withLock {
        require(days in 0..127 && (minute==null || minute in 0..1439) && mode in listOf("WORKDAYS","CUSTOM"))
        db.withTransaction {
            val routes=dao.routes()
            val route=routes.firstOrNull { it.id==id } ?: throw RouteUnavailableException()
            // A plan belongs to one logical route direction, including legacy references.
            routes.filter { it.groupKey==route.groupKey && it.direction==route.direction }.forEach {
                dao.saveRoute(it.copy(planDays=days,planMinute=minute,planMode=mode))
            }
        }
    }
    suspend fun deleteRoute(id: String) = mutex.withLock {
        db.withTransaction {
            require(dao.active().none { it.ride.routeId==id })
            // Preserve raw rides and their direction, but omit them from route baselines.
            dao.unlinkRoute(id)
            dao.deleteRoute(id)
        }
    }
    suspend fun deleteRouteGroup(groupId: String) = mutex.withLock {
        db.withTransaction {
            val variants=dao.routes().filter { it.groupKey==groupId }
            require(dao.active().none { it.ride.routeId in variants.map { route -> route.id } })
            variants.forEach { dao.unlinkRoute(it.id); dao.deleteRoute(it.id) }
        }
    }
    suspend fun unlinkRide(id: String,groupId: String) = mutex.withLock {
        db.withTransaction {
            val ride=dao.ride(id)?.ride ?: return@withTransaction
            require(ride.state!="RECORDING" && ride.mergedInto==null)
            val routeIds=dao.routes().filter { it.groupKey==groupId }.map { it.id }
            // Ignore a stale menu if the ride has since been assigned to another route.
            if(ride.routeId in routeIds) dao.saveRide(ride.copy(routeId=null))
        }
    }
    suspend fun discardActive(id: String) = mutex.withLock {
        db.withTransaction { dao.discardActive(id) }
    }
    private fun validateRoute(name: String, direction: String, startRadius: Double, endRadius: Double) {
        require(name.isNotBlank() && direction in listOf("WORK","HOME"))
        require(startRadius in 20.0..500.0 && endRadius in 20.0..500.0)
    }
}
