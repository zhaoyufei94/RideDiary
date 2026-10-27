package com.ridediary.core

import kotlin.math.*

object Policy {
    const val VERSION = "1.0"
    const val MAX_ACCURACY = 35.0
    const val MAX_GAP_MS = 15_000L
    const val MAX_SPEED = 55.0
    const val STOP_ENTER = 0.7
    const val STOP_EXIT = 1.4
    const val STOP_CONFIRM_MS = 12_000L
    const val ARRIVAL_DWELL_MS = 30_000L
    const val CORRIDOR_M = 25.0
    const val HISTORY_LIMIT = 20
    const val MIN_HISTORY = 3
    const val MIN_ROUTE_DISTANCE_M = 150.0
    const val MIN_MATCH_DISTANCE_M = 120.0
    const val MIN_INLIER_SHARE = 0.75
    const val MATCH_MARGIN_M = 10.0
    const val ARRIVAL_PROGRESS = 0.92
    const val MAX_UNKNOWN_SHARE = 0.2
}

data class Fix(val t: Long, val lat: Double, val lon: Double, val accuracy: Double = 5.0,
               val speed: Double? = null, val utc: Long = 0, val quality: String = "OK",
               val speedAccuracy: Double? = null,val breakBefore: Boolean = false)
enum class Motion { MOVING, WAITING, UNKNOWN }
data class Segment(val from: Long, val to: Long, val motion: Motion, val distance: Double = 0.0) {
    val duration get() = to - from
}
data class Metrics(val elapsed: Long, val distance: Double, val segments: List<Segment>) {
    fun time(kind: Motion) = segments.filter { it.motion == kind }.sumOf { it.duration }
    val moving get() = time(Motion.MOVING)
    val waiting get() = time(Motion.WAITING)
    val unknown get() = time(Motion.UNKNOWN)
    val stops get() = segments.count { it.motion == Motion.WAITING }
    val longestWait get() = segments.filter { it.motion == Motion.WAITING }.maxOfOrNull { it.duration } ?: 0
    val averageWait get() = if (stops == 0) 0L else waiting / stops
    val complete get() = elapsed > 0 && unknown.toDouble() / elapsed <= Policy.MAX_UNKNOWN_SHARE
    val averageKmh get() = if (complete && elapsed > 0) distance * 3600 / elapsed else null
}
data class RouteShape(val id: String, val name: String, val direction: String, val points: List<Fix>,
                      val startRadius: Double = 100.0, val endRadius: Double = 70.0) {
    internal val projectionIndex by lazy { Routing.Index(points) }
    val length get() = projectionIndex.length
}
data class Match(val route: RouteShape, val along: Double, val crossTrack: Double, val deviated: Boolean) {
    val progress get() = (along / route.length.coerceAtLeast(1.0)).coerceIn(0.0, 0.99)
}
data class Sample(val id: String, val startUtc: Long, val zone: String, val state: String,
                  val routeId: String?, val direction: String, val excluded: Boolean,
                  val metrics: Metrics, val points: List<Fix>, val endUtc: Long = startUtc + metrics.elapsed)

fun meters(a: Fix, b: Fix): Double {
    val lat = Math.toRadians((a.lat+b.lat)/2)
    return hypot((b.lat-a.lat)*111_195, (b.lon-a.lon)*111_195*cos(lat))
}

fun good(f: Fix) = f.quality == "OK" && f.lat.isFinite() && f.lon.isFinite() &&
    f.lat in -90.0..90.0 && f.lon in -180.0..180.0 && f.accuracy.isFinite() && f.accuracy in 0.0..Policy.MAX_ACCURACY
