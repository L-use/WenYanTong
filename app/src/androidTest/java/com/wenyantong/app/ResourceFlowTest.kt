package com.wenyantong.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.File

class ResourceFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun browseResourcesAndRespectOfflineConsent() {
        openResources()
        compose.onNodeWithText("检查资源更新").performScrollTo().performClick()
        compose.onNodeWithText("连接 GitHub 检查资源？").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("全局查词").performScrollTo().performClick()
        compose.onNodeWithText("古汉语词典").assertExists()
    }
    @Test fun downloadPublishedLibraryAndBrowseNewEntry() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveResources")=="true")
        openResources()
        compose.onNodeWithText("检查资源更新").performScrollTo().performClick()
        compose.onNodeWithText("允许检查").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("resource-sample.basic").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText("下载此包") and hasAnyAncestor(hasTestTag("resource-sample.basic"))).performScrollTo().performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("资源包安装成功，已加入全局搜索。").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("全局查词").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("濯")
        compose.onNodeWithText("zhuó").performScrollTo().performClick()
        compose.onAllNodesWithText("洗涤。").onFirst().assertExists()
        compose.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val folder=File(instrumentation.targetContext.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder,"14-downloaded-resource-entry.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
    @Test fun downloadLargeIndependentProsePackSearchAndRead() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveLargeUi")=="true")
        openResources()
        compose.onNodeWithText("检查资源更新").performScrollTo().performClick()
        compose.onNodeWithText("允许检查").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithTag("resource-reading.guwenguanzhi").fetchSemanticsNodes().isNotEmpty() }
        if(compose.onAllNodes(hasText("下载此包") and hasAnyAncestor(hasTestTag("resource-reading.guwenguanzhi"))).fetchSemanticsNodes().isEmpty()) {
            compose.onNode(hasText("删除") and hasAnyAncestor(hasTestTag("resource-reading.guwenguanzhi"))).performScrollTo().performClick()
            compose.onNodeWithText("删除此包").performClick()
            compose.waitUntil(30_000) { compose.onAllNodes(hasText("下载此包") and hasAnyAncestor(hasTestTag("resource-reading.guwenguanzhi"))).fetchSemanticsNodes().isNotEmpty() }
        }
        compose.onNode(hasText("下载此包") and hasAnyAncestor(hasTestTag("resource-reading.guwenguanzhi"))).performScrollTo().performClick()
        compose.waitUntil(120_000) { compose.onAllNodesWithText("资源包安装成功，已加入全局搜索。").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("全局查词").performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).performTextInput("学者")
        compose.onNodeWithContentDescription("查看学者的释义").performScrollTo().performClick()
        compose.onAllNodesWithText("查看完整来源").onFirst().performScrollTo().performClick()
        compose.onAllNodesWithText("上游 AI 生成",substring=true).onFirst().assertExists()
        compose.onNodeWithContentDescription("关闭释义").performScrollTo().performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNode(hasText("浏览此包") and hasAnyAncestor(hasTestTag("resource-reading.guwenguanzhi"))).performScrollTo().performClick()
        compose.waitUntil(20_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction()).performTextInput("师说")
        compose.waitUntil(20_000) { compose.onAllNodesWithText("师说").fetchSemanticsNodes().size>1 }
        compose.onNode(hasText("师说") and hasClickAction() and !hasSetTextAction()).performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithText("古文阅读").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("古之学者必有师",substring=true).onFirst().assertExists()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val folder=File(instrumentation.targetContext.getExternalFilesDir(null),"screenshots").apply { mkdirs() }
        compose.waitForIdle()
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder,"16-multipack-article.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    private fun openResources() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("开始探索").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("离线资源").performScrollTo().performClick()
        compose.onNodeWithText("检查资源更新").assertExists()
    }
}
