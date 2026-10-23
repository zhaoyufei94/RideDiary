package com.ridediary.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridediary.R
import com.ridediary.core.*
import com.ridediary.data.*
import java.time.YearMonth
import kotlin.math.ceil
import kotlin.math.min

@Composable fun StatsScreen(rides: List<RideData>,routes: List<RouteRecord>) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var direction by rememberSaveable { mutableStateOf("WORK") }
    var route by rememberSaveable { mutableStateOf<String?>(null) }
    var routePicker by remember { mutableStateOf(false) }
    val groups=remember(routes) { routeGroups(routes) }
    val selectedGroup=groups.firstOrNull { it.id==route } ?: groups.firstOrNull()
    val month=YearMonth.parse(monthText)
    val all=remember(rides) { rides.map { it.sample() } }
    val routeSamples=remember(all,month,selectedGroup) {
        val routeIds=selectedGroup?.routeIds.orEmpty()
        Statistics.month(all,month).filter { it.routeId in routeIds }.sortedBy { it.startUtc }
    }
    val samples=remember(routeSamples,direction) { routeSamples.filter { it.direction==direction } }
    LaunchedEffect(groups) {
        if(groups.none { it.id==route }) {
            route=selectedGroup?.id
            direction=if(selectedGroup?.direction("WORK").isNullOrEmpty()) "HOME" else "WORK"
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.statistics),fontSize=30.sp,fontWeight=FontWeight.SemiBold)
        Text(stringResource(R.string.stats_intro),fontSize=14.sp,color=Muted)
    }
    DataCard(padding=8) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            val previous=stringResource(R.string.previous_month)
            IconButton(onClick={ monthText=month.minusMonths(1).toString() },modifier=Modifier.semantics { contentDescription=previous }) {
                AppGlyph(Glyph.LEFT)
            }
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.monthly_review),fontSize=12.sp,color=Muted,letterSpacing=1.sp)
                Text(stringResource(R.string.month_label,month.year,month.monthValue),fontSize=19.sp,fontWeight=FontWeight.Medium)
            }
            val next=stringResource(R.string.next_month)
            IconButton(onClick={ monthText=month.plusMonths(1).toString() },modifier=Modifier.semantics { contentDescription=next }) { AppGlyph(Glyph.RIGHT) }
        }
    }
    OutlinedButton(onClick={ routePicker=true },modifier=Modifier.fillMaxWidth().heightIn(min=56.dp).testTag("stats-route-picker"),shape=RoundedCornerShape(16.dp)) {
        Text(selectedGroup?.name ?: stringResource(R.string.choose_route),Modifier.weight(1f),fontSize=17.sp)
        AppGlyph(Glyph.RIGHT)
    }
    if(routePicker) RouteListPicker(groups,selectedGroup?.id,onDismiss={ routePicker=false }) { group ->
        route=group.id; direction=if(group.direction("WORK").isEmpty()) "HOME" else "WORK"; routePicker=false
    }
    // The overview combines both directions within the selected route and month.
    Surface(shape=RoundedCornerShape(26.dp),color=RideCard) {
        Column(Modifier.fillMaxWidth().testTag("stats-overview").padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text(stringResource(R.string.stats_valid_distance),Modifier.weight(1f).padding(end=12.dp),fontSize=14.sp,lineHeight=21.sp,color=Accent)
                AppGlyph(Glyph.ROUTE,Modifier.size(24.dp),Accent.copy(alpha=.8f))
            }
            val distance=decimal(routeSamples.sumOf { it.metrics.distance }/1000)
            val fontScale=LocalDensity.current.fontScale
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val size=min(48f,(maxWidth.value-40)/(distance.length*.62f*fontScale)).coerceAtLeast(24f)
                Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(distance,fontSize=size.sp,fontWeight=FontWeight.Light,color=Surface,fontFamily=FontFamily.Monospace)
                    Text("km",Modifier.padding(bottom=7.dp),fontSize=16.sp,color=Accent)
                }
            }
            HorizontalDivider(color=Surface.copy(alpha=.16f))
            Row(Modifier.fillMaxWidth()) {
                HeroCount(stringResource(R.string.commute_days),Statistics.days(routeSamples),Modifier.weight(1f).testTag("stats-day-count"))
                HeroCount(stringResource(R.string.ride_count),routeSamples.size,Modifier.weight(1f).testTag("stats-ride-count"))
            }
        }
    }
    // Detailed charts and durations below use only the chosen direction.
    Surface(shape=RoundedCornerShape(16.dp),color=Soft.copy(alpha=.7f)) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            listOf("WORK","HOME").forEach { d ->
                Surface(onClick={ direction=d },modifier=Modifier.weight(1f).fillMaxHeight().testTag("stats-direction-$d").semantics { selected=direction==d },shape=RoundedCornerShape(12.dp),color=if(direction==d) Ink else Color.Transparent) {
                    Box(Modifier.fillMaxHeight().heightIn(min=48.dp).padding(horizontal=8.dp,vertical=12.dp),contentAlignment=Alignment.Center) {
                        Text(directionName(d),fontSize=14.sp,fontWeight=if(direction==d) FontWeight.Medium else FontWeight.Normal,color=if(direction==d) Surface else Muted)
                    }
                }
            }
        }
    }
    if(samples.isEmpty()) {
        DataCard {
            Surface(shape=CircleShape,color=Soft,modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=14.dp)) { AppGlyph(Glyph.CHART,Modifier.padding(20.dp).size(32.dp),Moving) }
            Text(stringResource(R.string.stats_empty_title),Modifier.align(Alignment.CenterHorizontally).padding(top=8.dp),fontSize=20.sp,fontWeight=FontWeight.Medium)
            Text(stringResource(R.string.empty_stats),Modifier.padding(bottom=12.dp),fontSize=14.sp,lineHeight=23.sp,color=Muted)
        }
        return
    }
    val total=samples.sumOf { it.metrics.elapsed }
    val moving=samples.sumOf { it.metrics.moving }; val waiting=samples.sumOf { it.metrics.waiting }; val unknown=samples.sumOf { it.metrics.unknown }
    val totals=samples.map { it.metrics.elapsed }; val waits=samples.map { it.metrics.waiting }
    AdaptivePair(
        first={ MetricTile(stringResource(R.string.total_time),duration(total),Glyph.CLOCK) },
        second={ MetricTile(stringResource(R.string.median),duration(median(totals)!!),Glyph.CHART) }
    )
    if(samples.size==1) Text(stringResource(R.string.single_sample),fontSize=13.sp,lineHeight=20.sp,color=Muted)
    SectionHeading(stringResource(R.string.stats_time_composition),stringResource(R.string.stats_time_caption))
    DataCard(modifier=Modifier.testTag("stats-composition")) {
        TimeComposition(total,moving,waiting,unknown)
    }
    SectionHeading(stringResource(R.string.chart_title),stringResource(R.string.stats_chart_hint))
    RideChart(samples)
    SectionHeading(stringResource(R.string.stats_rhythm),stringResource(R.string.stats_rhythm_caption))
    val departures=Statistics.clocks(samples,false)!!; val arrivals=Statistics.clocks(samples,true)!!
    AdaptivePair(
        first={ ClockTile(stringResource(R.string.departure),departures) },
        second={ ClockTile(stringResource(R.string.arrival_time),arrivals) }
    )
    DataCard {
        DurationSummary(stringResource(R.string.total_time),totals)
        HorizontalDivider(Modifier.padding(vertical=8.dp),color=Line.copy(alpha=.65f))
        DurationSummary(stringResource(R.string.waiting),waits)
    }
    Text(stringResource(R.string.clock_note),fontSize=12.sp,lineHeight=20.sp,color=Muted)
}

@Composable private fun HeroCount(label: String,value: Int,modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants=true) {},verticalArrangement=Arrangement.spacedBy(7.dp)) {
        Text(label,fontSize=13.sp,color=Surface.copy(alpha=.7f))
        Text(value.toString(),fontSize=26.sp,fontFamily=FontFamily.Monospace,color=Surface)
    }
}
@Composable private fun AdaptivePair(first: @Composable ()->Unit,second: @Composable ()->Unit) {
    if(LocalDensity.current.fontScale>1.3f) { first(); second() }
    else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f)) { first() }; Box(Modifier.weight(1f)) { second() }
    }
}
@Composable private fun MetricTile(label: String,value: String,glyph: Glyph) {
    DataCard(padding=18) {
        AppGlyph(glyph,Modifier.size(20.dp),Muted)
        Text(label,fontSize=13.sp,color=Muted)
        val scale=LocalDensity.current.fontScale
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val size=min(24f,maxWidth.value/(value.length*.62f*scale)).coerceAtLeast(17f)
            Text(value,fontSize=size.sp,fontWeight=FontWeight.Medium,fontFamily=FontFamily.Monospace,maxLines=1)
        }
    }
}
@Composable private fun TimeRing(total: Long,moving: Long,waiting: Long,unknown: Long,share: Double,modifier: Modifier=Modifier) {
    Box(modifier.size(130.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(7.dp).clearAndSetSemantics {}) {
            val stroke=14.dp.toPx(); var angle=-90f
            listOf(moving to Moving,waiting to Wait,unknown to Line).forEach { (time,color) ->
                val sweep=360f*time/total.coerceAtLeast(1)
                if(sweep>0) drawArc(color,angle+1f,(sweep-2f).coerceAtLeast(.1f),false,style=Stroke(stroke))
                angle+=sweep
            }
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(decimal(share)+"%",fontSize=23.sp,fontWeight=FontWeight.Medium,fontFamily=FontFamily.Monospace)
            Text(stringResource(R.string.stats_wait_share),fontSize=12.sp,color=Muted)
        }
    }
}
@Composable private fun TimeLegend(moving: Long,waiting: Long,unknown: Long,modifier: Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(14.dp)) {
        listOf(Triple(R.string.moving,moving,Moving),Triple(R.string.waiting,waiting,Wait),Triple(R.string.unknown,unknown,Line)).forEach { (label,time,color) ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Surface(Modifier.size(7.dp),shape=CircleShape,color=color) {}
                Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(label),fontSize=12.sp,color=Muted)
                    Text(duration(time),fontSize=17.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium)
                }
            }
        }
    }
}
@Composable private fun RideChart(samples: List<Sample>) {
    var selected by remember(samples) { mutableStateOf(samples.last().id) }
    val picked=samples.first { it.id==selected }
    val scale=(ceil(samples.maxOf { it.metrics.elapsed }.toDouble()/300_000)*300_000).toLong().coerceAtLeast(300_000)
    val scroll=rememberScrollState()
    val fontScale=LocalDensity.current.fontScale.coerceAtLeast(1f)
    LaunchedEffect(samples,scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }
    DataCard(modifier=Modifier.testTag("stats-chart")) {
        Row(horizontalArrangement=Arrangement.spacedBy(14.dp),modifier=Modifier.horizontalScroll(rememberScrollState())) {
            listOf(R.string.moving to Moving,R.string.waiting to Wait,R.string.unknown to Line).forEach { (label,color) ->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Surface(Modifier.size(7.dp),shape=CircleShape,color=color) {}; Text(stringResource(label),fontSize=12.sp,color=Muted)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top=8.dp)) {
            Box(Modifier.width(50.dp*fontScale).height(156.dp+22.dp*fontScale)) {
                listOf(scale,scale/2,0L).forEachIndexed { index,value ->
                    Text(duration(value),Modifier.offset(y=22.dp*fontScale+78.dp*index-8.dp*fontScale),fontSize=12.sp,lineHeight=16.sp,color=Muted,fontFamily=FontFamily.Monospace)
                }
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                val visible=((maxWidth.value+10)/(56*fontScale+10)).toInt().coerceAtLeast(1)
                val barWidth=(maxWidth-10.dp*(visible-1))/visible
                Canvas(Modifier.fillMaxWidth().height(156.dp).offset(y=22.dp*fontScale).clearAndSetSemantics {}) {
                    for(i in 0..2) drawLine(Line.copy(alpha=.45f),Offset(0f,size.height*i/2),Offset(size.width,size.height*i/2),1.dp.toPx())
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(scroll),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                samples.forEach { sample ->
                    val metrics=sample.metrics; val active=selected==sample.id
                    val label="${date(sample.startUtc,sample.zone)} ${clock(sample.startUtc,sample.zone)} · ${stringResource(R.string.moving)} ${duration(metrics.moving)} · ${stringResource(R.string.waiting)} ${duration(metrics.waiting)} · ${stringResource(R.string.unknown)} ${duration(metrics.unknown)}"
                    Column(Modifier.width(barWidth).clip(RoundedCornerShape(10.dp)).selectable(selected=active,onClick={ selected=sample.id }).testTag("stats-bar-${sample.id}").semantics(mergeDescendants=true) { contentDescription=label },horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(if(active) duration(metrics.elapsed) else "",Modifier.height(22.dp*fontScale),fontSize=12.sp,color=Ink,fontFamily=FontFamily.Monospace)
                        Canvas(Modifier.fillMaxWidth().height(156.dp).clearAndSetSemantics {}) {
                            val height=size.height*metrics.elapsed.toFloat()/scale
                            val left=12.dp.toPx(); val width=size.width-left*2; val top=size.height-height
                            val outline=Path().apply { addRoundRect(RoundRect(Rect(left,top,left+width,size.height),CornerRadius(5.dp.toPx()))) }
                            clipPath(outline) {
                                var y=size.height
                                listOf(metrics.moving to Moving,metrics.waiting to Wait,metrics.unknown to Line).forEach { (time,color) ->
                                    val h=size.height*time.toFloat()/scale; y-=h
                                    drawRect(color.copy(alpha=if(active) 1f else .62f),Offset(left,y),Size(width,h))
                                }
                            }
                        }
                        Text(Statistics.date(sample).dayOfMonth.toString(),Modifier.padding(top=10.dp),fontSize=13.sp,fontWeight=if(active) FontWeight.SemiBold else FontWeight.Normal,color=if(active) Ink else Muted)
                        Text(clock(sample.startUtc,sample.zone),fontSize=12.sp,color=Muted)
                        Surface(Modifier.padding(top=6.dp).size(4.dp),shape=CircleShape,color=if(active) Ink else Color.Transparent) {}
                    }
                }
            }
        }
        }
        HorizontalDivider(Modifier.padding(top=8.dp),color=Line.copy(alpha=.65f))
        Column(Modifier.fillMaxWidth().testTag("stats-selected-ride").semantics(mergeDescendants=true) {},verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text(date(picked.startUtc,picked.zone)+" · "+clock(picked.startUtc,picked.zone),Modifier.weight(1f).padding(end=8.dp),fontSize=13.sp,color=Muted)
                Text(duration(picked.metrics.elapsed),fontSize=20.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium)
            }
            TimeBar(picked.metrics)
        }
    }
}
@Composable private fun ClockTile(label: String,values: Triple<Int,Int,Int>) {
    DataCard(padding=18) {
        Text(label,fontSize=13.sp,color=Muted)
        Text(clockMinutes(values.first),fontSize=27.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium)
        HorizontalDivider(color=Line.copy(alpha=.65f))
        Text(stringResource(R.string.earliest)+"  "+clockMinutes(values.second),fontSize=12.sp,color=Muted)
        Text(stringResource(R.string.latest)+"  "+clockMinutes(values.third),fontSize=12.sp,color=Muted)
    }
}
@Composable private fun DurationSummary(label: String,values: List<Long>) {
    Text(label,fontSize=15.sp,fontWeight=FontWeight.Medium)
    val entries=listOf(R.string.average to values.average().toLong(),R.string.shortest to values.min(),R.string.longest to values.max())
    if(LocalDensity.current.fontScale>1.3f) {
        entries.forEach { (name,value) ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(name),Modifier.weight(1f),fontSize=13.sp,color=Muted)
                Text(duration(value),fontSize=17.sp,fontFamily=FontFamily.Monospace)
            }
        }
    } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        entries.forEach { (name,value) ->
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(stringResource(name),fontSize=12.sp,color=Muted)
                Text(duration(value),fontSize=16.sp,fontFamily=FontFamily.Monospace,maxLines=1)
            }
        }
    }
}

@Composable fun TimeComposition(total: Long,moving: Long,waiting: Long,unknown: Long) {
    val share=waiting*100.0/total.coerceAtLeast(1)
    if(LocalDensity.current.fontScale>1.3f) {
        TimeRing(total,moving,waiting,unknown,share,Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally))
        TimeLegend(moving,waiting,unknown)
    } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(22.dp)) {
        TimeRing(total,moving,waiting,unknown,share)
        TimeLegend(moving,waiting,unknown,Modifier.weight(1f))
    }
}
