package com.ridediary.core

import org.junit.Assert.*
import org.junit.Test

class RoutingTest {
    private val route=Replay.route()
    @Test fun recognizesAfterDepartureAndKeepsWholeJourney() {
        val trace=Replay.points().take(40)
        val match=Routing.recognize(trace,listOf(route))!!
        assertTrue(match.progress in 0.4..0.8); assertEquals(40,trace.size)
    }
    @Test fun noRecognitionBeforeEnoughTravel() { assertNull(Routing.recognize(Replay.points().take(4),listOf(route))) }
    @Test fun adjacentCandidatesRemainAmbiguous() {
        val parallel=route.copy(id="parallel",points=route.points.map { it.copy(lat=it.lat+5/111_195.0) })
        assertNull(Routing.recognize(Replay.points().take(30),listOf(route,parallel)))
    }
    @Test fun deviationIsVisible() {
        val points=Replay.points().take(40).toMutableList()
        points += points.last().copy(t=80_000,lat=points.last().lat+100/111_195.0)
        val result=Routing.follow(points,route)!!
        assertTrue(result.deviated)
    }
    @Test fun uTurnDoesNotPretendToAdvance() {
        val forward=Replay.points().take(35)
        val back=forward.takeLast(15).reversed().mapIndexed { i,f -> f.copy(t=70_000+i*2_000L) }
        assertTrue(Routing.follow(forward+back,route)!!.deviated)
    }
    @Test fun selfIntersectionProjectionStaysInLocalWindow() {
        val a=Fix(0,30.0,110.0)
        val crossing=RouteShape("cross","","WORK",listOf(a,a.copy(t=100_000,lon=110.01),a.copy(t=200_000,lat=30.01),a.copy(t=300_000),a.copy(t=400_000,lon=110.02)))
        val projected=Routing.project(a,crossing,0.0,180.0)!!
        assertEquals(0.0,projected.along,0.01)
        // A fix lying on the later crossing branch must not jump kilometers ahead.
        assertNull(Routing.project(a.copy(lon=110.015),crossing,100.0,180.0))
    }
    @Test fun shortVisitDoesNotConfirmArrival() {
        val points=Replay.points().take(95)
        val match=Routing.follow(points,route)!!
        assertFalse(Routing.arrived(points,match,188_000))
    }
    @Test fun sustainedStationaryArrivalConfirmsButProgressIsNotPrematurely100() {
        val points=Replay.points(); val match=Routing.follow(points,route)!!
        assertTrue(match.progress<1.0); assertTrue(Routing.arrived(points,match,220_000))
        assertFalse(Routing.arrived(points.filter { it.t<=206_000 },match,206_000))
    }
    @Test fun staleOrGapAtEndCannotConfirmArrival() {
        val points=Replay.points(); val match=Routing.follow(points,route)!!
        assertFalse(Routing.arrived(points.dropLast(10),match,220_000))
        assertFalse(Routing.arrived(points.map { if(it.t>200_000) it.copy(quality="POOR") else it },match,220_000))
    }
}
