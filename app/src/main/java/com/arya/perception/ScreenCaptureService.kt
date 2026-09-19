package com.arya.perception

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import com.arya.diagnostics.AryaLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer

/**
 * Production-Grade Foreground Service managing Android MediaProjection screen capture.
 * Features:
 * - Dynamic resolution scaling (preserves aspect ratio, reduces memory & latency)
 * - Android 14+ (API 34+) lifecycle callback compliance
 * - Frame rate throttling (prevents thrashing)
 * - Visual perceptual hashing (dHash) and deduplication
 * - Memory-safe bitmap buffers and lifecycle cleanup
 */
class ScreenCaptureService : Service() {

    private val binder = LocalBinder()
    private val captureMutex = Mutex()

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionCallback: MediaProjection.Callback? = null

    val deduplicator = FrameDeduplicator()

    var realScreenWidth: Int = 1080
        private set
    var realScreenHeight: Int = 2400
        private set
    var captureWidth: Int = 540
        private set
    var captureHeight: Int = 1200
        private set
    var captureScale: Float = 0.5f
        private set

    private var lastCaptureTimeMs: Long = 0L
    var minCaptureIntervalMs: Long = 150L

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
        instance = this
        return START_NOT_STICKY
    }

    private fun startForegroundNotification() {
        val notification = NotificationCompat.Builder(this, AryaApplication.CHANNEL_EXECUTION_ID)
            .setContentTitle("ARYA Digital Operator")
            .setContentText("Screen perception engine active")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * Initializes MediaProjection with Android 14+ lifecycle callbacks, aspect-ratio preserved downsampling,
     * and VirtualDisplay setup.
     */
    fun initializeProjection(
        resultCode: Int,
        data: Intent,
        realWidth: Int,
        realHeight: Int,
        densityDpi: Int,
        scaleFactor: Float = 0.5f
    ) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, data)

        // Mandatory callback registration for Android 14+ (API 34+)
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                AryaLogger.i(TAG, "MediaProjection stopped by OS or user.")
                cleanupResources()
            }
        }
        projectionCallback = callback
        mediaProjection?.registerCallback(callback, null)

        realScreenWidth = realWidth
        realScreenHeight = realHeight
        captureScale = scaleFactor.coerceIn(0.25f, 1.0f)

        // Calculate scaled dimensions maintaining aspect ratio
        captureWidth = (realWidth * captureScale).toInt().coerceAtLeast(1)
        captureHeight = (realHeight * captureScale).toInt().coerceAtLeast(1)

        AryaLogger.i(TAG, "Initializing projection: real=${realWidth}x${realHeight} -> capture=${captureWidth}x${captureHeight} (scale=$captureScale)")

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
        instance = this
    }

    /**
     * Captures a screenshot frame with throttling, timeout safety, and metadata calculation.
     */
    suspend fun captureScreenshot(
        timeoutMs: Long = 2000L,
        skipDuplicateCheck: Boolean = false
    ): Pair<Bitmap?, ScreenshotMetadata?> = withContext(Dispatchers.Default) {
        captureMutex.withLock {
            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastCaptureTimeMs
            if (timeSinceLast < minCaptureIntervalMs) {
                kotlinx.coroutines.delay(minCaptureIntervalMs - timeSinceLast)
            }

            val result = withTimeoutOrNull(timeoutMs) {
                val reader = imageReader ?: run {
                    AryaLogger.w(TAG, "captureScreenshot called but imageReader is null.")
                    return@withTimeoutOrNull null
                }

                val image = reader.acquireLatestImage() ?: return@withTimeoutOrNull null

                try {
                    val planes = image.planes
                    val buffer: ByteBuffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * captureWidth

                    val rawBitmap = Bitmap.createBitmap(
                        captureWidth + rowPadding / pixelStride,
                        captureHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    rawBitmap.copyPixelsFromBuffer(buffer)

                    val cleanBitmap = if (rowPadding == 0) {
                        rawBitmap
                    } else {
                        val cropped = Bitmap.createBitmap(rawBitmap, 0, 0, captureWidth, captureHeight)
                        rawBitmap.recycle()
                        cropped
                    }

                    // Compute visual hash
                    val hash = VisualHasher.computeDHash(cleanBitmap)
                    val isDup = !skipDuplicateCheck && deduplicator.isDuplicate(hash)
                    deduplicator.recordFrame(hash)

                    val orientation = resources.configuration.orientation
                    val metadata = ScreenshotMetadata(
                        width = captureWidth,
                        height = captureHeight,
                        originalWidth = realScreenWidth,
                        originalHeight = realScreenHeight,
                        scaleX = scaleX,
                        scaleY = scaleY,
                        visualHash = hash,
                        orientation = orientation,
                        timestamp = System.currentTimeMillis()
                    )

                    lastCaptureTimeMs = System.currentTimeMillis()
                    AryaLogger.d(TAG, "Frame captured: ${captureWidth}x${captureHeight}, dHash=${metadata.visualHashHex}, isDup=$isDup")

                    Pair(cleanBitmap, metadata)
                } catch (t: Throwable) {
                    AryaLogger.e(TAG, "Error decoding image buffer: ${t.message}", t)
                    null
                } finally {
                    image.close()
                }
            }

            result ?: Pair(null, null)
        }
    }

    private fun cleanupResources() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projectionCallback?.let { mediaProjection?.unregisterCallback(it) }
        projectionCallback = null
        mediaProjection?.stop()
        mediaProjection = null
        AryaApplication.instance.capabilityManager.setMediaProjectionReady(false)
        if (instance == this) {
            instance = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupResources()
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        const val NOTIFICATION_ID = 1001

        @Volatile
        var instance: ScreenCaptureService? = null
            private set
    }
}
