package com.wenyantong.app.feature.resources

import android.database.sqlite.SQLiteDatabase
import com.wenyantong.app.core.model.*
import org.json.JSONArray
import java.io.File

/** Database handles are only used under LocalRepository's resource lock. Heavy text is read on demand. */
class LibraryIndex {
    private data class Library(val pack: PreparedResource,val db: SQLiteDatabase) {
        val id=pack.manifest.getString("id")
        val title=pack.manifest.optString("title",id)
        val review=pack.manifest.optString("reviewStatus")
        val tables=mutableSetOf<String>().apply { db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'",null).use { c -> while(c.moveToNext())add(c.getString(0)) } }
    }
    private var libraries=listOf<Library>()
    fun open(packs: List<PreparedResource>) {
        val next=mutableListOf<Library>()
        try { packs.forEach { next.add(Library(it,SQLiteDatabase.openDatabase(File(it.directory,"sample.db").path,null,SQLiteDatabase.OPEN_READONLY))) } }
        catch(e: Exception) { next.forEach { it.db.close() };throw e }
        close();libraries=next
    }
    fun close() { libraries.forEach { it.db.close() };libraries=emptyList() }
    fun previews(): List<Entry> = libraries.flatMap { lib ->
        val aliases=mutableMapOf<String,MutableList<String>>()
        lib.db.rawQuery("SELECT entry_id,alias FROM dict_alias",null).use { c -> while(c.moveToNext())aliases.getOrPut(c.getString(0)) { mutableListOf() }.add(c.getString(1)) }
        lib.db.rawQuery("SELECT id,headword,pinyin,substr(summary,1,250),(SELECT COUNT(*) FROM dict_sense s WHERE s.entry_id=e.id) FROM dict_entry e ORDER BY rowid",null).use { c ->
            buildList { while(c.moveToNext())add(Entry(c.getString(0),c.getString(1),c.getString(2),c.getString(3),emptyList(),listOf(lib.title),totalSenses=c.getInt(4),aliases=aliases[c.getString(0)].orEmpty())) }
        }
    }
    private fun source(lib: Library,type: String,id: String): String {
        val header="资源包：${lib.title}\n${lib.review}\n"
        if("record_source" !in lib.tables)return header+lib.pack.manifest.optJSONObject("source")?.toString().orEmpty()
        return header+lib.db.rawQuery("SELECT DISTINCT r.field,p.work,p.author,p.locator,p.origin_kind,f.url FROM record_source r JOIN provenance p ON p.id=r.provenance_id JOIN source_file f ON f.id=p.file_id WHERE r.record_type=? AND r.record_id=? ORDER BY p.id",arrayOf(type,id)).use { c ->
            buildList { while(c.moveToNext())add("${c.getString(0)} · ${c.getString(1)} ${c.getString(2)}\n${c.getString(3)} · ${when(c.getString(4)) { "upstream_ai_generated" -> "上游 AI 生成";"public_domain_ancient_text" -> "古籍原文";else -> "上游整理，译注作者见原来源" }}\n${c.getString(5)}") }.joinToString("\n\n")
        }
    }
    fun details(word: String, limit: Int): List<Entry> = libraries.mapNotNull { lib ->
        lib.db.rawQuery("SELECT id,headword,pinyin,summary FROM dict_entry WHERE headword=? OR id IN (SELECT entry_id FROM dict_alias WHERE alias=?)",arrayOf(word,word)).use { c ->
            if(!c.moveToFirst())return@mapNotNull null
            val id=c.getString(0)
            val senses=lib.db.rawQuery("SELECT id,gloss,pos,example,translation,reference FROM dict_sense WHERE entry_id=? LIMIT ?",arrayOf(id,limit.toString())).use { s ->
                buildList { while(s.moveToNext()) add(Sense(s.getString(0),s.getString(1),s.getString(2),s.getString(3),s.getString(4),s.getString(5)+"\n"+source(lib,"dict_sense",s.getString(0)))) }
            }
            val count=lib.db.rawQuery("SELECT COUNT(*) FROM dict_sense WHERE entry_id=?",arrayOf(id)).use { it.moveToFirst();it.getInt(0) }
            Entry(id,c.getString(1),c.getString(2),c.getString(3),senses,listOf(lib.title),totalSenses=count,hasMore=count>limit,aliases=listOf(word))
        }
    }
    fun aliases(word: String): List<Entry> = libraries.flatMap { lib ->
        lib.db.rawQuery("SELECT e.id,e.headword,e.pinyin,substr(e.summary,1,250) FROM dict_entry e JOIN dict_alias a ON a.entry_id=e.id WHERE a.alias=?",arrayOf(word)).use { c -> buildList {
            while(c.moveToNext())add(Entry(c.getString(0),c.getString(1),c.getString(2),c.getString(3),emptyList(),listOf(lib.title)))
        } }
    }
    fun lookup(query: String, direction: Direction): List<Passage> = libraries.flatMap { lib ->
        val reverse=direction==Direction.ZH_TO_CLASSICAL
        if(direction !in listOf(Direction.CLASSICAL_TO_ZH,Direction.ZH_TO_CLASSICAL))return@flatMap emptyList()
        val where=if(reverse)"translation=?" else "normalized=?"
        lib.db.rawQuery("SELECT id,original,translation,work,analysis,entry_ids FROM passage WHERE $where LIMIT 50",arrayOf(if(reverse)query else TextRules.normalize(query))).use { c -> buildList {
            while(c.moveToNext()) {
                val original=c.getString(1);val translated=c.getString(2);val links=JSONArray(c.getString(5))
                add(Passage(c.getString(0),if(reverse)translated else original,if(reverse)original else translated,direction,c.getString(3),c.getString(4),(0 until links.length()).map { links.getString(it) },listOf(source(lib,"passage",c.getString(0)))))
            }
        } }
    }
    fun articles(packId: String, query: String, offset: Int): List<LibraryArticle> = buildList {
        var skip=offset
        for(lib in libraries.filter { packId.isBlank() || it.id==packId }) {
            if("article" !in lib.tables || size>=40)continue
            val count=lib.db.rawQuery("SELECT COUNT(*) FROM article WHERE instr(title,?)>0 OR instr(author,?)>0",arrayOf(query,query)).use { it.moveToFirst();it.getInt(0) }
            if(skip>=count) { skip-=count;continue }
            lib.db.rawQuery("SELECT id,title,author,dynasty FROM article WHERE instr(title,?)>0 OR instr(author,?)>0 ORDER BY rowid LIMIT ? OFFSET ?",arrayOf(query,query,(40-size).toString(),skip.toString())).use { c ->
                while(c.moveToNext())add(LibraryArticle(c.getString(0),lib.id,c.getString(1),c.getString(2),c.getString(3)))
            };skip=0
        }
    }
    fun article(item: LibraryArticle): ArticleText {
        val lib=libraries.first { it.id==item.packId }
        return lib.db.rawQuery("SELECT title,author,original,translation,background,appreciation FROM article WHERE id=?",arrayOf(item.id)).use { c ->
            check(c.moveToFirst());ArticleText(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4)+"\n"+c.getString(5),source(lib,"article",item.id))
        }
    }
}
