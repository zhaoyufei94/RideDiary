package com.ridediary.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ridediary.R
import com.ridediary.data.*

@Composable private fun DirectionPicker(value: String,onChange: (String)->Unit) {
    Text(stringResource(R.string.direction))
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("WORK","HOME").forEach { d -> FilterChip(selected=value==d,onClick={ onChange(d) },label={ Text(directionName(d)) }) }
    }
}
@Composable fun RouteDialog(data: RideData,onDismiss: ()->Unit,onSave: (String,String,Double,Double)->Unit) {
    var name by remember { mutableStateOf("") }; var direction by remember { mutableStateOf("WORK") }
    var start by remember { mutableStateOf("100") }; var end by remember { mutableStateOf("70") }
    val sr=start.toDoubleOrNull(); val er=end.toDoubleOrNull()
    AlertDialog(onDismissRequest=onDismiss,title={ Text(stringResource(R.string.route_create)) },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Note(stringResource(R.string.route_create_hint)); Trace(data.sample().points)
            Note(stringResource(R.string.endpoint_summary))
            OutlinedTextField(value=name,onValueChange={ name=it },label={ Text(stringResource(R.string.route_name)) },singleLine=true)
            DirectionPicker(direction) { direction=it }
            OutlinedTextField(value=start,onValueChange={ start=it },label={ Text(stringResource(R.string.start_radius)) },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
            OutlinedTextField(value=end,onValueChange={ end=it },label={ Text(stringResource(R.string.end_radius)) },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
        }
    },confirmButton={ TextButton(enabled=name.isNotBlank() && sr!=null && er!=null && sr in 20.0..500.0 && er in 20.0..500.0,onClick={ onSave(name,direction,sr!!,er!!) }) { Text(stringResource(R.string.save)) } },dismissButton={ TextButton(onClick=onDismiss) { Text(stringResource(R.string.cancel)) } })
}
@Composable fun ClassificationDialog(data: RideData,routes: List<RouteRecord>,onDismiss: ()->Unit,onSave: (RouteRecord?,String,Boolean,String)->Unit) {
    var selected by remember { mutableStateOf(data.ride.routeId) }
    var direction by remember { mutableStateOf(data.ride.direction) }
    var excluded by remember { mutableStateOf(data.ride.excluded) }
    val initialReason=if(data.ride.exclusionReason=="AUTO_ROUTE_CHECK") stringResource(R.string.auto_route_excluded) else data.ride.exclusionReason
    var reason by remember { mutableStateOf(initialReason) }
    AlertDialog(onDismissRequest=onDismiss,title={ Text(stringResource(R.string.route_edit)) },text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=selected==null,onClick={ selected=null },label={ Text(stringResource(R.string.no_route)) })
            routes.forEach { route -> FilterChip(selected=selected==route.id,onClick={ selected=route.id; direction=route.direction },label={ Text(routeName(route.name)+" · "+directionName(route.direction)) }) }
            if(selected==null) DirectionPicker(direction) { direction=it }
            Row { Checkbox(checked=excluded,onCheckedChange={ excluded=it }); Text(stringResource(R.string.exclude),modifier=Modifier.padding(top=10.dp)) }
            if(excluded) OutlinedTextField(value=reason,onValueChange={ reason=it },label={ Text(stringResource(R.string.exclusion_reason)) })
        }
    },confirmButton={ TextButton(onClick={ onSave(routes.firstOrNull { it.id==selected },direction,excluded,reason) }) { Text(stringResource(R.string.save)) } },dismissButton={ TextButton(onClick=onDismiss) { Text(stringResource(R.string.cancel)) } })
}
@Composable fun BoundaryDialog(data: RideData,onDismiss: ()->Unit,onSave: (Long,Long)->Unit) {
    var start by remember { mutableStateOf((data.ride.boundaryStart/1000).toString()) }
    var end by remember { mutableStateOf(((data.ride.boundaryEnd ?: data.ride.elapsed)/1000).toString()) }
    val s=start.toLongOrNull()?.takeIf { it in 0..data.ride.elapsed/1000 }?.times(1000)
    val e=end.toLongOrNull()?.takeIf { it in 0..data.ride.elapsed/1000 }?.times(1000)
    val valid=s!=null && e!=null && e>s
    AlertDialog(onDismissRequest=onDismiss,title={ Text(stringResource(R.string.boundaries)) },text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Note(stringResource(R.string.boundary_hint))
            OutlinedTextField(value=start,onValueChange={ start=it },label={ Text(stringResource(R.string.boundary_start)) },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            OutlinedTextField(value=end,onValueChange={ end=it },label={ Text(stringResource(R.string.boundary_end)) },keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            if(!valid) Note(stringResource(R.string.boundary_invalid))
        }
    },confirmButton={ TextButton(enabled=valid,onClick={ onSave(s!!,e!!) }) { Text(stringResource(R.string.save)) } },dismissButton={ TextButton(onClick=onDismiss) { Text(stringResource(R.string.cancel)) } })
}
