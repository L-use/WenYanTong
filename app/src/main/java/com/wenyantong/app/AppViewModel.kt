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
    val resourceBusy: Boolean = false, val resourceMessage: String = "", val resourceDownloaded: Long = 0,
    val resourceOffer: com.wenyantong.app.feature.resources.ResourceOffer? = null,
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
    init { initialize() }
    fun initialize() {
        viewModelScope.launch {
            mutable.update { it.copy(error=null) }
            runCatching {
                repo.initialize()
                val entries = repo.entries();val passages = repo.passages();val history = repo.history();val wrong = repo.wrongCount()
                mutable.update { it.copy(ready=true,entries=entries,passages=passages,history=history,wrongCount=wrong,resourceVersion=repo.manifest.getString("version"),resourceRevision=repo.manifest.optInt("revision",0),resourceQuestions=repo.manifest.getInt("questionCount")) }
                maybeCheckUpdate()
            }.onFailure { mutable.update { s -> s.copy(error=it.message ?: "本地资源加载失败") } }
        }
    }
    fun input(text: String) { mutable.update { it.copy(input=text.take(2000)) } }
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
                val offer=resources.check()
                mutable.update { it.copy(resourceBusy=false,resourceOffer=offer.takeIf { o -> o.revision>it.resourceRevision },resourceMessage=if(offer.revision>it.resourceRevision)"发现新资源库" else "已是最新资源库") }
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(resourceBusy=false,resourceMessage=updateError(e)) } }
        }
    }
    fun downloadResource() {
        val offer=state.value.resourceOffer ?: return
        if(resourceJob?.isActive==true)return
        mutable.update { it.copy(resourceBusy=true,resourceDownloaded=0,resourceMessage="正在下载资源包…") }
        resourceJob=viewModelScope.launch {
            try {
                val file=resources.download(offer) { bytes -> mutable.update { it.copy(resourceDownloaded=bytes) } }
                try {
                    val prepared=withContext(Dispatchers.IO) { file.inputStream().use { resources.prepare(it,state.value.resourceRevision,offer) } }
                    applyResource(prepared)
                } finally { file.delete() }
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(resourceBusy=false,resourceMessage=updateError(e)) } }
        }
    }
    fun importResource(uri: Uri) {
        if(resourceJob?.isActive==true)return
        mutable.update { it.copy(resourceBusy=true,resourceMessage="正在校验本地资源包…") }
        resourceJob=viewModelScope.launch {
            try {
                val prepared=withContext(Dispatchers.IO) { getApplication<Application>().contentResolver.openInputStream(uri)?.use { resources.prepare(it,state.value.resourceRevision) } ?: error("无法读取资源包。") }
                applyResource(prepared)
            } catch(e: kotlinx.coroutines.CancellationException) { throw e }
            catch(e: Exception) { mutable.update { it.copy(resourceBusy=false,resourceMessage=e.message ?: "资源导入失败") } }
        }
    }
    private suspend fun applyResource(prepared: com.wenyantong.app.feature.resources.PreparedResource) = withContext(kotlinx.coroutines.NonCancellable) {
        queryJob?.cancel();queryJob?.join()
        repo.installResource(prepared)
        val entries=repo.entries();val passages=repo.passages()
        mutable.update { it.copy(entries=entries,passages=passages,result=null,questions=emptyList(),answers=emptyList(),busy=false,
            resourceVersion=repo.manifest.getString("version"),resourceRevision=repo.manifest.getInt("revision"),resourceQuestions=repo.manifest.getInt("questionCount"),
            resourceBusy=false,resourceOffer=null,resourceMessage="资源库安装成功，词典、古文和练习已更新。") }
    }
    fun cancelResource() { resourceJob?.cancel();mutable.update { it.copy(resourceBusy=false,resourceMessage="已取消，可以重试。") } }
    private fun updateError(error: Exception): String = when(error) {
        is java.net.SocketTimeoutException -> "连接 GitHub 超时，请稍后重试。"
        is java.io.IOException -> "无法连接 GitHub，请检查网络后重试。"
        else -> error.message ?: "更新检查失败，请稍后重试。"
    }
    override fun onCleared() { repo.close() }
}
