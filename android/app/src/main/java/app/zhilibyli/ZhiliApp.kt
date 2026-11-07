package app.zhilibyli

import android.app.Application
import app.zhilibyli.data.OfflineRepository
import app.zhilibyli.download.DownloadService

class ZhiliApp : Application() {

    override fun onCreate() {
        super.onCreate()
        OfflineRepository.init(this)
        NetworkMonitor.init(this)
        DownloadService.createChannel(this)
    }
}
