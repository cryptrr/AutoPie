package com.autopi.autopieapp.widget

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.view.View
import android.widget.ImageView
import androidx.glance.layout.ContentScale
import kotlinx.coroutines.runBlocking
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class WidgetImageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun portraitImageKeepsBothWidthEdgesFilledWhenWidgetHeightChanges() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val source = Bitmap.createBitmap(60, 180, Bitmap.Config.ARGB_8888)
            source.eraseColor(Color.GREEN)
            try {
                listOf(150, 450, 1000).forEach { height ->
                    val image = ImageView(context).apply {
                        setImageBitmap(source)
                        scaleType = when (widgetImageContentScale(60, 180, 300f, height.toFloat())) {
                            ContentScale.Crop -> ImageView.ScaleType.CENTER_CROP
                            else -> ImageView.ScaleType.FIT_CENTER
                        }
                    }
                    image.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    image.layout(0, 0, 300, height)
                    val rendered = Bitmap.createBitmap(300, height, Bitmap.Config.ARGB_8888)
                    image.draw(Canvas(rendered))
                    assertEquals("Left edge at height $height", Color.GREEN, rendered.getPixel(0, height / 2))
                    assertEquals("Right edge at height $height", Color.GREEN, rendered.getPixel(299, height / 2))
                    rendered.recycle()
                }
            } finally {
                source.recycle()
            }
        }
    }

    @Test
    fun responsiveLayoutsShareOneBitmapAndReloadOnNewOutput() = runBlocking {
        val loader = WidgetImageLoader()
        val original = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use {
            original.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }
        original.recycle()
        val source = "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        val first = loader.load(context, source, 1)
        assertNotNull(first)
        assertSame(first, loader.load(context, source, 1))
        val next = loader.load(context, source, 2)
        assertNotNull(next)
        assertNotSame(first, next)
        first!!.recycle()
        next!!.recycle()
    }

    @Test
    fun pngBelowBudgetKeepsOriginalResolutionAndTransparency() {
        val original = Bitmap.createBitmap(1600, 800, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use {
            original.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }
        original.recycle()
        val source = "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
        val decoded = loadWidgetBitmap(context, source)
        assertNotNull(decoded)
        decoded!!.run {
            assertEquals(1600, width)
            assertEquals(800, height)
            assertEquals(width, height * 2)
            assertEquals(0, Color.alpha(getPixel(0, 0)))
            recycle()
        }
    }

    @Test
    fun wideImageRetainsEdgesAndSharpPixelsForLauncherPositioning() {
        val original = Bitmap.createBitmap(1200, 400, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.RED)
        // Keep the whole source; the launcher applies the crop using its actual bounds.
        for (y in 0 until 400) {
            for (x in 400 until 800) original.setPixel(x, y, Color.GREEN)
            original.setPixel(600, y, Color.BLUE)
        }
        val bytes = ByteArrayOutputStream().use {
            original.compress(Bitmap.CompressFormat.PNG, 100, it)
            it.toByteArray()
        }
        original.recycle()
        val decoded = loadWidgetBitmap(context,
            "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP))
        assertNotNull(decoded)
        decoded!!.run {
            assertEquals(1200, width)
            assertEquals(400, height)
            assertEquals(Color.RED, getPixel(0, 200))
            assertEquals(Color.GREEN, getPixel(599, 200))
            assertEquals(Color.BLUE, getPixel(600, 200))
            assertEquals(Color.GREEN, getPixel(601, 200))
            assertEquals(Color.RED, getPixel(1199, 200))
            recycle()
        }
    }

    @Test
    fun oversizedPngIsReducedOnlyToAndroidBitmapBudget() {
        val budget = widgetImagePixelBudget(context)
        val side = kotlin.math.sqrt(budget.toDouble()).toInt() + 100
        val original = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val file = File.createTempFile("widget-oversized", ".png", context.cacheDir)
        try {
            file.outputStream().use { original.compress(Bitmap.CompressFormat.PNG, 100, it) }
            original.recycle()
            val decoded = loadWidgetBitmap(context, file.absolutePath)
            assertNotNull(decoded)
            decoded!!.run {
                assertTrue(width < side)
                assertEquals(width, height)
                assertTrue(width.toLong() * height <= budget)
                assertTrue((width + 1L) * (height + 1L) > budget)
                recycle()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun bmpFileAndFileUriRenderPixels() {
        // A 1 x 1, uncompressed 24-bit red BMP with a padded BGR row.
        val bmp = ByteBuffer.allocate(58).order(ByteOrder.LITTLE_ENDIAN).apply {
            put('B'.code.toByte()).put('M'.code.toByte())
            putInt(58).putInt(0).putInt(54)
            putInt(40).putInt(1).putInt(1)
            putShort(1.toShort()).putShort(24.toShort())
            putInt(0).putInt(4).putInt(0).putInt(0).putInt(0).putInt(0)
            put(0.toByte()).put(0.toByte()).put(255.toByte()).put(0.toByte())
        }.array()
        val file = File.createTempFile("widget", ".bmp", context.cacheDir)
        try {
            file.writeBytes(bmp)
            listOf(file.absolutePath, file.toURI().toString()).forEach { source ->
                val decoded = loadWidgetBitmap(context, source)
                assertNotNull(decoded)
                assertEquals(Color.RED, decoded!!.getPixel(0, 0))
                decoded.recycle()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun invalidAndMissingImagesReturnNull() {
        assertNull(loadWidgetBitmap(context, "data:image/png;base64,AAAA"))
        assertNull(loadWidgetBitmap(context, "data:image/bmp;base64,%%%"))
        assertNull(loadWidgetBitmap(context, File(context.cacheDir, "missing-widget.png").absolutePath))
        assertNull(loadWidgetBitmap(context, "https://example.com/image.png"))
    }
}
