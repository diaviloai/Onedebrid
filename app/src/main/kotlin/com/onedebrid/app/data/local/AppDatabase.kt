package com.onedebrid.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        // Declare your entities here
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // Declare DAOs here
}
