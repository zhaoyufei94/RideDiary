package com.ridediary.core

data class MergeInput(val id: String,val startUtc: Long,val elapsed: Long,val state: String,val points: List<Fix>,
    val group: String?=null,val direction: String?=null,val hidden: Boolean=false,val clockValid: Boolean=true,val sourceCount: Int=1) {
    val endUtc get()=startUtc+elapsed
}
enum class MergeProblem { COUNT, MISSING, ACTIVE, HIDDEN, TIME, OVERLAP, GAP, POSITION, DISTANCE, SPEED, ROUTE, SKIPPED }
data class MergeJoin(val previousId: String,val nextId: String,val gap: Long,val distance: Double?)
data class MergePlan(val parts: List<MergeInput>,val joins: List<MergeJoin>,val problems: Set<MergeProblem>,val points: List<Fix>,val elapsed: Long) {
    val valid get()=problems.isEmpty()
    val startUtc get()=parts.firstOrNull()?.startUtc ?: 0
    val gap get()=joins.sumOf { it.gap.coerceAtLeast(0) }
}

/** Merge eligibility and the combined time axis are independent of storage and UI. */
object TripMerge {
    const val MAX_PARTS=10
    const val MAX_GAP_MS=30*60_000L
    const val MAX_DISTANCE_M=300.0
    const val ENDPOINT_AGE_MS=2*60_000L
    const val MAX_TOTAL_MS=12*3_600_000L

    fun plan(all: List<MergeInput>,ids: List<String>): MergePlan {
        val selected=ids.toSet()
        val problems=linkedSetOf<MergeProblem>()
        if(ids.size !in 2..MAX_PARTS || ids.size!=selected.size) problems+=MergeProblem.COUNT
        val parts=all.filter { it.id in selected }.sortedWith(compareBy<MergeInput> { it.startUtc }.thenBy { it.id })
        if(parts.sumOf { it.sourceCount.coerceAtLeast(1) }>MAX_PARTS) problems+=MergeProblem.COUNT
        if(parts.size!=selected.size) problems+=MergeProblem.MISSING
        if(all.any { !it.hidden && it.state=="RECORDING" }) problems+=MergeProblem.ACTIVE
        if(parts.any { it.hidden }) problems+=MergeProblem.HIDDEN
        if(parts.any { it.state !in listOf("COMPLETED","INTERRUPTED") || !it.clockValid || it.elapsed<=0 ||
                it.elapsed>MAX_TOTAL_MS || it.startUtc>Long.MAX_VALUE-it.elapsed }) problems+=MergeProblem.TIME
        if(parts.mapNotNull { it.group }.distinct().size>1 ||
            parts.mapNotNull { it.direction?.takeUnless { value -> value=="OTHER" } }.distinct().size>1) problems+=MergeProblem.ROUTE
        val joins=parts.zipWithNext().map { (a,b) ->
            val gap=b.startUtc-a.endUtc
            if(gap<0) problems+=MergeProblem.OVERLAP
            if(gap>MAX_GAP_MS) problems+=MergeProblem.GAP
            val end=a.points.lastOrNull(::good); val start=b.points.firstOrNull(::good)
            val distance=if(end!=null && start!=null) meters(end,start) else null
            if(end==null || start==null || a.elapsed-end.t !in 0..ENDPOINT_AGE_MS || start.t !in 0..ENDPOINT_AGE_MS) problems+=MergeProblem.POSITION
            if(distance!=null) {
                if(distance>MAX_DISTANCE_M) problems+=MergeProblem.DISTANCE
                val time=(b.startUtc+start!!.t)-(a.startUtc+end!!.t)
                if((time<=0 && distance>maxOf(end.accuracy,start.accuracy)) || (time>0 && distance*1000/time>Policy.MAX_SPEED)) problems+=MergeProblem.SPEED
            }
            MergeJoin(a.id,b.id,gap,distance)
        }
        val start=parts.firstOrNull()?.startUtc ?: 0
        val elapsed=(parts.lastOrNull()?.endUtc ?: start)-start
        if(elapsed !in 1..MAX_TOTAL_MS && parts.isNotEmpty()) problems+=MergeProblem.TIME
        if(parts.size>=2 && all.any { it.id !in selected && !it.hidden && it.state!="RECORDING" && it.endUtc>start && it.startUtc<start+elapsed }) problems+=MergeProblem.SKIPPED
        if(parts.any { part -> part.points.count(::good)<2 || part.points.any { it.t !in 0..part.elapsed } ||
                part.points.zipWithNext().any { (a,b)->a.t>b.t } }) problems+=MergeProblem.POSITION
        val points=if(problems.isEmpty()) parts.flatMapIndexed { index,part ->
            val firstGood=part.points.indexOfFirst(::good)
            part.points.mapIndexed { pointIndex,point ->
                point.copy(t=part.startUtc-start+point.t,breakBefore=point.breakBefore || (index>0 && (pointIndex==0 || pointIndex==firstGood)))
            }
        } else emptyList()
        return MergePlan(parts,joins,problems,points,elapsed.coerceAtLeast(0))
    }
}
