package com.openofficepdf.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 对象级增量操作日志表 (任务书 6.1 & 6.2 节)
 * 严格执行 CursorWindow 保护：before_meta_json / after_meta_json 单字段严格 < 64KB
 */
@Entity(
    tableName = "edit_operations",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DocumentSessionEntity::class,
            parentColumns = ["session_id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["session_id", "sequence_number"], name = "idx_edit_ops_session_seq"),
        Index(value = ["document_id"], name = "idx_edit_ops_document_id")
    ]
)
data class EditOperationEntity(
    @PrimaryKey
    @ColumnInfo(name = "op_id")
    val opId: String,
    
    @ColumnInfo(name = "document_id")
    val documentId: String,
    
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    
    @ColumnInfo(name = "page_index", defaultValue = "0")
    val pageIndex: Int = 0,
    
    @ColumnInfo(name = "target_object_id")
    val targetObjectId: String? = null,              // 关联的 PdfObjectModel.objectId
    
    @ColumnInfo(name = "operation_type")
    val operationType: String,               // ADD_TEXT, UPDATE_TEXT, MOVE_OBJ, REPLACE_IMG, REDACT, ANNOTATE
    
    @ColumnInfo(name = "before_meta_json")
    val beforeMetaJson: String? = null,             // 严格限制 < 64KB
    
    @ColumnInfo(name = "after_meta_json")
    val afterMetaJson: String? = null,              // 严格限制 < 64KB
    
    @ColumnInfo(name = "before_blob_path")
    val beforeBlobPath: String? = null,      // 大对象(如原图)沙盒文件相对路径引用
    
    @ColumnInfo(name = "after_blob_path")
    val afterBlobPath: String? = null,       // 大对象(如新图)沙盒文件相对路径引用
    
    @ColumnInfo(name = "sequence_number", defaultValue = "1")
    val sequenceNumber: Int = 1,                 // 单调递增序号，用于 Undo/Redo 游标控制
    
    @ColumnInfo(name = "is_applied", defaultValue = "1")
    val isApplied: Boolean = true,           // 1: 已生效, 0: 已撤销(Undo)
    
    @ColumnInfo(name = "created_at", defaultValue = "0")
    val createdAt: Long = System.currentTimeMillis()
) {
    val operationId: String get() = opId
    val operationPayloadJson: String? get() = afterMetaJson
    val blobPath: String? get() = afterBlobPath
    val timestamp: Long get() = createdAt

    @Ignore
    constructor(
        operationId: String,
        sessionId: String,
        pageIndex: Int,
        operationType: String,
        operationPayloadJson: String,
        blobPath: String? = null,
        timestamp: Long = System.currentTimeMillis(),
        documentId: String,
        sequenceNumber: Int = 1
    ) : this(
        opId = operationId,
        documentId = documentId,
        sessionId = sessionId,
        pageIndex = pageIndex,
        targetObjectId = null,
        operationType = operationType,
        beforeMetaJson = null,
        afterMetaJson = operationPayloadJson,
        beforeBlobPath = null,
        afterBlobPath = blobPath,
        sequenceNumber = sequenceNumber,
        isApplied = true,
        createdAt = timestamp
    )

    init {
        // 强制执行 CursorWindow 溢出防护契约 (64KB 限制)
        val maxByteLimit = 64 * 1024
        beforeMetaJson?.let {
            require(it.toByteArray(Charsets.UTF_8).size <= maxByteLimit) {
                "beforeMetaJson exceeds 64KB limit! Large blobs must be stored via beforeBlobPath."
            }
        }
        afterMetaJson?.let {
            require(it.toByteArray(Charsets.UTF_8).size <= maxByteLimit) {
                "afterMetaJson exceeds 64KB limit! Large blobs must be stored via afterBlobPath."
            }
        }
    }
}
