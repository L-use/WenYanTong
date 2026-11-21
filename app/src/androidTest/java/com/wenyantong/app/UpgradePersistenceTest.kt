package com.wenyantong.app

import androidx.test.platform.app.InstrumentationRegistry
import com.wenyantong.app.core.storage.LocalRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class UpgradePersistenceTest {
    @Test fun initialVersionFavoritesAndWrongAnswersSurviveReleaseUpgrade() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("upgrade")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repository=LocalRepository(context)
        try {
            repository.initialize()
            assertTrue(repository.history().any { it.query=="hello" && it.favorite })
            assertTrue(repository.wrongCount()>0)
            assertEquals(5,BuildConfig.VERSION_CODE)
            assertEquals(0,context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)
        } finally { repository.close() }
    }
}
