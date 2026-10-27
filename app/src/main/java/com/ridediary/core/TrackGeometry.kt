package com.ridediary.core

import kotlin.math.roundToInt

data class TrackEdge(val from: Fix,val to: Fix,val color: Int,val speedKmh: Double?)
data class TrackStop(val point: Fix,val from: Long,val to: Long) { val duration get()=to-from }
data class TrackGeometry(val points: List<Fix>,val edges: List<TrackEdge>,val stops: List<TrackStop>,val speedColored: Boolean)

/** Presentation only. Never joins across invalid fixes or edits the recorded analysis. */
object TrackPresentation {
    const val STOP_LABEL_MS=30_000L
    const val UNKNOWN_COLOR=0xff87938f.toInt()
    const val ROUTE_COLOR=0xff315d49.toInt()
    private val palette=listOf(0.0 to 0xffdd493d.toInt(),20.0 to 0xffe39b23.toInt(),40.0 to 0xff58a24c.toInt(),60.0 to 0xff147347.toInt())

    fun speedColor(kmh: Double?): Int {
        if(kmh==null || !kmh.isFinite() || kmh<0) return UNKNOWN_COLOR
        val value=kmh.coerceIn(0.0,60.0)
        val upper=palette.indexOfFirst { it.first>=value }.coerceAtLeast(0)
        if(upper==0) return palette[0].second
        val (lo,a)=palette[upper-1]; val (hi,b)=palette[upper]
        val fraction=(value-lo)/(hi-lo)
        fun channel(shift: Int)=(((a ushr shift) and 255)*(1-fraction)+((b ushr shift) and 255)*fraction).roundToInt()
        return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    fun build(points: List<Fix>,metrics: Metrics?=null): TrackGeometry {
        val edges=ArrayList<TrackEdge>(points.size)
        val disconnected=HashSet<Fix>()
        var segmentIndex=0
        for(index in 1 until points.size) {
            val a=points[index-1]; val b=points[index]; val dt=b.t-a.t
            if(!good(a) || !good(b) || dt<=0) continue
            if(b.breakBefore) { disconnected+=a; disconnected+=b; continue }
            val distance=meters(a,b)
            if(distance/(dt/1000.0)>Policy.MAX_SPEED) continue
            if(dt>Policy.MAX_GAP_MS) { disconnected+=a; disconnected+=b; continue }
            val segments=metrics?.segments.orEmpty()
            while(segmentIndex<segments.lastIndex && segments[segmentIndex].to<b.t) segmentIndex++
            val motion=segments.getOrNull(segmentIndex)?.takeIf { a.t>=it.from && b.t<=it.to }?.motion
            val observed=b.speed?.takeIf { it.isFinite() && it in 0.0..Policy.MAX_SPEED && (b.speedAccuracy==null || b.speedAccuracy<=2.0) }
                ?: (distance/(dt/1000.0)).takeIf { distance>(a.accuracy+b.accuracy)/2 }
            val kmh=when(motion) { Motion.WAITING->0.0; Motion.MOVING->observed?.times(3.6); else->null }
            edges+=TrackEdge(a,b,if(metrics==null) ROUTE_COLOR else speedColor(kmh),kmh)
        }
        val valid=points.filter(::good)
        // Select only fixes within the confirmed stop, and use medians to resist GNSS drift.
        val stops=metrics?.segments.orEmpty().filter { it.motion==Motion.WAITING && it.duration>=STOP_LABEL_MS }.mapNotNull { segment ->
            val fixes=valid.filter { it.t>segment.from && it.t<=segment.to }
            if(fixes.isEmpty()) null else {
                val middle=fixes.size/2
                TrackStop(fixes[middle].copy(lat=fixes.map { it.lat }.sorted()[middle],lon=fixes.map { it.lon }.sorted()[middle]),segment.from,segment.to)
            }
        }
        // An isolated jump must not enlarge the viewport until the real track becomes tiny.
        val supported=edges.flatMap { listOf(it.from,it.to) }.toHashSet()
        supported+=disconnected
        val displayed=if(supported.isEmpty()) valid.take(1) else valid.filter { it in supported }
        return TrackGeometry(displayed,edges,stops,metrics!=null)
    }
}
