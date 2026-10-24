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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridediary.R
import com.ridediary.core.*
import com.ridediary.data.*
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val completed=remember(rides) { rides.filter { it.ride.state=="COMPLETED" } }
    val loaded by produceState<List<Sample>?>(null,completed) {
        value=withContext(Dispatchers.Default) { completed.map { it.sample() } }
    }
    val all=loaded.orEmpty()
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
    val previous=stringResource(R.string.previous_month)
    val next=stringResource(R.string.next_month)
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        HeaderIconButton(Glyph.LEFT,previous) { monthText=month.minusMonths(1).toString() }
        Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(1.dp)) {
            Text(stringResource(R.string.monthly_review),fontSize=11.sp,color=Muted,letterSpacing=.6.sp,maxLines=1)
            Text(stringResource(R.string.month_label,month.year,month.monthValue),fontSize=20.sp,fontWeight=FontWeight.SemiBold,lineHeight=26.sp,maxLines=2,textAlign=TextAlign.Center)
        }
        HeaderIconButton(Glyph.RIGHT,next) { monthText=month.plusMonths(1).toString() }
    }
    if(groups.isEmpty()) {
        DataCard(padding=14) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                AppGlyph(Glyph.ROUTE,Modifier.size(22.dp),Moving)
                Text(stringResource(R.string.empty_routes),fontSize=13.sp,lineHeight=19.sp,color=Muted)
            }
        }
        return
    }
    val wideFont=LocalDensity.current.fontScale>1.3f
    if(wideFont) {
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
            RouteFilterButton(selectedGroup?.name ?: stringResource(R.string.choose_route),{ routePicker=true },Modifier.fillMaxWidth())
            DirectionSwitch(direction,onSelect={ direction=it },modifier=Modifier.fillMaxWidth(),tagPrefix="stats-direction-")
        }
    } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        RouteFilterButton(selectedGroup?.name ?: stringResource(R.string.choose_route),{ routePicker=true },Modifier.weight(1.35f))
        DirectionSwitch(direction,onSelect={ direction=it },modifier=Modifier.weight(1f),tagPrefix="stats-direction-")
    }
    if(routePicker) RouteListPicker(groups,selectedGroup?.id,onDismiss={ routePicker=false }) { group ->
        route=group.id; direction=if(group.direction("WORK").isEmpty()) "HOME" else "WORK"; routePicker=false
    }
    if(loaded==null) {
        DataCard(padding=14) { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        return
    }
    // The overview combines both directions within the selected route and month.
    Surface(shape=RoundedCornerShape(20.dp),color=RideCard) {
        Column(Modifier.fillMaxWidth().testTag("stats-overview").padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                Text(stringResource(R.string.stats_valid_distance),Modifier.weight(1f).padding(end=10.dp),fontSize=11.sp,lineHeight=16.sp,color=Accent)
                AppGlyph(Glyph.ROUTE,Modifier.size(20.dp),Accent.copy(alpha=.8f))
            }
            val distance=decimal(routeSamples.sumOf { it.metrics.distance }/1000)
            val fontScale=LocalDensity.current.fontScale
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val size=min(42f,(maxWidth.value-40)/(distance.length*.62f*fontScale)).coerceAtLeast(24f)
                Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    Text(distance,fontSize=size.sp,fontWeight=FontWeight.Light,color=Surface,fontFamily=FontFamily.Monospace)
                    Text("km",Modifier.padding(bottom=5.dp),fontSize=14.sp,color=Accent)
                }
            }
            HorizontalDivider(color=Surface.copy(alpha=.16f))
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                HeroCount(stringResource(R.string.commute_days),Statistics.days(routeSamples).toString(),Modifier.weight(1f).testTag("stats-day-count"))
                HeroCount(stringResource(R.string.ride_count),routeSamples.size.toString(),Modifier.weight(1f).testTag("stats-ride-count"))
            }
        }
    }
    if(samples.isEmpty()) {
        Panel {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Surface(shape=CircleShape,color=Soft) { AppGlyph(Glyph.CHART,Modifier.padding(9.dp).size(20.dp),Moving) }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.stats_empty_title),fontSize=15.sp,fontWeight=FontWeight.Medium)
                    Text(stringResource(R.string.empty_stats),fontSize=12.sp,lineHeight=17.sp,color=Muted)
                }
            }
        }
        return
    }
    val total=samples.sumOf { it.metrics.elapsed }
    val moving=samples.sumOf { it.metrics.moving }; val waiting=samples.sumOf { it.metrics.waiting }; val unknown=samples.sumOf { it.metrics.unknown }
    val totals=samples.map { it.metrics.elapsed }; val waits=samples.map { it.metrics.waiting }
    DataCard(padding=14) {
        if(wideFont) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            CompactMetric(stringResource(R.string.total_time),duration(total))
            HorizontalDivider(color=Line.copy(alpha=.65f))
            CompactMetric(stringResource(R.string.median),duration(median(totals)!!))
        } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            CompactMetric(stringResource(R.string.total_time),duration(total),Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(30.dp).background(Line.copy(alpha=.7f)))
            CompactMetric(stringResource(R.string.median),duration(median(totals)!!),Modifier.weight(1f))
        }
    }
    if(samples.size==1) Text(stringResource(R.string.single_sample),fontSize=12.sp,lineHeight=18.sp,color=Muted)
    SectionHeading(stringResource(R.string.stats_time_composition))
    DataCard(modifier=Modifier.testTag("stats-composition"),padding=14) {
        TimeComposition(total,moving,waiting,unknown)
    }
    SectionHeading(stringResource(R.string.chart_title))
    RideChart(samples)
    SectionHeading(stringResource(R.string.stats_rhythm))
    val departures=Statistics.clocks(samples,false)!!; val arrivals=Statistics.clocks(samples,true)!!
    AdaptivePair(
        first={ ClockTile(stringResource(R.string.departure),departures) },
        second={ ClockTile(stringResource(R.string.arrival_time),arrivals) }
    )
    DataCard(padding=14) {
        DurationSummary(stringResource(R.string.total_time),totals)
        HorizontalDivider(Modifier.padding(vertical=6.dp),color=Line.copy(alpha=.65f))
        DurationSummary(stringResource(R.string.waiting),waits)
    }
    Text(stringResource(R.string.clock_note),fontSize=11.sp,lineHeight=17.sp,color=Muted)
}

@Composable private fun RouteFilterButton(name: String,onClick: ()->Unit,modifier: Modifier) {
    OutlinedButton(onClick=onClick,modifier=modifier.heightIn(min=48.dp).testTag("stats-route-picker"),
        shape=RoundedCornerShape(14.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=0.dp)) {
        AppGlyph(Glyph.ROUTE,Modifier.size(18.dp),Muted)
        Spacer(Modifier.width(6.dp))
        Text(name,Modifier.weight(1f),fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
        AppGlyph(Glyph.DOWN,Modifier.size(16.dp),Muted)
    }
}
@Composable private fun HeroCount(label: String,value: String,modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants=true) {},verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(label,fontSize=10.sp,lineHeight=14.sp,color=Surface.copy(alpha=.66f),maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(value,fontSize=22.sp,lineHeight=27.sp,fontFamily=FontFamily.Monospace,color=Surface,maxLines=1)
    }
}
@Composable private fun AdaptivePair(first: @Composable ()->Unit,second: @Composable ()->Unit) {
    if(LocalDensity.current.fontScale>1.3f) { first(); second() }
    else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) { first() }; Box(Modifier.weight(1f)) { second() }
    }
}
@Composable private fun TimeRing(total: Long,moving: Long,waiting: Long,unknown: Long,share: Double,modifier: Modifier=Modifier) {
    Box(modifier.size(116.dp),contentAlignment=Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(6.dp).clearAndSetSemantics {}) {
            val stroke=12.dp.toPx(); var angle=-90f
            listOf(moving to Moving,waiting to Wait,unknown to Line).forEach { (time,color) ->
                val sweep=360f*time/total.coerceAtLeast(1)
                if(sweep>0) drawArc(color,angle+1f,(sweep-2f).coerceAtLeast(.1f),false,style=Stroke(stroke))
                angle+=sweep
            }
        }
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(decimal(share)+"%",fontSize=20.sp,fontWeight=FontWeight.Medium,fontFamily=FontFamily.Monospace)
            Text(stringResource(R.string.stats_wait_share),fontSize=11.sp,color=Muted)
        }
    }
}
@Composable private fun TimeLegend(moving: Long,waiting: Long,unknown: Long,modifier: Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf(Triple(R.string.moving,moving,Moving),Triple(R.string.waiting,waiting,Wait),Triple(R.string.unknown,unknown,Line)).forEach { (label,time,color) ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Surface(Modifier.size(7.dp),shape=CircleShape,color=color) {}
                Column(verticalArrangement=Arrangement.spacedBy(1.dp)) {
                    Text(stringResource(label),fontSize=11.sp,color=Muted)
                    Text(duration(time),fontSize=16.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium)
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
    DataCard(modifier=Modifier.testTag("stats-chart"),padding=12) {
        Row(horizontalArrangement=Arrangement.spacedBy(14.dp),modifier=Modifier.horizontalScroll(rememberScrollState())) {
            listOf(R.string.moving to Moving,R.string.waiting to Wait,R.string.unknown to Line).forEach { (label,color) ->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Surface(Modifier.size(7.dp),shape=CircleShape,color=color) {}; Text(stringResource(label),fontSize=11.sp,color=Muted)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top=6.dp)) {
            Box(Modifier.width(46.dp*fontScale).height(156.dp+22.dp*fontScale)) {
                listOf(scale,scale/2,0L).forEachIndexed { index,value ->
                    Text(duration(value),Modifier.offset(y=22.dp*fontScale+78.dp*index-8.dp*fontScale),fontSize=11.sp,lineHeight=16.sp,color=Muted,fontFamily=FontFamily.Monospace)
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
                        Text(if(active) duration(metrics.elapsed) else "",Modifier.height(22.dp*fontScale),fontSize=11.sp,color=Ink,fontFamily=FontFamily.Monospace)
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
                        Text(Statistics.date(sample).dayOfMonth.toString(),Modifier.padding(top=8.dp),fontSize=12.sp,fontWeight=if(active) FontWeight.SemiBold else FontWeight.Normal,color=if(active) Ink else Muted)
                        Text(clock(sample.startUtc,sample.zone),fontSize=11.sp,color=Muted)
                        Surface(Modifier.padding(top=4.dp).size(4.dp),shape=CircleShape,color=if(active) Ink else Color.Transparent) {}
                    }
                }
            }
        }
        }
        HorizontalDivider(Modifier.padding(top=6.dp),color=Line.copy(alpha=.65f))
        Column(Modifier.fillMaxWidth().testTag("stats-selected-ride").semantics(mergeDescendants=true) {},verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Text(date(picked.startUtc,picked.zone)+" · "+clock(picked.startUtc,picked.zone),Modifier.weight(1f).padding(end=8.dp),fontSize=12.sp,color=Muted)
                Text(duration(picked.metrics.elapsed),fontSize=18.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium)
            }
            TimeBar(picked.metrics)
        }
    }
}
@Composable private fun ClockTile(label: String,values: Triple<Int,Int,Int>) {
    DataCard(padding=14) {
        Text(label,fontSize=12.sp,color=Muted)
        Text(clockMinutes(values.first),fontSize=24.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium,lineHeight=30.sp)
        HorizontalDivider(color=Line.copy(alpha=.65f))
        Text(stringResource(R.string.earliest)+"  "+clockMinutes(values.second),fontSize=11.sp,color=Muted)
        Text(stringResource(R.string.latest)+"  "+clockMinutes(values.third),fontSize=11.sp,color=Muted)
    }
}
@Composable private fun DurationSummary(label: String,values: List<Long>) {
    Text(label,fontSize=14.sp,fontWeight=FontWeight.Medium)
    val entries=listOf(R.string.average to values.average().toLong(),R.string.shortest to values.min(),R.string.longest to values.max())
    if(LocalDensity.current.fontScale>1.3f) {
        entries.forEach { (name,value) ->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(stringResource(name),Modifier.weight(1f),fontSize=12.sp,color=Muted)
                Text(duration(value),fontSize=16.sp,fontFamily=FontFamily.Monospace)
            }
        }
    } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        entries.forEach { (name,value) ->
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(stringResource(name),fontSize=11.sp,color=Muted)
                Text(duration(value),fontSize=15.sp,fontFamily=FontFamily.Monospace,maxLines=1)
            }
        }
    }
}

@Composable fun TimeComposition(total: Long,moving: Long,waiting: Long,unknown: Long) {
    val share=waiting*100.0/total.coerceAtLeast(1)
    if(LocalDensity.current.fontScale>1.3f) {
        TimeRing(total,moving,waiting,unknown,share,Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally))
        TimeLegend(moving,waiting,unknown)
    } else Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
        TimeRing(total,moving,waiting,unknown,share)
        TimeLegend(moving,waiting,unknown,Modifier.weight(1f))
    }
}
