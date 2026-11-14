package com.openofficepdf.feature.markdown

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openofficepdf.core.common.AppDispatchers
import com.openofficepdf.core.database.dao.DocumentDao
import com.openofficepdf.core.database.entity.DocumentEntity
import com.openofficepdf.core.model.DocumentFormat
import com.openofficepdf.core.model.DocumentId
import com.openofficepdf.core.storage.DocumentAccessProvider
import com.openofficepdf.core.storage.TemporaryFileManager
import com.openofficepdf.core.storage.TransactionalFileCommitter
import com.openofficepdf.engine.markdown.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 屏幕布局模式
 */
enum class MarkdownDisplayMode {
    SPLIT_DUAL, // 平板/横屏：双栏同步对照 (左侧源码，右侧排版)
    SOURCE_ONLY, // 仅源码编辑
    PREVIEW_ONLY // 仅渲染预览
}

data class MarkdownUiState(
    val documentId: DocumentId? = null,
    val sourceUri: Uri? = null,
    val documentTitle: String = "未命名.md",
    val sourceContent: String = "",
    val parsedBlocks: List<MarkdownBlock> = emptyList(),
    val displayMode: MarkdownDisplayMode = MarkdownDisplayMode.SPLIT_DUAL,
    val isNightMode: Boolean = false,
    val isModified: Boolean = false,
    val isSaving: Boolean = false,
    val previewZoomScale: Float = 1.0f,
    val currentLineInEditor: Int = 0,
    val previewTargetIndex: Int = 0,
    val toastMessage: String? = null
)

class MarkdownWorkspaceViewModel(
    private val astParser: MarkdownAstParser,
    private val scrollSync: MarkdownScrollSyncAndSanitizer,
    private val mermaidPool: MermaidRenderPool,
    private val documentDao: DocumentDao,
    private val storageProvider: DocumentAccessProvider,
    private val tempFileManager: TemporaryFileManager,
    private val transactionalCommitter: TransactionalFileCommitter,
    private val context: Context? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarkdownUiState())
    val uiState: StateFlow<MarkdownUiState> = _uiState.asStateFlow()

    /**
     * 新建空白 Markdown 文档
     */
    fun createNewBlankDocument(title: String = "新建笔记.md") {
        _uiState.value = _uiState.value.copy(
            documentId = DocumentId("new_md_${System.currentTimeMillis()}"),
            sourceUri = null,
            documentTitle = title,
            sourceContent = "",
            parsedBlocks = emptyList(),
            isModified = true,
            toastMessage = "已新建空白 Markdown 笔记"
        )
    }

    fun loadDocument(docId: DocumentId, title: String, initialMarkdown: String) {
        val sanitized = scrollSync.sanitizeMarkdownText(initialMarkdown)
        val blocks = astParser.parseToBlocks(sanitized)
        _uiState.value = _uiState.value.copy(
            documentId = docId,
            documentTitle = title,
            sourceContent = sanitized,
            parsedBlocks = blocks,
            isModified = false
        )
    }

    /**
     * 通过 SAF Uri 打开真实外部 Markdown 文件
     */
    fun openFromUri(uri: Uri, title: String) {
        viewModelScope.launch {
            try {
                val content = withContext(AppDispatchers.io) {
                    context?.contentResolver?.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    } ?: ""
                }
                val sanitized = scrollSync.sanitizeMarkdownText(content)
                val blocks = astParser.parseToBlocks(sanitized)
                _uiState.value = _uiState.value.copy(
                    documentId = DocumentId(uri.toString()),
                    sourceUri = uri,
                    documentTitle = title,
                    sourceContent = sanitized,
                    parsedBlocks = blocks,
                    isModified = false,
                    toastMessage = "成功打开 $title"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(toastMessage = "读取文件失败: ${e.message}")
            }
        }
    }

    /**
     * 通过 SAF Uri 导出或保存 Markdown 文件
     */
    fun exportToUri(targetUri: Uri) {
        val content = _uiState.value.sourceContent
        _uiState.value = _uiState.value.copy(isSaving = true)

        viewModelScope.launch {
            try {
                withContext(AppDispatchers.io) {
                    context?.contentResolver?.openOutputStream(targetUri)?.use { out ->
                        out.write(content.toByteArray(Charsets.UTF_8))
                    }
                }
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    isModified = false,
                    toastMessage = "文件已成功保存落盘！"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    toastMessage = "保存失败: ${e.message}"
                )
            }
        }
    }

    fun onSourceChanged(newText: String) {
        val sanitized = scrollSync.sanitizeMarkdownText(newText)
        val blocks = astParser.parseToBlocks(sanitized)
        _uiState.value = _uiState.value.copy(
            sourceContent = sanitized,
            parsedBlocks = blocks,
            isModified = true
        )
    }

    fun toggleDisplayMode() {
        val next = when (_uiState.value.displayMode) {
            MarkdownDisplayMode.SPLIT_DUAL -> MarkdownDisplayMode.PREVIEW_ONLY
            MarkdownDisplayMode.PREVIEW_ONLY -> MarkdownDisplayMode.SOURCE_ONLY
            MarkdownDisplayMode.SOURCE_ONLY -> MarkdownDisplayMode.SPLIT_DUAL
        }
        _uiState.value = _uiState.value.copy(displayMode = next)
    }

    fun toggleNightMode() {
        _uiState.value = _uiState.value.copy(isNightMode = !_uiState.value.isNightMode)
    }

    fun zoomInPreview() {
        val next = (_uiState.value.previewZoomScale + 0.15f).coerceAtMost(2.5f)
        _uiState.value = _uiState.value.copy(previewZoomScale = next)
    }

    fun zoomOutPreview() {
        val next = (_uiState.value.previewZoomScale - 0.15f).coerceAtLeast(0.6f)
        _uiState.value = _uiState.value.copy(previewZoomScale = next)
    }

    fun setPreviewZoom(scale: Float) {
        val next = scale.coerceIn(0.6f, 2.5f)
        _uiState.value = _uiState.value.copy(previewZoomScale = next)
    }

    fun onEditorScrolledToLine(line: Int) {
        val targetIdx = scrollSync.findTargetBlockIndexForSourceLine(line, _uiState.value.parsedBlocks)
        _uiState.value = _uiState.value.copy(
            currentLineInEditor = line,
            previewTargetIndex = targetIdx
        )
    }

    fun saveDocument() {
        val state = _uiState.value
        val docId = state.documentId ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            documentDao.insertOrUpdate(
                DocumentEntity(
                    id = docId.value,
                    displayName = state.documentTitle,
                    mimeType = "text/markdown",
                    format = DocumentFormat.MD.name,
                    uri = state.sourceUri?.toString() ?: "memory://${docId.value}",
                    sizeBytes = state.sourceContent.toByteArray().size.toLong(),
                    createdAt = now,
                    modifiedAt = now,
                    lastOpenedAt = now
                )
            )
            _uiState.value = _uiState.value.copy(isSaving = false, isModified = false, toastMessage = "已保存到工作台记录")
        }
    }

    fun clearToast() {
        _uiState.value = _uiState.value.copy(toastMessage = null)
    }
}
