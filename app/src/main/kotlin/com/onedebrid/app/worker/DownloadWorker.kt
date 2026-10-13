package com.onedebrid.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.onedebrid.app.data.local.dao.DownloadDao
import com.onedebrid.app.di.DefaultHttpClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val downloadDao: DownloadDao,
    @DefaultHttpClient private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: "download.mp4"

        try {
            // Create a dedicated downloads directory in the app's external files
            val downloadDir = File(context.getExternalFilesDir(null), "OneDebrid_Downloads")
            if (!downloadDir.exists()) downloadDir.mkdirs()
            
            val outputFile = File(downloadDir, fileName)

            // Mark as downloading and save the future file path
            downloadDao.markAsDownloading(downloadId, outputFile.absolutePath)

            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                downloadDao.markAsFailed(downloadId)
                return@withContext Result.retry()
            }

            val body = response.body ?: throw Exception("Empty response body")
            
            // Stream the file to disk
            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesCopied = 0L
                    var read: Int
                    var lastUpdateTime = System.currentTimeMillis()

                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        bytesCopied += read

                        // Throttle database updates to once per second to avoid locking the DB
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateTime > 1000) {
                            downloadDao.updateProgress(downloadId, bytesCopied)
                            lastUpdateTime = currentTime
                        }
                    }
                }
            }

            // Mark as completed with the final file size
            downloadDao.markAsCompleted(downloadId, outputFile.length())
            Result.success()

        } catch (e: Exception) {
            downloadDao.markAsFailed(downloadId)
            Result.retry()
        }
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "file_name"
    }
}