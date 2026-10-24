package com.ridediary.core

import org.junit.Assert.*
import org.junit.Test

class TrackGeometryTest {
    private fun fix(t: Long,x: Double=0.0,speed: Double?=0.0)=Fix(t,30.0,110.0+x/(111_195*kotlin.math.cos(Math.toRadians(30.0))),5.0,speed)
    @Test fun invalidInteriorFixAlwaysBreaksTheLine() {
        val points=listOf(fix(0),fix(2_000,4.0).copy(quality="POOR"),fix(4_000,8.0),fix(6_000,12.0))
        val result=TrackPresentation.build(points)
        assertEquals(1,result.edges.size)
        assertEquals(points[2],result.edges.single().from)
    }
    @Test fun longGapsAndJumpPointsNeverCreateConnectingEdges() {
        val points=listOf(fix(0),fix(20_000,10.0),fix(22_000,10_000.0),fix(24_000,20.0),fix(26_000,25.0))
        val result=TrackPresentation.build(points)
        assertEquals(1,result.edges.size)
        assertEquals(points[3],result.edges.single().from)
        assertFalse(result.points.contains(points[2]))
    }
    @Test fun missingSpeedIsUnknownRatherThanStationaryRed() {
        val points=(0..20).map { fix(it*2_000L,speed=null) }
        val result=TrackPresentation.build(points,Analyzer.analyze(points,40_000))
        assertTrue(result.stops.isEmpty())
        assertTrue(result.edges.all { it.speedKmh==null && it.color==TrackPresentation.UNKNOWN_COLOR })
    }
    @Test fun trustworthyFixesAcrossAGapAreVisibleWithoutInventingAnEdge() {
        val points=listOf(fix(0),fix(30_000,100.0))
        val result=TrackPresentation.build(points)
        assertEquals(points,result.points)
        assertTrue(result.edges.isEmpty())
    }
    @Test fun validDisplacementCanSupplySpeedWhenSensorAccuracyIsPoor() {
        val points=listOf(fix(0),fix(2_000,20.0,50.0).copy(speedAccuracy=10.0))
        val result=TrackPresentation.build(points,Analyzer.analyze(points,2_000))
        assertEquals(36.0,result.edges.single().speedKmh!!,.01)
    }
    @Test fun longStopsUseConfirmedDurationAndDriftResistantPosition() {
        val points=(0..20).map { fix(it*2_000L,if(it==15) 25.0 else 0.0) }
        val metrics=Analyzer.analyze(points,40_000)
        val result=TrackPresentation.build(points,metrics)
        assertEquals(40_000,result.stops.single().duration)
        assertEquals(110.0,result.stops.single().point.lon,.000001)
        assertTrue(result.edges.all { it.speedKmh==0.0 })
        assertEquals(40_000,metrics.waiting)
    }
    @Test fun shortStopsRemainInMetricsButAreNotLabeled() {
        val points=(0..10).map { fix(it*2_000L) }
        val metrics=Analyzer.analyze(points,20_000)
        assertEquals(20_000,metrics.waiting)
        assertTrue(TrackPresentation.build(points,metrics).stops.isEmpty())
    }
    @Test fun exactlyThirtySecondsIsLabeledAndUnknownGapsDoNotMergeStops() {
        val points=(0..15).map { fix(it*2_000L) }+(0..15).map { fix(60_000+it*2_000L) }
        val metrics=Analyzer.analyze(points,90_000)
        val result=TrackPresentation.build(points,metrics)
        assertEquals(2,result.stops.size)
        assertTrue(result.stops.all { it.duration==30_000L })
        assertEquals(30_000,metrics.unknown)
    }
    @Test fun revisitsAtTheSamePositionRemainSeparateStops() {
        val points=(0..50).map { i -> fix(i*2_000L,when { i<20->0.0; i<25->(i-19)*10.0; i<30->(29-i)*10.0; else->0.0 },if(i in 20..29) 5.0 else 0.0) }
        val result=TrackPresentation.build(points,Analyzer.analyze(points,100_000))
        assertEquals(2,result.stops.size)
        assertTrue(result.stops[0].to<result.stops[1].from)
    }
    @Test fun referencesHaveNoBorrowedTripSpeedOrStopAnalysis() {
        val points=(0..20).map { fix(it*2_000L) }
        val result=TrackPresentation.build(points)
        assertFalse(result.speedColored)
        assertTrue(result.stops.isEmpty())
        assertTrue(result.edges.all { it.color==TrackPresentation.ROUTE_COLOR })
    }
    @Test fun speedColorsHaveAConsistentScaleAndOpaqueUnknown() {
        assertEquals(TrackPresentation.speedColor(60.0),TrackPresentation.speedColor(120.0))
        assertEquals(TrackPresentation.UNKNOWN_COLOR,TrackPresentation.speedColor(Double.NaN))
        assertEquals(TrackPresentation.UNKNOWN_COLOR,TrackPresentation.speedColor(null))
        assertNotEquals(TrackPresentation.speedColor(5.0),TrackPresentation.speedColor(35.0))
        assertEquals(255,(TrackPresentation.speedColor(30.0) ushr 24) and 255)
    }
}
