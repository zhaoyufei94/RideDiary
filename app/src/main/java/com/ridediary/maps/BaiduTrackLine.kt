package com.ridediary.maps

import com.ridediary.core.Fix
import com.ridediary.core.TrackEdge

/** Ordinary Baidu polylines take N - 1 segment colors, unlike gradient lines' N vertex colors. */
internal data class BaiduTrackLine(val points: List<Fix>,val colors: List<Int>)

internal fun baiduTrackLines(edges: List<TrackEdge>): List<BaiduTrackLine> {
    val result=mutableListOf<BaiduTrackLine>()
    var points=mutableListOf<Fix>()
    var colors=mutableListOf<Int>()
    fun finish() {
        if(colors.isNotEmpty()) result+=BaiduTrackLine(points.toList(),colors.toList())
        points=mutableListOf(); colors=mutableListOf()
    }
    for(edge in edges) {
        if(points.isNotEmpty() && (points.last()!=edge.from || points.size>=1000)) finish()
        if(points.isEmpty()) points+=edge.from
        points+=edge.to
        colors+=edge.color
    }
    finish()
    return result
}
