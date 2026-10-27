package com.ridediary.core

import com.ridediary.traffic.*
import org.junit.Assert.*
import org.junit.Test

class TrafficPolicyTest {
    private val hour=TrafficPolicy.HOUR
    private val quarter=900_000L
    private fun observation(time: Long,minutes: Int,status: Int=1)=TrafficObservation(time,minutes*60_000L,listOf(TrafficBand(status,1000.0)))
    @Test fun eligibilityIncludesBothBoundariesAndWorksAcrossMidnight() {
        val departure=24*hour+30*60_000
        assertFalse(TrafficPolicy.eligible(departure,departure-2*hour-1))
        assertTrue(TrafficPolicy.eligible(departure,departure-2*hour))
        assertTrue(TrafficPolicy.eligible(departure,departure+hour))
        assertFalse(TrafficPolicy.eligible(departure,departure+hour+1))
        assertTrue(TrafficPolicy.eligible(departure,24*hour-10*60_000))
    }
    @Test fun unknownOrMissingTrafficNeverBecomesClear() {
        assertEquals(0,TrafficObservation(0,null,emptyList()).status)
        assertEquals(0,TrafficObservation(0,null,listOf(TrafficBand(0,1000.0))).status)
        assertEquals(0,TrafficObservation(0,null,listOf(TrafficBand(1,700.0),TrafficBand(0,300.0))).status)
        assertEquals(1,observation(0,20,1).status)
        assertEquals(2,observation(0,20,2).status)
        assertEquals(3,observation(0,20,3).status)
        assertEquals(4,observation(0,20,4).status)
    }
    @Test fun congestionTrendNeedsRecentKnownObservations() {
        val old=TrafficSnapshot(0,observation(0,20))
        assertEquals(1,TrafficPolicy.trend(old,observation(quarter,25,3),quarter))
        assertEquals(-1,TrafficPolicy.trend(old.copy(current=observation(0,25,3)),observation(quarter,20),quarter))
        assertNull(TrafficPolicy.trend(old,observation(60_000,25,3),60_000))
        assertNull(TrafficPolicy.trend(old,TrafficObservation(quarter,null,emptyList()),quarter))
    }
    @Test fun savedRouteComparisonRejectsReverseDetoursAndMissingGeometry() {
        fun p(x: Double,y: Double=0.0)=Fix(0,30+y/111_195,110+x/(111_195*kotlin.math.cos(Math.toRadians(30.0))))
        val reference=listOf(p(0.0),p(1000.0),p(2000.0))
        assertTrue(TrafficGeometry.sameRoute(reference,listOf(p(0.0),p(1000.0,10.0),p(2000.0))))
        assertFalse(TrafficGeometry.sameRoute(reference,reference.reversed()))
        assertFalse(TrafficGeometry.sameRoute(reference,listOf(p(0.0),p(1000.0,400.0),p(2000.0))))
        assertFalse(TrafficGeometry.sameRoute(reference,reference.map { it.copy(lat=it.lat+.002) }))
        assertFalse(TrafficGeometry.sameRoute(reference,emptyList()))
        val dense=(0..100).map { p(it*20.0) }
        assertEquals(listOf(dense.first(),dense.last()),TrafficGeometry.simplify(dense))
        assertEquals(20,TrafficGeometry.anchors(dense).size)
        assertEquals(dense.first(),TrafficGeometry.anchors(dense).first())
        assertEquals(dense.last(),TrafficGeometry.anchors(dense).last())
    }
    @Test fun credentialsAreRedactedAndRequireAValidKey() {
        val config=TrafficConfig(true,"example-secret-key")
        assertTrue(config.ready); assertFalse(config.toString().contains("example-secret-key"))
        assertFalse(TrafficConfig(true," ").ready)
    }
    @Test fun existingMapKeySupportsTrafficIndependentlyOfBasemap() {
        val original=com.ridediary.maps.MapConfig(apiKey="example-existing-key")
        val enabled=original.copy(trafficEnabled=true)
        assertEquals(original.apiKey,enabled.apiKey)
        assertTrue(enabled.trafficReady); assertFalse(enabled.ready)
        assertTrue(enabled.copy(enabled=true).ready)
        assertFalse(enabled.copy(apiKey="").trafficReady)
        assertFalse(enabled.toString().contains(original.apiKey))
    }
    @Test fun nearbyDetoursAreUsableAsAReferenceWithMeasuredDifferences() {
        fun p(x: Double,y: Double=0.0)=Fix(0,30+y/111_195,110+x/(111_195*kotlin.math.cos(Math.toRadians(30.0))))
        val saved=listOf(p(0.0),p(1000.0),p(2000.0))
        val detour=listOf(p(0.0),p(1000.0,400.0),p(2000.0))
        val report=TrafficGeometry.compare(saved,detour)!!
        assertFalse(report.strictMatch)
        assertTrue(report.usableReference)
        assertTrue(report.similarity<.9)
        assertEquals(0.0,report.startOffset,.01)
        assertEquals(0.0,report.endOffset,.01)
        assertTrue(report.plannedLength>report.savedLength)
        val snapped=TrafficGeometry.compare(saved,saved.map { it.copy(lat=it.lat+200.0/111_195) })!!
        assertTrue(snapped.usableReference); assertFalse(snapped.strictMatch)
        assertEquals(200.0,snapped.startOffset,.1)
    }
    @Test fun oppositeDirectionDistantEndpointsAndMissingPathsAreStillRejected() {
        val saved=listOf(Fix(0,30.0,110.0),Fix(0,30.0,110.005))
        assertFalse(TrafficGeometry.compare(saved,saved.reversed())!!.usableReference)
        assertFalse(TrafficGeometry.compare(saved,saved.map { it.copy(lat=it.lat+.02) })!!.usableReference)
        assertNull(TrafficGeometry.compare(saved,emptyList()))
    }
    @Test fun changedPlanningRouteDoesNotProduceACongestionTrend() {
        val comparison=TrafficRouteComparison(1000.0,1000.0,0.0,0.0,1.0,1.0,1.0,1.0,false)
        val old=TrafficSnapshot(0,observation(0,20).copy(comparison=comparison))
        val changed=observation(quarter,25,3).copy(comparison=comparison.copy(plannedLength=1500.0))
        assertNull(TrafficPolicy.trend(old,changed,quarter))
    }
}
