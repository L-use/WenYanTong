@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.wenyantong.app.feature.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wenyantong.app.AppState
import com.wenyantong.app.AppViewModel

@Composable fun AiSkillsDialog(state: AppState,vm: AppViewModel,dismiss:()->Unit) {
    var editing by remember { mutableStateOf<AiSkill?>(null) }
    var deleting by remember { mutableStateOf<AiSkill?>(null) }
    AlertDialog(onDismissRequest=dismiss,title={Text("已有 Skills")},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text("启用的技能指令会随对应任务发送给当前 API，用于调整教学方式；不会执行手机命令。",fontSize=13.sp)
        Text("内置技能（始终启用）")
        BuiltInSkills.items.forEach { skill -> Text(skill.name);Text(skill.instruction,fontSize=12.sp) }
        HorizontalDivider()
        Text("用户自建技能 · ${state.skills.size} 个")
        if(state.skills.isEmpty())Text("暂无自建技能，可创建讲解风格、例句要求或出题侧重点。",fontSize=13.sp)
        state.skills.forEach { skill -> Column {
            Row { Column(Modifier.weight(1f)) { Text(skill.name);Text(skill.scope.title,fontSize=12.sp) };Switch(skill.enabled,{vm.saveSkill(skill.copy(enabled=it))}) }
            Text(skill.instruction.take(160),fontSize=12.sp)
            Row { TextButton(onClick={editing=skill}) { Text("编辑技能") };TextButton(onClick={deleting=skill}) { Text("删除技能") } }
        } }
        TextButton(onClick={editing=AiSkill("skill-${java.util.UUID.randomUUID()}","","")},enabled=state.skills.size<30) { Text("创建 Skill") }
        if(state.aiNotice.isNotBlank())Text(state.aiNotice,fontSize=12.sp)
    }},confirmButton={TextButton(onClick=dismiss) { Text("关闭") }})
    editing?.let { original ->
        var name by remember(original.id) { mutableStateOf(original.name) }
        var instruction by remember(original.id) { mutableStateOf(original.instruction) }
        var scope by remember(original.id) { mutableStateOf(original.scope) }
        com.wenyantong.app.core.designsystem.EditorDialog(if(original.name.isBlank())"创建 Skill" else "编辑 Skill","保存技能",name.isNotBlank() && instruction.isNotBlank(),{editing=null},{if(vm.saveSkill(original.copy(name=name.trim(),instruction=instruction.trim(),scope=scope)))editing=null}) {
            OutlinedTextField(name,{name=it.take(60)},label={Text("技能名称")},singleLine=true)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) { SkillScope.entries.forEach { value -> FilterChip(scope==value,{scope=value},label={Text(value.title)}) } }
            OutlinedTextField(instruction,{instruction=it.take(4000)},label={Text("技能指令")},placeholder={Text("例如：用初中生能理解的语言讲解，先释义，再给例句。")},minLines=5)
            if(state.aiNotice.isNotBlank())Text(state.aiNotice,fontSize=12.sp)
        }
    }
    deleting?.let { skill -> AlertDialog(onDismissRequest={deleting=null},title={Text("删除技能？")},text={Text(skill.name)},confirmButton={TextButton(onClick={vm.deleteSkill(skill.id);deleting=null}) { Text("删除") }},dismissButton={TextButton(onClick={deleting=null}) { Text("取消") }}) }
}
