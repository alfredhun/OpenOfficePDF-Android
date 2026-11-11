package com.openofficepdf.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "document_sessions",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["document_id"], name = "idx_sessions_doc_id")
    ]
)
data class DocumentSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    
    @ColumnInfo(name = "document_id")
    val documentId: String,
    
    @ColumnInfo(name = "engine_type", defaultValue = "OFFICE")
    val engineType: String = "OFFICE",                    // PDF, OFFICE, MARKDOWN
    
    @ColumnInfo(name = "current_page", defaultValue = "0")
    val currentPage: Int = 0,

    @ColumnInfo(name = "last_opened_page", defaultValue = "0")
    val lastOpenedPage: Int = currentPage,
    
    @ColumnInfo(name = "zoom_scale", defaultValue = "1.0")
    val zoomScale: Float = 1.0f,
    
    @ColumnInfo(name = "scroll_offset_x", defaultValue = "0.0")
    val scrollOffsetX: Float = 0.0f,
    
    @ColumnInfo(name = "scroll_offset_y", defaultValue = "0.0")
    val scrollOffsetY: Float = 0.0f,
    
    @ColumnInfo(name = "editor_mode", defaultValue = "READ")
    val editorMode: String = "READ",           // READ, ANNOTATE, OBJECT_EDIT, SPLIT_PREVIEW

    @ColumnInfo(name = "active_tool", defaultValue = "VIEW")
    val activeTool: String = editorMode,
    
    @ColumnInfo(name = "staging_file_path")
    val stagingFilePath: String? = null,       // 未提交的沙盒工作副本路径
    
    @ColumnInfo(name = "has_unsaved_changes", defaultValue = "0")
    val hasUnsavedChanges: Boolean = false,

    @ColumnInfo(name = "is_dirty", defaultValue = "0")
    val isDirty: Boolean = hasUnsavedChanges,

    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "last_active_at", defaultValue = "0")
    val lastActiveAt: Long = System.currentTimeMillis(),
    
    @ColumnInfo(name = "recovery_state", defaultValue = "CLEAN")
    val recoveryState: String = "CLEAN"        // CLEAN, DIRTY, CRASH_RECOVERABLE
)
