package com.wenyantong.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class QueryAndAiUiTest {
    private val preferences=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("ai_private",0)
    private val original=preferences.all.toMap()
    @org.junit.After fun restoreAiSettings() { preferences.edit().clear().apply { original.forEach { (key,value) -> if(value is String)putString(key,value) } }.commit() }
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun exactWordShowsDefinitionsInsteadOfMissingTranslationAndAiFormCanBeConfigured() {
        compose.waitUntil(15000) { compose.onAllNodesWithText("开始探索").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextInput("之")
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("翻译与解析  →"))
        compose.onNodeWithText("翻译与解析  →").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("已收录词条").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("尚无收录译文").assertDoesNotExist()
        compose.onNodeWithText("未找到本地收录内容").assertDoesNotExist()
        compose.onNodeWithText("查看完整释义与来源").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("AI 设置").performScrollTo().performClick()
        compose.onNodeWithText("自定义渠道").performScrollTo().performClick()
        compose.onNode(hasSetTextAction() and hasText("API Base URL")).performScrollTo().performTextInput("https://example.invalid/v1")
        compose.onNode(hasSetTextAction() and hasText("模型 ID")).performScrollTo().performTextInput("ui-test-model")
        compose.onNode(hasSetTextAction() and hasText("API Key")).performScrollTo().performTextInput("ui-fixture-not-real")
        compose.onNodeWithText("仅保存").performScrollTo().performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("AI 配置已加密保存。").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("清除 AI 配置和密钥").performScrollTo().performClick()
        compose.onNodeWithText("清除").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("AI 配置和密钥已清除。").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun publicCatalogCanFilterEnglishAndVocabularyTypes() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveEnglishCatalog")=="true")
        compose.waitUntil(15000) { compose.onAllNodesWithText("开始探索").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("离线资源").performScrollTo().performClick()
        compose.onNodeWithText("检查资源更新").performScrollTo().performClick()
        compose.onNodeWithText("允许检查").performClick()
        compose.waitUntil(60000) { compose.onAllNodesWithTag("resource-english.cet6.kyle").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("英文",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("英文词汇",substring=false).performScrollTo().performClick()
        compose.onNodeWithTag("resource-english.cet6.kyle").assertExists()
        compose.onNodeWithTag("resource-english.cet.examdata").assertExists()
        compose.onNodeWithTag("resource-reading.guwenguanzhi").assertDoesNotExist()
    }
}
