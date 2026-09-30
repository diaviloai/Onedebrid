package com.onedebrid.app.data.local

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

@Entity(tableName = "placeholder_entities")
data class PlaceholderEntity(
    @PrimaryKey val id: Int = 1
)

@Database(
    entities = [PlaceholderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // DAO interface declarations will go here
}
