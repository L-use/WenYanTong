@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.wenyantong.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.*
import com.wenyantong.app.AppState
import com.wenyantong.app.AppViewModel
import com.wenyantong.app.core.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.wenyantong.app.BuildConfig
import com.wenyantong.app.feature.update.UpdatePage
import com.wenyantong.app.feature.update.UpdatePhase
import com.wenyantong.app.feature.storage.StoragePage

private val Rounded = RoundedCornerShape(20.dp)
private val PagePadding = 24.dp

@Composable fun WenYanApp(vm: AppViewModel, state: AppState) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "home"
    var entry by remember { mutableStateOf<Entry?>(null) }
    var entryLimit by remember { mutableStateOf(30) }
    fun openEntry(item: Entry) { entryLimit=30;vm.entryDetails(item,30) { entry=it } }
    var practiceScope by rememberSaveable { mutableStateOf("") }
    fun go(destination: String) { nav.navigate(destination) { launchSingleTop=true } }
    fun lookup(text: String, direction: Direction = state.direction) { vm.query(text,direction);go("result") }
    fun practice(ids: List<String>) { practiceScope=ids.joinToString(",");go("practice") }
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        if(!state.ready) {
            Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Outlined.MenuBook,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(20.dp))
                Text("文言通",fontSize=30.sp,fontFamily=FontFamily.Serif)
                Spacer(Modifier.height(16.dp))
                if(state.error == null) { CircularProgressIndicator();Text("正在准备离线资源…",Modifier.padding(16.dp)) }
                else { Text(state.error);Button(onClick=vm::initialize) { Text("重试") } }
            }
        } else {
            Scaffold(containerColor=MaterialTheme.colorScheme.background,
                bottomBar={ if(route in listOf("home","practice","history","settings")) BottomNavigation(route) { destination ->
                    if(destination=="practice") practiceScope=""
                    nav.navigate(destination) { popUpTo("home");launchSingleTop=true }
                } }
            ) { padding ->
                NavHost(nav,startDestination="home",modifier=Modifier.padding(padding)) {
                    composable("home") { Home(state,vm,::lookup,{if(it=="dictionary")vm.dictionaryFilter("");go(it)},{practice(emptyList())}) }
                    composable("result") { ResultPage(state,{nav.popBackStack()},{openEntry(it)},{practice(it)},{item->vm.favorite(item)}) }
                    composable("dictionary") { DictionaryPage(state,{nav.popBackStack()},{openEntry(it)},{vm.dictionaryFilter("")}) }
                    composable("classics") { LibraryPage(state,vm,{nav.popBackStack()},{ vm.openArticle(it);go("article") }) { lookup(it.original,it.direction) } }
                    composable("article") { ArticlePage(state) { nav.popBackStack() } }
                    composable("practice") { PracticeConfig(state,practiceScope,{vm.loadWrong();go("wrong")},vm::cancelPractice) { count,useAi,kinds ->
                        vm.startPractice(practiceScope.split(",").filter(String::isNotBlank).toSet(),count,useAi=useAi,kinds=kinds) { go("quiz") }
                    } }
                    composable("quiz") { QuizPage(state,vm,{nav.popBackStack()},{nav.navigate("report") { popUpTo("quiz") { inclusive=true };launchSingleTop=true }}) }
                    composable("report") { ReportPage(state) { nav.popBackStack("practice",false) } }
                    composable("wrong") { WrongPage(state,vm,{nav.popBackStack()}) { vm.startPractice(emptySet(),20,true) { go("quiz") } } }
                    composable("history") { HistoryPage(state,{vm.favorite(it)},{vm.delete(it)}) { lookup(it.query,it.direction) } }
                    composable("settings") { SettingsPage(state,vm,{go(it)}) }
                    composable("resources") { ResourcePage(state,vm,{nav.popBackStack()},{go(it)}) }
                    composable("reading") { ReadingPage(state,vm,{nav.popBackStack()}) }
                    composable("ai") { com.wenyantong.app.feature.ai.AiSettingsPage(state,vm) { nav.popBackStack() } }
                    composable("updates") { UpdatePage(state,vm) { nav.popBackStack() } }
                    composable("storage") { StoragePage(state,vm) { nav.popBackStack() } }
                }
            }
        }
        entry?.let { selected ->
            ModalBottomSheet(onDismissRequest={entry=null},containerColor=MaterialTheme.colorScheme.background,
                sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
                EntrySheet(selected,state.fontSize,{entry=null},{entryLimit+=30;vm.entryDetails(selected,entryLimit) { entry=it }}) { entry=null;practice(selected.linkedIds) }
            }
        }
    }
}

@Composable private fun BottomNavigation(route: String, onClick: (String)->Unit) {
    Surface(Modifier.navigationBarsPadding().padding(horizontal=16.dp,vertical=8.dp),shape=RoundedCornerShape(28.dp),
        color=MaterialTheme.colorScheme.surface,shadowElevation=3.dp,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().padding(6.dp),horizontalArrangement=Arrangement.SpaceAround) {
            listOf(Triple("home","首页",Icons.Outlined.Home),Triple("practice","练习",Icons.Outlined.EditNote),Triple("history","历史",Icons.Outlined.History),Triple("settings","设置",Icons.Outlined.Tune)).forEach { (id,label,icon) ->
                val active=route==id
                Column(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable { onClick(id) }.padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(icon,null,tint=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.size(23.dp))
                    Spacer(Modifier.height(3.dp));Text(label,fontSize=11.sp,color=if(active)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable private fun PageHeader(title: String, back: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        if(back!=null) IconButton(onClick=back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"返回") } else Spacer(Modifier.width(12.dp))
        Text(title,Modifier.weight(1f),fontSize=21.sp,fontWeight=FontWeight.Medium)
        action?.invoke()
    }
}

@Composable private fun Eyebrow(text: String) { Text(text,color=MaterialTheme.colorScheme.primary,fontSize=11.sp,letterSpacing=2.sp,fontWeight=FontWeight.Medium) }
@Composable private fun SectionTitle(text: String, trailing: String? = null, onClick: ()->Unit = {}) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(text,Modifier.weight(1f),fontSize=19.sp,fontWeight=FontWeight.Medium)
        if(trailing!=null) TextButton(onClick=onClick) { Text(trailing,fontSize=12.sp) }
    }
}
@Composable private fun Paper(modifier: Modifier=Modifier, content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth(),shape=Rounded,color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
@Composable private fun Tag(text: String, icon: ImageVector? = null) {
    Surface(shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(horizontal=9.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            if(icon!=null) Icon(icon,null,Modifier.size(13.dp),tint=MaterialTheme.colorScheme.primary)
            Text(text,fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun MainButton(text: String, enabled: Boolean=true, onClick:()->Unit) {
    Button(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),shape=RoundedCornerShape(16.dp),contentPadding=PaddingValues(16.dp)) { Text(text,fontSize=16.sp) }
}

@Composable private fun Home(state: AppState, vm: AppViewModel, lookup:(String,Direction)->Unit, go:(String)->Unit, practice:()->Unit) {
    var directionMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    val context=LocalContext.current
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(PagePadding),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                Box {
                    OutlinedButton(onClick={directionMenu=true},shape=RoundedCornerShape(14.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),contentPadding=PaddingValues(horizontal=12.dp)) {
                        Icon(Icons.Outlined.Translate,null,Modifier.size(17.dp));Spacer(Modifier.width(8.dp));Text(TranslationMode.of(state.direction).title,fontSize=12.sp);Icon(Icons.Outlined.ExpandMore,null,Modifier.size(17.dp))
                    }
                    DropdownMenu(expanded=directionMenu,onDismissRequest={directionMenu=false}) { TranslationMode.entries.forEach { d ->
                        DropdownMenuItem(text={Text(d.title)},onClick={vm.direction(if(d==TranslationMode.ENGLISH)Direction.ZH_TO_EN else Direction.CLASSICAL_TO_ZH);directionMenu=false},trailingIcon={if(d==TranslationMode.of(state.direction)) Icon(Icons.Outlined.Check,null)})
                    } }
                }
                Box {
                    TextButton(onClick={modeMenu=true}) { Icon(if(state.mode==Mode.OFFLINE) Icons.Outlined.WifiOff else Icons.Outlined.AutoAwesome,null,Modifier.size(16.dp));Spacer(Modifier.width(6.dp));Text(state.mode.label);Icon(Icons.Outlined.ExpandMore,null,Modifier.size(16.dp)) }
                    DropdownMenu(expanded=modeMenu,onDismissRequest={modeMenu=false}) { Mode.entries.forEach { m -> DropdownMenuItem(text={Text(m.label)},onClick={vm.mode(m);modeMenu=false}) } }
                }
            }
        }
        item {
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) { Icon(Icons.Outlined.AutoStories,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.primary);Eyebrow("文言通  /  WENYAN") }
                Text("读懂古文，\n让理解更进一步。",fontSize=28.sp,lineHeight=38.sp,fontFamily=FontFamily.Serif,letterSpacing=1.sp)
                Text("从一个字，到一句话，再到温故知新。",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Paper {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    Eyebrow("开始探索")
                    if(state.mode==Mode.OFFLINE)Tag("本地资源",Icons.Outlined.VerifiedUser)
                }
                OutlinedTextField(value=state.input,onValueChange=vm::input,placeholder={Text(TranslationMode.of(state.direction).hint,fontSize=17.sp,lineHeight=28.sp)},
                    modifier=Modifier.fillMaxWidth().heightIn(min=110.dp,max=200.dp),textStyle=LocalTextStyle.current.copy(fontSize=state.fontSize.sp,lineHeight=(state.fontSize*1.65).sp),
                    shape=RoundedCornerShape(12.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=Color.Transparent,focusedBorderColor=MaterialTheme.colorScheme.outlineVariant))
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={ val clip=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager;vm.input(clip.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: "") }) { Icon(Icons.Outlined.ContentPaste,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text("粘贴",fontSize=12.sp) }
                    if(state.input.isNotEmpty()) IconButton(onClick={vm.input("")}) { Icon(Icons.Outlined.Close,"清空输入",Modifier.size(18.dp)) }
                    Spacer(Modifier.weight(1f));Text("${state.input.length} / 2000",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                MainButton("翻译与解析  →",state.input.isNotBlank() && !state.busy) { lookup(state.input,state.direction) }
            }
        }
        item {
            Text(if(state.mode==Mode.OFFLINE) "离线模式 · 仅查询已收录内容，学习数据保存在本机。" else if(state.aiSettings.hasKey)"${state.mode.label}模式 · 可结合本地词义请求您配置的 AI。" else "${state.mode.label}模式 · 请在 AI 设置填写渠道，当前先使用本地资源。",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(state.update.phase==UpdatePhase.AVAILABLE) item {
            Paper(Modifier.clickable { go("updates") }) { Text("发现新版本 ${state.update.info?.versionName}",color=MaterialTheme.colorScheme.primary);Text("查看更新说明与下载  →",fontSize=13.sp) }
        }
        item {
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Shortcut("经典古文",Icons.AutoMirrored.Outlined.MenuBook,Modifier.weight(1f)) { go("classics") }
                Shortcut("词典查询",Icons.Outlined.Layers,Modifier.weight(1f)) { go("dictionary") }
                Shortcut("温习练习",Icons.Outlined.School,Modifier.weight(1f),practice)
            }
        }
        item {
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                SectionTitle("今日读一句","试试这句") { lookup("学而时习之，不亦说乎？",Direction.CLASSICAL_TO_ZH) }
                Surface(shape=Rounded,color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth().clickable { lookup("学而时习之，不亦说乎？",Direction.CLASSICAL_TO_ZH) }) {
                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("学而时习之，不亦说乎？",fontFamily=FontFamily.Serif,fontSize=21.sp,lineHeight=32.sp)
                        Text("《论语·学而》  /  学习与温习",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("也可以输入“之”，查看多义项与关联练习。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun Shortcut(title: String, icon: ImageVector, modifier: Modifier, onClick:()->Unit) {
    Surface(modifier.clip(Rounded).clickable(onClick=onClick),shape=Rounded,color=MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(vertical=20.dp,horizontal=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(icon,null,Modifier.size(25.dp),tint=MaterialTheme.colorScheme.primary);Text(title,fontSize=12.sp)
        }
    }
}

@Composable private fun ResultPage(state: AppState, back:()->Unit, onEntry:(Entry)->Unit, practice:(List<String>)->Unit, favorite:(HistoryItem)->Unit) {
    val result=state.result
    val context=LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val item=state.history.firstOrNull { it.query==result?.query && it.direction==result?.direction }
    Column {
        PageHeader("翻译与解析",back) {
            if(item!=null) IconButton(onClick={favorite(item)}) { Icon(if(item.favorite)Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkBorder,if(item.favorite)"取消收藏" else "收藏") }
        }
        if(state.busy) { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) { CircularProgressIndicator() };return }
        if(result==null) { EmptyState("暂时无法查询",state.error ?: "请返回首页重新查询。",Icons.Outlined.SearchOff);return }
        LazyColumn(contentPadding=PaddingValues(PagePadding),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            item {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Tag(result.direction.shortTitle);if(state.mode==Mode.OFFLINE)Tag("本地资源",Icons.Outlined.VerifiedUser) else if(result.passage?.aiGenerated==true)Tag("AI 参考") }
            }
            item { Paper {
                Eyebrow("原文")
                LinkedOriginal(result.query,result.entries,state.fontSize+4,onEntry)
                if(result.entries.isNotEmpty()) Text("点击有颜色的字，查看释义",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            if(state.aiNotice.isNotBlank())item { Text(state.aiNotice,fontSize=13.sp,lineHeight=22.sp) }
            if(result.passage!=null && result.passage.translation.isNotBlank()) {
                val passage=result.passage
                item { Paper {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Eyebrow(if(passage.aiGenerated)"AI 生成译文" else if(result.direction==Direction.ZH_TO_CLASSICAL)"收录古文对照" else "收录译文");Spacer(Modifier.weight(1f))
                        IconButton(onClick={ (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("译文",passage.translation));copied=true }) { Icon(Icons.Outlined.ContentCopy,if(copied)"已复制" else "复制译文",Modifier.size(18.dp)) }
                    }
                    SelectionContainer { Text(passage.translation,fontSize=state.fontSize.sp,lineHeight=(state.fontSize*1.8).sp) }
                    Text(if(copied)"已复制译文" else if(passage.aiGenerated)"AI 生成，需结合出处核对" else "本地收录对照 · 来源见下方",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                item { Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    SectionTitle("句意解析")
                    Text(passage.analysis,fontSize=state.fontSize.sp,lineHeight=(state.fontSize*1.8).sp)
                    Text("出处：${passage.work}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                } }
            } else if(result.entries.isNotEmpty()) item {
                Paper {
                    Eyebrow(if(result.exactWordHit)"已收录词条" else "已命中相关字词")
                    if(!result.exactWordHit) Text("整句暂未收录译文，以下是命中的本地词义。",fontSize=13.sp,lineHeight=22.sp)
                    result.entries.take(if(result.exactWordHit)3 else 2).forEach { e ->
                        Text(e.headword+" · "+e.pinyin,fontSize=21.sp,fontFamily=FontFamily.Serif)
                        e.senses.take(3).forEach { sense -> Text((if(sense.pos.isBlank())"" else sense.pos+" · ")+sense.gloss,fontSize=state.fontSize.sp,lineHeight=(state.fontSize*1.7).sp) }
                        if(e.packs.isNotEmpty())Text("来源："+e.packs.joinToString("、"),fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
                        TextButton(onClick={onEntry(e)}) { Text("查看完整释义与来源") }
                    }
                }
            } else item {
                Paper {
                    Icon(Icons.Outlined.SearchOff,null,tint=MaterialTheme.colorScheme.primary)
                    Text("未找到本地收录内容",fontSize=20.sp)
                    Text("可下载更多对应语言的资源包，或配置 AI 后使用自动/AI 模式补充译文。",fontSize=14.sp,lineHeight=24.sp)
                }
            }
            if(result.entries.isNotEmpty()) item {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    SectionTitle("字词理解")
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        result.entries.forEach { e -> OutlinedButton(onClick={onEntry(e)},shape=RoundedCornerShape(12.dp),modifier=Modifier.heightIn(min=48.dp)) { Text(e.headword,fontSize=21.sp);Spacer(Modifier.width(10.dp));Text(e.pinyin,fontSize=12.sp) } }
                    }
                }
            }
            item { MainButton(if(result.entries.isNotEmpty())"围绕这些字词练一练" else "围绕本次查询练一练") { practice(result.entries.flatMap { it.linkedIds }) } }
            if(result.alternatives.isNotEmpty()) item {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    SectionTitle("对照版本与来源")
                    result.alternatives.forEach { alternative -> Paper {
                        Text(alternative.work,fontSize=14.sp)
                        if(alternative.translation.isNotBlank()) {
                            Text(alternative.translation,fontSize=state.fontSize.sp)
                        } else {
                            Text("此来源仅提供原文，没有白话译文。",fontSize=13.sp)
                        }
                        var expanded by remember(alternative.id) { mutableStateOf(false) }
                        TextButton(onClick={expanded=!expanded}) { Text(if(expanded)"收起来源" else "查看完整来源") }
                        if(expanded) SelectionContainer { Text(alternative.sources.ifEmpty { listOf(alternative.work) }.joinToString("\n\n"),fontSize=12.sp,lineHeight=20.sp) }
                    } }
                }
            }
            item { Text("来源说明：不同资源包的原文、译注和例句按各自来源及许可提供；AI 生成内容会单独标注，请结合出处核对。",fontSize=11.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable private fun LinkedOriginal(text: String, entries: List<Entry>, fontSize: Float, onEntry:(Entry)->Unit) {
    val color=MaterialTheme.colorScheme.primary
    val annotated=buildAnnotatedString {
        var i=0
        while(i<text.length) {
            val entry=entries.sortedByDescending { it.headword.length }.firstOrNull { text.startsWith(it.headword,i) }
            if(entry==null) { append(text[i]);i++ }
            else {
                withLink(LinkAnnotation.Clickable(entry.id,TextLinkStyles(style=SpanStyle(color=color,fontWeight=FontWeight.Medium))) { onEntry(entry) }) { append(entry.headword) }
                i+=entry.headword.length
            }
        }
    }
    Text(annotated,fontSize=fontSize.sp,lineHeight=(fontSize*1.65).sp,fontFamily=FontFamily.Serif)
}

@Composable private fun EntrySheet(entry: Entry, fontSize: Float, close:()->Unit, more:()->Unit, practice:()->Unit) {
    val view=LocalView.current
    val lightSystemBars=MaterialTheme.colorScheme.background.luminance()>0.5f
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.let { window ->
            WindowCompat.getInsetsController(window,window.decorView).apply {
                isAppearanceLightStatusBars=lightSystemBars
                isAppearanceLightNavigationBars=lightSystemBars
            }
        }
    }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(entry.headword,fontSize=46.sp,fontFamily=FontFamily.Serif)
            Text(entry.pinyin,Modifier.padding(start=16.dp).weight(1f),color=MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick=close) { Icon(Icons.Outlined.Close,"关闭释义") }
        }
        if(entry.packs.isNotEmpty()) Text("来自："+entry.packs.joinToString("、"),fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
        Text(entry.summary,fontSize=14.sp,lineHeight=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        SectionTitle("释义与例句")
        Text("以下为候选义项，请结合语境辨析。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        entry.senses.forEachIndexed { index,sense ->
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Tag("${index+1}");Column(verticalArrangement=Arrangement.spacedBy(6.dp)) { Text(sense.pos,color=MaterialTheme.colorScheme.primary,fontSize=12.sp);Text(sense.gloss,fontSize=fontSize.sp,lineHeight=(fontSize*1.7).sp) }
                }
                Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                        if(sense.example.isNotBlank()) Text(sense.example,fontFamily=FontFamily.Serif,fontSize=fontSize.sp,lineHeight=(fontSize*1.7).sp)
                        if(sense.translation.isNotBlank()) Text(sense.translation,fontSize=14.sp,lineHeight=23.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        var showSources by remember(sense.id) { mutableStateOf(false) }
                        Text(sense.source.substringBefore("\n"),fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
                        TextButton(onClick={showSources=!showSources}) { Text(if(showSources)"收起来源" else "查看完整来源") }
                        if(showSources) SelectionContainer { Text(sense.source,fontSize=12.sp,lineHeight=20.sp) }
                    }
                }
            }
        }
        if(entry.hasMore) OutlinedButton(onClick=more) { Text("加载更多释义（已显示 ${entry.senses.size} 条）") }
        MainButton("练习「${entry.headword}」的用法",onClick=practice)
    }
}

@Composable private fun DictionaryPage(state: AppState, back:()->Unit, onEntry:(Entry)->Unit, global:()->Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    Column {
        PageHeader("古汉语词典",back)
        if(state.dictionaryFilter.isNotBlank()) TextButton(onClick=global) { Text("当前：${state.dictionaryFilter} · 搜索全部资源包") }
        OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().padding(horizontal=24.dp),placeholder={Text("查找字词或释义")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=Rounded)
        val list=state.entries.filter { (state.dictionaryFilter.isBlank() || state.dictionaryFilter in it.packs) && (it.headword.contains(search,true) || it.aliases.any { alias -> alias.contains(search,true) } || it.summary.contains(search,true) || it.pinyin.contains(search,true)) }
        LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text("本地词典 · ${state.entries.size} 个词条",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list,key={it.id}) { e ->
                Paper(Modifier.clickable { onEntry(e) }) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(e.headword,fontSize=32.sp,fontFamily=FontFamily.Serif);Text(e.pinyin,Modifier.padding(start=16.dp).weight(1f),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Outlined.ChevronRight,"查看${e.headword}的释义")
                    }
                    if(e.packs.isNotEmpty()) Text(e.packs.joinToString(" · "),fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
                    Text(e.summary,fontSize=14.sp,lineHeight=23.sp);Text("${e.totalSenses} 条释义记录 · 查看例句",fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
                }
            }
            if(list.isEmpty()) item { EmptyState("未找到词条","试试输入“之”“习”或“故”。",Icons.Outlined.SearchOff) }
        }
    }
}

@Composable private fun ClassicsPage(state: AppState, back:()->Unit, select:(Passage)->Unit) {
    Column {
        PageHeader("经典古文",back)
        LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item { Eyebrow("从一句话开始") }
            items(state.passages.filter { it.direction==Direction.CLASSICAL_TO_ZH },key={it.id}) { p ->
                Paper(Modifier.clickable { select(p) }) { Text(p.original,fontSize=21.sp,lineHeight=34.sp,fontFamily=FontFamily.Serif);Text(p.work,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("阅读与解析  →",fontSize=12.sp,color=MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable private fun PracticeConfig(state: AppState, scope: String, wrong:()->Unit, cancel:()->Unit, start:(Int,Boolean,Set<QuestionKind>)->Unit) {
    var count by rememberSaveable { mutableIntStateOf(5) }
    var useAi by rememberSaveable(state.aiSettings.hasKey,state.mode) { mutableStateOf(state.aiSettings.hasKey && state.mode!=Mode.OFFLINE) }
    var kinds by remember { mutableStateOf(setOf(QuestionKind.SINGLE)) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Eyebrow("温故 · 知新")
        Text("把理解，\n变成自己的知识。",fontSize=29.sp,lineHeight=40.sp,fontFamily=FontFamily.Serif)
        Text(if(scope.isBlank())"结合当前搜索结果与已下载词库，开始一次练习。" else "本次练习关联：${state.entries.filter { it.id in scope.split(",") }.joinToString("、") { it.headword }}",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Paper {
            SectionTitle("练习设置")
            Text("题型",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { QuestionKind.entries.forEach { kind -> FilterChip(kind in kinds,{kinds=if(kind in kinds)kinds-kind else kinds+kind},label={Text(kind.title)},enabled=!state.practiceBusy) } }
            Text("可选择多种题型。所有查询均可进入练习；未收录的内容需配置 AI 在线出题。",fontSize=12.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Text("题目数量",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) { listOf(3,5,10).forEach { n -> FilterChip(selected=count==n,onClick={count=n},label={Text("$n 题")},enabled=!state.practiceBusy,modifier=Modifier.heightIn(min=48.dp)) } }
            Text("题库不足时按实际可用数量组卷。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(useAi,{useAi=it},enabled=!state.practiceBusy)
                Column { Text("AI 根据搜索结果与词库出题");Text(if(state.aiSettings.hasKey)"发送本次查询与相关词义；格式不符最多重试一次，可能产生 API 费用" else "请先在设置中配置 AI 渠道",fontSize=12.sp) }
            }
            if(state.mode==Mode.OFFLINE && useAi)Text("当前是离线模式，请切换自动或 AI 模式后生成。",fontSize=13.sp)
            if(state.practiceBusy) { LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick=cancel) { Text("取消出题") } }
            if(state.practiceNotice.isNotBlank())Text(state.practiceNotice,fontSize=13.sp,lineHeight=22.sp)
            MainButton(if(state.practiceBusy)"正在准备练习…" else "开始练习",!state.practiceBusy && kinds.isNotEmpty()) { start(count,useAi,kinds) }
        }
        Paper(Modifier.clickable(onClick=wrong)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Outlined.BookmarkBorder,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)) { Text("我的错题本",fontSize=17.sp);Text("${state.wrongCount} 道待巩固的题目",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) };Icon(Icons.Outlined.ChevronRight,"查看错题本") }
        }
        Text("每次提交都会保存答题记录。例句标注出处，解释为原创教学示例。",fontSize=12.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun QuizPage(state: AppState, vm: AppViewModel, back:()->Unit, report:()->Unit) {
    LaunchedEffect(state.practiceFinished) { if(state.practiceFinished) report() }
    val question=state.questions.getOrNull(state.questionIndex)
    var leave by remember { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(enabled=question!=null && !state.practiceFinished) { leave=true }
    Column {
        PageHeader("练习测试",{if(question==null)back() else leave=true})
        if(question==null) { EmptyState("暂无关联题目","示例题库暂未收录此范围的练习，可返回选择综合练习。",Icons.Outlined.School);TextButton(onClick=back,modifier=Modifier.padding(horizontal=24.dp)) { Text("返回练习设置") };return }
        LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            item {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Eyebrow((if(question.edited)"用户编辑" else if(question.id.startsWith("ai-") && !question.id.startsWith("ai-offline-"))"AI 生成" else "本地题库")+" · "+question.kind.title);Text("${state.questionIndex+1} / ${state.questions.size}",fontSize=13.sp) }
                Spacer(Modifier.height(14.dp));LinearProgressIndicator(progress={state.questionIndex.toFloat()/state.questions.size},modifier=Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),trackColor=MaterialTheme.colorScheme.surfaceVariant)
            }
            item { Text(question.stem,fontSize=(state.fontSize+3).sp,lineHeight=(state.fontSize*1.9).sp,fontWeight=FontWeight.Medium) }
            items(question.options.indices.toList()) { index ->
                val chosen=state.selected==index
                val correct=state.submitted && index==question.answer
                val wrong=state.submitted && chosen && !correct
                val border=when { correct->Color(0xFF5B8061);wrong->MaterialTheme.colorScheme.error;chosen->MaterialTheme.colorScheme.primary;else->MaterialTheme.colorScheme.outlineVariant }
                Surface(shape=RoundedCornerShape(15.dp),border=BorderStroke(if(chosen||correct)2.dp else 1.dp,border),color=if(chosen)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).clickable(enabled=!state.submitted && !state.savingAnswer) { vm.select(index) }) {
                    Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        Surface(shape=CircleShape,color=if(chosen)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.size(30.dp)) {
                            Box(contentAlignment=Alignment.Center) { Text(('A'+index).toString(),color=if(chosen)MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,fontSize=14.sp) }
                        }
                        Text(question.options[index],Modifier.weight(1f),fontSize=state.fontSize.sp,lineHeight=(state.fontSize*1.65).sp)
                        if(correct) Icon(Icons.Outlined.CheckCircle,"正确答案",tint=Color(0xFF5B8061))
                    }
                }
            }
            if(state.submitted) item {
                Paper {
                    Eyebrow(if(state.selected==question.answer)"答对了 · 继续保持" else "再巩固一下")
                    Text("正确答案：${'A'+question.answer}",fontWeight=FontWeight.Medium)
                    Text(question.explanation,fontSize=state.fontSize.sp,lineHeight=(state.fontSize*1.7).sp)
                }
            }
            if(state.error!=null) item { Text(state.error,color=MaterialTheme.colorScheme.error) }
            item { MainButton(if(state.submitted) { if(state.questionIndex+1==state.questions.size)"查看练习报告" else "下一题" } else if(state.savingAnswer)"正在保存…" else "提交答案",state.selected!=null && !state.savingAnswer) { if(state.submitted)vm.next() else vm.submit() } }
        }
    }
    if(leave) AlertDialog(onDismissRequest={leave=false},title={Text("结束本次练习？")},text={Text("已提交的答案与错题已保存。返回后可重新开始练习。")},confirmButton={TextButton(onClick={leave=false;back()}) { Text("结束练习") }},dismissButton={TextButton(onClick={leave=false}) { Text("继续答题") }})
}

@Composable private fun ReportPage(state: AppState, done:()->Unit) {
    val correct=state.answers.count { it.correct }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Spacer(Modifier.height(24.dp));Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(42.dp),tint=MaterialTheme.colorScheme.primary)
        Eyebrow("每一次温习，都有收获")
        Text("练习完成",fontSize=34.sp,fontFamily=FontFamily.Serif)
        Paper {
            Text("$correct / ${state.answers.size}",fontSize=46.sp,fontFamily=FontFamily.Serif,color=MaterialTheme.colorScheme.primary)
            Text("答对题数",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if(correct==state.answers.size)"这一轮掌握得不错，继续保持。" else "本次错题已加入错题本，稍后可以再次巩固。",fontSize=14.sp,lineHeight=24.sp)
        }
        state.questions.forEach { q -> val a=state.answers.firstOrNull { it.questionId==q.id };Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { Icon(if(a?.correct==true)Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,null,tint=if(a?.correct==true)Color(0xFF5B8061) else MaterialTheme.colorScheme.primary);Text(q.stem,fontSize=14.sp,lineHeight=23.sp) } }
        MainButton("完成，返回练习",onClick=done)
    }
}

@Composable private fun WrongPage(state: AppState, vm: AppViewModel, back:()->Unit, practice:()->Unit) {
    var editing by remember { mutableStateOf<Question?>(null) }
    Column { PageHeader("我的错题本",back)
        LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            item { if(state.wrongCount==0) EmptyState("暂时没有错题","每次答错的题目都会自动保存在这里。",Icons.Outlined.CheckCircle)
            else { Text("${state.wrongCount} 道题，值得再看一遍。",fontSize=25.sp,lineHeight=36.sp,fontFamily=FontFamily.Serif);Text("错题保存在本机，复练答对后仍会保留，方便反复温习。",fontSize=14.sp,lineHeight=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);MainButton("开始错题复练",onClick=practice) } }
            if(state.wrongNotice.isNotBlank())item { Text(state.wrongNotice) }
            items(state.wrongQuestions,key={it.id}) { q -> Paper(Modifier.testTag("wrong-${q.id}")) { Tag(q.kind.title);Text(q.stem,fontSize=18.sp);Text("正确答案：${q.options.getOrNull(q.answer).orEmpty()}");TextButton(onClick={editing=q}) { Text("编辑错题") } } }
        }
    }
    editing?.let { q -> QuestionEditor(q,{editing=null}) { vm.editWrong(it);editing=null } }
}

@Composable private fun QuestionEditor(question: Question,dismiss:()->Unit,save:(Question)->Unit) {
    var stem by remember(question.id) { mutableStateOf(question.stem) }
    var options by remember(question.id) { mutableStateOf(question.options) }
    var answer by remember(question.id) { mutableIntStateOf(question.answer) }
    var explanation by remember(question.id) { mutableStateOf(question.explanation) }
    com.wenyantong.app.core.designsystem.EditorDialog("编辑错题","保存修改",stem.isNotBlank() && explanation.isNotBlank() && options.all { it.isNotBlank() } && options.distinct().size==options.size,dismiss,{save(question.copy(stem=stem,options=options,answer=answer,explanation=explanation,edited=true))}) {
        OutlinedTextField(stem,{stem=it.take(2000)},label={Text("题干")})
        options.forEachIndexed { index,value -> Row(verticalAlignment=Alignment.CenterVertically) { RadioButton(answer==index,{answer=index});OutlinedTextField(value,{new -> options=options.mapIndexed { i,v -> if(i==index)new.take(500) else v }},label={Text("选项 ${'A'+index}")},modifier=Modifier.weight(1f)) } }
        Text("点击圆点设置正确答案。")
        OutlinedTextField(explanation,{explanation=it.take(16000)},label={Text("答案解析")})
        Text("修改只影响个人错题，不更改资源包。")
    }
}

@Composable private fun HistoryPage(state: AppState, favorite:(HistoryItem)->Unit, delete:(HistoryItem)->Unit, select:(HistoryItem)->Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<HistoryItem?>(null) }
    val list=state.history.filter { (!favoritesOnly || it.favorite) && it.query.contains(search,true) }
    Column {
        PageHeader("我的学习足迹")
        Row(Modifier.padding(horizontal=24.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) { FilterChip(!favoritesOnly,{favoritesOnly=false},label={Text("历史记录")},modifier=Modifier.heightIn(min=48.dp));FilterChip(favoritesOnly,{favoritesOnly=true},label={Text("我的收藏")},modifier=Modifier.heightIn(min=48.dp)) }
        OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=8.dp),placeholder={Text("搜索学习记录")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=Rounded)
        LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            items(list,key={it.id}) { item -> Paper(Modifier.clickable { select(item) }) {
                Text(item.query,fontSize=19.sp,lineHeight=30.sp,fontFamily=FontFamily.Serif)
                Text(item.direction.shortTitle+" · "+SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(Date(item.timestamp)),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) { IconButton(onClick={favorite(item)}) { Icon(if(item.favorite)Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkBorder,if(item.favorite)"取消收藏" else "收藏",tint=MaterialTheme.colorScheme.primary) };IconButton(onClick={deleting=item}) { Icon(Icons.Outlined.DeleteOutline,"删除记录",tint=MaterialTheme.colorScheme.onSurfaceVariant) } }
            } }
            if(list.isEmpty()) item { EmptyState(if(favoritesOnly)"还没有收藏" else "这里等待你的第一句古文",if(search.isBlank())"从首页开始查询，留下学习足迹。" else "没有找到匹配的记录。",Icons.Outlined.History) }
        }
    }
    deleting?.let { item -> AlertDialog(onDismissRequest={deleting=null},title={Text("删除这条记录？")},text={Text("这条记录的收藏状态也会一并删除。")},confirmButton={TextButton(onClick={delete(item);deleting=null}) { Text("删除") }},dismissButton={TextButton(onClick={deleting=null}) { Text("取消") }}) }
}

@Composable private fun SettingsPage(state: AppState, vm: AppViewModel, go:(String)->Unit) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        PageHeader("设置")
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Paper { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) { Icon(Icons.Outlined.AutoStories,null,Modifier.size(36.dp),tint=MaterialTheme.colorScheme.primary);Column { Text("文言通",fontSize=25.sp,fontFamily=FontFamily.Serif);Text("${BuildConfig.VERSION_NAME} · 离线学习",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) } };Text("一字一句，慢慢读懂。",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            Paper {
                SettingRow("版本更新","检查 GitHub 发布、下载与安装",Icons.Outlined.SystemUpdate) {go("updates")}
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SettingRow("数据与存储","本地路径、导出与合并导入",Icons.Outlined.Storage) {go("storage")}
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SettingRow("离线资源","内置示例库与来源说明",Icons.Outlined.Inventory2) {go("resources")}
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SettingRow("主题与阅读","外观、字号与阅读预览",Icons.Outlined.TextFields) {go("reading")}
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SettingRow("AI 设置",if(state.aiSettings.hasKey)state.aiSettings.model else "填写渠道、模型和 API Key",Icons.Outlined.AutoAwesome) {go("ai")}
            }
            SectionTitle("运行模式")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Mode.entries.forEach { mode -> FilterChip(state.mode==mode,{vm.mode(mode)},label={Text(mode.label)},modifier=Modifier.heightIn(min=48.dp)) } }
            Text("离线模式仅使用本机词库；配置自己的渠道后，自动/AI 模式可生成参考译文和关联练习。密钥只在本机加密保存。",fontSize=13.sp,lineHeight=23.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("学习历史、收藏、错题与阅读设置均保存在本机。无需账号。",fontSize=12.sp,lineHeight=21.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun SettingRow(title: String, subtitle: String, icon: ImageVector, onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick=onClick).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) { Text(title,fontSize=16.sp);Text(subtitle,fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        Icon(Icons.Outlined.ChevronRight,null)
    }
}

@Composable private fun ResourcePage(state: AppState, vm: AppViewModel, back:()->Unit, go:(String)->Unit) {
    var language by rememberSaveable { mutableStateOf("all") }
    var category by rememberSaveable { mutableStateOf("all") }
    fun visible(lang: String,type: String)=(language=="all" || language==lang) && (category=="all" || category==type)
    var consent by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<com.wenyantong.app.feature.resources.InstalledPack?>(null) }
    var importing by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { importing=it }
    if(consent) AlertDialog(onDismissRequest={consent=false},title={Text("连接 GitHub 检查资源？")},text={Text("获取可选资源目录，您可以逐包选择下载。仅使用已下载包进行离线搜索。")},confirmButton={TextButton(onClick={consent=false;vm.checkResources()}){Text("允许检查")}},dismissButton={TextButton(onClick={consent=false}){Text("取消")}})
    remove?.let { pack -> AlertDialog(onDismissRequest={remove=null},title={Text("删除 ${pack.title}？")},text={Text("仅删除此下载包，保留历史、收藏、错题和其他包。可随时重新下载。")},confirmButton={TextButton(onClick={remove=null;vm.removeResource(pack.id)}){Text("删除此包")}},dismissButton={TextButton(onClick={remove=null}){Text("取消")}}) }
    importing?.let { uri -> AlertDialog(onDismissRequest={importing=null},title={Text("导入资源包？")},text={Text("仅导入可信 ZIP；独立安装或更新同 ID 的包，不替换其他资源。")},confirmButton={TextButton(onClick={importing=null;vm.importResource(uri)}){Text("校验并导入")}},dismissButton={TextButton(onClick={importing=null}){Text("取消")}}) }
    Column { PageHeader("离线资源",back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text("选择需要的资源，\n离线慢慢读。",fontSize=27.sp,lineHeight=39.sp,fontFamily=FontFamily.Serif)
            Text("已下载 ${state.installedPacks.size} 个包 · 全局查词 ${state.entries.size} 个不同字词\n内置示例始终可用；原文包不提供白话译文。",fontSize=13.sp,lineHeight=23.sp)
            Button(onClick={if(state.mode==Mode.OFFLINE)consent=true else vm.checkResources()},enabled=!state.resourceBusy,modifier=Modifier.fillMaxWidth()) { Text("检查资源更新") }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={vm.dictionaryFilter("");go("dictionary")}) { Text("全局查词") }
                OutlinedButton(onClick={vm.loadArticles();go("classics")}) { Text("浏览古文") }
            }
            if(state.resourceBusy) {
                LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                Text(state.resourceMessage)
                if(state.resourceDownloaded>0) Text("已下载 ${state.resourceDownloaded/1024} KB")
                TextButton(onClick=vm::cancelResource,enabled=!state.resourceInstalling) { Text("取消下载") }
            } else if(state.resourceMessage.isNotBlank()) Text(state.resourceMessage,fontSize=14.sp)
            Text("语言",fontSize=15.sp)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("all" to "全部语言","zh" to "中文","en" to "英文").forEach { (id,label) -> FilterChip(language==id,{language=id},label={Text(label)}) } }
            Text("资源类型",fontSize=15.sp)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("all" to "全部类型","basic" to "综合基础","dictionary" to "字词字书","reading" to "古文译注","original" to "古籍原文","vocabulary" to "英文词汇").forEach { (id,label) -> FilterChip(category==id,{category=id},label={Text(label)}) } }
            val offers=state.resourceCatalog
            state.installedPacks.filter { installed -> visible(installed.language,installed.category) && offers.none { it.id==installed.id } }.forEach { pack -> Paper {
                Text(pack.title,fontSize=18.sp);Text("已安装 ${pack.version} · ${pack.entries} 字词 · ${pack.articles} 篇章",fontSize=13.sp)
                Text(pack.review,fontSize=12.sp,lineHeight=20.sp)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(pack.entries>0) OutlinedButton(onClick={vm.dictionaryFilter(pack.title);go("dictionary")}) { Text("查看词条") }
                    if(pack.articles>0) OutlinedButton(onClick={vm.loadArticles(pack.id);go("classics")}) { Text("浏览此包") }
                    TextButton(onClick={remove=pack},enabled=!state.resourceBusy) { Text("删除") }
                }
            } }
            offers.filter { visible(it.language,it.category) }.forEach { offer ->
                val installed=state.installedPacks.firstOrNull { it.id==offer.id }
                Paper(Modifier.testTag("resource-${offer.id}")) {
                    Text(offer.title,fontSize=19.sp)
                    Text("v${offer.version} · ${"%.2f".format(offer.bytes/1048576.0)} MB · ${offer.license}",fontSize=12.sp)
                    Text("${offer.entryCount} 字词 · ${offer.articleCount} 篇章",fontSize=13.sp)
                    Text(offer.notes,fontSize=14.sp,lineHeight=23.sp)
                    if(offer.review.isNotBlank()) Text(offer.review,fontSize=12.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.primary)
                    if(installed==null || installed.revision<offer.revision) Button(onClick={vm.downloadResource(offer)},enabled=!state.resourceBusy && offer.minApp<=BuildConfig.VERSION_CODE) { Text(if(offer.minApp>BuildConfig.VERSION_CODE)"需先升级应用" else if(installed==null)"下载此包" else "更新此包") }
                    else Text("已安装 · ${installed.version}",color=MaterialTheme.colorScheme.primary)
                    if(installed!=null) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(offer.entryCount>0) OutlinedButton(onClick={vm.dictionaryFilter(offer.title);go("dictionary")}) { Text("查看词条") }
                        if(offer.articleCount>0) OutlinedButton(onClick={vm.loadArticles(offer.id);go("classics")}) { Text("浏览此包") }
                        TextButton(onClick={remove=installed},enabled=!state.resourceBusy) { Text("删除") }
                    }
                }
            }
            OutlinedButton(onClick={picker.launch(arrayOf("application/zip","application/octet-stream","application/x-zip-compressed"))},enabled=!state.resourceBusy) { Text("导入本地资源 ZIP") }
            Text("各包单独安装、更新和删除。所有已下载包参与全局查词；不同释义和译文分别保留来源。AI 译注未经逐条学术审校，请对照原文使用。",fontSize=12.sp,lineHeight=21.sp)
        }
    }
}

@Composable private fun LibraryPage(state: AppState, vm: AppViewModel, back:()->Unit, open:(LibraryArticle)->Unit, sample:(Passage)->Unit) {
    var query by rememberSaveable { mutableStateOf(state.articleQuery) }
    val keyboard=androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(Unit) { vm.loadArticles(state.articlePack,state.articleQuery) }
    Column {
        PageHeader("经典古文",back)
        OutlinedTextField(query,{query=it;vm.loadArticles(state.articlePack,it)},Modifier.fillMaxWidth().padding(horizontal=24.dp),placeholder={Text("搜索已下载篇目或作者")},singleLine=true)
        if(state.articlePack.isNotBlank()) TextButton(onClick={vm.loadArticles()}) { Text("显示所有已下载资源") }
        LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            if(state.articleBusy)item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if(state.articlePack.isBlank())items(state.passages.filter { it.direction==Direction.CLASSICAL_TO_ZH && (query.isBlank() || it.work.contains(query) || it.original.contains(query)) },key={"sample-"+it.id}) { p -> Paper(Modifier.clickable { sample(p) }) { Text(p.original,fontSize=20.sp);Text(p.work,fontSize=12.sp) } }
            items(state.articles,key={it.id}) { item -> Paper(Modifier.clickable { focus.clearFocus();keyboard?.hide();open(item) }) {
                Text(item.title,fontSize=20.sp,fontFamily=FontFamily.Serif)
                Text(item.author+" · "+item.dynasty,fontSize=12.sp)
                Text(state.installedPacks.firstOrNull { it.id==item.packId }?.title ?: item.packId,fontSize=12.sp,color=MaterialTheme.colorScheme.primary)
            } }
            if(!state.articleBusy && state.articleMore)item { OutlinedButton(onClick={vm.loadArticles(state.articlePack,query,true)}) { Text("加载更多篇目") } }
            if(!state.articleBusy && state.articles.isEmpty())item { Text("此包可能为字词包；请使用全局查词查看词条。",fontSize=13.sp) }
        }
    }
}

@Composable private fun ArticlePage(state: AppState, back:()->Unit) {
    Column { PageHeader("古文阅读",back)
        val article=state.articleText
        if(state.articleBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(article!=null) {
            val sections=remember(article) { buildList {
                add("原文" to article.original)
                if(article.translation.isNotBlank())add("白话对照" to article.translation)
                if(article.notes.isNotBlank())add("上游解析" to article.notes)
                add("来源与整理说明" to article.source)
            }.flatMap { (title,body) -> listOf(true to title)+body.chunked(2000).map { false to it } } }
            LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item { Text(article.title,fontSize=27.sp,fontFamily=FontFamily.Serif);Text(article.author,fontSize=14.sp) }
                items(sections) { (heading,body) -> if(heading)Text(body,fontWeight=FontWeight.Bold,fontSize=17.sp) else SelectionContainer { Text(body,fontSize=state.fontSize.sp,lineHeight=(state.fontSize*1.8).sp) } }
            }
        } else if(!state.articleBusy) Text(state.error ?: "请返回选择文章。",Modifier.padding(24.dp))
    }
}

@Composable private fun ReadingPage(state: AppState, vm: AppViewModel, back:()->Unit) {
    Column { PageHeader("主题与阅读",back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            SectionTitle("外观")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("system" to "跟随系统","light" to "浅色","dark" to "深色").forEach { (value,label) -> FilterChip(state.theme==value,{vm.reading(value,state.fontSize)},label={Text(label)},modifier=Modifier.heightIn(min=48.dp)) } }
            SectionTitle("正文字号 · ${state.fontSize.toInt()}")
            Slider(value=state.fontSize,onValueChange={vm.reading(state.theme,it)},valueRange=16f..26f,steps=9)
            Paper { Eyebrow("阅读预览");Text("学而时习之，不亦说乎？",fontSize=(state.fontSize+3).sp,lineHeight=(state.fontSize*1.9).sp,fontFamily=FontFamily.Serif);Text("学习后时常温习所学的内容，不也令人愉快吗？",fontSize=state.fontSize.sp,lineHeight=(state.fontSize*1.8).sp);Text("设置会自动保存在本机。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable private fun EmptyState(title: String, description: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(vertical=36.dp,horizontal=24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Icon(icon,null,Modifier.size(38.dp),tint=MaterialTheme.colorScheme.primary)
        Text(title,fontSize=21.sp,lineHeight=31.sp,fontFamily=FontFamily.Serif)
        Text(description,fontSize=14.sp,lineHeight=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
