package com.autopi.autopieapp.widget

import androidx.glance.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.view.WindowManager
import kotlin.math.min
import kotlin.math.sqrt
import android.net.Uri
import android.util.Base64
import com.google.gson.JsonObject
import java.io.File
import java.net.URI

internal const val MAX_WIDGET_IMAGE_SOURCE_LENGTH = 4 * 1024 * 1024
private const val MAX_IMAGE_BYTES = 8 * 1024 * 1024
internal data class WidgetImageSize(val width: Int, val height: Int)

/** Always scale to width: short containers crop vertically, tall ones leave vertical space. */
internal fun widgetImageContentScale(
    imageWidth: Int,
    imageHeight: Int,
    widgetWidth: Float,
    widgetHeight: Float
): ContentScale = if (imageHeight.toDouble() * widgetWidth > imageWidth.toDouble() * widgetHeight) {
    ContentScale.Crop
} else {
    ContentScale.Fit
}

/** Share one bitmap between responsive layouts so Android counts its memory only once. */
internal class WidgetImageLoader {
    private val mutex = Mutex()
    private var key: Pair<String, Long?>? = null
    private var bitmap: Bitmap? = null

    suspend fun load(context: Context, source: String, revision: Long?): Bitmap? = mutex.withLock {
        val requestedKey = source to revision
        if (key != requestedKey) {
            val loaded = withContext(Dispatchers.IO) { loadWidgetBitmap(context, source) }
            key = requestedKey
            bitmap = loaded
        }
        bitmap
    }
}

// Android allows 6 * screenWidth * screenHeight bytes; ARGB_8888 uses 4 bytes/pixel.
internal fun widgetImagePixelBudget(screenWidth: Int, screenHeight: Int): Long =
    (screenWidth.toLong() * screenHeight * 3 / 2).coerceAtLeast(1)

@Suppress("DEPRECATION")
internal fun widgetImagePixelBudget(context: Context): Long {
    val displaySize = Point()
    context.getSystemService(WindowManager::class.java)?.defaultDisplay?.getRealSize(displaySize)
    val metrics = context.resources.displayMetrics
    return widgetImagePixelBudget(
        displaySize.x.takeIf { it > 0 } ?: metrics.widthPixels,
        displaySize.y.takeIf { it > 0 } ?: metrics.heightPixels
    )
}

/** Keep native resolution unless its decoded pixels exceed the Android widget limit. */
internal fun widgetImageDecodeSize(
    sourceWidth: Int,
    sourceHeight: Int,
    maxPixels: Long
): WidgetImageSize {
    val budget = maxPixels.coerceAtLeast(1)
    if (sourceWidth.toLong() * sourceHeight <= budget) {
        return WidgetImageSize(sourceWidth, sourceHeight)
    }
    val scale = min(1.0, sqrt(budget.toDouble() / (sourceWidth.toDouble() * sourceHeight)))
    val width = (sourceWidth * scale).toInt().coerceAtLeast(1)
    val height = (sourceHeight * scale).toInt().coerceAtLeast(1)
    // A very thin image can round one dimension up to 1; still enforce the pixel budget.
    return WidgetImageSize(minOf(width.toLong(), budget / height).toInt().coerceAtLeast(1),
        minOf(height.toLong(), budget / width).toInt().coerceAtLeast(1))
}

internal fun isWidgetImageSource(value: String): Boolean {
    if (value.startsWith("data:image/png;base64,", true) ||
        value.startsWith("data:image/bmp;base64,", true) ||
        value.startsWith("data:image/x-ms-bmp;base64,", true)
    ) return true
    val uri = runCatching { URI(value) }.getOrNull()
    if (uri?.scheme.equals("content", true)) return true
    val path = when {
        value.startsWith("/") -> value
        uri?.scheme.equals("file", true) -> uri?.path.orEmpty()
        else -> return false
    }
    return path.endsWith(".png", true) || path.endsWith(".bmp", true)
}

internal fun widgetImageDescriptor(obj: JsonObject): DisplayOutput.Image? {
    fun string(key: String) = obj.get(key)?.takeIf {
        it.isJsonPrimitive && it.asJsonPrimitive.isString
    }?.asString
    if (string("type") !in listOf("image", "bitmap")) return null
    val source = (string("src") ?: string("path"))?.takeIf { it.isNotBlank() } ?: return null
    return DisplayOutput.Image(source, string("caption") ?: string("alt"))
}

/** Decode off the main thread; keep both encoded input and RemoteViews bitmap memory bounded. */
internal fun loadWidgetBitmap(
    context: Context,
    source: String
): Bitmap? = runCatching {
    if (source.length > MAX_WIDGET_IMAGE_SOURCE_LENGTH) return null
    val bytes = if (source.startsWith("data:", true)) {
        if (!isWidgetImageSource(source)) return null
        Base64.decode(source.substringAfter(','), Base64.DEFAULT)
    } else {
        val stream = when {
            source.startsWith("/") -> File(source).inputStream()
            source.startsWith("file:", true) -> File(URI(source)).inputStream()
            source.startsWith("content:", true) -> context.contentResolver.openInputStream(Uri.parse(source))
            else -> null
        } ?: return null
        stream.use { it.readBytesBounded() } ?: return null
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val renderSize = widgetImageDecodeSize(bounds.outWidth, bounds.outHeight,
        widgetImagePixelBudget(context))
    val renderWidth = renderSize.width
    val renderHeight = renderSize.height
    val options = BitmapFactory.Options().apply {
        inSampleSize = widgetImageSampleSize(bounds.outWidth, bounds.outHeight, renderWidth, renderHeight)
        inPreferredConfig = Bitmap.Config.ARGB_8888
        inScaled = false
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    if (decoded.width == renderWidth && decoded.height == renderHeight) return decoded
    Bitmap.createScaledBitmap(decoded, renderWidth, renderHeight, true).also {
        if (it !== decoded) decoded.recycle()
    }
}.getOrNull()

internal fun widgetImageSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
    var sample = 1
    // Choose the largest sample that still supplies every pixel of the final crop.
    while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight &&
        sample < (1 shl 29)) sample *= 2
    // Bound intermediate decode memory without shrinking an original that already fits.
    val decodePixelLimit = maxOf(16L * 1024 * 1024, targetWidth.toLong() * targetHeight)
    while (((width.toLong() + sample - 1) / sample) *
        ((height.toLong() + sample - 1) / sample) > decodePixelLimit) sample *= 2
    return sample
}

private fun java.io.InputStream.readBytesBounded(): ByteArray? {
    val buffer = java.io.ByteArrayOutputStream()
    val chunk = ByteArray(8192)
    while (true) {
        val count = read(chunk)
        if (count == -1) return buffer.toByteArray()
        if (buffer.size() + count > MAX_IMAGE_BYTES) return null
        buffer.write(chunk, 0, count)
    }
}
