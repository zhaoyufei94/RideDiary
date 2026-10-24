package com.ridediary.maps

import org.junit.Assert.*
import org.junit.Test

class StopCalloutLayoutTest {
    private val stop=ScreenPoint(200f,200f)
    private val vertical=ScreenLine(ScreenPoint(200f,0f),ScreenPoint(200f,400f))
    private val horizontal=ScreenLine(ScreenPoint(0f,200f),ScreenPoint(400f,200f))

    @Test fun verticalTrackPlacesBubbleBesideTheStopWithoutMovingItsAnchor() {
        val placement=StopCalloutLayout(400f,400f,1f,listOf(vertical)).place(stop,80f,22f)!!
        assertEquals(stop,placement.point)
        assertTrue(placement.body.left>stop.x || placement.body.right<stop.x)
        assertFalse(segmentIntersects(vertical,placement.body.expanded(5f)))
    }

    @Test fun horizontalTrackCanUseTheSpaceAboveTheStop() {
        val placement=StopCalloutLayout(400f,400f,1f,listOf(horizontal)).place(stop,80f,22f)!!
        assertTrue(placement.body.bottom<stop.y)
        assertFalse(segmentIntersects(horizontal,placement.body.expanded(5f)))
    }

    @Test fun crossingsAndDiagonalTracksAreCheckedAgainstEveryRealEdge() {
        val diagonal=ScreenLine(ScreenPoint(0f,400f),ScreenPoint(400f,0f))
        for(track in listOf(listOf(vertical,horizontal),listOf(diagonal))) {
            val placement=StopCalloutLayout(400f,400f,1f,track).place(stop,80f,22f)!!
            assertTrue(track.none { segmentIntersects(it,placement.body.expanded(5f)) })
        }
    }

    @Test fun neighboringBubblesAndMarkersAreAvoided() {
        val marker=ScreenRect(220f,185f,245f,215f)
        val layout=StopCalloutLayout(400f,400f,1f,listOf(vertical),listOf(marker))
        val first=layout.place(stop,80f,22f)!!
        val second=layout.place(stop,80f,22f)!!
        assertFalse(first.body.expanded(5f).intersects(marker))
        assertFalse(second.body.expanded(5f).intersects(marker))
        assertFalse(first.body.intersects(second.body.expanded(5f)))
    }

    @Test fun nearAnEdgeTheBubbleStaysOnScreenAndTheStopStaysPut() {
        val point=ScreenPoint(10f,10f)
        val edge=ScreenLine(ScreenPoint(10f,0f),ScreenPoint(10f,400f))
        val placement=StopCalloutLayout(400f,400f,1f,listOf(edge)).place(point,80f,22f)!!
        assertEquals(point,placement.point)
        assertTrue(placement.body.left>=2f && placement.body.top>=2f && placement.body.right<=398f && placement.body.bottom<=398f)
    }

    @Test fun aDenseTrackHidesTheBubbleInsteadOfCoveringTheRoute() {
        val track=(0..400 step 2).map { y -> ScreenLine(ScreenPoint(0f,y.toFloat()),ScreenPoint(400f,y.toFloat())) }
        assertNull(StopCalloutLayout(400f,400f,1f,track).place(stop,80f,22f))
    }

    @Test fun zoomedSegmentsAreClippedButStillBlockTheVisibleTrack() {
        val long=ScreenLine(ScreenPoint(200f,-100_000f),ScreenPoint(200f,100_000f))
        val outside=ScreenLine(ScreenPoint(-100_000f,-100_000f),ScreenPoint(-90_000f,-90_000f))
        val placement=StopCalloutLayout(400f,400f,1f,listOf(long,outside)).place(stop,80f,22f)!!
        assertFalse(segmentIntersects(long,placement.body.expanded(5f)))
        // Separate real edges do not acquire a connecting segment across a GPS gap.
        val gap=listOf(ScreenLine(ScreenPoint(200f,0f),ScreenPoint(200f,100f)),ScreenLine(ScreenPoint(200f,250f),ScreenPoint(200f,400f)))
        val inGap=StopCalloutLayout(400f,400f,1f,gap).place(stop,80f,22f)!!
        assertTrue(inGap.body.bottom<200f)
    }

    @Test fun routeClearanceScalesWithDensityAndVisibleBubblesAreBounded() {
        val large=ScreenLine(ScreenPoint(600f,0f),ScreenPoint(600f,1200f))
        val placement=StopCalloutLayout(1200f,1200f,3f,listOf(large)).place(ScreenPoint(600f,600f),240f,66f)!!
        assertFalse(segmentIntersects(large,placement.body.expanded(15f)))
        val layout=StopCalloutLayout(2200f,400f,1f,emptyList())
        repeat(30) { assertNotNull(layout.place(ScreenPoint(50f+it*65,200f),40f,22f)) }
        assertNull(layout.place(ScreenPoint(2050f,200f),40f,22f))
    }

    @Test fun lineIntersectionHandlesDiagonalBoundsAndStationaryPoints() {
        val box=ScreenRect(10f,10f,20f,20f)
        assertTrue(segmentIntersects(ScreenLine(ScreenPoint(0f,0f),ScreenPoint(30f,30f)),box))
        assertFalse(segmentIntersects(ScreenLine(ScreenPoint(0f,0f),ScreenPoint(30f,5f)),box))
        assertTrue(segmentIntersects(ScreenLine(ScreenPoint(15f,15f),ScreenPoint(15f,15f)),box))
        assertFalse(segmentIntersects(ScreenLine(ScreenPoint(25f,15f),ScreenPoint(25f,15f)),box))
    }
}
