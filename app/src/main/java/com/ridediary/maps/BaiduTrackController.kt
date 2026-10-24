package com.ridediary.maps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.graphics.createBitmap
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.baidu.mapapi.utils.CoordinateConverter
import com.ridediary.core.*

/** One native map per visible surface. Coordinates are converted only for display. */
class BaiduTrackController(context: Context,config: MapConfig,private val interactive: Boolean,
    private val stopLabel: (Long)->String,private val onStyleFailure: ()->Unit,private val onLoaded: ()->Unit,
    private val onFailure: ()->Unit) {
    val view=TextureMapView(context,BaiduMapOptions().zoomControlsEnabled(false).scaleControlEnabled(true)
        .compassEnabled(false).rotateGesturesEnabled(false).overlookingGesturesEnabled(false)
        .scrollGesturesEnabled(interactive).zoomGesturesEnabled(interactive))
    private val map=view.map
    private val density=context.resources.displayMetrics.density
    private val converted=HashMap<Pair<Double,Double>,LatLng>()
    private val lines=mutableListOf<Pair<BaiduTrackLine,Polyline>>()
    private data class StopOverlay(val stop: TrackStop,val dot: Marker,var bubble: Marker?=null,var icon: BitmapDescriptor?=null,var slot: Int=-1)
    private var labels=emptyList<StopOverlay>()
    private var appearance=config.appearance
    private val callouts=StopCalloutPainter(density,context.resources.configuration.fontScale)
    private val stopIcon=BitmapDescriptorFactory.fromBitmap(callouts.pointBitmap())
    private var geometry: TrackGeometry?=null
    private var current: Fix?=null
    private var loaded=false
    private var destroyed=false
    private var fitted=false
    private var following=true
    private var lastFocused: LatLng?=null
    private var fitToken=-1
    private var locateToken=-1
    private var start: Marker?=null
    private var end: Marker?=null
    private val startIcon=icon("S",0xff315d49.toInt())
    private val endIcon=icon("E",0xffaa5b42.toInt())

    init {
        map.isBuildingsEnabled=appearance.style==MapStyle.STANDARD
        map.mapType=if(appearance.style==MapStyle.SATELLITE) BaiduMap.MAP_TYPE_SATELLITE else BaiduMap.MAP_TYPE_NORMAL
        map.setIndoorEnable(false)
        map.isTrafficEnabled=appearance.traffic
        map.showMapPoi(appearance.places)
        map.setOverlayUnderPoi(false)
        map.setOnMapLoadedCallback {
            safely { loaded=true; geometry?.let(::renderLines); focus(); updateLabelVisibility(); onLoaded() }
        }
        map.setOnMapStatusChangeListener(object: BaiduMap.OnMapStatusChangeListener {
            override fun onMapStatusChangeStart(status: MapStatus) = Unit
            override fun onMapStatusChangeStart(status: MapStatus,reason: Int) {
                if(reason==BaiduMap.OnMapStatusChangeListener.REASON_GESTURE) following=false
            }
            override fun onMapStatusChange(status: MapStatus) = Unit
            override fun onMapStatusChangeFinish(status: MapStatus) { safely { updateLabelVisibility() } }
        })
        view.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> safely { focus(); updateLabelVisibility() } }
        if(appearance.style==MapStyle.CUSTOM && config.styleId.isNotBlank()) {
            view.setMapCustomStyle(MapCustomStyleOptions().customStyleId(config.styleId),object: CustomMapStyleCallBack {
                override fun onPreLoadLastCustomMapStyle(path: String?)=false
                override fun onCustomMapStyleLoadSuccess(updated: Boolean,path: String?)=false
                override fun onCustomMapStyleLoadFailed(status: Int,message: String?,path: String?): Boolean {
                    if(!destroyed) { view.setMapCustomStyleEnable(false); onStyleFailure() }
                    return false
                }
            })
        }
    }
    private inline fun safely(block: ()->Unit) {
        if(destroyed) return
        try { block() }
        catch(_: Exception) { onFailure() }
        catch(_: LinkageError) { onFailure() }
    }
    private fun coordinate(fix: Fix): LatLng=converted.getOrPut(fix.lat to fix.lon) {
        CoordinateConverter().from(CoordinateConverter.CoordType.GPS).coord(LatLng(fix.lat,fix.lon)).convert()
    }
    private fun icon(letter: String,color: Int): BitmapDescriptor {
        val size=(28*density).toInt().coerceAtLeast(28)
        val bitmap=createBitmap(size,size,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=android.graphics.Color.WHITE }
        canvas.drawCircle(size/2f,size/2f,size/2f,paint)
        paint.color=color; canvas.drawCircle(size/2f,size/2f,size/2f-2*density,paint)
        paint.color=android.graphics.Color.WHITE; paint.textSize=13*density; paint.textAlign=Paint.Align.CENTER; paint.isFakeBoldText=true
        canvas.drawText(letter,size/2f,size/2f-(paint.ascent()+paint.descent())/2,paint)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
    private fun renderLines(value: TrackGeometry) {
        val desired=baiduTrackLines(value.edges).map { line ->
            if(appearance.speedColors) line else line.copy(colors=List(line.colors.size) { TrackPresentation.ROUTE_COLOR })
        }
        while(lines.size>desired.size) lines.removeAt(lines.lastIndex).second.remove()
        desired.forEachIndexed { index,model ->
            if(index<lines.size) {
                if(lines[index].first!=model) {
                    // Baidu requires colors to be set before points when updating a multicolor line.
                    lines[index].second.colorList=model.colors.toIntArray()
                    lines[index].second.points=model.points.map(::coordinate)
                    lines[index]=model to lines[index].second
                }
            } else {
                // Ordinary per-segment colors avoid the separate native gradient renderer.
                // Keep one opaque color per edge, and a fixed screen width at every zoom.
                val polyline=map.addOverlay(PolylineOptions().points(model.points.map(::coordinate))
                    .color(model.colors.first()).colorsValues(model.colors).isGradient(false)
                    .widthType(WidthType.PixelWidth).width((if(interactive) 4.5f else 5f)*density)
                    .isThined(false).zIndex(5)) as Polyline
                lines+=model to polyline
            }
        }
    }
    fun render(value: TrackGeometry,position: Fix?,fit: Int,locate: Int,display: MapAppearance) {
        if(destroyed) return
        val changed=geometry!==value || appearance!=display
        appearance=display
        map.isTrafficEnabled=display.traffic
        map.showMapPoi(display.places)
        geometry=value
        current=position?.takeIf(::good)
        if(!interactive && current!=null) following=true
        if(changed) {
            if(loaded) renderLines(value)
            value.points.firstOrNull()?.let { first ->
                if(start==null) start=map.addOverlay(MarkerOptions().position(coordinate(first)).icon(startIcon).anchor(.5f,.5f).zIndex(10)) as Marker
                else start?.position=coordinate(first)
            }
            value.points.lastOrNull()?.let { last ->
                if(end==null) end=map.addOverlay(MarkerOptions().position(coordinate(last)).icon(endIcon).anchor(.5f,.5f).zIndex(10)) as Marker
                else end?.position=coordinate(last)
            }
            val stops=if(interactive && display.stops) value.stops else emptyList()
            if(stops!=labels.map { it.stop }) {
                labels.forEach { removeBubble(it); it.dot.remove() }
                labels=stops.map { stop ->
                    StopOverlay(stop,map.addOverlay(MarkerOptions().position(coordinate(stop.point)).icon(stopIcon)
                        .anchor(.5f,.5f).perspective(false).zIndex(13).visible(display.stops)) as Marker)
                }
            }
            start?.isVisible=display.endpoints
            end?.isVisible=display.endpoints
        }
        map.isMyLocationEnabled=current!=null
        current?.let { fix ->
            val latLng=coordinate(fix)
            map.setMyLocationData(MyLocationData.Builder().latitude(latLng.latitude).longitude(latLng.longitude).accuracy(fix.accuracy.toFloat()).build())
        }
        if(fitToken!=fit) { val previous=fitToken; fitToken=fit; fitted=false; following=previous<0 && current!=null }
        if(locateToken!=locate) { val previous=locateToken; locateToken=locate; if(previous>=0) { following=true; lastFocused=null } }
        focus()
        updateLabelVisibility()
    }
    private fun focus() {
        if(!loaded || destroyed || view.width<=0 || view.height<=0) return
        if(following && current!=null) {
            val point=coordinate(current!!)
            if(!fitted || point!=lastFocused) map.setMapStatus(if(fitted) MapStatusUpdateFactory.newLatLng(point) else MapStatusUpdateFactory.newLatLngZoom(point,17f))
            lastFocused=point
            fitted=true
        } else if(!fitted) {
            val points=geometry?.points.orEmpty()
            if(points.isEmpty()) return
            val coordinates=points.map(::coordinate)
            if(coordinates.distinct().size==1) map.setMapStatus(MapStatusUpdateFactory.newLatLngZoom(coordinates.first(),17f))
            else {
                val bounds=LatLngBounds.Builder().apply { coordinates.forEach { include(it) } }.build()
                val padding=(40*density).toInt()
                map.setMapStatus(MapStatusUpdateFactory.newLatLngBounds(bounds,padding,padding,padding,padding))
            }
            fitted=true
        }
    }
    private fun updateLabelVisibility() {
        if(!loaded || destroyed || view.width==0) return
        if(labels.isEmpty()) return
        val projected=HashMap<Fix,ScreenPoint>()
        fun project(fix: Fix)=projected.getOrPut(fix) {
            val p=map.projection.toScreenLocation(coordinate(fix))
            ScreenPoint(p.x.toFloat(),p.y.toFloat())
        }
        val obstacles=mutableListOf<ScreenRect>()
        fun protect(fix: Fix,radius: Float) {
            val p=project(fix)
            obstacles+=ScreenRect(p.x-radius,p.y-radius,p.x+radius,p.y+radius)
        }
        if(appearance.endpoints) {
            geometry?.points?.firstOrNull()?.let { protect(it,14*density) }
            geometry?.points?.lastOrNull()?.let { protect(it,14*density) }
        }
        current?.let { protect(it,9*density) }
        labels.forEach { protect(it.stop.point,4*density) }
        val track=geometry?.edges.orEmpty().map { ScreenLine(project(it.from),project(it.to)) }
        val layout=StopCalloutLayout(view.width.toFloat(),view.height.toFloat(),density,track,obstacles)
        labels.sortedByDescending { it.stop.duration }.forEach { item ->
            item.dot.isVisible=appearance.stops
            val point=project(item.stop.point)
            val text=stopLabel(item.stop.duration)
            val placement=if(appearance.stops) layout.place(point,callouts.width(text),callouts.height) else null
            if(placement!=null) {
                if(item.bubble==null || item.slot!=placement.slot) {
                    removeBubble(item)
                    val image=callouts.bitmap(text,placement)
                    val icon=BitmapDescriptorFactory.fromBitmap(image.bitmap)
                    item.icon=icon
                    item.bubble=map.addOverlay(MarkerOptions().position(coordinate(item.stop.point)).icon(icon)
                        .anchor(image.anchorX,image.anchorY).perspective(false).zIndex(12)) as Marker
                    item.slot=placement.slot
                }
            } else removeBubble(item)
        }
    }
    private fun removeBubble(item: StopOverlay) {
        item.bubble?.remove(); item.bubble=null
        item.icon?.recycle(); item.icon=null
        item.slot=-1
    }
    fun resume() { if(!destroyed) view.onResume() }
    fun pause() { if(!destroyed) view.onPause() }
    fun destroy() {
        if(destroyed) return
        destroyed=true
        map.setOnMapLoadedCallback(null)
        map.setOnMapStatusChangeListener(null)
        labels.forEach { removeBubble(it); it.dot.remove() }; labels=emptyList()
        view.onPause(); view.onDestroy()
        startIcon.recycle(); endIcon.recycle(); stopIcon.recycle(); converted.clear()
    }
}
