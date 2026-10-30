package app.bunmusic

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import app.bunmusic.data.Downloads
import app.bunmusic.data.Prefs
import app.bunmusic.data.Store
import app.bunmusic.player.Playback

class BunApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Prefs.init(this)
        Downloads.init(this)
        Playback.init(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(220)
            .build()
}
