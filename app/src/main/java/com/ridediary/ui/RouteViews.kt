package com.ridediary.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ridediary.R
import com.ridediary.core.Statistics
import com.ridediary.data.*

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
    ContextMenuPanel(modifier=Modifier.testTag("route-${group.id}"),onClick=onOpen,title=group.name,
        onLongClickLabel=stringResource(R.string.route_actions),
        menu={ dismiss -> RouteActions(group.variants.first(),inUse,dismiss,onEdit,onDelete) }) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(group.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            AppGlyph(Glyph.RIGHT,Modifier.size(20.dp),Muted)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            listOf("WORK","HOME").forEach { direction ->
                val ids=group.direction(direction).map { it.id }.toSet()
                val count=rides.count { it.ride.routeId in ids }
                Text(directionName(direction)+" · "+count,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium,color=Muted)
            }
        }
        if(inUse) Text(stringResource(R.string.recording),style=MaterialTheme.typography.labelMedium)
    }
}

@Composable fun RouteOverview(group: RouteGroup,rides: List<RideData>,activeRouteId: String?,
    onEdit: (RouteRecord)->Unit,onDelete: (RouteRecord)->Unit,onPlan: (RouteRecord)->Unit={}) {
    var direction by rememberSaveable(group.id) { mutableStateOf(if(group.direction("WORK").isNotEmpty()) "WORK" else "HOME") }
    val shown=group.direction(direction)
    val route=shown.firstOrNull() ?: group.variants.first()
    val inUse=activeRouteId in group.routeIds
    Text(group.name,style=MaterialTheme.typography.headlineMedium)
    ContextMenuPanel(onLongClickLabel=stringResource(R.string.route_actions),title=group.name,subtitle=directionName(direction),
        menu={ dismiss -> RouteActions(route,inUse,dismiss,onEdit,onDelete) }) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("WORK","HOME").forEach { value ->
                    FilterChip(selected=direction==value,onClick={ direction=value },label={ Text(directionName(value)) })
                }
            }
            if(shown.isNotEmpty()) {
                val label=stringResource(R.string.edit_route)
                IconButton(enabled=!inUse,onClick={ onEdit(route) },modifier=Modifier.semantics { contentDescription=label }) {
                    AppGlyph(Glyph.EDIT)
                }
            }
        }
        if(shown.isEmpty()) Note(stringResource(R.string.route_direction_missing))
        else {
            Trace(route.route().points)
            val ids=shown.map { it.id }.toSet()
            val count=rides.count { it.ride.routeId in ids && Statistics.valid(it.sample()) }
            Note(stringResource(R.string.route_metadata,route.route().length/1000,count))
            Note(stringResource(R.string.start_radius)+": ${route.startRadius.toInt()} m · "+stringResource(R.string.end_radius)+": ${route.endRadius.toInt()} m")
        }
        if(inUse) Note(stringResource(R.string.route_in_use))
    }
    if(shown.isNotEmpty()) Panel {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.travel_plan),style=MaterialTheme.typography.titleMedium)
                Text(if(route.planMode=="WORKDAYS") stringResource(R.string.plan_workdays) else planDaysLabel(route.planDays),style=MaterialTheme.typography.bodyMedium,color=Muted)
                if(route.planMode=="WORKDAYS" || route.planDays!=0) Text(route.planMinute?.let { clockMinutes(it) } ?: stringResource(R.string.plan_use_history),style=MaterialTheme.typography.bodyMedium,color=Muted)
            }
            val label=stringResource(R.string.edit_travel_plan)
            IconButton(onClick={ onPlan(route) },modifier=Modifier.semantics { contentDescription=label }) { AppGlyph(Glyph.CALENDAR) }
        }
    }
}
