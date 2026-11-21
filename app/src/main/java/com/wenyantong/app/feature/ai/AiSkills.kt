package com.wenyantong.app.feature.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class SkillScope(val title: String) { BOTH("翻译与出题"), TRANSLATE("翻译解析"), PRACTICE("练习出题") }
data class AiSkill(val id: String,val name: String,val instruction: String,val scope: SkillScope=SkillScope.BOTH,val enabled: Boolean=true)
object BuiltInSkills {
    val items=listOf(
        AiSkill("builtin-translate","双向翻译与解析","自动判断所选语种组合中的翻译方向，解释词义与语境，明确不确定之处。",SkillScope.TRANSLATE),
        AiSkill("builtin-practice","查询内容出题","根据当前查询、译文和可用词义生成练习，给出答案、解析与依据。",SkillScope.PRACTICE)
    )
}
class AiSkillStore(context: Context) {
    private val prefs=context.getSharedPreferences("ai_private",Context.MODE_PRIVATE)
    fun all(): List<AiSkill> = decode(prefs.getString("user_skills","[]") ?: "[]")
    @Synchronized fun save(skill: AiSkill) {
        validate(skill);val items=all().filterNot { it.id==skill.id }+skill
        validateCollection(items)
        check(prefs.edit().putString("user_skills",encode(items)).commit())
    }
    @Synchronized fun replaceAll(items: List<AiSkill>) { validateCollection(items);check(prefs.edit().putString("user_skills",encode(items)).commit()) }
    @Synchronized fun delete(id: String) { check(prefs.edit().putString("user_skills",encode(all().filterNot { it.id==id })).commit()) }
    fun instructions(scope: SkillScope)=all().filter { it.enabled && (it.scope==SkillScope.BOTH || it.scope==scope) }.joinToString("\n\n") { "用户技能【${it.name}】：${it.instruction}" }
    companion object {
        fun validate(skill: AiSkill) {
            require(skill.id.startsWith("skill-") && skill.id.length<=128 && skill.name.isNotBlank() && skill.name.length<=60 && skill.instruction.isNotBlank() && skill.instruction.length<=4000) { "技能名称限 60 字，指令限 4000 字。" }
        }
        fun validateCollection(items: List<AiSkill>) {
            require(items.size<=30 && items.map { it.id }.distinct().size==items.size) { "技能最多 30 个，ID 不能重复。" }
            items.forEach(::validate)
            require(items.filter { it.enabled }.sumOf { it.name.length+it.instruction.length+20 }<=24000) { "已启用技能的总长度不能超过 24000 字，请先停用部分技能。" }
        }
        fun encode(items: List<AiSkill>)=JSONArray().apply { items.forEach { s -> put(JSONObject().put("id",s.id).put("name",s.name).put("instruction",s.instruction).put("scope",s.scope.name).put("enabled",s.enabled)) } }.toString()
        fun decode(text: String): List<AiSkill> { val array=JSONArray(text);require(array.length()<=30);return (0 until array.length()).map { i -> val s=array.getJSONObject(i);AiSkill(s.getString("id"),s.getString("name"),s.getString("instruction"),SkillScope.valueOf(s.getString("scope")),s.getBoolean("enabled")) }.also(::validateCollection) }
    }
}
