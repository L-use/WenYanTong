package com.wenyantong.app

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.wenyantong.app.core.storage.LocalRepository
import com.wenyantong.app.feature.ai.*
import com.wenyantong.app.feature.storage.UserBackupCodec
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class AiIntegrationTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun restore(prefs: android.content.SharedPreferences,values: Map<String,*>) { prefs.edit().clear().apply { values.forEach { (key,value) -> if(value is String)putString(key,value) } }.commit() }
    @Test fun encryptedSettingsMockQueryGroundedPracticeAndBackupRoundTrip() = runBlocking {
        val preferences=context.getSharedPreferences("ai_private",0);val old=preferences.all.toMap()
        val reading=context.getSharedPreferences("reading",0);val oldMode=reading.getString("mode","OFFLINE")
        val store=ViewModelStore();lateinit var vm: AppViewModel;var calls=0;val ids=mutableListOf<String>()
        val fake=AiTransport { _,secret,system,user,_ ->
            assertEquals("fixture-not-a-real-api-key",secret);assertFalse(user.contains(secret));calls++
            if(system.contains("\"questions\"")) {
                val input=JSONObject(user);assertEquals("之",input.getString("query"))
                val entry=input.getJSONArray("entries").getJSONObject(0);val sense=entry.getJSONArray("senses").getJSONObject(0)
                JSONObject().put("questions",JSONArray().apply { repeat(3) { i -> put(JSONObject().put("entryId",entry.getString("entryId")).put("stem","测试词义辨析第${i+1}题").put("options",JSONArray(listOf("代指所学内容","到某地","结构助词","转折连词"))).put("answer",0).put("explanation","依据本次给定的本地词义。").put("evidence",JSONArray().put(sense.getString("senseId")))) } }).toString()
            } else "{\"translation\":\"模拟参考译文\",\"analysis\":\"仅用于测试协议联调\"}"
        }
        try {
            preferences.edit().clear().commit()
            instrumentation.runOnMainSync {
                vm=ViewModelProvider(store,object: ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=AppViewModel(context.applicationContext as Application,AiService(fake)) as T
                })[AppViewModel::class.java]
            }
            withTimeout(15000) { vm.state.first { it.ready } }
            instrumentation.runOnMainSync { vm.saveAi(AiSettings(baseUrl="https://example.invalid/v1",model="fixture-model"),"fixture-not-a-real-api-key",true) }
            withTimeout(10000) { vm.state.first { it.aiSettings.hasKey && !it.aiBusy } }
            assertFalse(preferences.all.values.joinToString().contains("fixture-not-a-real-api-key"))
            assertEquals("fixture-not-a-real-api-key",AiSettingsStore(context).secret())
            instrumentation.runOnMainSync { vm.query("之") }
            withTimeout(10000) { vm.state.first { !it.busy && it.result?.query=="之" } }
            assertTrue(vm.state.value.result!!.exactWordHit);assertEquals(0,calls)
            instrumentation.runOnMainSync { vm.query("完全未收录的测试语句XYZ") }
            withTimeout(10000) { vm.state.first { !it.busy && it.result?.passage?.aiGenerated==true } }
            assertEquals(1,calls)
            instrumentation.runOnMainSync { vm.query("之") }
            withTimeout(10000) { vm.state.first { !it.busy && it.result?.query=="之" } }
            instrumentation.runOnMainSync { vm.startPractice(setOf("zhi"),3,useAi=true) { } }
            withTimeout(10000) { vm.state.first { !it.practiceBusy && it.questions.size==3 } }
            ids.addAll(vm.state.value.questions.map { it.id });assertTrue(ids.all { it.startsWith("ai-") })
            instrumentation.runOnMainSync { vm.select(1);vm.submit() }
            withTimeout(10000) { vm.state.first { it.submitted } }
            val repo=LocalRepository(context)
            try {
                repo.initialize();assertTrue(repo.questions(wrongOnly=true).any { it.id in ids })
                val bytes=repo.exportUserData();assertFalse(bytes.toString(Charsets.UTF_8).contains("fixture-not-a-real-api-key"))
                val backup=UserBackupCodec.decode(bytes);assertTrue(backup.generated.any { it.id in ids });repo.importUserData(backup)
                assertEquals(3,repo.questions(setOf("zhi")).count { it.id in ids })
            } finally { repo.close() }
            instrumentation.runOnMainSync { vm.mode(com.wenyantong.app.core.model.Mode.OFFLINE);vm.query("另一个未收录测试ZZZ") }
            withTimeout(10000) { vm.state.first { !it.busy } };assertEquals(2,calls)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            val db=android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("personal.db").path,null,0)
            try { ids.forEach { id -> listOf("generated_question" to "id","wrong_question" to "question_id","practice_answer" to "question_id").forEach { (table,column) -> db.delete(table,"$column=?",arrayOf(id)) } } } finally { db.close() }
            restore(preferences,old);reading.edit().putString("mode",oldMode).commit()
        }
    }
    @Test fun transportProtocolsAndInvalidEvidenceAreCheckedWithoutRealCredentials() = runBlocking {
        class Connection(url: URL): HttpURLConnection(url) {
            val sent=java.io.ByteArrayOutputStream();var status=200
            override fun connect() { };override fun disconnect() { };override fun usingProxy()=false
            override fun getOutputStream()=sent
            override fun getResponseCode()=status
            override fun getInputStream()="{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}".byteInputStream()
        }
        val connection=Connection(URL("https://example.invalid/v1/chat/completions"))
        val transport=AiHttpTransport { connection }
        assertEquals("OK",transport.post(AiSettings(baseUrl="https://example.invalid/v1",model="fixture"),"fake-key","system","user",32))
        assertEquals("Bearer fake-key",connection.getRequestProperty("Authorization"));assertFalse(connection.instanceFollowRedirects)
        assertFalse(connection.sent.toString("UTF-8").contains("fake-key"))
        val claude=AiCodec.request(AiSettings(AiProtocol.CLAUDE,"https://example.invalid/v1","fixture"),"system","user",32)
        assertEquals("system",claude.getString("system"));assertEquals(1,claude.getJSONArray("messages").length())
        assertEquals("OK",AiCodec.response(AiProtocol.CLAUDE,JSONObject("{\"content\":[{\"type\":\"text\",\"text\":\"OK\"}]}")))
        val entry=com.wenyantong.app.core.model.Entry("allowed","之","zhī","代词",listOf(com.wenyantong.app.core.model.Sense("sense-ok","代词","代词","例句","译文","出处")))
        val invalid=JSONObject().put("questions",JSONArray().put(JSONObject().put("entryId","allowed").put("stem","题目").put("options",JSONArray(listOf("A","B","C","D"))).put("answer",0).put("explanation","解释").put("evidence",JSONArray().put("invented-sense"))))
        try { AiCodec.questions(invalid.toString(),listOf(entry),1,"fixture");fail("Ungrounded question accepted") } catch(expected: IllegalStateException) { }
        connection.status=302
        try { transport.post(AiSettings(baseUrl="https://example.invalid/v1",model="fixture"),"fake-key","system","user",32);fail("Redirect accepted") } catch(expected: IllegalStateException) { assertFalse(expected.message.orEmpty().contains("fake-key")) }
    }
}
