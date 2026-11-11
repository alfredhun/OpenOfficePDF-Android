package com.openofficepdf.core.ipc

import android.graphics.Bitmap
import android.os.Build
import android.os.SharedMemory
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer

/**
 * 基于 SharedMemory 的跨进程位图零拷贝共享通道 (任务书 3.2 节 & 8.2.2 节)
 * 彻底规避 Android Binder 1MB 事务缓冲区上限限制
 */
object SharedMemoryBitmapChannel {

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    fun writeBitmapToSharedMemory(bitmap: Bitmap, name: String = "tile_ashmem"): Pair<SharedMemory, Int> {
        val byteCount = bitmap.allocationByteCount
        val sharedMemory = SharedMemory.create(name, byteCount)
        val buffer = sharedMemory.mapReadWrite()
        try {
            bitmap.copyPixelsToBuffer(buffer)
            return Pair(sharedMemory, byteCount)
        } finally {
            SharedMemory.unmap(buffer)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    fun readBitmapFromSharedMemory(
        sharedMemory: SharedMemory,
        targetBitmap: Bitmap
    ) {
        val buffer = sharedMemory.mapReadOnly()
        try {
            targetBitmap.copyPixelsFromBuffer(buffer)
        } finally {
            SharedMemory.unmap(buffer)
        }
    }
}
