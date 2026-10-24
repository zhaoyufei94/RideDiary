package com.ridediary.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridediary.R
import com.ridediary.RideDiaryApp
import com.ridediary.core.*
import com.ridediary.maps.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.*

@Composable fun Trace(points: List<Fix>,height: Dp=150.dp,metrics: Metrics?=null,live: Boolean=false,current: Fix?=null,visible: Boolean=true) {
    val config by (LocalContext.current.applicationContext as RideDiaryApp).mapSettings.config.collectAsStateWithLifecycle()
    val geometry by produceState<TrackGeometry?>(null,points,metrics) {
        value=withContext(Dispatchers.Default) { TrackPresentation.build(points,metrics) }
    }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val value=geometry
    val openLabel=stringResource(R.string.track_open)
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(10.dp))) {
            if(value!=null) TrackSurface(value,current,interactive=false,nativeAllowed=live && visible && !expanded,fitToken=0,locateToken=0,modifier=Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize().background(Soft),contentAlignment=Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp)) }
            Box(Modifier.fillMaxSize().clickable(onClickLabel=openLabel) { expanded=true }.testTag("track-preview"))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
            Note(openLabel)
            if(live) Text(stringResource(if(current==null) R.string.quality_searching else R.string.track_live_position),fontSize=11.sp,color=Muted)
        }
        if(value?.speedColored==true && config?.appearance?.speedColors!=false) SpeedLegend()
    }
    if(expanded && value!=null) TrackViewer(value,current,live) { expanded=false }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun TrackViewer(geometry: TrackGeometry,current: Fix?,live: Boolean,onDismiss: ()->Unit) {
    var fitToken by remember { mutableIntStateOf(0) }
    var locateToken by remember { mutableIntStateOf(0) }
    var localOnly by rememberSaveable { mutableStateOf(false) }
    var settings by remember { mutableStateOf(false) }
    var displayOptions by remember { mutableStateOf(false) }
    val config by (LocalContext.current.applicationContext as RideDiaryApp).mapSettings.config.collectAsStateWithLifecycle()
    val appearance=config?.appearance ?: MapAppearance()
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize().testTag("track-viewer"),color=Background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    HeaderIconButton(Glyph.LEFT,stringResource(R.string.back),onClick=onDismiss)
                    Text(stringResource(R.string.ride_trace_title),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    TextButton(onClick={ displayOptions=true },modifier=Modifier.testTag("track-display-options")) { Text(stringResource(R.string.map_display)) }
                    HeaderIconButton(Glyph.SETTINGS,stringResource(R.string.map_settings)) { settings=true }
                }
                TrackSurface(geometry,current,interactive=true,nativeAllowed=!localOnly,fitToken=fitToken,locateToken=locateToken,
                    modifier=Modifier.fillMaxWidth().weight(1f))
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(geometry.speedColored && appearance.speedColors) SpeedLegend()
                    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={ fitToken++ },modifier=Modifier.testTag("track-fit")) { Text(stringResource(R.string.track_fit)) }
                        if(live) OutlinedButton(onClick={ locateToken++ },enabled=current!=null) { Text(stringResource(R.string.track_follow)) }
                        TextButton(onClick={ localOnly=!localOnly }) { Text(stringResource(if(localOnly) R.string.track_use_map else R.string.track_use_local)) }
                    }
                    if(geometry.stops.isNotEmpty() && appearance.stops) Text(stringResource(R.string.track_stop_hint),fontSize=11.sp,color=Muted)
                }
            }
        }
    }
    if(settings) MapSettingsDialog { settings=false }
    if(displayOptions) MapDisplayDialog { displayOptions=false }
}

@Composable private fun SpeedLegend() {
    Row(Modifier.fillMaxWidth().testTag("track-speed-legend"),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("0",fontSize=10.sp,color=Muted)
        Box(Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Brush.horizontalGradient(
            listOf(0.0,20.0,40.0,60.0).map { Color(TrackPresentation.speedColor(it)) })))
        Text("60+ km/h",fontSize=10.sp,color=Muted)
        Box(Modifier.size(6.dp).background(Color(TrackPresentation.UNKNOWN_COLOR),RoundedCornerShape(2.dp)))
        Text(stringResource(R.string.unknown),fontSize=10.sp,color=Muted)
    }
}

@Composable private fun rememberOnline(): Boolean {
    val context=LocalContext.current
    val manager=remember(context) { context.getSystemService(ConnectivityManager::class.java) }
    fun online()=runCatching { manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true }.getOrDefault(false)
    var available by remember(manager) { mutableStateOf(online()) }
    DisposableEffect(manager) {
        val callback=object: ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { available=online() }
            override fun onLost(network: Network) { available=online() }
            override fun onCapabilitiesChanged(network: Network,capabilities: NetworkCapabilities) { available=online() }
        }
        val registered=runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess
        onDispose { if(registered) runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return available
}

@Composable private fun TrackSurface(geometry: TrackGeometry,current: Fix?,interactive: Boolean,nativeAllowed: Boolean,
    fitToken: Int,locateToken: Int,modifier: Modifier) {
    val context=LocalContext.current
    val config by (context.applicationContext as RideDiaryApp).mapSettings.config.collectAsStateWithLifecycle()
    val authError by BaiduRuntime.authError.collectAsStateWithLifecycle()
    val online=rememberOnline()
    var retry by remember { mutableIntStateOf(0) }
    var initialized by remember(config?.apiKey,nativeAllowed,online) { mutableStateOf(false) }
    var failed by remember(config?.apiKey,nativeAllowed,online) { mutableStateOf(false) }
    val appearance=(config?.appearance ?: MapAppearance()).forSurface(interactive)
    var mapLoaded by remember(config?.apiKey,config?.styleId,appearance.style,nativeAllowed,online,retry) { mutableStateOf(false) }
    var styleFailed by remember(config?.styleId,appearance.style) { mutableStateOf(appearance.style==MapStyle.CUSTOM && config?.styleId.isNullOrBlank()) }
    val ready=config?.ready==true
    val hasPosition=geometry.points.isNotEmpty() || current!=null
    LaunchedEffect(config?.apiKey,nativeAllowed,ready,online,hasPosition,retry) {
        if(nativeAllowed && ready && online && hasPosition) {
            initialized=BaiduRuntime.ensure(context,config!!)
            failed=!initialized
        }
    }
    val native=nativeAllowed && ready && online && initialized && !authError && !failed && (geometry.points.isNotEmpty() || current!=null)
    LaunchedEffect(native,config?.styleId,appearance.style,retry) {
        if(native) { delay(15_000); if(!mapLoaded) failed=true }
    }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(if(interactive) 0.dp else 10.dp))) {
            if(native) {
                key(config!!.apiKey,config!!.styleId,appearance.style,LocalDensity.current.fontScale,LocalConfiguration.current.locales.toLanguageTags(),retry) {
                    NativeTrack(geometry,current,config!!.copy(appearance=appearance),interactive,fitToken,locateToken,{ failed=true },{ styleFailed=true },{ mapLoaded=true })
                }
            } else LocalTrackCanvas(geometry,current,interactive,fitToken,locateToken,appearance,Modifier.fillMaxSize())
        }
        if(interactive) {
            val message=when {
                !nativeAllowed->R.string.track_local_mode
                !ready->R.string.map_not_configured
                !online->R.string.map_offline
                failed || authError->R.string.map_failed
                styleFailed->R.string.map_style_failed
                else->R.string.map_attribution
            }
            Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(message),Modifier.weight(1f).padding(vertical=6.dp),fontSize=11.sp,color=Muted)
                if(nativeAllowed && ready && (failed || authError)) TextButton(onClick={ retry++ }) { Text(stringResource(R.string.map_retry)) }
            }
        }
    }
}

@Composable private fun NativeTrack(geometry: TrackGeometry,current: Fix?,config: MapConfig,interactive: Boolean,fit: Int,locate: Int,
    onFailure: ()->Unit,onStyleFailure: ()->Unit,onLoaded: ()->Unit) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val holder=remember { arrayOfNulls<BaiduTrackController>(1) }
    val label=stringResource(R.string.track_stop_label)
    val styleFailure by rememberUpdatedState(onStyleFailure)
    val loaded by rememberUpdatedState(onLoaded)
    val failure by rememberUpdatedState(onFailure)
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,event ->
            when(event) {
                Lifecycle.Event.ON_RESUME->holder[0]?.resume()
                Lifecycle.Event.ON_PAUSE->holder[0]?.pause()
                Lifecycle.Event.ON_DESTROY->holder[0]?.destroy()
                else->Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); holder[0]?.destroy(); holder[0]=null }
    }
    AndroidView(modifier=Modifier.fillMaxSize(),factory={ context ->
        try {
            BaiduTrackController(context,config,interactive,{ "$label ${duration(it)}" },{ styleFailure() },{ loaded() },{ failure() }).also {
                holder[0]=it
                if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) it.resume()
            }.view
        } catch(_: Exception) {
            onFailure(); android.widget.FrameLayout(context)
        } catch(_: LinkageError) {
            onFailure(); android.widget.FrameLayout(context)
        }
    },update={
        try { holder[0]?.render(geometry,current,fit,locate,config.appearance) }
        catch(_: Exception) { onFailure() }
        catch(_: LinkageError) { onFailure() }
    })
}

@Composable private fun LocalTrackCanvas(geometry: TrackGeometry,current: Fix?,interactive: Boolean,fit: Int,locate: Int,appearance: MapAppearance,modifier: Modifier) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val density=LocalDensity.current
    val padding=with(density) { 32.dp.toPx() }
    val points=geometry.points
    val cosLat=remember(points) { cos(Math.toRadians(points.firstOrNull()?.lat ?: 0.0)).coerceAtLeast(.001) }
    val measuredBounds=remember(points,cosLat) {
        val xs=points.map { it.lon*cosLat }; val ys=points.map { it.lat }
        val minX=xs.minOrNull() ?: 0.0; val maxX=xs.maxOrNull() ?: minX
        val minY=ys.minOrNull() ?: 0.0; val maxY=ys.maxOrNull() ?: minY
        doubleArrayOf((minX+maxX)/2,(minY+maxY)/2,(maxX-minX).coerceAtLeast(.0001),(maxY-minY).coerceAtLeast(.0001))
    }
    val bounds=if(interactive) remember(fit,points.isEmpty()) { measuredBounds } else measuredBounds
    fun baseScale()=min(((canvasSize.width-padding*2).coerceAtLeast(1f))/bounds[2],((canvasSize.height-padding*2).coerceAtLeast(1f))/bounds[3])
    LaunchedEffect(fit) { zoom=1f; pan=Offset.Zero }
    LaunchedEffect(locate) {
        if(locate>0 && current!=null) {
            zoom=max(3f,zoom)
            pan=Offset((-(current.lon*cosLat-bounds[0])*baseScale()*zoom).toFloat(),((current.lat-bounds[1])*baseScale()*zoom).toFloat())
        }
    }
    val stopLabel=stringResource(R.string.track_stop_label)
    val description=stringResource(if(points.isEmpty()) R.string.no_trace else R.string.track_accessibility)
    val callouts=remember(density) { StopCalloutPainter(density.density,density.fontScale) }
    Box(modifier.background(Soft)) {
        Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(0.dp)).onSizeChanged { canvasSize=it }
            .semantics { contentDescription=description }
            .then(if(interactive) Modifier.pointerInput(Unit) {
                detectTransformGestures { centroid,delta,change,_ ->
                    val next=(zoom*change).coerceIn(1f,40f)
                    val ratio=next/zoom
                    val anchor=centroid-Offset(size.width/2f,size.height/2f)
                    pan=(pan-anchor)*ratio+anchor+delta
                    zoom=next
                    pan=Offset(pan.x.coerceIn(-size.width*zoom,size.width*zoom),pan.y.coerceIn(-size.height*zoom,size.height*zoom))
                }
            } else Modifier)) {
            if(points.isEmpty()) return@Canvas
            val scale=baseScale()*zoom
            fun at(fix: Fix)=Offset(((fix.lon*cosLat-bounds[0])*scale+size.width/2+pan.x).toFloat(),(-(fix.lat-bounds[1])*scale+size.height/2+pan.y).toFloat())
            val colorWidth=(if(interactive) 4.5.dp else 5.dp).toPx()
            // Paint every casing first: a later short segment's round white cap must
            // never erase the speed color of an earlier segment at overview scale.
            geometry.edges.forEach { edge ->
                drawLine(Color.White,at(edge.from),at(edge.to),colorWidth+2.dp.toPx(),StrokeCap.Round)
            }
            geometry.edges.forEach { edge ->
                drawLine(Color(if(appearance.speedColors) edge.color else TrackPresentation.ROUTE_COLOR),at(edge.from),at(edge.to),colorWidth,StrokeCap.Round)
            }
            if(appearance.endpoints) {
                drawCircle(Color.White,7.dp.toPx(),at(points.first()))
                drawCircle(Ink,7.dp.toPx(),at(points.first()),style=Stroke(2.dp.toPx()))
                drawCircle(Ink,6.dp.toPx(),at(points.last()))
            }
            if(current!=null) {
                val point=at(current)
                drawCircle(Color(0xff3878cb).copy(alpha=.12f),(current.accuracy*scale/111_195).toFloat().coerceIn(10.dp.toPx(),80.dp.toPx()),point)
                drawCircle(Color.White,8.dp.toPx(),point); drawCircle(Color(0xff3878cb),5.dp.toPx(),point)
            }
            if(appearance.stops) {
                val obstacles=mutableListOf<ScreenRect>()
                fun markerAt(fix: Fix,radius: Float) { val p=at(fix); obstacles+=ScreenRect(p.x-radius,p.y-radius,p.x+radius,p.y+radius) }
                if(appearance.endpoints) { markerAt(points.first(),8.dp.toPx()); markerAt(points.last(),8.dp.toPx()) }
                if(current!=null) markerAt(current,9.dp.toPx())
                geometry.stops.forEach { markerAt(it.point,4.dp.toPx()) }
                val layout=StopCalloutLayout(size.width,size.height,density.density,
                    geometry.edges.map { val a=at(it.from); val b=at(it.to); ScreenLine(ScreenPoint(a.x,a.y),ScreenPoint(b.x,b.y)) },obstacles)
                geometry.stops.sortedByDescending { it.duration }.forEach { stop ->
                    val point=at(stop.point)
                    val text="$stopLabel ${duration(stop.duration)}"
                    val placement=layout.place(ScreenPoint(point.x,point.y),callouts.width(text),callouts.height)
                    if(placement!=null) callouts.draw(drawContext.canvas.nativeCanvas,placement,text)
                }
                // Keep every stop's true position visible even when its bubble is suppressed.
                geometry.stops.forEach { stop ->
                    val point=at(stop.point)
                    if(point.x in 0f..size.width && point.y in 0f..size.height) callouts.drawPoint(drawContext.canvas.nativeCanvas,point.x,point.y)
                }
            }
        }
        if(points.isEmpty()) Text(description,Modifier.align(Alignment.Center).padding(16.dp),color=Muted)
    }
}
