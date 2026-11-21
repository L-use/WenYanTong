package com.wenyantong.app

import androidx.test.platform.app.InstrumentationRegistry
import com.wenyantong.app.core.storage.LocalRepository
import com.wenyantong.app.feature.resources.ResourceStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class EnglishPackImportTest {
    @Test fun englishPacksInstallSeparatelyAndKeepBothWordSources() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("englishPacks")=="true")
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("resource_updates",0)
        val old=prefs.getString("installed",null);val active=prefs.getString("active",null)
        val repo=LocalRepository(context);val store=ResourceStore(context)
        try {
            prefs.edit().clear().commit();repo.initialize()
            for(id in listOf("english.cet6.kyle","english.cet.examdata")) {
                val stream=android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/wenyantong-english/$id-1.0.0.zip"))
                val prepared=stream.use { store.prepare(it,0) };repo.installResource(prepared)
            }
            assertEquals(2,repo.installedPacks().size)
            assertEquals(3991,repo.installedPacks().first { it.id=="english.cet6.kyle" }.entries)
            assertEquals(5276,repo.installedPacks().first { it.id=="english.cet.examdata" }.entries)
            val entry=requireNotNull(repo.entryDetails("abandon"))
            assertEquals(2,entry.packs.size)
            assertTrue(entry.senses.any { it.source.contains("KyleBing") })
            assertTrue(entry.senses.any { it.source.contains("exam-data") })
            assertTrue(repo.entries().any { it.headword=="abandon" && "ABANDON" in it.aliases })
            assertTrue(repo.query("aBaNdOn",com.wenyantong.app.core.model.Direction.EN_TO_ZH).exactWordHit)
            repo.removeResource("english.cet.examdata")
            assertEquals(1,repo.installedPacks().size)
            assertNotNull(repo.entryDetails("abandon"))
        } finally { repo.close();prefs.edit().clear().putString("installed",old).putString("active",active).commit() }
    }
}
