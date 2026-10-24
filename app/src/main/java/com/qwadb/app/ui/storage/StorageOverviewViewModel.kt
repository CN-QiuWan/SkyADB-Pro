package com.qwadb.app.ui.storage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwadb.app.AppServices
import com.qwadb.app.R
import com.qwadb.app.i18n.appString
import com.qwadb.app.model.AdbOperationResult
import com.qwadb.app.repository.AdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class StorageCategory(val labelRes: Int, val path: String?) {
    Images(R.string.storage_cat_images, "/sdcard/DCIM"),
    Videos(R.string.storage_cat_videos, "/sdcard/Movies"),
    Audio(R.string.storage_cat_audio, "/sdcard/Music"),
    Documents(R.string.storage_cat_documents, "/sdcard/Documents"),
    Downloads(R.string.storage_cat_downloads, "/sdcard/Download"),
    Apk(R.string.storage_cat_apk, "/sdcard/Download"),
    Archives(R.string.storage_cat_archives, "/sdcard/Download"),
    Other(R.string.storage_cat_other, "/sdcard"),
}

data class StorageCategoryItem(
    val category: StorageCategory,
    val sizeBytes: Long,
)

data class StorageOverviewUiState(
    val loading: Boolean = false,
    val totalBytes: Long = 0L,
    val usedBytes: Long = 0L,
    val categories: List<StorageCategoryItem> = emptyList(),
    val error: String? = null,
) {
    val usedRatio: Float
        get() = if (totalBytes > 0L) (usedBytes.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f) else 0f

    val freeBytes: Long
        get() = (totalBytes - usedBytes).coerceAtLeast(0L)
}

/**
 * 存储空间概览：按类别统计设备 /sdcard 占用。
 *
 * 采用「目录归类 + 剩余归其他」的策略，避免全盘按扩展名遍历（对大容量设备过慢）。
 * 安装包/压缩包从 Download 目录单独统计，并从 Download 总量中扣除，保证各类别不重复计算。
 */
class StorageOverviewViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(StorageOverviewUiState())
    val uiState: StateFlow<StorageOverviewUiState> = state.asStateFlow()

    init {
        scan()
    }

    fun scan() {
        state.value = state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val df = run(DF_COMMAND)
            val (totalBytes, usedBytes) = parseDf(df)
            if (totalBytes <= 0L) {
                state.value = state.value.copy(
                    loading = false,
                    error = appString(R.string.storage_overview_empty),
                )
                return@launch
            }

            val du = run(DU_COMMAND)
            val dirSizes = parseDu(du)

            val images = sumDirs(dirSizes, IMAGE_DIRS)
            val videos = sumDirs(dirSizes, VIDEO_DIRS)
            val audio = sumDirs(dirSizes, AUDIO_DIRS)
            val documents = sumDirs(dirSizes, DOC_DIRS)
            val downloadTotal = dirSizes[DOWNLOAD_DIR] ?: 0L

            val apk = parseBytes(run(APK_COMMAND))
            val archives = parseBytes(run(ARCHIVE_COMMAND))
            val downloadRest = (downloadTotal - apk - archives).coerceAtLeast(0L)

            val known = images + videos + audio + documents + downloadRest + apk + archives
            val other = (usedBytes - known).coerceAtLeast(0L)

            val items = listOf(
                StorageCategoryItem(StorageCategory.Images, images),
                StorageCategoryItem(StorageCategory.Videos, videos),
                StorageCategoryItem(StorageCategory.Audio, audio),
                StorageCategoryItem(StorageCategory.Documents, documents),
                StorageCategoryItem(StorageCategory.Apk, apk),
                StorageCategoryItem(StorageCategory.Archives, archives),
                StorageCategoryItem(StorageCategory.Downloads, downloadRest),
                StorageCategoryItem(StorageCategory.Other, other),
            ).filter { it.sizeBytes > 0L }

            state.value = StorageOverviewUiState(
                loading = false,
                totalBytes = totalBytes,
                usedBytes = usedBytes,
                categories = items,
                error = null,
            )
        }
    }

    private suspend fun run(command: String): String {
        return when (val result = adbRepository.runShell(command)) {
            is AdbOperationResult.Success -> result.data.output
            is AdbOperationResult.Failure -> ""
        }
    }

    private fun parseDf(output: String): Pair<Long, Long> {
        val line = output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .lastOrNull { it.firstOrNull()?.isDigit() != true && !it.startsWith("Filesystem") }
            ?: return 0L to 0L
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 3) return 0L to 0L
        val total = parts[1].toLongOrNull() ?: return 0L to 0L
        val used = parts[2].toLongOrNull() ?: return 0L to 0L
        return total * 1024L to used * 1024L
    }

    private fun parseDu(output: String): Map<String, Long> {
        return output.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size < 2) return@mapNotNull null
            val kb = parts[0].toLongOrNull() ?: return@mapNotNull null
            parts[1].trim() to kb * 1024L
        }.toMap()
    }

    private fun sumDirs(sizes: Map<String, Long>, dirs: List<String>): Long {
        return dirs.sumOf { sizes[it] ?: 0L }
    }

    private fun parseBytes(output: String): Long {
        return output.trim().lineSequence().lastOrNull()?.toLongOrNull() ?: 0L
    }

    private companion object {
        const val DF_COMMAND = "df -k /sdcard"
        const val DOWNLOAD_DIR = "/sdcard/Download"

        val IMAGE_DIRS = listOf("/sdcard/DCIM", "/sdcard/Pictures")
        val VIDEO_DIRS = listOf("/sdcard/Movies")
        val AUDIO_DIRS = listOf(
            "/sdcard/Music",
            "/sdcard/Podcasts",
            "/sdcard/Ringtones",
            "/sdcard/Alarms",
            "/sdcard/Notifications",
            "/sdcard/Recordings",
        )
        val DOC_DIRS = listOf("/sdcard/Documents")

        val DU_COMMAND = "du -sk " + (IMAGE_DIRS + VIDEO_DIRS + AUDIO_DIRS + DOC_DIRS + listOf(DOWNLOAD_DIR))
            .distinct()
            .joinToString(" ") + " 2>/dev/null"

        const val APK_COMMAND =
            "find /sdcard/Download -maxdepth 1 -type f -iname '*.apk' -exec stat -c %s {} + 2>/dev/null | awk '{s+=\$1} END{print s+0}'"
        const val ARCHIVE_COMMAND =
            "find /sdcard/Download -maxdepth 1 -type f \\( -iname '*.zip' -o -iname '*.rar' -o -iname '*.7z' -o -iname '*.tar' -o -iname '*.gz' \\) -exec stat -c %s {} + 2>/dev/null | awk '{s+=\$1} END{print s+0}'"
    }
}
