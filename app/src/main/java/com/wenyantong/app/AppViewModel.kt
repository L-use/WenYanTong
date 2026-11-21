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
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.wenyantong.app.feature.storage.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

data class AppState(
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

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = LocalRepository(application)
    private val updates = UpdateRepository(application)
    private val resources=com.wenyantong.app.feature.resources.ResourceStore(application)
    private var resourceJob: Job?=null
    private val mutable = MutableStateFlow(AppState(mode=repo.mode(),theme=repo.theme(),fontSize=repo.fontSize(),automaticUpdates=updates.autoCheckEnabled()))
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
    fun mode(mode: Mode) { repo.setMode(mode);mutable.update { it.copy(mode=mode) };maybeCheckUpdate() }
    fun reading(theme: String, font: Float) { repo.setReading(theme,font);mutable.update { it.copy(theme=theme,fontSize=font) } }
    fun query(text: String = state.value.input, direction: Direction = state.value.direction) {
        if(text.isBlank() || !state.value.ready) return
        queryJob?.cancel()
        mutable.update { it.copy(input=text,direction=direction,busy=true,result=null,error=null) }
        queryJob = viewModelScope.launch {
            try {
                val result = repo.query(text,direction)
                val history = repo.history()
                mutable.update { it.copy(result=result,history=history,busy=false) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (e: Exception) { mutable.update { it.copy(error=e.message ?: "查询失败",busy=false) } }
        }
    }
    fun favorite(item: HistoryItem) = viewModelScope.launch { repo.favorite(item.id,!item.favorite);refreshHistory() }
    fun delete(item: HistoryItem) = viewModelScope.launch { repo.deleteHistory(item.id);refreshHistory() }
    private suspend fun refreshHistory() { val history = repo.history();mutable.update { it.copy(history=history) } }
    fun startPractice(entryIds: Set<String>, count: Int, wrongOnly: Boolean = false, onReady: () -> Unit) = viewModelScope.launch {
        val questions = repo.questions(entryIds,wrongOnly).shuffled().take(count)
        sessionId = UUID.randomUUID().toString()
        mutable.update { it.copy(questions=questions,answers=emptyList(),questionIndex=0,submitted=false,selected=null,savingAnswer=false,practiceFinished=false,error=null) }
        onReady()
    }
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
                mutable.update { it.copy(history=history,wrongCount=wrong,mode=repo.mode(),theme=repo.theme(),fontSize=repo.fontSize(),automaticUpdates=updates.autoCheckEnabled(),storage=StorageUiState(message="已合并导入 ${summary.history} 条历史、${summary.answers} 条答题记录和 ${summary.wrong} 道错题；重复记录已去重。")) }
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
