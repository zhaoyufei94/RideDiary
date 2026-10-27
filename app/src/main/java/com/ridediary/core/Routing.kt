package com.ridediary.core

import kotlin.math.*

object Routing {
    data class Projection(val along: Double, val error: Double)
    internal class Index(points: List<Fix>) {
        class Edge(val a: Fix,val length: Double,val start: Double) {
            val end=start+length
            val cosLat=cos(Math.toRadians(a.lat))
            var bx=0.0
            var by=0.0
        }
        val edges: List<Edge>
        val length: Double
        init {
            val result=ArrayList<Edge>(); var along=0.0; var total=0.0
            for(i in 1 until points.size) {
                val a=points[i-1]; val b=points[i]; val len=meters(a,b)
                if(b.breakBefore) continue
                total+=len
                if(len<0.01) continue
                result+=Edge(a,len,along).apply {
                    bx=(b.lon-a.lon)*111_195*cosLat; by=(b.lat-a.lat)*111_195
                }
                along+=len
            }
            edges=result; length=total
        }
    }
    fun project(f: Fix, route: RouteShape, previous: Double = 0.0, forward: Double = 180.0): Projection? =
        projectRange(f,route,previous-60,previous+forward,previous)

    private fun projectRange(f: Fix,route: RouteShape,lower: Double,upper: Double,anchor: Double): Projection? {
        val edges=route.projectionIndex.edges
        var lo=0; var hi=edges.size
        while(lo<hi) { val mid=(lo+hi)/2; if(edges[mid].end<lower) lo=mid+1 else hi=mid }
        var best: Projection?=null; var score=Double.POSITIVE_INFINITY
        for(i in lo until edges.size) {
            val e=edges[i]
            if(e.start>upper) break
            val fx=(f.lon-e.a.lon)*111_195*e.cosLat; val fy=(f.lat-e.a.lat)*111_195
            val u=((fx*e.bx+fy*e.by)/(e.length*e.length)).coerceIn(0.0,1.0)
            val along=e.start+u*e.length
            if(along in lower..upper) {
                val error=hypot(fx-u*e.bx,fy-u*e.by)
                val candidateScore=error+abs(along-anchor)*0.015
                if(candidateScore<score) { best=Projection(along,error); score=candidateScore }
            }
        }
        return best
    }
    enum class State { LEARNING, CONFIRMED, RECOVERING, DEVIATED }
    data class Candidate(val route: RouteShape,val along: Double,val crossTrack: Double,
        val overlap: Double,val matchedDistance: Double,val eligible: Boolean,val state: State) {
        val match get()=Match(route,along,crossTrack,state==State.RECOVERING || state==State.DEVIATED)
    }

    /** Ordered overlap, with a bounded three-fix reacquisition after losing the local window. */
    fun evaluate(points: List<Fix>,route: RouteShape): Candidate? {
        val usable=points.filter(::good)
        if(usable.isEmpty() || route.points.size<2 || route.length<Policy.MIN_ROUTE_DISTANCE_M ||
            meters(usable.first(),route.points.first())>route.startRadius) return null
        var along=0.0; var inliers=0; var matchedDistance=0.0; var error=Double.POSITIVE_INFINITY
        var prev=usable.first(); var lastAccepted: Fix?=null; var travelSinceLock=0.0
        var failures=0; var recovering=false
        val recovery=ArrayList<Pair<Fix,Projection>>()
        for((index,f) in usable.withIndex()) {
            val dt=f.t-prev.t
            val distance=meters(prev,f)
            val continuous=index==0 || (!f.breakBefore && dt in 1..Policy.MAX_GAP_MS && distance*1000/dt<=Policy.MAX_SPEED)
            if(index>0 && continuous) travelSinceLock+=distance
            fun speed(p: Fix)=p.speed?.takeIf { it.isFinite() && it>=0 } ?: 0.0
            val displacement=(distance*1.7+50).coerceIn(80.0,250.0)
            val timed=((dt.coerceAtLeast(0)/1000.0).coerceAtMost(15.0)*max(speed(f),speed(prev)).coerceIn(4.0,Policy.MAX_SPEED)*1.8+40).coerceIn(60.0,250.0)
            val window=if(index==0) route.startRadius.coerceIn(80.0,200.0)+20 else min(displacement,timed)
            val local=project(f,route,along,window)
            val accepted=failures<3 && continuous && local!=null && local.error<=Policy.CORRIDOR_M && local.along>=along-30
            if(accepted) {
                val last=lastAccepted
                if(last!=null && last==prev && index>0) matchedDistance+=distance
                along=max(along,local!!.along); error=local.error; inliers++
                lastAccepted=f; travelSinceLock=0.0; failures=0; recovering=false; recovery.clear()
            } else {
                failures++; recovering=true
                // No travel is inferred across a GNSS gap. The expanding window is capped to
                // prevent shortcuts to distant later branches at crossings or parallel roads.
                val budget=(travelSinceLock*2+150).coerceIn(250.0,1200.0)
                val wide=if(continuous) projectRange(f,route,(along-30).coerceAtLeast(0.0),along+budget,along) else null
                error=wide?.error ?: local?.error ?: Double.POSITIVE_INFINITY
                if(wide!=null && wide.error<=Policy.CORRIDOR_M && wide.along>=along-10) {
                    val last=recovery.lastOrNull()
                    if(last!=null && (f.t-last.first.t !in 1..Policy.MAX_GAP_MS ||
                        wide.along<last.second.along-10 ||
                        wide.along-last.second.along>meters(last.first,f)*2.5+60)) recovery.clear()
                    recovery+=f to wide
                    if(recovery.size>=3 && meters(recovery.first().first,f)>=15 &&
                        wide.along-recovery.first().second.along>=10) {
                        inliers+=recovery.size
                        matchedDistance+=recovery.zipWithNext().sumOf { (a,b)->meters(a.first,b.first) }
                        along=max(along,wide.along); lastAccepted=f; error=wide.error
                        travelSinceLock=0.0; failures=0; recovering=false; recovery.clear()
                    }
                } else recovery.clear()
            }
            prev=f
        }
        val overlap=inliers.toDouble()/usable.size
        val eligible=usable.size>=4 && along>=Policy.MIN_MATCH_DISTANCE_M &&
            matchedDistance>=Policy.MIN_MATCH_DISTANCE_M && overlap>=Policy.MIN_INLIER_SHARE
        val state=when {
            failures>=3 -> State.DEVIATED
            recovering -> State.RECOVERING
            eligible -> State.CONFIRMED
            else -> State.LEARNING
        }
        return Candidate(route,along,error,overlap,matchedDistance,eligible,state)
    }
    fun follow(points: List<Fix>, route: RouteShape): Match? = evaluate(points,route)?.takeIf { it.eligible }?.match
    fun recognize(points: List<Fix>,routes: List<RouteShape>): Match? =
        RouteIdentity.report(points,routes,routes.associate { it.id to it.id }).match
    fun arrived(points: List<Fix>, match: Match, elapsed: Long): Boolean {
        if (match.deviated || match.progress < Policy.ARRIVAL_PROGRESS) return false
        val end=match.route.points.last()
        val last=points.lastOrNull() ?: return false
        if(elapsed-last.t !in 0..3_000) return false
        var newer=last
        var count=0
        for(f in points.asReversed()) {
            if(newer!=f && newer.breakBefore) break
            if(!good(f) || meters(f,end)>match.route.endRadius || f.speed==null || !f.speed.isFinite() ||
                f.speed<0 || f.speed>=Policy.STOP_ENTER || (f.speedAccuracy!=null && f.speedAccuracy>2.0) || newer.t-f.t>Policy.MAX_GAP_MS) break
            newer=f; count++
            if(count>=3 && last.t-f.t>=Policy.ARRIVAL_DWELL_MS) return true
        }
        return false
    }
}
