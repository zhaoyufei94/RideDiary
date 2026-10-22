package com.ridediary.core

/** Synthetic coordinates, intentionally unrelated to the user's commute. Never stored in the real DB. */
object Replay {
    fun points(): List<Fix> = (0..110).map { i ->
        val step=(i - (i-45).coerceIn(0,18)).coerceAtMost(70)
        val x=step.coerceAtMost(40)*8.0
        val y=(step-40).coerceAtLeast(0)*8.0
        val stopped=i in 46..63 || i > 88
        Fix(i*2_000L,30.0+y/111_195,110.0+x/(111_195*kotlin.math.cos(Math.toRadians(30.0))),4.0,if (stopped) 0.1 else 4.0)
    }
    fun route()=RouteShape("demo-route","", "WORK",points().take(89))
    fun metrics(at: Long)=Analyzer.analyze(points().filter { it.t <= at },at)
}
