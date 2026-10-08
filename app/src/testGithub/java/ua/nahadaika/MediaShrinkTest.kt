package ua.nahadaika

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.ExifInterface
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ua.nahadaika.share.MediaShrink
import java.io.File

/** Стиснення фото перед відправкою в спільний чат (справжнє кодування — NATIVE-графіка). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MediaShrinkTest {
    private val app: Application = ApplicationProvider.getApplicationContext()

    private fun photo(name: String, w: Int, h: Int, quality: Int = 100): File {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Color.BLUE, Color.YELLOW, Shader.TileMode.MIRROR) })
        val dots = Paint().apply { color = Color.RED }
        for (i in 0 until 400) canvas.drawCircle((i * 97 % w).toFloat(), (i * 53 % h).toFloat(), 6f, dots)
        return File(app.cacheDir, name).apply { outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) } }
    }

    private fun size(file: File) = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(file.path, it) }

    @Test fun bigPhotoShrinksTo1600pxAndTargetWeight() {
        val source = photo("big.jpg", 4000, 3000)
        val out = File(app.cacheDir, "out.jpg")
        assertTrue(MediaShrink.photo(source, "image/jpeg", out))
        val o = size(out)
        assertEquals(1600, maxOf(o.outWidth, o.outHeight))
        assertEquals(1200, minOf(o.outWidth, o.outHeight))
        assertTrue("${out.length()} байт", out.length() <= MediaShrink.PHOTO_TARGET_BYTES)
        assertTrue(out.length() < source.length())
    }

    @Test fun cameraRotationIsBakedIn() {
        val source = photo("rotated.jpg", 2000, 1000)
        ExifInterface(source.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val out = File(app.cacheDir, "rotated-out.jpg")
        assertTrue(MediaShrink.photo(source, "image/jpeg", out))
        val o = size(out)
        assertEquals(800, o.outWidth)
        assertEquals(1600, o.outHeight)
    }

    @Test fun smallJpegIsSentAsIs() {
        val source = photo("small.jpg", 800, 600, quality = 80)
        assertTrue(source.length() <= MediaShrink.PHOTO_TARGET_BYTES)
        assertFalse(MediaShrink.photo(source, "image/jpeg", File(app.cacheDir, "small-out.jpg")))
    }
}
