package com.openofficepdf.core.database.dao

import androidx.room.*
import com.openofficepdf.core.database.entity.DocumentSessionEntity
import com.openofficepdf.core.database.entity.EditOperationEntity

@Dao
interface SessionAndEditDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: DocumentSessionEntity)

    @Query("SELECT * FROM document_sessions WHERE session_id = :sessionId LIMIT 1")
    suspend fun getSession(sessionId: String): DocumentSessionEntity?

    @Query("UPDATE document_sessions SET is_dirty = :isDirty, has_unsaved_changes = :isDirty WHERE session_id = :sessionId")
    suspend fun markSessionDirty(sessionId: String, isDirty: Boolean)

    @Query("DELETE FROM document_sessions WHERE session_id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEditOperation(operation: EditOperationEntity)

    @Query("SELECT * FROM edit_operations WHERE session_id = :sessionId ORDER BY sequence_number ASC")
    suspend fun getOperationsForSession(sessionId: String): List<EditOperationEntity>

    @Query("DELETE FROM edit_operations WHERE session_id = :sessionId")
    suspend fun deleteOperationsForSession(sessionId: String)
}

@Dao
interface DocumentSessionDao {

    @Query("SELECT * FROM document_sessions WHERE session_id = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): DocumentSessionEntity?

    @Query("SELECT * FROM document_sessions WHERE document_id = :documentId ORDER BY last_active_at DESC LIMIT 1")
    suspend fun getLatestSessionByDocId(documentId: String): DocumentSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(session: DocumentSessionEntity)

    @Query("UPDATE document_sessions SET has_unsaved_changes = :hasChanges, staging_file_path = :stagingPath, recovery_state = :recoveryState, last_active_at = :activeAt WHERE session_id = :sessionId")
    suspend fun updateStagingState(sessionId: String, hasChanges: Boolean, stagingPath: String?, recoveryState: String, activeAt: Long)

    @Delete
    suspend fun delete(session: DocumentSessionEntity)
}

@Dao
interface EditOperationDao {

    @Query("SELECT * FROM edit_operations WHERE session_id = :sessionId ORDER BY sequence_number ASC")
    suspend fun getOperationsBySession(sessionId: String): List<EditOperationEntity>

    @Query("SELECT MAX(sequence_number) FROM edit_operations WHERE session_id = :sessionId")
    suspend fun getMaxSequenceNumber(sessionId: String): Int?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOperation(op: EditOperationEntity)

    @Query("UPDATE edit_operations SET is_applied = :isApplied WHERE op_id = :opId")
    suspend fun updateAppliedStatus(opId: String, isApplied: Boolean)

    @Query("DELETE FROM edit_operations WHERE session_id = :sessionId AND sequence_number > :seq")
    suspend fun deleteOperationsAfterSeq(sessionId: String, seq: Int)

    @Query("DELETE FROM edit_operations WHERE session_id = :sessionId")
    suspend fun clearSessionOperations(sessionId: String)
}
