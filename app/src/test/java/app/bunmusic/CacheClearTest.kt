package app.bunmusic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.bunmusic.data.Cache
import app.bunmusic.widget.WidgetArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * "Clear artwork" used to leave the whole `cacheDir/widget_art` store behind,
 * because the null-check sat inside an inline `runCatching` and a null context
 * (or a Coil init that threw) non-locally returned before the widget cache was
 * touched. Both cover stores must be counted and both must be cleared.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CacheClearTest {

    @Test
    fun `clearImages removes coil art and the shared widget art cache`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Cache.init(ctx)

        // A cover written through the Coil/player path.
        val coilDir = Cache.imageDir().apply { mkdirs() }
        val coilFile = File(coilDir, "coil_cover.png").apply { writeBytes(ByteArray(2048)) }
        // A canonical square written by the notification / widget path.
        val widgetDir = File(ctx.cacheDir, "widget_art").apply { mkdirs() }
        val widgetFile = File(widgetDir, "0123456789abcdef0123456789abcdef.png")
            .apply { writeBytes(ByteArray(4096)) }

        assertTrue(coilFile.exists())
        assertTrue(widgetFile.exists())
        assertTrue("both stores must be counted before clearing", Cache.imageBytes() > 0L)
        assertTrue(WidgetArt.bytes(ctx) > 0L)

        Cache.clearImages()

        assertEquals("widget art must be gone", 0L, WidgetArt.bytes(ctx))
        assertTrue(!widgetFile.exists())
        assertTrue(!coilFile.exists())
        assertEquals(0L, Cache.imageBytes())
    }
}
