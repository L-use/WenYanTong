package com.wenyantong.app.feature.update

enum class UpdatePhase { IDLE, CHECKING, AVAILABLE, LATEST, DOWNLOADING, READY, ERROR }
data class UpdateUiState(
    val phase: UpdatePhase=UpdatePhase.IDLE,
    val info: AppUpdate?=null,
    val downloaded: Long=0,
    val total: Long=0,
    val apkPath: String?=null,
    val message: String?=null
)
