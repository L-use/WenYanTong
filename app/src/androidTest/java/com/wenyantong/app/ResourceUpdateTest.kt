package com.wenyantong.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.wenyantong.app.core.storage.LocalRepository
import com.wenyantong.app.feature.resources.ResourceStore
import com.wenyantong.app.feature.update.UpdateTransport
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ResourceUpdateTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun archive(revision: Int=1, extra: String?=null, badHash: Boolean=false): ByteArray {
        val m=JSONObject(context.assets.open("packs/manifest.json").bufferedReader().use { it.readText() })
        m.put("revision",revision).put("version","1.0.0").put("minAppVersionCode",3)
        if(badHash)m.getJSONArray("files").getJSONObject(0).put("sha256","0".repeat(64))
        val output=ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"));zip.write(m.toString().toByteArray());zip.closeEntry()
            zip.putNextEntry(ZipEntry("sample.db"));context.assets.open("packs/sample.db").use { it.copyTo(zip) };zip.closeEntry()
            if(extra!=null) { zip.putNextEntry(ZipEntry(extra));zip.write(byteArrayOf(1));zip.closeEntry() }
        }
        return output.toByteArray()
    }
    @Test fun installReloadAndReopenPreservesPersonalHistory() = runBlocking {
        val prefs=context.getSharedPreferences("resource_updates",0);val old=prefs.getString("active",null)
        val store=ResourceStore(context);var repo=LocalRepository(context)
        try {
            prefs.edit().clear().commit();repo.initialize()
            repo.query("资源更新保留历史",com.wenyantong.app.core.model.Direction.CLASSICAL_TO_ZH)
            val prepared=store.prepare(archive().inputStream(),0)
            repo.installResource(prepared)
            assertEquals(1,repo.manifest.getInt("revision"));assertTrue(repo.entries().isNotEmpty())
            assertTrue(repo.history().any { it.query=="资源更新保留历史" })
            repo.close();repo=LocalRepository(context);repo.initialize()
            assertEquals("1.0.0",repo.manifest.getString("version"))
            assertTrue(repo.history().any { it.query=="资源更新保留历史" })
        } finally { repo.close();prefs.edit().putString("active",old).commit() }
    }
    @Test fun invalidArchivesNeverChangeActiveLibrary() {
        val store=ResourceStore(context);val prefs=context.getSharedPreferences("resource_updates",0)
        val old=prefs.getString("active",null)
        for(bytes in listOf(archive(extra="../escape.db"),archive(badHash=true),archive(extra="unknown"),byteArrayOf(1,2,3))) {
            try { store.prepare(bytes.inputStream(),0);fail("Invalid resource accepted") } catch(expected: Exception) { }
            assertEquals(old,prefs.getString("active",null))
        }
        try { store.prepare(archive().inputStream(),1);fail("Downgrade accepted") } catch(expected: Exception) { }
    }
    @Test fun indexRejectsForeignSourceAndIncompatibleApplication() = runBlocking {
        val base=JSONObject().put("schemaVersion",1).put("id","sample.basic").put("version","1.0.0").put("revision",1)
            .put("bytes",10).put("sha256","a".repeat(64)).put("notes","test").put("minAppVersionCode",3)
            .put("url","https://github.com/L-use/WenYanTong/releases/download/resource-1.0.0/common-basic-1.0.0.zip")
        val store=ResourceStore(context,UpdateTransport { base.toString().byteInputStream() })
        assertEquals(1,store.check().revision)
        base.put("url","https://example.com/library.zip")
        try { store.check();fail("Foreign resource accepted") } catch(expected: Exception) { }
        base.put("minAppVersionCode",9999)
        try { store.check();fail("Incompatible resource accepted") } catch(expected: Exception) { }
    }
    @Test fun corruptDownloadsAndTruncatedDownloadsAreRejected() = runBlocking {
        val bytes=archive()
        val offer=com.wenyantong.app.feature.resources.ResourceOffer("1.0.0",1,
            "https://github.com/L-use/WenYanTong/releases/download/resource-1.0.0/common.zip",bytes.size.toLong(),"0".repeat(64),"test",3)
        val store=ResourceStore(context,UpdateTransport { bytes.inputStream() })
        try { store.download(offer) { };fail("Corrupt download accepted") } catch(expected: Exception) { }
        try { store.download(offer.copy(bytes=bytes.size+1L)) { };fail("Truncated download accepted") } catch(expected: Exception) { }
    }
    @Test fun livePublishedResourceCanDownloadAndInstall() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveResources")=="true")
        val store=ResourceStore(context);val offer=store.check()
        val file=store.download(offer) { }
        try {
            val prepared=file.inputStream().use { store.prepare(it,0,offer) }
            assertEquals(32,prepared.manifest.getInt("entryCount"))
            val prefs=context.getSharedPreferences("resource_updates",0);val old=prefs.getString("active",null)
            val repo=LocalRepository(context)
            try {
                repo.initialize();repo.installResource(prepared)
                assertTrue(repo.entries().any { it.headword=="濯" })
                assertNotNull(repo.query("山不在高，有仙则名。",com.wenyantong.app.core.model.Direction.CLASSICAL_TO_ZH).passage)
                assertEquals(34,repo.questions().size)
            } finally { repo.close();prefs.edit().putString("active",old).commit() }
        } finally { file.delete() }
    }
}
