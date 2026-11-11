package com.openofficepdf.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.openofficepdf.core.database.dao.DocumentDao
import com.openofficepdf.core.database.dao.DocumentSessionDao
import com.openofficepdf.core.database.dao.EditOperationDao
import com.openofficepdf.core.database.dao.SessionAndEditDao
import com.openofficepdf.core.database.dao.TaskDao
import com.openofficepdf.core.database.entity.*

/**
 * 核心持久化数据库
 * 架构铁律：仅允许主进程直接持有并读写本数据库 (任务书 3.2 节)
 */
@Database(
    entities = [
        DocumentEntity::class,
        DocumentSessionEntity::class,
        EditOperationEntity::class,
        ConversionTaskEntity::class,
        OcrTaskEntity::class,
        MarkdownAssetEntity::class,
        MarkdownBookmarkEntity::class,
        AppSettingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun documentSessionDao(): DocumentSessionDao
    abstract fun editOperationDao(): EditOperationDao
    abstract fun sessionAndEditDao(): SessionAndEditDao
    abstract fun taskDao(): TaskDao

    companion object {
        private const val DB_NAME = "openofficepdf.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
            }
        }
    }
}
