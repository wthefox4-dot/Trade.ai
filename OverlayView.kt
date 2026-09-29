package com.tradesignal.ai.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.tradesignal.ai.core.Signal

/**
 * Small draggable floating card. Shows only essential info and never intercepts
 * touches outside its own bounds, so it cannot block the trading app's controls.
 */
class OverlayWindow(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var titleView: TextView? = null
    private var signalView: TextView? = null
    private var subView: TextView? = null
    private var confView: TextView? = null
    private var timeView: TextView? = null
    private var params: WindowManager.LayoutParams? = null

    private val overlayType =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (root != null) return
        val density = context.resources.displayMetrics.density

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(Color.parseColor("#E6161616"))
                setStroke((1 * density).toInt(), Color.parseColor("#33FFFFFF"))
            }
        }
        titleView = TextView(context).apply {
            text = "TRADE SIGNAL  ·  LIVE ●"
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 10f
        }
        signalView = TextView(context).apply {
            text = "🔄 ANALYZING"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        subView = TextView(context).apply {
            text = "5 SEC MODE"
            setTextColor(Color.parseColor("#AAFFFFFF"))
            textSize = 11f
            gravity = Gravity.CENTER
        }
        confView = TextView(context).apply {
            text = ""
            setTextColor(Color.parseColor("#CCFFFFFF"))
            textSize = 11f
            gravity = Gravity.CENTER
        }
        timeView = TextView(context).apply {
            text = ""
            setTextColor(Color.parseColor("#77FFFFFF"))
            textSize = 9f
            gravity = Gravity.CENTER
        }
        layout.addView(titleView)
        layout.addView(signalView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = (4 * density).toInt() })
        layout.addView(subView)
        layout.addView(confView)
        layout.addView(timeView)
        root = layout

        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        p.gravity = Gravity.TOP or Gravity.START
        p.x = (16 * density).toInt()
        p.y = (120 * density).toInt()
        params = p

        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        layout.setOnTouchListener { _, ev ->
            val lp = params ?: return@setOnTouchListener false
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> { startX = lp.x; startY = lp.y; touchX = ev.rawX; touchY = ev.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (ev.rawX - touchX).toInt()
                    lp.y = startY + (ev.rawY - touchY).toInt()
                    wm.updateViewLayout(layout, lp)
                    true
                }
                else -> false
            }
        }
        wm.addView(layout, p)
    }

    fun update(signal: Signal, confidence: Int, horizonSec: Int, timeText: String, expired: Boolean) {
        val (label, color) = when {
            expired -> "⏳ EXPIRED" to "#888888"
            signal == Signal.UP -> "🟢 UP ↑" to "#37C871"
            signal == Signal.DOWN -> "🔴 DOWN ↓" to "#E8495B"
            else -> "⚪ WAIT ⏸" to "#CCCCCC"
        }
        signalView?.text = label
        signalView?.setTextColor(Color.parseColor(color))
        subView?.text = "$horizonSec SEC MODE"
        confView?.text = if (signal == Signal.WAIT || expired) "" else "Confidence $confidence%"
        timeView?.text = timeText
    }

    fun setAnalyzing() {
        signalView?.text = "🔄 ANALYZING"
        signalView?.setTextColor(Color.WHITE)
        confView?.text = ""
    }

    fun hide() {
        val v = root ?: return
        try { wm.removeView(v) } catch (_: Exception) {}
        root = null
    }

    fun isShowing() = root != null
}
