package com.wenyantong.app.feature.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class AiProtocol(val title: String) { COMPATIBLE("Chat Completions 兼容"), CLAUDE("Claude Messages") }
data class AiSettings(val protocol: AiProtocol=AiProtocol.COMPATIBLE,val baseUrl: String="",val model: String="",val hasKey: Boolean=false)

object AiPolicy {
    fun endpoint(settings: AiSettings): String {
        val uri=runCatching { URI(settings.baseUrl.trim()) }.getOrElse { error("API 地址格式不正确。") }
        require(uri.scheme=="https" && !uri.host.isNullOrBlank() && uri.userInfo==null && uri.rawQuery==null && uri.rawFragment==null && settings.baseUrl.length<=2048) { "请填写不含密钥、查询参数或账号的 HTTPS API 地址。" }
        val base=settings.baseUrl.trim().trimEnd('/')
        val suffix=if(settings.protocol==AiProtocol.CLAUDE)"/messages" else "/chat/completions"
        return if(base.endsWith(suffix))base else base+suffix
    }
    fun validate(settings: AiSettings) {
        endpoint(settings)
        require(settings.model.isNotBlank() && settings.model.length<=200 && settings.model.none { it=='\n' || it=='\r' }) { "请填写服务商提供的模型 ID。" }
    }
}

/** API credentials never enter backup/export models, source files, or log messages. */
class AiSettingsStore(context: Context) {
    private val prefs=context.getSharedPreferences("ai_private",Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("wenyantong_ai_key",null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("wenyantong_ai_key",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun settings()=AiSettings(runCatching { AiProtocol.valueOf(prefs.getString("protocol","COMPATIBLE")!!) }.getOrDefault(AiProtocol.COMPATIBLE),prefs.getString("base","") ?: "",prefs.getString("model","") ?: "",prefs.contains("encrypted_key"))
    @Synchronized fun credentials(): Pair<AiSettings,String> = settings() to secret()
    @Synchronized fun save(settings: AiSettings,newKey: String) {
        AiPolicy.validate(settings)
        val old=settings()
        require(newKey.isNotBlank() || (old.hasKey && old.protocol==settings.protocol && old.baseUrl==settings.baseUrl)) { "新渠道必须填写对应 API Key，不能沿用另一地址的密钥。" }
        require(newKey.length<=8192 && newKey.none { it=='\n' || it=='\r' }) { "API Key 格式不正确。" }
        val edit=prefs.edit().putString("protocol",settings.protocol.name).putString("base",settings.baseUrl.trim()).putString("model",settings.model.trim())
        if(newKey.isNotBlank()) {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
            edit.putString("encrypted_key",Base64.encodeToString(cipher.doFinal(newKey.trim().toByteArray()),Base64.NO_WRAP)).putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
        }
        check(edit.commit()) { "AI 设置保存失败。" }
    }
    @Synchronized fun secret(): String {
        try {
            val encoded=prefs.getString("encrypted_key",null) ?: error("请先在设置中配置 API Key。")
            val iv=Base64.decode(prefs.getString("iv","")!!,Base64.NO_WRAP)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv))
            return cipher.doFinal(Base64.decode(encoded,Base64.NO_WRAP)).toString(Charsets.UTF_8)
        } catch(e: Exception) { throw IllegalStateException("AI 密钥不可读取，请在设置中重新填写。") }
    }
    @Synchronized fun clear() { check(prefs.edit().clear().commit()) }
}
