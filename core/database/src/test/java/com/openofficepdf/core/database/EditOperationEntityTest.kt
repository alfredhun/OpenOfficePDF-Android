package com.openofficepdf.core.database

import com.openofficepdf.core.database.entity.EditOperationEntity
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

class EditOperationEntityTest {

    @Test
    fun testValidEditOperation() {
        val op = EditOperationEntity(
            opId = "op_1",
            documentId = "doc_1",
            sessionId = "session_1",
            pageIndex = 0,
            targetObjectId = "obj_1",
            operationType = "UPDATE_TEXT",
            beforeMetaJson = "{\"text\":\"old\"}",
            afterMetaJson = "{\"text\":\"new\"}",
            sequenceNumber = 1,
            createdAt = System.currentTimeMillis()
        )
        assertNotNull(op)
    }

    @Test
    fun testRejectLargeMetaJsonOver64KB() {
        // 创建超过 64KB 的垃圾文本
        val largeJson = "a".repeat(65 * 1024)
        try {
            EditOperationEntity(
                opId = "op_large",
                documentId = "doc_1",
                sessionId = "session_1",
                pageIndex = 0,
                targetObjectId = "obj_1",
                operationType = "UPDATE_TEXT",
                beforeMetaJson = largeJson,
                afterMetaJson = "{}",
                sequenceNumber = 1,
                createdAt = System.currentTimeMillis()
            )
            fail("Expected IllegalArgumentException because JSON exceeds 64KB CursorWindow limit")
        } catch (e: IllegalArgumentException) {
            // Success
        }
    }
}
