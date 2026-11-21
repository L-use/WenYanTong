package com.wenyantong.app.core.storage

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.content.edit
import com.wenyantong.app.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import com.wenyantong.app.feature.storage.*

/** Resource DB is immutable; personal data is kept in a separate database. */
class LocalRepository(private val context: Context) {
    private val personal = PersonalDatabase(context)
    private lateinit var resource: SQLiteDatabase
    private val resourceLock=Any()
    private val resourceStore=com.wenyantong.app.feature.resources.ResourceStore(context)
    private val prefs = context.getSharedPreferences("reading", Context.MODE_PRIVATE)
    lateinit var manifest: JSONObject
        private set

    suspend fun initialize() = withContext(Dispatchers.IO) {
        manifest = JSONObject(context.assets.open("packs/manifest.json").bufferedReader().use { it.readText() })
        val file = File(context.filesDir, "resources/sample-${manifest.getString("version")}.db")
        file.parentFile!!.mkdirs()
        val expected = manifest.getJSONArray("files").getJSONObject(0).getString("sha256")
        fun valid(target: File) = target.isFile && hash(target) == expected
        if (!valid(file)) {
            val temporary = File(file.parentFile, "sample.pending")
            context.assets.open("packs/sample.db").use { input -> temporary.outputStream().use { input.copyTo(it) } }
            check(valid(temporary)) { "内置资源校验失败，请重新安装应用。" }
            SQLiteDatabase.openDatabase(temporary.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("PRAGMA integrity_check", null).use { check(it.moveToFirst() && it.getString(0) == "ok") }
                check(db.version == 1) { "不支持的资源版本。" }
            }
            if (file.exists()) check(file.delete())
            check(temporary.renameTo(file)) { "示例资源安装失败。" }
        }
        val installed=resourceStore.active()
        val selected=if(installed!=null && installed.manifest.getInt("revision")>manifest.optInt("revision",0)) installed else null
        synchronized(resourceLock) {
            if(::resource.isInitialized) resource.close()
            resource = SQLiteDatabase.openDatabase(selected?.let { File(it.directory,"sample.db").path } ?: file.path, null, SQLiteDatabase.OPEN_READONLY)
            if(selected!=null) manifest=selected.manifest
        }
        personal.writableDatabase
    }

    suspend fun installResource(prepared: com.wenyantong.app.feature.resources.PreparedResource) = withContext(Dispatchers.IO) {
        synchronized(resourceLock) {
            val next=SQLiteDatabase.openDatabase(File(prepared.directory,"sample.db").path,null,SQLiteDatabase.OPEN_READONLY)
            try { resourceStore.activate(prepared) } catch(e: Exception) { next.close();throw e }
            val old=resource;resource=next;manifest=prepared.manifest;old.close()
        }
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(8192)
            while (true) { val n = input.read(bytes); if (n < 0) break; digest.update(bytes, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun entries(): List<Entry> = withContext(Dispatchers.IO) {
        synchronized(resourceLock) { resource.rawQuery("SELECT id,headword,pinyin,summary FROM dict_entry ORDER BY rowid", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val senses = resource.rawQuery("SELECT id,gloss,pos,example,translation,reference FROM dict_sense WHERE entry_id=? ORDER BY rowid", arrayOf(id)).use { sense ->
                        buildList { while (sense.moveToNext()) add(Sense(sense.getString(0),sense.getString(1),sense.getString(2),sense.getString(3),sense.getString(4),sense.getString(5))) }
                    }
                    add(Entry(id,cursor.getString(1),cursor.getString(2),cursor.getString(3),senses))
                }
            }
        }
        }
    }

    suspend fun passages(): List<Passage> = withContext(Dispatchers.IO) {
        synchronized(resourceLock) { resource.rawQuery("SELECT id,original,translation,direction,work,analysis,entry_ids FROM passage ORDER BY rowid", null).use { c ->
            buildList { while (c.moveToNext()) add(Passage(c.getString(0),c.getString(1),c.getString(2),Direction.valueOf(c.getString(3)),c.getString(4),c.getString(5),JSONArray(c.getString(6)).strings())) }
        }
        }
    }

    suspend fun query(text: String, direction: Direction): QueryResult = withContext(Dispatchers.IO) {
        val candidates = passages()
        val forward = if (direction == Direction.EN_TO_ZH) Direction.ZH_TO_EN else if (direction == Direction.ZH_TO_CLASSICAL) Direction.CLASSICAL_TO_ZH else direction
        val reverse = forward != direction
        val hit = candidates.firstOrNull { it.direction == forward && TextRules.matches(if (reverse) it.translation else it.original, text) }
        val match = hit?.let { if (reverse) it.copy(original=it.translation,translation=it.original,direction=direction) else it }
        val found = if (direction in listOf(Direction.CLASSICAL_TO_ZH,Direction.ZH_TO_CLASSICAL)) {
            entries().filter { e -> e.id in (hit?.entryIds ?: emptyList()) || (direction == Direction.CLASSICAL_TO_ZH && text.contains(e.headword)) }
                .sortedByDescending { it.headword.length }
        } else emptyList()
        val result = QueryResult(text.trim(),direction,match,found)
        saveHistory(result.query,direction)
        result
    }

    private fun saveHistory(text: String, direction: Direction) {
        val db = personal.writableDatabase
        val values = ContentValues().apply { put("query",text);put("direction",direction.name);put("timestamp",System.currentTimeMillis()) }
        if (db.update("history",values,"query=? AND direction=?",arrayOf(text,direction.name)) == 0) db.insertOrThrow("history",null,values)
    }
    suspend fun history(): List<HistoryItem> = withContext(Dispatchers.IO) {
        personal.readableDatabase.rawQuery("SELECT id,query,direction,timestamp,favorite FROM history ORDER BY timestamp DESC",null).use { c ->
            buildList { while(c.moveToNext()) add(HistoryItem(c.getLong(0),c.getString(1),Direction.valueOf(c.getString(2)),c.getLong(3),c.getInt(4)==1)) }
        }
    }
    suspend fun favorite(id: Long, favorite: Boolean) = withContext(Dispatchers.IO) {
        personal.writableDatabase.update("history",ContentValues().apply { put("favorite",if(favorite) 1 else 0) },"id=?", arrayOf(id.toString()))
    }
    suspend fun deleteHistory(id: Long) = withContext(Dispatchers.IO) { personal.writableDatabase.delete("history","id=?",arrayOf(id.toString())) }
    suspend fun questions(entryIds: Set<String> = emptySet(), wrongOnly: Boolean = false): List<Question> = withContext(Dispatchers.IO) {
        val wrongIds = wrongIds()
        synchronized(resourceLock) { resource.rawQuery("SELECT id,stem,options_json,answer,explanation,entry_id FROM question ORDER BY rowid",null).use { c ->
            buildList { while(c.moveToNext()) {
                val q = Question(c.getString(0),c.getString(1),JSONArray(c.getString(2)).strings(),c.getInt(3),c.getString(4),c.getString(5))
                if ((entryIds.isEmpty() || q.entryId in entryIds) && (!wrongOnly || q.id in wrongIds)) add(q)
            } }
        }
        }
    }
    private fun wrongIds(): Set<String> = personal.readableDatabase.rawQuery("SELECT question_id FROM wrong_question WHERE mastered=0",null).use { c ->
        buildSet { while(c.moveToNext()) add(c.getString(0)) }
    }
    suspend fun wrongCount(): Int = withContext(Dispatchers.IO) { wrongIds().size }
    suspend fun recordAnswer(sessionId: String, question: Question, choice: Int) = withContext(Dispatchers.IO) {
        val db = personal.writableDatabase
        val correct = TextRules.isCorrect(question,choice)
        db.beginTransaction()
        try {
            db.insertWithOnConflict("practice_answer",null,ContentValues().apply {
                put("session_id",sessionId);put("question_id",question.id);put("choice",choice);put("correct",if(correct)1 else 0);put("timestamp",System.currentTimeMillis())
            },SQLiteDatabase.CONFLICT_REPLACE)
            if (!correct) {
                db.execSQL("UPDATE wrong_question SET count=count+1,mastered=0 WHERE question_id=?",arrayOf(question.id))
                db.insertWithOnConflict("wrong_question",null,ContentValues().apply { put("question_id",question.id);put("count",1);put("mastered",0) },SQLiteDatabase.CONFLICT_IGNORE)
            }
            // A correct reattempt does not silently mark a question as mastered.
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    suspend fun exportUserData(): ByteArray = withContext(Dispatchers.IO) {
        val db=personal.readableDatabase
        db.beginTransaction()
        val snapshot=try {
            val history=db.rawQuery("SELECT query,direction,timestamp,favorite FROM history ORDER BY timestamp DESC",null).use { c ->
                buildList { while(c.moveToNext()) add(BackupHistory(c.getString(0),Direction.valueOf(c.getString(1)),c.getLong(2),c.getInt(3)==1)) }
            }
            val answers=db.rawQuery("SELECT session_id,question_id,choice,correct,timestamp FROM practice_answer ORDER BY timestamp",null).use { c ->
                buildList { while(c.moveToNext()) add(BackupAnswer(c.getString(0),c.getString(1),c.getInt(2),c.getInt(3)==1,c.getLong(4))) }
            }
            val wrong=db.rawQuery("SELECT question_id,count,mastered FROM wrong_question ORDER BY question_id",null).use { c ->
                buildList { while(c.moveToNext()) add(BackupWrong(c.getString(0),c.getInt(1),c.getInt(2)==1)) }
            }
            UserBackup(history,answers,wrong,BackupPreferences(mode(),theme(),fontSize(),context.getSharedPreferences("app_updates",Context.MODE_PRIVATE).getBoolean("auto",true)))
        } finally { db.endTransaction() }
        UserBackupCodec.encode(snapshot)
    }
    suspend fun importUserData(backup: UserBackup): BackupSummary = withContext(Dispatchers.IO) {
        // Validate the entire snapshot before starting any mutation, including callers other than the picker.
        val checked=UserBackupCodec.decode(UserBackupCodec.encode(backup))
        val db=personal.writableDatabase
        db.beginTransaction()
        try {
            checked.history.forEach { row ->
                db.execSQL("UPDATE history SET timestamp=MAX(timestamp,?),favorite=MAX(favorite,?) WHERE query=? AND direction=?",arrayOf(row.timestamp,if(row.favorite)1 else 0,row.query,row.direction.name))
                db.insertWithOnConflict("history",null,ContentValues().apply { put("query",row.query);put("direction",row.direction.name);put("timestamp",row.timestamp);put("favorite",if(row.favorite)1 else 0) },SQLiteDatabase.CONFLICT_IGNORE)
            }
            checked.answers.forEach { row ->
                db.insertWithOnConflict("practice_answer",null,ContentValues().apply { put("session_id",row.sessionId);put("question_id",row.questionId);put("choice",row.choice);put("correct",if(row.correct)1 else 0);put("timestamp",row.timestamp) },SQLiteDatabase.CONFLICT_IGNORE)
            }
            checked.wrong.forEach { row ->
                db.execSQL("UPDATE wrong_question SET count=MAX(count,?),mastered=MIN(mastered,?) WHERE question_id=?",arrayOf(row.count,if(row.mastered)1 else 0,row.questionId))
                db.insertWithOnConflict("wrong_question",null,ContentValues().apply { put("question_id",row.questionId);put("count",row.count);put("mastered",if(row.mastered)1 else 0) },SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        prefs.edit { putString("mode",checked.preferences.mode.name);putString("theme",checked.preferences.theme);putFloat("fontSize",checked.preferences.fontSize) }
        context.getSharedPreferences("app_updates",Context.MODE_PRIVATE).edit { putBoolean("auto",checked.preferences.automaticUpdates) }
        checked.summary()
    }
    fun mode() = Mode.entries.firstOrNull { it.name == prefs.getString("mode","OFFLINE") } ?: Mode.OFFLINE
    fun setMode(mode: Mode) { prefs.edit { putString("mode",mode.name) } }
    fun theme() = prefs.getString("theme","system") ?: "system"
    fun fontSize() = prefs.getFloat("fontSize",18f)
    fun setReading(theme: String, font: Float) { prefs.edit { putString("theme",theme);putFloat("fontSize",font) } }
    fun close() { if (::resource.isInitialized) resource.close();personal.close() }
}

private fun JSONArray.strings() = (0 until length()).map { getString(it) }

private class PersonalDatabase(context: Context) : SQLiteOpenHelper(context,"personal.db",null,1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT,query TEXT NOT NULL,direction TEXT NOT NULL,timestamp INTEGER NOT NULL,favorite INTEGER NOT NULL DEFAULT 0,UNIQUE(query,direction))")
        db.execSQL("CREATE TABLE practice_answer(session_id TEXT NOT NULL,question_id TEXT NOT NULL,choice INTEGER NOT NULL,correct INTEGER NOT NULL,timestamp INTEGER NOT NULL,PRIMARY KEY(session_id,question_id))")
        db.execSQL("CREATE TABLE wrong_question(question_id TEXT PRIMARY KEY,count INTEGER NOT NULL,mastered INTEGER NOT NULL DEFAULT 0)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { error("Missing personal database migration: $oldVersion → $newVersion") }
}
