package com.openofficepdf.core.model

import java.util.UUID

@JvmInline
value class DocumentId(val value: String) {
    companion object {
        fun generate(): DocumentId = DocumentId(UUID.randomUUID().toString())
    }
}

@JvmInline
value class SessionId(val value: String) {
    companion object {
        fun generate(): SessionId = SessionId(UUID.randomUUID().toString())
    }
}

enum class DocumentFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    MD("md", "text/markdown"),
    TXT("txt", "text/plain"),
    HTML("html", "text/html"),
    PNG("png", "image/png"),
    JPG("jpg", "image/jpeg"),
    IMAGE("image", "image/*");

    companion object {
        fun fromExtension(ext: String): DocumentFormat? {
            val normalized = ext.lowercase().trimStart('.')
            return entries.find { it.extension == normalized }
        }
    }
}

enum class EngineType {
    PDF,
    OFFICE,
    MARKDOWN,
    OCR,
    CONVERTER
}
