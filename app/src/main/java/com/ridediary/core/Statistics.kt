package com.ridediary.core

import java.time.*
import kotlin.math.*

fun median(values: List<Long>): Long? {
    if (values.isEmpty()) return null
    val sorted=values.sorted(); val middle=sorted.size/2
    return if (sorted.size%2==1) sorted[middle] else sorted[middle-1]+(sorted[middle]-sorted[middle-1])/2
}
object Statistics {
    fun valid(s: Sample) = s.state == "COMPLETED" && !s.excluded && s.routeId != null && s.metrics.complete && s.metrics.distance >= Policy.MIN_ROUTE_DISTANCE_M
    fun baseline(current: Sample, all: List<Sample>) = all.filter {
        valid(it) && it.id != current.id && it.startUtc < current.startUtc && it.routeId == current.routeId && it.direction == current.direction
    }.sortedByDescending { it.startUtc }.take(Policy.HISTORY_LIMIT)
    data class Comparison(val n: Int, val total: Long?, val moving: Long?, val waiting: Long?)
    fun compare(current: Sample, all: List<Sample>): Comparison {
        val samples=baseline(current,all)
        return Comparison(samples.size,median(samples.map { it.metrics.elapsed }),median(samples.map { it.metrics.moving }),median(samples.map { it.metrics.waiting }))
    }
    data class Estimate(val n: Int, val low: Long, val middle: Long, val high: Long, val elapsedDifference: Long)
    fun estimate(current: Sample, all: List<Sample>, match: Match): Estimate? {
        if (match.deviated) return null
        val positions=baseline(current,all).mapNotNull { sample ->
            var along=0.0
            sample.points.filter(::good).firstOrNull { f ->
                val p=Routing.project(f,match.route,along,250.0)
                if (p != null && p.error < Policy.CORRIDOR_M) along=max(along,p.along)
                along >= match.along
            }?.let { it.t.coerceAtMost(sample.metrics.elapsed) to sample.metrics.elapsed }
        }
        if (positions.size < Policy.MIN_HISTORY) return null
        val remaining=positions.map { (t,total) -> total-t }.sorted()
        return Estimate(positions.size,remaining[(remaining.size-1)/4],median(remaining)!!,remaining[(remaining.size-1)*3/4],
            current.metrics.elapsed-median(positions.map { it.first })!!)
    }
    fun date(s: Sample): LocalDate = Instant.ofEpochMilli(s.startUtc).atZone(ZoneId.of(s.zone)).toLocalDate()
    fun month(all: List<Sample>, month: YearMonth, routeId: String? = null, direction: String? = null) = all.filter {
        valid(it) && YearMonth.from(date(it)) == month && (routeId == null || routeId == it.routeId) && (direction == null || direction == it.direction)
    }
    fun days(samples: List<Sample>) = samples.map(::date).distinct().size
    /** Unwrap clock minutes around a circular mean, then average/min/max on that timeline. */
    fun clocks(samples: List<Sample>, arrival: Boolean): Triple<Int,Int,Int>? {
        if (samples.isEmpty()) return null
        val minutes=samples.map { Instant.ofEpochMilli(if (arrival) it.endUtc else it.startUtc).atZone(ZoneId.of(it.zone)).let { t -> t.hour*60+t.minute } }
        val x=minutes.sumOf { cos(it*2*PI/1440) }; val y=minutes.sumOf { sin(it*2*PI/1440) }
        val anchor=if (hypot(x,y)<0.001) minutes.first().toDouble() else atan2(y,x)*1440/(2*PI)
        val unfolded=minutes.map { anchor+((it-anchor+2160)%1440)-720 }
        fun wrap(v: Double)=((v.roundToInt()%1440)+1440)%1440
        return Triple(wrap(unfolded.average()),wrap(unfolded.min()),wrap(unfolded.max()))
    }
}
