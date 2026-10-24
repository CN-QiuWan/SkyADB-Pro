package com.qwadb.app.ui.files

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwadb.app.AppServices
import com.qwadb.app.R
import com.qwadb.app.i18n.appString
import com.qwadb.app.model.AdbOperationResult
import com.qwadb.app.model.RemoteFileEntry
import com.qwadb.app.repository.AdbRepository
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceFilesUiState(
    val path: String = DefaultDevicePath,
    val entries: List<RemoteFileEntry> = emptyList(),
    val loading: Boolean = false,
    val selected: Set<String> = emptySet(),
    val pulling: Boolean = false,
    val status: String? = null,
    val error: String? = null,
)

const val DefaultDevicePath = "/sdcard"

/** 进入「从设备拉取」时指定初始目录的一次性意图（如存储概览按类别下钻）。 */
object DeviceFilesLaunchIntent {
    private var pendingPath: String? = null

    fun request(path: String) {
        pendingPath = path
    }

    fun consume(): String? {
        val path = pendingPath
        pendingPath = null
        return path
    }
}

/**
 * 「从设备拉取」：只浏览设备侧文件（单窗口，不含本地目录），
 * 选中文件后拉取到手机本地 Download 目录。
 */
class DeviceFilesViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(DeviceFilesUiState())
    val uiState: StateFlow<DeviceFilesUiState> = state.asStateFlow()

    init {
        load(DefaultDevicePath)
    }

    fun load(path: String) {
        state.value = state.value.copy(path = path, loading = true, selected = emptySet(), error = null)
        viewModelScope.launch {
            when (val result = adbRepository.listFiles(path)) {
                is AdbOperationResult.Success -> {
                    state.value = state.value.copy(
                        entries = result.data,
                        loading = false,
                    )
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        loading = false,
                        error = result.message,
                    )
                }
            }
        }
    }

    fun open(entry: RemoteFileEntry) {
        if (entry.isDirectory) {
            load(entry.path)
        } else {
            toggleSelect(entry.path)
        }
    }

    fun goUp() {
        val current = state.value.path.trimEnd('/')
        if (current.isEmpty() || current == "/sdcard" || current == "/") return
        val parent = current.substringBeforeLast('/', "/sdcard").ifBlank { "/sdcard" }
        load(parent)
    }

    fun toggleSelect(path: String) {
        val current = state.value.selected
        state.value = state.value.copy(
            selected = if (path in current) current - path else current + path,
            status = null,
        )
    }

    fun pullSelected() {
        val selectedPaths = state.value.selected.toList()
        if (selectedPaths.isEmpty()) {
            state.value = state.value.copy(error = appString(R.string.device_files_select_first))
            return
        }
        state.value = state.value.copy(pulling = true, status = null, error = null)
        viewModelScope.launch {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloads.exists()) downloads.mkdirs()
            var ok = 0
            var failed = 0
            for (remotePath in selectedPaths) {
                val name = remotePath.substringAfterLast('/').ifBlank { "file" }
                val target = uniqueTarget(downloads, name)
                when (adbRepository.pull(remotePath, target)) {
                    is AdbOperationResult.Success -> ok++
                    is AdbOperationResult.Failure -> failed++
                }
            }
            state.value = state.value.copy(
                pulling = false,
                selected = emptySet(),
                status = if (failed == 0) {
                    appString(R.string.device_files_pull_done, ok)
                } else {
                    appString(R.string.device_files_pull_partial, ok, failed)
                },
            )
        }
    }

    private fun uniqueTarget(dir: File, name: String): File {
        var target = File(dir, name)
        if (!target.exists()) return target
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var index = 1
        while (target.exists()) {
            val candidate = if (ext.isBlank()) "$base ($index)" else "$base ($index).$ext"
            target = File(dir, candidate)
            index++
        }
        return target
    }
}
