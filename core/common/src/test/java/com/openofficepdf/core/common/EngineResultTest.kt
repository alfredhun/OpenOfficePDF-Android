package com.openofficepdf.core.common

import org.junit.Assert.*
import org.junit.Test

class EngineResultTest {

    @Test
    fun testSuccessState() {
        val result: EngineResult<String> = EngineResult.Success("test_data")
        assertTrue(result.isSuccess)
        assertFalse(result.isFailure)
        assertEquals("test_data", result.getOrNull())
        assertEquals("test_data", result.getOrThrow())
    }

    @Test
    fun testFailureState() {
        val result: EngineResult<String> = EngineResult.Failure(
            code = EngineErrorCode.FILE_NOT_FOUND,
            message = "Target document does not exist"
        )
        assertTrue(result.isFailure)
        assertFalse(result.isSuccess)
        assertNull(result.getOrNull())
        
        try {
            result.getOrThrow()
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("FILE_NOT_FOUND") == true)
        }
    }
}
