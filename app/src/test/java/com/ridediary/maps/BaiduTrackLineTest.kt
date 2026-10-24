package com.ridediary.maps

import com.ridediary.core.Fix
import com.ridediary.core.TrackEdge
import com.ridediary.core.TrackPresentation
import org.junit.Assert.*
import org.junit.Test

class BaiduTrackLineTest {
    private fun fix(i: Int)=Fix(i*2000L,30.0,110.0+i*.00001,5.0,5.0)

    @Test fun everySegmentKeepsItsOwnColorIncludingUnknown() {
        val points=(0..3).map(::fix)
        val colors=listOf(TrackPresentation.speedColor(5.0),TrackPresentation.UNKNOWN_COLOR,TrackPresentation.speedColor(40.0))
        val edges=points.zipWithNext().mapIndexed { i,(from,to) -> TrackEdge(from,to,colors[i],if(i==1) null else 5.0) }
        val line=baiduTrackLines(edges).single()
        assertEquals(points,line.points)
        assertEquals(colors,line.colors)
        assertEquals(line.points.size-1,line.colors.size)
    }

    @Test fun gapsRemainDisconnectedAndAppendedEdgesKeepTheirColors() {
        val edges=listOf(TrackEdge(fix(0),fix(1),1,5.0),TrackEdge(fix(3),fix(4),2,5.0))
        val before=baiduTrackLines(edges)
        val after=baiduTrackLines(edges+TrackEdge(fix(4),fix(5),3,5.0))
        assertEquals(2,after.size)
        assertEquals(before.first(),after.first())
        assertEquals(listOf(fix(3),fix(4),fix(5)),after.last().points)
        assertEquals(listOf(2,3),after.last().colors)
    }

    @Test fun chunkingLongTracksDoesNotDropOrDuplicateEdges() {
        val edges=(0..2000).map { TrackEdge(fix(it),fix(it+1),it,5.0) }
        val lines=baiduTrackLines(edges)
        assertTrue(lines.all { it.points.size in 2..1000 && it.colors.size==it.points.size-1 })
        assertEquals(edges,lines.flatMap { line -> line.points.zipWithNext().mapIndexed { i,(from,to) -> TrackEdge(from,to,line.colors[i],5.0) } })
        assertTrue(baiduTrackLines(emptyList()).isEmpty())
    }
}
