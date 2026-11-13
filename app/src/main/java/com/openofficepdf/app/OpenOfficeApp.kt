package com.openofficepdf.app

import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.openofficepdf.core.memory.BitmapPool
import com.openofficepdf.core.storage.TemporaryFileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class OpenOfficeApp : Application() {

    lateinit var appContainer: AppContainer
        private set

    val bitmapPool: BitmapPool
        get() = appContainer.bitmapPool

    val temporaryFileManager: TemporaryFileManager
        get() = appContainer.tempFileManager

    override fun onCreate() {
        super.onCreate()
        
        // 全局未捕获异常兜底，防止旋转或格式解析异常导致闪退
        setupGlobalCrashHandler()

        // 研发十条军规：严禁在 Application.onCreate 同步加载重型 Native so 库或 AI 模型
        appContainer = AppContainer(this)

        // 异步后台回收孤儿临时文件
        Thread {
            temporaryFileManager.cleanOrphanFiles()
        }.start()
    }

    private fun setupGlobalCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("OpenOfficeApp", "CRASH_PREVENTED: Uncaught exception in thread ${thread.name}", throwable)
            // 如果是在主线程发生的非致命 Compose/Measure 异常，记录日志并避免进程硬退出
            try {
                GlobalScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@OpenOfficeApp, "已拦截运行异常: ${throwable.localizedMessage ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            } catch (_: Throwable) { }

            // 对于严重的系统级 Error 交由默认处理
            if (throwable is VirtualMachineError) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
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
