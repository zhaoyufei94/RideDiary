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

enum class Glyph { EDIT, DELETE, ADJUST, LEFT, RIGHT, CHART, CLOCK, ROUTE, CALENDAR, SETTINGS, UNLINK }

/** Small local line icons keep the app independent of downloaded artwork. */
@Composable fun AppGlyph(kind: Glyph,modifier: Modifier=Modifier.size(22.dp),color: Color=Ink) {
    Canvas(modifier) {
        scale(size.width/24f,size.height/24f,Offset.Zero) {
            val stroke=Stroke(1.6f,cap=androidx.compose.ui.graphics.StrokeCap.Round,join=androidx.compose.ui.graphics.StrokeJoin.Round)
            fun line(ax: Float,ay: Float,bx: Float,by: Float)=drawLine(color,Offset(ax,ay),Offset(bx,by),1.6f,androidx.compose.ui.graphics.StrokeCap.Round)
            val path=Path()
            when(kind) {
                Glyph.SETTINGS -> {
                    drawCircle(color,7f,Offset(12f,12f),style=stroke)
                    drawCircle(color,2.8f,Offset(12f,12f),style=stroke)
                    repeat(8) { i ->
                        val angle=i*Math.PI/4
                        val x=kotlin.math.cos(angle).toFloat(); val y=kotlin.math.sin(angle).toFloat()
                        line(12f+7*x,12f+7*y,12f+10*x,12f+10*y)
                    }
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
                Glyph.CHART -> { line(5f,20f,5f,13f); line(12f,20f,12f,5f); line(19f,20f,19f,9f) }
                Glyph.CLOCK -> { drawCircle(color,8f,Offset(12f,12f),style=stroke); line(12f,7f,12f,12f); line(12f,12f,16f,14f) }
                Glyph.ROUTE -> { drawCircle(color,2.5f,Offset(6f,18f),style=stroke); drawCircle(color,2.5f,Offset(18f,6f),style=stroke); path.moveTo(6f,15f); path.cubicTo(4f,4f,20f,20f,18f,9f); drawPath(path,color,style=stroke) }
                Glyph.CALENDAR -> { drawRoundRect(color,Offset(4f,6f),androidx.compose.ui.geometry.Size(16f,14f),androidx.compose.ui.geometry.CornerRadius(3f),style=stroke); line(4f,11f,20f,11f); line(8f,4f,8f,8f); line(16f,4f,16f,8f) }
            }
        }
    }
}

@Composable private fun SheetHandle() {
    Box(Modifier.fillMaxWidth().padding(top=12.dp,bottom=20.dp),contentAlignment=Alignment.Center) {
        Surface(Modifier.size(36.dp,4.dp),shape=CircleShape,color=Line) {}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ActionSheet(title: String,subtitle: String?,eyebrow: String,onDismiss: ()->Unit,content: @Composable ColumnScope.()->Unit) {
    val parentContext=LocalContext.current; val parentDensity=LocalDensity.current; val parentConfiguration=LocalConfiguration.current
    ModalBottomSheet(modifier=Modifier.testTag("action-sheet"),onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp),containerColor=Surface,tonalElevation=0.dp,dragHandle={ SheetHandle() }) {
        CompositionLocalProvider(LocalContext provides parentContext,LocalDensity provides parentDensity,LocalConfiguration provides parentConfiguration) {
        Column(Modifier.fillMaxWidth().testTag("management-sheet").verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(eyebrow,fontSize=12.sp,color=Muted,letterSpacing=1.sp)
            Text(title,fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=Ink)
            if(!subtitle.isNullOrBlank()) Text(subtitle,fontSize=14.sp,color=Muted,lineHeight=21.sp)
            Spacer(Modifier.height(8.dp))
            content()
            TextButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) { Text(stringResource(R.string.cancel),color=Muted) }
        }
        }
    }
}

@Composable fun SheetAction(title: String,subtitle: String?=null,glyph: Glyph,onClick: ()->Unit,enabled: Boolean=true,destructive: Boolean=false) {
    val ink=if(!enabled) Muted.copy(alpha=.5f) else if(destructive) Danger else Ink
    Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(18.dp),color=if(destructive) DangerSoft else Soft.copy(alpha=.48f)) {
        Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            Surface(shape=CircleShape,color=Surface.copy(alpha=.8f)) { AppGlyph(glyph,Modifier.padding(10.dp).size(22.dp),ink) }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,fontSize=16.sp,fontWeight=FontWeight.Medium,color=ink)
                if(!subtitle.isNullOrBlank()) Text(subtitle,fontSize=13.sp,lineHeight=19.sp,color=if(enabled) Muted else Muted.copy(alpha=.5f))
            }
            AppGlyph(Glyph.RIGHT,Modifier.size(18.dp),ink.copy(alpha=.55f))
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
        Column(Modifier.fillMaxWidth().heightIn(max=maximum).imePadding().padding(horizontal=24.dp)) {
            Text(title,fontSize=24.sp,fontWeight=FontWeight.SemiBold,color=Ink)
            if(!subtitle.isNullOrBlank()) Text(subtitle,Modifier.padding(top=8.dp,bottom=20.dp),fontSize=14.sp,lineHeight=21.sp,color=Muted)
            else Spacer(Modifier.height(20.dp))
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(bottom=16.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
            HorizontalDivider(color=Line.copy(alpha=.65f))
            Row(Modifier.fillMaxWidth().padding(vertical=14.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick=onDismiss,modifier=Modifier.weight(1f).heightIn(min=52.dp),shape=RoundedCornerShape(16.dp)) { Text(stringResource(R.string.cancel)) }
                Button(onClick=onSave,enabled=saveEnabled,modifier=Modifier.weight(1f).heightIn(min=52.dp),shape=RoundedCornerShape(16.dp)) { Text(stringResource(R.string.save)) }
            }
        }
        }
    }
}
