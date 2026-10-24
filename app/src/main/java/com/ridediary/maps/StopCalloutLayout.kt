package com.ridediary.maps

import kotlin.math.max
import kotlin.math.min

internal data class ScreenPoint(val x: Float,val y: Float)
internal data class ScreenLine(val from: ScreenPoint,val to: ScreenPoint)
internal data class ScreenRect(val left: Float,val top: Float,val right: Float,val bottom: Float) {
    val width get()=right-left
    val height get()=bottom-top
    fun expanded(padding: Float)=ScreenRect(left-padding,top-padding,right+padding,bottom+padding)
    fun translated(x: Float,y: Float)=ScreenRect(left+x,top+y,right+x,bottom+y)
    fun intersects(other: ScreenRect)=left<=other.right && right>=other.left && top<=other.bottom && bottom>=other.top
}

internal data class StopCalloutPlacement(val point: ScreenPoint,val body: ScreenRect,val slot: Int) {
    fun translated(x: Float,y: Float)=copy(point=ScreenPoint(point.x+x,point.y+y),body=body.translated(x,y))
}

/** Screen-space layout shared by local and Baidu maps; no inferred lines across GPS gaps. */
internal class StopCalloutLayout(width: Float,height: Float,private val density: Float,
    track: List<ScreenLine>,obstacles: List<ScreenRect> = emptyList()) {
    private val viewport=ScreenRect(0f,0f,width,height)
    private val occupied=obstacles.toMutableList()
    private val route=ScreenRouteIndex(track,viewport,density)
    private var placed=0

    fun place(point: ScreenPoint,width: Float,height: Float): StopCalloutPlacement? {
        if(placed>=30 || point.x !in 0f..viewport.right || point.y !in 0f..viewport.bottom) return null
        // Above/below suit horizontal tracks; left/right suit vertical ones. Diagonals
        // and larger gaps provide alternatives at bends and near other stop labels.
        for((distanceIndex,gap) in listOf(16f,32f,48f).withIndex()) {
            val d=gap*density
            val positions=listOf(
                ScreenPoint(point.x-width/2,point.y-d-height),
                ScreenPoint(point.x+d,point.y-height/2),
                ScreenPoint(point.x-d-width,point.y-height/2),
                ScreenPoint(point.x-width/2,point.y+d),
                ScreenPoint(point.x+d,point.y-d-height),
                ScreenPoint(point.x-d-width,point.y-d-height),
                ScreenPoint(point.x+d,point.y+d),
                ScreenPoint(point.x-d-width,point.y+d))
            for((direction,p) in positions.withIndex()) {
                val body=ScreenRect(p.x,p.y,p.x+width,p.y+height)
                if(body.left<2*density || body.top<2*density || body.right>viewport.right-2*density || body.bottom>viewport.bottom-2*density) continue
                val padded=body.expanded(5*density) // Clear the visible route stroke/casing as well as its centerline.
                if(occupied.any { it.intersects(padded) } || route.intersects(padded)) continue
                occupied+=body
                placed++
                return StopCalloutPlacement(point,body,distanceIndex*8+direction)
            }
        }
        // The exact stop dot is drawn independently even when no bubble fits.
        return null
    }
}

/** Bucket only the visible portion of real track edges, keeping zoomed/long trips bounded. */
private class ScreenRouteIndex(lines: List<ScreenLine>,private val viewport: ScreenRect,density: Float) {
    private val cell=64*density
    private val buckets=HashMap<Long,MutableList<ScreenLine>>()
    init {
        for(line in lines) {
            val clipped=clip(line,viewport.expanded(8*density)) ?: continue
            val box=ScreenRect(min(clipped.from.x,clipped.to.x),min(clipped.from.y,clipped.to.y),
                max(clipped.from.x,clipped.to.x),max(clipped.from.y,clipped.to.y))
            cells(box) { key -> buckets.getOrPut(key) { mutableListOf() }+=clipped }
        }
    }
    fun intersects(rect: ScreenRect): Boolean {
        var found=false
        cells(rect) { key -> if(!found) found=buckets[key]?.any { segmentIntersects(it,rect) }==true }
        return found
    }
    private inline fun cells(rect: ScreenRect,visit: (Long)->Unit) {
        val x0=(rect.left.coerceAtLeast(0f)/cell).toInt()
        val x1=(rect.right.coerceIn(0f,viewport.right.coerceAtLeast(0f))/cell).toInt()
        val y0=(rect.top.coerceAtLeast(0f)/cell).toInt()
        val y1=(rect.bottom.coerceIn(0f,viewport.bottom.coerceAtLeast(0f))/cell).toInt()
        for(x in x0..x1) for(y in y0..y1) visit((x.toLong() shl 32) or (y.toLong() and 0xffffffffL))
    }
    private fun clip(line: ScreenLine,rect: ScreenRect): ScreenLine? {
        val dx=line.to.x-line.from.x; val dy=line.to.y-line.from.y
        var enter=0f; var exit=1f
        if(dx==0f) { if(line.from.x<rect.left || line.from.x>rect.right) return null }
        else {
            val a=(rect.left-line.from.x)/dx; val b=(rect.right-line.from.x)/dx
            enter=max(enter,min(a,b)); exit=min(exit,max(a,b))
        }
        if(dy==0f) { if(line.from.y<rect.top || line.from.y>rect.bottom) return null }
        else {
            val a=(rect.top-line.from.y)/dy; val b=(rect.bottom-line.from.y)/dy
            enter=max(enter,min(a,b)); exit=min(exit,max(a,b))
        }
        if(enter>exit) return null
        return ScreenLine(ScreenPoint(line.from.x+dx*enter,line.from.y+dy*enter),ScreenPoint(line.from.x+dx*exit,line.from.y+dy*exit))
    }
}

internal fun segmentIntersects(line: ScreenLine,rect: ScreenRect): Boolean {
    val dx=line.to.x-line.from.x; val dy=line.to.y-line.from.y
    var enter=0f; var exit=1f
    if(dx==0f) { if(line.from.x<rect.left || line.from.x>rect.right) return false }
    else {
        val a=(rect.left-line.from.x)/dx; val b=(rect.right-line.from.x)/dx
        enter=max(enter,min(a,b)); exit=min(exit,max(a,b))
    }
    if(dy==0f) { if(line.from.y<rect.top || line.from.y>rect.bottom) return false }
    else {
        val a=(rect.top-line.from.y)/dy; val b=(rect.bottom-line.from.y)/dy
        enter=max(enter,min(a,b)); exit=min(exit,max(a,b))
    }
    return enter<=exit
}
