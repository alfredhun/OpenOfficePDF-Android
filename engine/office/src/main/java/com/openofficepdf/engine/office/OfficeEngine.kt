package com.openofficepdf.engine.office

import android.net.Uri
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.model.DocumentId
import com.openofficepdf.core.model.SessionId
import com.openofficepdf.engine.office.openxml.ParsedDocxDocument
import com.openofficepdf.engine.office.openxml.ParsedPptxDocument
import com.openofficepdf.engine.office.openxml.ParsedXlsxDocument

data class OfficeSession(
    val sessionId: SessionId,
    val documentId: DocumentId,
    val nativeDocHandle: Int,
    val isReflowSupported: Boolean,
    val parsedDocx: ParsedDocxDocument? = null,
    val parsedXlsx: ParsedXlsxDocument? = null,
    val parsedPptx: ParsedPptxDocument? = null
)

/**
 * Word 与 Excel 跨进程核心引擎接口 (任务书 7.3 节)
 */
interface OfficeEngine {
    suspend fun openSession(documentId: DocumentId, fileUri: Uri? = null): EngineResult<OfficeSession>
    suspend fun getParsedDocx(sessionId: SessionId): ParsedDocxDocument?
    suspend fun getParsedXlsx(sessionId: SessionId): ParsedXlsxDocument?
    suspend fun getParsedPptx(sessionId: SessionId): ParsedPptxDocument?
    suspend fun setMobileReflowMode(sessionId: SessionId, enabled: Boolean): EngineResult<Unit>
    suspend fun dispatchKeyEvent(sessionId: SessionId, keyCode: Int, unicodeChar: Int): EngineResult<Unit>
    suspend fun executeCommand(sessionId: SessionId, unoCommand: String, paramsJson: String): EngineResult<Unit>
    suspend fun saveSession(sessionId: SessionId, targetUri: Uri? = null): EngineResult<Unit>
    suspend fun exportToPdf(sessionId: SessionId, outputUri: Uri): EngineResult<Unit>
    suspend fun closeSession(sessionId: SessionId): EngineResult<Unit>
}
