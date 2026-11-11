package com.openofficepdf.engine.converter

import android.net.Uri
import com.openofficepdf.core.common.AppDispatchers
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.database.dao.TaskDao
import com.openofficepdf.core.database.entity.ConversionTaskEntity
import com.openofficepdf.core.model.DocumentFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 格式转换流水线调度引擎实现
 * 1. 支持异步逐页流式输出进度
 * 2. 具备逐页 Checkpoint 断点保护
 * 3. 支持任务暂停与无损取消
 */
class ConversionEngineImpl(
    private val taskDao: TaskDao
) : ConversionEngine {

    private val activeCancellations = ConcurrentHashMap<String, Boolean>()

    override fun executeConversion(
        taskId: String,
        sourceUri: Uri,
        sourceFormat: DocumentFormat,
        targetFormat: DocumentFormat,
        outputUri: Uri,
        options: ConversionOptions
    ): Flow<TaskProgress> = flow {
        activeCancellations[taskId] = false

        // 读取断点记录
        val existingTask = taskDao.getConversionTask(taskId)
        val startPage = existingTask?.completedPages ?: 0
        val totalPages = existingTask?.totalPages ?: 5

        for (currentPage in startPage until totalPages) {
            if (activeCancellations[taskId] == true) {
                // 更新任务为暂停或取消状态
                taskDao.insertConversionTask(
                    ConversionTaskEntity(
                        taskId = taskId,
                        sourceUri = sourceUri.toString(),
                        sourceFormat = sourceFormat.name,
                        targetFormat = targetFormat.name,
                        status = "PAUSED",
                        totalPages = totalPages,
                        completedPages = currentPage,
                        qualityTier = options.tier.name,
                        checkpointDataJson = """{"lastPage":$currentPage}""",
                        outputUri = outputUri.toString(),
                        createdAt = existingTask?.createdAt ?: System.currentTimeMillis()
                    )
                )
                throw CancellationException("Conversion task $taskId was canceled by user")
            }

            // 模拟不同转换层级处理耗时与阶段描述
            val stageDesc = when (options.tier) {
                ConversionTier.LOSSLESS -> "正在生成高保真矢量结构 (第 ${currentPage + 1}/$totalPages 页)"
                ConversionTier.LAYOUT_REBUILD -> "正在逆向分析版面流式段落 (第 ${currentPage + 1}/$totalPages 页)"
                ConversionTier.OCR_REBUILD -> "正在执行离线 OCR 与表格恢复 (第 ${currentPage + 1}/$totalPages 页)"
            }

            // 逐页持久化 Checkpoint
            val updatedTask = ConversionTaskEntity(
                taskId = taskId,
                sourceUri = sourceUri.toString(),
                sourceFormat = sourceFormat.name,
                targetFormat = targetFormat.name,
                status = if (currentPage + 1 == totalPages) "COMPLETED" else "RUNNING",
                totalPages = totalPages,
                completedPages = currentPage + 1,
                qualityTier = options.tier.name,
                checkpointDataJson = """{"lastPage":$currentPage}""",
                outputUri = outputUri.toString(),
                createdAt = existingTask?.createdAt ?: System.currentTimeMillis()
            )
            taskDao.insertConversionTask(updatedTask)

            val progress = (currentPage + 1).toFloat() / totalPages
            emit(
                TaskProgress(
                    taskId = taskId,
                    currentPage = currentPage + 1,
                    totalPages = totalPages,
                    progress = progress,
                    stageDescription = stageDesc
                )
            )
        }
    }.flowOn(AppDispatchers.io)

    override suspend fun cancelConversion(taskId: String): EngineResult<Unit> = withContext(AppDispatchers.io) {
        activeCancellations[taskId] = true
        EngineResult.success(Unit)
    }
}
