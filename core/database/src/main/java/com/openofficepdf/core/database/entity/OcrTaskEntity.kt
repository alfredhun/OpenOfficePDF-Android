package com.openofficepdf.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ocr_tasks",
    indices = [
        Index(value = ["status"], name = "idx_ocr_tasks_status")
    ]
)
data class OcrTaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "task_id")
    val taskId: String,
    
    @ColumnInfo(name = "document_id")
    val documentId: String?,
    
    @ColumnInfo(name = "source_uri")
    val sourceUri: String,
    
    @ColumnInfo(name = "output_uri")
    val outputUri: String?,
    
    @ColumnInfo(name = "target_output_type")
    val targetOutputType: String,             // SEARCHABLE_PDF, DOCX, XLSX, MD, TXT
    
    @ColumnInfo(name = "language_pack", defaultValue = "ch_en")
    val languagePack: String = "ch_en",
    
    @ColumnInfo(name = "model_version")
    val modelVersion: String,
    
    @ColumnInfo(name = "status")
    val status: String,
    
    @ColumnInfo(name = "total_pages", defaultValue = "1")
    val totalPages: Int = 1,
    
    @ColumnInfo(name = "last_completed_page", defaultValue = "0")
    val lastCompletedPage: Int = 0,           // 断点恢复游标
    
    @ColumnInfo(name = "progress", defaultValue = "0.0")
    val progress: Float = 0.0f,
    
    @ColumnInfo(name = "result_json_dir_path")
    val resultJsonDirPath: String?,           // 按页存储的 OcrPageResult JSON 目录路径
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
