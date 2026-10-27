package com.ridediary.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridediary.R
import com.ridediary.core.*
import com.ridediary.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun MergeRidesDialog(rides: List<RideData>,routes: List<RouteRecord>,anchorId: String?,vm: RideViewModel,
    onDismiss: ()->Unit,onMerged: (String)->Unit) {
    var selected by rememberSaveable(anchorId) { mutableStateOf(listOfNotNull(anchorId)) }
    var saving by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<Set<MergeProblem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    val inputs by produceState<List<MergeInput>?>(null,rides,routes) {
        value=null
        value=withContext(Dispatchers.Default) { rides.map { it.mergeInput(routes) }.sortedByDescending { it.startUtc } }
    }
    val plan by produceState<MergePlan?>(null,inputs,selected) {
        value=null
        value=inputs?.let { all -> withContext(Dispatchers.Default) { TripMerge.plan(all,selected) } }
    }
    val preview=plan
    AlertDialog(modifier=Modifier.testTag("merge-rides-dialog"),onDismissRequest={ if(!saving) onDismiss() },
        containerColor=Surface,tonalElevation=0.dp,
        title={ Text(stringResource(R.string.merge_rides)) },
        text={
            LazyColumn(Modifier.fillMaxWidth().heightIn(max=440.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                item { Text(stringResource(R.string.merge_rules),fontSize=13.sp,color=Muted) }
                item { Text(stringResource(R.string.merge_preserve),fontSize=13.sp,color=Muted) }
                item { Text(stringResource(R.string.merge_selected,selected.size,TripMerge.MAX_PARTS)) }
                if(inputs==null || preview==null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if(preview!=null && selected.size>=2) {
                    item {
                        Text(stringResource(R.string.merge_total,duration(preview.elapsed),duration(preview.gap)),fontSize=13.sp)
                    }
                    items(preview.joins) { join ->
                        val index=preview.joins.indexOf(join)+1
                        Text(stringResource(R.string.merge_join,index,index+1,duration(join.gap.coerceAtLeast(0)),
                            join.distance?.let { decimal(it) } ?: "—"),fontSize=12.sp,color=Muted)
                    }
                    items((failure ?: preview.problems).toList()) { problem ->
                        Text(stringResource(mergeProblemString(problem)),color=Danger,fontSize=13.sp)
                    }
                }
                if(failed) item { Text(stringResource(R.string.error),color=Danger) }
                items(inputs.orEmpty().filter { it.state!="RECORDING" },key={ it.id }) { input ->
                    val ride=rides.firstOrNull { it.ride.id==input.id }?.ride
                    if(ride!=null) {
                        val route=routes.firstOrNull { it.id==ride.routeId }
                        val checked=input.id in selected
                        Row(Modifier.fillMaxWidth().testTag("merge-candidate-${input.id}")
                            .toggleable(value=checked,role=Role.Checkbox,enabled=!saving,onValueChange={ enabled ->
                                selected=if(enabled) selected+input.id else selected-input.id
                                failure=null; failed=false
                            }).padding(vertical=6.dp)) {
                            Checkbox(checked=checked,onCheckedChange=null,enabled=!saving)
                            Column(Modifier.weight(1f).padding(start=8.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                                Text(friendlyDateTime(input.startUtc,input.endUtc,ride.zone),fontSize=13.sp)
                                Text((route?.let { routeName(it.name)+" · "+directionName(it.direction) }
                                    ?: stringResource(R.string.ordinary))+" · "+duration(input.elapsed),fontSize=12.sp,color=Muted)
                                if(ride.mergedCount>0) Text(stringResource(R.string.merge_source_count,ride.mergedCount),fontSize=12.sp,color=Muted)
                            }
                        }
                    }
                }
            }
        },
        confirmButton={
            TextButton(enabled=!saving && preview?.valid==true,onClick={
                saving=true; failed=false; failure=null
                scope.launch {
                    try { val id=vm.mergeRides(selected); onMerged(id) }
                    catch(cancelled: CancellationException) { throw cancelled }
                    catch(rejected: MergeRejectedException) { failure=rejected.problems }
                    catch(_: Exception) { failed=true }
                    finally { saving=false }
                }
            }) { Text(stringResource(if(saving) R.string.merge_saving else R.string.merge_confirm)) }
        },
        dismissButton={ TextButton(enabled=!saving,onClick=onDismiss) { Text(stringResource(R.string.cancel)) } })
}

private fun mergeProblemString(problem: MergeProblem)=when(problem) {
    MergeProblem.COUNT -> R.string.merge_problem_count
    MergeProblem.MISSING -> R.string.merge_problem_missing
    MergeProblem.ACTIVE -> R.string.merge_problem_active
    MergeProblem.HIDDEN -> R.string.merge_problem_hidden
    MergeProblem.TIME -> R.string.merge_problem_time
    MergeProblem.OVERLAP -> R.string.merge_problem_overlap
    MergeProblem.GAP -> R.string.merge_problem_gap
    MergeProblem.POSITION -> R.string.merge_problem_position
    MergeProblem.DISTANCE -> R.string.merge_problem_distance
    MergeProblem.SPEED -> R.string.merge_problem_speed
    MergeProblem.ROUTE -> R.string.merge_problem_route
    MergeProblem.SKIPPED -> R.string.merge_problem_skipped
}
