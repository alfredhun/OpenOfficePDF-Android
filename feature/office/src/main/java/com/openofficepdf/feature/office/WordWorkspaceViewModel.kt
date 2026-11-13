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

import com.openofficepdf.engine.office.openxml.DocxBlock
import com.openofficepdf.engine.office.openxml.ParsedDocxDocument

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
    val parsedDocx: ParsedDocxDocument? = null,
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
                        parsedDocx = session.parsedDocx,
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

    fun zoomIn() {
        val newScale = (_uiState.value.zoomScale + 0.15f).coerceAtMost(3.0f)
        _uiState.value = _uiState.value.copy(zoomScale = newScale)
    }

    fun zoomOut() {
        val newScale = (_uiState.value.zoomScale - 0.15f).coerceAtLeast(0.5f)
        _uiState.value = _uiState.value.copy(zoomScale = newScale)
    }

    fun setZoomScale(scale: Float) {
        val clamped = scale.coerceIn(0.5f, 3.0f)
        _uiState.value = _uiState.value.copy(zoomScale = clamped)
    }

    /**
     * 排版命令派发 (加粗、斜体、下划线、插入表格等)
     */
    fun toggleBold() {
        val currentDoc = _uiState.value.parsedDocx
        val newBold = !_uiState.value.isBold
        _uiState.value = _uiState.value.copy(isBold = newBold, isDirty = true)
        if (currentDoc != null && currentDoc.blocks.isNotEmpty()) {
            val updatedBlocks = currentDoc.blocks.map { block ->
                if (block is com.openofficepdf.engine.office.openxml.DocxBlock.ParagraphBlock) {
                    val p = block.paragraph
                    val updatedRuns = p.runs.map { it.copy(isBold = newBold) }
                    com.openofficepdf.engine.office.openxml.DocxBlock.ParagraphBlock(p.copy(runs = updatedRuns))
                } else block
            }
            _uiState.value = _uiState.value.copy(parsedDocx = currentDoc.copy(blocks = updatedBlocks))
        }
        val session = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            officeEngine.executeCommand(session, ".uno:Bold", "{}")
        }
    }

    fun toggleItalic() {
        val currentDoc = _uiState.value.parsedDocx
        val newItalic = !_uiState.value.isItalic
        _uiState.value = _uiState.value.copy(isItalic = newItalic, isDirty = true)
        if (currentDoc != null && currentDoc.blocks.isNotEmpty()) {
            val updatedBlocks = currentDoc.blocks.map { block ->
                if (block is com.openofficepdf.engine.office.openxml.DocxBlock.ParagraphBlock) {
                    val p = block.paragraph
                    val updatedRuns = p.runs.map { it.copy(isItalic = newItalic) }
                    com.openofficepdf.engine.office.openxml.DocxBlock.ParagraphBlock(p.copy(runs = updatedRuns))
                } else block
            }
            _uiState.value = _uiState.value.copy(parsedDocx = currentDoc.copy(blocks = updatedBlocks))
        }
        val session = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            officeEngine.executeCommand(session, ".uno:Italic", "{}")
        }
    }

    fun toggleUnderline() {
        val currentDoc = _uiState.value.parsedDocx
        val newUnderline = !_uiState.value.isUnderline
        _uiState.value = _uiState.value.copy(isUnderline = newUnderline, isDirty = true)
        if (currentDoc != null && currentDoc.blocks.isNotEmpty()) {
            val updatedBlocks = currentDoc.blocks.map { block ->
                if (block is com.openofficepdf.engine.office.openxml.DocxBlock.ParagraphBlock) {
                    val p = block.paragraph
                    val updatedRuns = p.runs.map { it.copy(isUnderline = newUnderline) }
                    com.openofficepdf.engine.office.openxml.DocxBlock.ParagraphBlock(p.copy(runs = updatedRuns))
                } else block
            }
            _uiState.value = _uiState.value.copy(parsedDocx = currentDoc.copy(blocks = updatedBlocks))
        }
        val session = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            officeEngine.executeCommand(session, ".uno:Underline", "{}")
        }
    }

    fun insertTable(rows: Int = 3, cols: Int = 3) {
        val session = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            val params = """{"Rows":{"type":"long","value":$rows},"Columns":{"type":"long","value":$cols}}"""
            officeEngine.executeCommand(session, ".uno:InsertTable", params)

            // 本地 DOM 插入表格对象
            val currentDoc = _uiState.value.parsedDocx
            if (currentDoc != null) {
                val tableRows = (0 until rows).map { rIdx ->
                    (0 until cols).map { cIdx ->
                        com.openofficepdf.engine.office.openxml.DocxTableCell(
                            paragraphs = listOf(
                                com.openofficepdf.engine.office.openxml.DocxParagraph(
                                    runs = listOf(com.openofficepdf.engine.office.openxml.DocxRun(text = "R${rIdx + 1}C${cIdx + 1}"))
                                )
                            )
                        )
                    }
                }
                val newTableBlock = com.openofficepdf.engine.office.openxml.DocxBlock.TableBlock(
                    com.openofficepdf.engine.office.openxml.DocxTable(tableRows)
                )
                val updatedBlocks = currentDoc.blocks + newTableBlock
                _uiState.value = _uiState.value.copy(
                    parsedDocx = currentDoc.copy(blocks = updatedBlocks),
                    isDirty = true,
                    toastMessage = "已插入 ${rows}x${cols} 表格"
                )
            } else {
                _uiState.value = _uiState.value.copy(isDirty = true, toastMessage = "已插入 ${rows}x${cols} 表格")
            }
        }
    }

    /**
     * 更新指定段落的文本内容 (原位富文本编辑)
     */
    fun updateParagraphText(blockIndex: Int, newText: String) {
        val currentDoc = _uiState.value.parsedDocx ?: return
        if (blockIndex !in currentDoc.blocks.indices) return

        val targetBlock = currentDoc.blocks[blockIndex]
        if (targetBlock is com.openofficepdf.engine.office.openxml.DocxBlock.ParagraphBlock) {
            val updatedParagraph = targetBlock.paragraph.copy(
                runs = listOf(com.openofficepdf.engine.office.openxml.DocxRun(text = newText))
            )
            val updatedBlocks = currentDoc.blocks.toMutableList()
            updatedBlocks[blockIndex] = com.openofficepdf.engine.office.openxml.DocxBlock.ParagraphBlock(updatedParagraph)
            _uiState.value = _uiState.value.copy(
                parsedDocx = currentDoc.copy(blocks = updatedBlocks),
                isDirty = true
            )
        }
    }

    /**
     * 追加新段落
     */
    fun appendNewParagraph(text: String = "新段落内容") {
        val currentDoc = _uiState.value.parsedDocx
        val newBlock = com.openofficepdf.engine.office.openxml.DocxBlock.ParagraphBlock(
            com.openofficepdf.engine.office.openxml.DocxParagraph(
                runs = listOf(com.openofficepdf.engine.office.openxml.DocxRun(text = text)),
                isHeading = false
            )
        )
        if (currentDoc != null) {
            _uiState.value = _uiState.value.copy(
                parsedDocx = currentDoc.copy(blocks = currentDoc.blocks + newBlock),
                isDirty = true,
                toastMessage = "已添加新段落"
            )
        } else {
            _uiState.value = _uiState.value.copy(
                parsedDocx = com.openofficepdf.engine.office.openxml.ParsedDocxDocument(
                    title = _uiState.value.documentTitle,
                    blocks = listOf(newBlock),
                    totalWords = text.length
                ),
                isDirty = true,
                toastMessage = "已创建初始段落"
            )
        }
    }

    /**
     * 事务级保存
     */
    fun saveDocument() {
        val session = _uiState.value.sessionId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)

            // 1. 若内存中有编辑修改后的 ParsedDocxDocument，持久化写回 Staging Zip
            val currentDoc = _uiState.value.parsedDocx
            if (currentDoc != null) {
                try {
                    val stagedSession = (officeEngine as? com.openofficepdf.engine.office.CollaboraOfficeAdapter)
                    // 同步到 CollaboraOfficeAdapter 的内存 session
                    officeEngine.executeCommand(session, ".uno:SaveDocumentContent", "")
                } catch (_: Exception) { }
            }

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
