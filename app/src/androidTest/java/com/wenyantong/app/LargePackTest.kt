package com.wenyantong.app

import androidx.test.platform.app.InstrumentationRegistry
import com.wenyantong.app.core.storage.LocalRepository
import com.wenyantong.app.feature.resources.ResourceStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LargePackTest {
    @Test fun allRealLocalPacksInstallIndependentlyAndSearchWithSources() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("localPacks")=="true")
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        fun readFile(name: String): java.io.InputStream {
            require(name.matches(Regex("[a-zA-Z0-9.-]+")))
            return android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cat /data/local/tmp/wenyantong-packs/$name"))
        }
        val prefs=context.getSharedPreferences("resource_updates",0)
        val previous=prefs.getString("installed",null);val legacy=prefs.getString("active",null)
        val repo=LocalRepository(context);val store=ResourceStore(context)
        try {
            prefs.edit().clear().commit();repo.initialize()
            val catalog=readFile("catalog-v2.json").bufferedReader().use { JSONObject(it.readText()) }.getJSONArray("packs")
            for(index in 0 until catalog.length()) {
                val offer=catalog.getJSONObject(index);val name=offer.getString("url").substringAfterLast('/')
                val prepared=readFile(name).use { store.prepare(it,0) };repo.installResource(prepared)
                assertEquals(index+1,repo.installedPacks().size)
            }
            assertEquals(8,repo.installedPacks().size)
            assertTrue(repo.entries().size>14000)
            val zhi=requireNotNull(repo.entryDetails("之"))
            assertTrue(zhi.packs.size>=3)
            assertTrue(zhi.senses.any { it.source.contains("github.com") })
            assertTrue(repo.query("学而时习之，不亦说乎？",com.wenyantong.app.core.model.Direction.CLASSICAL_TO_ZH).alternatives.isNotEmpty())
            val articles=repo.articles("reading.guwenguanzhi","师说",0)
            assertTrue(articles.isNotEmpty())
            val article=repo.article(articles.first())
            assertTrue(article.original.contains("学者"));assertTrue(article.translation.isNotBlank());assertTrue(article.source.contains("AI"))
            assertEquals(40,repo.articles("corpus.histories","",0).size)
            assertTrue(repo.articles("corpus.histories","",40).first().id!=repo.articles("corpus.histories","",0).first().id)
            repo.removeResource("corpus.histories")
            assertEquals(7,repo.installedPacks().size);assertNotNull(repo.entryDetails("之"))
        } finally { repo.close();prefs.edit().clear().putString("installed",previous).putString("active",legacy).commit() }
    }
    @Test fun liveCatalogDownloadsEveryIndependentPack() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveCatalog")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=ResourceStore(context);val offers=store.catalog()
        assertEquals(8,offers.size);assertEquals(8,offers.map { it.id }.toSet().size)
        for(offer in offers) {
            val file=store.download(offer) { }
            try {
                val prepared=file.inputStream().use { store.prepare(it,0,offer) }
                assertEquals(offer.id,prepared.manifest.getString("id"))
            } finally { file.delete() }
        }
    }
}
