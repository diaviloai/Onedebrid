package com.onedebrid.app.coordinator

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.onedebrid.app.data.local.dao.DownloadDao
import com.onedebrid.app.data.local.entity.DownloadEntity
import com.onedebrid.app.data.local.entity.DownloadStatus
import com.onedebrid.app.domain.model.Media
import com.onedebrid.app.domain.model.StreamSource
import com.onedebrid.app.worker.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val workManager = WorkManager.getInstance(context)

    fun startDownload(
        media: Media,
        stream: StreamSource,
        profileId: String,
        episodeId: String? = null,
        seasonNum: Int? = null,
        epNum: Int? = null
    ) {
        val downloadId = UUID.randomUUID().toString()
        
        val entity = DownloadEntity(
            id = downloadId,
            profileId = profileId,
            mediaId = media.id,
            episodeId = episodeId,
            seasonNumber = seasonNum,
            episodeNumber = epNum,
            title = media.title,
            status = DownloadStatus.QUEUED,
            createdAt = System.currentTimeMillis()
        )

        // Insert into DB and enqueue the worker
        CoroutineScope(Dispatchers.IO).launch {
            downloadDao.insertDownload(entity)

            val inputData = Data.Builder()
                .putString(DownloadWorker.KEY_DOWNLOAD_ID, downloadId)
                .putString(DownloadWorker.KEY_URL, stream.url)
                .putString(DownloadWorker.KEY_FILE_NAME, stream.fileName)
                .build()

            // Only download when connected to the network
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .build()

            workManager.enqueue(request)
        }
    }
}