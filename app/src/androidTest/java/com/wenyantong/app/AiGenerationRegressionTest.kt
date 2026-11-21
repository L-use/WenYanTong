package com.wenyantong.app

import com.wenyantong.app.core.model.*
import com.wenyantong.app.feature.ai.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiGenerationRegressionTest {
    private val settings=AiSettings(baseUrl="https://fixture.invalid/v1",model="fixture")
    private val result=QueryResult("test query",Direction.EN_TO_ZH,null,emptyList())
    private fun batch(kind: String?,count: Int,options: List<String> = listOf("甲","乙","丙","丁"))=JSONObject().put("questions",JSONArray().apply {
        repeat(count) { i -> put(JSONObject().apply {
            if(kind!=null)put("kind",kind)
            put("entryId","query");put("stem","选择正确内容 ___ 第${i+1}题");put("options",JSONArray(options));put("answer",0);put("explanation","测试解析");put("evidence",JSONArray().put("query"))
        }) }
    }).toString()
    @Test fun singleChoiceFiveQuestionsRegeneratesWrongKindOnce()=runBlocking {
        var calls=0
        val service=AiService(AiTransport { _,_,system,_,_ ->
            calls++
            assertTrue(system.contains("每道题的 kind 都必须等于 SINGLE"))
            assertTrue(system.contains("\"kind\":\"SINGLE\""))
            assertFalse(system.contains("TRUE_FALSE"));assertFalse(system.contains("FILL_BLANK"))
            if(calls==1)batch("TRUE_FALSE",5,listOf("正确","错误")) else {
                assertTrue(system.contains("上一批未通过"));batch("SINGLE",5)
            }
        })
        val questions=service.generate(settings,"fake-key",result,emptyList(),5)
        assertEquals(2,calls);assertEquals(5,questions.size)
        assertTrue(questions.all { it.kind==QuestionKind.SINGLE && it.options.size==4 })
    }
    @Test fun commonNamesAndMissingSingleSelectedKindRemainStrictlyValidated() {
        for(alias in listOf("单选题","single_choice","single choice","SINGLE_CHOICE_QUESTION")) {
            assertEquals(QuestionKind.SINGLE,AiCodec.questions(batch(alias,1),emptyList(),1,"fixture",result).single().kind)
        }
        assertEquals(QuestionKind.TRUE_FALSE,AiCodec.questions(batch(null,1,listOf("正确","错误")),emptyList(),1,"fixture",result,setOf(QuestionKind.TRUE_FALSE)).single().kind)
        assertEquals(QuestionKind.FILL_BLANK,AiCodec.questions(batch("选词填空",1),emptyList(),1,"fixture",result,setOf(QuestionKind.FILL_BLANK)).single().kind)
        try { AiCodec.questions(batch("TRUE_FALSE",1,listOf("正确","错误")),emptyList(),1,"fixture",result);fail("Wrong kind was relabeled") } catch(expected: IllegalArgumentException) { }
        try { AiCodec.questions(batch("单选题",1,listOf("正确","错误")),emptyList(),1,"fixture",result);fail("Wrong structure accepted") } catch(expected: IllegalArgumentException) { }
    }
    @Test fun regenerationIsBoundedAndCannotBypassSourceValidation()=runBlocking {
        var calls=0
        val bad=JSONObject(batch("SINGLE",3)).apply { getJSONArray("questions").getJSONObject(0).put("entryId","invented-entry") }.toString()
        val service=AiService(AiTransport { _,_,_,_,_ -> calls++;bad })
        try { service.generate(settings,"fake-key",result,emptyList(),3);fail("Invalid source accepted") }
        catch(expected: IllegalStateException) { assertTrue(expected.message.orEmpty().contains("连续两次")) }
        assertEquals(2,calls)
    }
    @Test fun networkFailureAndCancellationDoNotTriggerAnotherRequest()=runBlocking {
        var calls=0
        val network=AiService(AiTransport { _,_,_,_,_ -> calls++;throw java.io.IOException("fixture failure") })
        try { network.generate(settings,"fake-key",result,emptyList(),3);fail("Network failure swallowed") } catch(expected: java.io.IOException) { }
        assertEquals(1,calls)
        calls=0
        val cancelled=AiService(AiTransport { _,_,_,_,_ -> calls++;throw CancellationException("fixture cancellation") })
        try { cancelled.generate(settings,"fake-key",result,emptyList(),3);fail("Cancellation swallowed") } catch(expected: CancellationException) { }
        assertEquals(1,calls)
    }
}
