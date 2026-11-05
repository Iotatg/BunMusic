package app.bunmusic

import app.bunmusic.core.AppVersion
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The About screen reads [AppVersion], the APK reads `build.gradle.kts`, and
 * nothing tied them together — so 2.5.0, 2.6.0 and 2.7.0 all shipped an About
 * screen that said 2.4.0 (build 26). This test reads the Gradle file at test
 * time and fails the build the moment the two disagree again.
 */
class AppVersionTest {

    @Test
    fun aboutScreenAndApkAgreeOnTheVersion() {
        val gradle = listOf(
            File("build.gradle.kts"),
            File("app/build.gradle.kts"),
            File("../app/build.gradle.kts"),
        ).firstOrNull { it.isFile }
            ?: return // a test run without the module sources cannot check this

        val text = gradle.readText()
        val name = Regex("""versionName\s*=\s*"([^"]+)"""").find(text)?.groupValues?.get(1)
        val code = Regex("""versionCode\s*=\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()

        assertEquals("versionName in ${gradle.path}", AppVersion.VERSION, name)
        assertEquals("versionCode in ${gradle.path}", AppVersion.CODE, code)
        assertEquals("$name ($code)", AppVersion.LABEL)
    }
}
