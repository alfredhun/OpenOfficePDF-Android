package com.openofficepdf.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["last_opened_at"], orders = [Index.Order.DESC], name = "idx_documents_last_opened"),
        Index(value = ["format"], name = "idx_documents_format"),
        Index(value = ["is_favorite"], name = "idx_documents_favorite")
    ]
)
data class DocumentEntity(
    @PrimaryKey
    val id: String,                              // UUID
    
    @ColumnInfo(name = "uri")
    val uri: String,                            // SAF content:// URI (Unique)
    
    @ColumnInfo(name = "display_name")
    val displayName: String,
    
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    
    @ColumnInfo(name = "format")
    val format: String,                         // PDF, DOCX, XLSX, MD, HTML, TXT, JPG, PNG
    
    @ColumnInfo(name = "size_bytes", defaultValue = "0")
    val sizeBytes: Long = 0,
    
    @ColumnInfo(name = "page_count", defaultValue = "0")
    val pageCount: Int = 0,
    
    @ColumnInfo(name = "checksum_sha256")
    val checksumSha256: String? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "modified_at")
    val modifiedAt: Long,
    
    @ColumnInfo(name = "last_opened_at")
    val lastOpenedAt: Long? = null,
    
    @ColumnInfo(name = "uri_permission_valid", defaultValue = "1")
    val uriPermissionValid: Boolean = true,
    
    @ColumnInfo(name = "last_validated_at")
    val lastValidatedAt: Long? = null,
    
    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false,
    
    @ColumnInfo(name = "thumbnail_cache_path")
    val thumbnailCachePath: String? = null
)
