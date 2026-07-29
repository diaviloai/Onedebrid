package com.onedebrid.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity representing a Continue Watching entry.
 *
 * Stores playback progress for a piece of media so the user can resume
 * from where they left off. Each entry belongs to a profile.
 *
 * mediaId references a Media domain model but is not a foreign key — media
 * data lives in the cache or is fetched from the network, not in a separate
 * Room table. Storing only the ID keeps this table independent of cache state.
 *
 * For TV episodes, episodeId, seasonNumber, and episodeNumber are all non-null.
 * For movies or non-episodic content, all three are null.
 * The repository enforces this invariant — the entity does not.
 *
 * isCompleted is set when playback reaches the completion threshold (typically
 * ~90%). The UI uses this to distinguish resumable content from fully watched
 * content.
 */
@Entity(
    tableName = "continue_watching",
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
        Index(value = ["profileId", "mediaId"], unique = true)
    ]
)
data class ContinueWatchingEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val profileId: String,

    val mediaId: String,

    // Episode information — all null for movies, all non-null for TV episodes
    val episodeId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,

    // Playback progress
    val positionMs: Long,
    val durationMs: Long? = null,

    // Timestamps
    val lastWatchedAt: Long,

    // Resume status
    val isCompleted: Boolean = false
)