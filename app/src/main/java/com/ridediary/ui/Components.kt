package com.ridediary.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.ridediary.R
import com.ridediary.core.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.*

fun duration(ms: Long): String {
    val s=ms.coerceAtLeast(0)/1000
    return if(s>=3600) String.format(Locale.ROOT,"%d:%02d:%02d",s/3600,s/60%60,s%60) else String.format(Locale.ROOT,"%02d:%02d",s/60,s%60)
}
fun delta(ms: Long)=(if(ms<0) "−" else "+")+duration(abs(ms))
fun decimal(n: Double)=String.format(Locale.getDefault(),"%.1f",n)
fun clock(utc: Long,zone: String)=Instant.ofEpochMilli(utc).atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("HH:mm"))
fun date(utc: Long,zone: String)=Instant.ofEpochMilli(utc).atZone(ZoneId.of(zone)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
fun clockMinutes(m: Int)=String.format(Locale.ROOT,"%02d:%02d",m/60,m%60)
fun shortDate(utc: Long,zone: String)=Instant.ofEpochMilli(utc).atZone(ZoneId.of(zone))
    .format(DateTimeFormatter.ofPattern("MM/dd",Locale.ROOT))
fun weekdayText(utc: Long,zone: String,locale: Locale)=Instant.ofEpochMilli(utc).atZone(ZoneId.of(zone)).dayOfWeek
    .getDisplayName(TextStyle.SHORT,locale)
@Composable fun friendlyDate(utc: Long,zone: String=ZoneId.systemDefault().id): String {
    val locale=LocalConfiguration.current.locales[0]
    return Instant.ofEpochMilli(utc).atZone(ZoneId.of(zone))
        .format(DateTimeFormatter.ofPattern(if(locale.language=="zh") "M月d日 EEE" else "MMM d, EEE",locale))
}
@Composable fun friendlyDateTime(start: Long,end: Long?,zone: String): String {
    val locale=LocalConfiguration.current.locales[0]
    val day=Instant.ofEpochMilli(start).atZone(ZoneId.of(zone))
        .format(DateTimeFormatter.ofPattern(if(locale.language=="zh") "M月d日 EEE" else "MMM d, EEE",locale))
    return day+" · "+clock(start,zone)+(end?.let { "–"+clock(it,zone) } ?: "")
}
@Composable fun directionName(direction: String)=stringResource(when(direction){"WORK"->R.string.work;"HOME"->R.string.home;else->R.string.other})
@Composable fun routeName(name: String)=name.ifBlank { stringResource(R.string.route_unnamed) }

/** Compact page context: metadata first, no repeated tab name. */
@Composable fun PageHeader(modifier: Modifier=Modifier,overline: String?=null,title: String,subtitle: String?=null,
    leading: (@Composable ()->Unit)?=null,actions: @Composable RowScope.()->Unit={}) {
    Row(modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)) {
        leading?.invoke()
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(1.dp)) {
            if(overline!=null) Text(overline,fontSize=11.sp,letterSpacing=.4.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text(title,fontSize=21.sp,fontWeight=FontWeight.SemiBold,lineHeight=27.sp,color=Ink,maxLines=2,overflow=TextOverflow.Ellipsis)
            if(subtitle!=null) Text(subtitle,fontSize=12.sp,lineHeight=17.sp,color=Muted,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        actions()
    }
}
@Composable fun HeaderIconButton(glyph: Glyph,label: String,enabled: Boolean=true,tint: Color=Ink,onClick: ()->Unit) {
    IconButton(onClick,enabled=enabled,modifier=Modifier.semantics { contentDescription=label }) {
        AppGlyph(glyph,Modifier.size(22.dp),if(enabled) tint else Muted.copy(alpha=.4f))
    }
}
@Composable fun StatusPill(text: String,good: Boolean?=null,dark: Boolean=false,dotTag: String?=null) {
    Row(Modifier.clip(CircleShape).background(if(dark) Surface.copy(alpha=.14f) else Soft)
        .padding(horizontal=9.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        if(good!=null) Box(Modifier.size(7.dp)
            .then(if(dotTag!=null) Modifier.testTag(dotTag) else Modifier)
            .background(if(good) (if(dark) Accent else Color(0xff32945a)) else Danger,CircleShape))
        Text(text,fontSize=11.sp,lineHeight=14.sp,color=if(dark) Surface else Ink,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}
@Composable fun DirectionSwitch(direction: String,onSelect: (String)->Unit,modifier: Modifier=Modifier,tagPrefix: String?=null) {
    Surface(shape=RoundedCornerShape(12.dp),color=Soft.copy(alpha=.72f),modifier=modifier.height(48.dp)) {
        Row(Modifier.fillMaxSize().padding(3.dp),horizontalArrangement=Arrangement.spacedBy(3.dp)) {
            listOf("WORK","HOME").forEach { value ->
                val active=direction==value
                Surface(onClick={ onSelect(value) },shape=RoundedCornerShape(9.dp),color=if(active) Ink else Color.Transparent,
                    modifier=Modifier.weight(1f).fillMaxHeight()
                        .then(if(tagPrefix!=null) Modifier.testTag("$tagPrefix$value") else Modifier)
                        .semantics { selected=active }) {
                    Box(Modifier.fillMaxSize().padding(horizontal=4.dp),contentAlignment=Alignment.Center) {
                        Text(directionName(value),fontSize=12.sp,fontWeight=if(active) FontWeight.Medium else FontWeight.Normal,
                            color=if(active) Surface else Muted,maxLines=1)
                    }
                }
            }
        }
    }
}
@Composable fun SectionTitle(text: String) { Text(text,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=8.dp,bottom=2.dp)) }
@Composable fun Note(text: String) { Text(text,color=Muted,fontSize=13.sp,lineHeight=19.sp) }
@Composable fun Panel(modifier: Modifier=Modifier,color: Color=Surface,radius: Dp=14.dp,padding: Dp=14.dp,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(radius),color=color,border=if(color==Surface) BorderStroke(1.dp,Line) else null) {
        Column(Modifier.padding(padding),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun ContextMenuPanel(
    modifier: Modifier=Modifier,
    onClick: ()->Unit={},
    onLongClickLabel: String,
    title: String=onLongClickLabel,
    subtitle: String?=null,
    containerColor: Color=Surface,
    cornerRadius: Dp=16.dp,
    contentPadding: Dp=16.dp,
    canManage: Boolean=true,
    menu: @Composable ColumnScope.(dismiss: ()->Unit)->Unit,
    content: @Composable ColumnScope.()->Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics=LocalHapticFeedback.current
    val showMenu: (() -> Unit)?=if(canManage) ({
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        expanded=true
    }) else null
    Box(Modifier.fillMaxWidth()) {
        Panel(modifier=modifier.combinedClickable(onClick=onClick,onLongClickLabel=onLongClickLabel,onLongClick=showMenu),
            color=containerColor,radius=cornerRadius,padding=contentPadding,content=content)
        if(expanded && canManage) ActionSheet(title=title,subtitle=subtitle,eyebrow=onLongClickLabel,onDismiss={ expanded=false }) {
            menu { expanded=false }
        }
    }
}
@Composable fun Metric(label: String,value: String,modifier: Modifier=Modifier,valueColor: Color=Ink) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(label,fontSize=12.sp,color=Muted,maxLines=1)
        Text(value,fontSize=20.sp,fontFamily=FontFamily.Monospace,color=valueColor,fontWeight=FontWeight.Medium,maxLines=1)
    }
}
@Composable fun CompactMetric(label: String,value: String,modifier: Modifier=Modifier,valueColor: Color=Ink,labelColor: Color=Muted) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(label,fontSize=11.sp,lineHeight=15.sp,color=labelColor,maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(value,fontSize=18.sp,lineHeight=23.sp,fontFamily=FontFamily.Monospace,fontWeight=FontWeight.Medium,color=valueColor,maxLines=1)
    }
}
@Composable fun MetricPair(a: String,av: String,b: String,bv: String) {
    Panel(padding=12.dp) {
        if(LocalDensity.current.fontScale>1.35f) {
            Metric(a,av); Metric(b,bv)
        } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Metric(a,av,Modifier.weight(1f)); Metric(b,bv,Modifier.weight(1f))
        }
    }
}
@Composable fun LargeTime(ms: Long,preferred: Float=50f,color: Color=Color.Unspecified) {
    val text=duration(ms); val scale=LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val fitted=min(preferred,maxWidth.value/(text.length*0.62f)/scale).coerceAtLeast(20f)
        Text(text,fontSize=fitted.sp,fontFamily=FontFamily.Monospace,color=color,maxLines=1)
    }
}
@Composable fun TimeBar(m: Metrics,modifier: Modifier=Modifier) {
    val label="${stringResource(R.string.moving)} ${duration(m.moving)}, ${stringResource(R.string.waiting)} ${duration(m.waiting)}, ${stringResource(R.string.unknown)} ${duration(m.unknown)}"
    Column(modifier.semantics(mergeDescendants=true){ contentDescription=label },verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp))) {
            var x=0f
            listOf(m.moving to Accent,m.waiting to Wait,m.unknown to Line).forEach { (time,color) ->
                val width=size.width*(time.toDouble()/m.elapsed.coerceAtLeast(1)).toFloat()
                drawRect(color,Offset(x,0f),androidx.compose.ui.geometry.Size(width,size.height)); x+=width
            }
        }
        Text(label,fontSize=12.sp,lineHeight=17.sp,color=Muted)
    }
}
@Composable fun MotorcycleProgress(progress: Double) {
    val target=progress.coerceIn(0.0,1.0).toFloat()
    val animated by animateFloatAsState(target,tween(650,easing=FastOutSlowInEasing),label="route-progress")
    Canvas(Modifier.fillMaxWidth().height(46.dp).semantics { progressBarRangeInfo=ProgressBarRangeInfo(target,0f..1f) }) {
        val margin=22.dp.toPx(); val y=size.height-9.dp.toPx(); val width=(size.width-2*margin).coerceAtLeast(0f)
        drawLine(Color(0xff50654d),Offset(margin,y),Offset(margin+width,y),7.dp.toPx(),StrokeCap.Round)
        drawLine(Accent,Offset(margin,y),Offset(margin+width*animated,y),7.dp.toPx(),StrokeCap.Round)
        val scale=0.92.dp.toPx(); val x=margin+width*animated-21*scale; val top=y-31*scale
        fun xy(a: Float,b: Float)=Offset(x+a*scale,top+b*scale)
        val style=Stroke(2.2f*scale,cap=StrokeCap.Round)
        drawCircle(Accent,6*scale,xy(9f,23f),style=style); drawCircle(Accent,6*scale,xy(33f,23f),style=style)
        val path=Path().apply {
            moveTo(x+9*scale,top+23*scale); lineTo(x+18*scale,top+11*scale); lineTo(x+25*scale,top+23*scale); close()
            moveTo(x+9*scale,top+23*scale); lineTo(x+15*scale,top+15*scale); lineTo(x+27*scale,top+15*scale); lineTo(x+33*scale,top+23*scale)
            moveTo(x+27*scale,top+6*scale); lineTo(x+31*scale,top+6*scale); lineTo(x+34*scale,top+16*scale)
            moveTo(x+18*scale,top+11*scale); lineTo(x+15*scale,top+7*scale); lineTo(x+11*scale,top+7*scale)
            moveTo(x+21*scale,top+9*scale); lineTo(x+26*scale,top+14*scale); lineTo(x+31*scale,top+14*scale)
            moveTo(x+21*scale,top+9*scale); lineTo(x+16*scale,top+13*scale); lineTo(x+21*scale,top+19*scale)
        }
        drawPath(path,Accent,style=style); drawCircle(Accent,2.8f*scale,xy(23f,4f))
    }
}
@Composable fun DataCard(modifier: Modifier=Modifier,padding: Int=16,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=Surface,border=BorderStroke(1.dp,Line.copy(alpha=.65f))) {
        Column(Modifier.padding(padding.dp),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    }
}
@Composable fun SectionHeading(title: String,subtitle: String?=null) {
    Column(Modifier.padding(top=8.dp,bottom=2.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
        Text(title,fontSize=17.sp,fontWeight=FontWeight.SemiBold,lineHeight=22.sp)
        if(!subtitle.isNullOrBlank()) Text(subtitle,fontSize=12.sp,color=Muted,lineHeight=18.sp)
    }
}
