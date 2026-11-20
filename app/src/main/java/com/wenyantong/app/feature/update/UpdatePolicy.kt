package com.wenyantong.app.feature.update

import com.wenyantong.app.core.model.Mode
import java.net.URI

data class AppUpdate(
    val versionCode: Int, val versionName: String, val tag: String,
    val apkUrl: String, val bytes: Long, val sha256: String, val minSdk: Int,
    val notes: String, val releaseUrl: String
)

object UpdatePolicy {
    const val MAX_APK_BYTES = 150L * 1024 * 1024
    const val CHECK_INTERVAL = 24L * 60 * 60 * 1000
    fun shouldAutoCheck(mode: Mode, enabled: Boolean, lastCheck: Long, now: Long, connected: Boolean): Boolean =
        mode != Mode.OFFLINE && enabled && connected && (lastCheck == 0L || now < lastCheck || now - lastCheck >= CHECK_INTERVAL)

    fun trustedConnection(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase() ?: return false
        uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1,443) &&
            (host == "api.github.com" || host == "github.com" || host.endsWith(".githubusercontent.com"))
    }.getOrDefault(false)

    fun releaseAsset(url: String, repository: String, tag: String): Boolean = runCatching {
        val uri = URI(url)
        trustedConnection(url) && uri.host == "github.com" && uri.rawQuery == null && uri.rawFragment == null &&
            uri.rawPath.startsWith("/$repository/releases/download/$tag/") &&
            !uri.rawPath.contains("..") && !uri.rawPath.contains('%') &&
            uri.rawPath.substringAfterLast('/').isNotBlank()
    }.getOrDefault(false)

    fun validate(update: AppUpdate, repository: String) {
        require(update.versionCode > 0 && update.versionName.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "更新版本格式不正确。" }
        require(update.tag == "v${update.versionName}") { "更新标签与版本不一致。" }
        require(update.bytes in 1..MAX_APK_BYTES) { "安装包大小超出允许范围。" }
        require(update.sha256.matches(Regex("[a-f0-9]{64}"))) { "安装包校验信息不正确。" }
        require(update.minSdk >= 26 && update.notes.length <= 20000) { "更新信息不完整。" }
        require(update.releaseUrl == "https://github.com/$repository/releases/tag/${update.tag}") { "更新页面来源不正确。" }
        require(releaseAsset(update.apkUrl,repository,update.tag) && update.apkUrl.endsWith(".apk")) { "安装包来源不可信。" }
    }
}
