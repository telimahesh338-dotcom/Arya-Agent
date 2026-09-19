package com.arya.perception

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arya.AryaApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/**
 * Foreground Service managing Android MediaProjection screen capture.
 * Captures frames at 50% resolution to minimize latency and token size,
 * providing accurate coordinate scaling back to native screen dimensions.
 */
class ScreenCaptureService : Service() {

    private val binder = LocalBinder()
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    var realScreenWidth: Int = 1080
        private set
    var realScreenHeight: Int = 2400
        private set
    var captureWidth: Int = 540
        private set
    var captureHeight: Int = 1200
        private set

    val scaleX: Float
        get() = if (captureWidth > 0) realScreenWidth.toFloat() / captureWidth.toFloat() else 1.0f

    val scaleY: Float
        get() = if (captureHeight > 0) realScreenHeight.toFloat() / captureHeight.toFloat() else 1.0f

    inner class LocalBinder : Binder() {
        fun getService(): ScreenCaptureService = this@ScreenCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundNotification()
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, AryaApplication.CHANNEL_EXECUTION_ID)
            .setContentTitle("ARYA Digital Operator")
            .setContentText("Screen perception active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    fun initializeProjection(
        resultCode: Int,
        data: Intent,
        realWidth: Int,
        realHeight: Int,
        densityDpi: Int
    ) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)

        realScreenWidth = realWidth
        realScreenHeight = realHeight
        // 50% resolution downscaling for optimal capture & VLM processing
        captureWidth = (realWidth * 0.5f).toInt()
        captureHeight = (realHeight * 0.5f).toInt()

        imageReader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ARYA_ScreenCapture",
            captureWidth,
            captureHeight,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        AryaApplication.instance.capabilityManager.setMediaProjectionReady(true)
    }

    suspend fun captureScreenshot(): Bitmap? = withContext(Dispatchers.Default) {
        val reader = imageReader ?: return@withContext null
        val image = reader.acquireLatestImage() ?: return@withContext null

        try {
            val planes = image.planes
            val buffer: ByteBuffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * captureWidth

            val bitmap = Bitmap.createBitmap(
                captureWidth + rowPadding / pixelStride,
                captureHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            if (rowPadding == 0) {
                bitmap
            } else {
                val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, captureWidth, captureHeight)
                bitmap.recycle()
                cleanBitmap
            }
        } catch (_: Throwable) {
            null
        } finally {
            image.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        AryaApplication.instance.capabilityManager.setMediaProjectionReady(false)
    }

    companion object {
        const val NOTIFICATION_ID = 1001
    }
}
