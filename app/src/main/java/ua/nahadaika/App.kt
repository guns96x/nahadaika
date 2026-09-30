package ua.nahadaika

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import ua.nahadaika.alarm.Notifier
import ua.nahadaika.data.Repo
import ua.nahadaika.ui.theme.ThemeSettings

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        ThemeSettings.init(this)
        Notifier.createChannel(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(VideoFrameDecoder.Factory()) }
        .crossfade(true)
        .build()
}
