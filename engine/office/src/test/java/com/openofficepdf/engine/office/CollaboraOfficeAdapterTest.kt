package com.openofficepdf.engine.office

import android.net.Uri
import com.openofficepdf.core.common.EngineResult
import com.openofficepdf.core.database.dao.SessionAndEditDao
import com.openofficepdf.core.database.entity.DocumentSessionEntity
import com.openofficepdf.core.database.entity.EditOperationEntity
import com.openofficepdf.core.model.DocumentFormat
import com.openofficepdf.core.model.DocumentId
import com.openofficepdf.core.model.SessionId
import com.openofficepdf.core.storage.DocumentAccessProvider
import com.openofficepdf.core.storage.TemporaryFileManager
import com.openofficepdf.core.storage.TransactionalFileCommitter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class CollaboraOfficeAdapterTest {

    private lateinit var adapter: CollaboraOfficeAdapter
    private lateinit var mockStorageProvider: DocumentAccessProvider
    private lateinit var mockTempFileManager: TemporaryFileManager
    private lateinit var mockCommitter: TransactionalFileCommitter
    private lateinit var mockSessionDao: SessionAndEditDao

    @Before
    fun setUp() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "office_test")
        tempDir.mkdirs()

        mockStorageProvider = object : DocumentAccessProvider {
            override suspend fun checkFileAccess(uri: Uri): EngineResult<Unit> = EngineResult.Success(Unit)
            override fun openReadOnlyStream(uri: Uri): InputStream =
                ByteArrayInputStream("PK\u0003\u0004mock_docx_bytes".toByteArray())
            override suspend fun acquirePosixWorkingCopy(uri: Uri, sessionId: String): EngineResult<WorkingCopyHandle> =
                EngineResult.Success(WorkingCopyHandle(File(tempDir, "mock.docx"), isTemporary = true))
        }

        mockTempFileManager = object : TemporaryFileManager() {
            override fun createStagingCopy(sessionId: SessionId, prefix: String): EngineResult<File> {
                val f = File(tempDir, "${prefix}${sessionId.value}.docx")
                f.writeText("temp")
                return EngineResult.Success(f)
            }
            override fun getTemporaryFile(relativePath: String): File = File(tempDir, relativePath)
            override fun releaseSessionStaging(sessionId: SessionId): Long = 0L
            override fun pinFile(file: File) {}
            override fun unpinFile(file: File) {}
        }

        mockCommitter = object : TransactionalFileCommitter() {
            override suspend fun commitTransaction(
                sessionId: SessionId,
                stagingFile: File,
                destinationUri: Uri,
                format: DocumentFormat
            ): EngineResult<Unit> = EngineResult.Success(Unit)
        }

        mockSessionDao = object : SessionAndEditDao {
            override suspend fun insertSession(session: DocumentSessionEntity) {}
            override suspend fun getSession(sessionId: String): DocumentSessionEntity? = null
            override suspend fun markSessionDirty(sessionId: String, isDirty: Boolean) {}
            override suspend fun deleteSession(sessionId: String) {}
            override suspend fun insertEditOperation(operation: EditOperationEntity) {}
            override suspend fun getOperationsForSession(sessionId: String): List<EditOperationEntity> = emptyList()
            override suspend fun deleteOperationsForSession(sessionId: String) {}
        }

        adapter = CollaboraOfficeAdapter(
            storageProvider = mockStorageProvider,
            tempFileManager = mockTempFileManager,
            committer = mockCommitter,
            sessionDao = mockSessionDao
        )
    }

    @Test
    fun testOpenSessionAndToggleReflow() = runBlocking {
        val docId = DocumentId("doc_word_001")
        val fileUri = Uri.parse("content://mock/word.docx")

        val openResult = adapter.openSession(docId, fileUri)
        assertTrue(openResult is EngineResult.Success)

        val session = (openResult as EngineResult.Success).data
        assertEquals(docId, session.documentId)
        assertTrue(session.isReflowSupported)

        // 测试开启移动自适应视图
        val reflowResult = adapter.setMobileReflowMode(session.sessionId, true)
        assertTrue(reflowResult is EngineResult.Success)

        // 测试事务安全保存
        val saveResult = adapter.saveSession(session.sessionId)
        assertTrue(saveResult is EngineResult.Success)

        // 测试释放会话
        val closeResult = adapter.closeSession(session.sessionId)
        assertTrue(closeResult is EngineResult.Success)
    }
}
