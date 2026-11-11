package com.openofficepdf.core.model.pdf

import android.graphics.RectF

data class SearchMatch(
    val pageIndex: Int,
    val bounds: RectF,           // Normalized Page Space (pt)
    val snippet: String,
    val matchIndexInPage: Int
)

data class TextEditOutcome(
    val updatedObject: PdfTextObject,
    val fontFallbackTriggered: Boolean,
    val fallbackFontName: String?
)

/**
 * 闭环脱敏安全核查报告 (任务书 2.1.3 与 7.2 节)
 */
data class RedactionVerificationReport(
    val removedTextObjectCount: Int,
    val sanitizedImageCount: Int,
    val postSaveSearchVerifiedClean: Boolean
)

data class EditStateSnapshot(
    val canUndo: Boolean,
    val canRedo: Boolean,
    val lastAppliedOpId: String?
)
