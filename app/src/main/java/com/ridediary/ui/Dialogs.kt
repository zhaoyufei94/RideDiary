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

@Composable private fun DirectionPicker(value: String,onChange: (String)->Unit) {
    Text(stringResource(R.string.direction),fontSize=14.sp,color=Muted)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        listOf("WORK","HOME").forEach { d ->
            FilterChip(selected=value==d,onClick={ onChange(d) },label={ Text(directionName(d)) },shape=RoundedCornerShape(12.dp),
                colors=FilterChipDefaults.filterChipColors(selectedContainerColor=Ink,selectedLabelColor=Surface))
        }
    }
}
@Composable fun RouteDialog(points: List<Fix>,route: RouteRecord?=null,onDismiss: ()->Unit,onSave: (String,String,Double,Double)->Unit) {
    var name by remember { mutableStateOf(route?.name ?: "") }; var direction by remember { mutableStateOf(route?.direction ?: "WORK") }
    var start by remember { mutableStateOf((route?.startRadius?.toInt() ?: 100).toString()) }; var end by remember { mutableStateOf((route?.endRadius?.toInt() ?: 70).toString()) }
    val sr=start.toDoubleOrNull(); val er=end.toDoubleOrNull()
    val valid=name.isNotBlank() && sr!=null && er!=null && sr in 20.0..500.0 && er in 20.0..500.0
    EditorSheet(title=stringResource(if(route==null) R.string.route_create else R.string.edit_route),
        subtitle=stringResource(if(route==null) R.string.route_create_hint else R.string.route_edit_hint),
        onDismiss=onDismiss,saveEnabled=valid,onSave={ onSave(name,direction,sr!!,er!!) }) {
        OutlinedTextField(value=name,onValueChange={ name=it },modifier=Modifier.fillMaxWidth(),label={ Text(stringResource(R.string.route_name)) },singleLine=true,shape=RoundedCornerShape(14.dp))
        DirectionPicker(direction) { direction=it }
        Text(stringResource(R.string.endpoint_settings),fontSize=16.sp,fontWeight=FontWeight.Medium)
        OutlinedTextField(value=start,onValueChange={ start=it },modifier=Modifier.fillMaxWidth(),label={ Text(stringResource(R.string.start_radius)) },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,shape=RoundedCornerShape(14.dp),isError=sr==null || sr !in 20.0..500.0)
        OutlinedTextField(value=end,onValueChange={ end=it },modifier=Modifier.fillMaxWidth(),label={ Text(stringResource(R.string.end_radius)) },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true,shape=RoundedCornerShape(14.dp),isError=er==null || er !in 20.0..500.0)
        Text(stringResource(R.string.radius_range),fontSize=13.sp,color=Muted)
        Trace(points)
        Text(stringResource(R.string.endpoint_summary),fontSize=13.sp,lineHeight=20.sp,color=Muted)
    }
}
@Composable fun ClassificationDialog(data: RideData,routes: List<RouteRecord>,onDismiss: ()->Unit,onSave: (RouteRecord?,String,Boolean,String)->Unit) {
    var selected by remember { mutableStateOf(data.ride.routeId) }
    var direction by remember { mutableStateOf(data.ride.direction) }
    var excluded by remember { mutableStateOf(data.ride.excluded) }
    val initialReason=if(data.ride.exclusionReason=="AUTO_ROUTE_CHECK") stringResource(R.string.auto_route_excluded) else data.ride.exclusionReason
    var reason by remember { mutableStateOf(initialReason) }
    EditorSheet(title=stringResource(R.string.route_edit),subtitle=stringResource(R.string.action_classify_hint),onDismiss=onDismiss,
        onSave={ onSave(routes.firstOrNull { it.id==selected },direction,excluded,reason) }) {
        FilterChip(selected=selected==null,onClick={ selected=null },label={ Text(stringResource(R.string.no_route)) })
        routes.forEach { route -> FilterChip(selected=selected==route.id,onClick={ selected=route.id; direction=route.direction },label={ Text(routeName(route.name)+" · "+directionName(route.direction)) }) }
        if(selected==null) DirectionPicker(direction) { direction=it }
        HorizontalDivider(color=Line)
        Row { Checkbox(checked=excluded,onCheckedChange={ excluded=it }); Text(stringResource(R.string.exclude),modifier=Modifier.padding(top=12.dp),fontSize=14.sp,lineHeight=21.sp) }
        if(excluded) OutlinedTextField(value=reason,onValueChange={ reason=it },modifier=Modifier.fillMaxWidth(),label={ Text(stringResource(R.string.exclusion_reason)) },shape=RoundedCornerShape(14.dp))
    }
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
