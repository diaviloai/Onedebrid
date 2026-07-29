package com.onedebrid.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity representing a recently played media entry.
 *
 * Records that a profile watched a piece of media, and when. Used by the
 * Home screen to populate the Recently Played row and provide quick access
 * to content the user has engaged with.
 *
 * Unlike ContinueWatchingEntity, this entity stores no playback position or
 * completion state. Its sole purpose is recording recency.
 *
 * For TV content, episodeId, seasonNumber, and episodeNumber identify the
 * last episode the user watched within the show. All three are null for
 * movies or non-episodic content.
 *
 * Entries are deduplicated per profile per media item — rewatching something
 * updates the timestamp rather than adding a duplicate row. The DAO handles
 * this via onConflict = REPLACE on the unique index over (profileId, mediaId).
 *
 * CASCADE delete on profileId ensures entries are removed automatically when
 * a profile is deleted.
 */
@Entity(
    tableName = "recently_played",
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
data class RecentlyPlayedEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val profileId: String,

    val mediaId: String,

    // Last watched episode — all null for movies, all non-null for TV episodes
    val episodeId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,

    val lastPlayedAt: Long
)