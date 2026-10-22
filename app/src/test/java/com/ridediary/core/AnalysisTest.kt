package com.ridediary.core

import org.junit.Assert.*
import org.junit.Test

class AnalysisTest {
    private fun f(t: Long,x: Double=0.0,speed: Double?=0.1,accuracy: Double=5.0)=Fix(t,30.0,110.0+x/(111_195*kotlin.math.cos(Math.toRadians(30.0))),accuracy,speed)
    @Test fun stationaryDriftBackfillsCandidateAndRemovesDistance() {
        val points=(0..15).map { f(it*2_000L,if(it%2==0) 0.0 else 3.0) }
        val m=Analyzer.analyze(points,30_000)
        assertEquals(30_000,m.waiting); assertEquals(1,m.stops); assertEquals(0.0,m.distance,0.01)
    }
    @Test fun slowCreepingIsMovement() {
        val m=Analyzer.analyze((0..15).map { f(it*2_000L,it*2.0,1.0) },30_000)
        assertEquals(0,m.waiting); assertEquals(30_000,m.moving)
        assertEquals(30.0,m.distance,0.1)
    }
    @Test fun missingSpeedDoesNotBecomeAStop() {
        val m=Analyzer.analyze((0..10).map { f(it*2_000L,0.0,null) },20_000)
        assertEquals(20_000,m.unknown); assertEquals(0,m.stops)
    }
    @Test fun missingSpeedCanBeInferredWhenDisplacementExceedsAccuracy() {
        val m=Analyzer.analyze((0..10).map { f(it*2_000L,it*10.0,null) },20_000)
        assertEquals(20_000,m.moving); assertEquals(100.0,m.distance,0.1)
    }
    @Test fun gapsDoNotBridgeDistanceOrBecomeWaiting() {
        val m=Analyzer.analyze(listOf(f(0),f(2_000,10.0,5.0),f(120_000,500.0,5.0)),130_000)
        assertEquals(128_000,m.unknown); assertEquals(10.0,m.distance,0.1)
        assertEquals(m.elapsed,m.moving+m.waiting+m.unknown)
    }
    @Test fun jumpIsUnknownAndDoesNotPolluteDistance() {
        val m=Analyzer.analyze(listOf(f(0),f(2_000,10_000.0,5.0),f(4_000,20.0,5.0),f(6_000,30.0,5.0)),6_000)
        assertEquals(4_000,m.unknown); assertEquals(10.0,m.distance,0.1)
    }
    @Test fun badAccuracyAndStaleFixesAreUnknown() {
        val m=Analyzer.analyze(listOf(f(0),f(2_000,0.0,0.0,100.0),f(4_000).copy(quality="STALE"),f(6_000)),6_000)
        assertEquals(6_000,m.unknown)
    }
    @Test fun noFixStillCountsTimeAndHidesSpeed() {
        val m=Analyzer.analyze(emptyList(),60_000)
        assertEquals(60_000,m.unknown); assertNull(m.averageKmh)
    }
    @Test fun separatedStopsAreMergedOnlyWithinTheirOwnIntervals() {
        val points=(0..30).map { i -> f(i*2_000L,if(i<10) 0.0 else if(i<20) (i-9)*8.0 else 80.0,if(i in 10..19) 4.0 else 0.1) }
        val m=Analyzer.analyze(points,60_000)
        assertEquals(2,m.stops); assertEquals(m.waiting/2,m.averageWait)
    }
    @Test fun replayIsDeterministic() {
        assertEquals(Replay.metrics(220_000),Replay.metrics(220_000))
        val m=Replay.metrics(220_000)
        assertEquals(m.elapsed,m.moving+m.waiting+m.unknown)
        assertTrue(m.distance>500); assertTrue(m.waiting>30_000)
    }
    @Test fun concurrentProvidersDoNotTurnGoodGnssIntoJumpIntervalsAndNetworkRecovers() {
        val selector=FixSelector(); val selected=mutableListOf<Fix>()
        for(i in 0..10) {
            val gps=f(i*2_000L,i*8.0,4.0)
            if(selector.accept(true,gps.t,gps)) selected+=gps
            val network=f(gps.t+200,i*8.0+15,4.0)
            if(selector.accept(false,network.t,network)) selected+=network
        }
        val m=Analyzer.analyze(selected,20_000)
        assertEquals(11,selected.size); assertEquals(80.0,m.distance,0.1); assertEquals(0,m.unknown)
        val fallback=f(32_000,128.0,4.0)
        assertTrue(selector.accept(false,32_000,fallback))
        assertFalse(selector.accept(true,32_200,fallback.copy(t=32_200,accuracy=100.0)))
    }
}
