package com.wenyantong.app

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.wenyantong.app.core.storage.LocalRepository
import com.wenyantong.app.feature.resources.ResourceStore
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MultiPackTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(id: String,title: String,gloss: String): ByteArray {
        val path=File(context.cacheDir,"fixture-$id.db");path.delete()
        SQLiteDatabase.openOrCreateDatabase(path,null).use { db ->
            db.version=2
            db.execSQL("CREATE TABLE source(id TEXT PRIMARY KEY,title TEXT,license TEXT,review_status TEXT)")
            db.execSQL("CREATE TABLE dict_entry(id TEXT PRIMARY KEY,headword TEXT,pinyin TEXT,summary TEXT)")
            db.execSQL("CREATE TABLE dict_sense(id TEXT PRIMARY KEY,entry_id TEXT REFERENCES dict_entry(id),gloss TEXT,pos TEXT,example TEXT,translation TEXT,reference TEXT)")
            db.execSQL("CREATE TABLE dict_alias(alias TEXT,entry_id TEXT REFERENCES dict_entry(id),kind TEXT)")
            db.execSQL("CREATE TABLE passage(id TEXT,original TEXT,translation TEXT,direction TEXT,work TEXT,analysis TEXT,entry_ids TEXT,normalized TEXT)")
            db.execSQL("CREATE TABLE question(id TEXT,stem TEXT,options_json TEXT,answer INTEGER,explanation TEXT,entry_id TEXT)")
            db.execSQL("INSERT INTO dict_entry VALUES(?,?,?,?)",arrayOf("$id:entry","测","cè",gloss))
            db.execSQL("INSERT INTO dict_sense VALUES(?,?,?,?,?,?,?)",arrayOf("$id:sense","$id:entry",gloss,"动词","","","测试出处"))
            db.execSQL("INSERT INTO dict_alias VALUES(?,?,?)",arrayOf("測","$id:entry","variant"))
        }
        val manifest=JSONObject().put("id",id).put("title",title).put("version","1.0.0").put("revision",1).put("schemaVersion",2)
            .put("minAppVersionCode",4).put("database","sample.db").put("entryCount",1).put("passageCount",0).put("questionCount",0)
            .put("files",JSONArray().put(JSONObject().put("path","sample.db").put("bytes",path.length()).put("sha256",ResourceStore.hash(path))))
        val output=ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("sample.db"));zip.write(path.readBytes());zip.closeEntry()
            zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifest.toString().toByteArray());zip.closeEntry()
        };path.delete();return output.toByteArray()
    }
    @Test fun independentlyInstalledPacksSearchTogetherAndRemovalKeepsOtherPack() = runBlocking {
        val prefs=context.getSharedPreferences("resource_updates",0)
        val old=prefs.getString("installed",null);val legacy=prefs.getString("active",null)
        val repo=LocalRepository(context);val store=ResourceStore(context)
        try {
            prefs.edit().clear().commit();repo.initialize()
            for((id,title,gloss) in listOf(Triple("test.one","一号包","测量"),Triple("test.two","二号包","推测"))) {
                val prepared=store.prepare(fixture(id,title,gloss).inputStream(),0);repo.installResource(prepared)
            }
            assertEquals(2,repo.installedPacks().size)
            assertEquals(1,repo.entries().count { it.headword=="测" })
            val detail=requireNotNull(repo.entryDetails("测"));assertEquals(setOf("测量","推测"),detail.senses.map { it.gloss }.toSet())
            assertEquals(setOf("一号包","二号包"),detail.packs.toSet())
            assertTrue(repo.query("測",com.wenyantong.app.core.model.Direction.CLASSICAL_TO_ZH).entries.isNotEmpty())
            repo.removeResource("test.one")
            assertEquals(listOf("test.two"),repo.installedPacks().map { it.id })
            assertEquals("推测",repo.entryDetails("测")!!.senses.single().gloss)
            assertTrue(repo.history().any { it.query=="測" })
        } finally { repo.close();prefs.edit().clear().putString("installed",old).putString("active",legacy).commit() }
    }
    @Test fun legacySinglePackMigratesAndDoesNotReturnAfterRemoval() = runBlocking {
        val prefs=context.getSharedPreferences("resource_updates",0)
        val old=prefs.getString("installed",null);val active=prefs.getString("active",null)
        val folder=File(context.filesDir,"resources/installed-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        context.assets.open("packs/sample.db").use { input -> File(folder,"sample.db").outputStream().use { input.copyTo(it) } }
        val m=JSONObject(context.assets.open("packs/manifest.json").bufferedReader().use { it.readText() }).put("revision",1).put("version","1.0.0").put("minAppVersionCode",3)
        File(folder,"manifest.json").writeText(m.toString())
        val repo=LocalRepository(context)
        try {
            prefs.edit().clear().putString("active",folder.name).commit();repo.initialize()
            assertEquals("1.0.0",repo.manifest.getString("version"));assertEquals(1,repo.installedPacks().size)
            repo.removeResource("sample.basic");repo.initialize()
            assertTrue(repo.installedPacks().isEmpty());assertEquals("0.1.0",repo.manifest.getString("version"))
        } finally { repo.close();prefs.edit().clear().putString("installed",old).putString("active",active).commit() }
    }
}
