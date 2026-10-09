package com.rocky.remotepad

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

/** Draws a bitmap to fill the whole screen (center-crop, keeps proportions) with a light dark overlay. */
class CoverBackground(private val bmp: Bitmap) : Drawable() {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dim = Paint().apply { color = 0x66000000 }
    private val matrix = Matrix()

    override fun draw(canvas: Canvas) {
        val b: Rect = bounds
        if (b.isEmpty) return
        val scale = maxOf(b.width().toFloat() / bmp.width, b.height().toFloat() / bmp.height)
        val dx = (b.width() - bmp.width * scale) / 2f
        val dy = (b.height() - bmp.height * scale) / 2f
        matrix.reset()
        matrix.setScale(scale, scale)
        matrix.postTranslate(b.left + dx, b.top + dy)
        canvas.drawBitmap(bmp, matrix, paint)
        canvas.drawRect(b, dim)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity(): Int = PixelFormat.OPAQUE
}
