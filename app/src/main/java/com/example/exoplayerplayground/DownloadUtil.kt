package com.example.exoplayerplayground

import android.content.Context
import androidx.media3.datasource.cronet.CronetDataSourceFactory
import androidx.media3.datasource.cronet.CronetEngineWrapper
import com.google.android.exoplayer2.database.DatabaseProvider
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.offline.DownloadManager
import com.google.android.exoplayer2.ui.DownloadNotificationHelper
import com.google.android.exoplayer2.upstream.DataSource.Factory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.HttpDataSource
import com.google.android.exoplayer2.upstream.cache.Cache
import com.google.android.exoplayer2.upstream.cache.CacheDataSource
import com.google.android.exoplayer2.upstream.cache.NoOpCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import java.io.File
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.Executors

class DownloadUtil {

    var DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"

    val FOREGROUND_NOTIFICATION_ID = 1

    private val USE_CRONET_FOR_NETWORKING = true

    private val TAG = "DemoUtil"
    private val DOWNLOAD_CONTENT_DIRECTORY = "downloads"

    private var dataSourceFactory: Factory? = null
    private var httpDataSourceFactory: HttpDataSource.Factory? = null


    private var databaseProvider: DatabaseProvider? = null

    private var downloadDirectory: File? = null

    companion object {
        private var downloadCache: Cache? = null
    }

    private var downloadManager: DownloadManager? = null


    private var downloadNotificationHelper: DownloadNotificationHelper? = null

    @Synchronized
    fun getDownloadManager(context: Context): DownloadManager {
        ensureDownloadManagerInitialized(context)
        return downloadManager!!
    }

    @Synchronized
    private fun ensureDownloadManagerInitialized(context: Context) {
        if (downloadManager == null) {
            downloadManager = DownloadManager(
                context,
                getDatabaseProvider(context),
                getDownloadCache(context),
                getHttpDataSourceFactory(context),
                Executors.newFixedThreadPool( /* nThreads= */6)
            )
        }
    }

    @Synchronized
    private fun getDatabaseProvider(context: Context): DatabaseProvider {
        if (databaseProvider == null) {
            databaseProvider = StandaloneDatabaseProvider(context)
        }
        return databaseProvider!!
    }

    @Synchronized
    fun getDownloadCache(context: Context): Cache {
        if (downloadCache == null) {
            val downloadContentDirectory =
                File(getDownloadDirectory(context), DOWNLOAD_CONTENT_DIRECTORY)
            downloadCache = SimpleCache(
                downloadContentDirectory, NoOpCacheEvictor(), getDatabaseProvider(context)
            )
        }
        return downloadCache!!
    }

    @Synchronized
    private fun getDownloadDirectory(context: Context): File? {
        if (downloadDirectory == null) {
            downloadDirectory = context.cacheDir
            if (downloadDirectory == null) {
                downloadDirectory = context.filesDir
            }
        }
        return downloadDirectory
    }

    @Synchronized
    fun getHttpDataSourceFactory(context: Context): HttpDataSource.Factory {
        var context = context
        if (httpDataSourceFactory == null) {
            if (USE_CRONET_FOR_NETWORKING) {
                context = context.applicationContext
                val cronetEngine = CronetEngineWrapper(context)

                val cr = CronetDataSourceFactory(cronetEngine, Executors.newSingleThreadExecutor())
                // TODO accroddingly
                //httpDataSourceFactory = CronetDataSource.Factory(cronetEngine, Executors.newSingleThreadExecutor())
            }
            if (httpDataSourceFactory == null) {
                // We don't want to use Cronet, or we failed to instantiate a CronetEngine.
                val cookieManager = CookieManager()
                cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER)
                CookieHandler.setDefault(cookieManager)
                httpDataSourceFactory = DefaultHttpDataSource.Factory()
            }
        }
        return DefaultHttpDataSource.Factory()
    }

    fun getCacheDataSourceFactory(context: Context): Factory {
        return CacheDataSource.Factory()
            .setCache(DownloadUtil().getDownloadCache(context))
            .setUpstreamDataSourceFactory(getHttpDataSourceFactory(context))
            .setCacheWriteDataSinkFactory(null)
    }

    @Synchronized
    fun getDownloadNotificationHelper(
        context: Context?
    ): DownloadNotificationHelper {
        if (downloadNotificationHelper == null) {
            downloadNotificationHelper =
                DownloadNotificationHelper(context!!, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
        }
        return downloadNotificationHelper!!
    }
}

