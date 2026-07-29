package com.onedebrid.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity representing a single search history entry.
 *
 * Stores a query string that the user has previously searched, associated
 * with the profile that made the search. Used by the Search screen to display
 * recent queries as suggestions.
 *
 * Entries are deduplicated per profile — searching the same query again
 * updates the timestamp rather than creating a duplicate. The DAO handles
 * this via onConflict = REPLACE on the unique index over (profileId, query).
 *
 * Search history may be cleared by the user at any time, either for a specific
 * profile or entirely. The CASCADE delete on profileId ensures entries are
 * removed automatically when a profile is deleted.
 */
@Entity(
    tableName = "search_history",
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
        Index(value = ["profileId", "query"], unique = true)
    ]
)
data class SearchHistoryEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val profileId: String,

    val query: String,

    val searchedAt: Long
)