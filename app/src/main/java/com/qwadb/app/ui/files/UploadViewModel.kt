package com.qwadb.app.ui.files

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwadb.app.AppServices
import com.qwadb.app.R
import com.qwadb.app.files.LocalFileManager
import com.qwadb.app.i18n.appString
import com.qwadb.app.model.AdbOperationResult
import com.qwadb.app.repository.AdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class UploadItemStatus { Pending, Uploading, Done, Failed }

data class UploadItem(
    val name: String,
    val sizeBytes: Long,
    val status: UploadItemStatus = UploadItemStatus.Pending,
    val message: String? = null,
)

data class UploadUiState(
    val items: List<UploadItem> = emptyList(),
    val busy: Boolean = false,
    val status: String? = null,
)

/**
 * 「上传到设备」：把手机本地选中的文件推送到设备 `/sdcard/Uploads`（不存在会自动创建）。
 */
class UploadViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
    private val localFileManager: LocalFileManager = AppServices.localFileManager,
) : ViewModel() {
    private val state = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = state.asStateFlow()

    fun upload(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val items = uris.map { uri ->
            val name = runCatching { localFileManager.displayName(uri) }.getOrDefault("file")
            UploadItem(name = name, sizeBytes = 0L)
        }
        state.value = UploadUiState(items = items, busy = true, status = null)

        viewModelScope.launch {
            // 确保目标目录存在
            adbRepository.runShell("mkdir -p $UploadDir")

            var ok = 0
            var failed = 0
            items.forEachIndexed { index, item ->
                updateItem(index) { it.copy(status = UploadItemStatus.Uploading) }
                runCatching {
                    val localFile = localFileManager.copyToCache(uris[index], item.name)
                    adbRepository.push(localFile, "$UploadDir/${localFile.name}", null)
                }.fold(
                    onSuccess = { result ->
                        if (result is AdbOperationResult.Success) {
                            ok++
                            updateItem(index) { it.copy(status = UploadItemStatus.Done) }
                        } else {
                            failed++
                            val msg = (result as? AdbOperationResult.Failure)?.message
                            updateItem(index) { it.copy(status = UploadItemStatus.Failed, message = msg) }
                        }
                    },
                    onFailure = { error ->
                        failed++
                        updateItem(index) {
                            it.copy(status = UploadItemStatus.Failed, message = error.message)
                        }
                    },
                )
            }
            state.value = state.value.copy(
                busy = false,
                status = if (failed == 0) {
                    appString(R.string.upload_done, ok)
                } else {
                    appString(R.string.upload_partial, ok, failed)
                },
            )
        }
    }

    private fun updateItem(index: Int, transform: (UploadItem) -> UploadItem) {
        val current = state.value.items
        if (index !in current.indices) return
        val updated = current.toMutableList()
        updated[index] = transform(updated[index])
        state.value = state.value.copy(items = updated)
    }

    private companion object {
        const val UploadDir = "/sdcard/Uploads"
    }
}
