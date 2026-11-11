package com.openofficepdf.core.database.dao

import androidx.room.*
import com.openofficepdf.core.database.entity.ConversionTaskEntity
import com.openofficepdf.core.database.entity.OcrTaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM conversion_tasks ORDER BY created_at DESC")
    fun getAllConversionTasks(): Flow<List<ConversionTaskEntity>>

    @Query("SELECT * FROM conversion_tasks WHERE task_id = :taskId LIMIT 1")
    suspend fun getConversionTaskById(taskId: String): ConversionTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConversionTask(task: ConversionTaskEntity)

    @Query("UPDATE conversion_tasks SET status = :status, progress = :progress, last_completed_page = :lastCompletedPage, updated_at = :updatedAt WHERE task_id = :taskId")
    suspend fun updateConversionProgress(taskId: String, status: String, progress: Float, lastCompletedPage: Int, updatedAt: Long)

    @Query("SELECT * FROM ocr_tasks ORDER BY created_at DESC")
    fun getAllOcrTasks(): Flow<List<OcrTaskEntity>>

    @Query("SELECT * FROM ocr_tasks WHERE task_id = :taskId LIMIT 1")
    suspend fun getOcrTaskById(taskId: String): OcrTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateOcrTask(task: OcrTaskEntity)

    @Query("UPDATE ocr_tasks SET status = :status, progress = :progress, last_completed_page = :lastCompletedPage, updated_at = :updatedAt WHERE task_id = :taskId")
    suspend fun updateOcrProgress(taskId: String, status: String, progress: Float, lastCompletedPage: Int, updatedAt: Long)
}
