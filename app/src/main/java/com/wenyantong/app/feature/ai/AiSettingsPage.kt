@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.wenyantong.app.feature.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wenyantong.app.AppState
import com.wenyantong.app.AppViewModel

@Composable fun AiSettingsPage(state: AppState,vm: AppViewModel,back:()->Unit) {
    var protocol by remember(state.aiSettings) { mutableStateOf(state.aiSettings.protocol) }
    var base by remember(state.aiSettings) { mutableStateOf(state.aiSettings.baseUrl) }
    var model by remember(state.aiSettings) { mutableStateOf(state.aiSettings.model) }
    var secret by remember { mutableStateOf("") }
    var remove by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth().padding(12.dp)) { IconButton(onClick=back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回") };Text("AI 设置",Modifier.padding(12.dp),fontSize=21.sp) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("选择渠道，连接自己的模型。",fontSize=24.sp)
            Text("模型只接收本次查询及少量相关词义/出处，不上传历史或完整词库。调用可能产生服务商费用；API Key 在手机 Keystore 加密保存，不随用户备份导出。",fontSize=13.sp,lineHeight=22.sp)
            Text("渠道预设",fontSize=15.sp)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={protocol=AiProtocol.COMPATIBLE;base="https://api.deepseek.com/v1";model="";secret=""}) { Text("DeepSeek") }
                OutlinedButton(onClick={protocol=AiProtocol.COMPATIBLE;base="https://generativelanguage.googleapis.com/v1beta/openai";model="";secret=""}) { Text("Gemini 兼容") }
                OutlinedButton(onClick={protocol=AiProtocol.CLAUDE;base="https://api.anthropic.com/v1";model="";secret=""}) { Text("Claude") }
                OutlinedButton(onClick={protocol=AiProtocol.COMPATIBLE;base="";model="";secret=""}) { Text("自定义渠道") }
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { AiProtocol.entries.forEach { value -> FilterChip(protocol==value,{protocol=value},label={Text(value.title)}) } }
            OutlinedTextField(base,{base=it},Modifier.fillMaxWidth(),label={Text("API Base URL")},placeholder={Text("https://服务地址/v1")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri))
            Text("可填接口根地址或完整 chat/completions、messages 地址；仅支持 HTTPS，密钥不会跟随跳转。",fontSize=12.sp,lineHeight=20.sp)
            OutlinedTextField(model,{model=it},Modifier.fillMaxWidth(),label={Text("模型 ID")},placeholder={Text("填写渠道实际提供的模型名称")},singleLine=true)
            OutlinedTextField(secret,{secret=it},Modifier.fillMaxWidth(),label={Text("API Key")},placeholder={Text(if(state.aiSettings.hasKey)"已加密保存；留空保留同渠道密钥" else "填入自己的 API Key")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
            Button(onClick={vm.saveAi(AiSettings(protocol,base,model),secret,true);secret=""},enabled=!state.aiBusy,modifier=Modifier.fillMaxWidth()) { Text("保存并启用自动模式") }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={vm.saveAi(AiSettings(protocol,base,model),secret,false);secret=""},enabled=!state.aiBusy) { Text("仅保存") }
                OutlinedButton(onClick={vm.testAi(AiSettings(protocol,base,model),secret);secret=""},enabled=!state.aiBusy) { Text("保存并测试连接") }
            }
            if(state.aiBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(state.aiNotice.isNotBlank())Text(state.aiNotice,fontSize=14.sp,lineHeight=23.sp)
            Text("自动模式：优先本地收录，缺少整句译文时请求 AI；AI 模式：结合本地词义请求模型；离线模式：不调用模型。练习可选择 AI 根据当前结果和词库出题，或使用离线题库。AI 输出需结合出处核对。",fontSize=13.sp,lineHeight=23.sp)
            TextButton(onClick={remove=true},enabled=!state.aiBusy) { Text("清除 AI 配置和密钥") }
        }
    }
    if(remove)AlertDialog(onDismissRequest={remove=false},title={Text("清除 AI 配置？")},text={Text("移除本机保存的渠道、模型及 API Key，不影响词库和学习记录。")},confirmButton={TextButton(onClick={remove=false;vm.clearAi()}) { Text("清除") }},dismissButton={TextButton(onClick={remove=false}) { Text("取消") }})
}
