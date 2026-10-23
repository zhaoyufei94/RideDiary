package com.ridediary.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ridediary.R
import com.ridediary.data.*
import com.ridediary.weather.TravelPlans
import java.time.*
import java.time.format.TextStyle
import androidx.compose.ui.platform.LocalContext

@Composable fun planDaysLabel(days: Int): String {
    val locale=LocalContext.current.resources.configuration.locales[0]
    return when(days) {
        0 -> stringResource(R.string.plan_disabled)
        TravelPlans.WEEKDAYS -> stringResource(R.string.plan_weekdays)
        TravelPlans.EVERY_DAY -> stringResource(R.string.plan_every_day)
        else -> DayOfWeek.entries.filter { days and (1 shl (it.value-1))!=0 }
            .joinToString(" / ") { it.getDisplayName(TextStyle.SHORT,locale) }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TravelPlanDialog(route: RouteRecord,rides: List<RideData>,routes: List<RouteRecord>,onDismiss: ()->Unit,onSave: (String,Int,Int?)->Unit) {
    var mode by rememberSaveable(route.id) { mutableStateOf(route.planMode) }
    var days by rememberSaveable(route.id) { mutableIntStateOf(route.planDays) }
    var automatic by rememberSaveable(route.id) { mutableStateOf(route.planMinute==null) }
    val minute=remember(route.id) {
        val siblings=routes.filter { it.groupKey==route.groupKey && it.direction==route.direction }
        val history=TravelPlans.history(rides,siblings,System.currentTimeMillis()).take(20)
        route.planMinute ?: TravelPlans.learnedMinute(history,LocalDate.now(),ZoneId.systemDefault()) ?: TravelPlans.defaultMinute(route.direction)
    }
    var selectedMinute by rememberSaveable(route.id) { mutableIntStateOf(minute) }
    var timeOpen by rememberSaveable { mutableStateOf(false) }
    val locale=LocalContext.current.resources.configuration.locales[0]
    EditorSheet(title=stringResource(R.string.travel_plan),subtitle=routeName(route.name)+" · "+directionName(route.direction),
        onDismiss=onDismiss,onSave={ onSave(mode,days,if(automatic) null else selectedMinute) }) {
        Text(stringResource(R.string.plan_repeat),style=MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=mode=="WORKDAYS",onClick={ mode="WORKDAYS" },label={ Text(stringResource(R.string.plan_workdays)) })
            FilterChip(selected=mode=="CUSTOM",onClick={ mode="CUSTOM" },label={ Text(stringResource(R.string.plan_custom)) })
        }
        if(mode=="WORKDAYS") Text(stringResource(R.string.plan_workday_rules),style=MaterialTheme.typography.bodySmall,color=Muted)
        else {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=days==TravelPlans.WEEKDAYS,onClick={ days=TravelPlans.WEEKDAYS },label={ Text(stringResource(R.string.plan_weekdays)) })
            FilterChip(selected=days==TravelPlans.EVERY_DAY,onClick={ days=TravelPlans.EVERY_DAY },label={ Text(stringResource(R.string.plan_every_day)) })
        }
        Column {
            DayOfWeek.entries.forEach { day ->
                val bit=1 shl (day.value-1)
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=days and bit!=0,role=Role.Checkbox,
                    onValueChange={ checked -> days=if(checked) days or bit else days and bit.inv() }),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=days and bit!=0,onCheckedChange=null)
                    Text(day.getDisplayName(TextStyle.FULL,locale),Modifier.padding(start=12.dp))
                }
            }
        }
        if(days==0) Text(stringResource(R.string.plan_disabled),style=MaterialTheme.typography.bodyMedium,color=Muted)
        }
        HorizontalDivider(color=Line)
        Text(stringResource(R.string.plan_departure),style=MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(value=automatic,role=Role.Switch,onValueChange={ automatic=it }),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.plan_use_history),Modifier.weight(1f))
            Switch(checked=automatic,onCheckedChange=null)
        }
        if(automatic) Text(stringResource(R.string.plan_time_fallback),style=MaterialTheme.typography.bodySmall,color=Muted)
        else OutlinedButton(onClick={ timeOpen=true }) { Text(clockMinutes(selectedMinute)) }
    }
    if(timeOpen) {
        val state=rememberTimePickerState(initialHour=selectedMinute/60,initialMinute=selectedMinute%60,is24Hour=true)
        AlertDialog(onDismissRequest={ timeOpen=false },title={ Text(stringResource(R.string.plan_departure)) },
            text={ TimeInput(state=state) },containerColor=Surface,
            confirmButton={ TextButton(onClick={ selectedMinute=state.hour*60+state.minute; timeOpen=false }) { Text(stringResource(R.string.save)) } },
            dismissButton={ TextButton(onClick={ timeOpen=false }) { Text(stringResource(R.string.cancel)) } })
    }
}
