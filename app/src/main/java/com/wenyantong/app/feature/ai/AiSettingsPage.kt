@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.wenyantong.app.feature.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import com.wenyantong.app.AppState
import com.wenyantong.app.AppViewModel

@Composable fun AiSettingsPage(state: AppState,vm: AppViewModel,back:()->Unit) {
    var protocol by remember(state.aiSettings,state.activeAiProfile) { mutableStateOf(state.aiSettings.protocol) }
    var base by remember(state.aiSettings,state.activeAiProfile) { mutableStateOf(state.aiSettings.baseUrl) }
    var model by remember(state.aiSettings,state.activeAiProfile) { mutableStateOf(state.aiSettings.model) }
    var secret by remember(state.activeAiProfile) { mutableStateOf("") }
    var profileName by remember(state.activeAiProfile) { mutableStateOf(state.aiProfiles.firstOrNull { it.id==state.activeAiProfile }?.name.orEmpty()) }
    var manager by remember { mutableStateOf(false) }
    var skillManager by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    var provider by remember(state.aiSettings) { mutableStateOf(AiProvider.matching(state.aiSettings)) }
    var providerPicker by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var handledRefresh by remember { mutableIntStateOf(0) }
    var autoDiscover by remember { mutableStateOf(true) }
    var models by remember(protocol,base,secret,state.activeAiProfile) { mutableStateOf(emptyList<AiModelOption>()) }
    var modelNotice by remember(protocol,base,secret,state.activeAiProfile) { mutableStateOf("") }
    var loading by remember(protocol,base,secret,state.activeAiProfile) { mutableStateOf(false) }
    var modelPicker by remember(protocol,base,secret,state.activeAiProfile) { mutableStateOf(false) }
    LaunchedEffect(protocol,base,secret,refresh,autoDiscover,state.activeAiProfile) {
        val manual=refresh!=handledRefresh
        handledRefresh=refresh
        if(!manual && (!autoDiscover || secret.isBlank())) {
            loading=false;if(models.isEmpty())modelNotice=""
            return@LaunchedEffect
        }
        if(base.isBlank())return@LaunchedEffect
        if(!manual)delay(1200)
        loading=true;models=emptyList();modelNotice="正在获取厂商模型目录…"
        try {
            models=vm.discoverAiModels(AiSettings(protocol,base,""),secret)
            modelNotice="已获取 ${models.size} 个候选模型，请选择后测试连接。"
        } catch(e: CancellationException) { throw e }
        catch(e: Exception) { modelNotice=when(e) {
            is java.io.IOException -> "网络连接失败，请重试或手动填写模型 ID。"
            is AiModelException -> e.message ?: "获取模型失败，请手动填写模型 ID。"
            else -> "获取模型失败，请检查渠道或手动填写模型 ID。"
        } } finally { loading=false }
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(12.dp)) { IconButton(onClick=back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回") };Text("AI 设置",Modifier.padding(12.dp),fontSize=21.sp) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("选择渠道，连接自己的模型。",fontSize=24.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=vm::newApi,enabled=!state.aiBusy) { Text("添加 API") }
                OutlinedButton(onClick={manager=true},enabled=!state.aiBusy) { Text("API 管理（${state.aiProfiles.size}）") }
            }
            val tested=state.aiConnected && protocol==state.aiSettings.protocol && base.trim()==state.aiSettings.baseUrl && model.trim()==state.aiSettings.model && secret.isBlank()
            Text(if(tested)"连接成功 · 上次测试通过 · ${state.aiSettings.model}" else "当前配置尚未通过连接测试",color=if(tested)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(profileName,{profileName=it.take(60)},Modifier.fillMaxWidth(),label={Text("API 名称（可选）")},singleLine=true)
            OutlinedButton(onClick={skillManager=true},modifier=Modifier.fillMaxWidth()) { Text("Skills 管理（${BuiltInSkills.items.size} 个内置 · ${state.skills.size} 个自建）") }
            Text("模型只接收本次查询及少量相关词义/出处，不上传历史或完整词库。调用可能产生服务商费用；API Key 在手机 Keystore 加密保存，不随用户备份导出。",fontSize=13.sp,lineHeight=22.sp)
            OutlinedButton(onClick={providerPicker=true},modifier=Modifier.fillMaxWidth()) { Text("选择厂商：${provider.title}") }
            if(provider.note.isNotBlank())Text(provider.note,fontSize=12.sp,lineHeight=20.sp)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { AiProtocol.entries.forEach { value -> FilterChip(protocol==value,{protocol=value},label={Text(value.title)}) } }
            OutlinedTextField(base,{base=it},Modifier.fillMaxWidth(),label={Text("API Base URL")},placeholder={Text("https://服务地址/v1")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri))
            Text("可填接口根地址或完整 chat/completions、messages 地址；仅支持 HTTPS，密钥不会跟随跳转。",fontSize=12.sp,lineHeight=20.sp)
            OutlinedTextField(secret,{secret=it},Modifier.fillMaxWidth(),label={Text("API Key")},placeholder={Text(if(state.aiSettings.hasKey)"已加密保存；留空保留同渠道密钥" else "填入自己的 API Key")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
            Row { Checkbox(autoDiscover,{autoDiscover=it});Text("输入 Key 后自动获取模型",Modifier.padding(top=12.dp)) }
            Text("只向当前地址查询模型目录，不发送学习内容。目录不保证余额、调用权限或题目格式兼容，选择后可测试连接。",fontSize=12.sp,lineHeight=20.sp)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={refresh++},enabled=!loading && !state.aiBusy && base.isNotBlank()) { Text("刷新模型列表") }
                OutlinedButton(onClick={modelPicker=true},enabled=models.isNotEmpty() && !loading) { Text("选择模型（${models.size}）") }
            }
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(modelNotice.isNotBlank())Text(modelNotice,fontSize=13.sp,lineHeight=21.sp)
            OutlinedTextField(model,{model=it},Modifier.fillMaxWidth(),label={Text("模型 ID")},placeholder={Text("从目录选择，也可手动填写")},singleLine=true)
            Button(onClick={vm.renameApi(profileName);vm.saveAi(AiSettings(protocol,base,model),secret,true);secret=""},enabled=!state.aiBusy,modifier=Modifier.fillMaxWidth()) { Text("保存并启用自动模式") }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={vm.renameApi(profileName);vm.saveAi(AiSettings(protocol,base,model),secret,false);secret=""},enabled=!state.aiBusy) { Text("仅保存") }
                OutlinedButton(onClick={vm.renameApi(profileName);vm.testAi(AiSettings(protocol,base,model),secret);secret=""},enabled=!state.aiBusy) { Text("保存并测试连接") }
            }
            if(state.aiBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(state.aiNotice.isNotBlank())Text(state.aiNotice,fontSize=14.sp,lineHeight=23.sp)
            Text("自动模式：优先本地收录，缺少整句译文时请求 AI；AI 模式：结合本地词义请求模型；离线模式：不调用模型。练习可选择 AI 根据当前结果和词库出题，或使用离线题库。AI 输出需结合出处核对。",fontSize=13.sp,lineHeight=23.sp)
            TextButton(onClick={remove=true},enabled=!state.aiBusy) { Text("清除 AI 配置和密钥") }
        }
    }
    if(manager)AlertDialog(onDismissRequest={manager=false},title={Text("API 管理")},text={LazyColumn(Modifier.heightIn(max=420.dp)) {
        if(state.aiProfiles.isEmpty())item { Text("还没有保存的 API，点击添加 API 创建。") }
        items(state.aiProfiles,key={it.id}) { profile -> Column(Modifier.padding(vertical=10.dp)) {
            Text(profile.name+if(profile.id==state.activeAiProfile)" · 当前使用" else "")
            Text(profile.settings.model,fontSize=13.sp)
            Text(if(profile.connected)"连接成功" else "未连接或未测试",fontSize=12.sp)
            Row { TextButton(onClick={vm.useApi(profile.id);manager=false}) { Text("使用 / 编辑") };TextButton(onClick={vm.deleteApi(profile.id)}) { Text("删除 API") } }
        } }
    }},confirmButton={TextButton(onClick={manager=false}) { Text("关闭") }})
    if(skillManager)AiSkillsDialog(state,vm) { skillManager=false }
    if(providerPicker)AlertDialog(onDismissRequest={providerPicker=false},title={Text("选择模型厂商")},text={
        LazyColumn(Modifier.heightIn(max=420.dp).testTag("ai-provider-list")) { items(AiProvider.entries.toList()) { value ->
            TextButton(onClick={
                providerPicker=false;provider=value;protocol=value.protocol;base=value.base;model="";secret=""
            },modifier=Modifier.fillMaxWidth()) { Text(value.title) }
        } }
    },confirmButton={TextButton(onClick={providerPicker=false}) { Text("取消") }})
    if(modelPicker)AiModelPicker(models,{modelPicker=false}) { selected -> model=selected;modelPicker=false }
    if(remove)AlertDialog(onDismissRequest={remove=false},title={Text("清除 AI 配置？")},text={Text("移除本机保存的渠道、模型及 API Key，不影响词库和学习记录。")},confirmButton={TextButton(onClick={remove=false;vm.clearAi()}) { Text("清除") }},dismissButton={TextButton(onClick={remove=false}) { Text("取消") }})
}

@Composable internal fun AiModelPicker(models: List<AiModelOption>,dismiss:()->Unit,select:(String)->Unit) {
    var query by remember { mutableStateOf("") }
    val filtered=remember(models,query) { models.filter { it.id.contains(query,true) || it.title.contains(query,true) } }
    AlertDialog(onDismissRequest=dismiss,title={Text("选择模型")},text={Column {
        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("搜索模型")},singleLine=true)
        Text("${filtered.size} 个结果",Modifier.padding(vertical=8.dp))
        LazyColumn(Modifier.heightIn(max=360.dp)) { items(filtered,key={it.id}) { value ->
            TextButton(onClick={select(value.id)},modifier=Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth()) {
                Text(value.id);if(value.title!=value.id)Text(value.title,fontSize=12.sp)
            } }
        } }
    }},confirmButton={TextButton(onClick=dismiss) { Text("取消") }})
}
