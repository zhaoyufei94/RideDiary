package com.ridediary.ui

import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridediary.R
import com.ridediary.traffic.*
import com.ridediary.weather.RouteSuggestion
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable internal fun RouteTrafficInfo(item: RouteSuggestion,dashboard: RecordDashboard,onSettings: ()->Unit) {
    if(!TrafficPolicy.eligible(item.departure,dashboard.now)) return
    val config=dashboard.data.trafficConfig ?: return
    Column(Modifier.fillMaxWidth().testTag("route-traffic-${item.route.id}"),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        if(!config.enabled || !config.ready) {
            TextButton(onClick=onSettings,contentPadding=PaddingValues(0.dp),modifier=Modifier.heightIn(min=48.dp)) {
                Text(stringResource(R.string.traffic_configure),fontSize=11.sp)
            }
            return@Column
        }
        val result=dashboard.data.traffic[item.trafficKey]
        val snapshot=result?.snapshot?.takeIf { TrafficPolicy.fresh(it.fetchedAt,dashboard.now) }
        if(snapshot==null) {
            val comparison=result?.comparisons?.maxByOrNull { it.similarity }
            TrafficDetails(stringResource(if(result==null && dashboard.data.loading) R.string.traffic_loading else failureText(result?.failure)),
                hasDetails=comparison!=null,color=Muted) { comparison?.let { TrafficComparisonInfo(it) } }
            return@Column
        }
        TrafficSnapshotView(snapshot)
    }
}

private fun failureText(failure: TrafficFailure?)=when(failure) {
    TrafficFailure.AUTH,TrafficFailure.CONFIGURATION -> R.string.traffic_auth
    TrafficFailure.ROUTE_MISMATCH -> R.string.traffic_route_mismatch
    TrafficFailure.ROUTE_COMPLEX -> R.string.traffic_route_complex
    TrafficFailure.DATA -> R.string.traffic_no_route_data
    else -> R.string.traffic_unavailable
}
private fun minutes(duration: Long)=ceil(duration/60_000.0).toInt()

@Composable internal fun TrafficSnapshotView(snapshot: TrafficSnapshot) {
    val zone=ZoneId.systemDefault().id
    val status=stringResource(when(snapshot.current.status) {
        1 -> R.string.traffic_clear
        2 -> R.string.traffic_slow
        3 -> R.string.traffic_congested
        4 -> R.string.traffic_severe
        else -> R.string.traffic_unknown
    })
    val current=stringResource(R.string.traffic_current,status)+snapshot.current.duration?.let { " · "+stringResource(R.string.traffic_driving_minutes,minutes(it)) }.orEmpty()
    // Even a low-coverage response remains explicitly unknown, never green or inferred clear.
    TrafficDetails(current,color=if(snapshot.current.status==0) Muted else Ink) {
        val trend=snapshot.trend?.let { stringResource(when(it) { 1->R.string.traffic_worsening; -1->R.string.traffic_easing; else->R.string.traffic_steady }) }
        Text(listOfNotNull(trend,stringResource(R.string.traffic_updated,clock(snapshot.fetchedAt,zone))).joinToString(" · "),fontSize=10.sp,color=Muted)
        snapshot.current.comparison?.let { TrafficComparisonInfo(it) }
    }
}

@Composable private fun TrafficDetails(summary: String,hasDetails: Boolean=true,color: Color=Ink,details: @Composable ColumnScope.()->Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val toggleLabel=stringResource(if(expanded) R.string.traffic_details_hide else R.string.traffic_details)
    Column(Modifier.fillMaxWidth().animateContentSize(),verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(summary,Modifier.weight(1f),fontSize=11.sp,color=color)
            if(hasDetails) IconButton(onClick={ expanded=!expanded },modifier=Modifier.size(36.dp).semantics { contentDescription=toggleLabel }) {
                AppGlyph(Glyph.DOWN,Modifier.size(17.dp).rotate(if(expanded) 180f else 0f),Muted)
            }
        }
        if(hasDetails && expanded) {
            HorizontalDivider(color=Line.copy(alpha=.7f))
            details()
        }
    }
}

@Composable private fun TrafficComparisonInfo(comparison: TrafficRouteComparison) {
    Text(stringResource(R.string.traffic_route_reference,(comparison.similarity*100).roundToInt()),fontSize=10.sp,color=Muted)
    Text(stringResource(R.string.traffic_route_difference,(abs(comparison.lengthRatio-1)*100).roundToInt(),
        comparison.startOffset.roundToInt(),comparison.endOffset.roundToInt()),fontSize=10.sp,color=Muted)
}
