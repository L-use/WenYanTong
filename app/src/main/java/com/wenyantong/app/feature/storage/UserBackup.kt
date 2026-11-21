package com.wenyantong.app.feature.storage

import com.wenyantong.app.BuildConfig
import com.wenyantong.app.core.model.Direction
import com.wenyantong.app.core.model.Mode
import com.wenyantong.app.core.model.Question
import com.wenyantong.app.core.model.QuestionKind
import com.wenyantong.app.core.model.LearningText
import com.wenyantong.app.feature.ai.AiSkill
import com.wenyantong.app.feature.ai.AiSkillStore
import org.json.JSONArray
import org.json.JSONObject

data class BackupHistory(val query: String,val direction: Direction,val timestamp: Long,val favorite: Boolean)
data class BackupAnswer(val sessionId: String,val questionId: String,val choice: Int,val correct: Boolean,val timestamp: Long)
data class BackupWrong(val questionId: String,val count: Int,val mastered: Boolean)
data class BackupPreferences(val mode: Mode,val theme: String,val fontSize: Float,val automaticUpdates: Boolean)
data class UserBackup(val history: List<BackupHistory>,val answers: List<BackupAnswer>,val wrong: List<BackupWrong>,val preferences: BackupPreferences,val generated: List<Question> = emptyList(),val skills: List<AiSkill> = emptyList()) {
    fun summary()=BackupSummary(history.size,history.count { it.favorite },answers.size,wrong.size,generated.size,skills.size)
}
data class BackupSummary(val history: Int,val favorites: Int,val answers: Int,val wrong: Int,val generated: Int=0,val skills: Int=0)
data class StorageUiState(val busy: Boolean=false,val message: String?=null,val pending: BackupSummary?=null)

object UserBackupCodec {
    const val MAX_BYTES=20*1024*1024
    private const val MAX_RECORDS=50000
    fun encode(data: UserBackup): ByteArray {
        require(data.history.size.toLong()+data.answers.size+data.wrong.size+data.generated.size<=MAX_RECORDS) { "备份记录超过当前 5 万条上限。" }
        val root=JSONObject().put("format","wenyantong-user-data").put("schemaVersion",2)
            .put("appVersion",BuildConfig.VERSION_NAME).put("exportedAt",System.currentTimeMillis())
            .put("history",JSONArray().apply { data.history.forEach { put(JSONObject().put("query",it.query).put("direction",it.direction.name).put("timestamp",it.timestamp).put("favorite",it.favorite)) } })
            .put("answers",JSONArray().apply { data.answers.forEach { put(JSONObject().put("sessionId",it.sessionId).put("questionId",it.questionId).put("choice",it.choice).put("correct",it.correct).put("timestamp",it.timestamp)) } })
            .put("wrongQuestions",JSONArray().apply { data.wrong.forEach { put(JSONObject().put("questionId",it.questionId).put("count",it.count).put("mastered",it.mastered)) } })
            .put("generatedQuestions",JSONArray().apply { data.generated.forEach { put(JSONObject().put("id",it.id).put("stem",it.stem).put("options",JSONArray(it.options)).put("answer",it.answer).put("explanation",it.explanation).put("entryId",it.entryId).put("kind",it.kind.name).put("edited",it.edited)) } })
            .put("skills",JSONArray(AiSkillStore.encode(data.skills)))
            .put("preferences",JSONObject().put("mode",data.preferences.mode.name).put("theme",data.preferences.theme).put("fontSize",data.preferences.fontSize.toDouble()).put("automaticUpdates",data.preferences.automaticUpdates))
        val bytes=root.toString(2).toByteArray(Charsets.UTF_8)
        require(bytes.size<=MAX_BYTES) { "学习记录较多，当前备份上限为 20 MB。" }
        return bytes
    }
    fun decode(bytes: ByteArray): UserBackup {
        require(bytes.size in 1..MAX_BYTES) { "备份文件为空或超过 20 MB。" }
        val root=JSONObject(bytes.toString(Charsets.UTF_8))
        require(text(root,"format",100)=="wenyantong-user-data") { "请选择文言通导出的 JSON 备份。" }
        number(root,"schemaVersion",1,2)
        val history=root.getJSONArray("history")
        val answers=root.getJSONArray("answers")
        val wrong=root.getJSONArray("wrongQuestions")
        val generated=root.optJSONArray("generatedQuestions") ?: JSONArray()
        require(history.length().toLong()+answers.length()+wrong.length()+generated.length()<=MAX_RECORDS) { "备份记录超过当前导入上限。" }
        val p=root.getJSONObject("preferences")
        val theme=text(p,"theme",20)
        require(theme in listOf("system","light","dark")) { "备份主题配置无效。" }
        val font=p.get("fontSize")
        require(font is Number && font.toFloat().isFinite() && font.toFloat() in 16f..26f) { "备份字号配置无效。" }
        return UserBackup(
            (0 until history.length()).map { history.getJSONObject(it).let { row -> BackupHistory(text(row,"query",2000),Direction.valueOf(text(row,"direction",40)),number(row,"timestamp",0,Long.MAX_VALUE),bool(row,"favorite")) } },
            (0 until answers.length()).map { answers.getJSONObject(it).let { row -> BackupAnswer(text(row,"sessionId",128),text(row,"questionId",128),number(row,"choice",0,999).toInt(),bool(row,"correct"),number(row,"timestamp",0,Long.MAX_VALUE)) } },
            (0 until wrong.length()).map { wrong.getJSONObject(it).let { row -> BackupWrong(text(row,"questionId",128),number(row,"count",1,Int.MAX_VALUE.toLong()).toInt(),bool(row,"mastered")) } },
            BackupPreferences(Mode.valueOf(text(p,"mode",20)),theme,font.toFloat(),bool(p,"automaticUpdates")),
            (0 until generated.length()).map { index -> val q=generated.getJSONObject(index);val options=q.getJSONArray("options");val kind=QuestionKind.valueOf(q.optString("kind","SINGLE"));val size=if(kind==QuestionKind.TRUE_FALSE)2 else 4;require(options.length()==size)
                val choices=(0 until size).map { val value=restoreText(options.getString(it));require(value.isNotBlank() && value.length<=500);value };require(choices.toSet().size==size)
                val id=text(q,"id",128);val edited=q.optBoolean("edited",false);require(id.startsWith("ai-") || edited)
                Question(id,restoreText(text(q,"stem",2000)),choices,number(q,"answer",0,(size-1).toLong()).toInt(),restoreText(text(q,"explanation",16000)),text(q,"entryId",128),kind,edited)
            },
            AiSkillStore.decode((root.optJSONArray("skills") ?: JSONArray()).toString())
        )
    }
    // Preserve old damaged records for manual correction rather than rejecting the entire user backup.
    private fun restoreText(value: String)=runCatching { LearningText.clean(value) }.getOrDefault(value)
    private fun text(json: JSONObject,key: String,max: Int): String {
        val value=json.get(key)
        require(value is String && value.isNotBlank() && value.length<=max && '\u0000' !in value) { "备份字段 $key 无效。" }
        return value
    }
    private fun bool(json: JSONObject,key: String): Boolean {
        val value=json.get(key)
        require(value is Boolean) { "备份字段 $key 无效。" }
        return value
    }
    private fun number(json: JSONObject,key: String,min: Long,max: Long): Long {
        val value=json.get(key)
        require(value is Number) { "备份字段 $key 无效。" }
        val integer=value.toString().toLongOrNull()
        require(integer!=null && integer in min..max) { "备份字段 $key 无效。" }
        return integer
    }
}
