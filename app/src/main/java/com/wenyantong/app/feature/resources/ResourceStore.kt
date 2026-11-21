package com.wenyantong.app.feature.resources

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.wenyantong.app.BuildConfig
import com.wenyantong.app.feature.update.GitHubTransport
import com.wenyantong.app.feature.update.UpdatePolicy
import com.wenyantong.app.feature.update.UpdateTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

data class ResourceOffer(val version: String, val revision: Int, val url: String, val bytes: Long,
    val sha256: String, val notes: String, val minApp: Int, val id: String="sample.basic", val title: String="常用文言文基础库", val tag: String="resource-$version",
    val entryCount: Int=0, val articleCount: Int=0, val license: String="", val review: String="",val language: String=if(id.startsWith("english."))"en" else "zh",val category: String=resourceCategory(id))
data class InstalledPack(val id: String, val title: String, val version: String, val revision: Int, val entries: Int, val articles: Int, val review: String,val language: String=if(id.startsWith("english."))"en" else "zh",val category: String=resourceCategory(id))
fun resourceCategory(id: String): String=when { id.startsWith("english.")->"vocabulary";id.startsWith("dictionary.")->"dictionary";id.startsWith("reading.")->"reading";id.startsWith("corpus.")->"original";else->"basic" }
data class PreparedResource(val directory: File, val manifest: JSONObject)

/** One cumulative base library. User records live in another database and are never replaced. */
class ResourceStore(private val context: Context, private val transport: UpdateTransport = GitHubTransport()) {
    private val root=File(context.filesDir,"resources")
    private val prefs=context.getSharedPreferences("resource_updates",Context.MODE_PRIVATE)
    private val repository=BuildConfig.UPDATE_REPOSITORY
    companion object {
        const val MAX_BYTES=128L*1024*1024
        const val MAX_DOWNLOAD=64L*1024*1024
        private val checkedDatabases=java.util.concurrent.ConcurrentHashMap<String,String>()
        fun hash(file: File): String {
            val digest=MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input -> val buffer=ByteArray(8192);while(true) {
                val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)
            } }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
    private fun registry(): JSONObject {
        val saved=prefs.getString("installed",null)
        if(saved!=null)return JSONObject(saved)
        return JSONObject().apply { prefs.getString("active",null)?.let { put("sample.basic",it) } }
    }
    fun installed(): List<PreparedResource> {
        val registered=registry()
        return registered.keys().asSequence().mapNotNull { id -> runCatching {
            val name=registered.getString(id);require(name.matches(Regex("installed-[a-f0-9-]+")))
            val folder=File(root,name);val manifest=JSONObject(File(folder,"manifest.json").readText())
            require(manifest.getString("id")==id);validate(folder,manifest)
            PreparedResource(folder,manifest)
        }.getOrNull() }.toList()
    }
    fun active(): PreparedResource? = installed().firstOrNull { it.manifest.getString("id")=="sample.basic" }
    fun revision(id: String): Int = installed().firstOrNull { it.manifest.getString("id")==id }?.manifest?.getInt("revision") ?: 0
    fun activate(prepared: PreparedResource) {
        require(prepared.directory.parentFile?.canonicalFile==root.canonicalFile)
        val registered=registry();registered.put(prepared.manifest.getString("id"),prepared.directory.name)
        require(registered.length()<=20) { "最多同时安装 20 个资源包。" }
        check(prefs.edit().putString("installed",registered.toString()).commit()) { "无法保存资源版本。" }
    }
    fun remove(id: String) {
        val registered=registry();val name=registered.optString(id);registered.remove(id)
        check(prefs.edit().putString("installed",registered.toString()).commit())
        if(name.matches(Regex("installed-[a-f0-9-]+"))) File(root,name).deleteRecursively()
    }
    suspend fun catalog(): List<ResourceOffer> = withContext(Dispatchers.IO) {
        val json=transport.open("https://github.com/$repository/releases/download/resources-v2/resources.json").use {
            JSONObject(readBounded(it,256*1024).toString(Charsets.UTF_8))
        }
        require(json.getInt("schemaVersion")==2)
        val packs=json.getJSONArray("packs");require(packs.length() in 1..100)
        val ids=mutableSetOf<String>()
        (0 until packs.length()).map { index ->
            val item=packs.getJSONObject(index)
            val offer=ResourceOffer(item.getString("version"),item.getInt("revision"),item.getString("url"),item.getLong("bytes"),
                item.getString("sha256"),item.getString("notes"),item.getInt("minAppVersionCode"),item.getString("id"),item.getString("title"),item.getString("tag"),
                item.getInt("entryCount"),item.optInt("articleCount"),item.getString("license"),item.optString("reviewStatus"),item.optString("language",if(item.getString("id").startsWith("english."))"en" else "zh"),item.optString("category",resourceCategory(item.getString("id"))))
            require(ids.add(offer.id));validateOffer(offer);offer
        }
    }
    private fun validateOffer(offer: ResourceOffer) {
        require(offer.id.matches(Regex("[a-z][a-z0-9.]{2,70}")) && offer.title.length in 1..100)
        require(offer.version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")) && offer.revision>0)
        require(offer.tag.matches(Regex("[a-zA-Z0-9.-]{1,100}")))
        require(offer.bytes in 1..MAX_DOWNLOAD && offer.sha256.matches(Regex("[a-f0-9]{64}")))
        require(offer.minApp>=1 && offer.notes.length<=10000)
        require(UpdatePolicy.releaseAsset(offer.url,repository,offer.tag) && offer.url.endsWith(".zip")) { "资源下载来源不可信。" }
    }
    suspend fun check(): ResourceOffer = withContext(Dispatchers.IO) {
        val json=transport.open("https://github.com/$repository/releases/download/resources-v1/resources.json").use {
            JSONObject(readBounded(it,256*1024).toString(Charsets.UTF_8))
        }
        require(json.getInt("schemaVersion")==1 && json.getString("id")=="sample.basic") { "不支持的资源索引。" }
        val offer=ResourceOffer(json.getString("version"),json.getInt("revision"),json.getString("url"),
            json.getLong("bytes"),json.getString("sha256"),json.getString("notes"),json.getInt("minAppVersionCode"))
        require(offer.version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")) && offer.revision>0)
        require(offer.bytes in 1..MAX_BYTES && offer.sha256.matches(Regex("[a-f0-9]{64}")))
        require(offer.notes.length<=10000 && offer.minApp in 1..BuildConfig.VERSION_CODE) { "请先更新应用，再下载此资源库。" }
        require(UpdatePolicy.releaseAsset(offer.url,repository,"resource-${offer.version}") && offer.url.endsWith(".zip")) { "资源下载来源不可信。" }
        offer
    }
    suspend fun download(offer: ResourceOffer, progress: (Long)->Unit): File = withContext(Dispatchers.IO) {
        validateOffer(offer)
        require(offer.minApp<=BuildConfig.VERSION_CODE) { "请先更新应用，再下载此包。" }
        val target=File(context.cacheDir,"resource-${UUID.randomUUID()}.zip")
        try {
            transport.open(offer.url).use { input -> target.outputStream().use { output ->
                val buffer=ByteArray(32768);var total=0L
                while(true) { coroutineContext.ensureActive();val n=input.read(buffer);if(n<0)break
                    total+=n;require(total<=offer.bytes) { "资源包超出声明大小。" };output.write(buffer,0,n);progress(total)
                }
                require(total==offer.bytes) { "资源包下载不完整。" }
            } }
            require(hash(target)==offer.sha256) { "资源包校验失败。" };target
        } catch(e: Exception) { target.delete();throw e }
    }
    fun prepare(input: InputStream, currentRevision: Int, offer: ResourceOffer?=null): PreparedResource {
        root.mkdirs()
        val folder=File(root,"installed-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val seen=mutableSetOf<String>();var total=0L
            ZipInputStream(input).use { zip -> while(true) {
                val entry=zip.nextEntry ?: break
                require(!entry.isDirectory && entry.name in setOf("sample.db","manifest.json") && seen.add(entry.name)) { "资源包包含不允许的文件或重复文件。" }
                val limit=if(entry.name=="manifest.json")256*1024L else MAX_BYTES
                File(folder,entry.name).outputStream().use { output ->
                    val buffer=ByteArray(8192);var count=0L
                    while(true) { val n=zip.read(buffer);if(n<0)break;count+=n;total+=n
                        require(count<=limit && total<=MAX_BYTES+256*1024) { "资源解压大小超限。" };output.write(buffer,0,n)
                    }
                }
            } }
            require(seen==setOf("sample.db","manifest.json")) { "资源包文件不完整。" }
            val manifest=JSONObject(File(folder,"manifest.json").readText())
            validate(folder,manifest)
            require(manifest.getInt("revision")>(if(currentRevision<0)revision(manifest.getString("id")) else currentRevision)) { "已安装相同或更新的资源版本。" }
            if(offer!=null) require(manifest.getString("id")==offer.id && manifest.getInt("revision")==offer.revision && manifest.getString("version")==offer.version) { "资源包与索引版本不一致。" }
            return PreparedResource(folder,manifest)
        } catch(e: Exception) { folder.deleteRecursively();throw e }
    }
    private fun validate(folder: File, m: JSONObject) {
        val schema=m.getInt("schemaVersion")
        require(schema!=1 || m.getString("id")=="sample.basic")
        require(schema in 1..2 && m.getString("id").matches(Regex("[a-z][a-z0-9.]{2,70}")) && m.getString("database")=="sample.db") { "不支持的资源格式。" }
        require(m.getInt("revision")>0 && m.getString("version").matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+")))
        require(m.optInt("minAppVersionCode",4) in 1..BuildConfig.VERSION_CODE) { "资源库需要更新的应用版本。" }
        val files=m.getJSONArray("files");require(files.length()==1)
        val info=files.getJSONObject(0);val dbFile=File(folder,"sample.db")
        require(info.getString("path")=="sample.db" && dbFile.length() in 1..MAX_BYTES && dbFile.length()==info.getLong("bytes"))
        require(hash(dbFile)==info.getString("sha256")) { "资源数据库校验失败。" }
        val validationKey=m.toString()
        if(checkedDatabases[dbFile.canonicalPath]==validationKey)return
        SQLiteDatabase.openDatabase(dbFile.path,null,SQLiteDatabase.OPEN_READONLY).use { db ->
            require(db.version==schema)
            db.rawQuery("PRAGMA integrity_check",null).use { require(it.moveToFirst() && it.getString(0)=="ok") }
            db.rawQuery("PRAGMA foreign_key_check",null).use { require(!it.moveToFirst()) }
            // Reject views/triggers and unexpected tables before querying any downloaded database.
            db.rawQuery("SELECT name,type FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'",null).use { c ->
                val tables=mutableSetOf<String>()
                while(c.moveToNext()) { require(c.getString(1) in setOf("table","index"));if(c.getString(1)=="table")tables.add(c.getString(0)) }
                val required=setOf("source","dict_entry","dict_sense","passage","question")
                val allowed=required+setOf("dict_alias","article","article_segment","source_file","provenance","record_source","knowledge_record","android_metadata")
                require(tables.containsAll(required) && tables.all { it in allowed })
            }
            for((table,count) in listOf("dict_entry" to "entryCount","passage" to "passageCount","question" to "questionCount")) {
                db.rawQuery("SELECT COUNT(*) FROM $table",null).use { require(it.moveToFirst() && it.getInt(0) in 0..100000 && it.getInt(0)==m.getInt(count)) }
            }
            val ids=mutableSetOf<String>()
            db.rawQuery("SELECT id,headword,pinyin,summary FROM dict_entry",null).use { c -> while(c.moveToNext()) { ids.add(c.getString(0));for(i in 0..3)require(c.getString(i).length in 1..10000) } }
            db.rawQuery("SELECT id,entry_id,gloss,pos,example,translation,reference FROM dict_sense",null).use { c -> var count=0;while(c.moveToNext()) { require(++count<=100000 && c.getString(1) in ids);for(i in 0..6)require(c.getString(i).length in (if(schema==2 && i in 3..5)0 else 1)..20000) } }
            db.rawQuery("SELECT id,original,translation,direction,work,analysis,entry_ids,normalized FROM passage",null).use { c -> while(c.moveToNext()) {
                for(i in 0..7)require(c.getString(i).length in (if(schema==2 && i in listOf(2,5))0 else 1)..100000)
                require(c.getString(3) in setOf("CLASSICAL_TO_ZH","ZH_TO_EN"))
                val links=org.json.JSONArray(c.getString(6));for(i in 0 until links.length())require(links.getString(i) in ids)
            } }
            if(schema==2) {
                db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='article'",null).use { names -> if(names.moveToFirst()) {
                    db.rawQuery("SELECT id,title,author,dynasty,original,translation,background,appreciation,metadata_json FROM article",null).use { c ->
                        var count=0;while(c.moveToNext()) { require(++count<=50000);require(c.getString(0).isNotBlank() && c.getString(1).isNotBlank());for(i in 0..8)require(c.getString(i).length<=2*1024*1024) }
                        require(count==m.getInt("articleCount"))
                    }
                } }
                db.rawQuery("SELECT alias,entry_id FROM dict_alias",null).use { c -> var count=0;while(c.moveToNext()) { require(++count<=200000 && c.getString(0).length in 1..1000 && c.getString(1) in ids) } }
            }
            db.rawQuery("SELECT id,stem,options_json,answer,explanation,entry_id FROM question",null).use { c -> while(c.moveToNext()) {
                val options=org.json.JSONArray(c.getString(2));require(options.length() in 2..8 && c.getInt(3) in 0 until options.length() && c.getString(5) in ids)
                for(i in 0 until options.length())require(options.getString(i).length in 1..10000)
                require(c.getString(0).isNotBlank() && c.getString(1).length in 1..20000 && c.getString(4).length in 1..20000)
            } }
        }
        checkedDatabases[dbFile.canonicalPath]=validationKey
    }
    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true) { val n=input.read(buffer);if(n<0)break;require(output.size()+n<=limit);output.write(buffer,0,n) };return output.toByteArray()
    }
}
