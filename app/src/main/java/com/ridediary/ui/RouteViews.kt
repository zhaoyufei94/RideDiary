package com.ridediary.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridediary.R
import com.ridediary.core.Statistics
import com.ridediary.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable private fun RouteActions(route: RouteRecord,inUse: Boolean,dismiss: ()->Unit,
    onEdit: (RouteRecord)->Unit,onDelete: (RouteRecord)->Unit) {
    SheetAction(title=stringResource(R.string.edit_route),subtitle=if(inUse) stringResource(R.string.route_in_use) else null,
        glyph=Glyph.EDIT,enabled=!inUse,onClick={ dismiss(); onEdit(route) })
    SheetAction(title=stringResource(R.string.delete_route),glyph=Glyph.DELETE,enabled=!inUse,destructive=true,
        onClick={ dismiss(); onDelete(route) })
}

@Composable fun RouteListCard(group: RouteGroup,rides: List<RideData>,activeRouteId: String?,onOpen: ()->Unit,
    onEdit: (RouteRecord)->Unit,onDelete: (RouteRecord)->Unit) {
    val inUse=activeRouteId in group.routeIds
    val workIds=group.direction("WORK").map { it.id }.toSet()
    val homeIds=group.direction("HOME").map { it.id }.toSet()
    val work=rides.count { it.ride.routeId in workIds }
    val home=rides.count { it.ride.routeId in homeIds }
    ContextMenuPanel(modifier=Modifier.testTag("route-${group.id}"),onClick=onOpen,title=group.name,
        onLongClickLabel=stringResource(R.string.route_actions),
        contentPadding=14.dp,cornerRadius=14.dp,
        menu={ dismiss -> RouteActions(group.variants.first(),inUse,dismiss,onEdit,onDelete) }) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(group.name,fontSize=16.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(inUse) Text(stringResource(R.string.recording),fontSize=10.sp,color=Moving)
                }
                Text("${directionName("WORK")} $work · ${directionName("HOME")} $home",fontSize=11.sp,color=Muted,maxLines=1)
            }
            AppGlyph(Glyph.RIGHT,Modifier.size(18.dp),Muted)
        }
    }
}

@Composable fun RouteOverview(group: RouteGroup,rides: List<RideData>,activeRouteId: String?,direction: String,onDirection: (String)->Unit,onBack: ()->Unit,
    onEdit: (RouteRecord)->Unit,onDelete: (RouteRecord)->Unit,onPlan: (RouteRecord)->Unit={}) {
    var sheet by remember(group.id) { mutableStateOf(false) }
    val shown=group.direction(direction)
    val route=shown.firstOrNull() ?: group.variants.first()
    val inUse=activeRouteId in group.routeIds
    val routeShape=route.route()
    val shownIds=remember(shown) { shown.map { it.id }.toSet() }
    val shownRides by produceState<Int?>(null,rides,shownIds) {
        value=withContext(Dispatchers.Default) { rides.count { it.ride.routeId in shownIds && Statistics.valid(it.sample()) } }
    }
    Row(Modifier.fillMaxWidth().testTag("route-overview-header"),verticalAlignment=Alignment.CenterVertically) {
        HeaderIconButton(Glyph.LEFT,stringResource(R.string.back),onClick=onBack)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
            Text(group.name,fontSize=20.sp,fontWeight=FontWeight.SemiBold,lineHeight=26.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(directionName(direction)+" · "+stringResource(R.string.route_metadata,routeShape.length/1000,shownRides ?: 0),fontSize=11.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        if(shown.isNotEmpty()) HeaderIconButton(Glyph.EDIT,stringResource(R.string.edit_route),enabled=!inUse) { onEdit(route) }
        HeaderIconButton(Glyph.MORE,stringResource(R.string.more_actions)) { sheet=true }
    }
    DirectionSwitch(direction,onSelect=onDirection,modifier=Modifier.fillMaxWidth(),tagPrefix="route-direction-")
    if(shown.isEmpty()) {
        Panel { Note(stringResource(R.string.route_direction_missing)) }
    } else {
        DataCard(padding=14) {
            Trace(routeShape.points,height=140.dp)
            Text(stringResource(R.string.route_metadata,routeShape.length/1000,shownRides ?: 0),fontSize=12.sp,color=Muted)
            Text(stringResource(R.string.start_radius)+": ${route.startRadius.toInt()} m · "+stringResource(R.string.end_radius)+": ${route.endRadius.toInt()} m",fontSize=11.sp,color=Muted)
            if(inUse) Note(stringResource(R.string.route_in_use))
            HorizontalDivider(Modifier.padding(top=2.dp),color=Line.copy(alpha=.65f))
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.travel_plan),fontSize=14.sp,fontWeight=FontWeight.Medium)
                    Text(if(route.planMode=="WORKDAYS") stringResource(R.string.plan_workdays) else planDaysLabel(route.planDays),fontSize=12.sp,color=Muted)
                    if(route.planMode=="WORKDAYS" || route.planDays!=0) Text(route.planMinute?.let { clockMinutes(it) } ?: stringResource(R.string.plan_use_history),fontSize=12.sp,color=Muted)
                }
                val label=stringResource(R.string.edit_travel_plan)
                IconButton(onClick={ onPlan(route) },modifier=Modifier.semantics { contentDescription=label }) { AppGlyph(Glyph.CALENDAR) }
            }
        }
    }
    if(sheet) ActionSheet(title=group.name,subtitle=directionName(direction),eyebrow=stringResource(R.string.route_actions),onDismiss={ sheet=false }) {
        RouteActions(route,inUse,{ sheet=false },onEdit,onDelete)
    }
}
