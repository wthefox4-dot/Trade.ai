package com.tradesignal.ai.capture

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import com.tradesignal.ai.core.Frame

/**
 * Wraps Android's official screen-capture API (MediaProjection).
 * Captures ONE frame per request (pull model) instead of continuously streaming frames,
 * which is what keeps Live Mode cheap on CPU/battery.
 *
 * No frame is ever written to disk or sent off the device.
 */
class ScreenCapture(private val context: Context) {

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var width = 0
    private var height = 0
    private var dpi = 0

    fun requestIntent(activity: Activity): Intent {
        val mgr = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return mgr.createScreenCaptureIntent()
    }

    /** Must be called from a foreground service after startForeground(). */
    fun start(resultCode: Int, data: Intent): Boolean {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = mgr.getMediaProjection(resultCode, data) ?: return false
        projection = proj

        val dm = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        width = dm.widthPixels
        height = dm.heightPixels
        dpi = dm.densityDpi

        val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        virtualDisplay = proj.createVirtualDisplay(
            "TradeSignalAICapture", width, height, dpi,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, Handler(Looper.getMainLooper())
        )
        return true
    }

    /** Pulls the most recent frame, if any, converting it into our Frame model. Non-blocking. */
    fun captureFrame(): Frame? {
        val reader = imageReader ?: return null
        val image = try { reader.acquireLatestImage() } catch (e: Exception) { null } ?: return null
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            val cropped = if (rowPadding == 0) bitmap else Bitmap.createBitmap(bitmap, 0, 0, width, height)

            // downscale for speed: full resolution is unnecessary for candle-shape detection
            val scale = if (width > 720) 720f / width else 1f
            val scaled = if (scale < 1f)
                Bitmap.createScaledBitmap(cropped, (width * scale).toInt(), (height * scale).toInt(), true)
            else cropped

            val w = scaled.width
            val h = scaled.height
            val px = IntArray(w * h)
            scaled.getPixels(px, 0, w, 0, 0, w, h)

            if (scaled !== cropped) scaled.recycle()
            if (cropped !== bitmap) cropped.recycle()
            bitmap.recycle()

            return Frame(w, h, px)
        } catch (e: Exception) {
            return null
        } finally {
            image.close()
        }
    }

    fun stop() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
    }

    fun isActive(): Boolean = projection != null
}
