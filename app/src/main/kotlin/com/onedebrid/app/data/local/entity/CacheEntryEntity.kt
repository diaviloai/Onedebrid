package com.onedebrid.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Database entity representing a single cached response.
 *
 * CacheEntryEntity provides a generic, type-discriminated cache store for
 * temporary network responses. Rather than maintaining separate tables per
 * cacheable resource type, a single table is used with a cacheType column
 * identifying the kind of data stored.
 *
 * This works because the Cache system never queries on the contents of cached
 * data — it only needs to know whether a given key exists and is still fresh.
 * The data column is opaque to the database; the Cache system deserialises it.
 *
 * key is the unique identifier for a cached resource. Format conventions are
 * defined by the Cache system, not this entity. Examples:
 *   "metadata:tt1234567"
 *   "subtitles:tt1234567:en"
 *   "artwork:tmdb:550"
 *
 * cacheType identifies the category of cached data. Used for bulk operations
 * such as clearing all metadata cache or all subtitle cache independently.
 * Examples: "metadata", "artwork", "subtitles", "provider"
 *
 * data holds the serialised JSON response. The Cache system is responsible
 * for serialising before writing and deserialising after reading.
 *
 * expiresAt is a Unix timestamp in milliseconds. Entries where the current
 * time exceeds expiresAt are considered stale. The DAO exposes a query to
 * purge all expired entries.
 *
 * CacheEntryEntity has no foreign key to ProfileEntity. Most cached data
 * is profile-independent — metadata for a film is the same regardless of
 * which profile is active. If profile-scoped caching becomes necessary in
 * future, a nullable profileId column can be added without breaking existing
 * entries.
 */
@Entity(
    tableName = "cache_entries",
    indices = [
        Index(value = ["key"], unique = true),
        Index("cacheType"),
        Index("expiresAt")
    ]
)
data class CacheEntryEntity(

    @PrimaryKey
    val key: String,

    val cacheType: String,

    val data: String,

    val cachedAt: Long,

    val expiresAt: Long
)