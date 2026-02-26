package com.autoglm.phoneagent.capture

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.RequiresApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Captures screenshots of the app's UI.
 * Provides multiple methods for capturing Activity windows or individual Views.
 */
object ScreenCapture {

    /**
     * Represents a captured screenshot with metadata.
     *
     * @property bitmap The captured bitmap
     * @property width Width of the screenshot in pixels
     * @property height Height of the screenshot in pixels
     * @property base64Data Base64 encoded PNG data (lazily computed)
     */
    data class Screenshot(
        val bitmap: Bitmap,
        val width: Int,
        val height: Int
    ) {
        val base64Data: String by lazy {
            android.util.Base64.encodeToString(
                toByteArray(),
                android.util.Base64.NO_WRAP
            )
        }

        /**
         * Convert bitmap to PNG byte array.
         */
        fun toByteArray(): ByteArray {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            return stream.toByteArray()
        }

        /**
         * Get the data URL for use in API requests.
         */
        fun toDataURL(): String {
            return "data:image/png;base64,$base64Data"
        }
    }

    /**
     * Capture the entire Activity window.
     * This is the recommended method for full-screen captures.
     *
     * @param activity The activity to capture
     * @return Screenshot object containing the bitmap and metadata
     */
    fun captureActivity(activity: Activity): Screenshot {
        val decorView = activity.window.decorView
        val width = decorView.width
        val height = decorView.height

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        decorView.draw(canvas)

        return Screenshot(bitmap, width, height)
    }

    /**
     * Capture a specific View.
     * Use this for capturing individual components.
     *
     * @param view The view to capture
     * @return Screenshot object containing the bitmap and metadata
     */
    fun captureView(view: View): Screenshot {
        val width = view.width
        val height = view.height

        if (width <= 0 || height <= 0) {
            throw IllegalStateException("View has invalid dimensions: ${width}x$height")
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Handle view backgrounds
        if (view.background != null) {
            view.background.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }

        view.draw(canvas)

        return Screenshot(bitmap, width, height)
    }

    /**
     * Capture the entire Activity window using PixelCopy (API 24+).
     * This method is more accurate for capturing layered UI elements but requires API 24+.
     *
     * @param activity The activity to capture
     * @return Screenshot object containing the bitmap and metadata
     */
    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun captureWithPixelCopy(activity: Activity): Screenshot = suspendCancellableCoroutine { continuation ->
        val window = activity.window
        val decorView = window.decorView
        val width = decorView.width
        val height = decorView.height

        if (width <= 0 || height <= 0) {
            continuation.resumeWithException(
                IllegalStateException("Window has invalid dimensions: ${width}x$height")
            )
            return@suspendCancellableCoroutine
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        android.view.PixelCopy.request(
            window,
            bitmap,
            { result ->
                if (result == android.view.PixelCopy.SUCCESS) {
                    continuation.resume(Screenshot(bitmap, width, height))
                } else {
                    bitmap.recycle()
                    continuation.resumeWithException(
                        RuntimeException("PixelCopy failed with result: $result")
                    )
                }
            },
            android.os.Handler(android.os.Looper.getMainLooper())
        )

        continuation.invokeOnCancellation {
            bitmap.recycle()
        }
    }

    /**
     * Capture with automatic method selection.
     * Uses PixelCopy on API 24+ devices, falls back to Canvas draw on older devices.
     *
     * @param activity The activity to capture
     * @return Screenshot object containing the bitmap and metadata
     */
    suspend fun captureAuto(activity: Activity): Screenshot {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                captureWithPixelCopy(activity)
            } catch (e: Exception) {
                // Fallback to canvas method if PixelCopy fails
                captureActivity(activity)
            }
        } else {
            captureActivity(activity)
        }
    }

    /**
     * Capture the root view of the activity (content view only, excluding status/action bars).
     *
     * @param activity The activity to capture
     * @return Screenshot object containing the bitmap and metadata
     */
    fun captureContentView(activity: Activity): Screenshot {
        val contentView = activity.findViewById<ViewGroup>(android.R.id.content)
        return captureView(contentView)
    }

    /**
     * Get the current screen name/activity name for logging.
     *
     * @param activity The activity to get the name for
     * @return The simple class name of the activity
     */
    fun getCurrentScreenName(activity: Activity): String {
        return activity.javaClass.simpleName
    }
}
