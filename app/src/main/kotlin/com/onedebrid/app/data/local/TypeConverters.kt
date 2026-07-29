package com.onedebrid.app.data.local

import androidx.room.TypeConverter
import com.onedebrid.app.domain.model.SubtitleFormat
import com.onedebrid.app.domain.model.VideoQuality
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room TypeConverters for complex types that SQLite cannot store natively.
 *
 * All converters follow the same contract:
 * - Writing to the database: convert the type to a String.
 * - Reading from the database: convert the String back to the type.
 *
 * Enums are stored by name, never by ordinal. Ordinals shift if enum cases
 * are reordered; names remain stable.
 *
 * Complex types (maps, lists) are serialised to JSON using
 * kotlinx.serialization, which is already a project dependency.
 *
 * These converters are registered on AppDatabase via @TypeConverters and
 * are available to every DAO and entity in the database.
 */
class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    // -------------------------------------------------------------------------
    // Map<String, List<String>>
    // Used by: ProfileEntity.providerPriorities
    // -------------------------------------------------------------------------

    @TypeConverter
    fun fromProviderPriorities(value: Map<String, List<String>>): String {
        return json.encodeToString(
            MapSerializer(
                String.serializer(),
                ListSerializer(String.serializer())
            ),
            value
        )
    }

    @TypeConverter
    fun toProviderPriorities(value: String): Map<String, List<String>> {
        return json.decodeFromString(
            MapSerializer(
                String.serializer(),
                ListSerializer(String.serializer())
            ),
            value
        )
    }

    // -------------------------------------------------------------------------
    // List<String>
    // Used by: any entity storing an ordered list of string identifiers
    // -------------------------------------------------------------------------

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return json.encodeToString(ListSerializer(String.serializer()), value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return json.decodeFromString(ListSerializer(String.serializer()), value)
    }

    // -------------------------------------------------------------------------
    // VideoQuality enum
    // Stored as the enum entry name (e.g. "HD_1080"), never as ordinal.
    // -------------------------------------------------------------------------

    @TypeConverter
    fun fromVideoQuality(value: VideoQuality): String {
        return value.name
    }

    @TypeConverter
    fun toVideoQuality(value: String): VideoQuality {
        return VideoQuality.valueOf(value)
    }

    // -------------------------------------------------------------------------
    // SubtitleFormat enum (nullable)
    // Null means no format preference — stored as an empty string.
    // -------------------------------------------------------------------------

    @TypeConverter
    fun fromSubtitleFormat(value: SubtitleFormat?): String {
        return value?.name ?: ""
    }

    @TypeConverter
    fun toSubtitleFormat(value: String): SubtitleFormat? {
        return if (value.isEmpty()) null
        else SubtitleFormat.valueOf(value)
    }
    // -------------------------------------------------------------------------
// DownloadStatus enum
// Stored as the enum entry name (e.g. "DOWNLOADING"), never as ordinal.
// -------------------------------------------------------------------------

@TypeConverter
fun fromDownloadStatus(value: com.onedebrid.app.data.local.entity.DownloadStatus): String {
    return value.name
}

@TypeConverter
fun toDownloadStatus(value: String): com.onedebrid.app.data.local.entity.DownloadStatus {
    return com.onedebrid.app.data.local.entity.DownloadStatus.valueOf(value)
}
}