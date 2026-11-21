package com.wenyantong.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wenyantong.app.core.model.*
import com.wenyantong.app.core.storage.LocalRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import com.wenyantong.app.feature.update.*
import com.wenyantong.app.feature.ai.*
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.wenyantong.app.feature.storage.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class AppState(
    val aiProfiles: List<AiProfile> = emptyList(),val activeAiProfile: String="",val aiConnected: Boolean=false,val skills: List<AiSkill> = emptyList(),val wrongQuestions: List<Question> = emptyList(),val wrongNotice: String="",
    val aiSettings: AiSettings=AiSettings(), val aiNotice: String="", val aiBusy: Boolean=false, val practiceBusy: Boolean=false, val practiceNotice: String="",
    val ready: Boolean = false, val busy: Boolean = false, val error: String? = null,
    val input: String = "", val direction: Direction = Direction.CLASSICAL_TO_ZH,
    val mode: Mode = Mode.OFFLINE, val theme: String = "system", val fontSize: Float = 18f,
    val entries: List<Entry> = emptyList(), val passages: List<Passage> = emptyList(),
    val result: QueryResult? = null, val history: List<HistoryItem> = emptyList(),
    val questions: List<Question> = emptyList(), val answers: List<Answer> = emptyList(),
    val questionIndex: Int = 0, val submitted: Boolean = false, val selected: Int? = null,
    val savingAnswer: Boolean = false, val practiceFinished: Boolean = false, val wrongCount: Int = 0,
    val update: UpdateUiState = UpdateUiState(), val automaticUpdates: Boolean = true,
    val resourceVersion: String = "", val resourceRevision: Int = 0, val resourceQuestions: Int = 0,
    val resourceBusy: Boolean = false, val resourceInstalling: Boolean=false, val resourceMessage: String = "", val resourceDownloaded: Long = 0,
    val resourceOffer: com.wenyantong.app.feature.resources.ResourceOffer? = null,
    val resourceCatalog: List<com.wenyantong.app.feature.resources.ResourceOffer> = emptyList(),
    val installedPacks: List<com.wenyantong.app.feature.resources.InstalledPack> = emptyList(),
    val articlePack: String="", val articleQuery: String="", val articles: List<LibraryArticle> = emptyList(),
    val articleText: ArticleText?=null, val articleBusy: Boolean=false, val articleMore: Boolean=true, val dictionaryFilter: String="",
    val storage: StorageUiState = StorageUiState()
)

class AppViewModel @JvmOverloads constructor(application: Application, private val ai: AiService=AiService(), private val modelClient: AiModelClient=AiModelClient()) : AndroidViewModel(application) {
    private val aiStore=AiSettingsStore(application)
    private val skillStore=AiSkillStore(application)
    private var practiceJob: Job?=null
    private var aiSettingsJob: Job?=null
    private val repo = LocalRepository(application)
    private val updates = UpdateRepository(application)
    private val resources=com.wenyantong.app.feature.resources.ResourceStore(application)
    private var resourceJob: Job?=null
    private val mutable = MutableStateFlow(AppState(aiSettings=aiStore.settings(),aiProfiles=aiStore.profiles(),activeAiProfile=aiStore.activeProfile(),aiConnected=aiStore.connected(),skills=skillStore.all(),mode=repo.mode(),theme=repo.theme(),fontSize=repo.fontSize(),automaticUpdates=updates.autoCheckEnabled()))
    val state = mutable.asStateFlow()
    private var queryJob: Job? = null
    private var sessionId = ""
    private var updateJob: Job?=null
    private var updateOperation=0
    private var preparedBackup: UserBackup?=null
    private var articleJob: Job?=null
    init { initialize() }
    fun initialize() {
        viewModelScope.launch {
            mutable.update { it.copy(error=null) }
            runCatching {
                repo.initialize()
                val entries = repo.entries();val passages = repo.passages();val history = repo.history();val wrong = repo.wrongCount()
                mutable.update { it.copy(installedPacks=repo.installedPacks(),ready=true,entries=entries,passages=passages,history=history,wrongCount=wrong,resourceVersion=repo.manifest.getString("version"),resourceRevision=repo.manifest.optInt("revision",0),resourceQuestions=repo.manifest.getInt("questionCount")) }
                maybeCheckUpdate()
            }.onFailure { mutable.update { s -> s.copy(error=it.message ?: "本地资源加载失败") } }
        }
    }
    fun input(text: String) { mutable.update { it.copy(input=text.take(2000)) } }
    fun dictionaryFilter(title: String) { mutable.update { it.copy(dictionaryFilter=title) } }
    fun direction(direction: Direction) { mutable.update { it.copy(direction=direction) } }
    fun mode(mode: Mode) { if(mode==Mode.OFFLINE) { queryJob?.cancel();practiceJob?.cancel();mutable.update { it.copy(busy=false,practiceBusy=false) } };repo.setMode(mode);mutable.update { it.copy(mode=mode) };maybeCheckUpdate() }
    fun reading(theme: String, font: Float) { repo.setReading(theme,font);mutable.update { it.copy(theme=theme,fontSize=font) } }
    fun query(text: String = state.value.input, direction: Direction = state.value.direction) {
        if(text.isBlank() || !state.value.ready) return
        queryJob?.cancel()
        mutable.update { it.copy(input=text,direction=direction,busy=true,result=null,error=null,aiNotice="") }
        queryJob = viewModelScope.launch {
            try {
                val family=TranslationMode.of(direction)
                val guessed=family.direction(text)
                val first=repo.query(text,guessed,false)
                val opposite=when(guessed) { Direction.CLASSICAL_TO_ZH->Direction.ZH_TO_CLASSICAL;Direction.ZH_TO_CLASSICAL->Direction.CLASSICAL_TO_ZH;Direction.ZH_TO_EN->Direction.EN_TO_ZH;Direction.EN_TO_ZH->Direction.ZH_TO_EN }
                val second=if(family==TranslationMode.CLASSICAL && first.passage==null)repo.query(text,opposite,false) else null
                val local=repo.query(text,if(second?.passage!=null)opposite else guessed)
                val history = repo.history()
                mutable.update { it.copy(result=local,direction=local.direction,history=history) }
                val mode=state.value.mode;val settings=aiStore.settings()
                val needsAi=mode==Mode.AI || (mode==Mode.AUTO && local.passage==null && !local.exactWordHit)
                if(needsAi && settings.hasKey) {
                    mutable.update { it.copy(aiNotice="正在结合本地词义请求 AI…") }
                    try {
                        val credentials=withContext(Dispatchers.IO) { aiStore.credentials() }
                        val generated=ai.translate(credentials.first,credentials.second,local,skillStore.instructions(SkillScope.TRANSLATE))
                        mutable.update { it.copy(result=local.copy(direction=generated.direction,passage=generated,alternatives=listOf(generated)+local.alternatives),direction=generated.direction,busy=false,aiNotice="AI 生成参考；请结合语境与出处核对。") }
                    } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch(e: Exception) { mutable.update { it.copy(result=local,busy=false,aiNotice="AI 补充未完成："+aiError(e)) } }
                } else mutable.update { it.copy(busy=false,aiNotice=if(needsAi && !settings.hasKey)"尚未配置 AI 渠道，当前展示本地查询结果。" else "") }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (e: Exception) { mutable.update { it.copy(error=e.message ?: "查询失败",busy=false) } }
        }
    }
    fun favorite(item: HistoryItem) = viewModelScope.launch { repo.favorite(item.id,!item.favorite);refreshHistory() }
    fun delete(item: HistoryItem) = viewModelScope.launch { repo.deleteHistory(item.id);refreshHistory() }
    private suspend fun refreshHistory() { val history = repo.history();mutable.update { it.copy(history=history) } }
    fun startPractice(entryIds: Set<String>, count: Int, wrongOnly: Boolean = false, useAi: Boolean=false,kinds: Set<QuestionKind> = setOf(QuestionKind.SINGLE), onReady: () -> Unit) {
        if(practiceJob?.isActive==true)return
        mutable.update { it.copy(practiceBusy=true,practiceNotice="",error=null) }
        practiceJob=viewModelScope.launch {
            try {
                require(kinds.isNotEmpty()) { "请至少选择一种题型。" }
                val questions=if(useAi && !wrongOnly) {
                    check(state.value.mode!=Mode.OFFLINE) { "离线模式不调用 AI，请先切换自动或 AI 模式。" }
                    val settings=aiStore.settings();check(settings.hasKey) { "请先配置 AI 渠道与 API Key。" }
                    val result=state.value.result ?: state.value.input.takeIf { it.isNotBlank() }?.let { QueryResult(it,state.value.direction,null,emptyList()) }
                    val previews=when {
                        entryIds.isNotEmpty() -> state.value.entries.filter { e -> e.linkedIds.any { it in entryIds } }
                        result?.entries?.isNotEmpty()==true -> result.entries
                        result!=null -> emptyList()
                        else -> state.value.entries.take(6)
                    }
                    val context=previews.take(6).mapNotNull { repo.entryDetails(it.headword,3) }.filter { it.senses.isNotEmpty() }
                    check(context.isNotEmpty() || !result?.query.isNullOrBlank()) { "请先输入查询内容或选择词条。" }
                    mutable.update { it.copy(practiceNotice="AI 正在根据当前结果与本地词义出题…") }
                    val credentials=withContext(Dispatchers.IO) { aiStore.credentials() }
                    ai.generate(credentials.first,credentials.second,result,context,count,kinds,skillStore.instructions(SkillScope.PRACTICE)).also { repo.saveGenerated(it,result?.query.orEmpty()) }
                } else {
                    val scope=if(entryIds.isNotEmpty())entryIds else state.value.result?.entries?.flatMap { it.linkedIds }?.toSet().orEmpty()
                    val local=if(!wrongOnly && state.value.result!=null && scope.isEmpty())emptyList() else repo.questions(if(wrongOnly)emptySet() else scope,wrongOnly)
                        .filter { wrongOnly || it.kind in kinds || (it.kind==QuestionKind.SINGLE && it.options.size==4) }.shuffled().take(count)
                    check(local.isNotEmpty()) { "本次查询暂无离线题目；切换自动或 AI 模式并配置 API 后，可基于任意查询生成。" }
                    if(wrongOnly)local else local.mapIndexed { index,q ->
                        if(q.kind!=QuestionKind.SINGLE)return@mapIndexed q
                        val kind=kinds.toList()[index%kinds.size]
                        when(kind) {
                            QuestionKind.SINGLE -> q
                            QuestionKind.TRUE_FALSE -> { val correct=index%2==0;val choice=if(correct)q.answer else (q.answer+1)%q.options.size
                                q.copy(id="ai-offline-${UUID.randomUUID()}",stem="判断所给答案是否正确：\n${q.stem}\n所给答案：${q.options[choice]}",options=listOf("正确","错误"),answer=if(correct)0 else 1,explanation=q.explanation+"\n原题正确答案：${q.options[q.answer]}",kind=kind) }
                            QuestionKind.FILL_BLANK -> q.copy(id="ai-offline-${UUID.randomUUID()}",stem="选择正确内容填入 ___：\n${q.stem}\n答案：___",kind=kind)
                        }
                    }.also { repo.saveGenerated(it.filter { q -> q.id.startsWith("ai-offline-") },state.value.result?.query.orEmpty()) }
                }
                sessionId=UUID.randomUUID().toString()
                mutable.update { it.copy(questions=questions,answers=emptyList(),questionIndex=0,submitted=false,selected=null,savingAnswer=false,practiceFinished=false,practiceBusy=false,practiceNotice=if(useAi)"AI 题目已生成并保存，可结合原词条核对。" else "",error=null) }
                onReady()
            } catch(e: kotlinx.coroutines.CancellationException) { mutable.update { it.copy(practiceBusy=false) };throw e }
            catch(e: Exception) { mutable.update { it.copy(practiceBusy=false,practiceNotice=aiError(e)) } }
        }
    }
    fun cancelPractice() { practiceJob?.cancel();mutable.update { it.copy(practiceBusy=false,practiceNotice="已取消出题，可重新开始。") } }
    private fun aiError(error: Exception): String = when(error) {
        is org.json.JSONException -> "AI 返回格式不符合要求，请重试或更换模型。"
        is java.net.SocketTimeoutException -> "AI 请求超时，请稍后重试。"
        is java.io.IOException -> "无法连接 AI 服务，请检查网络和地址。"
        else -> error.message ?: "AI 请求失败，请检查渠道设置。"
    }
    suspend fun discoverAiModels(settings: AiSettings,key: String): List<AiModelOption> {
        val resolved=withContext(Dispatchers.IO) { aiStore.discoveryKey(settings,key) }
        return modelClient.list(settings,resolved)
    }
    private fun refreshAi(notice: String="") { val profiles=aiStore.profiles();mutable.update { it.copy(aiSettings=aiStore.settings(),aiProfiles=profiles,activeAiProfile=aiStore.activeProfile(),aiConnected=aiStore.connected(),skills=skillStore.all(),aiNotice=notice) } }
    fun newApi() { if(state.value.aiBusy)return;runCatching { aiStore.newProfile();refreshAi("请填写新 API，保存后加入管理列表。") }.onFailure { mutable.update { s -> s.copy(aiNotice=aiError(it as Exception)) } } }
    fun useApi(id: String) { if(state.value.aiBusy)return;queryJob?.cancel();practiceJob?.cancel();aiStore.activateProfile(id);refreshAi("已切换当前 API。");mutable.update { it.copy(busy=false,practiceBusy=false) } }
    fun deleteApi(id: String) { if(state.value.aiBusy)return;aiStore.deleteProfile(id);refreshAi("API 已删除。");if(!state.value.aiSettings.hasKey)mode(Mode.OFFLINE) }
    fun renameApi(name: String) { if(name.isNotBlank())runCatching { aiStore.rename(name) };refreshAi() }
    fun saveSkill(skill: AiSkill): Boolean = runCatching { skillStore.save(skill);refreshAi("技能已保存。");true }.getOrElse { error -> mutable.update { s -> s.copy(aiNotice=error.message ?: "技能保存失败。") };false }
    fun deleteSkill(id: String) { skillStore.delete(id);refreshAi("技能已删除。") }
    fun loadWrong()=viewModelScope.launch { val questions=repo.questions(wrongOnly=true);mutable.update { it.copy(wrongQuestions=questions,wrongNotice="") } }
    fun editWrong(question: Question)=viewModelScope.launch {
        try { repo.editQuestion(question);val questions=repo.questions(wrongOnly=true);mutable.update { it.copy(wrongQuestions=questions,wrongNotice="错题修改已保存。") } }
        catch(e: Exception) { mutable.update { it.copy(wrongNotice=e.message ?: "保存失败。") } }
    }
    fun saveAi(settings: AiSettings,key: String,enableAuto: Boolean) {
        if(aiSettingsJob?.isActive==true)return
        mutable.update { it.copy(aiBusy=true,aiNotice="正在加密保存…") }
        aiSettingsJob=viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { aiStore.save(settings,key) }
                mutable.update { it.copy(aiSettings=aiStore.settings(),aiBusy=false,aiNotice="AI 配置已加密保存。") }
                refreshAi("AI 配置已加密保存。")
                if(enableAuto)mode(Mode.AUTO)
            } catch(e: Exception) { mutable.update { it.copy(aiBusy=false,aiNotice=aiError(e)) } }
        }
    }
    fun testAi(settings: AiSettings,key: String) {
        if(aiSettingsJob?.isActive==true)return
        mutable.update { it.copy(aiBusy=true,aiNotice="正在保存并测试连接…") }
        aiSettingsJob=viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { aiStore.save(settings,key) }
                val credentials=withContext(Dispatchers.IO) { aiStore.credentials() }
                val message=ai.test(credentials.first,credentials.second)
                aiStore.markConnected(true)
                mutable.update { it.copy(aiSettings=credentials.first,aiBusy=false,aiNotice=message) }
                refreshAi(message)
            } catch(e: Exception) { aiStore.markConnected(false);mutable.update { it.copy(aiSettings=aiStore.settings(),aiBusy=false,aiNotice=aiError(e)) };refreshAi(aiError(e)) }
        }
    }
    fun clearAi() { aiSettingsJob?.cancel();queryJob?.cancel();practiceJob?.cancel();aiStore.clear();mode(Mode.OFFLINE);mutable.update { it.copy(aiSettings=AiSettings(),aiBusy=false) };refreshAi("AI 配置和密钥已清除。") }
    fun select(index: Int) { if(!state.value.submitted && !state.value.savingAnswer) mutable.update { it.copy(selected=index) } }
    fun submit() {
        val s = state.value
        val selected = s.selected ?: return
        val question = s.questions.getOrNull(s.questionIndex) ?: return
        if(s.submitted || s.savingAnswer) return
        mutable.update { it.copy(savingAnswer=true) }
        viewModelScope.launch {
            try {
                repo.recordAnswer(sessionId,question,selected)
                val wrong = repo.wrongCount()
                mutable.update { it.copy(submitted=true,savingAnswer=false,wrongCount=wrong,answers=it.answers+Answer(question.id,selected,TextRules.isCorrect(question,selected))) }
            } catch(e: Exception) { mutable.update { it.copy(savingAnswer=false,error="答题保存失败，请重试。") } }
        }
    }
    fun next() {
        if(!state.value.submitted) return
        mutable.update { if(it.questionIndex+1 >= it.questions.size) it.copy(practiceFinished=true) else it.copy(questionIndex=it.questionIndex+1,submitted=false,selected=null) }
    }
    fun exportUserData(uri: Uri) {
        if(state.value.storage.busy) return
        mutable.update { it.copy(storage=StorageUiState(busy=true)) }
        viewModelScope.launch {
            try {
                val bytes=repo.exportUserData()
                withContext(Dispatchers.IO) {
                    val output=getApplication<Application>().contentResolver.openOutputStream(uri,"wt") ?: error("无法打开导出位置。")
                    output.use { it.write(bytes) }
                }
                mutable.update { it.copy(storage=StorageUiState(message="已导出学习数据备份，可用于本机恢复或迁移。")) }
            } catch(e: Exception) { mutable.update { it.copy(storage=StorageUiState(message="导出失败，请重新选择文件位置后重试。")) } }
        }
    }
    fun prepareImport(uri: Uri) {
        if(state.value.storage.busy) return
        preparedBackup=null
        mutable.update { it.copy(storage=StorageUiState(busy=true)) }
        viewModelScope.launch {
            try {
                val backup=withContext(Dispatchers.IO) {
                    val input=getApplication<Application>().contentResolver.openInputStream(uri) ?: error("无法读取备份文件。")
                    val bytes=input.use { stream ->
                        val output=java.io.ByteArrayOutputStream()
                        val buffer=ByteArray(8192)
                        while(true) {
                            val n=stream.read(buffer)
                            if(n<0) break
                            require(output.size()+n<=UserBackupCodec.MAX_BYTES) { "备份超过 20 MB，无法导入。" }
                            output.write(buffer,0,n)
                        }
                        output.toByteArray()
                    }
                    UserBackupCodec.decode(bytes)
                }
                preparedBackup=backup
                mutable.update { it.copy(storage=StorageUiState(pending=backup.summary())) }
            } catch(e: Exception) { mutable.update { it.copy(storage=StorageUiState(message="无法导入：请选择格式完整的文言通 JSON 备份（最大 20 MB）。")) } }
        }
    }
    fun cancelImport() { preparedBackup=null;mutable.update { it.copy(storage=StorageUiState()) } }
    fun importPreparedData() {
        if(state.value.storage.busy) return
        val backup=preparedBackup ?: return
        mutable.update { it.copy(storage=StorageUiState(busy=true)) }
        viewModelScope.launch {
            try {
                val summary=repo.importUserData(backup)
                val history=repo.history();val wrong=repo.wrongCount()
                preparedBackup=null
                mutable.update { it.copy(history=history,wrongCount=wrong,skills=skillStore.all(),mode=repo.mode(),theme=repo.theme(),fontSize=repo.fontSize(),automaticUpdates=updates.autoCheckEnabled(),storage=StorageUiState(message="已合并导入 ${summary.history} 条历史、${summary.answers} 条答题记录和 ${summary.wrong} 道错题；重复记录已去重。")) }
            } catch(e: Exception) { mutable.update { it.copy(storage=StorageUiState(message="导入未完成，现有记录已保留，请检查备份后重试。")) } }
        }
    }
    fun automaticUpdates(enabled: Boolean) {
        updates.autoCheckEnabled(enabled)
        mutable.update { it.copy(automaticUpdates=enabled) }
        if(enabled) maybeCheckUpdate()
    }
    private fun maybeCheckUpdate() {
        val connectivity=getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val connected=connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)==true
        if(UpdatePolicy.shouldAutoCheck(state.value.mode,state.value.automaticUpdates,updates.lastCheck(),System.currentTimeMillis(),connected)) checkUpdate()
    }
    fun checkUpdate(offlineConsent: Boolean=false) {
        if(updateJob?.isActive==true) return
        val operation=++updateOperation
        val mode=state.value.mode
        mutable.update { it.copy(update=UpdateUiState(phase=UpdatePhase.CHECKING)) }
        updateJob=viewModelScope.launch {
            try {
                val info=updates.check(mode,offlineConsent)
                if(operation==updateOperation) mutable.update { it.copy(update=UpdateUiState(if(info==null)UpdatePhase.LATEST else UpdatePhase.AVAILABLE,info)) }
            } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch(error: Exception) {
                if(operation==updateOperation) mutable.update { it.copy(update=UpdateUiState(UpdatePhase.ERROR,message=updateError(error))) }
            }
        }
    }
    fun downloadUpdate() {
        if(updateJob?.isActive==true) return
        val info=state.value.update.info ?: return
        val operation=++updateOperation
        mutable.update { it.copy(update=UpdateUiState(UpdatePhase.DOWNLOADING,info,total=info.bytes)) }
        updateJob=viewModelScope.launch {
            try {
                val file=updates.download(info) { downloaded,total ->
                    if(operation==updateOperation) mutable.update { it.copy(update=it.update.copy(downloaded=downloaded,total=total)) }
                }
                if(operation==updateOperation) mutable.update { it.copy(update=UpdateUiState(UpdatePhase.READY,info,info.bytes,info.bytes,file.path)) }
            } catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch(error: Exception) {
                if(operation==updateOperation) mutable.update { it.copy(update=UpdateUiState(UpdatePhase.ERROR,info,message=updateError(error))) }
            }
        }
    }
    fun cancelUpdate() {
        updateOperation++
        updateJob?.cancel();updateJob=null
        mutable.update { it.copy(update=UpdateUiState(UpdatePhase.AVAILABLE,it.update.info,message="下载已取消，可以重新下载。")) }
    }
    fun checkResources() {
        if(resourceJob?.isActive==true)return
        mutable.update { it.copy(resourceBusy=true,resourceMessage="正在检查资源更新…",resourceOffer=null) }
        resourceJob=viewModelScope.launch {
            try {
                val offers=resources.catalog()
                mutable.update { it.copy(resourceBusy=false,resourceCatalog=offers,resourceMessage="可选资源包 ${offers.size} 个；仅下载您需要的内容。") }
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(resourceBusy=false,resourceInstalling=false,resourceMessage=updateError(e)) } }
        }
    }
    fun downloadResource() { state.value.resourceOffer?.let { downloadResource(it) } }
    fun downloadResource(offer: com.wenyantong.app.feature.resources.ResourceOffer) {
        if(resourceJob?.isActive==true)return
        mutable.update { it.copy(resourceOffer=offer,resourceBusy=true,resourceDownloaded=0,resourceMessage="正在下载 ${offer.title}…") }
        resourceJob=viewModelScope.launch {
            try {
                val file=resources.download(offer) { bytes -> mutable.update { it.copy(resourceDownloaded=bytes) } }
                try {
                    val prepared=withContext(Dispatchers.IO) { file.inputStream().use { resources.prepare(it,state.value.installedPacks.firstOrNull { p -> p.id==offer.id }?.revision ?: 0,offer) } }
                    applyResource(prepared)
                } finally { file.delete() }
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(resourceBusy=false,resourceInstalling=false,resourceMessage=updateError(e)) } }
        }
    }
    fun importResource(uri: Uri) {
        if(resourceJob?.isActive==true)return
        mutable.update { it.copy(resourceBusy=true,resourceMessage="正在校验本地资源包…") }
        resourceJob=viewModelScope.launch {
            try {
                val prepared=withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openInputStream(uri)?.use { resources.prepare(it,-1) } ?: error("无法读取资源包。") }
                applyResource(prepared)
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(resourceBusy=false,resourceInstalling=false,resourceMessage=e.message ?: "资源导入失败") } }
        }
    }
    private suspend fun applyResource(prepared: com.wenyantong.app.feature.resources.PreparedResource) = withContext(kotlinx.coroutines.NonCancellable) {
        mutable.update { it.copy(resourceInstalling=true,resourceMessage="正在安装与刷新资源…") }
        queryJob?.cancel();queryJob?.join()
        articleJob?.cancel();articleJob?.join()
        repo.installResource(prepared)
        val entries=repo.entries();val passages=repo.passages()
        mutable.update { it.copy(installedPacks=repo.installedPacks(),entries=entries,passages=passages,result=null,questions=emptyList(),answers=emptyList(),busy=false,
            resourceVersion=repo.manifest.getString("version"),resourceRevision=repo.manifest.optInt("revision",0),resourceQuestions=repo.manifest.getInt("questionCount"),
            resourceBusy=false,resourceInstalling=false,resourceOffer=null,resourceMessage="资源包安装成功，已加入全局搜索。",articles=emptyList(),articleText=null) }
    }
    fun removeResource(id: String) {
        if(resourceJob?.isActive==true)return
        mutable.update { it.copy(resourceBusy=true,resourceMessage="正在移除资源包…") }
        resourceJob=viewModelScope.launch {
            try {
                queryJob?.cancel();queryJob?.join();articleJob?.cancel();articleJob?.join()
                withContext(kotlinx.coroutines.NonCancellable) {
                    repo.removeResource(id)
                    val entries=repo.entries();val passages=repo.passages()
                    mutable.update { it.copy(installedPacks=repo.installedPacks(),entries=entries,passages=passages,result=null,articles=emptyList(),articleText=null,resourceBusy=false,resourceMessage="已移除资源包；历史、收藏和其他资源保持保留。") }
                }
            } catch(e: Exception) { mutable.update { it.copy(resourceBusy=false,resourceInstalling=false,resourceMessage=e.message ?: "移除失败") } }
        }
    }
    fun entryDetails(entry: Entry, limit: Int=30, ready:(Entry)->Unit) = viewModelScope.launch {
        try { ready(repo.entryDetails(entry.headword,limit) ?: entry) } catch(e: Exception) { mutable.update { it.copy(error="词条加载失败，请重试。") } }
    }
    fun loadArticles(packId: String="", query: String="", more: Boolean=false) {
        articleJob?.cancel()
        val offset=if(more)state.value.articles.count { it.packId==packId || packId.isBlank() } else 0
        mutable.update { it.copy(articlePack=packId,articleQuery=query,articleBusy=true,articleText=null,articles=if(more)it.articles else emptyList()) }
        articleJob=viewModelScope.launch {
            try { val rows=repo.articles(packId,query,offset)
                mutable.update { it.copy(articles=(it.articles+rows).distinctBy { a -> a.id },articleBusy=false,articleMore=rows.isNotEmpty()) }
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(articleBusy=false,error=e.message ?: "古文加载失败") } }
        }
    }
    fun openArticle(item: LibraryArticle) {
        articleJob?.cancel();mutable.update { it.copy(articleBusy=true,articleText=null) }
        articleJob=viewModelScope.launch {
            try { val value=repo.article(item);mutable.update { it.copy(articleText=value,articleBusy=false) } }
            catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(articleBusy=false,error="文章加载失败，请重试。") } }
        }
    }
    fun cancelResource() { if(state.value.resourceInstalling)return;resourceJob?.cancel();mutable.update { it.copy(resourceBusy=false,resourceMessage="已取消，可以重试。") } }
    private fun updateError(error: Exception): String = when(error) {
        is java.net.SocketTimeoutException -> "连接 GitHub 超时，请稍后重试。"
        is java.io.IOException -> "无法连接 GitHub，请检查网络后重试。"
        else -> error.message ?: "更新检查失败，请稍后重试。"
    }
    override fun onCleared() { repo.close() }
}
