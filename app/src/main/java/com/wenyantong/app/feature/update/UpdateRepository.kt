package com.wenyantong.app.feature.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import com.wenyantong.app.BuildConfig
import com.wenyantong.app.core.model.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class OfflineUpdateConsentRequired : IllegalStateException("离线模式需确认后才能连接 GitHub。")
class UpdateHttpError(val status: Int) : IllegalStateException(when(status) {
    404 -> "尚无可用的公开发布版本。"
    403,429 -> "GitHub 请求暂受限制，请稍后再试。"
    else -> "GitHub 暂时不可用，请稍后重试。"
})

fun interface UpdateTransport { fun open(url: String): InputStream }

class GitHubTransport : UpdateTransport {
    override fun open(url: String): InputStream {
        var next = url
        repeat(6) {
            check(UpdatePolicy.trustedConnection(next)) { "更新连接来源不可信。" }
            val connection = URL(next).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects=false
            connection.connectTimeout=12000
            connection.readTimeout=20000
            connection.setRequestProperty("User-Agent","WenYanTong/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Accept","application/vnd.github+json, application/octet-stream")
            val status = try { connection.responseCode } catch(e: Exception) { connection.disconnect();throw e }
            if(status in listOf(301,302,303,307,308)) {
                val location=connection.getHeaderField("Location")
                connection.disconnect()
                check(!location.isNullOrBlank()) { "更新下载地址无效。" }
                next=URL(URL(next),location).toString()
            } else {
                if(status !in 200..299) { connection.disconnect();throw UpdateHttpError(status) }
                val stream=try { connection.inputStream } catch(e: Exception) { connection.disconnect();throw e }
                return object : java.io.FilterInputStream(stream) {
                    override fun close() { try { super.close() } finally { connection.disconnect() } }
                }
            }
        }
        error("更新下载跳转过多。")
    }
}

class UpdateRepository(
    private val context: Context,
    private val transport: UpdateTransport = GitHubTransport(),
    private val installedVersion: Int = BuildConfig.VERSION_CODE
) {
    private val repository=BuildConfig.UPDATE_REPOSITORY
    private val prefs=context.getSharedPreferences("app_updates",Context.MODE_PRIVATE)
    fun autoCheckEnabled()=prefs.getBoolean("auto",true)
    fun autoCheckEnabled(enabled: Boolean) { prefs.edit { putBoolean("auto",enabled) } }
    fun lastCheck()=prefs.getLong("last_check",0)

    suspend fun check(mode: Mode, offlineConsent: Boolean = false): AppUpdate? = withContext(Dispatchers.IO) {
        if(mode==Mode.OFFLINE && !offlineConsent) throw OfflineUpdateConsentRequired()
        prefs.edit { putLong("last_check",System.currentTimeMillis()) }
        val release=JSONObject(readText("https://api.github.com/repos/$repository/releases/latest"))
        require(!release.optBoolean("draft") && !release.optBoolean("prerelease")) { "此版本尚未正式发布。" }
        val tag=release.getString("tag_name")
        require(tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+"))) { "发布标签格式不正确。" }
        val assets=release.getJSONArray("assets")
        val descriptor=(0 until assets.length()).map { assets.getJSONObject(it) }.singleOrNull { it.getString("name")=="update.json" }
            ?: error("此发布尚未提供应用更新信息。")
        val descriptorUrl=descriptor.getString("browser_download_url")
        require(UpdatePolicy.releaseAsset(descriptorUrl,repository,tag)) { "更新信息来源不可信。" }
        val json=JSONObject(readText(descriptorUrl))
        require(json.getInt("schemaVersion")==1 && json.getString("packageName")==context.packageName) { "此更新不适用于当前应用。" }
        val update=AppUpdate(json.getInt("versionCode"),json.getString("versionName"),json.getString("tag"),
            json.getString("apkUrl"),json.getLong("apkBytes"),json.getString("sha256"),json.getInt("minSdk"),
            json.getString("notes"),json.getString("releaseUrl"))
        UpdatePolicy.validate(update,repository)
        require(update.tag==tag) { "更新信息与发布版本不一致。" }
        require(update.minSdk<=Build.VERSION.SDK_INT) { "新版本暂不支持当前安卓系统。" }
        prefs.edit { putLong("last_check",System.currentTimeMillis()) }
        if(update.versionCode>installedVersion) update else null
    }

    private fun readText(url: String): String = transport.open(url).use { stream ->
        val bytes=stream.readNBytesCompat(256*1024+1)
        require(bytes.size<=256*1024) { "更新信息过大。" }
        bytes.toString(Charsets.UTF_8)
    }

    suspend fun download(update: AppUpdate, progress: (Long,Long)->Unit): File = withContext(Dispatchers.IO) {
        UpdatePolicy.validate(update,repository)
        require(update.versionCode>installedVersion) { "此版本无需安装。" }
        val folder=File(context.cacheDir,"app-updates").apply { mkdirs() }
        val target=File(folder,"WenYanTong-${update.versionCode}.apk")
        val partial=File(folder,"download-${java.util.UUID.randomUUID()}.part")
        val digest=MessageDigest.getInstance("SHA-256")
        var downloaded=0L
        try {
            transport.open(update.apkUrl).use { input ->
                partial.outputStream().use { output ->
                    val buffer=ByteArray(64*1024)
                    while(true) {
                        coroutineContext.ensureActive()
                        val count=input.read(buffer)
                        if(count<0) break
                        coroutineContext.ensureActive()
                        downloaded+=count
                        require(downloaded<=update.bytes) { "安装包大小与更新信息不符。" }
                        output.write(buffer,0,count);digest.update(buffer,0,count)
                        progress(downloaded,update.bytes)
                    }
                    output.fd.sync()
                }
            }
            coroutineContext.ensureActive()
            require(downloaded==update.bytes && digest.digest().hex()==update.sha256) { "安装包校验失败，请重新下载。" }
            ApkUpdateVerifier.verify(context,partial,update.versionCode)
            if(target.exists()) check(target.delete())
            check(partial.renameTo(target)) { "无法保存更新安装包。" }
            target
        } finally { partial.delete() }
    }
}

object ApkUpdateVerifier {
    @Suppress("DEPRECATION")
    fun verify(context: Context, file: File, expectedVersion: Int) {
        val flags=if(Build.VERSION.SDK_INT>=28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val current=context.packageManager.getPackageInfo(context.packageName,flags)
        val archive=context.packageManager.getPackageArchiveInfo(file.path,flags) ?: error("安装包格式不正确。")
        require(archive.packageName==context.packageName) { "安装包标识不匹配。" }
        val version=if(Build.VERSION.SDK_INT>=28) archive.longVersionCode else archive.versionCode.toLong()
        require(version==expectedVersion.toLong()) { "安装包版本与发布信息不符。" }
        fun certificates(info: android.content.pm.PackageInfo): Set<String> {
            val signatures=if(Build.VERSION.SDK_INT>=28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }.toSet()
        }
        val trusted=certificates(current)
        require(trusted.isNotEmpty() && certificates(archive)==trusted) { "安装包签名与当前应用不一致，已停止安装。" }
    }
}

private fun ByteArray.hex()=joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }
private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
    val output=java.io.ByteArrayOutputStream()
    val buffer=ByteArray(8192)
    while(output.size()<limit) {
        val count=read(buffer,0,minOf(buffer.size,limit-output.size()))
        if(count<0) break
        output.write(buffer,0,count)
    }
    return output.toByteArray()
}
