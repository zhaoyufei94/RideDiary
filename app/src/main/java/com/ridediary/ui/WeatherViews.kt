package com.ridediary.ui

import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ridediary.R
import com.ridediary.core.good
import com.ridediary.calendar.WorkCalendarState
import com.ridediary.data.*
import com.ridediary.weather.*
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
    val position: WeatherPoint?=null,val point: WeatherPoint?=null,val results: Map<WeatherPoint,WeatherResult> = emptyMap())
data class RecordDashboard(val plans: PlannedRoutes,val fallback: RouteRecord?,val data: WeatherPageLoad,val now: Long,val calendar: WorkCalendarState,val workdayPlans: Boolean)
private fun RouteRecord.weatherEnds()=route().points.let { points -> listOfNotNull(points.firstOrNull(),points.lastOrNull()).map { WeatherPoint.of(it.lat,it.lon) } }

/** One page request coordinates location, distinct endpoints, refresh completion and attribution. */
@Composable fun rememberRecordDashboard(vm: WeatherViewModel,rides: List<RideData>,routes: List<RouteRecord>,active: RideData?,refresh: Long): RecordDashboard {
    val config by vm.config.collectAsStateWithLifecycle()
    val context=LocalContext.current; val foreground=weatherForeground(); val now=weatherClock()
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
    val latest by rememberUpdatedState(active)
    val language=if(LocalConfiguration.current.locales[0].language=="zh") "zh" else "en"
    val result by produceState(WeatherPageLoad(),config,foreground,now/900_000,endpoints,fallbackPoint,active?.ride?.id,refresh,language) {
        val old=if(value.config==config) value else WeatherPageLoad()
        value=old.copy(token=refresh,loading=foreground && config.enabled && config.ready,config=config)
        if(!foreground || !config.enabled || !config.ready) {
            value=value.copy(loading=false); return@produceState
        }
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
        value=WeatherPageLoad(refresh,false,config,position,point,fetched)
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
    Text(text,Modifier.fillMaxWidth().padding(top=12.dp,bottom=8.dp),style=MaterialTheme.typography.labelSmall,color=Muted)
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
    if(!loaded) return
    Panel(color=Soft.copy(alpha=.65f)) {
        Text(if(data.position!=null) stringResource(R.string.weather_current)
            else if(dashboard.fallback!=null) stringResource(R.string.weather_route_origin,dashboard.fallback.name)
            else stringResource(R.string.weather_title),style=MaterialTheme.typography.titleMedium)
        when {
            !config.enabled -> TextButton(onClick=onSettings) { Text(stringResource(R.string.weather_disabled)) }
            !config.ready -> TextButton(onClick=onSettings) { Text(stringResource(R.string.weather_configuration)) }
            snapshot!=null -> {
                snapshot.current?.let { current ->
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text(number(current.temperature,"°"),style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Medium)
                        Column {
                            Text(condition(current),style=MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.weather_feels,number(current.feelsLike,"°C")),style=MaterialTheme.typography.bodyMedium,color=Muted)
                        }
                    }
                    Text(stringResource(R.string.weather_wind,number(current.wind),number(current.gust)),style=MaterialTheme.typography.bodyMedium)
                }
                val chance=precipitationChance(listOf(snapshot),dashboard.now,dashboard.now+3_600_000)
                Text(stringResource(R.string.weather_next_hour_rain,number(chance,"%")),style=MaterialTheme.typography.bodyMedium)
                val day=snapshot.days.firstOrNull { it.date==Instant.ofEpochMilli(dashboard.now).atZone(ZoneId.of(snapshot.zone)).toLocalDate() }
                if(day!=null) {
                    HorizontalDivider(color=Line)
                    Text(stringResource(R.string.weather_today,number(day.low,"°"),number(day.high,"°"),number(day.rainChance,"%")),style=MaterialTheme.typography.bodyMedium)
                } else Text(stringResource(R.string.weather_today_unavailable),style=MaterialTheme.typography.bodyMedium,color=Muted)
                Text(stringResource(R.string.weather_updated,clock(snapshot.fetchedAt,snapshot.zone)),style=MaterialTheme.typography.labelSmall,color=Muted)
            }
            data.loading -> Text(stringResource(R.string.weather_loading),color=Muted)
            data.point==null -> Text(stringResource(if(failed) R.string.weather_location_failed else R.string.weather_need_location),color=Muted)
            else -> Text(stringResource(R.string.weather_data_failed),color=Muted)
        }
        val hasLocation=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
        if(config.enabled && (!hasLocation || data.position==null) && !data.loading) TextButton(onClick={
            if(hasLocation) onRefresh() else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
        }) { Text(stringResource(R.string.weather_use_location)) }
    }
}

@Composable fun RouteRecommendations(dashboard: RecordDashboard,vm: WeatherViewModel,onStart: (String)->Unit) {
    listOf(R.string.today_routes to dashboard.plans.today,R.string.tomorrow_routes to dashboard.plans.tomorrow).forEach { (heading,items) ->
        SectionHeading(stringResource(heading))
        val date=Instant.ofEpochMilli(dashboard.now).atZone(ZoneId.systemDefault()).toLocalDate().let { if(heading==R.string.tomorrow_routes) it.plusDays(1) else it }
        val unknown=dashboard.workdayPlans && dashboard.calendar.workday(date)==null
        if(unknown) Panel {
            Text(if(dashboard.calendar.loading) stringResource(R.string.calendar_loading)
                else stringResource(R.string.calendar_not_bundled,dashboard.calendar.missingYear(date)),color=Muted)
        }
        if(items.isEmpty() && !unknown) Panel { Text(stringResource(if(heading==R.string.today_routes) R.string.no_today_routes else R.string.no_tomorrow_routes),color=Muted) }
        items.forEach { item -> key("${item.date}:${item.route.id}") { RouteStartCard(item,dashboard,vm,onStart) } }
    }
}
@Composable private fun RouteStartCard(item: RouteSuggestion,dashboard: RecordDashboard,vm: WeatherViewModel,onStart: (String)->Unit) {
    val context=LocalContext.current
    val config by vm.config.collectAsStateWithLifecycle()
    val points=remember(item.route.shape) { item.route.weatherEnds() }
    val results=points.mapNotNull { dashboard.data.results[it] }
    Panel {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(routeName(item.route.name),style=MaterialTheme.typography.titleMedium)
                Text(directionName(item.route.direction)+" · "+clock(item.departure,ZoneId.systemDefault().id),style=MaterialTheme.typography.labelLarge,color=Muted)
            }
            FilledIconButton(onClick={ onStart(item.route.id) },shape=CircleShape,modifier=Modifier.size(48.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Accent,contentColor=Ink)) {
                AppGlyph(Glyph.PLAY,Modifier.size(20.dp).semantics {
                    contentDescription=context.getString(R.string.start_route,item.route.name,context.getString(if(item.route.direction=="HOME") R.string.home else R.string.work))
                })
            }
        }
        if(config.enabled) {
            val snapshots=results.mapNotNull { it.snapshot }.filter { it.provider==config.provider }
            val until=item.departure+(item.duration ?: 3_600_000)
            val hours=forecastWindow(snapshots,item.departure,until)
            val complete=snapshots.size==2 && snapshots.all { snapshot ->
                val times=forecastWindow(listOf(snapshot),item.departure,until).sortedBy { it.time }
                times.isNotEmpty() && times.first().time<=item.departure && times.last().time+3_600_000>=until &&
                    times.zipWithNext().all { (a,b) -> b.time-a.time<=3_600_000 }
            }
            when {
                !config.ready -> Text(stringResource(R.string.weather_configuration),style=MaterialTheme.typography.bodySmall,color=Muted)
                hours.isNotEmpty() -> {
                    Text(stringResource(R.string.weather_route_preview,number(hours.mapNotNull { it.feelsLike }.minOrNull(),"°"),
                        number(hours.mapNotNull { it.feelsLike }.maxOrNull(),"°"),number(precipitationChance(snapshots,item.departure,until),"%"),number(maximumGust(snapshots,item.departure,until))),style=MaterialTheme.typography.bodyMedium)
                    if(!complete) Text(stringResource(R.string.weather_preview_partial),style=MaterialTheme.typography.labelSmall,color=Muted)
                }
                dashboard.data.loading -> Text(stringResource(R.string.weather_loading),style=MaterialTheme.typography.bodySmall,color=Muted)
                else -> Text(stringResource(R.string.weather_no_forecast),style=MaterialTheme.typography.bodySmall,color=Muted)
            }
        }
    }
}
