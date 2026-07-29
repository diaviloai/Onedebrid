package com.onedebrid.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity representing a managed offline download.
 *
 * Tracks media that the user has requested for offline viewing. Each entry
 * represents one downloadable file — for TV content this means one episode
 * per row, not one show.
 *
 * DownloadStatus drives the Download system's state machine. The system
 * transitions entries through statuses as downloads progress, fail, or
 * are cancelled.
 *
 * localPath is the on-device file URI where the downloaded content is stored.
 * It is null until the file has been created on disk.
 *
 * retryCount tracks how many times the Download system has attempted this
 * download. Used to enforce a retry limit before marking an entry as
 * permanently failed.
 *
 * totalBytes and downloadedBytes are both nullable — totalBytes may not be
 * known until the server responds, and downloadedBytes starts at zero but
 * is stored as nullable to distinguish "not started" from "started but zero".
 * In practice, downloadedBytes is 0 once downloading begins.
 *
 * CASCADE delete on profileId ensures downloads are removed when a profile
 * is deleted.
 */
@Entity(
    tableName = "downloads",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("profileId"),
        Index("status")
    ]
)
data class DownloadEntity(

    @PrimaryKey
    val id: String,

    val profileId: String,

    val mediaId: String,

    // For TV episodes — null for movies
    val episodeId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,

    // Human-readable title for display in the Downloads screen
    val title: String,

    // On-device file location — null until the file has been created
    val localPath: String? = null,

    // Progress tracking
    val totalBytes: Long? = null,
    val downloadedBytes: Long? = null,

    // State machine
    val status: DownloadStatus,

    // Retry handling
    val retryCount: Int = 0,

    // Timestamps
    val createdAt: Long,
    val completedAt: Long? = null
)

/**
 * Represents the current state of a download managed by the Download system.
 *
 * Stored by name via TypeConverters — never by ordinal.
 */
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}