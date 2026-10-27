package com.ridediary.core

import com.ridediary.data.*
import org.junit.Assert.*
import org.junit.Test

class RideMergeInputTest {
    private fun data(ride: RideRecord)=RideData(ride,(0..5).map { i ->
        PointRecord(ride.id,i,i*2_000L,ride.startUtc+i*2_000,30.0,110.0+i*.0001,4.0,5.0,null,"OK")
    },emptyList())
    private fun ride()=RideRecord("trip",1_000,"Asia/Shanghai",state="COMPLETED",elapsed=10_000,endUtc=11_000)

    @Test fun trimUsesEffectiveClockAndRetainsUtc() {
        val input=data(ride().copy(boundaryStart=2_000,boundaryEnd=8_000,direction="WORK")).mergeInput(emptyList())
        assertEquals(3_000L,input.startUtc); assertEquals(6_000L,input.elapsed)
        assertEquals(listOf(0L,2_000L,4_000L,6_000L),input.points.map { it.t })
        assertEquals(listOf(3_000L,5_000L,7_000L,9_000L),input.points.map { it.utc })
        assertEquals("WORK",input.direction); assertTrue(input.clockValid)
    }
    @Test fun recoveryTailIsRemovedForInterruptedRecordingsUnlessExplicitlyTrimmed() {
        val interrupted=data(ride().copy(state="INTERRUPTED",elapsed=50_000,endUtc=51_000))
        assertEquals(10_000L,interrupted.mergeInput(emptyList()).elapsed)
        assertEquals(20_000L,interrupted.copy(ride=interrupted.ride.copy(boundaryEnd=20_000)).mergeInput(emptyList()).elapsed)
    }
    @Test fun clockDisagreementAndInvalidBoundsAreRejected() {
        assertFalse(data(ride().copy(endUtc=100_000)).mergeInput(emptyList()).clockValid)
        assertFalse(data(ride().copy(boundaryStart=8_000,boundaryEnd=2_000)).mergeInput(emptyList()).clockValid)
        assertTrue(data(ride().copy(endUtc=11_010)).mergeInput(emptyList()).clockValid)
    }
    @Test fun hiddenAndNestedMetadataSurviveNormalization() {
        val input=data(ride().copy(mergedInto="parent",mergedCount=3)).mergeInput(emptyList())
        assertTrue(input.hidden); assertEquals(3,input.sourceCount)
    }
}
