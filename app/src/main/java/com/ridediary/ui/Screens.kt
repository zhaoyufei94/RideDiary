package com.ridediary.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ridediary.R
import com.ridediary.core.*
import com.ridediary.data.*
import com.ridediary.recording.RecordingService
import java.time.YearMonth
import kotlinx.coroutines.delay

@Composable fun RideDiaryScreen(onStart: ()->Unit,onStop: ()->Unit,onLanguage: ()->Unit,vm: RideViewModel=viewModel()) {
    val context=LocalContext.current
    val real by vm.realRides.collectAsStateWithLifecycle()
    val realRoutes by vm.realRoutes.collectAsStateWithLifecycle()
    val demo by vm.demo.collectAsStateWithLifecycle()
    val demoRides by vm.demoRides.collectAsStateWithLifecycle()
    val demoRoutes by vm.demoRoutes.collectAsStateWithLifecycle()
    val playing by vm.replaying.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val quality by RecordingService.quality.collectAsStateWithLifecycle()
    val rides=if(demo) demoRides else real
    val routes=if(demo) demoRoutes else realRoutes
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
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
    LaunchedEffect(active?.ride?.id,demo) {
        if(previous!=null && active==null) {
            val finished=rides.firstOrNull { it.ride.id==previous }
            if(finished!=null) { selected=finished.ride.id; tab=1 }
        }
        previous=active?.ride?.id
    }
    LaunchedEffect(demo) { selected=null; previous=null }
    fun begin() {
        if(demo) vm.replay()
        else if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) {
            if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) explain=true else onStart()
        } else explain=true
    }
    Scaffold(containerColor=Background,bottomBar={
        NavigationBar(containerColor=Surface) {
            listOf(R.string.commute,R.string.summary,R.string.statistics,R.string.routes).forEachIndexed { i,res ->
                NavigationBarItem(selected=tab==i,onClick={ tab=i; if(i==1) selected=null },icon={ Text(listOf("◉","✓","▥","⌁")[i],fontSize=22.sp) },label={ Text(stringResource(res),maxLines=2,fontSize=12.sp) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(R.string.app_name)+" / COMMUTE",style=MaterialTheme.typography.labelLarge,color=Muted)
                TextButton(onClick=onLanguage) { Text(stringResource(R.string.language)) }
            }
            if(demo) {
                Panel { Text(stringResource(R.string.demo_banner),color=Ink); TextButton(onClick={ vm.toggleDemo() }) { Text(stringResource(R.string.demo_exit)) } }
            } else if(active==null) TextButton(onClick={ vm.toggleDemo() }) { Text(stringResource(R.string.demo_enter)) }
            if(error) Panel { Text(stringResource(R.string.error)); TextButton(onClick={ vm.error.value=false }) { Text(stringResource(R.string.dismiss)) } }
            when(tab) {
                0 -> {
                    Text(stringResource(if(active==null) R.string.ready else R.string.ride_title),style=MaterialTheme.typography.headlineMedium)
                    if(active!=null) LiveRide(active,routes,rides,demo,quality)
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
                    if(notificationsDenied && !demo) Note(stringResource(R.string.notification_denied))
                    if(quality=="LOCATION_OFF" && active!=null && !demo) TextButton(onClick={ context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text(stringResource(R.string.open_settings)) }
                    if(active==null && quality in listOf("STORAGE_ERROR","START_FAILED","PERMISSION_LOST") && !demo) Note(stringResource(if(quality=="STORAGE_ERROR") R.string.error else if(quality=="START_FAILED") R.string.quality_start else R.string.quality_permission))
                    if(active==null || demo) Button(onClick={ begin() },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(12.dp)) {
                        Text(stringResource(if(demo) if(playing) R.string.pause else R.string.replay else R.string.start))
                    }
                    if(active!=null) OutlinedButton(onClick={ finishDialog=true },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(stringResource(R.string.finish)) }
                    Note(stringResource(R.string.privacy))
                }
                1 -> {
                    val detail=rides.firstOrNull { it.ride.id==selected }
                    if(detail!=null) {
                        TextButton(onClick={ selected=null }) { Text(stringResource(R.string.back)) }
                        RideDetail(detail,routes,rides,vm)
                    } else {
                        Text(stringResource(R.string.history),style=MaterialTheme.typography.headlineMedium)
                        History(rides,routes) { selected=it }
                    }
                }
                2 -> StatsScreen(rides,routes)
                3 -> {
                    Text(stringResource(R.string.routes),style=MaterialTheme.typography.headlineMedium)
                    if(routes.isEmpty()) Panel { Text(stringResource(R.string.empty_routes)) }
                    routes.forEach { route ->
                        Panel {
                            Text(routeName(route.name)+" · "+directionName(route.direction),style=MaterialTheme.typography.titleMedium)
                            Trace(route.route().points)
                            val count=rides.map { it.sample() }.count { Statistics.valid(it) && it.routeId==route.id && it.direction==route.direction }
                            Note(stringResource(R.string.route_metadata,route.route().length/1000,count))
                            Note(stringResource(R.string.start_radius)+": ${route.startRadius.toInt()} m · "+stringResource(R.string.end_radius)+": ${route.endRadius.toInt()} m")
                        }
                    }
                    SectionTitle(stringResource(R.string.history))
                    History(rides,routes) { selected=it; tab=1 }
                }
            }
            Spacer(Modifier.height(8.dp))
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
    if(finishDialog) AlertDialog(onDismissRequest={ finishDialog=false },title={ Text(stringResource(R.string.finish_confirm)) },confirmButton={ TextButton(onClick={ finishDialog=false; if(demo) vm.stopDemo() else onStop() }) { Text(stringResource(R.string.finish)) } },dismissButton={ TextButton(onClick={ finishDialog=false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable private fun LiveRide(data: RideData,routes: List<RouteRecord>,all: List<RideData>,demo: Boolean,quality: String) {
    val sample=data.sample(); val metrics=sample.metrics
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(demo) { while(!demo) { now=System.currentTimeMillis(); delay(1_000) } }
    val points=sample.points
    val latest=points.lastOrNull()
    val fresh=demo || (latest!=null && good(latest) && data.ride.elapsed-latest.t in 0..15_000)
    val route=routes.firstOrNull { it.id==sample.routeId }?.route()
    val match=route?.let { Routing.follow(points,it) }
    val uncertain=!fresh || match==null || match.deviated
    val eta=if(!uncertain) Statistics.estimate(sample,all.map { it.sample() },match) else null
    val status=when { quality=="STORAGE_ERROR"->R.string.error; quality=="LOCATION_OFF"->R.string.quality_off; quality=="PERMISSION_LOST" || quality=="PRECISE_REQUIRED"->R.string.quality_permission; quality=="START_FAILED"->R.string.quality_start; !fresh->R.string.quality_searching; quality=="POOR" || quality=="STALE"->R.string.quality_poor; else->R.string.quality_good }
    Note(stringResource(if(demo) R.string.quality_good else status))
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
                val base=if(demo) sample.startUtc+metrics.elapsed else now
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

@Composable private fun History(rides: List<RideData>,routes: List<RouteRecord>,onSelect: (String)->Unit) {
    if(rides.isEmpty()) Panel { Text(stringResource(R.string.empty_rides)) }
    rides.forEach { data ->
        val s=data.sample(); val route=routes.firstOrNull { it.id==s.routeId }
        OutlinedCard(onClick={ onSelect(s.id) },modifier=Modifier.fillMaxWidth(),colors=CardDefaults.outlinedCardColors(containerColor=Surface),border=BorderStroke(1.dp,Line)) {
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("${date(s.startUtc,s.zone)} · ${clock(s.startUtc,s.zone)}",style=MaterialTheme.typography.titleMedium)
                Text(if(route==null) stringResource(R.string.ordinary) else routeName(route.name)+" · "+directionName(s.direction))
                Text("${duration(s.metrics.elapsed)} · ${decimal(s.metrics.distance/1000)} km",fontFamily=FontFamily.Monospace)
                if(s.state=="INTERRUPTED") Note(stringResource(R.string.interrupted))
                if(s.state=="RECORDING") Note(stringResource(R.string.recording))
                if(s.excluded) Note(stringResource(R.string.excluded))
            }
        }
    }
}

@Composable private fun RideDetail(data: RideData,routes: List<RouteRecord>,all: List<RideData>,vm: RideViewModel) {
    val s=data.sample(); val m=s.metrics
    var create by remember { mutableStateOf(false) }; var edit by remember { mutableStateOf(false) }; var trim by remember { mutableStateOf(false) }
    Text(stringResource(if(data.ride.arrivalConfirmed) R.string.arrival else R.string.details),style=MaterialTheme.typography.headlineMedium)
    Note("${date(s.startUtc,s.zone)} · ${clock(s.startUtc,s.zone)} → ${clock(s.endUtc,s.zone)} · ${s.zone}")
    Panel {
        LargeTime(m.elapsed,44f)
        if(data.ride.arrivalConfirmed) {
            Surface(color=RideCard,shape=RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) { Text("100%",color=Accent); MotorcycleProgress(1.0) }
            }
        }
        Text(routes.firstOrNull { it.id==s.routeId }?.let { routeName(it.name) } ?: stringResource(R.string.no_route))
        Text(directionName(s.direction))
        if(s.state=="INTERRUPTED") Note(stringResource(R.string.interrupted))
        if(s.excluded) { Note(stringResource(R.string.excluded)); if(data.ride.exclusionReason.isNotBlank()) Text(if(data.ride.exclusionReason=="AUTO_ROUTE_CHECK") stringResource(R.string.auto_route_excluded) else data.ride.exclusionReason) }
        if(!m.complete) Note(stringResource(R.string.incomplete))
        if(data.ride.boundaryStart>0 || data.ride.boundaryEnd!=null) Note(stringResource(R.string.trim_note))
        Trace(s.points)
    }
    MetricPair(stringResource(R.string.distance),decimal(m.distance/1000)+" km",stringResource(R.string.average_speed),m.averageKmh?.let { decimal(it)+" km/h" } ?: "—")
    MetricPair(stringResource(R.string.stops),m.stops.toString(),stringResource(R.string.moving_speed),if(m.complete && m.moving>0) decimal(m.distance*3600/m.moving)+" km/h" else "—")
    SectionTitle(stringResource(R.string.time_split)); TimeBar(m)
    Panel {
        DetailRow(stringResource(R.string.moving),duration(m.moving)); DetailRow(stringResource(R.string.waiting),duration(m.waiting)); DetailRow(stringResource(R.string.unknown),duration(m.unknown))
        DetailRow(stringResource(R.string.longest_wait),duration(m.longestWait)); DetailRow(stringResource(R.string.average_wait),duration(m.averageWait))
        DetailRow(stringResource(R.string.wait_share),decimal(m.waiting*100.0/m.elapsed.coerceAtLeast(1))+"%")
        Note(stringResource(R.string.wait_note))
    }
    val compare=Statistics.compare(s,all.map { it.sample() })
    SectionTitle(stringResource(R.string.baseline,compare.n))
    Panel {
        if(compare.n<Policy.MIN_HISTORY || !m.complete) Note(stringResource(R.string.no_baseline))
        else {
            Text(stringResource(R.string.total_delta,delta(m.elapsed-compare.total!!)))
            Text(stringResource(R.string.moving_delta,delta(m.moving-compare.moving!!)))
            Text(stringResource(R.string.waiting_delta,delta(m.waiting-compare.waiting!!)))
            Text(stringResource(R.string.component_delta,delta(m.moving-compare.moving+m.waiting-compare.waiting)))
            Note(stringResource(R.string.baseline_note))
        }
    }
    if(s.state!="RECORDING") {
        val canCreate=s.points.count(::good)>=4 && m.distance>=Policy.MIN_ROUTE_DISTANCE_M
        Button(onClick={ create=true },enabled=canCreate,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.route_create)) }
        if(!canCreate) Note(stringResource(R.string.route_too_short))
        OutlinedButton(onClick={ edit=true },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.route_edit)) }
        TextButton(onClick={ trim=true },modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.boundaries)) }
    }
    if(create) RouteDialog(data,onDismiss={ create=false },onSave={ name,direction,start,end -> vm.createRoute(s.id,name,direction,start,end); create=false })
    if(edit) ClassificationDialog(data,routes,onDismiss={ edit=false },onSave={ route,direction,excluded,reason -> vm.classify(s.id,route,direction,excluded,reason); edit=false })
    if(trim) BoundaryDialog(data,onDismiss={ trim=false },onSave={ start,end -> vm.boundaries(s.id,start,end); trim=false })
}
@Composable private fun DetailRow(label: String,value: String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) { Text(label,Modifier.weight(1f),fontSize=14.sp); Text(value,fontFamily=FontFamily.Monospace,fontSize=14.sp) }
}
