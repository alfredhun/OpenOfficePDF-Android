package com.openofficepdf.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "markdown_assets",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["document_id"], name = "idx_md_assets_doc")
    ]
)
data class MarkdownAssetEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "document_id")
    val documentId: String,
    
    @ColumnInfo(name = "relative_path")
    val relativePath: String,
    
    @ColumnInfo(name = "resolved_uri")
    val resolvedUri: String?,
    
    @ColumnInfo(name = "mime_type")
    val mimeType: String?,
    
    @ColumnInfo(name = "checksum_sha256")
    val checksumSha256: String?,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)

@Entity(
    tableName = "markdown_bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["document_id"], name = "idx_md_bookmarks_doc")
    ]
)
data class MarkdownBookmarkEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "document_id")
    val documentId: String,
    
    @ColumnInfo(name = "heading_id")
    val headingId: String,
    
    @ColumnInfo(name = "heading_title")
    val headingTitle: String,
    
    @ColumnInfo(name = "source_line")
    val sourceLine: Int,                      // 绑定 AST 源码行号
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey
    @ColumnInfo(name = "setting_key")
    val settingKey: String,
    
    @ColumnInfo(name = "setting_value")
    val settingValue: String,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
