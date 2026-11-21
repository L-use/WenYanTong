package com.wenyantong.app

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.wenyantong.app.core.model.*
import com.wenyantong.app.core.storage.LocalRepository
import com.wenyantong.app.feature.ai.*
import com.wenyantong.app.feature.storage.UserBackupCodec
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LearningUpgradeTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun restore(prefs: android.content.SharedPreferences,before: Map<String,*>) { prefs.edit().clear().apply { before.forEach { (k,v) -> if(v is String)putString(k,v) } }.commit() }
    @Test fun offlineKindsNeverReuseAnIncompatibleTwoOptionQuestion()=runBlocking {
        val reading=context.getSharedPreferences("reading",0);val oldMode=reading.getString("mode","OFFLINE")
        val holder=ViewModelStore();lateinit var vm: AppViewModel
        val ids=mutableSetOf<String>();val repo=LocalRepository(context)
        try {
            repo.initialize();val id="ai-kind-${java.util.UUID.randomUUID()}";ids+=id
            repo.saveGenerated(listOf(Question(id,"判断题夹具",listOf("正确","错误"),0,"夹具解析","zhi",QuestionKind.TRUE_FALSE)),"夹具")
            instrumentation.runOnMainSync {
                vm=ViewModelProvider(holder,object: ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=AppViewModel(context.applicationContext as Application) as T
                })[AppViewModel::class.java]
            }
            withTimeout(15000) { vm.state.first { it.ready } }
            instrumentation.runOnMainSync { vm.mode(Mode.OFFLINE) }
            for(kind in QuestionKind.entries) {
                instrumentation.runOnMainSync { vm.startPractice(setOf("zhi"),10,kinds=setOf(kind)) { } }
                withTimeout(10000) { vm.state.first { !it.practiceBusy && it.questions.isNotEmpty() && it.questions.all { q -> q.kind==kind } } }
                assertTrue(vm.state.value.questions.all { it.options.size==if(kind==QuestionKind.TRUE_FALSE)2 else 4 })
                ids.addAll(vm.state.value.questions.filter { it.id.startsWith("ai-offline-") }.map { it.id })
            }
        } finally {
            instrumentation.runOnMainSync { holder.clear() };repo.close()
            val db=android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("personal.db").path,null,0)
            try { ids.forEach { db.delete("generated_question","id=?",arrayOf(it)) } } finally { db.close() }
            reading.edit().putString("mode",oldMode).commit()
        }
    }
    @Test fun apiProfilesKeepCredentialsAndConnectionStatusSeparateAndSkillsHaveScopes() {
        val prefs=context.getSharedPreferences("ai_private",0);val before=prefs.all.toMap()
        try {
            prefs.edit().clear().commit()
            val settings=AiSettings(baseUrl="https://a.invalid/v1",model="model-a")
            val store=AiSettingsStore(context)
            store.save(settings,"fake-a");store.rename("API 甲");store.markConnected(true)
            val first=store.activeProfile()
            val skills=AiSkillStore(context)
            skills.save(AiSkill("skill-translate","翻译技能","只用简洁中文讲解",SkillScope.TRANSLATE))
            skills.save(AiSkill("skill-practice","练习技能","选项避免文字长度提示",SkillScope.PRACTICE))
            assertTrue(skills.instructions(SkillScope.TRANSLATE).contains("只用简洁中文"))
            assertFalse(skills.instructions(SkillScope.PRACTICE).contains("只用简洁中文"))
            store.newProfile();assertFalse(store.settings().hasKey)
            store.save(settings.copy(baseUrl="https://b.invalid/v1",model="model-b"),"fake-b")
            val second=store.activeProfile();assertEquals(2,store.profiles().size)
            assertFalse(store.connected());assertEquals("fake-b",store.secret())
            store.activateProfile(first);assertEquals("fake-a",store.secret());assertTrue(store.connected())
            store.save(settings.copy(model="model-a-new"),"");assertFalse(store.connected())
            assertFalse(prefs.all.values.joinToString().contains("fake-a"));assertFalse(prefs.all.values.joinToString().contains("fake-b"))
            store.deleteProfile(second);assertEquals(1,store.profiles().size)
            skills.save(skills.all().first().copy(enabled=false));assertEquals("",skills.instructions(SkillScope.TRANSLATE))
            skills.delete("skill-practice");assertEquals(1,skills.all().size)
            store.clear();assertFalse(store.settings().hasKey);assertEquals(1,skills.all().size)
        } finally { restore(prefs,before) }
    }
    @Test fun arbitraryEnglishQuerySupportsThreeKindsWrongEditingSkillsAndBackup()=runBlocking {
        val prefs=context.getSharedPreferences("ai_private",0);val before=prefs.all.toMap()
        val reading=context.getSharedPreferences("reading",0);val oldMode=reading.getString("mode","OFFLINE")
        val holder=ViewModelStore();val ids=mutableListOf<String>();lateinit var vm: AppViewModel
        val query="unlisted language fixture abcxyz"
        var sawTranslate=false;var sawPractice=false
        val fake=AiTransport { _,key,system,user,_ ->
            assertEquals("fake-learning-key",key)
            if(system.contains("连接测试"))return@AiTransport "OK"
            assertEquals(query,JSONObject(user).getString("query"))
            if(system.contains("\"questions\"")) {
                sawPractice=true;assertTrue(system.contains("用简洁题干"));assertFalse(system.contains("翻译用中文"))
                JSONObject().put("questions",JSONArray().apply { QuestionKind.entries.forEachIndexed { index,kind ->
                    put(JSONObject().put("kind",kind.name).put("entryId","query").put("stem",if(kind==QuestionKind.FILL_BLANK)"Choose ___ in this sentence." else "查询理解第${index+1}题")
                        .put("options",JSONArray(if(kind==QuestionKind.TRUE_FALSE)listOf("正确","错误") else listOf("选项甲","选项乙","选项丙","选项丁")))
                        .put("answer",0).put("explanation","基于当前查询的模拟解析").put("evidence",JSONArray().put("query")))
                } }).toString()
            } else if(system.contains("连接测试"))"OK" else {
                sawTranslate=true;assertTrue(system.contains("翻译用中文"));assertFalse(system.contains("用简洁题干"))
                """{"direction":"EN_TO_ZH","translation":"模拟的未收录查询译文","analysis":"模拟解释"}"""
            }
        }
        try {
            prefs.edit().clear().commit()
            AiSkillStore(context).save(AiSkill("skill-trans","翻译","翻译用中文",SkillScope.TRANSLATE))
            AiSkillStore(context).save(AiSkill("skill-quiz","练习","用简洁题干",SkillScope.PRACTICE))
            instrumentation.runOnMainSync {
                vm=ViewModelProvider(holder,object: ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=AppViewModel(context.applicationContext as Application,AiService(fake)) as T
                })[AppViewModel::class.java]
            }
            withTimeout(15000) { vm.state.first { it.ready } }
            instrumentation.runOnMainSync { vm.saveAi(AiSettings(baseUrl="https://fixture.invalid/v1",model="fixture-model"),"fake-learning-key",true) }
            withTimeout(10000) { vm.state.first { it.aiSettings.hasKey && !it.aiBusy } }
            instrumentation.runOnMainSync { vm.query(query,Direction.ZH_TO_EN) }
            withTimeout(10000) { vm.state.first { !it.busy && it.result?.passage?.aiGenerated==true } }
            assertTrue(sawTranslate);assertEquals(Direction.EN_TO_ZH,vm.state.value.result!!.direction)
            assertTrue(vm.state.value.result!!.entries.isEmpty())
            instrumentation.runOnMainSync { vm.startPractice(emptySet(),3,useAi=true,kinds=QuestionKind.entries.toSet()) { } }
            withTimeout(10000) { vm.state.first { !it.practiceBusy && it.questions.size==3 } }
            assertTrue(sawPractice);assertEquals(QuestionKind.entries.toSet(),vm.state.value.questions.map { it.kind }.toSet())
            ids.addAll(vm.state.value.questions.map { it.id })
            instrumentation.runOnMainSync { vm.select(1);vm.submit() }
            withTimeout(10000) { vm.state.first { it.submitted } }
            val repo=LocalRepository(context)
            try {
                repo.initialize();val wrong=repo.questions(wrongOnly=true).first { it.id in ids }
                repo.editQuestion(wrong.copy(stem="修订后的题干",explanation="修订后的解析",answer=1))
                val edited=repo.questions(wrongOnly=true).first { it.id==wrong.id }
                assertTrue(edited.edited);assertEquals("修订后的题干",edited.stem);assertEquals(1,edited.answer)
                val backup=UserBackupCodec.decode(repo.exportUserData())
                assertTrue(backup.generated.any { it.id==wrong.id && it.edited && it.answer==1 });assertEquals(2,backup.skills.size)
                assertFalse(repo.exportUserData().toString(Charsets.UTF_8).contains("fake-learning-key"))
                repo.importUserData(backup)
                assertEquals(3,repo.questions().count { it.id in ids })
                val old=JSONObject(repo.exportUserData().toString(Charsets.UTF_8)).put("schemaVersion",1).put("generatedQuestions",JSONArray()).apply { remove("skills") }
                assertTrue(UserBackupCodec.decode(old.toString().toByteArray()).skills.isEmpty())
            } finally { repo.close() }
            instrumentation.runOnMainSync { vm.testAi(vm.state.value.aiSettings,"") }
            withTimeout(10000) { vm.state.first { !it.aiBusy && it.aiConnected } }
            assertTrue(vm.state.value.aiNotice.startsWith("连接成功"))
        } finally {
            instrumentation.runOnMainSync { holder.clear() }
            val db=android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("personal.db").path,null,0)
            try { ids.forEach { id -> listOf("generated_question" to "id","wrong_question" to "question_id","practice_answer" to "question_id").forEach { (table,column) -> db.delete(table,"$column=?",arrayOf(id)) } };db.delete("history","query=?",arrayOf(query)) } finally { db.close() }
            restore(prefs,before);reading.edit().putString("mode",oldMode).commit()
        }
    }
}
