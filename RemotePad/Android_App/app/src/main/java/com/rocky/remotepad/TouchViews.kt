package com.rocky.remotepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs

private fun Context.dpf(v: Int) = v * resources.displayMetrics.density

/**
 * Laptop-style touchpad.
 *  - drag with 1 finger  = move cursor (hover)
 *  - tap                 = left click
 *  - tap with 2 fingers  = right click
 *  - drag with 2 fingers = scroll
 */
class TouchpadView(
    ctx: Context,
    private val client: UdpClient,
    private val sens: () -> Float
) : View(ctx) {

    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop.toFloat()
    private var lastX = 0f

    // Movements are collected and sent at a steady rate (about 60 per second) for smoother cursor
    private var pendX = 0
    private var pendY = 0
    private var flushScheduled = false
    private val flushHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val flushRunnable = Runnable { flushMove() }

    private fun queueMove(ix: Int, iy: Int) {
        pendX += ix
        pendY += iy
        if (!flushScheduled) {
            flushScheduled = true
            flushHandler.postDelayed(flushRunnable, 16)
        }
    }

    private fun flushMove() {
        flushScheduled = false
        if (pendX != 0 || pendY != 0) {
            client.send("MV|$pendX|$pendY")
            pendX = 0
            pendY = 0
        }
    }
    private var lastY = 0f
    private var downTime = 0L
    private var travel = 0f
    private var maxPointers = 0
    private var accX = 0f
    private var accY = 0f
    private var accS = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A97A8.toInt()
        textSize = ctx.dpf(14)
        textAlign = Paint.Align.CENTER
    }

    init {
        background = GradientDrawable().apply {
            cornerRadius = ctx.dpf(16)
            setColor(0xA6141A22.toInt())
            setStroke(ctx.dpf(2).toInt(), 0xFFFF8A3D.toInt())
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val lines = listOf(
            "TOUCHPAD",
            "drag = move cursor",
            "tap = left click",
            "2-finger tap = right click",
            "2-finger drag = scroll"
        )
        val y0 = height / 2f - lines.size * paint.textSize * 0.75f
        lines.forEachIndexed { i, t ->
            canvas.drawText(t, width / 2f, y0 + i * paint.textSize * 1.5f, paint)
        }
    }

    private fun centerOf(e: MotionEvent, skip: Int): Pair<Float, Float> {
        var sx = 0f
        var sy = 0f
        var n = 0
        for (i in 0 until e.pointerCount) {
            if (i == skip) continue
            sx += e.getX(i)
            sy += e.getY(i)
            n++
        }
        return if (n == 0) Pair(lastX, lastY) else Pair(sx / n, sy / n)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val c = centerOf(e, -1)
                lastX = c.first
                lastY = c.second
                downTime = e.eventTime
                travel = 0f
                maxPointers = 1
                accX = 0f
                accY = 0f
                accS = 0f
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = maxOf(maxPointers, e.pointerCount)
                val c = centerOf(e, -1)
                lastX = c.first
                lastY = c.second
            }
            MotionEvent.ACTION_MOVE -> {
                val c = centerOf(e, -1)
                val dx = c.first - lastX
                val dy = c.second - lastY
                lastX = c.first
                lastY = c.second
                travel += abs(dx) + abs(dy)
                if (e.pointerCount >= 2) {
                    accS += -dy * 4f
                    val n = accS.toInt()
                    if (n != 0) {
                        client.send("SCR|$n")
                        accS -= n
                    }
                } else {
                    accX += dx * sens()
                    accY += dy * sens()
                    val ix = accX.toInt()
                    val iy = accY.toInt()
                    if (ix != 0 || iy != 0) {
                        queueMove(ix, iy)
                        accX -= ix
                        accY -= iy
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val c = centerOf(e, e.actionIndex)
                lastX = c.first
                lastY = c.second
            }
            MotionEvent.ACTION_UP -> {
                if (travel < slop * 3f && e.eventTime - downTime < 250) {
                    when (maxPointers) {
                        1 -> client.send("BTN|L|click")
                        2 -> client.send("BTN|R|click")
                    }
                }
            }
        }
        return true
    }
}

private const val VK_VOLUME_UP = 0xAF
private const val VK_VOLUME_DOWN = 0xAE

/** Vertical strip on the left side of the mouse screen: slide up = PC volume up, down = volume down. */
class VolumeStrip(ctx: Context, private val client: UdpClient) : View(ctx) {
    private var lastY = 0f
    private var acc = 0f
    private val stepPx = ctx.dpf(14)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A97A8.toInt()
        textSize = ctx.dpf(14)
        textAlign = Paint.Align.CENTER
    }

    init {
        background = GradientDrawable().apply {
            cornerRadius = ctx.dpf(16)
            setColor(0xA6141A22.toInt())
            setStroke(ctx.dpf(2).toInt(), 0xFF2A3340.toInt())
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.rotate(-90f, width / 2f, height / 2f)
        canvas.drawText("▲  VOLUME  ▼", width / 2f, height / 2f + paint.textSize / 3f, paint)
        canvas.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastY = e.y
                acc = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = e.y - lastY
                lastY = e.y
                acc += -dy
                while (acc >= stepPx) {
                    client.send("KEY|$VK_VOLUME_UP|tap")
                    acc -= stepPx
                }
                while (acc <= -stepPx) {
                    client.send("KEY|$VK_VOLUME_DOWN|tap")
                    acc += stepPx
                }
            }
        }
        return true
    }
}

/** Vertical strip on the right side: slide finger up/down to scroll. */
class ScrollStrip(ctx: Context, private val client: UdpClient) : View(ctx) {
    private var lastY = 0f
    private var acc = 0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A97A8.toInt()
        textSize = ctx.dpf(14)
        textAlign = Paint.Align.CENTER
    }

    init {
        background = GradientDrawable().apply {
            cornerRadius = ctx.dpf(16)
            setColor(0xA6141A22.toInt())
            setStroke(ctx.dpf(2).toInt(), 0xFF2A3340.toInt())
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.rotate(-90f, width / 2f, height / 2f)
        canvas.drawText("▲  SCROLL  ▼", width / 2f, height / 2f + paint.textSize / 3f, paint)
        canvas.restore()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastY = e.y
                acc = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = e.y - lastY
                lastY = e.y
                acc += -dy * 5f
                val n = acc.toInt()
                if (n != 0) {
                    client.send("SCR|$n")
                    acc -= n
                }
            }
        }
        return true
    }
}
