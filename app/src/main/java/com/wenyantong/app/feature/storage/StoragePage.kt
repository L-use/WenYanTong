@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.wenyantong.app.feature.storage

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wenyantong.app.AppState
import com.wenyantong.app.AppViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable fun StoragePage(state: AppState,vm: AppViewModel,back:()->Unit) {
    val context=LocalContext.current
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> if(uri!=null)vm.exportUserData(uri) }
    val importPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null)vm.prepareImport(uri) }
    Column {
        TopAppBar(title={Text("数据与存储")},navigationIcon={IconButton(onClick=back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回") }},colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Text("把学习积累，留在自己手中。",fontSize=25.sp,lineHeight=36.sp)
            Card(shape=RoundedCornerShape(20.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("用户数据备份",fontSize=20.sp)
                    Text("导出历史、收藏、答题记录、错题与阅读/运行偏好。备份保存在你选择的位置，可离线迁移。",fontSize=14.sp,lineHeight=24.sp)
                    Text("备份不包含资源库、安装包缓存或 API 密钥。JSON 文件可阅读，学习内容请保存在自己信任的位置。",fontSize=12.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick={export.launch("WenYanTong-backup-${SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(Date())}.json")},enabled=!state.storage.busy,modifier=Modifier.fillMaxWidth()) { Text("导出用户数据") }
                    OutlinedButton(onClick={importPicker.launch(arrayOf("application/json","text/plain","application/octet-stream"))},enabled=!state.storage.busy,modifier=Modifier.fillMaxWidth()) { Text("导入用户数据") }
                    if(state.storage.busy) { LinearProgressIndicator(Modifier.fillMaxWidth());Text("正在处理，请稍候…",fontSize=13.sp) }
                    state.storage.message?.let { Text(it,fontSize=14.sp,lineHeight=24.sp,color=MaterialTheme.colorScheme.primary) }
                    Text("导入前会显示记录数量；合并并去重，保留现有收藏。导入会恢复备份中的阅读与运行偏好。",fontSize=12.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("本地路径",fontSize=21.sp)
            Text("以下为安卓系统管理的应用目录，可复制查看。备份和迁移直接使用上方导入、导出，无需访问私有目录。",fontSize=13.sp,lineHeight=23.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            val locations=listOf("APK 安装文件" to context.applicationInfo.sourceDir,
                "应用私有目录" to context.applicationInfo.dataDir,
                "用户学习数据库" to context.getDatabasePath("personal.db").path,
                "离线资源库目录" to File(context.filesDir,"resources").path,
                "偏好设置目录" to File(context.applicationInfo.dataDir,"shared_prefs").path,
                "更新下载缓存" to File(context.cacheDir,"app-updates").path)
            locations.forEach { (title,path) ->
                Card(shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            Text(title,Modifier.weight(1f).padding(top=12.dp),fontSize=15.sp)
                            TextButton(onClick={ (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(title,path)) }) { Text("复制路径") }
                        }
                        SelectionContainer { Text(path,fontSize=12.sp,lineHeight=21.sp) }
                    }
                }
            }
        }
    }
    state.storage.pending?.let { summary ->
        AlertDialog(onDismissRequest=vm::cancelImport,title={Text("合并导入这份备份？")},
            text={Text("历史 ${summary.history} 条，收藏 ${summary.favorites} 条，答题 ${summary.answers} 条，错题 ${summary.wrong} 道，AI 题目快照 ${summary.generated} 道。\n\n现有记录会保留；重复记录去重。阅读与运行偏好恢复为备份中的设置。")},
            confirmButton={TextButton(onClick=vm::importPreparedData) { Text("合并导入") }},dismissButton={TextButton(onClick=vm::cancelImport) { Text("取消") }})
    }
}
