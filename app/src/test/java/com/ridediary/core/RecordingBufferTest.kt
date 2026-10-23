package com.ridediary.core

import org.junit.Assert.*
import org.junit.Test

class RecordingBufferTest {
    private fun fix(t: Long)=Fix(t,30.0,120.0,5.0,4.0)
    @Test fun backlogRetainsAllRawPointsButOnlyOneCheckpointAndFixedStopTime() {
        val buffer=RecordingBuffer()
        repeat(1800) { buffer.checkpoint(it*2000L,100_000+it*2000L,fix(it*2000L)) }
        buffer.stop(3_600_000,3_700_000,false)
        repeat(100) { buffer.checkpoint(3_900_000,4_000_000,fix(3_900_000)) }
        buffer.stop(4_000_000,4_100_000,false)
        val batch=buffer.batch()
        assertEquals(1800,batch.fixes.size)
        assertEquals(3_600_000,batch.elapsed)
        assertEquals(3_700_000,batch.utc)
        assertFalse(batch.cutoff!!.discard)
        assertEquals(batch,buffer.batch()) // A failed write does not consume pending points.
        buffer.acknowledge(batch)
        assertTrue(buffer.batch().fixes.isEmpty())
        assertEquals(batch.cutoff,buffer.batch().cutoff)
    }
    @Test fun fixesArrivingDuringWriteSurviveAcknowledgementAndStop() {
        val buffer=RecordingBuffer()
        buffer.checkpoint(2000,102000,fix(2000))
        val writing=buffer.batch()
        buffer.checkpoint(4000,104000,fix(4000))
        buffer.stop(4500,104500,false)
        buffer.acknowledge(writing)
        assertEquals(listOf(fix(4000)),buffer.batch().fixes)
        assertEquals(4500,buffer.batch().elapsed)
    }
    @Test fun noFixStopAndDuplicateCommandsPreserveFirstDecision() {
        val buffer=RecordingBuffer()
        buffer.checkpoint(0,100000)
        buffer.stop(1234,101234,true)
        buffer.stop(5000,105000,false)
        assertTrue(buffer.batch().fixes.isEmpty())
        assertTrue(buffer.batch().cutoff!!.discard)
        assertEquals(1234,buffer.batch().elapsed)
    }
}
