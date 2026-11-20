package com.wenyantong.app

import androidx.test.platform.app.InstrumentationRegistry
import com.wenyantong.app.core.model.Mode
import com.wenyantong.app.feature.update.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class UpdateRepositoryTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun offlineConsentIsEnforcedBeforeOpeningAnyConnection() = runBlocking {
        var connections=0
        val repository=UpdateRepository(context,UpdateTransport { connections++;error("Network should not open") })
        assertTrue(runCatching { repository.check(Mode.OFFLINE) }.exceptionOrNull() is OfflineUpdateConsentRequired)
        assertEquals(0,connections)
    }
    @Test fun wrongHashNeverProducesAnInstallableApk() = runBlocking {
        val bytes="not an APK".toByteArray()
        val repository=UpdateRepository(context,UpdateTransport { ByteArrayInputStream(bytes) },1)
        val update=AppUpdate(2,"0.2.0","v0.2.0","https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/download/v0.2.0/test.apk",bytes.size.toLong(),"0".repeat(64),26,"notes","https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases/tag/v0.2.0")
        assertTrue(runCatching { repository.download(update) { _,_-> } }.isFailure)
        assertFalse(File(context.cacheDir,"app-updates/WenYanTong-2.apk").exists())
    }
    @Test fun installedApkSignatureAndVersionAreVerified() {
        ApkUpdateVerifier.verify(context,File(context.packageCodePath),BuildConfig.VERSION_CODE)
        assertTrue(runCatching { ApkUpdateVerifier.verify(context,File(context.packageCodePath),BuildConfig.VERSION_CODE+1) }.isFailure)
    }
}
