package com.onedebrid.app.data.local

import androidx.room.TypeConverter
import com.onedebrid.app.domain.model.SubtitleFormat
import com.onedebrid.app.domain.model.VideoQuality
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {

    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromListToString(value: List<String>?): String {
        return value?.let { json.encodeToString(it) } ?: "[]"
    }

    @TypeConverter
    fun fromStringToList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromProviderPrioritiesMap(value: Map<String, List<String>>?): String {
        return value?.let { json.encodeToString(it) } ?: "{}"
    }

    @TypeConverter
    fun toProviderPrioritiesMap(value: String?): Map<String, List<String>> {
        if (value.isNullOrEmpty()) return emptyMap()
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    @TypeConverter
    fun fromVideoQuality(quality: VideoQuality?): String? {
        return quality?.name
    }

    @TypeConverter
    fun toVideoQuality(value: String?): VideoQuality {
        if (value.isNullOrEmpty()) return VideoQuality.SD
        return try {
            VideoQuality.valueOf(value)
        } catch (e: Exception) {
            VideoQuality.SD
        }
    }

    @TypeConverter
    fun fromSubtitleFormat(format: SubtitleFormat?): String? {
        return format?.name
    }

    @TypeConverter
    fun toSubtitleFormat(value: String?): SubtitleFormat? {
        if (value.isNullOrEmpty()) return null
        return try {
            SubtitleFormat.valueOf(value)
        } catch (e: Exception) {
            null
        }
    }
}
