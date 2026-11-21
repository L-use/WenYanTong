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
                    composable("home") { Home(state,vm,::lookup,{go(it)},{practice(emptyList())}) }
                    composable("result") { ResultPage(state,{nav.popBackStack()},{entry=it},{practice(it)},{item->vm.favorite(item)}) }
                    composable("dictionary") { DictionaryPage(state,{nav.popBackStack()},{entry=it}) }
                    composable("classics") { ClassicsPage(state,{nav.popBackStack()}) { lookup(it.original,it.direction) } }
                    composable("practice") { PracticeConfig(state,practiceScope,{go("wrong")}) { count ->
                        vm.startPractice(practiceScope.split(",").filter(String::isNotBlank).toSet(),count) { go("quiz") }
                    } }
                    composable("quiz") { QuizPage(state,vm,{nav.popBackStack()},{nav.navigate("report") { popUpTo("quiz") { inclusive=true };launchSingleTop=true }}) }
                    composable("report") { ReportPage(state) { nav.popBackStack("practice",false) } }
                    composable("wrong") { WrongPage(state,{nav.popBackStack()}) { vm.startPractice(emptySet(),20,true) { go("quiz") } } }
                    composable("history") { HistoryPage(state,{vm.favorite(it)},{vm.delete(it)}) { lookup(it.query,it.direction) } }
                    composable("settings") { SettingsPage(state,vm,{go(it)}) }
                    composable("resources") { ResourcePage(state,vm,{nav.popBackStack()},{go(it)}) }
                    composable("reading") { ReadingPage(state,vm,{nav.popBackStack()}) }
                    composable("ai") { AiPage({nav.popBackStack()}) }
                    composable("updates") { UpdatePage(state,vm) { nav.popBackStack() } }
                    composable("storage") { StoragePage(state,vm) { nav.popBackStack() } }
                }
            }
        }
        entry?.let { selected ->
            ModalBottomSheet(onDismissRequest={entry=null},containerColor=MaterialTheme.colorScheme.background,
                sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
                EntrySheet(selected,state.fontSize,{entry=null}) { entry=null;practice(listOf(selected.id)) }
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
                        Icon(Icons.Outlined.Translate,null,Modifier.size(17.dp));Spacer(Modifier.width(8.dp));Text(state.direction.shortTitle,fontSize=12.sp);Icon(Icons.Outlined.ExpandMore,null,Modifier.size(17.dp))
                    }
                    DropdownMenu(expanded=directionMenu,onDismissRequest={directionMenu=false}) { Direction.entries.forEach { d ->
                        DropdownMenuItem(text={Text(d.title)},onClick={vm.direction(d);directionMenu=false},trailingIcon={if(d==state.direction) Icon(Icons.Outlined.Check,null)})
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
                    Tag("本地资源",Icons.Outlined.VerifiedUser)
                }
                OutlinedTextField(value=state.input,onValueChange=vm::input,placeholder={Text(state.direction.hint,fontSize=17.sp,lineHeight=28.sp)},
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
            Text(if(state.mode==Mode.OFFLINE) "离线模式 · 仅查询已收录内容，学习数据保存在本机。" else "${state.mode.label}模式偏好已保存 · 初版尚未接入 AI，当前仅查本地资源。",fontSize=12.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Tag(result.direction.shortTitle);Tag("离线资源",Icons.Outlined.VerifiedUser) }
            }
            item { Paper {
                Eyebrow("原文")
                LinkedOriginal(result.query,result.entries,state.fontSize+4,onEntry)
                if(result.entries.isNotEmpty()) Text("点击有颜色的字，查看释义",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            if(result.passage!=null) {
                val passage=result.passage
                item { Paper {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Eyebrow(if(result.direction==Direction.ZH_TO_CLASSICAL)"收录古文对照" else "收录译文");Spacer(Modifier.weight(1f))
                        IconButton(onClick={ (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("译文",passage.translation));copied=true }) { Icon(Icons.Outlined.ContentCopy,if(copied)"已复制" else "复制译文",Modifier.size(18.dp)) }
                    }
                    SelectionContainer { Text(passage.translation,fontSize=state.fontSize.sp,lineHeight=(state.fontSize*1.8).sp) }
                    Text(if(copied)"已复制译文" else "项目原创示例译文 · 非权威审校版本",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                item { Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    SectionTitle("句意解析")
                    Text(passage.analysis,fontSize=state.fontSize.sp,lineHeight=(state.fontSize*1.8).sp)
                    Text("出处：${passage.work}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                } }
            } else item {
                Paper {
                    Icon(Icons.Outlined.SearchOff,null,tint=MaterialTheme.colorScheme.primary)
                    Text("尚无收录译文",fontSize=20.sp)
                    Text(if(result.entries.isNotEmpty())"可以先查看下方已收录字词。字词命中不代表整句译文命中。" else "示例库暂未收录这段内容。可尝试首页推荐句或到经典古文中选择；云端 AI 将在后续版本接入。",fontSize=14.sp,lineHeight=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if(result.entries.isNotEmpty()) item {
                Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    SectionTitle("字词理解")
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        result.entries.forEach { e -> OutlinedButton(onClick={onEntry(e)},shape=RoundedCornerShape(12.dp),modifier=Modifier.heightIn(min=48.dp)) { Text(e.headword,fontSize=21.sp);Spacer(Modifier.width(10.dp));Text(e.pinyin,fontSize=12.sp) } }
                    }
                    MainButton("围绕这些字词练一练") { practice(result.entries.map { it.id }) }
                }
            }
            item { Text("来源说明：古籍原文为公有领域文本；释义、译文和题目为项目原创教学示例，尚未经学术审校。",fontSize=11.sp,lineHeight=19.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
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

@Composable private fun EntrySheet(entry: Entry, fontSize: Float, close:()->Unit, practice:()->Unit) {
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
                        Text(sense.example,fontFamily=FontFamily.Serif,fontSize=fontSize.sp,lineHeight=(fontSize*1.7).sp)
                        Text(sense.translation,fontSize=14.sp,lineHeight=23.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(sense.source,fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        MainButton("练习「${entry.headword}」的用法",onClick=practice)
    }
}

@Composable private fun DictionaryPage(state: AppState, back:()->Unit, onEntry:(Entry)->Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    Column {
        PageHeader("古汉语词典",back)
        OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().padding(horizontal=24.dp),placeholder={Text("查找字词或释义")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true,shape=Rounded)
        val list=state.entries.filter { it.headword.contains(search) || it.summary.contains(search) || it.pinyin.contains(search,true) }
        LazyColumn(contentPadding=PaddingValues(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            item { Text("本地词典 · ${state.entries.size} 个词条",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            items(list,key={it.id}) { e ->
                Paper(Modifier.clickable { onEntry(e) }) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(e.headword,fontSize=32.sp,fontFamily=FontFamily.Serif);Text(e.pinyin,Modifier.padding(start=16.dp).weight(1f),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Outlined.ChevronRight,"查看${e.headword}的释义")
                    }
                    Text(e.summary,fontSize=14.sp,lineHeight=23.sp);Text("${e.senses.size} 个义项 · 查看例句",fontSize=11.sp,color=MaterialTheme.colorScheme.primary)
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

@Composable private fun PracticeConfig(state: AppState, scope: String, wrong:()->Unit, start:(Int)->Unit) {
    var count by rememberSaveable { mutableIntStateOf(5) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Eyebrow("温故 · 知新")
        Text("把理解，\n变成自己的知识。",fontSize=29.sp,lineHeight=40.sp,fontFamily=FontFamily.Serif)
        Text(if(scope.isBlank())"从本地题库开始一次小练习。" else "本次练习关联：${state.entries.filter { it.id in scope.split(",") }.joinToString("、") { it.headword }}",fontSize=14.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Paper {
            SectionTitle("练习设置")
            Text("题型",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Tag("单选题 · 初版已开放",Icons.Outlined.RadioButtonChecked)
            Text("多选、填空等其他题型将在后续迭代加入。",fontSize=12.sp,lineHeight=20.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Text("题目数量",fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) { listOf(3,5,10).forEach { n -> FilterChip(selected=count==n,onClick={count=n},label={Text("$n 题")},modifier=Modifier.heightIn(min=48.dp)) } }
            Text("题库不足时按实际可用数量组卷。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            MainButton("开始练习") { start(count) }
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
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Eyebrow("单选题");Text("${state.questionIndex+1} / ${state.questions.size}",fontSize=13.sp) }
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

@Composable private fun WrongPage(state: AppState, back:()->Unit, practice:()->Unit) {
    Column { PageHeader("我的错题本",back)
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            if(state.wrongCount==0) EmptyState("暂时没有错题","每次答错的题目都会自动保存在这里。",Icons.Outlined.CheckCircle)
            else { Text("${state.wrongCount} 道题，值得再看一遍。",fontSize=25.sp,lineHeight=36.sp,fontFamily=FontFamily.Serif);Text("错题保存在本机，复练答对后仍会保留，方便反复温习。",fontSize=14.sp,lineHeight=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);MainButton("开始错题复练",onClick=practice) }
        }
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
                SettingRow("AI 设置","接入规划与当前能力",Icons.Outlined.AutoAwesome) {go("ai")}
            }
            SectionTitle("运行模式")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Mode.entries.forEach { mode -> FilterChip(state.mode==mode,{vm.mode(mode)},label={Text(mode.label)},modifier=Modifier.heightIn(min=48.dp)) } }
            Text("初版仅使用本地资源。自动与 AI 偏好会保留，待云端能力接入后生效。",fontSize=13.sp,lineHeight=23.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
    var consent by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }
    val picker=androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri -> confirmImport=uri }
    if(consent) AlertDialog(onDismissRequest={consent=false},title={Text("连接 GitHub 检查资源？")},text={Text("本次检查及后续下载会使用网络。学习模式保持不变。")},confirmButton={TextButton(onClick={consent=false;vm.checkResources()}) { Text("允许检查") }},dismissButton={TextButton(onClick={consent=false}){Text("取消")}})
    confirmImport?.let { uri -> AlertDialog(onDismissRequest={confirmImport=null},title={Text("导入本地资源包？")},text={Text("仅导入可信来源的资源 ZIP。软件会校验格式与完整性，安装后替换当前基础库，保留个人学习数据。")},confirmButton={TextButton(onClick={confirmImport=null;vm.importResource(uri)}){Text("校验并导入")}},dismissButton={TextButton(onClick={confirmImport=null}){Text("取消")}}) }
    Column { PageHeader("离线资源",back)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Eyebrow("下载一次 · 离线使用")
            Text("把常用古文，\n留在口袋里。",fontSize=29.sp,lineHeight=41.sp,fontFamily=FontFamily.Serif)
            Paper {
                Text("常用文言文基础库",fontSize=19.sp)
                Text("已安装 v${state.resourceVersion} · SHA-256 校验",fontSize=13.sp)
                Text("${state.entries.size} 个词条 · ${state.entries.sumOf { it.senses.size }} 个义项\n${state.passages.size} 组文本对照 · ${state.resourceQuestions} 道单选题",fontSize=16.sp,lineHeight=28.sp)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick={go("dictionary")}) { Text("查看词条") }
                    OutlinedButton(onClick={go("classics")}) { Text("查看古文") }
                }
            }
            Button(onClick={if(state.mode==Mode.OFFLINE)consent=true else vm.checkResources()},enabled=!state.resourceBusy,modifier=Modifier.fillMaxWidth()) { Text("检查资源更新") }
            state.resourceOffer?.let { offer -> Paper {
                Text("可下载 v${offer.version}",fontSize=19.sp)
                Text("${offer.bytes/1024} KB · ${offer.notes}",fontSize=14.sp,lineHeight=24.sp)
                Button(onClick=vm::downloadResource,enabled=!state.resourceBusy) { Text("下载并安装资源包") }
            } }
            if(state.resourceBusy) {
                LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
                if(state.resourceDownloaded>0) Text("已下载 ${state.resourceDownloaded/1024} KB")
                TextButton(onClick=vm::cancelResource) { Text("取消") }
            }
            if(state.resourceMessage.isNotBlank()) Text(state.resourceMessage,fontSize=14.sp,lineHeight=24.sp)
            OutlinedButton(onClick={picker.launch(arrayOf("application/zip","application/octet-stream","application/x-zip-compressed"))},enabled=!state.resourceBusy,modifier=Modifier.fillMaxWidth()) { Text("导入本地资源 ZIP") }
            Text("资源说明",fontWeight=FontWeight.Medium)
            Text("古籍原文为公有领域作品，逐条标注出处。现代释义、译文、解析和题目为项目原创（CC0-1.0）。常用篇章以节选学习为主，未经专业学术审校。双语仅提供两组示例。",fontSize=13.sp,lineHeight=23.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("资源库与应用分别更新。安装资源不删除历史、收藏及错题；下载或校验失败时继续使用原资源。",fontSize=13.sp,lineHeight=23.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
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

@Composable private fun AiPage(back:()->Unit) {
    Column { PageHeader("AI 设置",back)
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Icon(Icons.Outlined.AutoAwesome,null,Modifier.size(40.dp),tint=MaterialTheme.colorScheme.primary)
            Text("先把本地学习做好。",fontSize=27.sp,lineHeight=38.sp,fontFamily=FontFamily.Serif)
            Text("当前版本尚未接入云端 AI，也不收集或保存 API Key。已收录的古文、字词、例句和练习均可直接离线使用。",fontSize=16.sp,lineHeight=28.sp)
            Text("后续支持自选厂商、模型与兼容 API，并用 Android Keystore 加密保存密钥。",fontSize=14.sp,lineHeight=24.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
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
