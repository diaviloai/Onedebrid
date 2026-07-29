package com.onedebrid.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.onedebrid.app.data.local.entity.DownloadEntity
import com.onedebrid.app.data.local.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    // --- Observation ---

    /**
     * Observes all downloads across all statuses, ordered by most recently
     * queued. Drives the Downloads management screen.
     */
    @Query("""
        SELECT * FROM downloads
        ORDER BY queuedAt DESC
    """)
    fun observeAllDownloads(): Flow<List<DownloadEntity>>

    /**
     * Observes downloads in active states only (QUEUED, DOWNLOADING, PAUSED).
     * Used by the Download Coordinator to know what work needs to be done
     * and by the UI to show active transfer progress.
     */
    @Query("""
        SELECT * FROM downloads
        WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED')
        ORDER BY queuedAt ASC
    """)
    fun observeActiveDownloads(): Flow<List<DownloadEntity>>

    /**
     * Observes a single download entry by ID.
     * Used on a detail or progress view for a specific download.
     */
    @Query("SELECT * FROM downloads WHERE id = :downloadId")
    fun observeDownload(downloadId: String): Flow<DownloadEntity?>

    // --- One-shot reads ---

    /**
     * Returns a single download entry by ID.
     * Used by the Download Coordinator when it needs current state
     * before deciding the next transition.
     */
    @Query("SELECT * FROM downloads WHERE id = :downloadId")
    suspend fun getDownload(downloadId: String): DownloadEntity?

    /**
     * Returns all downloads in FAILED status.
     * Used to surface retry candidates and enforce retryCount limits.
     */
    @Query("SELECT * FROM downloads WHERE status = 'FAILED'")
    suspend fun getFailedDownloads(): List<DownloadEntity>

    /**
     * Returns all COMPLETED downloads.
     * Used by the Download System for storage management and cleanup.
     */
    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED'")
    suspend fun getCompletedDownloads(): List<DownloadEntity>

    // --- Insert ---

    /**
     * Queues a new download. IGNORE on conflict means attempting to queue
     * the same download twice has no effect — the existing entry is kept.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDownload(download: DownloadEntity)

    // --- Targeted state transitions ---

    /**
     * Transitions a download to DOWNLOADING and sets the local file path.
     * localPath is set here because the file is created when downloading
     * begins — it does not exist at queue time.
     */
    @Query("""
        UPDATE downloads
        SET status = 'DOWNLOADING', localPath = :localPath
        WHERE id = :downloadId
    """)
    suspend fun markAsDownloading(downloadId: String, localPath: String)

    /**
     * Updates download progress. Called frequently during an active
     * download. Targeted update avoids reading and rewriting the full row.
     */
    @Query("""
        UPDATE downloads
        SET downloadedBytes = :downloadedBytes
        WHERE id = :downloadId
    """)
    suspend fun updateProgress(downloadId: String, downloadedBytes: Long)

    /**
     * Transitions a download to PAUSED.
     */
    @Query("""
        UPDATE downloads
        SET status = 'PAUSED'
        WHERE id = :downloadId
    """)
    suspend fun markAsPaused(downloadId: String)

    /**
     * Transitions a download from PAUSED back to DOWNLOADING.
     */
    @Query("""
        UPDATE downloads
        SET status = 'DOWNLOADING'
        WHERE id = :downloadId
    """)
    suspend fun markAsResumed(downloadId: String)

    /**
     * Transitions a download to COMPLETED and records the final file size.
     */
    @Query("""
        UPDATE downloads
        SET status = 'COMPLETED', downloadedBytes = :finalBytes
        WHERE id = :downloadId
    """)
    suspend fun markAsCompleted(downloadId: String, finalBytes: Long)

    /**
     * Transitions a download to FAILED and increments retryCount.
     * The repository checks retryCount against a limit before allowing
     * further retry attempts.
     */
    @Query("""
        UPDATE downloads
        SET status = 'FAILED', retryCount = retryCount + 1
        WHERE id = :downloadId
    """)
    suspend fun markAsFailed(downloadId: String)

    /**
     * Transitions a download to CANCELLED.
     * Applies regardless of current status — any active download can
     * be cancelled.
     */
    @Query("""
        UPDATE downloads
        SET status = 'CANCELLED'
        WHERE id = :downloadId
    """)
    suspend fun markAsCancelled(downloadId: String)

    /**
     * Requeues a failed download for retry by resetting status to QUEUED.
     * Does not reset retryCount — the count is cumulative so the repository
     * can enforce a maximum retry limit across attempts.
     */
    @Query("""
        UPDATE downloads
        SET status = 'QUEUED'
        WHERE id = :downloadId AND status = 'FAILED'
    """)
    suspend fun requeueForRetry(downloadId: String)

    // --- Deletion ---

    /**
     * Removes a download record entirely.
     * Used after cancellation or when the user removes a completed download
     * from their library. Deleting the local file is the Download System's
     * responsibility — this only removes the database record.
     */
    @Query("DELETE FROM downloads WHERE id = :downloadId")
    suspend fun deleteDownload(downloadId: String)
}