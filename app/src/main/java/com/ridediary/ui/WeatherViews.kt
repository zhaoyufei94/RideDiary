package com.ridediary.ui

import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridediary.R
import com.ridediary.core.good
import com.ridediary.calendar.WorkCalendarState
import com.ridediary.data.*
import com.ridediary.weather.*
import com.ridediary.traffic.*
import kotlinx.coroutines.*
import java.time.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun RecordRefreshContainer(enabled: Boolean,refreshing: Boolean,onRefresh: ()->Unit,modifier: Modifier,content: @Composable ()->Unit) {
    if(enabled) PullToRefreshBox(isRefreshing=refreshing,onRefresh=onRefresh,modifier=modifier) { content() }
    else Box(modifier) { content() }
}
@Composable private fun weatherForeground(): Boolean {
    val state by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    return state.isAtLeast(Lifecycle.State.STARTED)
}
@Composable private fun weatherClock(): Long {
    val foreground=weatherForeground()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(foreground) { if(foreground) while(true) {
        now=System.currentTimeMillis()
        delay((60_000-now%60_000).coerceAtLeast(100))
    } }
    return now
}
data class WeatherPageLoad(val token: Long=-1,val loading: Boolean=true,val config: WeatherConfig?=null,
    val position: WeatherPoint?=null,val point: WeatherPoint?=null,val results: Map<WeatherPoint,WeatherResult> = emptyMap(),
    val trafficConfig: TrafficConfig?=null,val traffic: Map<Triple<String,String,Long>,TrafficResult> = emptyMap())
internal val RouteSuggestion.trafficKey get()=Triple(route.id,route.shape,departure)
data class RecordDashboard(val plans: PlannedRoutes,val fallback: RouteRecord?,val data: WeatherPageLoad,val now: Long,val calendar: WorkCalendarState,val workdayPlans: Boolean)
private fun RouteRecord.weatherEnds()=route().points.let { points -> listOfNotNull(points.firstOrNull(),points.lastOrNull()).map { WeatherPoint.of(it.lat,it.lon) } }

/** One page request coordinates location, distinct endpoints, refresh completion and attribution. */
@Composable fun rememberRecordDashboard(vm: WeatherViewModel,rides: List<RideData>,routes: List<RouteRecord>,active: RideData?,refresh: Long,visible: Boolean=true): RecordDashboard {
    val config by vm.config.collectAsStateWithLifecycle()
    val trafficConfig by vm.trafficConfig.collectAsStateWithLifecycle()
    val context=LocalContext.current; val foreground=weatherForeground() && visible; val now=weatherClock()
    val completed=rides.filter { it.ride.state=="COMPLETED" }
    val zone=ZoneId.systemDefault()
    val date=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val years=setOf(date.year,date.plusDays(1).year,date.year+1)
    val calendar by produceState(WorkCalendarState(),years) { value=vm.workCalendar.load(years) }
    val plans=remember(completed,routes,now/60_000,zone,calendar) { RouteSuggestions.planned(completed,routes,now,zone,calendar) }
    val fallback=routes.firstOrNull { it.id==active?.ride?.routeId } ?:
        (plans.today+plans.tomorrow).firstOrNull()?.route ?: routes.firstOrNull()
    val endpoints=remember(plans,active?.ride?.id) {
        if(active!=null) emptyList() else (plans.today+plans.tomorrow).flatMap { it.route.weatherEnds() }.distinct()
    }
    val fallbackPoint=remember(fallback?.shape) { fallback?.weatherEnds()?.firstOrNull() }
    val trafficRequests=if(active==null) (plans.today+plans.tomorrow).filter { TrafficPolicy.eligible(it.departure,now) } else emptyList()
    val latest by rememberUpdatedState(active)
    val language=if(LocalConfiguration.current.locales[0].language=="zh") "zh" else "en"
    val result by produceState(WeatherPageLoad(),config,trafficConfig,foreground,now/900_000,endpoints,trafficRequests,fallbackPoint,active?.ride?.id,refresh,language) {
        val old=if(value.config==config && value.trafficConfig==trafficConfig) value else WeatherPageLoad()
        value=old.copy(token=refresh,loading=foreground,config=config,trafficConfig=trafficConfig,
            traffic=old.traffic.filterKeys { key -> trafficRequests.any { it.trafficKey==key } })
        if(!foreground) {
            value=value.copy(loading=false); return@produceState
        }
        coroutineScope {
            val trafficJob=launch {
                if(trafficConfig.enabled && trafficConfig.ready) trafficRequests.map { item -> async {
                    val response=vm.fetchTraffic(item.route,item.departure,now,freshAfter=refresh)
                    value=value.copy(traffic=value.traffic+(item.trafficKey to response))
                } }.awaitAll()
            }
            if(config.enabled && config.ready) {
                val fix=latest?.fixes()?.lastOrNull { good(it) && System.currentTimeMillis()-it.utc in 0..900_000 }
                val hasLocation=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
                val position=if(fix!=null) WeatherPoint.of(fix.lat,fix.lon) else if(hasLocation) {
                    vm.locate(); vm.location.value
                } else null
                val point=position ?: fallbackPoint
                val points=(listOfNotNull(point)+endpoints).distinct()
                value=value.copy(position=position,point=point,results=value.results.filterKeys { it in points })
                val fetched=coroutineScope { points.map { p -> async {
                    val response=vm.fetch(p,language,freshAfter=refresh)
                    // Children resume on Main: each point can render without waiting for the slowest endpoint.
                    value=value.copy(results=value.results+(p to response))
                    p to response
                } }.awaitAll().toMap() }
                value=value.copy(position=position,point=point,results=fetched)
                fetched.values.firstOrNull { it.failure!=null }?.failure?.let { failure ->
                    val message=when(failure) {
                        WeatherFailure.AUTH -> R.string.weather_auth
                        WeatherFailure.RATE_LIMIT -> R.string.weather_limit
                        WeatherFailure.CONFIGURATION -> R.string.weather_configuration
                        else -> R.string.weather_network_failed
                    }
                    Toast.makeText(context,context.getString(message),Toast.LENGTH_SHORT).show()
                }
            }
            trafficJob.join()
        }
        value=value.copy(loading=false)
    }
    return RecordDashboard(plans,fallback,result,now,calendar,routes.any { it.planMode=="WORKDAYS" })
}
private fun number(value: Double?,suffix: String=""): String = value?.let { "${it.roundToInt()}$suffix" } ?: "—"
@Composable private fun condition(hour: WeatherHour): String = hour.text ?: stringResource(when(hour.kind) {
    WeatherKind.CLEAR->R.string.weather_clear; WeatherKind.CLOUDY->R.string.weather_cloudy
    WeatherKind.RAIN->R.string.weather_rain; WeatherKind.SNOW->R.string.weather_snow
    WeatherKind.THUNDER->R.string.weather_thunder; WeatherKind.FOG->R.string.weather_fog
    WeatherKind.UNKNOWN->R.string.weather_unknown
})

@Composable fun RecordWeatherSources(vm: WeatherViewModel,dashboard: RecordDashboard) {
    val config by vm.config.collectAsStateWithLifecycle()
    if(!config.enabled) return
    val links=if(config.provider==WeatherProvider.OPEN_METEO) listOf("Open-Meteo" to "https://open-meteo.com/","CC BY 4.0" to "https://creativecommons.org/licenses/by/4.0/")
        else dashboard.data.results.values.mapNotNull { it.snapshot }.filter { it.provider==config.provider }.flatMap { it.attribution }.distinct().map {
            (runCatching { java.net.URI(it).host }.getOrNull() ?: "QWeather") to it
        }
    val text=buildAnnotatedString {
        if(config.provider==WeatherProvider.QWEATHER) append("QWeather")
        links.forEachIndexed { index,(label,url) ->
            if(index>0 || config.provider==WeatherProvider.QWEATHER) append(" · ")
            withLink(LinkAnnotation.Url(url,TextLinkStyles(style=SpanStyle(color=Muted,textDecoration=TextDecoration.Underline)))) { append(label) }
        }
    }
    Text(text,Modifier.fillMaxWidth().padding(top=2.dp,bottom=2.dp),style=MaterialTheme.typography.labelSmall,color=Muted)
}

@Composable fun RecordWeather(vm: WeatherViewModel,dashboard: RecordDashboard,onRefresh: ()->Unit,onSettings: ()->Unit) {
    val config by vm.config.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val failed by vm.locationFailed.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onRefresh() }
    val data=dashboard.data
    val result=data.results[data.point]
    val snapshot=result?.snapshot?.takeIf { data.config==config }
    var expanded by rememberSaveable { mutableStateOf(false) }
    if(!loaded) return
    val hasLocation=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
    val title=when {
        data.position!=null -> stringResource(R.string.weather_current)
        dashboard.fallback!=null -> stringResource(R.string.weather_route_origin,routeName(dashboard.fallback.name))
        else -> stringResource(R.string.weather_title)
    }
    Surface(shape=RoundedCornerShape(14.dp),color=Soft.copy(alpha=.58f)) {
        Column(Modifier.fillMaxWidth().animateContentSize().padding(horizontal=14.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(title,fontSize=10.sp,color=Muted,letterSpacing=.3.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                    when {
                        !config.enabled -> Text(stringResource(R.string.weather_disabled),fontSize=12.sp,color=Muted)
                        !config.ready -> Text(stringResource(R.string.weather_configuration),fontSize=12.sp,color=Muted)
                        snapshot!=null -> {
                            val current=snapshot.current
                            if(current!=null) {
                                Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                    Text(number(current.temperature,"°"),fontSize=30.sp,lineHeight=34.sp,fontWeight=FontWeight.Medium)
                                    Column(Modifier.padding(bottom=2.dp),verticalArrangement=Arrangement.spacedBy(1.dp)) {
                                        Text(condition(current),fontSize=13.sp,lineHeight=17.sp,fontWeight=FontWeight.Medium,maxLines=1)
                                        val chance=precipitationChance(listOf(snapshot),dashboard.now,dashboard.now+3_600_000)
                                        Text(stringResource(R.string.weather_feels,number(current.feelsLike,"°C"))+" · "+stringResource(R.string.weather_rain_short,number(chance,"%")),
                                            fontSize=10.sp,lineHeight=14.sp,color=Muted,maxLines=1)
                                    }
                                }
                            } else Text(stringResource(R.string.weather_data_failed),fontSize=12.sp,color=Muted)
                        }
                        data.loading -> Text(stringResource(R.string.weather_loading),fontSize=12.sp,color=Muted)
                        data.point==null -> Text(stringResource(if(failed) R.string.weather_location_failed else R.string.weather_need_location),fontSize=12.sp,lineHeight=17.sp,color=Muted)
                        else -> Text(stringResource(R.string.weather_data_failed),fontSize=12.sp,color=Muted)
                    }
                }
                if(snapshot!=null) {
                    val day=snapshot.days.firstOrNull { it.date==Instant.ofEpochMilli(dashboard.now).atZone(ZoneId.of(snapshot.zone)).toLocalDate() }
                    Column(horizontalAlignment=Alignment.End,verticalArrangement=Arrangement.spacedBy(0.dp)) {
                        if(day!=null) Text(stringResource(R.string.weather_today_short,number(day.low,"°"),number(day.high,"°")),fontSize=10.sp,color=Muted,maxLines=1)
                        IconButton(onClick={ expanded=!expanded },modifier=Modifier.size(36.dp).semantics { contentDescription=context.getString(if(expanded) R.string.weather_details_hide else R.string.weather_details) }) {
                            AppGlyph(Glyph.DOWN,Modifier.size(17.dp).rotate(if(expanded) 180f else 0f),Muted)
                        }
                    }
                }
            }
            if(expanded && snapshot!=null) {
                HorizontalDivider(color=Line.copy(alpha=.7f))
                snapshot.current?.let { current ->
                    Text(stringResource(R.string.weather_wind,number(current.wind),number(current.gust)),fontSize=11.sp,color=Muted)
                }
                val chance=precipitationChance(listOf(snapshot),dashboard.now,dashboard.now+3_600_000)
                Text(stringResource(R.string.weather_next_hour_rain,number(chance,"%")),fontSize=11.sp,color=Muted)
                Text(stringResource(R.string.weather_updated,clock(snapshot.fetchedAt,snapshot.zone)),fontSize=10.sp,color=Muted)
            }
            val needsLocation=!hasLocation || data.position==null
            if(config.enabled && needsLocation && !data.loading && snapshot==null) {
                TextButton(onClick={
                    if(hasLocation) onRefresh() else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
                },contentPadding=PaddingValues(horizontal=0.dp,vertical=0.dp),modifier=Modifier.height(30.dp)) {
                    Text(stringResource(R.string.weather_use_location),fontSize=12.sp)
                }
            }
            if(config.enabled && needsLocation && !data.loading && snapshot!=null && expanded) {
                TextButton(onClick={
                    if(hasLocation) onRefresh() else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
                },contentPadding=PaddingValues(horizontal=0.dp,vertical=0.dp),modifier=Modifier.height(30.dp)) {
                    Text(stringResource(R.string.weather_use_location),fontSize=12.sp)
                }
            }
            if(!config.ready) {
                TextButton(onClick=onSettings,contentPadding=PaddingValues(horizontal=0.dp,vertical=0.dp),modifier=Modifier.height(30.dp)) {
                    Text(stringResource(R.string.settings),fontSize=12.sp)
                }
            }
        }
    }
}

@Composable fun RouteRecommendations(dashboard: RecordDashboard,vm: WeatherViewModel,onSettings: ()->Unit={},onStart: (String)->Unit) {
    if(dashboard.plans.today.isEmpty() && dashboard.plans.tomorrow.isEmpty()) {
        val today=Instant.ofEpochMilli(dashboard.now).atZone(ZoneId.systemDefault()).toLocalDate()
        val tomorrow=today.plusDays(1)
        val todayUnknown=dashboard.workdayPlans && dashboard.calendar.workday(today)==null
        val tomorrowUnknown=dashboard.workdayPlans && dashboard.calendar.workday(tomorrow)==null
        if(todayUnknown || tomorrowUnknown) {
            if(dashboard.calendar.loading) Text(stringResource(R.string.calendar_loading),fontSize=12.sp,lineHeight=18.sp,color=Muted)
            else listOfNotNull(today.takeIf { todayUnknown }?.year,tomorrow.takeIf { tomorrowUnknown }?.year).distinct().forEach { year ->
                Text(stringResource(R.string.calendar_not_bundled,year),fontSize=12.sp,lineHeight=18.sp,color=Muted)
            }
        } else Text(stringResource(R.string.no_upcoming_routes),fontSize=12.sp,lineHeight=18.sp,color=Muted)
        return
    }
    listOf(R.string.today_routes to dashboard.plans.today,R.string.tomorrow_routes to dashboard.plans.tomorrow).forEach { (heading,items) ->
        Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.Bottom) {
            Text(stringResource(heading),Modifier.weight(1f),fontSize=15.sp,fontWeight=FontWeight.SemiBold,lineHeight=20.sp)
            if(items.isNotEmpty()) Text(items.size.toString(),fontSize=11.sp,color=Muted)
        }
        val date=Instant.ofEpochMilli(dashboard.now).atZone(ZoneId.systemDefault()).toLocalDate().let { if(heading==R.string.tomorrow_routes) it.plusDays(1) else it }
        val unknown=dashboard.workdayPlans && dashboard.calendar.workday(date)==null
        if(unknown) Text(if(dashboard.calendar.loading) stringResource(R.string.calendar_loading)
            else stringResource(R.string.calendar_not_bundled,dashboard.calendar.missingYear(date)),fontSize=12.sp,lineHeight=18.sp,color=Muted)
        if(items.isEmpty() && !unknown) Text(stringResource(if(heading==R.string.today_routes) R.string.no_today_routes else R.string.no_tomorrow_routes),fontSize=12.sp,color=Muted)
        items.forEach { item -> key("${item.date}:${item.route.id}") { RouteStartCard(item,dashboard,vm,onSettings,onStart) } }
    }
}
@Composable private fun RouteStartCard(item: RouteSuggestion,dashboard: RecordDashboard,vm: WeatherViewModel,onSettings: ()->Unit,onStart: (String)->Unit) {
    val context=LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()
    val departureClock=clock(item.departure,ZoneId.systemDefault().id)
    val today=Instant.ofEpochMilli(dashboard.now).atZone(ZoneId.systemDefault()).toLocalDate()
    val departureLabel=if(item.date<today) stringResource(R.string.departure_yesterday,departureClock) else departureClock
    val points=remember(item.route.shape) { item.route.weatherEnds() }
    val results=points.mapNotNull { dashboard.data.results[it] }
    val snapshots=if(config.enabled) results.mapNotNull { it.snapshot }.filter { it.provider==config.provider } else emptyList()
    val until=item.departure+(item.duration ?: 3_600_000)
    val hours=if(config.enabled) forecastWindow(snapshots,item.departure,until) else emptyList()
    val complete=if(config.enabled && snapshots.size==2) snapshots.all { snapshot ->
        val times=forecastWindow(listOf(snapshot),item.departure,until).sortedBy { it.time }
        times.isNotEmpty() && times.first().time<=item.departure && times.last().time+3_600_000>=until &&
            times.zipWithNext().all { (a,b) -> b.time-a.time<=3_600_000 }
    } else false
    val weatherLine=if(config.enabled) when {
        !config.ready -> stringResource(R.string.weather_configuration)
        hours.isNotEmpty() -> stringResource(R.string.weather_route_compact,
            number(hours.mapNotNull { it.feelsLike }.minOrNull(),"°C"),
            number(hours.mapNotNull { it.feelsLike }.maxOrNull(),"°C"),
            number(precipitationChance(snapshots,item.departure,until),"%"))
        dashboard.data.loading -> stringResource(R.string.weather_loading)
        else -> stringResource(R.string.weather_no_forecast)
    } else null
    Surface(shape=RoundedCornerShape(14.dp),color=Surface,border=BorderStroke(1.dp,Line.copy(alpha=.65f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text(routeName(item.route.name),fontSize=15.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text(directionName(item.route.direction),fontSize=11.sp,color=Moving,maxLines=1)
                }
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(departureLabel,fontSize=12.sp,lineHeight=16.sp,fontFamily=androidx.compose.ui.text.font.FontFamily.Monospace,fontWeight=FontWeight.Medium,color=Ink,maxLines=1)
                    if(weatherLine!=null) Text("· "+weatherLine,fontSize=11.sp,lineHeight=16.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                if(config.enabled && hours.isNotEmpty() && !complete) Text(stringResource(R.string.weather_preview_partial),fontSize=10.sp,lineHeight=14.sp,color=Muted)
                RouteTrafficInfo(item,dashboard,onSettings)
            }
            FilledIconButton(onClick={ onStart(item.route.id) },shape=CircleShape,modifier=Modifier.size(44.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Accent,contentColor=Ink)) {
                AppGlyph(Glyph.PLAY,Modifier.size(18.dp).semantics {
                    contentDescription=context.getString(R.string.start_route,item.route.name,context.getString(if(item.route.direction=="HOME") R.string.home else R.string.work))
                })
            }
        }
    }
}
