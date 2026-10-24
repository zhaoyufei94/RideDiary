package com.ridediary.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridediary.R

enum class Glyph { EDIT, DELETE, ADJUST, LEFT, RIGHT, MORE, DOWN, CHART, CLOCK, ROUTE, CALENDAR, SETTINGS, UNLINK, PLAY, REFRESH, NAV_RECORD, NAV_TRIPS }

/** Small local line icons keep the app independent of downloaded artwork. */
@Composable fun AppGlyph(kind: Glyph,modifier: Modifier=Modifier.size(22.dp),color: Color=Ink) {
    Canvas(modifier) {
        scale(size.width/24f,size.height/24f,Offset.Zero) {
            val stroke=Stroke(1.6f,cap=androidx.compose.ui.graphics.StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round)
            fun line(ax: Float,ay: Float,bx: Float,by: Float)=drawLine(color,Offset(ax,ay),Offset(bx,by),1.6f,androidx.compose.ui.graphics.StrokeCap.Round)
            val path=Path()
            when(kind) {
                Glyph.PLAY -> { path.moveTo(8f,5f); path.lineTo(19f,12f); path.lineTo(8f,19f); path.close(); drawPath(path,color) }
                Glyph.REFRESH -> { drawArc(color,40f,285f,false,Offset(4f,4f),androidx.compose.ui.geometry.Size(16f,16f),style=stroke); line(20f,4f,20f,10f); line(14f,10f,20f,10f) }
                Glyph.SETTINGS -> {
                    val teeth=8
                    val sector=2.0*Math.PI/teeth
                    val baseHalf=0.22
                    val tipHalf=0.13
                    fun point(radius: Double,angle: Double)=Offset(
                        (12+radius*kotlin.math.cos(angle)).toFloat(),
                        (12+radius*kotlin.math.sin(angle)).toFloat()
                    )
                    val gear=Path()
                    for(i in 0 until teeth) {
                        val center=i*sector
                        val rootStart=point(6.6,center-baseHalf)
                        val tipStart=point(9.2,center-tipHalf)
                        val tipEnd=point(9.2,center+tipHalf)
                        val rootEnd=point(6.6,center+baseHalf)
                        val nextRoot=point(6.6,center+sector-baseHalf)
                        if(i==0) gear.moveTo(rootStart.x,rootStart.y) else gear.lineTo(rootStart.x,rootStart.y)
                        gear.lineTo(tipStart.x,tipStart.y)
                        gear.lineTo(tipEnd.x,tipEnd.y)
                        gear.lineTo(rootEnd.x,rootEnd.y)
                        gear.lineTo(nextRoot.x,nextRoot.y)
                    }
                    gear.close()
                    drawPath(gear,color,style=stroke)
                    drawCircle(color,2.7f,Offset(12f,12f),style=stroke)
                }
                Glyph.UNLINK -> {
                    path.moveTo(8f,15f); path.lineTo(5f,18f); path.cubicTo(1f,22f,5f,24f,9f,20f)
                    path.lineTo(11f,17f); drawPath(path,color,style=stroke)
                    path.reset(); path.moveTo(13f,7f); path.lineTo(16f,4f); path.cubicTo(20f,0f,24f,4f,20f,8f)
                    path.lineTo(17f,11f); drawPath(path,color,style=stroke)
                    line(4f,4f,20f,20f)
                }
                Glyph.EDIT -> { path.moveTo(5f,15f); path.lineTo(15f,5f); path.lineTo(19f,9f); path.lineTo(9f,19f); path.lineTo(4f,20f); path.close(); drawPath(path,color,style=stroke); line(13f,7f,17f,11f) }
                Glyph.DELETE -> { line(4f,7f,20f,7f); path.moveTo(7f,7f); path.lineTo(8f,20f); path.lineTo(16f,20f); path.lineTo(17f,7f); drawPath(path,color,style=stroke); line(9f,4f,15f,4f); line(10f,10f,10f,17f); line(14f,10f,14f,17f) }
                Glyph.ADJUST -> { listOf(6f to 9f,12f to 16f,18f to 7f).forEach { (x,y) -> line(x,4f,x,y-2); line(x,y+2,x,20f); drawCircle(color,2f,Offset(x,y),style=stroke) } }
                Glyph.LEFT -> { path.moveTo(14f,6f); path.lineTo(8f,12f); path.lineTo(14f,18f); drawPath(path,color,style=stroke) }
                Glyph.RIGHT -> { path.moveTo(10f,6f); path.lineTo(16f,12f); path.lineTo(10f,18f); drawPath(path,color,style=stroke) }
                Glyph.MORE -> { drawCircle(color,1.6f,Offset(5f,12f)); drawCircle(color,1.6f,Offset(12f,12f)); drawCircle(color,1.6f,Offset(19f,12f)) }
                Glyph.DOWN -> { path.moveTo(6f,9f); path.lineTo(12f,15f); path.lineTo(18f,9f); drawPath(path,color,style=stroke) }
                Glyph.CHART -> { line(5f,20f,5f,13f); line(12f,20f,12f,5f); line(19f,20f,19f,9f) }
                Glyph.NAV_RECORD -> { drawCircle(color,8f,Offset(12f,12f),style=stroke); drawCircle(color,4f,Offset(12f,12f)) }
                Glyph.NAV_TRIPS -> { drawRoundRect(color,Offset(4f,4f),androidx.compose.ui.geometry.Size(16f,16f),androidx.compose.ui.geometry.CornerRadius(4f),style=stroke); line(8f,9f,16f,9f); line(8f,13f,14f,13f); line(8f,17f,12f,17f) }
                Glyph.CLOCK -> { drawCircle(color,8f,Offset(12f,12f),style=stroke); line(12f,7f,12f,12f); line(12f,12f,16f,14f) }
                Glyph.ROUTE -> { drawCircle(color,2.5f,Offset(6f,18f),style=stroke); drawCircle(color,2.5f,Offset(18f,6f),style=stroke); path.moveTo(6f,15f); path.cubicTo(4f,4f,20f,20f,18f,9f); drawPath(path,color,style=stroke) }
                Glyph.CALENDAR -> { drawRoundRect(color,Offset(4f,6f),androidx.compose.ui.geometry.Size(16f,14f),androidx.compose.ui.geometry.CornerRadius(3f),style=stroke); line(4f,11f,20f,11f); line(8f,4f,8f,8f); line(16f,4f,16f,8f) }
            }
        }
    }
}

@Composable private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(top=10.dp,bottom=14.dp),contentAlignment=Alignment.Center) {
        Surface(Modifier.size(34.dp,4.dp),shape=CircleShape,color=Line) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ActionSheet(title: String,subtitle: String?,eyebrow: String,onDismiss: ()->Unit,content: @Composable ColumnScope.()->Unit) {
    val parentContext=LocalContext.current; val parentDensity=LocalDensity.current; val parentConfiguration=LocalConfiguration.current
    ModalBottomSheet(modifier=Modifier.testTag("action-sheet"),onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),containerColor=Surface,tonalElevation=0.dp,dragHandle={ SheetHandle() }) {
        CompositionLocalProvider(LocalContext provides parentContext,LocalDensity provides parentDensity,LocalConfiguration provides parentConfiguration) {
        Column(Modifier.fillMaxWidth().testTag("management-sheet").verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(eyebrow,fontSize=11.sp,color=Muted,letterSpacing=.8.sp)
            Text(title,fontSize=22.sp,fontWeight=FontWeight.SemiBold,color=Ink)
            if(!subtitle.isNullOrBlank()) Text(subtitle,fontSize=13.sp,color=Muted,lineHeight=19.sp)
            Spacer(Modifier.height(4.dp))
            content()
            TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) { Text(stringResource(R.string.cancel),color=Muted) }
        }
        }
    }
}

@Composable fun SheetAction(title: String,subtitle: String?=null,glyph: Glyph,onClick: ()->Unit,enabled: Boolean=true,destructive: Boolean=false) {
    val ink=if(!enabled) Muted.copy(alpha=.5f) else if(destructive) Danger else Ink
    Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(14.dp),color=if(destructive) DangerSoft else Soft.copy(alpha=.48f)) {
        Row(Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Surface(shape=CircleShape,color=Surface.copy(alpha=.8f)) { AppGlyph(glyph,Modifier.padding(9.dp).size(20.dp),ink) }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                Text(title,fontSize=15.sp,fontWeight=FontWeight.Medium,color=ink)
                if(!subtitle.isNullOrBlank()) Text(subtitle,fontSize=12.sp,lineHeight=17.sp,color=if(enabled) Muted else Muted.copy(alpha=.5f))
            }
            AppGlyph(Glyph.RIGHT,Modifier.size(17.dp),ink.copy(alpha=.55f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun EditorSheet(title: String,subtitle: String?=null,onDismiss: ()->Unit,onSave: ()->Unit,saveEnabled: Boolean=true,content: @Composable ColumnScope.()->Unit) {
    val parentContext=LocalContext.current; val parentDensity=LocalDensity.current; val parentConfiguration=LocalConfiguration.current
    val windowHeight=LocalWindowInfo.current.containerSize.height
    val maximum=with(LocalDensity.current) { windowHeight.toDp()*.84f }
    ModalBottomSheet(modifier=Modifier.testTag("editor-sheet"),onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),containerColor=Surface,tonalElevation=0.dp,dragHandle={ SheetHandle() }) {
        CompositionLocalProvider(LocalContext provides parentContext,LocalDensity provides parentDensity,LocalConfiguration provides parentConfiguration) {
        Column(Modifier.fillMaxWidth().heightIn(max=maximum).imePadding().padding(horizontal=20.dp)) {
            Text(title,fontSize=22.sp,fontWeight=FontWeight.SemiBold,color=Ink)
            if(!subtitle.isNullOrBlank()) Text(subtitle,Modifier.padding(top=6.dp,bottom=16.dp),fontSize=13.sp,lineHeight=19.sp,color=Muted)
            else Spacer(Modifier.height(16.dp))
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(bottom=14.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content)
            HorizontalDivider(color=Line.copy(alpha=.65f))
            Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick=onDismiss,modifier=Modifier.weight(1f).heightIn(min=50.dp),shape=RoundedCornerShape(14.dp)) { Text(stringResource(R.string.cancel)) }
                Button(onClick=onSave,enabled=saveEnabled,modifier=Modifier.weight(1f).heightIn(min=50.dp),shape=RoundedCornerShape(14.dp)) { Text(stringResource(R.string.save)) }
            }
        }
        }
    }
}
