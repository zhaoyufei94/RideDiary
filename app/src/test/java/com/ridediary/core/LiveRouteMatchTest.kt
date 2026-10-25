package com.ridediary.core

import com.ridediary.data.LiveRouteMatch
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos

class LiveRouteMatchTest {
    private fun point(i: Int,y: Double=0.0)=Fix(i*2000L,30+y/111_195,
        110+i*8.0/(111_195*cos(Math.toRadians(30.0))),4.0,4.0)
    private val reference=(0..400).map { point(it) }
    private val route=RouteShape("office","Office","WORK",reference,50.0,50.0)
    private fun snapshot(points: List<Fix>,bound: String?=null): LiveRouteMatch =
        LiveRouteMatch("ride",RouteIdentity.report(points,listOf(route),mapOf(route.id to "office")),
            points.last().t,0,5000,0,bound)

    @Test fun sharedStartDetourRejoinAndSecondDetourDoNotLatchTheInitialRoute() {
        val trace=(0..400).map { i -> point(i,if(i in 40..100 || i>=300) 80.0 else 0.0) }
        assertEquals(route.id,snapshot(trace.take(40)).confirmedMatch?.route?.id)
        val detour=snapshot(trace.take(101))
        assertTrue(detour.report.candidates.single().overlap<Policy.MIN_INLIER_SHARE)
        assertNull(detour.confirmedMatch)
        // Back on the reference, but accumulated overlap is still below the acceptance threshold.
        val rejoining=snapshot(trace.take(140))
        assertFalse(rejoining.report.candidates.single().match.deviated)
        assertNull(rejoining.confirmedMatch)
        assertEquals(route.id,snapshot(trace.take(300)).confirmedMatch?.route?.id)
        assertNull(snapshot(trace).confirmedMatch)
    }

    @Test fun aChosenRouteAlsoLosesLiveConfirmationWhenDeviating() {
        val trace=(0..80).map { i -> point(i,if(i>=40) 80.0 else 0.0) }
        assertEquals(route.id,snapshot(trace.take(40),route.id).confirmedMatch?.route?.id)
        assertNull(snapshot(trace,route.id).confirmedMatch)
        // Even with high total overlap, a currently deviating route cannot drive title/progress/ETA.
        val briefDetour=snapshot(trace.take(44),route.id)
        assertTrue(briefDetour.report.candidates.single().eligible)
        assertTrue(briefDetour.report.candidates.single().match.deviated)
        assertNull(briefDetour.confirmedMatch)
    }

    @Test fun ambiguousCandidatesDoNotProvideALiveTitleAndBoundRoutesCannotBorrowAnotherMatch() {
        val parallel=route.copy(id="parallel",points=reference.map { it.copy(lat=it.lat+5/111_195.0) })
        val report=RouteIdentity.report(reference.take(40),listOf(route,parallel),mapOf(route.id to "a",parallel.id to "b"))
        assertTrue(report.ambiguous)
        assertNull(LiveRouteMatch("ride",report,78000,0,5000,0,null).confirmedMatch)
        assertNull(snapshot(reference.take(40),"missing-bound-route").confirmedMatch)
    }
}
