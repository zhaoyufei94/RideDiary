package com.ridediary.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridediary.R
import com.ridediary.data.*
import com.ridediary.core.Fix

@Composable private fun DirectionPicker(value: String,blocked: Set<String> = emptySet(),onChange: (String)->Unit) {
    Text(stringResource(R.string.direction),fontSize=14.sp,color=Muted)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        listOf("WORK","HOME").forEach { d ->
            FilterChip(selected=value==d,enabled=d !in blocked,onClick={ onChange(d) },label={ Text(directionName(d)) },shape=RoundedCornerShape(12.dp),
                colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Ink,selectedLabelColor=Surface))
        }
    }
}
@Composable fun RouteDialog(points: List<Fix>,route: RouteRecord?=null,initialName: String="",initialDirection: String="WORK",lockName: Boolean=false,
    groups: List<RouteGroup> = emptyList(),onDismiss: ()->Unit,onSave: (String,String,Double,Double)->Unit) {
    var name by remember { mutableStateOf(route?.name ?: initialName) }; var direction by remember { mutableStateOf(route?.direction ?: initialDirection) }
    var start by remember { mutableStateOf((route?.startRadius?.toInt() ?: 100).toString()) }; var end by remember { mutableStateOf((route?.endRadius?.toInt() ?: 70).toString()) }
    val sr=start.toDoubleOrNull(); val er=end.toDoubleOrNull()
    val existing=groups.firstOrNull { com.ridediary.core.RouteIdentity.key(it.name)==com.ridediary.core.RouteIdentity.key(name) }
    val blocked=existing?.variants?.filter { it.id!=route?.id && it.direction!=route?.direction }?.map { it.direction }?.toSet() ?: emptySet()
    val enoughTrack=route!=null || (points.count { com.ridediary.core.good(it) }>=4 && com.ridediary.core.Analyzer.analyze(points,points.lastOrNull()?.t ?: 0).distance>=com.ridediary.core.Policy.MIN_ROUTE_DISTANCE_M)
    val valid=enoughTrack && name.isNotBlank() && direction !in blocked && sr!=null && er!=null && sr in 20.0..500.0 && er in 20.0..500.0
    EditorSheet(title=if(route!=null) stringResource(R.string.edit_route) else if(lockName) stringResource(R.string.add_direction,directionName(direction)) else stringResource(R.string.create_new_route),
        subtitle=stringResource(if(route==null) R.string.route_create_hint else R.string.route_edit_hint),
        onDismiss=onDismiss,saveEnabled=valid,onSave={ onSave(name,direction,sr!!,er!!) }) {
        OutlinedTextField(value=name,onValueChange={ name=it },readOnly=lockName,modifier=Modifier.fillMaxWidth(),label={ Text(stringResource(R.string.route_name)) },singleLine=true,shape=RoundedCornerShape(14.dp))
        DirectionPicker(direction,blocked) { direction=it }
        if(direction in blocked) Note(stringResource(R.string.route_exists_direction))
        Text(stringResource(R.string.endpoint_settings),fontSize=16.sp,fontWeight=FontWeight.Medium)
        OutlinedTextField(value=start,onValueChange={ start=it },modifier=Modifier.fillMaxWidth(),label={ Text(stringResource(R.string.start_radius)) },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,shape=RoundedCornerShape(14.dp),isError=sr==null || sr !in 20.0..500.0)
        OutlinedTextField(value=end,onValueChange={ end=it },modifier=Modifier.fillMaxWidth(),label={ Text(stringResource(R.string.end_radius)) },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,shape=RoundedCornerShape(14.dp),isError=er==null || er !in 20.0..500.0)
        Text(stringResource(R.string.radius_range),fontSize=13.sp,color=Muted)
        if(!enoughTrack) Note(stringResource(R.string.route_too_short))
        Trace(points)
        Text(stringResource(R.string.endpoint_summary),fontSize=13.sp,lineHeight=20.sp,color=Muted)
    }
}
@Composable fun ClassificationDialog(data: RideData,routes: List<RouteRecord>,onDismiss: ()->Unit,onSave: (RouteRecord?,String,Boolean,String)->Unit,
    onCreate: (String,String,Double,Double,String?,Boolean,String)->Unit) {
    val groups=routeGroups(routes)
    var selected by remember { mutableStateOf(routes.firstOrNull { it.id==data.ride.routeId }?.groupKey) }
    var direction by remember { mutableStateOf(data.ride.direction.takeIf { it in listOf("WORK","HOME") } ?: "WORK") }
    var creating by remember { mutableStateOf(false) }
    var creatingGroup by remember { mutableStateOf<RouteGroup?>(null) }
    var excluded by remember { mutableStateOf(data.ride.excluded) }
    val initialReason=if(data.ride.exclusionReason=="AUTO_ROUTE_CHECK") stringResource(R.string.auto_route_excluded) else data.ride.exclusionReason
    var reason by remember { mutableStateOf(initialReason) }
    if(creating) {
        RouteDialog(points=data.sample().points,initialName=creatingGroup?.name ?: "",initialDirection=direction,lockName=creatingGroup!=null,
            groups=groups,onDismiss={ creating=false },onSave={ name,d,start,end -> onCreate(name,d,start,end,creatingGroup?.id,excluded,reason) })
        return
    }
    EditorSheet(title=stringResource(R.string.choose_route),onDismiss=onDismiss,
        onSave={
            val group=groups.firstOrNull { it.id==selected }
            val variants=group?.direction(direction).orEmpty()
            if(group!=null && variants.isEmpty()) { creatingGroup=group; creating=true }
            else {
                val matched=com.ridediary.core.Routing.recognize(data.sample().points,variants.map { it.route() })
                onSave(variants.firstOrNull { it.id==matched?.route?.id } ?: variants.firstOrNull(),direction,excluded,reason)
            }
        }) {
        OutlinedButton(onClick={ creatingGroup=null; creating=true },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("route-create-new")) {
            Text("＋ "+stringResource(R.string.create_new_route))
        }
        groups.forEach { group ->
            Surface(onClick={ selected=group.id },shape=RoundedCornerShape(16.dp),color=if(selected==group.id) Soft else Surface,
                border=BorderStroke(1.dp,if(selected==group.id) Ink else Line),modifier=Modifier.fillMaxWidth().testTag("route-choice-${group.id}")) {
                Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(group.name,fontSize=16.sp,fontWeight=FontWeight.Medium)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("WORK","HOME").forEach { d ->
                            FilterChip(selected=selected==group.id && direction==d,onClick={ selected=group.id; direction=d },
                                label={ Text(if(group.direction(d).isEmpty()) stringResource(R.string.add_direction,directionName(d)) else directionName(d)) },
                                modifier=Modifier.testTag("route-choice-${group.id}-$d"))
                        }
                    }
                }
            }
        }
        FilterChip(selected=selected==null,onClick={ selected=null },label={ Text(stringResource(R.string.no_route)) })
        HorizontalDivider(color=Line)
        Row { Checkbox(checked=excluded,onCheckedChange={ excluded=it }); Text(stringResource(R.string.exclude),modifier=Modifier.padding(top=12.dp),fontSize=14.sp,lineHeight=21.sp) }
        if(excluded) OutlinedTextField(value=reason,onValueChange={ reason=it },modifier=Modifier.fillMaxWidth(),label={ Text(stringResource(R.string.exclusion_reason)) },shape=RoundedCornerShape(14.dp))
    }
}

@Composable fun RouteListPicker(groups: List<RouteGroup>,selected: String?,onDismiss: ()->Unit,onSelect: (RouteGroup)->Unit) {
    AlertDialog(onDismissRequest=onDismiss,containerColor=Surface,tonalElevation=0.dp,title={ Text(stringResource(R.string.choose_route)) },text={
        Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            groups.forEach { group ->
                OutlinedButton(onClick={ onSelect(group) },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("stats-route-${group.id}")) {
                    Text((if(group.id==selected) "✓ " else "")+group.name)
                }
            }
            if(groups.isEmpty()) Note(stringResource(R.string.empty_routes))
        }
    },confirmButton={ TextButton(onClick=onDismiss) { Text(stringResource(R.string.cancel)) } })
}
@Composable fun BoundaryDialog(data: RideData,onDismiss: ()->Unit,onSave: (Long,Long)->Unit) {
    var start by remember { mutableStateOf((data.ride.boundaryStart/1000).toString()) }
    var end by remember { mutableStateOf(((data.ride.boundaryEnd ?: data.ride.elapsed)/1000).toString()) }
    val s=start.toLongOrNull()?.takeIf { it in 0..data.ride.elapsed/1000 }?.times(1000)
    val e=end.toLongOrNull()?.takeIf { it in 0..data.ride.elapsed/1000 }?.times(1000)
    val valid=s!=null && e!=null && e>s
    EditorSheet(title=stringResource(R.string.boundaries),subtitle=stringResource(R.string.boundary_hint),onDismiss=onDismiss,saveEnabled=valid,onSave={ onSave(s!!,e!!) }) {
        OutlinedTextField(value=start,onValueChange={ start=it },modifier=Modifier.fillMaxWidth(),label={ Text(stringResource(R.string.boundary_start)) },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),shape=RoundedCornerShape(14.dp))
        OutlinedTextField(value=end,onValueChange={ end=it },modifier=Modifier.fillMaxWidth(),label={ Text(stringResource(R.string.boundary_end)) },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),shape=RoundedCornerShape(14.dp))
        if(!valid) Text(stringResource(R.string.boundary_invalid),fontSize=14.sp,color=Danger)
    }
}

@Composable fun DeleteDialog(title: String,message: String,onDismiss: ()->Unit,onConfirm: ()->Unit) {
    AlertDialog(modifier=Modifier.testTag("delete-confirmation"),onDismissRequest=onDismiss,shape=RoundedCornerShape(28.dp),containerColor=Surface,tonalElevation=0.dp,
        icon={ Surface(shape=CircleShape,color=DangerSoft) { AppGlyph(Glyph.DELETE,Modifier.padding(16.dp).size(26.dp),Danger) } },
        title={ Text(title,fontSize=22.sp,fontWeight=FontWeight.SemiBold) },
        text={ Text(message,fontSize=15.sp,lineHeight=24.sp,color=Muted) },
        confirmButton={ Button(onClick=onConfirm,shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=Danger)) { Text(stringResource(R.string.delete)) } },
        dismissButton={ OutlinedButton(onClick=onDismiss,shape=RoundedCornerShape(14.dp)) { Text(stringResource(R.string.cancel)) } })
}
