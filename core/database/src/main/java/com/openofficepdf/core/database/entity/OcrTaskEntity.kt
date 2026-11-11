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
    val documentId: String? = null,
    
    @ColumnInfo(name = "source_uri")
    val sourceUri: String,
    
    @ColumnInfo(name = "output_uri")
    val outputUri: String? = null,
    
    @ColumnInfo(name = "target_output_type", defaultValue = "SEARCHABLE_PDF")
    val targetOutputType: String = "SEARCHABLE_PDF",             // SEARCHABLE_PDF, DOCX, XLSX, MD, TXT
    
    @ColumnInfo(name = "language_pack", defaultValue = "ch_en")
    val languagePack: String = "ch_en",
    
    @ColumnInfo(name = "model_version", defaultValue = "v4")
    val modelVersion: String = "v4",
    
    @ColumnInfo(name = "status")
    val status: String,
    
    @ColumnInfo(name = "total_pages", defaultValue = "1")
    val totalPages: Int = 1,
    
    @ColumnInfo(name = "last_completed_page", defaultValue = "0")
    val lastCompletedPage: Int = 0,           // 断点恢复游标

    @ColumnInfo(name = "completed_pages", defaultValue = "0")
    val completedPages: Int = lastCompletedPage,

    @ColumnInfo(name = "last_completed_page_index", defaultValue = "0")
    val lastCompletedPageIndex: Int = lastCompletedPage,

    @ColumnInfo(name = "enable_table_recovery", defaultValue = "0")
    val enableTableRecovery: Boolean = false,

    @ColumnInfo(name = "checkpoint_data_json")
    val checkpointDataJson: String? = null,
    
    @ColumnInfo(name = "progress", defaultValue = "0.0")
    val progress: Float = 0.0f,
    
    @ColumnInfo(name = "result_json_dir_path")
    val resultJsonDirPath: String? = null,           // 按页存储的 OcrPageResult JSON 目录路径
    
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "updated_at", defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis()
)
