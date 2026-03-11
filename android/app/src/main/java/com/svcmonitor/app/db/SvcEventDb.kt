package com.svcmonitor.app.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SvcEventEntity::class, SvcEventFtsEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SvcEventDb : RoomDatabase() {
    abstract fun dao(): SvcEventDao

    companion object {
        @Volatile private var INSTANCE: SvcEventDb? = null

        fun get(context: Context): SvcEventDb {
            val c = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(c, SvcEventDb::class.java, "svc_events.db")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
