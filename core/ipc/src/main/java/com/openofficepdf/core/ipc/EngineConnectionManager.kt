package com.openofficepdf.core.ipc

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 跨进程服务连接管理器 (任务书 3.2 节 & 8.2.2 节)
 * 绑定 linkToDeath 回调，子进程 SIGSEGV 崩溃时主进程不闪退，自动触发保护恢复流程
 */
class EngineConnectionManager(
    private val context: Context,
    private val serviceAction: String,
    private val servicePackage: String = context.packageName
) {
    companion object {
        private const val TAG = "EngineConnectionManager"
    }

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var remoteBinder: IBinder? = null
    var onProcessDiedListener: (() -> Unit)? = null

    private val deathRecipient = IBinder.DeathRecipient {
        Log.e(TAG, "Subprocess disconnected or died unexpectedly! Triggering recovery.")
        _isConnected.value = false
        remoteBinder = null
        onProcessDiedListener?.invoke()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "Subprocess service connected: $name")
            remoteBinder = service
            try {
                service?.linkToDeath(deathRecipient, 0)
                _isConnected.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to link death recipient: ${e.message}")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Subprocess service disconnected: $name")
            _isConnected.value = false
            remoteBinder = null
        }
    }

    fun bindService() {
        if (_isConnected.value) return
        val intent = Intent(serviceAction).apply {
            setPackage(servicePackage)
        }
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService() {
        if (_isConnected.value) {
            try {
                remoteBinder?.unlinkToDeath(deathRecipient, 0)
                context.unbindService(serviceConnection)
            } catch (e: Exception) {
                Log.e(TAG, "Unbind service failed: ${e.message}")
            } finally {
                _isConnected.value = false
                remoteBinder = null
            }
        }
    }

    fun getBinder(): IBinder? = remoteBinder
}
