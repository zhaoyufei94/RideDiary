package com.ridediary.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class StatisticsTest {
    private fun sample(id: String,start: String,wait: Long=0,state: String="COMPLETED",excluded: Boolean=false,route: String="r",direction: String="WORK"): Sample {
        val t=Instant.parse(start).toEpochMilli()
        return Sample(id,t,"Asia/Shanghai",state,route,direction,excluded,Metrics(60_000,1000.0,listOf(Segment(0,60_000-wait,Motion.MOVING),Segment(60_000-wait,60_000,Motion.WAITING))),Replay.points())
    }
    @Test fun baselineExcludesCurrentFutureInterruptedAnomalousAndOtherRoutes() {
        val current=sample("now","2026-10-20T00:00:00Z")
        val good=sample("good","2026-10-19T00:00:00Z")
        val all=listOf(current,good,sample("future","2026-10-21T00:00:00Z"),sample("bad","2026-10-18T00:00:00Z",excluded=true),sample("interrupted","2026-10-17T00:00:00Z",state="INTERRUPTED"),sample("route","2026-10-16T00:00:00Z",route="other"),sample("direction","2026-10-16T00:00:00Z",direction="HOME"))
        val poor=good.copy(id="poor",metrics=Analyzer.analyze(emptyList(),60_000))
        assertEquals(listOf("good"),Statistics.baseline(current,all+poor).map { it.id })
    }
    @Test fun insufficientHistoryHasNoEta() {
        val current=sample("now","2026-10-20T00:00:00Z")
        assertNull(Statistics.estimate(current,emptyList(),Routing.follow(Replay.points().take(30),Replay.route())!!))
    }
    @Test fun commuteDaysDeduplicateReturnTripAndUseDepartureLocalDate() {
        val a=sample("a","2026-10-01T15:50:00Z")
        val b=sample("b","2026-10-01T15:59:00Z",direction="HOME")
        val c=sample("c","2026-09-30T16:10:00Z")
        assertEquals(1,Statistics.days(listOf(a,b,c)))
        assertEquals(3,Statistics.month(listOf(a,b,c),YearMonth.of(2026,10)).size)
    }
    @Test fun changingClassificationOrExclusionRebuildsAggregate() {
        val a=sample("a","2026-10-01T00:00:00Z")
        assertEquals(1,Statistics.month(listOf(a),YearMonth.of(2026,10),"r").size)
        assertTrue(Statistics.month(listOf(a.copy(excluded=true)),YearMonth.of(2026,10),"r").isEmpty())
        assertTrue(Statistics.month(listOf(a.copy(routeId="new")),YearMonth.of(2026,10),"r").isEmpty())
    }
    @Test fun namedRouteScopeCombinesOnlyItsReferencesAndOneDirection() {
        val all=listOf(sample("work","2026-10-01T00:00:00Z",route="work"),
            sample("duplicate","2026-10-02T00:00:00Z",route="duplicate"),
            sample("home","2026-10-01T10:00:00Z",route="home",direction="HOME"),
            sample("unrelated","2026-10-03T00:00:00Z",route="another"))
        val ids=setOf("work","home","duplicate"); val month=YearMonth.of(2026,10)
        assertEquals(setOf("work","duplicate"),Statistics.monthForRoute(all,month,ids,"WORK").map { it.id }.toSet())
        assertEquals(listOf("home"),Statistics.monthForRoute(all,month,ids,"HOME").map { it.id })
        assertTrue(Statistics.monthForRoute(all,month,emptySet(),"WORK").isEmpty())
    }
    @Test fun midnightClockMeanIsMidnightNotNoon() {
        val a=sample("a","2026-10-01T15:50:00Z"); val b=sample("b","2026-10-01T16:10:00Z")
        assertEquals(0,Statistics.clocks(listOf(a,b),false)!!.first)
    }
    @Test fun zeroWaitAndSingleSampleAreLegal() {
        val a=sample("a","2026-10-01T00:00:00Z")
        assertTrue(Statistics.valid(a)); assertEquals(0L,median(listOf(a.metrics.waiting)))
        assertFalse(Statistics.valid(a.copy(metrics=a.metrics.copy(distance=0.0))))
    }
    @Test fun componentMediansAreIndependentFromTotalMedian() {
        val todayMoving=23*60_000L+18_000; val todayWaiting=6*60_000L+18_000
        val componentMoving=22*60_000L+18_000; val componentWaiting=9*60_000L+30_000
        assertEquals(29*60_000L+36_000,todayMoving+todayWaiting)
        assertEquals(-132_000,todayMoving-componentMoving+todayWaiting-componentWaiting)
        assertEquals(-154_000,todayMoving+todayWaiting-(32*60_000L+10_000))
    }
    @Test fun etaUsesRemainingHistoricalTimeAndOnlyEarlierRides() {
        val start=Instant.parse("2026-10-20T00:00:00Z").toEpochMilli()
        val points=Replay.points()
        fun ride(id: String,utc: Long)=Sample(id,utc,"Asia/Shanghai","COMPLETED","demo-route","WORK",false,Replay.metrics(220_000),points)
        val current=ride("now",start).copy(state="RECORDING",metrics=Replay.metrics(60_000),points=points.filter { it.t<=60_000 })
        val history=(1..3).map { ride("past-$it",start-it*86_400_000L) }
        val eta=Statistics.estimate(current,history+current,Routing.follow(current.points,Replay.route())!!)!!
        assertEquals(3,eta.n); assertTrue(eta.middle in 150_000..170_000)
    }
}
