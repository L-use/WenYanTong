package com.wenyantong.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.wenyantong.app.feature.ai.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL

class AiModelDiscoveryTest {
    @get:Rule val compose=createComposeRule()
    private class Connection(url: URL,val body: String,val status: Int=200): HttpURLConnection(url) {
        var disconnected=false
        override fun connect() { }
        override fun disconnect() { disconnected=true }
        override fun usingProxy()=false
        override fun getResponseCode()=status
        override fun getInputStream()=body.byteInputStream()
    }
    @Test fun protocolsPaginationAndModalityFilteringUseAuthenticatedGet()=runBlocking {
        val requests=mutableListOf<Connection>()
        val client=AiModelClient { url ->
            Connection(url,if(url.query==null)"""{"data":[{"id":"text-a","display_name":"文本甲"},{"id":"image-only","output_modalities":["image"]},{"id":"text-embedding-3-small"}],"has_more":true,"last_id":"text-a"}"""
                else """{"data":[{"id":"text-b"},{"id":"text-a"}],"has_more":false}""").also { requests+=it }
        }
        val result=client.list(AiSettings(AiProtocol.CLAUDE,"https://example.invalid/v1/messages"),"fixture-key")
        assertEquals(listOf("text-a","text-b"),result.map { it.id })
        assertEquals(2,requests.size)
        assertTrue(requests[1].url.query.contains("after_id=text-a"))
        requests.forEach { assertEquals("GET",it.requestMethod);assertEquals("fixture-key",it.getRequestProperty("x-api-key"));assertEquals("2023-06-01",it.getRequestProperty("anthropic-version"));assertFalse(it.instanceFollowRedirects);assertFalse(it.doOutput);assertTrue(it.disconnected) }
        val bearer=Connection(URL("https://example.invalid/v1/models"),"""{"data":[{"id":"text"}]}""")
        assertEquals("text",AiModelClient { bearer }.list(AiSettings(baseUrl="https://example.invalid/v1"),"fixture-key").single().id)
        assertEquals("Bearer fixture-key",bearer.getRequestProperty("Authorization"))
        val bailian=JSONObject("""{"output":{"total":101,"page_no":1,"page_size":100,"models":[{"model":"qwen-text","name":"千问","inference_metadata":{"response_modality":["Text"]}}]}}""")
        assertEquals("qwen-text",AiModelClient.parse(bailian).single().id)
        assertEquals("https://example.invalid/api/v1/models?page_no=2&page_size=100",AiModelClient.nextPage("https://example.invalid/api/v1/models",bailian,AiProtocol.COMPATIBLE))
    }
    @Test fun errorsAndPaginationNeverForwardKeysToAnotherHost()=runBlocking {
        for(status in listOf(302,401,404,429)) {
            val connection=Connection(URL("https://example.invalid/v1/models"),"fixture-key",status)
            try { AiModelClient { connection }.list(AiSettings(baseUrl="https://example.invalid/v1"),"fixture-key");fail("HTTP $status accepted") }
            catch(expected: IllegalStateException) { assertFalse(expected.message.orEmpty().contains("fixture-key")) }
        }
        var calls=0
        try {
            AiModelClient { url -> calls++;Connection(url,"""{"data":[{"id":"text"}],"next":"https://other.invalid/v1/models"}""") }
                .list(AiSettings(baseUrl="https://example.invalid/v1"),"fixture-key")
            fail("Cross-host next accepted")
        } catch(expected: AiModelException) { assertEquals(1,calls) }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("ai_private",0);val before=prefs.all.toMap()
        try {
            prefs.edit().clear().commit()
            val store=AiSettingsStore(context);val settings=AiSettings(baseUrl="https://example.invalid/v1",model="text")
            store.save(settings,"fixture-key")
            assertEquals("fixture-key",store.discoveryKey(settings.copy(model=""),""))
            try { store.discoveryKey(settings.copy(baseUrl="https://other.invalid/v1"),"");fail("Saved key reused on another host") } catch(expected: AiModelException) { }
            assertEquals("draft-key",store.discoveryKey(settings.copy(baseUrl="https://other.invalid/v1"),"draft-key"))
            assertEquals("fixture-key",store.secret())
        } finally { prefs.edit().clear().apply { before.forEach { (k,v) -> if(v is String)putString(k,v) } }.commit() }
    }
    @Test fun pickerSearchesAndSelectsReturnedModelId() {
        var selected=""
        compose.setContent { MaterialTheme { AiModelPicker(listOf(AiModelOption("text-a","甲模型"),AiModelOption("text-b","乙模型")),{}) { selected=it } } }
        compose.onNodeWithText("搜索模型").performTextInput("乙")
        compose.onNodeWithText("text-a").assertDoesNotExist()
        compose.onNodeWithText("text-b").performClick()
        compose.runOnIdle { assertEquals("text-b",selected) }
    }
    @Test fun providerAndKeyAutomaticallyLoadModelsAndSelectedModelIsSaved() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefs=context.getSharedPreferences("ai_private",0);val before=prefs.all.toMap()
        val store=ViewModelStore();lateinit var vm: AppViewModel
        val calls=java.util.concurrent.atomic.AtomicInteger()
        val client=AiModelClient { url ->
            assertEquals("https://api.deepseek.com/models",url.toString())
            calls.incrementAndGet()
            Connection(url,"""{"data":[{"id":"fixture-text-a"},{"id":"fixture-text-b"}]}""")
        }
        try {
            prefs.edit().clear().commit()
            instrumentation.runOnMainSync {
                vm=ViewModelProvider(store,object: ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=AppViewModel(context.applicationContext as android.app.Application,AiService(),client) as T
                })[AppViewModel::class.java]
            }
            compose.setContent { MaterialTheme { AiSettingsPage(vm.state.collectAsState().value,vm,{}) } }
            compose.onNodeWithText("选择厂商：",substring=true).performScrollTo().performClick()
            compose.onNodeWithText("DeepSeek",substring=false).performClick()
            compose.onNode(hasSetTextAction() and hasText("API Key")).performScrollTo().performTextInput("fixture-key")
            compose.waitUntil(10000) { compose.onAllNodesWithText("选择模型（2）").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1,calls.get());assertFalse(prefs.contains("encrypted_key"))
            compose.onNodeWithText("选择模型（2）").performScrollTo().performClick()
            compose.onNodeWithText("搜索模型").performTextInput("text-b")
            compose.onNodeWithText("fixture-text-a").assertDoesNotExist()
            compose.onNodeWithText("fixture-text-b").performClick()
            compose.onNodeWithText("仅保存").performScrollTo().performClick()
            compose.waitUntil(10000) { AiSettingsStore(context).settings().model=="fixture-text-b" }
            assertEquals("fixture-key",AiSettingsStore(context).secret())
            assertFalse(prefs.all.values.joinToString().contains("fixture-key"))
            compose.onNodeWithText("选择厂商：",substring=true).performScrollTo().performClick()
            compose.onNodeWithTag("ai-provider-list").performScrollToNode(hasText("Claude · Anthropic"))
            compose.onNodeWithText("Claude · Anthropic").performClick()
            compose.onNodeWithText("刷新模型列表").performScrollTo().performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("请填写当前厂商的 API Key。").fetchSemanticsNodes().isNotEmpty() }
            assertEquals(1,calls.get());assertEquals("fixture-text-b",AiSettingsStore(context).settings().model)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            prefs.edit().clear().apply { before.forEach { (k,v) -> if(v is String)putString(k,v) } }.commit()
        }
    }
}
