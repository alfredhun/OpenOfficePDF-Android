package com.openofficepdf.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversion_tasks",
    indices = [
        Index(value = ["status"], name = "idx_conv_tasks_status")
    ]
)
data class ConversionTaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "task_id")
    val taskId: String,
    
    @ColumnInfo(name = "source_document_id")
    val sourceDocumentId: String?,
    
    @ColumnInfo(name = "source_uri")
    val sourceUri: String,
    
    @ColumnInfo(name = "source_format")
    val sourceFormat: String,
    
    @ColumnInfo(name = "target_format")
    val targetFormat: String,
    
    @ColumnInfo(name = "conversion_tier")
    val conversionTier: String,               // LOSSLESS, LAYOUT_REBUILD, OCR_REBUILD
    
    @ColumnInfo(name = "output_uri")
    val outputUri: String?,
    
    @ColumnInfo(name = "staging_dir_path")
    val stagingDirPath: String?,              // 分页中间产物缓存目录
    
    @ColumnInfo(name = "status")
    val status: String,                       // PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
    
    @ColumnInfo(name = "total_pages", defaultValue = "1")
    val totalPages: Int = 1,
    
    @ColumnInfo(name = "last_completed_page", defaultValue = "0")
    val lastCompletedPage: Int = 0,           // 页级 Checkpoint 断点恢复游标
    
    @ColumnInfo(name = "progress", defaultValue = "0.0")
    val progress: Float = 0.0f,
    
    @ColumnInfo(name = "error_code")
    val errorCode: String? = null,
    
    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
