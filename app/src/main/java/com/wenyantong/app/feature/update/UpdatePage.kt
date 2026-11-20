@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.wenyantong.app.feature.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.wenyantong.app.AppState
import com.wenyantong.app.AppViewModel
import com.wenyantong.app.BuildConfig
import com.wenyantong.app.core.model.Mode
import java.io.File

@Composable fun UpdatePage(state: AppState, vm: AppViewModel, back:()->Unit) {
    val context=LocalContext.current
    var consent by remember { mutableStateOf(false) }
    var installMessage by remember { mutableStateOf<String?>(null) }
    val update=state.update
    val busy=update.phase in listOf(UpdatePhase.CHECKING,UpdatePhase.DOWNLOADING)
    Column {
        TopAppBar(title={Text("版本更新")},navigationIcon={IconButton(onClick=back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回") }},
            colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Icon(Icons.Outlined.SystemUpdate,null,Modifier.size(42.dp),tint=MaterialTheme.colorScheme.primary)
            Text("让学习体验，持续变好。",fontSize=26.sp,lineHeight=38.sp)
            Text("当前版本 ${BuildConfig.VERSION_NAME} · GitHub 稳定版渠道",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),shape=RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text(when(update.phase) {
                        UpdatePhase.IDLE -> "检查是否有新版本"
                        UpdatePhase.CHECKING -> "正在检查 GitHub 发布…"
                        UpdatePhase.AVAILABLE -> "发现新版本 ${update.info?.versionName}"
                        UpdatePhase.LATEST -> "已经是最新版本"
                        UpdatePhase.DOWNLOADING -> "正在下载安装包…"
                        UpdatePhase.READY -> "安装包已校验，可以升级"
                        UpdatePhase.ERROR -> "暂时无法完成更新"
                    },fontSize=20.sp,lineHeight=30.sp)
                    update.message?.let { Text(it,fontSize=14.sp,lineHeight=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    if(update.phase==UpdatePhase.CHECKING) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if(update.phase==UpdatePhase.DOWNLOADING) {
                        LinearProgressIndicator(progress={if(update.total==0L)0f else update.downloaded.toFloat()/update.total},modifier=Modifier.fillMaxWidth())
                        Text("${update.downloaded/1024/1024} / ${update.total/1024/1024} MB",fontSize=13.sp)
                        OutlinedButton(onClick=vm::cancelUpdate) { Text("取消下载") }
                    }
                    update.info?.let { info ->
                        Text(info.notes,fontSize=15.sp,lineHeight=25.sp)
                        Text("安装包约 ${"%.1f".format(java.util.Locale.ROOT,info.bytes/1024.0/1024)} MB · 下载后校验完整性、包名、版本和签名。",fontSize=12.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(update.phase in listOf(UpdatePhase.AVAILABLE,UpdatePhase.ERROR)) Button(onClick=vm::downloadUpdate,modifier=Modifier.fillMaxWidth()) { Text("下载更新") }
                    }
                    if(update.phase==UpdatePhase.READY) Button(onClick={
                        try {
                            if(!context.packageManager.canRequestPackageInstalls()) {
                                installMessage="请在系统中允许文言通安装应用，返回后再点“安装更新”。"
                                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")))
                            } else {
                                val file=File(requireNotNull(update.apkPath))
                                val uri=FileProvider.getUriForFile(context,"${context.packageName}.updates",file)
                                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                            }
                        } catch(e: Exception) { installMessage="无法打开系统安装界面，请重新下载或从 GitHub 发布页安装。" }
                    },modifier=Modifier.fillMaxWidth()) { Text("安装更新") }
                    installMessage?.let { Text(it,fontSize=13.sp,lineHeight=22.sp) }
                    if(!busy && update.phase!=UpdatePhase.READY) OutlinedButton(onClick={if(state.mode==Mode.OFFLINE)consent=true else vm.checkUpdate()},modifier=Modifier.fillMaxWidth()) { Text(if(update.phase==UpdatePhase.IDLE)"检查更新" else "重新检查") }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text("自动检查更新");Text("允许联网的模式下，每天最多检查一次。",fontSize=12.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(checked=state.automaticUpdates,onCheckedChange=vm::automaticUpdates)
            }
            Text("离线模式不自动联网。安装包仅在你点击后下载，安装由安卓系统确认；学习历史与收藏保存在本机。",fontSize=13.sp,lineHeight=23.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases")))}) { Text("查看 GitHub 发布页") }
        }
    }
    if(consent) AlertDialog(onDismissRequest={consent=false},title={Text("本次允许连接 GitHub？")},
        text={Text("当前为离线模式。本次检查和你主动选择的更新下载需要联网；翻译与查询仍使用本地资源。")},
        confirmButton={TextButton(onClick={consent=false;vm.checkUpdate(true)}) { Text("本次允许") }},
        dismissButton={TextButton(onClick={consent=false}) { Text("取消") }})
}
