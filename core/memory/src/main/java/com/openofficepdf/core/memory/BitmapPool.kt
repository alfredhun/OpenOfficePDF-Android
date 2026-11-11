package com.openofficepdf.core.memory

import android.content.ComponentCallbacks2
import android.graphics.Bitmap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 位图内存复用池接口 (任务书 5.3 节与 8.2.1 节)
 * 基于尺寸分桶 (Size-Bucketed) 管理 512x512 等规格瓦片与页面底图
 */
interface BitmapPool {
    fun acquire(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap
    fun release(bitmap: Bitmap)
    fun trimMemory(level: Int)
    fun trim(level: Int) = trimMemory(level)
    fun clear()
    fun poolSizeBytes(): Long = 0L
}

class BitmapPoolImpl(
    private val maxPoolSizeBytes: Long = 64L * 1024 * 1024 // 64MB 上限
) : BitmapPool {

    // 分桶键: width_height_config -> 复用队列
    private val buckets = ConcurrentHashMap<String, ConcurrentLinkedQueue<Bitmap>>()
    private var currentPoolSizeBytes: Long = 0

    override fun acquire(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        val key = makeKey(width, height, config)
        val queue = buckets[key]
        var bitmap = queue?.poll()

        while (bitmap != null && (bitmap.isRecycled || !bitmap.isMutable)) {
            bitmap = queue?.poll()
        }

        if (bitmap != null) {
            synchronized(this) {
                currentPoolSizeBytes -= bitmap.allocationByteCount
            }
            bitmap.eraseColor(0) // 清理脏数据
            return bitmap
        }

        return Bitmap.createBitmap(width, height, config)
    }

    override fun release(bitmap: Bitmap) {
        if (bitmap.isRecycled || !bitmap.isMutable) return

        val byteCount = bitmap.allocationByteCount.toLong()
        synchronized(this) {
            if (currentPoolSizeBytes + byteCount > maxPoolSizeBytes) {
                // 超出配额直接回收，避免堆积
                bitmap.recycle()
                return
            }
            currentPoolSizeBytes += byteCount
        }

        val key = makeKey(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        buckets.computeIfAbsent(key) { ConcurrentLinkedQueue() }.offer(bitmap)
    }

    override fun trimMemory(level: Int) {
        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                clear()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> {
                // 释放一半
                synchronized(this) {
                    buckets.values.forEach { queue ->
                        val half = queue.size / 2
                        repeat(half) {
                            queue.poll()?.recycle()
                        }
                    }
                    recalculatePoolSize()
                }
            }
        }
    }

    override fun clear() {
        synchronized(this) {
            buckets.values.forEach { queue ->
                while (true) {
                    val b = queue.poll() ?: break
                    b.recycle()
                }
            }
            buckets.clear()
            currentPoolSizeBytes = 0
        }
    }

    private fun recalculatePoolSize() {
        var size = 0L
        buckets.values.forEach { queue ->
            queue.forEach { bitmap ->
                if (!bitmap.isRecycled) size += bitmap.allocationByteCount
            }
        }
        currentPoolSizeBytes = size
    }

    private fun makeKey(width: Int, height: Int, config: Bitmap.Config): String =
        "${width}x${height}_${config.name}"
}
