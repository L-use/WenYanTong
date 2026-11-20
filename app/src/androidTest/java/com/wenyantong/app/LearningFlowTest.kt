package com.wenyantong.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import java.io.File
import org.junit.Rule
import org.junit.Test

class LearningFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun queryCharacterOpenSenseAndFinishPractice() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("开始探索").fetchSemanticsNodes().isNotEmpty() }
        screenshot("01-home")
        compose.onNode(hasSetTextAction()).performTextInput("之")
        scrollListTo("翻译与解析  →")
        compose.onNodeWithText("翻译与解析  →").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("字词理解").fetchSemanticsNodes().isNotEmpty() }
        screenshot("02-result")
        compose.onNodeWithText("zhī").performScrollTo().performClick()
        compose.onNodeWithText("释义与例句").assertIsDisplayed()
        screenshot("03-senses")
        compose.onNodeWithText("练习「之」的用法").performScrollTo().performClick()
        compose.onNodeWithText("开始练习").performScrollTo().performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("提交答案").fetchSemanticsNodes().isNotEmpty() }
        screenshot("04-practice")
        compose.onNodeWithText("A").performScrollTo().performClick()
        compose.onNodeWithText("提交答案").performScrollTo().performClick()
        waitForFeedback()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("下一题"))
        screenshot("05-feedback")
        repeat(3) { index ->
            compose.onNodeWithText("下一题").performScrollTo().performClick()
            compose.onNodeWithText("A").performScrollTo().performClick()
            compose.onNodeWithText("提交答案").performScrollTo().performClick()
            val button=if(index==2) "查看练习报告" else "下一题"
            waitForFeedback()
            compose.onNode(hasScrollAction()).performScrollToNode(hasText(button))
        }
        compose.onNodeWithText("查看练习报告").performScrollTo().performClick()
        compose.onNodeWithText("练习完成").assertIsDisplayed()
        screenshot("06-report")
        compose.onNodeWithText("完成，返回练习").performScrollTo().performClick()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("主题与阅读").performScrollTo().performClick()
        compose.onNodeWithText("深色").performClick()
        screenshot("07-dark-reading")
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .performSemanticsAction(SemanticsActions.SetProgress) { it(26f) }
        screenshot("08-large-reading")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("首页").performClick()
        screenshot("08b-home-after-navigation")
        scrollListTo("翻译与解析  →")
        compose.onNodeWithText("翻译与解析  →").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("原文").fetchSemanticsNodes().isNotEmpty() }
        scrollListTo("zhī")
        compose.onNodeWithText("zhī").performClick()
        compose.onNodeWithText("练习「之」的用法").performScrollTo().assertIsDisplayed()
        screenshot("09-large-senses")
        compose.onNodeWithContentDescription("关闭释义").performScrollTo().performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("数据与存储").performScrollTo().performClick()
        compose.onNodeWithText("导出用户数据").assertIsDisplayed()
        compose.onNodeWithText("导入用户数据").assertIsDisplayed()
        screenshot("10-user-data-backup")
        compose.onNodeWithText("用户学习数据库").performScrollTo().assertIsDisplayed()
        screenshot("11-local-data-paths")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("版本更新").performScrollTo().performClick()
        compose.onNodeWithText("检查更新").performScrollTo().performClick()
        compose.onNodeWithText("本次允许连接 GitHub？").assertIsDisplayed()
        screenshot("12-offline-update-consent")
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("检查是否有新版本").assertIsDisplayed()
    }

    private fun waitForFeedback() {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("正确答案：",substring=true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun scrollListTo(text: String) {
        try {
            compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex))
                .performScrollToNode(hasText(text))
        } catch (failure: AssertionError) {
            throw AssertionError("Could not scroll to $text\n" + compose.onRoot().printToString(), failure)
        }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        // Allow the emulator compositor to present the frame before taking a physical screenshot.
        android.os.SystemClock.sleep(200)
        val dir=File(instrumentation.targetContext.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
}
