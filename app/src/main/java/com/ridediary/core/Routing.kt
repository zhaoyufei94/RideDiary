package com.ridediary.core

import kotlin.math.*

object Routing {
    data class Projection(val along: Double, val error: Double)
    fun project(f: Fix, route: RouteShape, previous: Double = 0.0, forward: Double = 180.0): Projection? {
        var accumulated = 0.0
        var best: Projection? = null
        var score = Double.POSITIVE_INFINITY
        for ((a,b) in route.points.zipWithNext()) {
            val len = meters(a,b)
            if (len < 0.01) continue
            val cosLat = cos(Math.toRadians(a.lat))
            val bx=(b.lon-a.lon)*111_195*cosLat; val by=(b.lat-a.lat)*111_195
            val fx=(f.lon-a.lon)*111_195*cosLat; val fy=(f.lat-a.lat)*111_195
            val u=((fx*bx+fy*by)/(len*len)).coerceIn(0.0,1.0)
            val along=accumulated+u*len
            val error=hypot(fx-u*bx,fy-u*by)
            // Ordered local window prevents crossing/parallel branch shortcuts.
            if (along >= previous-60 && along <= previous+forward) {
                val candidateScore=error + abs(along-previous)*0.015
                if (candidateScore < score) { best=Projection(along,error); score=candidateScore }
            }
            accumulated += len
        }
        return best
    }
    fun follow(points: List<Fix>, route: RouteShape): Match? {
        val usable=points.filter(::good)
        if (usable.size < 4 || route.points.size < 2 || route.length < Policy.MIN_ROUTE_DISTANCE_M || meters(usable.first(),route.points.first()) > route.startRadius) return null
        var along=0.0; var inliers=0; var error=0.0; var reversed=false
        var prev=usable.first()
        for ((index,f) in usable.withIndex()) {
            val dt=(f.t-prev.t).coerceAtLeast(0)/1000.0
            // Never search a distant later branch after a data gap.
            val displacementWindow=(meters(prev,f)*1.7+50).coerceIn(80.0,250.0)
            fun usableSpeed(point: Fix)=point.speed?.takeIf { it.isFinite() && it>=0 } ?: 0.0
            val reportedSpeed=max(usableSpeed(f),usableSpeed(prev)).coerceIn(4.0,Policy.MAX_SPEED)
            val timedWindow=(dt.coerceAtMost(15.0)*reportedSpeed*1.8+40).coerceIn(60.0,250.0)
            val window=if(index==0) route.startRadius.coerceIn(80.0,200.0)+20 else min(displacementWindow,timedWindow)
            val p=project(f,route,along,window)
            if (p != null && p.error <= Policy.CORRIDOR_M && (dt == 0.0 || meters(prev,f)/dt <= Policy.MAX_SPEED)) {
                if (p.along < along-30) reversed=true
                if (!reversed) along=max(along,p.along)
                inliers++
            }
            error=p?.error ?: Double.POSITIVE_INFINITY
            prev=f
        }
        if (along < Policy.MIN_MATCH_DISTANCE_M || inliers.toDouble()/usable.size < Policy.MIN_INLIER_SHARE) return null
        return Match(route,along,error,reversed || error > Policy.CORRIDOR_M)
    }
    fun recognize(points: List<Fix>, routes: List<RouteShape>): Match? {
        val candidates=routes.mapNotNull { follow(points,it) }.filterNot { it.deviated }.sortedBy { it.crossTrack }
        if (candidates.size > 1 && candidates[1].crossTrack-candidates[0].crossTrack < Policy.MATCH_MARGIN_M) return null
        return candidates.firstOrNull()
    }
    fun arrived(points: List<Fix>, match: Match, elapsed: Long): Boolean {
        if (match.deviated || match.progress < Policy.ARRIVAL_PROGRESS) return false
        val end=match.route.points.last()
        val last=points.lastOrNull() ?: return false
        if(elapsed-last.t !in 0..3_000) return false
        var newer=last
        var count=0
        for(f in points.asReversed()) {
            if(!good(f) || meters(f,end)>match.route.endRadius || f.speed==null || !f.speed.isFinite() ||
                f.speed<0 || f.speed>=Policy.STOP_ENTER || (f.speedAccuracy!=null && f.speedAccuracy>2.0) || newer.t-f.t>Policy.MAX_GAP_MS) break
            newer=f; count++
            if(count>=3 && last.t-f.t>=Policy.ARRIVAL_DWELL_MS) return true
        }
        return false
    }
}
