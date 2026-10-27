package com.ridediary.data

import com.ridediary.core.*
import kotlin.math.abs

class MergeRejectedException(val problems: Set<MergeProblem>): IllegalStateException()

/** For interrupted recordings, recovery-only trailing time belongs to the unrecorded join. */
fun RideData.mergeInput(routes: List<RouteRecord>): MergeInput {
    val start=ride.boundaryStart
    val end=ride.boundaryEnd ?: if(ride.state=="INTERRUPTED")
        (points.maxOfOrNull { it.t } ?: ride.elapsed).coerceAtMost(ride.elapsed) else ride.elapsed
    val reference=routes.firstOrNull { it.id==ride.routeId }
    val clockValid=start>=0 && end>start && end<=ride.elapsed &&
        (ride.state!="COMPLETED" || ride.boundaryEnd!=null || ride.endUtc==null || abs(ride.endUtc-(ride.startUtc+ride.elapsed))<=60_000)
    return MergeInput(ride.id,ride.startUtc+start,(end-start).coerceAtLeast(0),ride.state,
        fixes().filter { it.t in start..end }.map { it.copy(t=it.t-start) },reference?.groupKey,reference?.direction ?: ride.direction,
        hidden=ride.mergedInto!=null,clockValid=clockValid,sourceCount=ride.mergedCount.coerceAtLeast(1))
}
