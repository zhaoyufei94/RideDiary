package com.ridediary.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ridediary.R
import com.ridediary.core.*
import com.ridediary.data.*
import com.ridediary.recording.RecordingService
import kotlinx.coroutines.delay

@Composable fun RideDiaryScreen(onStart: ()->Unit,onStop: ()->Unit,onLanguage: ()->Unit,vm: RideViewModel=viewModel()) {
    val context=LocalContext.current
    val rides by vm.rides.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val quality by RecordingService.quality.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var editingRoute by remember { mutableStateOf<RouteRecord?>(null) }
    var deletingRoute by remember { mutableStateOf<RouteRecord?>(null) }
    var deletingRide by remember { mutableStateOf<RideData?>(null) }
    BackHandler(enabled=tab==1 && selected!=null) { selected=null }
    var explain by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var notificationsDenied by remember { mutableStateOf(false) }
    var finishDialog by remember { mutableStateOf(false) }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val precise=results[Manifest.permission.ACCESS_FINE_LOCATION] == true || ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        denied=!precise
        notificationsDenied=Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
        if(precise) onStart()
    }
    val active=rides.firstOrNull { it.ride.state=="RECORDING" }
    var previous by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(active?.ride?.id) {
        if(previous!=null && active==null) {
            val finished=rides.firstOrNull { it.ride.id==previous }
            if(finished!=null) { selected=finished.ride.id; tab=1 }
        }
        previous=active?.ride?.id
    }
    fun begin() {
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) {
            if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) explain=true else onStart()
        } else explain=true
    }
    val pageScroll=key(tab,selected) { rememberScrollState() }
    Scaffold(containerColor=Background,bottomBar={
        NavigationBar(containerColor=Surface) {
            listOf(R.string.commute,R.string.summary,R.string.nav_statistics,R.string.routes).forEachIndexed { i,res ->
                NavigationBarItem(selected=tab==i,onClick={ tab=i; if(i==1) selected=null },icon={ Text(listOf("◉","✓","▥","⌁")[i],fontSize=22.sp) },label={ Text(stringResource(res),maxLines=2,fontSize=12.sp) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(pageScroll).padding(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_ridediary_logo),contentDescription=null,modifier=Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                    Text(stringResource(R.string.app_brand),style=MaterialTheme.typography.labelLarge,color=Muted,modifier=Modifier.weight(1f))
                }
                TextButton(onClick=onLanguage) { Text(stringResource(R.string.language)) }
            }
            if(error) Panel { Text(stringResource(R.string.error)); TextButton(onClick={ vm.error.value=false }) { Text(stringResource(R.string.dismiss)) } }
            when(tab) {
                0 -> {
                    Text(stringResource(if(active==null) R.string.ready else R.string.ride_title),style=MaterialTheme.typography.headlineMedium)
                    if(active!=null) LiveRide(active,routes,rides,quality)
                    else {
                        Panel {
                            Text(stringResource(R.string.tagline),style=MaterialTheme.typography.titleLarge)
                            val last=rides.firstOrNull { it.ride.routeId!=null }
                            val route=routes.firstOrNull { it.id==last?.ride?.routeId }
                            if(route!=null) { Note(stringResource(R.string.last_route)); Text(routeName(route.name)+" · "+directionName(route.direction)) }
                            Note(stringResource(R.string.safety))
                        }
                    }
                    if(denied) Panel {
                        Text(stringResource(R.string.permission_denied))
                        TextButton(onClick={ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:${context.packageName}".toUri())) }) { Text(stringResource(R.string.open_settings)) }
                    }
                    if(notificationsDenied) Note(stringResource(R.string.notification_denied))
                    if(quality=="LOCATION_OFF" && active!=null) TextButton(onClick={ context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text(stringResource(R.string.open_settings)) }
                    if(active==null && quality in listOf("STORAGE_ERROR","START_FAILED","PERMISSION_LOST")) Note(stringResource(if(quality=="STORAGE_ERROR") R.string.error else if(quality=="START_FAILED") R.string.quality_start else R.string.quality_permission))
                    if(active==null) Button(onClick={ begin() },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.start))
                    }
                    if(active!=null) {
                        Note(stringResource(R.string.recording_screen_on))
                        OutlinedButton(onClick={ finishDialog=true },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(stringResource(R.string.finish)) }
                    }
                    Note(stringResource(R.string.privacy))
                }
                1 -> {
                    val detail=rides.firstOrNull { it.ride.id==selected }
                    if(detail!=null) {
                        TextButton(onClick={ selected=null }) { Text(stringResource(R.string.back)) }
                        RideDetail(detail,routes,rides,vm,onDelete={ deletingRide=detail })
                    } else {
                        Text(stringResource(R.string.history),style=MaterialTheme.typography.headlineMedium)
                        History(rides,routes,onDelete={ deletingRide=it }) { selected=it }
                    }
                }
                2 -> StatsScreen(rides,routes)
                3 -> {
                    Text(stringResource(R.string.routes),style=MaterialTheme.typography.headlineMedium)
                    if(routes.isEmpty()) Panel { Text(stringResource(R.string.empty_routes)) }
                    if(routes.isNotEmpty()) Note(stringResource(R.string.route_long_press_hint))
                    routes.forEach { route -> key(route.id) {
                        val inUse=active?.ride?.routeId==route.id
                        ContextMenuPanel(
                            modifier=Modifier.testTag("route-${route.id}"),
                            onLongClickLabel=stringResource(R.string.route_actions),
                            title=routeName(route.name),subtitle=directionName(route.direction)+" · "+decimal(route.route().length/1000)+" km",
                            menu={ dismiss ->
                                SheetAction(title=stringResource(R.string.edit_route),subtitle=stringResource(if(inUse) R.string.route_in_use else R.string.action_edit_route_hint),glyph=Glyph.EDIT,enabled=!inUse,onClick={ dismiss(); editingRoute=route })
                                SheetAction(title=stringResource(R.string.delete_route),subtitle=stringResource(R.string.action_delete_route_hint),glyph=Glyph.DELETE,enabled=!inUse,destructive=true,onClick={ dismiss(); deletingRoute=route })
                            }
                        ) {
                            Text(routeName(route.name)+" · "+directionName(route.direction),style=MaterialTheme.typography.titleMedium)
                            Trace(route.route().points)
                            val count=rides.map { it.sample() }.count { Statistics.valid(it) && it.routeId==route.id && it.direction==route.direction }
                            Note(stringResource(R.string.route_metadata,route.route().length/1000,count))
                            Note(stringResource(R.string.start_radius)+": ${route.startRadius.toInt()} m · "+stringResource(R.string.end_radius)+": ${route.endRadius.toInt()} m")
                            if(inUse) Note(stringResource(R.string.route_in_use))
                        }
                    } }
                    SectionTitle(stringResource(R.string.history))
                    History(rides,routes,onDelete={ deletingRide=it }) { selected=it; tab=1 }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
    editingRoute?.let { route ->
        RouteDialog(points=route.route().points,route=route,onDismiss={ editingRoute=null },onSave={ name,direction,start,end ->
            vm.editRoute(route.id,name,direction,start,end); editingRoute=null
        })
    }
    deletingRoute?.let { route ->
        DeleteDialog(title=stringResource(R.string.delete_route),message=stringResource(R.string.delete_route_message,routeName(route.name)),onDismiss={ deletingRoute=null }) {
            vm.deleteRoute(route.id); deletingRoute=null
        }
    }
    deletingRide?.let { data ->
        val sample=data.sample()
        DeleteDialog(title=stringResource(R.string.delete_ride),message=stringResource(R.string.delete_ride_message,date(sample.startUtc,sample.zone)+" "+clock(sample.startUtc,sample.zone)),onDismiss={ deletingRide=null }) {
            vm.deleteRide(data.ride.id) { if(selected==data.ride.id) selected=null }
            deletingRide=null
        }
    }
    if(explain) AlertDialog(onDismissRequest={ explain=false },title={ Text(stringResource(R.string.permission_title)) },text={ Text(stringResource(R.string.permission_body)) },confirmButton={
        TextButton(onClick={
            explain=false
            val permissions=mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)
            if(Build.VERSION.SDK_INT>=33) permissions+=Manifest.permission.POST_NOTIFICATIONS
            permission.launch(permissions.toTypedArray())
        }) { Text(stringResource(R.string.permission_allow)) }
    },dismissButton={ TextButton(onClick={ explain=false }) { Text(stringResource(R.string.cancel)) } })
    if(finishDialog) AlertDialog(onDismissRequest={ finishDialog=false },title={ Text(stringResource(R.string.finish_confirm)) },confirmButton={ TextButton(onClick={ finishDialog=false; onStop() }) { Text(stringResource(R.string.finish)) } },dismissButton={ TextButton(onClick={ finishDialog=false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun LiveRide(data: RideData,routes: List<RouteRecord>,all: List<RideData>,quality: String) {
    val sample=data.sample(); val metrics=sample.metrics
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true) { now=System.currentTimeMillis(); delay(1_000) } }
    val points=sample.points
    val latest=points.lastOrNull()
    val fresh=(latest!=null && good(latest) && data.ride.elapsed-latest.t in 0..15_000)
    val route=routes.firstOrNull { it.id==sample.routeId }?.route()
    val match=route?.let { Routing.follow(points,it) }
    val uncertain=!fresh || match==null || match.deviated
    val eta=if(!uncertain) Statistics.estimate(sample,all.map { it.sample() },match) else null
    val status=when { quality=="STORAGE_ERROR"->R.string.error; quality=="LOCATION_OFF"->R.string.quality_off; quality=="PERMISSION_LOST" || quality=="PRECISE_REQUIRED"->R.string.quality_permission; quality=="START_FAILED"->R.string.quality_start; !fresh->R.string.quality_searching; quality=="POOR" || quality=="STALE"->R.string.quality_poor; else->R.string.quality_good }
    Note(stringResource(status))
    Surface(color=RideCard,contentColor=Surface,shape=RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(route!=null) routeName(route.name)+" · "+directionName(route.direction) else stringResource(R.string.ordinary),fontSize=18.sp)
            Text(stringResource(R.string.elapsed),color=Accent,fontSize=14.sp)
            LargeTime(metrics.elapsed)
            Text(stringResource(if(route==null) R.string.matching else if(uncertain) R.string.deviated else R.string.matched),fontSize=14.sp,color=Accent)
            if(match!=null && !uncertain) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(stringResource(R.string.progress),modifier=Modifier.weight(1f)); Text("${(match.progress*100).toInt()}%",fontFamily=FontFamily.Monospace) }
                MotorcycleProgress(match.progress)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(stringResource(R.string.origin)); Text(stringResource(R.string.remaining,(route!!.length-match.along).coerceAtLeast(0.0)/1000)) }
            } else Text(stringResource(if(route==null) R.string.unavailable else R.string.deviated),fontSize=14.sp)
            HorizontalDivider(color=Color(0xff50654d))
            Text(stringResource(R.string.eta),color=Accent)
            Text(if(eta==null) stringResource(R.string.learning) else {
                val base=now
                "${clock(base+eta.low,sample.zone)}–${clock(base+eta.high,sample.zone)}"
            },fontSize=20.sp)
            if(eta!=null) Text(stringResource(R.string.comparison_position,delta(eta.elapsedDifference),eta.n),fontSize=14.sp)
        }
    }
    MetricPair(stringResource(R.string.average_speed),metrics.averageKmh?.let { decimal(it)+" km/h" } ?: "—",stringResource(R.string.waiting),duration(metrics.waiting))
    val last=metrics.segments.lastOrNull { it.motion != Motion.UNKNOWN }
    if(fresh && latest?.speed != null && latest.speed < Policy.STOP_EXIT && last?.motion==Motion.WAITING) Note(stringResource(R.string.current_wait,duration(last.duration)))
    SectionTitle(stringResource(R.string.time_split)); TimeBar(metrics)
}

@Composable private fun History(rides: List<RideData>,routes: List<RouteRecord>,onDelete: (RideData)->Unit,onSelect: (String)->Unit) {
    if(rides.isEmpty()) Panel { Text(stringResource(R.string.empty_rides)) }
    else Note(stringResource(R.string.ride_long_press_hint))
    rides.forEach { data -> key(data.ride.id) {
        val s=data.sample(); val route=routes.firstOrNull { it.id==s.routeId }
        ContextMenuPanel(
            modifier=Modifier.testTag("ride-${s.id}"),onClick={ onSelect(s.id) },
            onLongClickLabel=stringResource(R.string.ride_actions),canManage=s.state!="RECORDING",
            title=date(s.startUtc,s.zone)+" · "+clock(s.startUtc,s.zone),subtitle=duration(s.metrics.elapsed)+" · "+decimal(s.metrics.distance/1000)+" km",
            menu={ dismiss ->
                SheetAction(title=stringResource(R.string.delete_ride),subtitle=stringResource(R.string.action_delete_ride_hint),glyph=Glyph.DELETE,destructive=true,onClick={ dismiss(); onDelete(data) })
            }
        ) {
            Text("${date(s.startUtc,s.zone)} · ${clock(s.startUtc,s.zone)}",style=MaterialTheme.typography.titleMedium)
            Text(if(route==null) stringResource(R.string.ordinary) else routeName(route.name)+" · "+directionName(s.direction))
            Text("${duration(s.metrics.elapsed)} · ${decimal(s.metrics.distance/1000)} km",fontFamily=FontFamily.Monospace)
            if(s.state=="INTERRUPTED") Note(stringResource(R.string.interrupted))
            if(s.state=="RECORDING") Note(stringResource(R.string.recording))
            if(s.excluded) Note(stringResource(R.string.excluded))
        }
    } }
}

@Composable private fun RideDetail(data: RideData,routes: List<RouteRecord>,all: List<RideData>,vm: RideViewModel,onDelete: ()->Unit) {
    val s=data.sample(); val m=s.metrics
    val route=routes.firstOrNull { it.id==s.routeId }
    var create by remember(s.id) { mutableStateOf(false) }
    var edit by remember(s.id) { mutableStateOf(false) }
    var trim by remember(s.id) { mutableStateOf(false) }
    Text(stringResource(if(data.ride.arrivalConfirmed) R.string.arrival else R.string.details),fontSize=28.sp,fontWeight=FontWeight.SemiBold)
    Text(date(s.startUtc,s.zone)+" · "+clock(s.startUtc,s.zone)+" – "+clock(s.endUtc,s.zone),fontSize=14.sp,color=Muted)
    if(s.state!="RECORDING") Text(stringResource(R.string.detail_long_press_hint),fontSize=13.sp,color=Muted)
    ContextMenuPanel(
        modifier=Modifier.testTag("ride-detail-${s.id}"),containerColor=RideCard,cornerRadius=26.dp,contentPadding=24.dp,
        onLongClickLabel=stringResource(R.string.ride_actions),canManage=s.state!="RECORDING",
        title=date(s.startUtc,s.zone)+" · "+clock(s.startUtc,s.zone),subtitle=duration(m.elapsed)+" · "+decimal(m.distance/1000)+" km",
        menu={ dismiss ->
            SheetAction(title=stringResource(R.string.route_edit),subtitle=stringResource(R.string.action_classify_hint),glyph=Glyph.EDIT,onClick={ dismiss(); edit=true })
            SheetAction(title=stringResource(R.string.boundaries),subtitle=stringResource(R.string.action_boundary_hint),glyph=Glyph.ADJUST,onClick={ dismiss(); trim=true })
            SheetAction(title=stringResource(R.string.delete_ride),subtitle=stringResource(R.string.action_delete_ride_hint),glyph=Glyph.DELETE,destructive=true,onClick={ dismiss(); onDelete() })
        }
    ) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(route?.let { routeName(it.name) } ?: stringResource(R.string.ordinary),fontSize=19.sp,fontWeight=FontWeight.Medium,color=Surface)
                Text(directionName(s.direction),fontSize=13.sp,color=Accent)
            }
            AppGlyph(Glyph.ROUTE,Modifier.size(26.dp),Accent)
        }
        Text(stringResource(R.string.total_time),Modifier.padding(top=12.dp),fontSize=13.sp,color=Surface.copy(alpha=.7f))
        LargeTime(m.elapsed,48f,Surface)
        if(data.ride.arrivalConfirmed) { Text("100%",fontSize=13.sp,color=Accent); MotorcycleProgress(1.0) }
        HorizontalDivider(Modifier.padding(vertical=10.dp),color=Surface.copy(alpha=.16f))
        if(LocalDensity.current.fontScale>1.3f) {
            RideHeroMetric(stringResource(R.string.distance),decimal(m.distance/1000)+" km")
            RideHeroMetric(stringResource(R.string.average_speed),m.averageKmh?.let { decimal(it)+" km/h" } ?: "—")
        } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
            RideHeroMetric(stringResource(R.string.distance),decimal(m.distance/1000)+" km",Modifier.weight(1f))
            RideHeroMetric(stringResource(R.string.average_speed),m.averageKmh?.let { decimal(it)+" km/h" } ?: "—",Modifier.weight(1f))
        }
    }
    if(s.state=="INTERRUPTED" || s.state=="RECORDING" || s.excluded || !m.complete || data.ride.boundaryStart>0 || data.ride.boundaryEnd!=null) {
        Surface(shape=RoundedCornerShape(18.dp),color=Soft.copy(alpha=.65f)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                if(s.state=="INTERRUPTED") Note(stringResource(R.string.interrupted))
                if(s.state=="RECORDING") Note(stringResource(R.string.recording))
                if(s.excluded) {
                    Note(stringResource(R.string.excluded))
                    if(data.ride.exclusionReason.isNotBlank()) Note(if(data.ride.exclusionReason=="AUTO_ROUTE_CHECK") stringResource(R.string.auto_route_excluded) else data.ride.exclusionReason)
                }
                if(!m.complete) Note(stringResource(R.string.incomplete))
                if(data.ride.boundaryStart>0 || data.ride.boundaryEnd!=null) Note(stringResource(R.string.trim_note))
            }
        }
    }
    SectionHeading(stringResource(R.string.stats_time_composition),stringResource(R.string.ride_time_caption))
    DataCard { TimeComposition(m.elapsed,m.moving,m.waiting,m.unknown) }
    SectionHeading(stringResource(R.string.ride_stops_title),stringResource(R.string.wait_note))
    DataCard(Modifier.testTag("ride-stops")) {
        val entries=listOf(
            stringResource(R.string.stops) to m.stops.toString(),
            stringResource(R.string.longest_wait) to duration(m.longestWait),
            stringResource(R.string.average_wait) to duration(m.averageWait),
            stringResource(R.string.moving_speed) to if(m.complete && m.moving>0) decimal(m.distance*3600/m.moving)+" km/h" else "—"
        )
        entries.chunked(2).forEachIndexed { index,pair ->
            if(index>0) HorizontalDivider(Modifier.padding(vertical=6.dp),color=Line.copy(alpha=.6f))
            if(LocalDensity.current.fontScale>1.3f) pair.forEach { (label,value) -> RideMetric(label,value) }
            else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                pair.forEach { (label,value) -> RideMetric(label,value,Modifier.weight(1f)) }
            }
        }
    }
    SectionHeading(stringResource(R.string.ride_trace_title),stringResource(R.string.ride_trace_caption))
    DataCard(Modifier.testTag("ride-trace")) { Trace(s.points) }
    val compare=Statistics.compare(s,all.map { it.sample() })
    SectionHeading(stringResource(R.string.baseline,compare.n),stringResource(R.string.ride_comparison_caption))
    DataCard(Modifier.testTag("ride-comparison")) {
        if(compare.n<Policy.MIN_HISTORY || !m.complete) {
            AppGlyph(Glyph.CHART,Modifier.size(26.dp),Moving)
            Text(stringResource(R.string.no_baseline),fontSize=16.sp,lineHeight=24.sp,color=Muted)
        } else {
            Text(stringResource(R.string.ride_total_comparison),fontSize=14.sp,color=Muted)
            Text(delta(m.elapsed-compare.total!!),fontSize=34.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium,color=if(m.elapsed>compare.total) Danger else Moving)
            HorizontalDivider(Modifier.padding(vertical=6.dp),color=Line.copy(alpha=.6f))
            val movingDelta=m.moving-compare.moving!!; val waitDelta=m.waiting-compare.waiting!!
            if(LocalDensity.current.fontScale>1.3f) {
                RideMetric(stringResource(R.string.moving),delta(movingDelta),valueColor=if(movingDelta>0) Danger else Moving)
                RideMetric(stringResource(R.string.waiting),delta(waitDelta),valueColor=if(waitDelta>0) Danger else Moving)
            } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                RideMetric(stringResource(R.string.moving),delta(movingDelta),Modifier.weight(1f),if(movingDelta>0) Danger else Moving)
                RideMetric(stringResource(R.string.waiting),delta(waitDelta),Modifier.weight(1f),if(waitDelta>0) Danger else Moving)
            }
            Text(stringResource(R.string.component_delta,delta(movingDelta+waitDelta)),fontSize=13.sp,color=Muted)
            Text(stringResource(R.string.baseline_note),fontSize=12.sp,lineHeight=20.sp,color=Muted)
        }
    }
    if(s.state!="RECORDING") {
        val canCreate=s.points.count(::good)>=4 && m.distance>=Policy.MIN_ROUTE_DISTANCE_M
        Button(onClick={ create=true },enabled=canCreate,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),shape=RoundedCornerShape(16.dp)) {
            AppGlyph(Glyph.ROUTE,Modifier.size(20.dp),if(canCreate) Surface else Muted)
            Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.route_create))
        }
        if(!canCreate) Note(stringResource(R.string.route_too_short))
    }
    if(create) RouteDialog(points=s.points,onDismiss={ create=false },onSave={ name,direction,start,end -> vm.createRoute(s.id,name,direction,start,end); create=false })
    if(edit) ClassificationDialog(data,routes,onDismiss={ edit=false },onSave={ selectedRoute,direction,excluded,reason -> vm.classify(s.id,selectedRoute,direction,excluded,reason); edit=false })
    if(trim) BoundaryDialog(data,onDismiss={ trim=false },onSave={ start,end -> vm.boundaries(s.id,start,end); trim=false })
}
@Composable private fun RideHeroMetric(label: String,value: String,modifier: Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Text(label,fontSize=13.sp,color=Surface.copy(alpha=.7f))
        Text(value,fontSize=22.sp,color=Surface,fontFamily=FontFamily.Monospace)
    }
}
@Composable private fun RideMetric(label: String,value: String,modifier: Modifier=Modifier,valueColor: Color=Ink) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Text(label,fontSize=13.sp,color=Muted)
        Text(value,fontSize=22.sp,color=valueColor,fontWeight=FontWeight.Medium,fontFamily=FontFamily.Monospace)
    }
}
