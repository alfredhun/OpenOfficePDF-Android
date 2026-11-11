package com.openofficepdf.app

import android.app.Application
import android.content.Context
import com.openofficepdf.core.memory.BitmapPool
import com.openofficepdf.core.storage.TemporaryFileManager

class OpenOfficeApp : Application() {

    lateinit var appContainer: AppContainer
        private set

    val bitmapPool: BitmapPool
        get() = appContainer.bitmapPool

    val temporaryFileManager: TemporaryFileManager
        get() = appContainer.tempFileManager

    override fun onCreate() {
        super.onCreate()
        
        // 研发十条军规：严禁在 Application.onCreate 同步加载重型 Native so 库或 AI 模型
        appContainer = AppContainer(this)

        // 异步后台回收孤儿临时文件
        Thread {
            temporaryFileManager.cleanOrphanFiles()
        }.start()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        bitmapPool.trimMemory(level)
    }

    companion object {
        fun get(context: Context): OpenOfficeApp =
            context.applicationContext as OpenOfficeApp
    }
}
