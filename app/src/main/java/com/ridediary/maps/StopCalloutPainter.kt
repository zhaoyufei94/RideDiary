package com.ridediary.maps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

internal data class StopCalloutImage(val bitmap: Bitmap,val anchorX: Float,val anchorY: Float)

/** Shared bubble, leader and location dot for Canvas tracks and native bitmap markers. */
internal class StopCalloutPainter(private val density: Float,fontScale: Float) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize=11*density*fontScale }
    private val ink=0xff315d49.toInt()
    val height=max(22*density,paint.fontSpacing+8*density)
    fun width(text: String)=ceil((paint.measureText(text)+16*density).toDouble()).toFloat()

    fun draw(canvas: Canvas,placement: StopCalloutPlacement,text: String) {
        val body=placement.body
        val rect=RectF(body.left,body.top,body.right,body.bottom)
        val point=placement.point
        // Attach to the facing edge, inset from its rounded corners.
        val inset=6*density
        val attachment=when {
            point.x<body.left->ScreenPoint(body.left,point.y.coerceIn(body.top+inset,body.bottom-inset))
            point.x>body.right->ScreenPoint(body.right,point.y.coerceIn(body.top+inset,body.bottom-inset))
            point.y<body.top->ScreenPoint(point.x.coerceIn(body.left+inset,body.right-inset),body.top)
            else->ScreenPoint(point.x.coerceIn(body.left+inset,body.right-inset),body.bottom)
        }
        paint.style=Paint.Style.STROKE; paint.strokeWidth=density; paint.color=ink
        canvas.drawLine(attachment.x,attachment.y,point.x,point.y,paint)
        paint.style=Paint.Style.FILL; paint.color=0xfffaf7ef.toInt()
        canvas.drawRoundRect(rect,6*density,6*density,paint)
        paint.style=Paint.Style.STROKE; paint.color=0xffaab7ab.toInt()
        val outline=RectF(rect).apply { inset(density/2,density/2) }
        canvas.drawRoundRect(outline,6*density,6*density,paint)
        paint.style=Paint.Style.FILL; paint.color=0xff233d30.toInt()
        canvas.drawText(text,rect.left+8*density,rect.centerY()-(paint.ascent()+paint.descent())/2,paint)
    }
    fun drawPoint(canvas: Canvas,x: Float,y: Float) {
        paint.style=Paint.Style.FILL; paint.color=Color.WHITE
        canvas.drawCircle(x,y,4*density,paint)
        paint.color=ink; canvas.drawCircle(x,y,2.5f*density,paint)
    }
    fun bitmap(text: String,placement: StopCalloutPlacement): StopCalloutImage {
        // The bitmap includes both the bubble and its leader. Its geographic anchor
        // remains the stop, even when the bubble is beside or below that location.
        val left=min(placement.body.left,placement.point.x)-density
        val top=min(placement.body.top,placement.point.y)-density
        val right=max(placement.body.right,placement.point.x)+density
        val bottom=max(placement.body.bottom,placement.point.y)+density
        val bitmap=createBitmap(ceil((right-left).toDouble()).toInt().coerceAtLeast(1),ceil((bottom-top).toDouble()).toInt().coerceAtLeast(1))
        val local=placement.translated(-left,-top)
        draw(Canvas(bitmap),local,text)
        return StopCalloutImage(bitmap,local.point.x/bitmap.width,local.point.y/bitmap.height)
    }
    fun pointBitmap(): Bitmap {
        val size=ceil((10*density).toDouble()).toInt().coerceAtLeast(10)
        return createBitmap(size,size).also { drawPoint(Canvas(it),size/2f,size/2f) }
    }
}
