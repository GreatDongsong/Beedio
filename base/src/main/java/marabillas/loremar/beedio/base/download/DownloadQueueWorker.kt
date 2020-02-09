/*
 * Beedio is an Android app for downloading videos
 * Copyright (C) 2019 Loremar Marabillas
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package marabillas.loremar.beedio.base.download

import android.content.Context
import androidx.room.Room
import androidx.work.*
import com.google.gson.Gson
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import marabillas.loremar.beedio.base.database.DownloadItem
import marabillas.loremar.beedio.base.database.DownloadListDatabase
import marabillas.loremar.beedio.base.media.VideoDetails
import marabillas.loremar.beedio.base.media.VideoDetailsFetcher
import java.io.File
import java.util.concurrent.Executors

class DownloadQueueWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    private val downloadList = Room
            .databaseBuilder(
                    context,
                    DownloadListDatabase::class.java,
                    "downloads"
            )
            .build()
            .downloadListDao()

    private val videoDetailsFetcher = VideoDetailsFetcher()
    private val videoDownloader = VideoDownloader(context)
    private val gson = Gson()

    enum class Status { INACTIVE, FETCHING_DETAILS, DOWNLOADING }

    companion object {
        var status = Status.INACTIVE
        const val QUEUE_EVENT = "queue_event"
        const val QUEUE_EVENT_DATA_FILE = "queue_event_data.json"
        const val QUEUE_VIDEO_DETAILS_FILE = "queue_video_details.json"
        const val QUEUE_AUDIO_DETAILS_FILE = "queue_audio_details.json"
        const val QUEUE_START_NEW = 0
        const val QUEUE_FINISHED = 1
        const val QUEUE_VIDEO_DETAILS = 2
        const val QUEUE_AUDIO_DETAILS = 3
        const val QUEUE_DOWNLOAD_START = 4
        const val QUEUE_EXCEPTION = 5
        private const val UNIQUE_NAME = "download_queue_worker"

        private val downloadQueueContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

        fun work(context: Context) {
            val downloadRequest = OneTimeWorkRequestBuilder<DownloadQueueWorker>()
                    .build()

            WorkManager
                    .getInstance(context)
                    .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, downloadRequest)
        }

        fun stop(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)

        fun getQueueEventLiveData(context: Context) =
                WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(UNIQUE_NAME)

        private fun saveEventData(context: Context, json: String, filename: String) = runBlocking(downloadQueueContext) {
            File(context.filesDir, filename).writeText(json)
        }

        private fun deleteEventData(context: Context, filename: String) = runBlocking(downloadQueueContext) {
            val file = File(context.filesDir, filename)
            if (file.exists())
                file.delete()
        }

        fun loadEventData(context: Context, filename: String): String? = runBlocking(downloadQueueContext) {
            val file = File(context.filesDir, filename)
            if (file.exists())
                file.readText()
            else
                null
        }
    }

    override fun doWork(): Result {
        workOnTopItem()
        return Result.success()
    }

    private fun workOnTopItem() {
        status = Status.INACTIVE
        deleteEventData(applicationContext, QUEUE_VIDEO_DETAILS_FILE)
        deleteEventData(applicationContext, QUEUE_AUDIO_DETAILS_FILE)
        val first = downloadList.first() ?: return {
            update(QUEUE_FINISHED)
        }()

        status = Status.FETCHING_DETAILS
        update(QUEUE_START_NEW)

        videoDetailsFetcher.fetchDetails(first.videoUrl, object : VideoDetailsFetcher.FetchListener {
            override fun onUnFetched(error: Throwable) {
                startDownload(first)
            }

            override fun onFetched(details: VideoDetails) {
                update(QUEUE_VIDEO_DETAILS, details)

                if (first.audioUrl != null)
                    videoDetailsFetcher.fetchDetails(first.audioUrl, object : VideoDetailsFetcher.FetchListener {
                        override fun onUnFetched(error: Throwable) {
                            if (!isStopped)
                                startDownload(first)
                        }

                        override fun onFetched(details: VideoDetails) {
                            update(QUEUE_AUDIO_DETAILS, details)
                            if (!isStopped)
                                startDownload(first)
                        }
                    })
                else
                    if (!isStopped)
                        startDownload(first)
            }
        })

    }

    private fun startDownload(item: DownloadItem) {
        status = Status.DOWNLOADING
        update(QUEUE_DOWNLOAD_START)

        try {
            videoDownloader.download(item)
            if (isStopped)
                return
            completed()
        } catch (e: VideoDownloader.DownloadException) {
            failed(e)
        }
        next()
    }

    private fun completed() {
        // TODO
    }

    private fun next() {
        videoDownloader.stop()
        deleteEventData(applicationContext, QUEUE_VIDEO_DETAILS_FILE)
        deleteEventData(applicationContext, QUEUE_AUDIO_DETAILS_FILE)
        val list = downloadList.load().toMutableList()
        downloadList.delete(list)
        list.removeAt(0)
        list.forEachIndexed { i, item -> item.uid = i }
        downloadList.save(list)
        status = Status.INACTIVE
        workOnTopItem()
    }

    private fun failed(e: VideoDownloader.DownloadException) {
        if (e.cause is VideoDownloader.UnavailableException) {
            // TODO Add to inactive list
        } else {
            update(QUEUE_EXCEPTION, e)
        }
    }

    private fun update(event: Int, data: Any? = null) {
        if (data != null) {
            val json = gson.toJson(data)
            when (event) {
                QUEUE_VIDEO_DETAILS -> saveEventData(applicationContext, json, QUEUE_VIDEO_DETAILS_FILE)
                QUEUE_AUDIO_DETAILS -> saveEventData(applicationContext, json, QUEUE_AUDIO_DETAILS_FILE)
                else -> saveEventData(applicationContext, json, QUEUE_EVENT_DATA_FILE)
            }
        }
        val workData = workDataOf(QUEUE_EVENT to event)
        setProgressAsync(workData)
    }

    override fun onStopped() {
        super.onStopped()
        videoDetailsFetcher.cancel()
        status = Status.INACTIVE
        videoDownloader.stop()
        deleteEventData(applicationContext, QUEUE_VIDEO_DETAILS_FILE)
        deleteEventData(applicationContext, QUEUE_AUDIO_DETAILS_FILE)
    }
}