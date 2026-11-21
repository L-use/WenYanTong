package com.wenyantong.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import com.wenyantong.app.core.model.Question
import com.wenyantong.app.core.storage.LocalRepository
import kotlinx.coroutines.runBlocking

class LearningUpgradeUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val prefs=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("ai_private",0)
    private val before=prefs.all.toMap()
    private val reading=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("reading",0)
    private val oldMode=reading.getString("mode","OFFLINE")
    init { reading.edit().putString("mode","OFFLINE").commit() }
    @org.junit.After fun restore() { prefs.edit().clear().apply { before.forEach { (k,v) -> if(v is String)putString(k,v) } }.commit();reading.edit().putString("mode",oldMode).commit() }
    @Test fun twoModeSelectorUnknownQueryPracticeAndSkillsCrudAreVisible() {
        compose.waitUntil(15000) { compose.onAllNodesWithText("开始探索").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("本地资源",substring=false).assertExists()
        compose.onNodeWithText("离线",substring=false).performClick()
        compose.onNodeWithText("自动",substring=false).performClick()
        compose.onNodeWithText("本地资源",substring=false).assertDoesNotExist()
        compose.onNodeWithText("自动",substring=false).performClick()
        compose.onNodeWithText("离线",substring=false).performClick()
        compose.onNodeWithText("文言白话互译",substring=false).performClick()
        compose.onNodeWithText("中英互译",substring=false).performClick()
        compose.onNodeWithText("中英互译",substring=false).assertExists()
        compose.onNodeWithText("中文 → English",substring=false).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).performTextInput("xyzunknownqueryfixture")
        compose.onNodeWithText("翻译与解析  →").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("围绕本次查询练一练").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("围绕本次查询练一练").performScrollTo().performClick()
        compose.onNodeWithText("判断题",substring=false).performScrollTo().assertExists()
        compose.onNodeWithText("选词填空",substring=false).assertExists()
        compose.onNodeWithText("设置",substring=false).performClick()
        compose.onNodeWithText("AI 设置",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("添加 API",substring=false).assertExists()
        compose.onNodeWithText("Skills 管理",substring=true).performScrollTo().performClick()
        compose.onNodeWithText("双向翻译与解析",substring=false).assertExists()
        compose.onNodeWithText("查询内容出题",substring=false).assertExists()
        screenshot("18-skills-list")
        compose.onNodeWithText("创建 Skill",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("技能名称",substring=false).performTextInput("界面测试技能")
        compose.onNodeWithText("技能指令",substring=false).performTextInput("请先讲词义，再举例句。")
        compose.onNodeWithText("保存技能",substring=false).performClick()
        compose.onNodeWithText("界面测试技能",substring=false).performScrollTo().assertExists()
        compose.onNodeWithText("编辑技能",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("技能名称",substring=false).performTextReplacement("修改后的技能")
        compose.onNodeWithText("保存技能",substring=false).performClick()
        compose.onNodeWithText("修改后的技能",substring=false).performScrollTo().assertExists()
        compose.onNodeWithText("删除技能",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("删除",substring=false).performClick()
        compose.onNodeWithText("修改后的技能",substring=false).assertDoesNotExist()
    }
    @Test fun wrongQuestionEditorChangesStemOptionsAndCorrectAnswer()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val id="ai-ui-edit-${java.util.UUID.randomUUID()}"
        val repo=LocalRepository(context)
        try {
            repo.initialize()
            val q=Question(id,"界面错题原题干",listOf("甲项","乙项","丙项","丁项"),0,"界面原解析","query")
            repo.saveGenerated(listOf(q),"界面测试")
            repo.recordAnswer("ui-edit-session",q,1)
            compose.activityRule.scenario.recreate()
            compose.waitUntil(15000) { compose.onAllNodesWithText("开始探索").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("练习",substring=false).performClick()
            compose.onNodeWithText("我的错题本",substring=false).performScrollTo().performClick()
            compose.onNode(hasScrollAction()).performScrollToNode(hasTestTag("wrong-$id"))
            compose.onNode(hasText("编辑错题") and hasAnyAncestor(hasTestTag("wrong-$id"))).performClick()
            compose.onNodeWithText("题干",substring=false).performTextReplacement("界面修改后的题干")
            compose.onAllNodes(isSelectable())[1].performClick()
            screenshot("19-wrong-editor")
            compose.onNodeWithText("保存修改",substring=false).performClick()
            compose.waitUntil(10000) { compose.onAllNodesWithText("错题修改已保存。").fetchSemanticsNodes().isNotEmpty() }
            val saved=repo.questions(wrongOnly=true).first { it.id==id }
            org.junit.Assert.assertEquals("界面修改后的题干",saved.stem)
            org.junit.Assert.assertEquals(1,saved.answer)
        } finally {
            repo.close()
            val db=android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath("personal.db").path,null,0)
            try { listOf("generated_question" to "id","wrong_question" to "question_id","practice_answer" to "question_id").forEach { (table,column) -> db.delete(table,"$column=?",arrayOf(id)) } } finally { db.close() }
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle();val instrumentation=InstrumentationRegistry.getInstrumentation();instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(250)
        val folder=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(folder,"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
}
