package com.qwadb.app.ui.remote

import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwadb.app.AppServices
import com.qwadb.app.R
import com.qwadb.app.i18n.AppText
import com.qwadb.app.i18n.appString
import com.qwadb.app.model.AdbOperationResult
import com.qwadb.app.model.OperationStatus
import com.qwadb.app.repository.AdbRepository
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RemoteControlUiState(
    val status: OperationStatus = OperationStatus.Idle,
    val customKeys: List<String> = emptyList(),
    val customKeyInput: String = "",
    val customKeyError: String? = null,
)

enum class RemoteKey(@param:StringRes val labelRes: Int, val keyCode: String) {
    Power(R.string.remote_key_power, "KEYCODE_POWER"),
    Wakeup(R.string.remote_key_wakeup, "KEYCODE_WAKEUP"),
    Sleep(R.string.remote_key_sleep, "KEYCODE_SLEEP"),
    Home(R.string.remote_key_home, "KEYCODE_HOME"),
    Back(R.string.remote_key_back, "KEYCODE_BACK"),
    Menu(R.string.remote_key_menu, "KEYCODE_MENU"),
    Up(R.string.remote_key_up, "KEYCODE_DPAD_UP"),
    Down(R.string.remote_key_down, "KEYCODE_DPAD_DOWN"),
    Left(R.string.remote_key_left, "KEYCODE_DPAD_LEFT"),
    Right(R.string.remote_key_right, "KEYCODE_DPAD_RIGHT"),
    Center(R.string.remote_key_center, "KEYCODE_DPAD_CENTER"),
    VolumeUp(R.string.remote_key_volume_up, "KEYCODE_VOLUME_UP"),
    VolumeDown(R.string.remote_key_volume_down, "KEYCODE_VOLUME_DOWN"),
    Mute(R.string.remote_key_mute, "KEYCODE_VOLUME_MUTE"),
    PlayPause(R.string.remote_key_play_pause, "KEYCODE_MEDIA_PLAY_PAUSE"),
    Previous(R.string.remote_key_previous, "KEYCODE_MEDIA_PREVIOUS"),
    Next(R.string.remote_key_next, "KEYCODE_MEDIA_NEXT"),
}

class RemoteControlViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(RemoteControlUiState())
    val uiState: StateFlow<RemoteControlUiState> = state.asStateFlow()

    fun sendKey(key: RemoteKey) {
        val label = appString(key.labelRes)
        state.value = state.value.copy(status = OperationStatus.Running(appString(R.string.remote_sending, label)))
        viewModelScope.launch {
            when (val result = adbRepository.runShell("input keyevent ${key.keyCode}")) {
                is AdbOperationResult.Success -> {
                    state.value = if (result.data.exitCode == 0) {
                        state.value.copy(status = OperationStatus.Success(appString(R.string.remote_sent, label)))
                    } else {
                        state.value.copy(
                            status = OperationStatus.Failed(
                                text = appString(R.string.remote_send_failed),
                                suggestion = result.data.errorOutput
                                    .toRemoteInputSuggestion()
                                    .resolve(AppServices.context),
                            ),
                        )
                    }
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        status = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }

    fun onCustomKeyInputChanged(value: String) {
        state.value = state.value.copy(customKeyInput = value, customKeyError = null)
    }

    fun addCustomKey(sequence: String) {
        val trimmed = sequence.trim()
        if (parseKeyCodeSequence(trimmed).isEmpty()) {
            state.value = state.value.copy(customKeyError = appString(R.string.remote_custom_key_invalid))
            return
        }
        val canonical = trimmed.split(",").joinToString(",") { it.trim().uppercase(Locale.ROOT) }
        val current = state.value
        if (current.customKeys.contains(canonical) || current.customKeys.size >= MaxCustomKeys) {
            state.value = current.copy(customKeyError = appString(R.string.remote_custom_key_invalid))
            return
        }
        state.value = current.copy(
            customKeys = current.customKeys + canonical,
            customKeyInput = "",
            customKeyError = null,
            status = OperationStatus.Success(appString(R.string.remote_custom_key_saved)),
        )
    }

    fun removeCustomKey(sequence: String) {
        state.value = state.value.copy(
            customKeys = state.value.customKeys.filterNot { it == sequence },
        )
    }

    fun clearCustomKeys() {
        state.value = state.value.copy(customKeys = emptyList())
    }

    fun sendCustomKey(sequence: String) {
        val keyCodes = parseKeyCodeSequence(sequence)
        if (keyCodes.isEmpty()) return
        state.value = state.value.copy(
            status = OperationStatus.Running(appString(R.string.remote_sending, sequence)),
        )
        viewModelScope.launch {
            for (keyCode in keyCodes) {
                ensureActive()
                when (val result = adbRepository.runShell("input keyevent $keyCode")) {
                    is AdbOperationResult.Success -> {
                        if (result.data.exitCode != 0) {
                            state.value = state.value.copy(
                                status = OperationStatus.Failed(
                                    text = appString(R.string.remote_send_failed),
                                    suggestion = result.data.errorOutput
                                        .toRemoteInputSuggestion()
                                        .resolve(AppServices.context),
                                ),
                            )
                            return@launch
                        }
                    }
                    is AdbOperationResult.Failure -> {
                        state.value = state.value.copy(
                            status = OperationStatus.Failed(result.message, result.suggestion),
                        )
                        return@launch
                    }
                }
                delay(KeySendDelayMillis)
            }
            state.value = state.value.copy(
                status = OperationStatus.Success(appString(R.string.remote_sent, sequence)),
            )
        }
    }

    /** Maps a key name (case-insensitive, e.g. "HOME") to its KeyEvent keycode, or null if unknown. */
    fun parseKeyCode(name: String): Int? {
        val key = RemoteKey.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
            ?: return null
        return runCatching {
            KeyEvent::class.java.getField(key.keyCode).getInt(null)
        }.getOrNull()
    }

    private fun parseKeyCodeSequence(sequence: String): List<Int> {
        val names = sequence.split(",").map { it.trim() }
        if (names.isEmpty() || names.any { it.isEmpty() }) return emptyList()
        val codes = names.mapNotNull { parseKeyCode(it) }
        return if (codes.size == names.size) codes else emptyList()
    }

    private companion object {
        const val MaxCustomKeys = 10
        const val KeySendDelayMillis = 100L
    }
}

internal fun String.toRemoteInputSuggestion(): AppText {
    return when {
        contains("INJECT_EVENTS", ignoreCase = true) ||
            contains("Injecting input events", ignoreCase = true) ->
            AppText.Res(R.string.remote_key_control_blocked)
        else -> {
            val firstLine = lineSequence().firstOrNull { it.isNotBlank() }
            if (firstLine != null) {
                AppText.Plain(firstLine)
            } else {
                AppText.Res(R.string.remote_confirm_connected)
            }
        }
    }
}
