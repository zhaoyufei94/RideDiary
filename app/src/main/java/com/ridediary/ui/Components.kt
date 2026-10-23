package com.ridediary.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.ridediary.R
import com.ridediary.core.*
import java.time.*
import java.time.format.DateTimeFormatter
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
@Composable fun directionName(direction: String)=stringResource(when(direction){"WORK"->R.string.work;"HOME"->R.string.home;else->R.string.other})
@Composable fun routeName(name: String)=name.ifBlank { stringResource(R.string.route_unnamed) }
@Composable fun SectionTitle(text: String) { Text(text,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=12.dp,bottom=6.dp)) }
@Composable fun Note(text: String) { Text(text,color=Muted,fontSize=14.sp,lineHeight=21.sp) }
@Composable fun Panel(modifier: Modifier=Modifier,color: Color=Surface,radius: Dp=16.dp,padding: Dp=16.dp,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(radius),color=color,border=if(color==Surface) BorderStroke(1.dp,Line) else null) {
        Column(Modifier.padding(padding),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
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
        Panel(modifier=modifier.combinedClickable(onClick=onClick,onLongClickLabel=onLongClickLabel,onLongClick=showMenu),color=containerColor,radius=cornerRadius,padding=contentPadding,content=content)
        if(expanded && canManage) ActionSheet(title=title,subtitle=subtitle,eyebrow=onLongClickLabel,onDismiss={ expanded=false }) {
            menu { expanded=false }
        }
    }
}
@Composable fun Metric(label: String,value: String,modifier: Modifier=Modifier) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(label,fontSize=14.sp,color=Muted)
        Text(value,fontSize=22.sp,fontFamily=FontFamily.Monospace,color=Ink)
    }
}
@Composable fun MetricPair(a: String,av: String,b: String,bv: String) {
    Panel {
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
    Column(modifier.semantics(mergeDescendants=true){ contentDescription=label },verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(12.dp)) {
            var x=0f
            listOf(m.moving to Accent,m.waiting to Wait,m.unknown to Line).forEach { (time,color) ->
                val width=size.width*(time.toDouble()/m.elapsed.coerceAtLeast(1)).toFloat()
                drawRect(color,Offset(x,0f),androidx.compose.ui.geometry.Size(width,size.height)); x+=width
            }
        }
        Text(label,fontSize=14.sp,color=Muted)
    }
}
@Composable fun MotorcycleProgress(progress: Double) {
    val p=progress.coerceIn(0.0,1.0).toFloat()
    Canvas(Modifier.fillMaxWidth().height(58.dp).semantics { progressBarRangeInfo=ProgressBarRangeInfo(p,0f..1f) }) {
        val margin=24.dp.toPx(); val y=size.height-10.dp.toPx(); val width=(size.width-2*margin).coerceAtLeast(0f)
        drawLine(Color(0xff50654d),Offset(margin,y),Offset(margin+width,y),8.dp.toPx(),StrokeCap.Round)
        drawLine(Accent,Offset(margin,y),Offset(margin+width*p,y),8.dp.toPx(),StrokeCap.Round)
        val scale=1.dp.toPx(); val x=margin+width*p-21*scale; val top=y-33*scale
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
@Composable fun Trace(points: List<Fix>) {
    val valid=points.filter(::good)
    val label=stringResource(if(valid.size<2) R.string.no_trace else R.string.route_sketch)
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(160.dp).background(Soft,RoundedCornerShape(12.dp)).semantics { contentDescription=label }) {
            if(valid.size<2) return@Canvas
            val cosLat=cos(Math.toRadians(valid.first().lat))
            val xs=valid.map { it.lon*cosLat }; val ys=valid.map { it.lat }
            val minX=xs.min(); val minY=ys.min(); val rangeX=(xs.max()-minX).coerceAtLeast(0.00001); val rangeY=(ys.max()-minY).coerceAtLeast(0.00001)
            val padding=20.dp.toPx(); val scale=min((size.width-padding*2)/rangeX,(size.height-padding*2)/rangeY)
            fun position(f: Fix)=Offset(((f.lon*cosLat-minX)*scale+(size.width-rangeX*scale)/2).toFloat(),(size.height-(f.lat-minY)*scale-(size.height-rangeY*scale)/2).toFloat())
            valid.zipWithNext().forEach { (a,b) ->
                if(b.t-a.t in 1..Policy.MAX_GAP_MS && meters(a,b)/(b.t-a.t)*1000<=Policy.MAX_SPEED)
                    drawLine(Ink,position(a),position(b),3.dp.toPx(),StrokeCap.Round)
            }
            drawCircle(Surface,6.dp.toPx(),position(valid.first())); drawCircle(Ink,6.dp.toPx(),position(valid.first()),style=Stroke(2.dp.toPx()))
            drawCircle(Ink,6.dp.toPx(),position(valid.last()))
        }
        Note(label)
    }
}

@Composable fun DataCard(modifier: Modifier=Modifier,padding: Int=20,content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=Surface,border=BorderStroke(1.dp,Line.copy(alpha=.6f))) {
        Column(Modifier.padding(padding.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable fun SectionHeading(title: String,subtitle: String?=null) {
    Column(Modifier.padding(top=10.dp,bottom=2.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(title,fontSize=19.sp,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold)
        if(!subtitle.isNullOrBlank()) Text(subtitle,fontSize=13.sp,color=Muted,lineHeight=20.sp)
    }
}
