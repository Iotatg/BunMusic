package app.bunmusic

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import app.bunmusic.data.Cache
import okio.Path.Companion.toPath
import app.bunmusic.data.Auth
import app.bunmusic.data.Downloads
import app.bunmusic.data.Prefs
import app.bunmusic.data.Store
import app.bunmusic.player.Playback

class BunApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Prefs.init(this)
        Auth.init(this)
        app.bunmusic.core.Skin.load(this)
        Cache.init(this)
        Downloads.init(this)
        Playback.init(this)
    }

    /**
     * Cover art gets its own ceiling so a long listening session cannot fill the
     * phone with thumbnails. Coil's default is a fraction of free space, which on
     * a full device is either enormous or zero — neither is a good answer.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val disk = DiskCache.Builder()
            .directory(Cache.imageDir().absolutePath.toPath())
            .maxSizeBytes(220L * 1024L * 1024L)
            .build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .diskCache(disk)
            .crossfade(220)
            .build()
    }
}
