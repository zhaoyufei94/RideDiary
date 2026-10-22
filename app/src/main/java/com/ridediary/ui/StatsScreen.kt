package com.ridediary.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridediary.R
import com.ridediary.core.*
import com.ridediary.data.*
import java.time.YearMonth

@Composable fun StatsScreen(rides: List<RideData>,routes: List<RouteRecord>) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var direction by rememberSaveable { mutableStateOf<String?>(null) }
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    val month=YearMonth.parse(monthText)
    Text(stringResource(R.string.statistics),style=MaterialTheme.typography.headlineMedium)
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        TextButton(onClick={ monthText=month.minusMonths(1).toString() }) { Text("‹",Modifier.semantics { contentDescription="" },fontSize=24.sp); Text(stringResource(R.string.previous_month)) }
        TextButton(onClick={ monthText=month.plusMonths(1).toString() }) { Text(stringResource(R.string.next_month)); Text("›",fontSize=24.sp) }
    }
    Text(stringResource(R.string.month_label,month.year,month.monthValue),style=MaterialTheme.typography.titleLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(selected=direction==null,onClick={ direction=null },label={ Text(stringResource(R.string.all)) })
        listOf("WORK","HOME").forEach { d -> FilterChip(selected=direction==d,onClick={ direction=d },label={ Text(directionName(d)) }) }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(selected=route==null,onClick={ route=null },label={ Text(stringResource(R.string.all)) })
        routes.forEach { r -> FilterChip(selected=route==r.id,onClick={ route=r.id },label={ Text(routeName(r.name)) }) }
    }
    val samples=Statistics.month(rides.map { it.sample() },month,route,direction).sortedBy { it.startUtc }
    if(samples.isEmpty()) { Panel { Text(stringResource(R.string.empty_stats)) }; return }
    val total=samples.sumOf { it.metrics.elapsed }; val waiting=samples.sumOf { it.metrics.waiting }
    MetricPair(stringResource(R.string.commute_days),Statistics.days(samples).toString(),stringResource(R.string.ride_count),samples.size.toString())
    MetricPair(stringResource(R.string.total_distance),decimal(samples.sumOf { it.metrics.distance }/1000)+" km",stringResource(R.string.total_time),duration(total))
    MetricPair(stringResource(R.string.waiting),duration(waiting),stringResource(R.string.wait_share),decimal(waiting*100.0/total.coerceAtLeast(1))+"%")
    if(samples.size==1) Note(stringResource(R.string.single_sample))
    SectionTitle(stringResource(R.string.chart_title))
    Panel {
        val largest=samples.maxOf { it.metrics.elapsed }.coerceAtLeast(1)
        val movingLabel=stringResource(R.string.moving); val waitLabel=stringResource(R.string.waiting); val unknownLabel=stringResource(R.string.unknown)
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            samples.forEach { sample ->
                val m=sample.metrics
                val description="${date(sample.startUtc,sample.zone)} ${clock(sample.startUtc,sample.zone)}: $movingLabel ${duration(m.moving)}, $waitLabel ${duration(m.waiting)}, $unknownLabel ${duration(m.unknown)}"
                Column(Modifier.width(54.dp).semantics(mergeDescendants=true) { contentDescription=description }) {
                    Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                        var y=size.height
                        listOf(m.moving to Accent,m.waiting to Wait,m.unknown to Line).forEach { (time,color) ->
                            val h=size.height*time.toFloat()/largest
                            y-=h; drawRect(color,Offset(6.dp.toPx(),y),Size(size.width-12.dp.toPx(),h))
                        }
                    }
                    Text(Statistics.date(sample).dayOfMonth.toString(),fontSize=14.sp)
                    Text(clock(sample.startUtc,sample.zone),fontSize=12.sp)
                }
            }
        }
        Note("$movingLabel / $waitLabel / $unknownLabel")
    }
    Panel {
        val fontScale=LocalDensity.current.fontScale.coerceAtLeast(1f)
        BoxWithConstraints {
        val tableWidth=maxWidth*fontScale
        Column(Modifier.horizontalScroll(rememberScrollState())) {
        Column(Modifier.width(tableWidth)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("",Modifier.weight(1.1f)); Text(stringResource(R.string.average),Modifier.weight(1f),fontSize=13.sp); Text(stringResource(R.string.minimum),Modifier.weight(1f),fontSize=13.sp); Text(stringResource(R.string.maximum),Modifier.weight(1f),fontSize=13.sp)
        }
        val departures=Statistics.clocks(samples,false)!!; val arrivals=Statistics.clocks(samples,true)!!
        StatRow(stringResource(R.string.departure),clockMinutes(departures.first),clockMinutes(departures.second),clockMinutes(departures.third))
        StatRow(stringResource(R.string.arrival_time),clockMinutes(arrivals.first),clockMinutes(arrivals.second),clockMinutes(arrivals.third))
        val totals=samples.map { it.metrics.elapsed }; val waits=samples.map { it.metrics.waiting }
        StatRow(stringResource(R.string.total_time),duration(totals.average().toLong()),duration(totals.min()),duration(totals.max()))
        StatRow(stringResource(R.string.waiting),duration(waits.average().toLong()),duration(waits.min()),duration(waits.max()))
        Text(stringResource(R.string.median)+": "+duration(median(totals)!!),fontSize=15.sp)
        }
        }
        }
        Note(stringResource(R.string.clock_note))
    }
}
@Composable private fun StatRow(label: String,a: String,b: String,c: String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,Modifier.weight(1.1f),fontSize=13.sp)
        listOf(a,b,c).forEach { Text(it,Modifier.weight(1f),fontFamily=FontFamily.Monospace,fontSize=13.sp) }
    }
}
