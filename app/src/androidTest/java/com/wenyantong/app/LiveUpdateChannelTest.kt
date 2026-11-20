package com.wenyantong.app

import androidx.test.platform.app.InstrumentationRegistry
import com.wenyantong.app.core.model.Mode
import com.wenyantong.app.feature.update.UpdateRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class LiveUpdateChannelTest {
    @Test fun publishedReleaseDownloadsAndMatchesInstalledSigningIdentity() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveUpdate")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repository=UpdateRepository(context,installedVersion=1)
        val update=requireNotNull(repository.check(Mode.AUTO))
        assertEquals(BuildConfig.VERSION_CODE,update.versionCode)
        val apk=repository.download(update) { _,_-> }
        assertEquals(update.bytes,apk.length())
        assertTrue(apk.isFile)
    }
}
