package com.wenyantong.app

import androidx.test.platform.app.InstrumentationRegistry
import com.wenyantong.app.core.model.*
import com.wenyantong.app.core.storage.LocalRepository
import com.wenyantong.app.feature.storage.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class UserBackupTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun exportImportRoundTripIsMergeSafeAndIdempotent() = runBlocking {
        val repository=LocalRepository(context)
        try {
            repository.initialize()
            repository.query("backup-roundtrip-marker",Direction.EN_TO_ZH)
            val item=repository.history().first { it.query=="backup-roundtrip-marker" }
            repository.favorite(item.id,true)
            val question=repository.questions().first()
            repository.recordAnswer("backup-roundtrip",question,(question.answer+1)%question.options.size)
            val backup=UserBackupCodec.decode(repository.exportUserData())
            assertTrue(backup.history.any { it.query==item.query && it.favorite })
            assertTrue(backup.answers.any { it.sessionId=="backup-roundtrip" })
            repository.deleteHistory(item.id)
            repository.setReading("light",16f)
            repository.importUserData(backup)
            val first=UserBackupCodec.decode(repository.exportUserData())
            repository.importUserData(backup)
            val second=UserBackupCodec.decode(repository.exportUserData())
            assertEquals(first.history,second.history)
            assertEquals(first.answers,second.answers)
            assertEquals(first.wrong,second.wrong)
            assertEquals(backup.preferences,second.preferences)
            assertTrue(repository.history().first { it.query==item.query }.favorite)
        } finally { repository.close() }
    }
    @Test fun corruptBackupsAreRejectedBeforeExistingDataChanges() = runBlocking {
        val repository=LocalRepository(context)
        try {
            repository.initialize()
            val before=repository.history()
            val valid=repository.exportUserData()
            val malformed=JSONObject(valid.toString(Charsets.UTF_8)).put("schemaVersion",999).toString().toByteArray()
            assertTrue(runCatching { UserBackupCodec.decode(malformed) }.isFailure)
            assertTrue(runCatching { UserBackupCodec.decode("not-json".toByteArray()) }.isFailure)
            assertTrue(runCatching { UserBackupCodec.decode(ByteArray(UserBackupCodec.MAX_BYTES+1)) }.isFailure)
            assertEquals(before,repository.history())
            val exported=valid.toString(Charsets.UTF_8)
            assertFalse(exported.contains("secretAlias"))
            assertFalse(exported.contains("apiKey"))
        } finally { repository.close() }
    }
}
