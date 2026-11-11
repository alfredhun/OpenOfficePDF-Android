package com.openofficepdf.core.database.dao

import androidx.room.*
import com.openofficepdf.core.database.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY last_opened_at DESC")
    fun getAllRecentDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE format = :format ORDER BY last_opened_at DESC")
    fun getDocumentsByFormat(format: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE is_favorite = 1 ORDER BY last_opened_at DESC")
    fun getFavoriteDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE uri = :uri LIMIT 1")
    suspend fun getDocumentByUri(uri: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(document: DocumentEntity)

    @Update
    suspend fun update(document: DocumentEntity)

    @Delete
    suspend fun delete(document: DocumentEntity)

    @Query("UPDATE documents SET uri_permission_valid = :valid, last_validated_at = :validatedAt WHERE id = :id")
    suspend fun updatePermissionStatus(id: String, valid: Boolean, validatedAt: Long)

    @Query("UPDATE documents SET last_opened_at = :openedAt WHERE id = :id")
    suspend fun updateLastOpened(id: String, openedAt: Long)
}
