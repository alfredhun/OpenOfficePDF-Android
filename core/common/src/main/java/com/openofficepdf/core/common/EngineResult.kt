package com.openofficepdf.core.common

/**
 * 核心引擎统一返回结果与状态封装 (任务书 7.1 节)
 */
sealed interface EngineResult<out T> {
    data class Success<T>(val data: T) : EngineResult<T>
    
    data class Failure(
        val code: EngineErrorCode,
        val message: String,
        val cause: Throwable? = null
    ) : EngineResult<Nothing>
    
    data object Cancelled : EngineResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    val isFailure: Boolean
        get() = this is Failure

    val isCancelled: Boolean
        get() = this is Cancelled

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Failure -> throw IllegalStateException("Engine operation failed: [$code] $message", cause)
        is Cancelled -> throw CancellationException("Engine operation was cancelled")
    }
}

class CancellationException(message: String) : Exception(message)
