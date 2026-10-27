package com.ridediary.core

import org.junit.Assert.*
import org.junit.Test

class TripMergeTest {
    private fun f(t: Long,x: Double)=Fix(t,30.0,110.0+x/(111_195*kotlin.math.cos(Math.toRadians(30.0))),4.0,5.0)
    private fun part(id: String,start: Long=1_000,x: Double=0.0)=MergeInput(id,start,10_000,"COMPLETED",
        (0..5).map { f(it*2_000L,x+it*10) })
    private val a get()=part("a")
    private val b get()=part("b",16_000,60.0)
    private fun problems(first: MergeInput=a,second: MergeInput=b)=TripMerge.plan(listOf(first,second),listOf(first.id,second.id)).problems

    @Test fun selectionIsSortedAndRetainsOriginalUtcAndCoordinates() {
        val first=a.copy(points=a.points.map { it.copy(utc=1_000+it.t) })
        val second=b.copy(points=b.points.map { it.copy(utc=16_000+it.t) })
        val result=TripMerge.plan(listOf(second,first),listOf("b","a"))
        assertTrue(result.problems.toString(),result.valid)
        assertEquals(listOf("a","b"),result.parts.map { it.id })
        assertEquals(25_000L,result.elapsed); assertEquals(5_000L,result.gap)
        assertEquals((first.points+second.points).map { it.utc to it.lon },result.points.map { it.utc to it.lon })
        assertEquals(15_000L,result.points[6].t); assertTrue(result.points[6].breakBefore)
    }
    @Test fun evenShortGapsDoNotInventDistanceWaitingOrMapEdges() {
        val plan=TripMerge.plan(listOf(a,b),listOf("a","b"))
        val metrics=Analyzer.analyze(plan.points,plan.elapsed)
        assertEquals(5_000L,metrics.unknown)
        assertEquals(100.0,metrics.distance,0.1)
        assertEquals(0L,metrics.waiting)
        assertEquals(metrics.elapsed,metrics.moving+metrics.waiting+metrics.unknown)
        val edges=TrackPresentation.build(plan.points,metrics).edges
        assertEquals(10,edges.size)
        assertFalse(edges.any { it.from.t==10_000L && it.to.t==15_000L })
    }
    @Test fun overlapAndLargeGapAreRejected() {
        assertTrue(MergeProblem.OVERLAP in problems(second=b.copy(startUtc=10_999)))
        assertTrue(MergeProblem.GAP in problems(second=b.copy(startUtc=11_000+TripMerge.MAX_GAP_MS+1)))
        assertTrue(problems(second=b.copy(startUtc=11_000+TripMerge.MAX_GAP_MS)).isEmpty())
    }
    @Test fun distanceAndImpossibleSpeedAreRejected() {
        assertTrue(MergeProblem.DISTANCE in problems(second=part("b",16_000,351.0)))
        assertTrue(MergeProblem.SPEED in problems(second=part("b",11_001,150.0)))
    }
    @Test fun staleMissingAndBadEndpointFixesAreRejected() {
        assertTrue(MergeProblem.POSITION in problems(first=a.copy(elapsed=TripMerge.ENDPOINT_AGE_MS+10_001),
            second=b.copy(startUtc=TripMerge.ENDPOINT_AGE_MS+20_000)))
        assertTrue(MergeProblem.POSITION in problems(second=b.copy(points=emptyList())))
        assertTrue(MergeProblem.POSITION in problems(first=a.copy(points=a.points.map { it.copy(accuracy=200.0) })))
    }
    @Test fun conflictChecksKnownGroupsAndDirectionsEvenWithoutRoutes() {
        assertTrue(MergeProblem.ROUTE in problems(a.copy(group="office",direction="WORK"),b.copy(group="office",direction="HOME")))
        assertTrue(MergeProblem.ROUTE in problems(a.copy(group="office"),b.copy(group="shop")))
        assertTrue(MergeProblem.ROUTE in problems(a.copy(direction="WORK"),b.copy(direction="HOME")))
        assertTrue(problems(a.copy(group="office",direction="WORK"),b.copy(direction="OTHER")).isEmpty())
    }
    @Test fun omittedInterveningTripIsRejectedButUnrelatedHistoryIsAllowed() {
        val middle=part("middle",12_000,50.0).copy(elapsed=1_000,points=listOf(f(0,50.0),f(1_000,50.0)))
        assertTrue(MergeProblem.SKIPPED in TripMerge.plan(listOf(a,middle,b),listOf("a","b")).problems)
        assertTrue(TripMerge.plan(listOf(a,b,part("later",100_000)),listOf("a","b")).valid)
    }
    @Test fun activeRecordingHiddenDuplicateMissingAndTooManySourcesAreRejected() {
        assertTrue(MergeProblem.ACTIVE in TripMerge.plan(listOf(a,b,part("live",100_000).copy(state="RECORDING")),listOf("a","b")).problems)
        assertTrue(MergeProblem.HIDDEN in problems(first=a.copy(hidden=true)))
        assertTrue(MergeProblem.COUNT in TripMerge.plan(listOf(a,b),listOf("a","a")).problems)
        assertTrue(MergeProblem.MISSING in TripMerge.plan(listOf(a),listOf("a","b")).problems)
        assertTrue(MergeProblem.COUNT in problems(first=a.copy(sourceCount=10)))
    }
    @Test fun invalidClockDurationAndPointOrderAreRejected() {
        assertTrue(MergeProblem.TIME in problems(first=a.copy(clockValid=false)))
        assertTrue(MergeProblem.TIME in problems(first=a.copy(elapsed=0)))
        assertTrue(MergeProblem.POSITION in problems(first=a.copy(points=a.points.reversed())))
        assertTrue(MergeProblem.TIME in problems(second=b.copy(startUtc=Long.MAX_VALUE-5)))
    }
    @Test fun filteringBadFixesAndReversingRoutesKeepBreaks() {
        val second=b.copy(points=listOf(f(0,60.0).copy(quality="STALE"))+b.points.drop(1))
        val plan=TripMerge.plan(listOf(a,second),listOf("a","b"))
        assertTrue(plan.valid)
        assertTrue(plan.points[6].breakBefore); assertTrue(plan.points[7].breakBefore)
        val good=plan.points.filter(::good)
        val route=RouteShape("route","Office","WORK",good,100.0,70.0)
        val reversed=RouteIdentity.reversed(route)
        assertEquals(route.length,reversed.length,0.01)
        assertEquals(90.0,route.length,0.1)
        assertEquals(1,reversed.points.count { it.breakBefore })
        assertEquals(9,TrackPresentation.build(good).edges.size)
    }
    @Test fun nestedMergingKeepsEveryEarlierGap() {
        val original=TripMerge.plan(listOf(a,b),listOf("a","b"))
        val merged=MergeInput("merged",original.startUtc,original.elapsed,"COMPLETED",original.points,sourceCount=2)
        val third=part("c",31_000,120.0)
        val result=TripMerge.plan(listOf(merged,third),listOf("c","merged"))
        assertTrue(result.valid)
        assertEquals(2,result.points.count { it.breakBefore })
        assertEquals(10_000L,Analyzer.analyze(result.points,result.elapsed).unknown)
    }
    @Test fun arrivalDwellCannotCrossARecordingBreak() {
        val shape=RouteShape("route","Office","WORK",listOf(f(0,0.0),f(20_000,400.0)),100.0,70.0)
        val points=(0..10).map { f(it*2_000L,400.0).copy(speed=0.0,breakBefore=it==9) }
        assertFalse(Routing.arrived(points,Match(shape,shape.length,0.0,false),20_000))
    }
}
