package com.example.exoplayerplayground

import android.net.Uri
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.exoplayerplayground.databinding.ActivityMainBinding
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.offline.Download
import com.google.android.exoplayer2.offline.DownloadRequest
import com.google.android.exoplayer2.offline.DownloadService
import com.google.android.exoplayer2.source.ConcatenatingMediaSource
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MediaSource.Factory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.util.Util


class MainActivity : AppCompatActivity() {


    private val viewBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityMainBinding.inflate(layoutInflater)
    }

    private var mediaSession: MediaSessionCompat? = null
    protected lateinit var mediaSessionConnector: MediaSessionConnector
    private var player: ExoPlayer? = null

    private var playWhenReadyy = true
    private var currentWindow = 0
    private var playbackPosition = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(viewBinding.root)
        viewBinding.buttonPlay.setOnClickListener {
            playPlayer(player)
        }

        viewBinding.buttonDownload.setOnClickListener {
            startDownload()
        }

        viewBinding.buttonPlayFromCache.setOnClickListener {
            playFromCache()
        }


        // Start the download service if it should be running but it's not currently.
        // Starting the service in the foreground causes notification flicker if there is no scheduled
        // action. Starting it in the background throws an exception if the app is in the background too
        // (e.g. if device screen is locked).
        try {
            DownloadService.start(this, ClipDownloadService::class.java)
        } catch (e: IllegalStateException) {
            DownloadService.startForeground(this, ClipDownloadService::class.java)
        }
    }

    private fun startDownload() {

        val downloadRequest: DownloadRequest = DownloadRequest.Builder(
            "123",
            Uri.parse("https://www.bensound.com/bensound-music/bensound-creativeminds.mp3")
        ).setMimeType("audio/mpeg").build()

        if (downloadRequest != null) {
            DownloadService.sendAddDownload(
                this,
                ClipDownloadService::class.java,
                downloadRequest!!,
                /* foreground= */ false
            )
        }
    }

    private fun releasePlayer() {
        player?.run {
            playbackPosition = this.currentPosition
            currentWindow = this.currentMediaItemIndex
            playWhenReadyy = this.playWhenReady
            release()
        }
        player = null
        mediaSession?.release()
    }

    private fun playPlayer(player: ExoPlayer?) {

        if (player != null) {
            player.playWhenReady = true
            player.setMediaSource(buildMediaSource())
            player.prepare()
            player.play()
        }
    }

    private fun initializePlayer() {
        player = ExoPlayer.Builder(this).setMediaSourceFactory(createMediaSourceFactory())
            .build()
            .also { exoPlayer ->
                viewBinding.pvMain.player = exoPlayer
                exoPlayer.playWhenReady = playWhenReadyy
                exoPlayer.seekTo(currentWindow, playbackPosition)
            }
        mediaSession = MediaSessionCompat(this, "sample")
        mediaSessionConnector = MediaSessionConnector(mediaSession!!)
        with(mediaSessionConnector) {
            setPlayer(player)
            setEnabledPlaybackActions(
                PlaybackStateCompat.ACTION_PLAY_PAUSE
                        or PlaybackStateCompat.ACTION_PLAY
                        or PlaybackStateCompat.ACTION_PAUSE
                        or PlaybackStateCompat.ACTION_SEEK_TO
                        or PlaybackStateCompat.ACTION_FAST_FORWARD
                        or PlaybackStateCompat.ACTION_REWIND
                        or PlaybackStateCompat.ACTION_STOP
                        or PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                        or PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE

            )

            setPlaybackPreparer(MusicPlaybackPreparer())

            //To provide control over the playlist then tell the connector to handle the
            // ACTION_SKIP_* commands by adding a QueueNavigator.
            mediaSessionConnector.setQueueNavigator(object : TimelineQueueNavigator(mediaSession) {
                override fun getMediaDescription(player: Player, idx: Int): MediaDescriptionCompat {
                    return MediaCatalog.list[idx]
                }
            })
        }
    }

    fun playFromCache() {

        var download: Download? = null
        val downloadIndex = DownloadUtil().getDownloadManager(this).downloadIndex
        val loadedDownloads = downloadIndex.getDownloads()
        while (loadedDownloads.moveToNext()) {
            download = loadedDownloads.download
        }
        val downloadRequest = download?.request
        val mediaItem = downloadRequest?.toMediaItem()

        if (mediaItem != null)
            player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()

    }


    private fun buildMediaSource(): MediaSource {
        val uriList = mutableListOf<MediaSource>()
        MediaCatalog.list.forEach {
            uriList.add(createExtractorMediaSource(it.mediaUri!!))
        }
        return ConcatenatingMediaSource(*uriList.toTypedArray())
    }

    private fun createExtractorMediaSource(uri: Uri): MediaSource {
        return ProgressiveMediaSource.Factory(
            DefaultDataSource.Factory(this)
        ).createMediaSource(MediaItem.fromUri(uri))
    }


    private fun createMediaSourceFactory(): Factory {

        return DefaultMediaSourceFactory(DownloadUtil().getDataSourceFactory(this))

    }

    public override fun onStart() {
        super.onStart()
        if (Util.SDK_INT >= 24) {
            initializePlayer()
        }
        mediaSession?.isActive = true
    }

    public override fun onResume() {
        super.onResume()
        if ((Util.SDK_INT < 24 || player == null)) {
            initializePlayer()
        }
    }

    public override fun onPause() {
        super.onPause()
        if (Util.SDK_INT < 24) {
            releasePlayer()
        }
    }


    public override fun onStop() {
        super.onStop()
        if (Util.SDK_INT >= 24) {
            releasePlayer()
        }
        mediaSession?.isActive = false
    }


    private inner class MusicPlaybackPreparer : MediaSessionConnector.PlaybackPreparer {
        override fun onPrepareFromSearch(
            query: String, playWhenReady: Boolean,
            extras: Bundle?
        ) = Unit


        override fun onCommand(
            player: Player,
            command: String,
            extras: Bundle?,
            cb: ResultReceiver?
        ): Boolean {
            return false
        }

        override fun getSupportedPrepareActions(): Long =
            PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT

        override fun onPrepareFromMediaId(
            mediaId: String, playWhenReady: Boolean,
            extras: Bundle?
        ) {
            //TODO: 1.Get song from SongListRepository by using mediaId
            //TODO: 2.Use exoplayer to play song.
        }

        override fun onPrepareFromUri(uri: Uri, playWhenReady: Boolean, extras: Bundle?) = Unit

        override fun onPrepare(playWhenReady: Boolean) {
            Log.d("PlaybackPrepare", "Called")
        }
    }


    object MediaCatalog {
        val list = mutableListOf<MediaDescriptionCompat>()

        init {
            list.add(
                with(MediaDescriptionCompat.Builder()) {
                    setDescription("MP3 loaded over HTTP")
                    setMediaId("1")
                    setMediaUri(Uri.parse("https://www.bensound.com/bensound-music/bensound-creativeminds.mp3"))
                    setTitle("Stock footage")
                    build()
                })
            list.add(
                with(MediaDescriptionCompat.Builder()) {
                    setDescription("MP3 loaded over HTTP")
                    setMediaId("2")
                    setMediaUri(Uri.parse("https://www.bensound.com/bensound-music/bensound-anewbeginning.mp3"))
                    setTitle("Spoken track")
                    setSubtitle("Streaming audio")
                    build()
                })
        }
    }

}
