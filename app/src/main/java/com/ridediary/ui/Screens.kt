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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
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
import com.ridediary.weather.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.SystemClock
import java.time.YearMonth

@Composable private fun PageColumn(scroll: androidx.compose.foundation.ScrollState,error: Boolean,onDismissError: ()->Unit,content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().background(Background).verticalScroll(scroll).padding(horizontal=16.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(error) Panel { Text(stringResource(R.string.error)); TextButton(onClick=onDismissError) { Text(stringResource(R.string.dismiss)) } }
        content()
        Spacer(Modifier.height(4.dp))
    }
}
@Composable private fun BottomTabs(pagerState: PagerState,onSelect: (Int)->Unit) {
    Column {
        HorizontalDivider(color=Line.copy(alpha=.7f))
        NavigationBar(containerColor=Surface,tonalElevation=0.dp) {
            listOf(
                R.string.commute to Glyph.NAV_RECORD,
                R.string.summary to Glyph.NAV_TRIPS,
                R.string.nav_statistics to Glyph.CHART,
                R.string.routes to Glyph.ROUTE
            ).forEachIndexed { i,(res,glyph) ->
                val activeTab=pagerState.currentPage==i
                NavigationBarItem(selected=activeTab,onClick={ onSelect(i) },
                    icon={ AppGlyph(glyph,Modifier.size(22.dp),if(activeTab) Ink else Muted) },
                    label={ Text(stringResource(res),maxLines=1,fontSize=11.sp,fontWeight=if(activeTab) FontWeight.Medium else FontWeight.Normal) },
                    colors=NavigationBarItemDefaults.colors(indicatorColor=Soft,selectedIconColor=Ink,selectedTextColor=Ink,
                        unselectedIconColor=Muted,unselectedTextColor=Muted))
            }
        }
    }
}
@Composable private fun DetailBackHandler(pagerState: PagerState,selectedTrip: String?,selectedRoute: String?,selectedRouteRide: String?,
    onClearTrip: ()->Unit,onClearRouteRide: ()->Unit,onClearRoute: ()->Unit) {
    BackHandler(enabled=(pagerState.currentPage==1 && selectedTrip!=null) || (pagerState.currentPage==3 && (selectedRouteRide!=null || selectedRoute!=null))) {
        when {
            pagerState.currentPage==1 -> onClearTrip()
            selectedRouteRide!=null -> onClearRouteRide()
            else -> onClearRoute()
        }
    }
}
@Composable private fun DetailOverlay(visible: Boolean,content: @Composable ()->Unit) {
    val progress=remember { Animatable(if(visible) 1f else 0f) }
    LaunchedEffect(visible) { progress.animateTo(if(visible) 1f else 0f,tween(300)) }
    val mounted by remember { derivedStateOf { visible || progress.value>0.001f } }
    if(mounted) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val width=constraints.maxWidth.toFloat()
            Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { } })
            Surface(
                modifier=Modifier.fillMaxSize().graphicsLayer { translationX=width*(1f-progress.value) },
                color=Background
            ) { content() }
        }
    }
}
@Composable fun RideDiaryScreen(onStart: ()->Unit,onStop: ()->Unit,onLanguage: ()->Unit,onDiscard: ()->Unit={},stopReviewToken: Int=0,vm: RideViewModel=viewModel(),onStartRoute: (String)->Unit={ onStart() },weather: WeatherViewModel=viewModel()) {
    val context=LocalContext.current
    val rides by vm.rides.collectAsStateWithLifecycle()
    val routes by vm.routes.collectAsStateWithLifecycle()
    val matching by vm.matching.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val quality by RecordingService.quality.collectAsStateWithLifecycle()
    val saving by RecordingService.saving.collectAsStateWithLifecycle()
    val stopPending by RecordingService.stopPending.collectAsStateWithLifecycle()
    var selectedTrip by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRouteRide by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTripSample by remember { mutableStateOf<Sample?>(null) }
    var selectedRouteRideSample by remember { mutableStateOf<Sample?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var unlinkingRide by remember { mutableStateOf<Pair<RideData,String>?>(null) }
    var editingRoute by remember { mutableStateOf<RouteRecord?>(null) }
    var editingPlanId by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshToken by remember { mutableLongStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    fun refreshRecord() { if(!refreshing) { refreshing=true; refreshToken=System.currentTimeMillis() } }
    var deletingRoute by remember { mutableStateOf<RouteRecord?>(null) }
    var deletingRide by remember { mutableStateOf<RideData?>(null) }
    val closeTrip=remember { { selectedTrip=null } }
    val closeRouteRide=remember { { selectedRouteRide=null } }
    val closeRoute=remember { { selectedRoute=null } }
    val dismissError=remember { { vm.error.value=false } }
    val scope=rememberCoroutineScope()
    val pagerState=rememberPagerState(initialPage=0,pageCount={4})
    fun openTab(page: Int) {
        scope.launch { pagerState.animateScrollToPage(page) }
    }
    DetailBackHandler(pagerState,selectedTrip,selectedRoute,selectedRouteRide,
        onClearTrip=closeTrip,onClearRouteRide=closeRouteRide,onClearRoute=closeRoute)
    var explain by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var notificationsDenied by remember { mutableStateOf(false) }
    var finishDialog by rememberSaveable { mutableStateOf(false) }
    var handledStop by rememberSaveable { mutableIntStateOf(0) }
    var handledArrival by rememberSaveable { mutableStateOf<String?>(null) }
    var classifyAfterSave by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingRouteId by rememberSaveable { mutableStateOf<String?>(null) }
    fun startPending() { val route=pendingRouteId; pendingRouteId=null; if(route!=null) onStartRoute(route) else onStart() }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val precise=results[Manifest.permission.ACCESS_FINE_LOCATION] == true || ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        denied=!precise
        notificationsDenied=Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED
        if(precise) startPending()
    }
    val active=rides.firstOrNull { it.ride.state=="RECORDING" }
    LaunchedEffect(stopReviewToken,active?.ride?.id,active?.ride?.arrivalSuggested) {
        if(active!=null && ((stopReviewToken>handledStop) || (active.ride.arrivalSuggested && handledArrival!=active.ride.id))) {
            handledStop=stopReviewToken; handledArrival=active.ride.id.takeIf { active.ride.arrivalSuggested }
            finishDialog=true
        }
    }
    var previous by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(active?.ride?.id) {
        if(previous!=null && active==null) {
            val finished=rides.firstOrNull { it.ride.id==previous }
            if(finished!=null) { selectedTrip=finished.ride.id; selectedTripSample=null; openTab(1); classifyAfterSave=finished.ride.id }
        }
        previous=active?.ride?.id
    }
    fun begin(routeId: String?=null) {
        pendingRouteId=routeId
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) {
            if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) explain=true else startPending()
        } else explain=true
    }
    val recordScroll=rememberScrollState()
    val tripsScroll=rememberScrollState()
    val statsScroll=rememberScrollState()
    val routesScroll=rememberScrollState()
    val tripDetailList=rememberLazyListState()
    val routeDetailScroll=rememberScrollState()
    val routeRideDetailList=rememberLazyListState()
    val currentTrip=selectedTrip?.let { id -> rides.firstOrNull { it.ride.id==id } }
    var lastTrip by remember { mutableStateOf<RideData?>(null) }
    LaunchedEffect(currentTrip) { if(currentTrip!=null) lastTrip=currentTrip }
    val shownTrip=if(selectedTrip==null) lastTrip else currentTrip
    LaunchedEffect(selectedTrip) { if(selectedTrip!=null) tripDetailList.scrollToItem(0) }
    val currentGroup=selectedRoute?.let { id -> routeGroups(routes).firstOrNull { it.id==id } }
    var lastGroup by remember { mutableStateOf<RouteGroup?>(null) }
    LaunchedEffect(currentGroup) { if(currentGroup!=null) lastGroup=currentGroup }
    val shownGroup=if(selectedRoute==null) lastGroup else currentGroup
    LaunchedEffect(selectedRoute) { if(selectedRoute!=null) routeDetailScroll.scrollTo(0) }
    val currentRouteRide=selectedRouteRide?.let { id -> rides.firstOrNull { it.ride.id==id } }
    var lastRouteRide by remember { mutableStateOf<RideData?>(null) }
    LaunchedEffect(currentRouteRide) { if(currentRouteRide!=null) lastRouteRide=currentRouteRide }
    val shownRouteRide=if(selectedRouteRide==null) lastRouteRide else currentRouteRide
    LaunchedEffect(selectedRouteRide) { if(selectedRouteRide!=null) routeRideDetailList.scrollToItem(0) }
    val overlayOpen=selectedTrip!=null || selectedRoute!=null || selectedRouteRide!=null
    Scaffold(containerColor=Background,bottomBar={
        BottomTabs(pagerState){ openTab(it) }
    }) { padding ->
        HorizontalPager(
            state=pagerState,
            modifier=Modifier.fillMaxSize().padding(padding),
            beyondViewportPageCount=3,
            userScrollEnabled=!overlayOpen,
            pageSpacing=0.dp
        ) { page ->
            when(page) {
                0 -> RecordRefreshContainer(enabled=true,refreshing=refreshing,onRefresh={ refreshRecord() },modifier=Modifier.fillMaxSize()) {
                    PageColumn(recordScroll,error,{ vm.error.value=false }) {
                        val dashboard=rememberRecordDashboard(weather,rides,routes,active,refreshToken)
                        LaunchedEffect(dashboard.data.token,dashboard.data.loading,refreshToken) {
                            refreshing=refreshToken>0 && (dashboard.data.token!=refreshToken || dashboard.data.loading)
                        }
                        if(active!=null) {
                            LiveRide(active,routes,rides,quality,matching)
                            if(stopPending) Note(stringResource(if(saving) R.string.saving_ride else R.string.save_ride_failed))
                            OutlinedButton(enabled=!saving,onClick={ if(stopPending) onStop() else finishDialog=true },modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(14.dp)) {
                                Text(stringResource(if(saving) R.string.saving_ride else if(stopPending) R.string.retry_save_ride else R.string.finish))
                            }
                            RecordWeather(weather,dashboard,onRefresh={ refreshRecord() }) { settingsOpen=true }
                            if(quality=="LOCATION_OFF") TextButton(onClick={ context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text(stringResource(R.string.open_settings)) }
                            RecordWeatherSources(weather,dashboard)
                        } else {
                            PageHeader(overline=friendlyDate(dashboard.now),title=stringResource(R.string.record_ready),
                                actions={ HeaderIconButton(Glyph.SETTINGS,stringResource(R.string.settings),tint=Muted) { settingsOpen=true } })
                            if(denied) Panel {
                                Text(stringResource(R.string.permission_denied))
                                TextButton(onClick={ context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,"package:${context.packageName}".toUri())) }) { Text(stringResource(R.string.open_settings)) }
                            }
                            Button(onClick={ begin() },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(14.dp)) {
                                AppGlyph(Glyph.PLAY,Modifier.size(18.dp),Surface)
                                Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.start))
                            }
                            if(notificationsDenied) Note(stringResource(R.string.notification_denied))
                            if(quality in listOf("STORAGE_ERROR","START_FAILED","PERMISSION_LOST")) Note(stringResource(if(quality=="STORAGE_ERROR") R.string.error else if(quality=="START_FAILED") R.string.quality_start else R.string.quality_permission))
                            if(quality=="ROUTE_UNAVAILABLE") Note(stringResource(R.string.route_unavailable))
                            RecordWeather(weather,dashboard,onRefresh={ refreshRecord() }) { settingsOpen=true }
                            RouteRecommendations(dashboard,weather) { begin(it) }
                            val latest=rides.firstOrNull { it.ride.state=="COMPLETED" }
                            if(dashboard.plans.today.isEmpty() && dashboard.plans.tomorrow.isEmpty() && latest!=null) {
                                SectionHeading(stringResource(R.string.recent_ride))
                                RecentRideRow(latest,routes) { sample -> selectedTrip=latest.ride.id; selectedTripSample=sample; openTab(1) }
                            }
                            RecordWeatherSources(weather,dashboard)
                        }
                    }
                }
                1 -> Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().then(if(selectedTrip!=null) Modifier.clearAndSetSemantics {} else Modifier)) {
                    PageColumn(tripsScroll,error,{ vm.error.value=false }) {
                        val completed=rides.filter { it.ride.state=="COMPLETED" }
                        val samples=remember(completed) { completed.map { it.sample() } }
                        val month=YearMonth.now()
                        val monthSamples=remember(samples,month) { samples.filter { YearMonth.from(Statistics.date(it))==month } }
                        val allKm=remember(samples) { samples.sumOf { it.metrics.distance }/1000 }
                        val monthKm=remember(monthSamples) { monthSamples.sumOf { it.metrics.distance }/1000 }
                        PageHeader(modifier=Modifier.testTag("trip-journal-header"),overline=stringResource(R.string.month_label,month.year,month.monthValue),
                            title=if(completed.isEmpty()) stringResource(R.string.empty_rides) else stringResource(R.string.monthly_summary,monthSamples.size,decimal(monthKm)),
                            subtitle=if(completed.isEmpty()) null else stringResource(R.string.all_time_summary,completed.size,decimal(allKm)),
                            actions={ HeaderIconButton(Glyph.SETTINGS,stringResource(R.string.settings),tint=Muted) { settingsOpen=true } })
                        History(rides,routes,onDelete={ deletingRide=it }) { data,sample -> selectedTrip=data.ride.id; selectedTripSample=sample }
                    }
                    }
                    val deleteDetail: ()->Unit=remember(shownTrip) { { shownTrip?.let { deletingRide=it }; Unit } }
                    DetailOverlay(visible=selectedTrip!=null && currentTrip!=null) {
                        shownTrip?.let { detail ->
                            RideDetail(detail,routes,rides,vm,listState=tripDetailList,initialSample=selectedTripSample,error=error,onDismissError=dismissError,
                                onBack=closeTrip,onDelete=deleteDetail)
                        }
                    }
                }
                2 -> PageColumn(statsScroll,error,{ vm.error.value=false }) { StatsScreen(rides,routes) }
                else -> Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().then(if(selectedRoute!=null) Modifier.clearAndSetSemantics {} else Modifier)) {
                    PageColumn(routesScroll,error,{ vm.error.value=false }) {
                        val groups=routeGroups(routes)
                        val linked=rides.count { it.ride.routeId!=null && it.ride.state=="COMPLETED" }
                        PageHeader(modifier=Modifier.testTag("routes-header"),title=if(groups.isEmpty()) stringResource(R.string.empty_routes_title) else stringResource(R.string.routes_summary,groups.size,linked),
                            subtitle=if(groups.isEmpty()) stringResource(R.string.empty_routes) else stringResource(R.string.route_manage_hint),
                            actions={ HeaderIconButton(Glyph.SETTINGS,stringResource(R.string.settings),tint=Muted) { settingsOpen=true } })
                        groups.forEach { group -> key(group.id) {
                            RouteListCard(group,rides,active?.ride?.routeId,
                                onOpen={ selectedRoute=group.id; selectedRouteRide=null; selectedRouteRideSample=null },
                                onEdit={ editingRoute=it },onDelete={ deletingRoute=it })
                        } }
                    }
                    }
                    DetailOverlay(visible=selectedRoute!=null && currentGroup!=null) {
                        shownGroup?.let { group ->
                            Box(Modifier.fillMaxSize().then(if(selectedRouteRide!=null) Modifier.clearAndSetSemantics {} else Modifier)) {
                            PageColumn(routeDetailScroll,error,{ vm.error.value=false }) {
                                RouteOverview(group,rides,active?.ride?.routeId,onBack={ selectedRoute=null },onEdit={ editingRoute=it },onDelete={ deletingRoute=it },onPlan={ editingPlanId=it.id })
                                SectionHeading(stringResource(R.string.route_trips))
                                History(rides.filter { it.ride.routeId in group.routeIds },routes,
                                    onUnlink={ unlinkingRide=it to group.id },emptyMessage=R.string.empty_route_trips) { data,sample ->
                                    selectedRouteRide=data.ride.id; selectedRouteRideSample=sample
                                }
                            }
                            }
                        }
                    }
                    val deleteDetail: ()->Unit=remember(shownRouteRide) { { shownRouteRide?.let { deletingRide=it }; Unit } }
                    DetailOverlay(visible=selectedRouteRide!=null && currentRouteRide!=null) {
                        shownRouteRide?.let { detail ->
                            RideDetail(detail,routes,rides,vm,listState=routeRideDetailList,initialSample=selectedRouteRideSample,error=error,onDismissError=dismissError,
                                onBack=closeRouteRide,onDelete=deleteDetail)
                        }
                    }
                }
            }
        }
    }
    routes.firstOrNull { it.id==editingPlanId }?.let { route ->
        TravelPlanDialog(route,rides,routes,onDismiss={ editingPlanId=null },onSave={ mode,days,minute ->
            vm.updatePlan(route.id,days,minute,mode) { editingPlanId=null }
        })
    }
    editingRoute?.let { route ->
        RouteDialog(points=route.route().points,route=route,groups=routeGroups(routes),onDismiss={ editingRoute=null },onSave={ name,direction,start,end ->
            vm.editRoute(route.id,name,direction,start,end); editingRoute=null
        })
    }
    deletingRoute?.let { route ->
        DeleteDialog(title=stringResource(R.string.delete_route),message=stringResource(R.string.delete_route_message,routeName(route.name)),onDismiss={ deletingRoute=null }) {
            vm.deleteRouteGroup(route.groupKey) {
                if(selectedRoute==route.groupKey) { selectedRoute=null; selectedRouteRide=null; selectedRouteRideSample=null }
                deletingRoute=null
            }
        }
    }
    deletingRide?.let { data ->
        val sample=data.sample()
        DeleteDialog(title=stringResource(R.string.delete_ride),message=stringResource(R.string.delete_ride_message,date(sample.startUtc,sample.zone)+" "+clock(sample.startUtc,sample.zone)),onDismiss={ deletingRide=null }) {
            vm.deleteRide(data.ride.id) { if(selectedTrip==data.ride.id) selectedTrip=null; if(selectedRouteRide==data.ride.id) selectedRouteRide=null }
            deletingRide=null
        }
    }
    if(settingsOpen) SettingsDialog(onDismiss={ settingsOpen=false },onLanguage=onLanguage,weather=weather)
    unlinkingRide?.let { (data,groupId) ->
        val name=routeGroups(routes).firstOrNull { it.id==groupId }?.name ?: ""
        AlertDialog(onDismissRequest={ unlinkingRide=null },containerColor=Surface,tonalElevation=0.dp,
            title={ Text(stringResource(R.string.unlink_ride)) },
            text={ Text(stringResource(R.string.unlink_ride_message,name)) },
            confirmButton={ TextButton(onClick={ vm.unlinkRide(data.ride.id,groupId) { unlinkingRide=null } }) { Text(stringResource(R.string.unlink_ride)) } },
            dismissButton={ TextButton(onClick={ unlinkingRide=null }) { Text(stringResource(R.string.cancel)) } })
    }
    if(explain) AlertDialog(onDismissRequest={ explain=false },title={ Text(stringResource(R.string.permission_title)) },text={ Text(stringResource(R.string.permission_body)) },confirmButton={
        TextButton(onClick={
            explain=false
            val permissions=mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)
            if(Build.VERSION.SDK_INT>=33) permissions+=Manifest.permission.POST_NOTIFICATIONS
            permission.launch(permissions.toTypedArray())
        }) { Text(stringResource(R.string.permission_allow)) }
    },dismissButton={ TextButton(onClick={ explain=false }) { Text(stringResource(R.string.cancel)) } })
    if(finishDialog && active!=null && !stopPending) AlertDialog(modifier=Modifier.testTag("finish-confirmation"),onDismissRequest={ finishDialog=false },containerColor=Surface,tonalElevation=0.dp,
        title={ Text(stringResource(if(active.ride.arrivalSuggested) R.string.arrival_review_title else R.string.finish_confirm)) },
        text={ Text(stringResource(R.string.finish_options_body)) },
        confirmButton={ TextButton(onClick={ finishDialog=false; onStop() }) { Text(stringResource(R.string.save)) } },
        dismissButton={ Row {
            TextButton(onClick={ finishDialog=false }) { Text(stringResource(R.string.cancel)) }
            TextButton(onClick={ finishDialog=false; onDiscard() }) { Text(stringResource(R.string.delete),color=Danger) }
        } })
    rides.firstOrNull { it.ride.id==classifyAfterSave }?.let { data ->
        ClassificationDialog(data,routes,onDismiss={ classifyAfterSave=null },onSave={ route,direction,excluded,reason ->
            vm.classify(data.ride.id,route,direction,excluded,reason); classifyAfterSave=null
        },onCreate={ name,direction,start,end,group,excluded,reason ->
            vm.createRoute(data.ride.id,name,direction,start,end,group,excluded,reason); classifyAfterSave=null
        })
    }
}

private data class LiveAnalysis(val sample: Sample)

@Composable private fun LiveRide(data: RideData,routes: List<RouteRecord>,all: List<RideData>,quality: String,matching: LiveRouteMatch?) {
    val analysis by produceState<LiveAnalysis?>(null,data) {
        value=withContext(Dispatchers.Default) { LiveAnalysis(data.sample()) }
    }
    val view=analysis
    if(view==null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    val sample=view.sample; val metrics=sample.metrics
    val snapshot=matching?.takeIf { it.rideId==data.ride.id }
    val report=snapshot?.report
    val fixedCandidate=report?.candidates?.firstOrNull { it.route.id==snapshot.boundRouteId }
    val match=if(snapshot?.boundRouteId!=null) fixedCandidate?.takeIf { it.eligible }?.match else report?.match
    val route=match?.route ?: routes.firstOrNull { it.id==sample.routeId }?.route()
    val history=remember(all,data.ride.id) { all.filter { it.ride.id!=data.ride.id } }
    val estimate by produceState<Statistics.Estimate?>(null,snapshot,history) {
        value=if(match!=null && !match.deviated) withContext(Dispatchers.Default) {
            val current=data.sample().copy(routeId=match.route.id,direction=match.route.direction)
            Statistics.estimate(current,history.map { it.sample() },match)
        } else null
    }
    var clocks by remember { mutableStateOf(System.currentTimeMillis() to SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while(true) { clocks=System.currentTimeMillis() to SystemClock.elapsedRealtime(); delay(1_000) } }
    val now=clocks.first
    val receivedRealtime by RecordingService.latestFixRealtime.collectAsStateWithLifecycle()
    val serviceActive by RecordingService.active.collectAsStateWithLifecycle()
    // Read acquisition time, independently of database/rendering lag.
    val latest=sample.points.lastOrNull()
    val fixAge=if(serviceActive) receivedRealtime?.let { (clocks.second-it).coerceAtLeast(0) }
        else latest?.let { data.ride.elapsed-it.t }
    val fresh=fixAge!=null && fixAge in 0..15_000 && if(serviceActive) quality=="GOOD" else latest?.let(::good)==true
    val uncertain=!fresh || match==null || match.deviated
    val eta=if(uncertain) null else estimate
    val status=when { quality=="STORAGE_ERROR"->R.string.error; quality=="LOCATION_OFF"->R.string.quality_off; quality=="PERMISSION_LOST" || quality=="PRECISE_REQUIRED"->R.string.quality_permission; quality=="START_FAILED"->R.string.quality_start; quality=="POOR" || quality=="STALE"->R.string.quality_poor; !fresh->R.string.quality_searching; else->R.string.quality_good }
    val locationNormal=status==R.string.quality_good
    Column(Modifier.fillMaxWidth().testTag("live-recording-header"),verticalArrangement=Arrangement.spacedBy(1.dp)) {
        Text(friendlyDate(now,sample.zone),fontSize=11.sp,letterSpacing=.4.sp,color=Muted,maxLines=1)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ride_title),fontSize=21.sp,fontWeight=FontWeight.SemiBold,lineHeight=27.sp,maxLines=1)
            Spacer(Modifier.weight(1f))
            StatusPill(stringResource(status),good=locationNormal,dotTag=if(locationNormal) "location-status-good" else "location-status-problem")
        }
    }
    Surface(modifier=Modifier.testTag("live-ride-overview"),color=RideCard,contentColor=Surface,shape=RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            if(route!=null) Text(routeName(route.name)+" · "+directionName(route.direction),fontSize=12.sp,color=Accent,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(stringResource(R.string.elapsed),color=Surface.copy(alpha=.62f),fontSize=11.sp)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { LargeTime(metrics.elapsed,44f,Surface) }
                if(match!=null && !uncertain) Text("${(match.progress*100).toInt()}%",fontSize=24.sp,fontFamily=FontFamily.Monospace,color=Accent)
            }
            if(match!=null && !uncertain) {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.progress),fontSize=11.sp,color=Surface.copy(alpha=.68f))
                    Text(stringResource(R.string.remaining,(route!!.length-match.along).coerceAtLeast(0.0)/1000),fontSize=11.sp,color=Surface.copy(alpha=.68f))
                }
                MotorcycleProgress(match.progress)
            }
            HorizontalDivider(color=Color(0xff50654d))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.eta),color=Accent,fontSize=11.sp)
                    Text(if(eta==null) stringResource(R.string.learning) else {
                        "${clock(now+eta.low,sample.zone)}–${clock(now+eta.high,sample.zone)}"
                    },fontSize=if(eta==null) 13.sp else 18.sp,color=Surface,lineHeight=22.sp)
                }
                if(eta!=null) Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.history_comparison),color=Surface.copy(alpha=.62f),fontSize=11.sp)
                    Text(delta(eta.elapsedDifference),fontSize=18.sp,fontFamily=FontFamily.Monospace,color=if(eta.elapsedDifference>0) Wait else Accent)
                }
            }
            if(eta!=null) Text(stringResource(R.string.comparison_position,delta(eta.elapsedDifference),eta.n),fontSize=11.sp,color=Surface.copy(alpha=.62f))
        }
    }
    RouteCandidatePanel(snapshot,fresh)
    Surface(shape=RoundedCornerShape(14.dp),color=Surface,border=BorderStroke(1.dp,Line.copy(alpha=.65f))) {
        val wideFont=LocalDensity.current.fontScale>1.3f
        if(wideFont) Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            CompactMetric(stringResource(R.string.average_speed),metrics.averageKmh?.let { decimal(it)+" km/h" } ?: "—")
            HorizontalDivider(color=Line.copy(alpha=.65f))
            CompactMetric(stringResource(R.string.waiting),duration(metrics.waiting))
            HorizontalDivider(color=Line.copy(alpha=.65f))
            CompactMetric(stringResource(R.string.stops),metrics.stops.toString())
        } else Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            CompactMetric(stringResource(R.string.average_speed),metrics.averageKmh?.let { decimal(it)+" km/h" } ?: "—",Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(30.dp).background(Line.copy(alpha=.7f)))
            CompactMetric(stringResource(R.string.waiting),duration(metrics.waiting),Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(30.dp).background(Line.copy(alpha=.7f)))
            CompactMetric(stringResource(R.string.stops),metrics.stops.toString(),Modifier.weight(1f))
        }
    }
    val last=metrics.segments.lastOrNull { it.motion != Motion.UNKNOWN }
    if(fresh && latest?.speed != null && latest.speed < Policy.STOP_EXIT && last?.motion==Motion.WAITING) Note(stringResource(R.string.current_wait,duration(last.duration)))
    SectionTitle(stringResource(R.string.time_split)); DataCard(padding=14) { TimeBar(metrics) }
}

@Composable private fun RouteCandidatePanel(snapshot: LiveRouteMatch?,fresh: Boolean) {
    val candidates=snapshot?.report?.candidates.orEmpty().take(2)
    if(!fresh || candidates.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(horizontal=4.dp).testTag("live-route-candidates"),verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(stringResource(R.string.candidate_title),fontSize=10.sp,color=Muted)
        candidates.forEach { candidate ->
            Row(Modifier.fillMaxWidth().testTag("live-candidate-${candidate.route.id}"),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(routeName(candidate.route.name)+" · "+directionName(candidate.route.direction),modifier=Modifier.weight(1f),fontSize=12.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(decimal(candidate.overlap*100)+"%",fontSize=12.sp,fontFamily=FontFamily.Monospace,color=Muted,maxLines=1)
            }
        }
    }
}

@Composable private fun History(rides: List<RideData>,routes: List<RouteRecord>,onDelete: ((RideData)->Unit)?=null,
    onUnlink: ((RideData)->Unit)?=null,emptyMessage: Int=R.string.empty_rides,onSelect: (RideData,Sample)->Unit) {
    if(rides.isEmpty()) Panel { Text(stringResource(emptyMessage)) }
    val samples=remember(rides) { rides.associate { it.ride.id to it.sample() } }
    rides.forEach { data -> key(data.ride.id) {
        val s=samples.getValue(data.ride.id); val route=routes.firstOrNull { it.id==s.routeId }
        ContextMenuPanel(
            modifier=Modifier.testTag("ride-${s.id}"),onClick={ onSelect(data,s) },
            onLongClickLabel=stringResource(R.string.ride_actions),canManage=s.state!="RECORDING",
            title=date(s.startUtc,s.zone)+" · "+clock(s.startUtc,s.zone),subtitle=duration(s.metrics.elapsed)+" · "+decimal(s.metrics.distance/1000)+" km",
            contentPadding=12.dp,cornerRadius=14.dp,
            menu={ dismiss ->
                if(onUnlink!=null) SheetAction(title=stringResource(R.string.unlink_ride),glyph=Glyph.UNLINK,onClick={ dismiss(); onUnlink(data) })
                if(onDelete!=null) SheetAction(title=stringResource(R.string.delete_ride),glyph=Glyph.DELETE,destructive=true,onClick={ dismiss(); onDelete(data) })
            }
        ) {
            RideRowContent(s,route)
        }
    } }
}

/** Shared dense trip row used by the journal and the Record page's last-trip card. */
@Composable private fun RideRowContent(s: Sample,route: RouteRecord?) {
    val locale=LocalConfiguration.current.locales[0]
    val wideFont=LocalDensity.current.fontScale>1.3f
    val flags=buildList {
        if(s.state=="INTERRUPTED") add(stringResource(R.string.status_interrupted_short))
        if(s.state=="RECORDING") add(stringResource(R.string.status_recording_short))
        if(s.excluded) add(stringResource(R.string.status_excluded_short))
    }
    if(wideFont) {
        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(shortDate(s.startUtc,s.zone),fontSize=14.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium,color=Ink,maxLines=1)
                Text(weekdayText(s.startUtc,s.zone,locale),fontSize=11.sp,color=Muted,maxLines=1)
                Spacer(Modifier.weight(1f))
                Text(duration(s.metrics.elapsed),fontSize=17.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium,color=Ink,maxLines=1)
            }
            Text(if(route==null) stringResource(R.string.ordinary) else routeName(route.name),fontSize=15.sp,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(clock(s.startUtc,s.zone)+" · "+directionName(s.direction)+(if(flags.isEmpty()) "" else " · "+flags.joinToString(" · ")),fontSize=11.sp,color=if(s.state=="INTERRUPTED") Danger else Muted,maxLines=2,overflow=TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                Text(decimal(s.metrics.distance/1000)+" km",fontSize=11.sp,fontFamily=FontFamily.Monospace,color=Muted,maxLines=1)
            }
        }
    } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(54.dp),verticalArrangement=Arrangement.spacedBy(1.dp)) {
            Text(shortDate(s.startUtc,s.zone),fontSize=14.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium,color=Ink,maxLines=1)
            Text(weekdayText(s.startUtc,s.zone,locale),fontSize=10.sp,color=Muted,maxLines=1)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(if(route==null) stringResource(R.string.ordinary) else routeName(route.name),fontSize=15.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(clock(s.startUtc,s.zone)+" · "+directionName(s.direction),fontSize=11.sp,color=Muted,maxLines=1)
                if(flags.isNotEmpty()) Text("· "+flags.joinToString(" · "),fontSize=10.sp,color=if(s.state=="INTERRUPTED") Danger else Wait,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
        Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(1.dp)) {
            Text(duration(s.metrics.elapsed),fontSize=17.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium,color=Ink,maxLines=1)
            Text(decimal(s.metrics.distance/1000)+" km",fontSize=11.sp,fontFamily=FontFamily.Monospace,color=Muted,maxLines=1)
        }
        AppGlyph(Glyph.RIGHT,Modifier.size(15.dp),Muted.copy(alpha=.55f))
    }
}

@Composable private fun RecentRideRow(data: RideData,routes: List<RouteRecord>,onClick: (Sample)->Unit) {
    val s=remember(data) { data.sample() }; val route=routes.firstOrNull { it.id==s.routeId }
    Surface(onClick={ onClick(s) },shape=RoundedCornerShape(14.dp),color=Surface,border=BorderStroke(1.dp,Line.copy(alpha=.65f))) {
        Box(Modifier.fillMaxWidth().padding(12.dp)) { RideRowContent(s,route) }
    }
}

@Composable private fun RideDetail(data: RideData,routes: List<RouteRecord>,all: List<RideData>,vm: RideViewModel,
    listState: LazyListState,initialSample: Sample?,error: Boolean,onDismissError: ()->Unit,onBack: ()->Unit,onDelete: ()->Unit) {
    val s=remember(data,initialSample) {
        initialSample?.takeIf { sample ->
            sample.id==data.ride.id && sample.routeId==data.ride.routeId && sample.direction==data.ride.direction &&
                sample.startUtc==data.ride.startUtc+data.ride.boundaryStart &&
                sample.endUtc==data.ride.startUtc+(data.ride.boundaryEnd ?: data.ride.elapsed)
        } ?: data.sample()
    }; val m=s.metrics
    val route=routes.firstOrNull { it.id==s.routeId }
    var edit by remember(s.id) { mutableStateOf(false) }
    var trim by remember(s.id) { mutableStateOf(false) }
    var sheet by remember(s.id) { mutableStateOf(false) }
    val canManage=s.state!="RECORDING"
    LazyColumn(
        state=listState,
        modifier=Modifier.fillMaxSize().background(Background).testTag("ride-detail-list"),
        contentPadding=PaddingValues(horizontal=16.dp,vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)
    ) {
        if(error) item {
            Panel { Text(stringResource(R.string.error)); TextButton(onClick=onDismissError) { Text(stringResource(R.string.dismiss)) } }
        }
        item {
            Row(Modifier.fillMaxWidth().testTag("ride-detail-header"),verticalAlignment=Alignment.CenterVertically) {
                HeaderIconButton(Glyph.LEFT,stringResource(R.string.back),onClick=onBack)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
                    Text(friendlyDateTime(s.startUtc,s.endUtc,s.zone),fontSize=11.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(route?.let { routeName(it.name) } ?: stringResource(R.string.ordinary),fontSize=20.sp,fontWeight=FontWeight.SemiBold,lineHeight=26.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(directionName(s.direction)+" · "+duration(m.elapsed),fontSize=12.sp,color=Muted,maxLines=1)
                }
                if(canManage) HeaderIconButton(Glyph.MORE,stringResource(R.string.more_actions)) { sheet=true }
            }
        }
        item {
            Surface(
                modifier=Modifier.testTag("ride-detail-${s.id}").combinedClickable(onClick={},onLongClickLabel=stringResource(R.string.ride_actions),
                    onLongClick=if(canManage) ({ sheet=true }) else null),
                color=RideCard,contentColor=Surface,shape=RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(stringResource(R.string.total_time),Modifier.weight(1f),fontSize=11.sp,color=Surface.copy(alpha=.66f))
                        if(data.ride.arrivalConfirmed) Text("100%",fontSize=11.sp,color=Accent)
                    }
                    LargeTime(m.elapsed,42f,Surface)
                    if(data.ride.arrivalConfirmed) MotorcycleProgress(1.0)
                    HorizontalDivider(Modifier.padding(vertical=4.dp),color=Surface.copy(alpha=.16f))
                    if(LocalDensity.current.fontScale>1.3f) {
                        RideHeroMetric(stringResource(R.string.distance),decimal(m.distance/1000)+" km")
                        RideHeroMetric(stringResource(R.string.average_speed),m.averageKmh?.let { decimal(it)+" km/h" } ?: "—")
                    } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(18.dp)) {
                        RideHeroMetric(stringResource(R.string.distance),decimal(m.distance/1000)+" km",Modifier.weight(1f))
                        RideHeroMetric(stringResource(R.string.average_speed),m.averageKmh?.let { decimal(it)+" km/h" } ?: "—",Modifier.weight(1f))
                    }
                }
            }
        }
        if(s.state=="INTERRUPTED" || s.state=="RECORDING" || s.excluded || !m.complete || data.ride.boundaryStart>0 || data.ride.boundaryEnd!=null) {
            item {
                Surface(shape=RoundedCornerShape(14.dp),color=Soft.copy(alpha=.65f)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
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
        }
        item { SectionHeading(stringResource(R.string.stats_time_composition)) }
        item {
            DataCard(Modifier.testTag("ride-stops"),padding=14) {
                TimeComposition(m.elapsed,m.moving,m.waiting,m.unknown)
                HorizontalDivider(Modifier.padding(vertical=4.dp),color=Line.copy(alpha=.6f))
                Text(stringResource(R.string.ride_stops_title),fontSize=14.sp,fontWeight=FontWeight.Medium)
                val entries=listOf(
                    stringResource(R.string.stops) to m.stops.toString(),
                    stringResource(R.string.longest_wait) to duration(m.longestWait),
                    stringResource(R.string.average_wait) to duration(m.averageWait),
                    stringResource(R.string.moving_speed) to if(m.complete && m.moving>0) decimal(m.distance*3600/m.moving)+" km/h" else "—"
                )
                entries.chunked(2).forEachIndexed { index,pair ->
                    if(index>0) HorizontalDivider(Modifier.padding(vertical=4.dp),color=Line.copy(alpha=.6f))
                    if(LocalDensity.current.fontScale>1.3f) pair.forEach { (label,value) -> RideMetric(label,value) }
                    else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        pair.forEach { (label,value) -> RideMetric(label,value,Modifier.weight(1f)) }
                    }
                }
            }
        }
        item { SectionHeading(stringResource(R.string.ride_trace_title)) }
        item { DataCard(Modifier.testTag("ride-trace"),padding=14) { Trace(s.points,height=140.dp) } }
        item { RideComparisonSection(s,all) }
        if(s.state!="RECORDING") {
            item {
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    val canCreate=s.points.count(::good)>=4 && m.distance>=Policy.MIN_ROUTE_DISTANCE_M
                    Button(onClick={ edit=true },enabled=canCreate,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp),shape=RoundedCornerShape(14.dp)) {
                        AppGlyph(Glyph.ROUTE,Modifier.size(18.dp),if(canCreate) Surface else Muted)
                        Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.route_create))
                    }
                    if(!canCreate) Note(stringResource(R.string.route_too_short))
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
    if(sheet) ActionSheet(title=date(s.startUtc,s.zone)+" · "+clock(s.startUtc,s.zone),subtitle=duration(m.elapsed)+" · "+decimal(m.distance/1000)+" km",
        eyebrow=stringResource(R.string.ride_actions),onDismiss={ sheet=false }) {
        SheetAction(title=stringResource(R.string.route_edit),glyph=Glyph.EDIT,onClick={ sheet=false; edit=true })
        SheetAction(title=stringResource(R.string.boundaries),glyph=Glyph.ADJUST,onClick={ sheet=false; trim=true })
        SheetAction(title=stringResource(R.string.delete_ride),glyph=Glyph.DELETE,destructive=true,onClick={ sheet=false; onDelete() })
    }
    if(edit) ClassificationDialog(data,routes,onDismiss={ edit=false },onSave={ selectedRoute,direction,excluded,reason -> vm.classify(s.id,selectedRoute,direction,excluded,reason); edit=false },
        onCreate={ name,direction,start,end,group,excluded,reason -> vm.createRoute(s.id,name,direction,start,end,group,excluded,reason); edit=false })
    if(trim) BoundaryDialog(data,onDismiss={ trim=false },onSave={ start,end -> vm.boundaries(s.id,start,end); trim=false })
}
@Composable private fun RideComparisonSection(s: Sample,all: List<RideData>) {
    val m=s.metrics
    val comparison by produceState<Statistics.Comparison?>(null,s,all) {
        value=withContext(Dispatchers.Default) { Statistics.compare(s,all.map { it.sample() }) }
    }
    val compare=comparison
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        SectionHeading(stringResource(R.string.baseline,compare?.n ?: 0),stringResource(R.string.ride_comparison_caption))
        DataCard(Modifier.testTag("ride-comparison"),padding=14) {
            if(compare==null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else if(compare.n<Policy.MIN_HISTORY || !m.complete) {
                AppGlyph(Glyph.CHART,Modifier.size(22.dp),Moving)
                Text(stringResource(R.string.no_baseline),fontSize=14.sp,lineHeight=20.sp,color=Muted)
            } else {
                Text(stringResource(R.string.ride_total_comparison),fontSize=12.sp,color=Muted)
                Text(delta(m.elapsed-compare.total!!),fontSize=30.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium,color=if(m.elapsed>compare.total) Danger else Moving)
                HorizontalDivider(Modifier.padding(vertical=4.dp),color=Line.copy(alpha=.6f))
                val movingDelta=m.moving-compare.moving!!; val waitDelta=m.waiting-compare.waiting!!
                if(LocalDensity.current.fontScale>1.3f) {
                    RideMetric(stringResource(R.string.moving),delta(movingDelta),valueColor=if(movingDelta>0) Danger else Moving)
                    RideMetric(stringResource(R.string.waiting),delta(waitDelta),valueColor=if(waitDelta>0) Danger else Moving)
                } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    RideMetric(stringResource(R.string.moving),delta(movingDelta),Modifier.weight(1f),if(movingDelta>0) Danger else Moving)
                    RideMetric(stringResource(R.string.waiting),delta(waitDelta),Modifier.weight(1f),if(waitDelta>0) Danger else Moving)
                }
                Text(stringResource(R.string.component_delta,delta(movingDelta+waitDelta)),fontSize=12.sp,color=Muted)
                Text(stringResource(R.string.baseline_note),fontSize=11.sp,lineHeight=17.sp,color=Muted)
            }
        }
    }
}
@Composable private fun RideHeroMetric(label: String,value: String,modifier: Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(label,fontSize=11.sp,color=Surface.copy(alpha=.66f))
        Text(value,fontSize=20.sp,color=Surface,fontFamily=FontFamily.Monospace)
    }
}
@Composable private fun RideMetric(label: String,value: String,modifier: Modifier=Modifier,valueColor: Color=Ink) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(label,fontSize=11.sp,color=Muted,maxLines=1)
        Text(value,fontSize=18.sp,color=valueColor,fontWeight=FontWeight.Medium,fontFamily=FontFamily.Monospace,maxLines=1)
    }
}
