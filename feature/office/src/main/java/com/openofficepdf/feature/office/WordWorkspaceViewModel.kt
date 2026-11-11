package com.openofficepdf.feature.office

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.model.DocumentId
import com.openofficepdf.core.model.SessionId
import com.openofficepdf.engine.office.OfficeEngine
import com.openofficepdf.engine.office.OfficeSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 视图呈现模式
 */
enum class WordLayoutViewMode {
    PRINT_LAYOUT, // 原始打印视图 (包含完整页眉、页脚、固定 A4 页面边界)
    MOBILE_REFLOW // 移动端自适应流式排版 (字体自适应、无横向滚动、边距收紧)
}

data class WordWorkspaceUiState(
    val sessionId: SessionId? = null,
    val documentTitle: String = "未命名文档.docx",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val viewMode: WordLayoutViewMode = WordLayoutViewMode.MOBILE_REFLOW,
    val zoomScale: Float = 1.0f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val errorMessage: String? = null,
    val toastMessage: String? = null
)

class WordWorkspaceViewModel(
    private val officeEngine: OfficeEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(WordWorkspaceUiState())
    val uiState: StateFlow<WordWorkspaceUiState> = _uiState.asStateFlow()

    fun openDocument(docId: DocumentId, title: String, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, documentTitle = title)

            when (val result = officeEngine.openSession(docId, uri)) {
                is EngineResult.Success -> {
                    val session = result.data
                    // 默认开启移动端自适应视图，提升小屏阅读体验
                    officeEngine.setMobileReflowMode(session.sessionId, true)
                    _uiState.value = _uiState.value.copy(
                        sessionId = session.sessionId,
                        isLoading = false,
                        viewMode = WordLayoutViewMode.MOBILE_REFLOW
                    )
                }
                is EngineResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "打开文档失败: ${result.errorMessage}"
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
        }
    }

    /**
     * 核心功能：自适应流式视图与打印排版视图切换 (任务书 2.2 节)
     */
    fun toggleViewMode() {
        val currentSession = _uiState.value.sessionId ?: return
        val targetMode = if (_uiState.value.viewMode == WordLayoutViewMode.MOBILE_REFLOW) {
            WordLayoutViewMode.PRINT_LAYOUT
        } else {
            WordLayoutViewMode.MOBILE_REFLOW
        }

        viewModelScope.launch {
            val enableReflow = (targetMode == WordLayoutViewMode.MOBILE_REFLOW)
            officeEngine.setMobileReflowMode(currentSession, enableReflow)
            _uiState.value = _uiState.value.copy(
                viewMode = targetMode,
                toastMessage = if (enableReflow) "已切换至移动自适应视图" else "已切换至原始打印视图"
            )
        }
    }

    /**
     * 排版命令派发 (加粗、斜体、下划线、插入表格等)
     */
    fun toggleBold() {
        val session = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            officeEngine.executeCommand(session, ".uno:Bold", "{}")
            _uiState.value = _uiState.value.copy(isBold = !_uiState.value.isBold, isDirty = true)
        }
    }

    fun toggleItalic() {
        val session = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            officeEngine.executeCommand(session, ".uno:Italic", "{}")
            _uiState.value = _uiState.value.copy(isItalic = !_uiState.value.isItalic, isDirty = true)
        }
    }

    fun toggleUnderline() {
        val session = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            officeEngine.executeCommand(session, ".uno:Underline", "{}")
            _uiState.value = _uiState.value.copy(isUnderline = !_uiState.value.isUnderline, isDirty = true)
        }
    }

    fun insertTable(rows: Int = 3, cols: Int = 3) {
        val session = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            val params = """{"Rows":{"type":"long","value":$rows},"Columns":{"type":"long","value":$cols}}"""
            officeEngine.executeCommand(session, ".uno:InsertTable", params)
            _uiState.value = _uiState.value.copy(isDirty = true, toastMessage = "已插入 ${rows}x${cols} 表格")
        }
    }

    /**
     * 事务级保存
     */
    fun saveDocument() {
        val session = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            when (val result = officeEngine.saveSession(session)) {
                is EngineResult.Success -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, isDirty = false, toastMessage = "文档已安全保存")
                }
                is EngineResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = "保存失败: ${result.errorMessage}")
                }
                else -> {
                    _uiState.value = _uiState.value.copy(isSaving = false)
                }
            }
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
