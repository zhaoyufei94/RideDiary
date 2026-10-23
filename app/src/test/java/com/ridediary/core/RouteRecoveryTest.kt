package com.ridediary.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos

class RouteRecoveryTest {
    private fun point(i: Int,x: Double,y: Double=0.0)=Fix(i*2000L,30+y/111_195,110+x/(111_195*cos(Math.toRadians(30.0))),4.0,4.0)
    private val straight=(0..350).map { point(it,it*8.0) }
    private val route=RouteShape("straight","Commute","HOME",straight,50.0,50.0)

    @Test fun reacquiresAfterReferenceStartLoopInsteadOfFreezingAtOrigin() {
        val reference=listOf(point(0,0.0),point(1,-55.0),point(2,0.0))+straight.drop(1)
        val candidate=Routing.evaluate(straight,route.copy(points=reference))!!
        assertTrue(candidate.eligible)
        assertEquals(Routing.State.CONFIRMED,candidate.state)
        assertTrue(candidate.overlap>.95)
        assertTrue(candidate.match.progress>.98)
    }
    @Test fun reacquiresAfterShortDeviationUsingConsecutiveFixes() {
        val trace=straight.mapIndexed { i,f -> if(i in 100..110) point(i,i*8.0,65.0) else f }
        val during=Routing.evaluate(trace.take(111),route)!!
        assertEquals(Routing.State.DEVIATED,during.state)
        val pending=Routing.evaluate(trace.take(112),route)!!
        assertTrue(pending.match.deviated)
        val recovered=Routing.evaluate(trace,route)!!
        assertTrue(recovered.eligible)
        assertFalse(recovered.match.deviated)
        assertTrue(recovered.overlap>.94)
        assertTrue(recovered.match.progress>.98)
    }
    @Test fun aGapCannotJumpToADistantLaterBranch() {
        val trace=straight.take(30)+straight.takeLast(10).mapIndexed { i,f -> f.copy(t=600_000L+i*2000) }
        val candidate=Routing.evaluate(trace,route)!!
        assertTrue(candidate.along<400)
        assertTrue(candidate.match.deviated)
    }
    @Test fun overlapIsCollectedPointShareNotWholeRouteProgressOrConfidence() {
        val report=RouteIdentity.report(straight.take(12),listOf(route),mapOf(route.id to "group"))
        assertEquals(1,report.candidates.size)
        assertEquals(1.0,report.candidates.single().overlap,0.0)
        assertEquals(Routing.State.LEARNING,report.candidates.single().state)
        assertNull(report.match)
        assertTrue(report.candidates.single().match.progress<.1)
    }
    @Test fun adjacentRoadsRemainAmbiguousAfterReacquisition() {
        val loop=listOf(point(0,0.0),point(1,-55.0),point(2,0.0))+straight.drop(1)
        val a=route.copy(points=loop)
        val b=a.copy(id="parallel",points=loop.map { it.copy(lat=it.lat+5/111_195.0) })
        val report=RouteIdentity.report(straight,listOf(a,b),mapOf(a.id to "a",b.id to "b"))
        assertEquals(2,report.candidates.size)
        assertTrue(report.ambiguous)
        assertNull(report.match)
    }
}
