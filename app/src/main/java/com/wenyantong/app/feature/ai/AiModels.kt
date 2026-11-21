package com.wenyantong.app.feature.ai

import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.coroutines.coroutineContext

enum class AiProvider(val title: String,val base: String,val protocol: AiProtocol=AiProtocol.COMPATIBLE,val note: String="") {
    DEEPSEEK("DeepSeek","https://api.deepseek.com"),
    QWEN("通义千问 · 百炼（北京）","https://dashscope.aliyuncs.com/compatible-mode/v1",note="请使用北京地域 Key；也可填写控制台提供的业务空间专属地址。"),
    KIMI("Kimi · Moonshot","https://api.moonshot.cn/v1"),
    GLM("智谱 GLM","https://open.bigmodel.cn/api/paas/v4",note="使用按量 API Key；Coding 套餐需按服务商要求修改地址。"),
    DOUBAO("豆包 · 火山方舟","https://ark.cn-beijing.volces.com/api/v3",note="目录不代表已开通权限；部分调用需填写控制台的推理接入点 ID。"),
    MINIMAX("MiniMax（中国）","https://api.minimax.cn/v1"),
    BAIDU("文心 · 百度千帆","https://qianfan.baidubce.com/v2"),
    HUNYUAN("腾讯混元","https://api.hunyuan.cloud.tencent.com/v1"),
    SILICONFLOW("硅基流动","https://api.siliconflow.cn/v1"),
    OPENAI("OpenAI","https://api.openai.com/v1"),
    CLAUDE("Claude · Anthropic","https://api.anthropic.com/v1",AiProtocol.CLAUDE),
    GEMINI("Gemini · Google","https://generativelanguage.googleapis.com/v1beta/openai"),
    GROK("Grok · xAI","https://api.x.ai/v1"),
    OPENROUTER("OpenRouter","https://openrouter.ai/api/v1",note="返回渠道目录；模型权限、地区和额度以账号及连接测试为准。"),
    CUSTOM("自定义渠道","");
    companion object {
        fun matching(settings: AiSettings)=entries.firstOrNull { it.base.isNotEmpty() && it.base==settings.baseUrl.trim().trimEnd('/') && it.protocol==settings.protocol } ?: CUSTOM
    }
}

data class AiModelOption(val id: String,val title: String)
class AiModelException(message: String): IllegalStateException(message)
private inline fun modelCheck(condition: Boolean,message:()->String) { if(!condition)throw AiModelException(message()) }

/** Model discovery never saves a draft key or sends a generation request. */
class AiModelClient(private val connectionFactory: (URL)->HttpURLConnection={ it.openConnection() as HttpURLConnection }) {
    suspend fun list(settings: AiSettings,key: String): List<AiModelOption> = withContext(Dispatchers.IO) { coroutineScope {
        modelCheck(key.isNotBlank() && key.length<=8192 && key.none { it.isWhitespace() || it.isISOControl() }) { "请填写有效的 API Key。" }
        val root=AiPolicy.modelsEndpoint(settings)
        var next: String?=if(URI(root).path=="/api/v1/models")root+"?page_no=1&page_size=100" else root
        val visited=mutableSetOf<String>();val models=linkedMapOf<String,AiModelOption>()
        while(next!=null) {
            ensureActive()
            modelCheck(visited.add(next) && visited.size<=20) { "模型目录分页过多，请手动填写模型 ID。" }
            val connection=connectionFactory(URL(next))
            connection.requestMethod="GET";connection.instanceFollowRedirects=false
            connection.connectTimeout=15000;connection.readTimeout=20000
            connection.setRequestProperty("Accept","application/json")
            if(settings.protocol==AiProtocol.CLAUDE) {
                connection.setRequestProperty("x-api-key",key);connection.setRequestProperty("anthropic-version","2023-06-01")
            } else connection.setRequestProperty("Authorization","Bearer $key")
            val cancellation=launch(Dispatchers.Default,start=CoroutineStart.UNDISPATCHED) { try { awaitCancellation() } finally { connection.disconnect() } }
            val json=try {
                val status=connection.responseCode
                modelCheck(status in 200..299) { when(status) {
                    401,403 -> "模型目录认证失败，请检查 Key、地域和账号权限。"
                    404,405,501 -> "该渠道未提供兼容的模型列表接口，请手动填写模型 ID。"
                    429 -> "获取模型过于频繁或额度受限，请稍后重试。"
                    in 300..399 -> "模型接口发生跳转，已停止；请填写最终 HTTPS 地址。"
                    else -> "模型目录暂不可用（HTTP $status），可手动填写模型 ID。"
                } }
                val output=java.io.ByteArrayOutputStream()
                connection.inputStream.use { input -> val buffer=ByteArray(8192);while(true) {
                    coroutineContext.ensureActive();val n=input.read(buffer);if(n<0)break
                    modelCheck(output.size()+n<=4*1024*1024) { "模型目录过大，请手动填写模型 ID。" };output.write(buffer,0,n)
                } }
                try { JSONObject(output.toString("UTF-8")) } catch(e: org.json.JSONException) { throw AiModelException("模型目录格式不兼容，请手动填写模型 ID。") }
            } finally { cancellation.cancel();connection.disconnect() }
            parse(json).forEach { models[it.id]=it }
            modelCheck(models.size<=10000) { "模型数量过多，请手动填写模型 ID。" }
            next=nextPage(root,json,settings.protocol)
        }
        modelCheck(models.isNotEmpty()) { "未返回可选文本模型，请检查权限或手动填写模型 ID。" }
        models.values.sortedBy { it.id.lowercase() }
    } }

    companion object {
        fun parse(json: JSONObject): List<AiModelOption> {
            val data=json.optJSONArray("data") ?: json.optJSONObject("output")?.optJSONArray("models") ?: throw AiModelException("模型目录格式不兼容，请手动填写模型 ID。")
            return (0 until data.length()).mapNotNull { i ->
                val item=data.optJSONObject(i) ?: return@mapNotNull null
                val id=((item.opt("id") ?: item.opt("model")) as? String)?.trim() ?: return@mapNotNull null
                if(id.isBlank() || id.length>200 || id.any { it.isISOControl() })return@mapNotNull null
                // Exclude known non-text models without excluding unfamiliar future text models.
                val modality=item.optJSONObject("architecture")?.optJSONArray("output_modalities") ?: item.optJSONArray("output_modalities") ?: item.optJSONObject("inference_metadata")?.optJSONArray("response_modality")
                if(modality!=null && (0 until modality.length()).none { modality.optString(it).equals("text",true) })return@mapNotNull null
                if(modality==null && Regex("(^|[/_-])(embedding|embed|rerank|reranker|tts|whisper|dall-e|image|video)([/_-]|$)",RegexOption.IGNORE_CASE).containsMatchIn(id))return@mapNotNull null
                AiModelOption(id,item.optString("display_name",item.optString("name",id)).take(240))
            }.distinctBy { it.id }
        }
        fun nextPage(root: String,json: JSONObject,protocol: AiProtocol): String? {
            json.optJSONObject("output")?.takeIf { it.has("models") }?.let { output ->
                val page=output.optInt("page_no",1);val size=output.optInt("page_size",20);val total=output.optInt("total")
                modelCheck(page>0 && size in 1..1000 && total in 0..10000) { "模型分页格式不兼容。" }
                return if(page.toLong()*size<total)root+"?page_no=${page+1}&page_size=$size" else null
            }
            if(protocol==AiProtocol.CLAUDE && json.optBoolean("has_more")) {
                val id=json.optString("last_id");modelCheck(id.isNotBlank() && id.length<=200) { "模型分页格式不兼容。" }
                return root+"?after_id="+java.net.URLEncoder.encode(id,"UTF-8")
            }
            val next=json.optString("next").takeIf { it.isNotBlank() && it!="null" } ?: return null
            val base=URI(root);val target=base.resolve(next)
            modelCheck(target.scheme=="https" && target.host==base.host && target.port==base.port && target.rawPath==base.rawPath && target.userInfo==null && target.fragment==null) { "模型分页地址不安全，已停止。" }
            return target.toString()
        }
    }
}
