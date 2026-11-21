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
data class AiProfile(val id: String,val name: String,val settings: AiSettings,val connected: Boolean)

object AiPolicy {
    fun modelsEndpoint(settings: AiSettings): String {
        val endpoint=endpoint(settings)
        val root=endpoint.removeSuffix(if(settings.protocol==AiProtocol.CLAUDE)"/messages" else "/chat/completions")
        val uri=URI(root)
        // Bailian's catalog uses its native API on the same host, not the chat compatibility path.
        if(settings.protocol==AiProtocol.COMPATIBLE && (uri.host=="dashscope.aliyuncs.com" || uri.host=="dashscope-intl.aliyuncs.com" || uri.host=="dashscope-us.aliyuncs.com" || uri.host.endsWith(".maas.aliyuncs.com")) && uri.path=="/compatible-mode/v1") {
            return root.removeSuffix("/compatible-mode/v1")+"/api/v1/models"
        }
        return root+"/models"
    }
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
    private val profileKeys=listOf("profile_name","protocol","base","model","encrypted_key","iv","connection_status")
    private fun archiveActive() {
        val id=prefs.getString("active_profile",null) ?: java.util.UUID.randomUUID().toString()
        val profiles=org.json.JSONObject(prefs.getString("profiles","{}")!!)
        val snapshot=org.json.JSONObject();profileKeys.forEach { name -> prefs.getString(name,null)?.let { snapshot.put(name,it) } }
        if(snapshot.optString("base").isNotBlank() || snapshot.has("encrypted_key"))profiles.put(id,snapshot) else profiles.remove(id)
        check(prefs.edit().putString("active_profile",id).putString("profiles",profiles.toString()).commit())
    }
    @Synchronized fun profiles(): List<AiProfile> {
        archiveActive();val json=org.json.JSONObject(prefs.getString("profiles","{}")!!)
        return json.keys().asSequence().mapNotNull { id -> val p=json.getJSONObject(id)
            if(p.optString("base").isBlank() && !p.has("encrypted_key"))return@mapNotNull null
            val settings=AiSettings(runCatching { AiProtocol.valueOf(p.optString("protocol","COMPATIBLE")) }.getOrDefault(AiProtocol.COMPATIBLE),p.optString("base"),p.optString("model"),p.has("encrypted_key"))
            AiProfile(id,p.optString("profile_name").ifBlank { AiProvider.matching(settings).title },settings,p.optString("connection_status")=="connected")
        }.toList()
    }
    fun activeProfile()=prefs.getString("active_profile","") ?: ""
    fun connected()=prefs.getString("connection_status","")=="connected"
    @Synchronized fun markConnected(value: Boolean) { check(prefs.edit().putString("connection_status",if(value)"connected" else "failed").commit());archiveActive() }
    @Synchronized fun rename(name: String) { require(name.isNotBlank() && name.length<=60);check(prefs.edit().putString("profile_name",name.trim()).commit());archiveActive() }
    @Synchronized fun newProfile() {
        archiveActive();require(profiles().size<20) { "最多保存 20 个 API。" }
        val edit=prefs.edit();profileKeys.forEach { edit.remove(it) };edit.putString("active_profile",java.util.UUID.randomUUID().toString());check(edit.commit())
    }
    @Synchronized fun activateProfile(id: String) {
        archiveActive();val all=org.json.JSONObject(prefs.getString("profiles","{}")!!);val snapshot=all.getJSONObject(id)
        val edit=prefs.edit();profileKeys.forEach { name -> edit.remove(name);if(snapshot.has(name))edit.putString(name,snapshot.getString(name)) };edit.putString("active_profile",id);check(edit.commit())
    }
    @Synchronized fun deleteProfile(id: String) {
        archiveActive();val all=org.json.JSONObject(prefs.getString("profiles","{}")!!);all.remove(id)
        val edit=prefs.edit().putString("profiles",all.toString())
        if(activeProfile()==id) { profileKeys.forEach { edit.remove(it) };edit.putString("active_profile",java.util.UUID.randomUUID().toString()) }
        check(edit.commit())
    }
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
    @Synchronized fun discoveryKey(settings: AiSettings,newKey: String): String {
        AiPolicy.endpoint(settings)
        val candidate=newKey.trim()
        if(candidate.length>8192 || candidate.any { it.isWhitespace() || it.isISOControl() })throw AiModelException("API Key 格式不正确。")
        if(candidate.isNotBlank())return candidate
        val old=settings()
        if(!(old.hasKey && old.protocol==settings.protocol && AiPolicy.modelsEndpoint(old)==AiPolicy.modelsEndpoint(settings)))throw AiModelException("请填写当前厂商的 API Key。")
        return secret()
    }
    @Synchronized fun save(settings: AiSettings,newKey: String) {
        AiPolicy.validate(settings)
        val old=settings()
        require(newKey.isNotBlank() || (old.hasKey && old.protocol==settings.protocol && old.baseUrl==settings.baseUrl)) { "新渠道必须填写对应 API Key，不能沿用另一地址的密钥。" }
        require(newKey.length<=8192 && newKey.none { it=='\n' || it=='\r' }) { "API Key 格式不正确。" }
        val edit=prefs.edit().putString("protocol",settings.protocol.name).putString("base",settings.baseUrl.trim()).putString("model",settings.model.trim())
        if(old.protocol!=settings.protocol || old.baseUrl!=settings.baseUrl.trim() || old.model!=settings.model.trim() || newKey.isNotBlank())edit.remove("connection_status")
        if(newKey.isNotBlank()) {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
            edit.putString("encrypted_key",Base64.encodeToString(cipher.doFinal(newKey.trim().toByteArray()),Base64.NO_WRAP)).putString("iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP))
        }
        check(edit.commit()) { "AI 设置保存失败。" }
        archiveActive()
    }
    @Synchronized fun secret(): String {
        try {
            val encoded=prefs.getString("encrypted_key",null) ?: error("请先在设置中配置 API Key。")
            val iv=Base64.decode(prefs.getString("iv","")!!,Base64.NO_WRAP)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv))
            return cipher.doFinal(Base64.decode(encoded,Base64.NO_WRAP)).toString(Charsets.UTF_8)
        } catch(e: Exception) { throw IllegalStateException("AI 密钥不可读取，请在设置中重新填写。") }
    }
    @Synchronized fun clear() { val skills=prefs.getString("user_skills","[]");check(prefs.edit().clear().putString("user_skills",skills).commit()) }
}
