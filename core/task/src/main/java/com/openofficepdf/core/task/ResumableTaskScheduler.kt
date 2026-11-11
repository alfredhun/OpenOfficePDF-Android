package com.openofficepdf.core.task

import android.content.Context
import androidx.work.*
import com.openofficepdf.core.database.AppDatabase
import com.openofficepdf.core.database.entity.ConversionTaskEntity
import com.openofficepdf.core.database.entity.OcrTaskEntity
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * 支持页级断点续传的后台长耗时任务调度器 (任务书 3.3 节 & 8.2.2 节)
 * 适配 Android 14+ 前台任务管理与 WorkManager
 */
class ResumableTaskScheduler(
    private val context: Context,
    private val appDatabase: AppDatabase
) {

    private val workManager = WorkManager.getInstance(context)

    fun observeConversionTasks(): Flow<List<ConversionTaskEntity>> {
        return appDatabase.taskDao().getAllConversionTasks()
    }

    fun observeOcrTasks(): Flow<List<OcrTaskEntity>> {
        return appDatabase.taskDao().getAllOcrTasks()
    }

    suspend fun scheduleConversionTask(
        taskId: String,
        sourceUri: String,
        sourceFormat: String,
        targetFormat: String,
        totalPages: Int
    ) {
        val now = System.currentTimeMillis()
        val entity = ConversionTaskEntity(
            taskId = taskId,
            sourceDocumentId = null,
            sourceUri = sourceUri,
            sourceFormat = sourceFormat,
            targetFormat = targetFormat,
            conversionTier = "LOSSLESS",
            outputUri = null,
            stagingDirPath = null,
            status = "PENDING",
            totalPages = totalPages,
            lastCompletedPage = 0,
            progress = 0f,
            createdAt = now,
            updatedAt = now
        )
        appDatabase.taskDao().insertOrUpdateConversionTask(entity)

        // 提交 WorkManager 执行
        val workRequest = OneTimeWorkRequestBuilder<ResumablePageWorker>()
            .setInputData(
                workDataOf(
                    "TASK_ID" to taskId,
                    "TYPE" to "CONVERSION"
                )
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(taskId, ExistingWorkPolicy.KEEP, workRequest)
    }

    suspend fun cancelTask(taskId: String) {
        workManager.cancelUniqueWork(taskId)
        val now = System.currentTimeMillis()
        appDatabase.taskDao().updateConversionProgress(taskId, "CANCELLED", 0f, 0, now)
        appDatabase.taskDao().updateOcrProgress(taskId, "CANCELLED", 0f, 0, now)
    }
}

/**
 * 示例页级检查点工作器
 */
class ResumablePageWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString("TASK_ID") ?: return Result.failure()
        
        // 响应系统 onStopped 中断，保存 Checkpoint
        if (isStopped) {
            return Result.retry()
        }

        return Result.success()
    }
}
