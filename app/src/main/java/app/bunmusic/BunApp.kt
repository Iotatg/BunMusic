package app.bunmusic

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import app.bunmusic.core.CrashGuard
import app.bunmusic.data.Cache
import okio.Path.Companion.toPath
import app.bunmusic.data.Auth
import app.bunmusic.data.Downloads
import app.bunmusic.data.Prefs
import app.bunmusic.data.Scrobble
import app.bunmusic.data.Session
import app.bunmusic.data.Store
import app.bunmusic.player.Playback

class BunApp : Application(), SingletonImageLoader.Factory {

    /**
     * Nothing in here is allowed to throw.
     *
     * The handler goes in first, because a corrupt preferences file or a full
     * disk during one of the initialisers used to mean the app closed on launch
     * and then closed again on the next launch — an unrecoverable loop with no
     * way in. Every initialiser below is wrapped: a piece that fails leaves that
     * feature empty, and the app still opens.
     */
    override fun onCreate() {
        super.onCreate()
        CrashGuard.install(this)
        CrashGuard.safe("init:store") { Store.init(this) }
        CrashGuard.safe("init:prefs") { Prefs.init(this) }
        CrashGuard.safe("init:auth") { Auth.init(this) }
        CrashGuard.safe("init:skin") { app.bunmusic.core.Skin.load(this) }
        CrashGuard.safe("init:cache") { Cache.init(this) }
        CrashGuard.safe("init:downloads") { Downloads.init(this) }
        CrashGuard.safe("init:session") { Session.init(this) }
        CrashGuard.safe("init:scrobble") { Scrobble.init(this) }
        CrashGuard.safe("init:playback") { Playback.init(this) }
        // Bring the last queue back, paused where it stopped. Nothing is prepared
        // here — the first press of play does the network work.
        CrashGuard.safe("init:restore") {
            if (Prefs.settings.value.resumeOnLaunch) Playback.restoreSession()
        }
    }

    /**
     * Cover art gets its own ceiling so a long listening session cannot fill the
     * phone with thumbnails. Coil's default is a fraction of free space, which on
     * a full device is either enormous or zero — neither is a good answer.
     *
     * If the disk cache cannot be built — no free space, an unwritable directory —
     * an in-memory loader is still worth having: covers work for the session and
     * the app does not die on the first album tile.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val base = ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(220)
        val disk = CrashGuard.safe("coil:disk") {
            DiskCache.Builder()
                .directory(Cache.imageDir().absolutePath.toPath())
                .maxSizeBytes(220L * 1024L * 1024L)
                .build()
        }
        return if (disk != null) base.diskCache(disk).build() else base.build()
    }
}
