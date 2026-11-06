package com.example.yuewen.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.yuewen.BuildConfig
import com.example.yuewen.data.update.ApkInstaller
import com.example.yuewen.data.update.UpdateChecker
import com.example.yuewen.data.update.UpdateInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 「检查更新」对话框的状态机（v2.14）。
 *
 * 一次更新要走完这几步，每一步用户都得看得见：
 *
 * ```
 * Idle ──检查──▶ Checking ─┬─▶ Latest（已是最新）
 *                          ├─▶ Available ──下载──▶ Downloading ──▶ Ready ──▶ 拉起系统安装器
 *                          └─▶ Failed（可重试）
 * ```
 *
 * 之所以做成一个封闭的 `sealed interface` 而不是「一堆 Boolean + 一个 Int 进度」：
 * 后者允许出现「既在检查又在下载」「进度 80% 但没文件」这类非法组合，
 * 而 UI 必须为这些不该存在的组合写 if，最后总有一条分支没测到。
 */
sealed interface UpdateUiState {
    /** 还没查过。 */
    data object Idle : UpdateUiState

    /** 正在问 GitHub。 */
    data object Checking : UpdateUiState

    /** 查到了，但已经是最新。 */
    data class Latest(val current: String) : UpdateUiState

    /** 有新版本，等用户决定要不要下。 */
    data class Available(val info: UpdateInfo) : UpdateUiState

    /** 正在下载。[percent] 为 null 表示服务端没给总大小，进度不可知。 */
    data class Downloading(val info: UpdateInfo, val percent: Int?) : UpdateUiState

    /** 下载完成，可以装了。 */
    data class Ready(val info: UpdateInfo, val file: File) : UpdateUiState

    /** 出错了。[canRetry] 决定 UI 上要不要给「重试」按钮。 */
    data class Failed(val message: String, val canRetry: Boolean = true) : UpdateUiState
}

/**
 * 检查更新 / 下载 / 调起安装。
 *
 * 拿的是 `AndroidViewModel`（要 Application）而不是普通 ViewModel：
 * 下载要写 `cacheDir`、安装要 `startActivity`，两件事都需要 Context。
 * 存 Application 是安全的（它和进程同寿，不会泄漏 Activity）。
 */
class UpdateViewModel(app: Application) : AndroidViewModel(app) {

    private val _visible = MutableStateFlow(false)

    /** 对话框要不要显示。单独一个流，和 [state] 分开 —— 关闭对话框不该把状态清掉。 */
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** 当前装的版本，显示在对话框顶部，让用户知道「从哪个版本升到哪个版本」。 */
    val currentVersion: String = BuildConfig.VERSION_NAME

    /** 正在跑的任务（检查或下载）。连点按钮时先掐掉上一个，否则两个下载会互相覆盖临时文件。 */
    private var job: Job? = null

    /**
     * 打开对话框。
     *
     * - 从没查过 → 自动查一次（用户点「检查更新」就是想要结果，不该再要一次点击）
     * - 上次失败了 → 重查
     * - 已经查到结果 / 下载完了 → 原样展示，别把人家的下载进度冲掉
     */
    fun open() {
        _visible.value = true
        when (_state.value) {
            is UpdateUiState.Idle, is UpdateUiState.Failed -> check()
            else -> Unit
        }
    }

    /** 关掉对话框。**下载中**会把下载停掉（否则用户关了还在后台偷偷跑流量）。 */
    fun dismiss() {
        _visible.value = false
        val s = _state.value
        if (s is UpdateUiState.Checking || s is UpdateUiState.Downloading) {
            job?.cancel()
            // 回到「可以再来一次」的状态，而不是卡在 60% 的下载条上
            _state.value = if (s is UpdateUiState.Downloading) {
                UpdateUiState.Available(s.info)
            } else {
                UpdateUiState.Idle
            }
        }
    }

    /** 问 GitHub 有没有新版本。 */
    fun check() {
        job?.cancel()
        _state.value = UpdateUiState.Checking
        job = viewModelScope.launch {
            UpdateChecker.fetchLatest(currentVersion)
                .onSuccess { info ->
                    _state.value = if (info == null) {
                        UpdateUiState.Latest(currentVersion)
                    } else {
                        UpdateUiState.Available(info)
                    }
                }
                .onFailure { e ->
                    _state.value = UpdateUiState.Failed(
                        e.message ?: "检查更新失败（网络不通？）"
                    )
                }
        }
    }

    /**
     * 下载当前这个新版本，**下完直接拉起系统安装器**。
     *
     * 为什么要自动拉起：用户点「下载并安装」时就已经表达了「我要装」，
     * 下完再让他点一次「安装」是多余的一步。
     * 但如果系统还没给「安装未知应用」权限，就**不**自动拉起 ——
     * 否则用户会被扔进一个「已阻止安装」的界面，还不知道为什么。
     */
    fun download(info: UpdateInfo) {
        job?.cancel()
        _state.value = UpdateUiState.Downloading(info, 0)
        job = viewModelScope.launch {
            runCatching {
                UpdateChecker.downloadTo(getApplication(), info) { p ->
                    // 回调在 IO 线程，写 StateFlow 是线程安全的；
                    // 这里只在百分比真的变了才发（每 64KB 一次，太密集会让 UI 一直重组）
                    val cur = _state.value
                    if (cur is UpdateUiState.Downloading && cur.percent != p) {
                        _state.value = cur.copy(percent = p)
                    }
                }
            }.onSuccess { file ->
                _state.value = UpdateUiState.Ready(info, file)
                // 权限够就直接拉起系统安装器；不够就停在对话框的「去开启权限」那一步
                if (ApkInstaller.canInstall(getApplication())) {
                    ApkInstaller.install(getApplication(), file)
                }
            }.onFailure { e ->
                // 用户自己取消（关对话框）不算错误，别把界面变成红色报错
                if (e is kotlinx.coroutines.CancellationException) return@onFailure
                _state.value = UpdateUiState.Failed(
                    e.message ?: "下载失败",
                    canRetry = true
                )
            }
        }
    }

    /**
     * 手动再拉一次安装界面（下载完但用户中途退出了安装器、或刚授完权回来）。
     *
     * @return 没有 apk 文件时返回 false
     */
    fun installAgain(): Boolean {
        val s = _state.value as? UpdateUiState.Ready ?: return false
        if (!s.file.exists()) {
            // 文件被系统清掉了（cache 被回收）→ 让用户重新下
            _state.value = UpdateUiState.Available(s.info)
            return false
        }
        return ApkInstaller.install(getApplication(), s.file)
    }

    companion object {
        fun provide(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    UpdateViewModel(app) as T
            }
    }
}
