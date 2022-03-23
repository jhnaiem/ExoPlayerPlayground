package com.example.exoplayerplayground

import android.app.Notification
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.offline.DownloadService
import com.google.android.exoplayer2.scheduler.PlatformScheduler
import com.google.android.exoplayer2.scheduler.Scheduler

class ClipDownloadService(
    notificationId: Int = DownloadUtil().FOREGROUND_NOTIFICATION_ID,
    interval: Long = DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    notificationChannelId: String = DownloadUtil().DOWNLOAD_NOTIFICATION_CHANNEL_ID
) : DownloadService(
    notificationId, interval, notificationChannelId,
    R.string.exo_download_notification_channel_name, 0
) {

    private val JOB_ID = 1


    override fun getDownloadManager(): DownloadManager {

        // This will only happen once, because getDownloadManager is guaranteed to be called only once
        // in the life cycle of the process.
        val downloadManager = DownloadUtil().getDownloadManager( /* context= */this)
        val downloadNotificationHelper =
            DownloadUtil().getDownloadNotificationHelper( /* context= */this)
        return downloadManager
    }

    override fun getScheduler(): Scheduler {
        return PlatformScheduler(this, JOB_ID)
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return DownloadUtil().getDownloadNotificationHelper(/* context= */ this)
            .buildProgressNotification(
                /* context= */ this,
                R.drawable.ic_launcher_background,
                /* contentIntent= */ null,
                /* message= */ null,
                downloads,
                notMetRequirements
            );
    }
}
