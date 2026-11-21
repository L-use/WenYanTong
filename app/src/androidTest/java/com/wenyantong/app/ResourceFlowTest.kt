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
        compose.onNodeWithText("查看词条").performScrollTo().performClick()
        compose.onNodeWithText("古汉语词典").assertExists()
    }
    @Test fun downloadPublishedLibraryAndBrowseNewEntry() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveResources")=="true")
        openResources()
        compose.onNodeWithText("检查资源更新").performScrollTo().performClick()
        compose.onNodeWithText("允许检查").performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("下载并安装资源包").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("下载并安装资源包").performScrollTo().performClick()
        compose.waitUntil(60_000) { compose.onAllNodesWithText("资源库安装成功，词典、古文和练习已更新。").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("查看词条").performScrollTo().performClick()
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
    private fun openResources() {
        compose.waitUntil(15_000) { compose.onAllNodesWithText("开始探索").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("离线资源").performScrollTo().performClick()
        compose.onNodeWithText("常用文言文基础库").assertExists()
    }
}
