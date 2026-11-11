package com.openofficepdf.core.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TransactionalFileCommitterTest {

    @Test
    fun testPdfIntegrityCheckLogic() {
        val tempValidPdf = File.createTempFile("test_valid", ".pdf").apply {
            writeBytes("%PDF-1.7\n1 0 obj\n<<>>\nendobj\nxref\ntrailer\n<<>>\nstartxref\n100\n%%EOF".toByteArray())
            deleteOnExit()
        }

        val tempCorruptPdf = File.createTempFile("test_corrupt", ".pdf").apply {
            writeBytes("THIS_IS_NOT_A_PDF_FILE".toByteArray())
            deleteOnExit()
        }

        val isHeaderValid = tempValidPdf.readBytes().take(5).toByteArray().decodeToString().startsWith("%PDF")
        val isTailValid = tempValidPdf.readText().contains("%%EOF")
        assertTrue(isHeaderValid && isTailValid)

        val isCorruptHeaderValid = tempCorruptPdf.readBytes().take(5).toByteArray().decodeToString().startsWith("%PDF")
        assertFalse(isCorruptHeaderValid)
    }
}
