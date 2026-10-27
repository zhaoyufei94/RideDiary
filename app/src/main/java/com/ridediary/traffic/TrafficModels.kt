package com.ridediary.traffic

import com.ridediary.core.Fix
import kotlin.math.*

data class TrafficConfig(val enabled: Boolean=false,val apiKey: String="") {
    val ready get()=Regex("[A-Za-z0-9_-]{8,128}").matches(apiKey.trim())
    override fun toString()="TrafficConfig(enabled=$enabled, credentials=<redacted>)"
}
enum class TrafficFailure { CONFIGURATION, AUTH, NETWORK, DATA, ROUTE_MISMATCH, ROUTE_COMPLEX }
data class TrafficBand(val status: Int,val distance: Double)
data class TrafficRouteComparison(val savedLength: Double,val plannedLength: Double,val startOffset: Double,val endOffset: Double,
    val savedCoverage50: Double,val plannedCoverage50: Double,val savedCoverage100: Double,val plannedCoverage100: Double,val reversed: Boolean) {
    val lengthRatio get()=plannedLength/savedLength
    val similarity get()=minOf(savedCoverage100,plannedCoverage100)
    val strictMatch get()=startOffset<=100 && endOffset<=100 && lengthRatio in .8..1.25 && minOf(savedCoverage50,plannedCoverage50)>=.9
    // Traffic is a same-origin/destination driving reference, not navigation on an exact saved track.
    val usableReference get()=!reversed && startOffset<=1000 && endOffset<=1000 && lengthRatio in .3..3.5
}
data class TrafficObservation(val departure: Long,val duration: Long?,val bands: List<TrafficBand>,val comparison: TrafficRouteComparison?=null) {
    private val total get()=bands.sumOf { it.distance }
    val coverage get()=if(total>0) bands.filter { it.status in 1..4 }.sumOf { it.distance }/total else 0.0
    val congestion get()=if(coverage>0) bands.filter { it.status in 3..4 }.sumOf { it.distance }/total else 0.0
    val status: Int get() {
        if(coverage<.8) return 0
        val known=bands.filter { it.status in 1..4 }.sumOf { it.distance }
        val severity=bands.filter { it.status in 1..4 }.sumOf { it.status*it.distance }/known
        return when { severity>=3.5 -> 4; severity>=2.5 -> 3; severity>=1.5 -> 2; else -> 1 }
    }
}
data class TrafficSnapshot(val fetchedAt: Long,val current: TrafficObservation,val trend: Int?=null)
data class TrafficResult(val snapshot: TrafficSnapshot?=null,val failure: TrafficFailure?=null,val comparisons: List<TrafficRouteComparison> = emptyList())
object TrafficPolicy {
    const val HOUR=3_600_000L
    const val FRESH_MS=20*60_000L
    fun eligible(departure: Long,now: Long)=now in (departure-2*HOUR)..(departure+HOUR)
    fun fresh(fetched: Long,now: Long)=now-fetched in 0..FRESH_MS
    fun trend(previous: TrafficSnapshot?,current: TrafficObservation,now: Long): Int? {
        if(previous==null || now-previous.fetchedAt !in 5*60_000L..45*60_000L || previous.current.coverage<.8 || current.coverage<.8) return null
        val old=previous.current.comparison; val new=current.comparison
        if(old!=null && new!=null && (abs(old.plannedLength-new.plannedLength)/old.plannedLength>.1 || abs(old.similarity-new.similarity)>.1)) return null
        val change=current.congestion-previous.current.congestion
        return when { change>.05 -> 1; change<-.05 -> -1; else -> 0 }
    }
}

/** Geometry is compared only after both shapes have been converted into BD09LL. */
object TrafficGeometry {
    private fun distance(a: Fix,b: Fix)=com.ridediary.core.meters(a,b)
    private fun error(p: Fix,a: Fix,b: Fix): Double {
        val scale=cos(Math.toRadians(p.lat))
        val x=(b.lon-a.lon)*111_195*scale; val y=(b.lat-a.lat)*111_195
        val px=(p.lon-a.lon)*111_195*scale; val py=(p.lat-a.lat)*111_195
        val t=if(x*x+y*y>0) ((px*x+py*y)/(x*x+y*y)).coerceIn(0.0,1.0) else 0.0
        return hypot(px-t*x,py-t*y)
    }
    fun simplify(points: List<Fix>,tolerance: Double=15.0): List<Fix> {
        if(points.size<=2) return points
        val keep=sortedSetOf(0,points.lastIndex)
        val pending=ArrayDeque<Pair<Int,Int>>().apply { add(0 to points.lastIndex) }
        while(pending.isNotEmpty()) {
            val (a,b)=pending.removeLast()
            val far=(a+1 until b).maxByOrNull { error(points[it],points[a],points[b]) } ?: continue
            if(error(points[far],points[a],points[b])>tolerance) {
                keep+=far; pending.add(a to far); pending.add(far to b)
            }
        }
        return keep.map { points[it] }
    }
    fun anchors(points: List<Fix>,maximum: Int=20): List<Fix> {
        if(points.size<=maximum) return points
        return (0 until maximum).map { points[(it.toDouble()*points.lastIndex/(maximum-1)).roundToInt()] }
    }
    fun sameRoute(reference: List<Fix>,candidate: List<Fix>)=compare(reference,candidate)?.strictMatch==true
    fun compare(reference: List<Fix>,candidate: List<Fix>): TrafficRouteComparison? {
        if(reference.size<2 || candidate.size<2) return null
        val referenceLength=reference.zipWithNext().sumOf { (a,b)->distance(a,b) }
        val candidateLength=candidate.zipWithNext().sumOf { (a,b)->distance(a,b) }
        if(!referenceLength.isFinite() || !candidateLength.isFinite() || referenceLength<150 || candidateLength<100) return null
        fun coverage(source: List<Fix>,target: List<Fix>): Pair<Double,Double> {
            var matched50=0.0; var matched100=0.0; var total=0.0
            val targetEdges=target.zipWithNext()
            source.zipWithNext().forEach { (a,b) ->
                val length=distance(a,b)
                val count=ceil(length/25).toInt().coerceAtLeast(1)
                repeat(count) { index ->
                    val t=(index+.5)/count
                    val point=a.copy(lat=a.lat+(b.lat-a.lat)*t,lon=a.lon+(b.lon-a.lon)*t)
                    var nearest=Double.POSITIVE_INFINITY
                    targetEdges.forEach { (c,d) ->
                        // Reject the opposite travel direction on nearby divided roads.
                        if((b.lat-a.lat)*(d.lat-c.lat)+(b.lon-a.lon)*(d.lon-c.lon)>0) nearest=minOf(nearest,error(point,c,d))
                    }
                    total+=length/count
                    if(nearest<=50) matched50+=length/count
                    if(nearest<=100) matched100+=length/count
                }
            }
            return if(total>0) matched50/total to matched100/total else 0.0 to 0.0
        }
        val saved=coverage(reference,candidate); val planned=coverage(candidate,reference)
        val start=distance(reference.first(),candidate.first()); val end=distance(reference.last(),candidate.last())
        val reverse=distance(reference.first(),candidate.last())+distance(reference.last(),candidate.first())
        return TrafficRouteComparison(referenceLength,candidateLength,start,end,saved.first,planned.first,saved.second,planned.second,start+end>reverse+50)
    }
}
