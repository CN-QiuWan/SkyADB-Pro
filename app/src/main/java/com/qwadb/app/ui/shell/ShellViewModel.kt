package com.qwadb.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwadb.app.AppServices
import com.qwadb.app.R
import com.qwadb.app.adb.AdbSessionKind
import com.qwadb.app.data.AppSettingsStore
import com.qwadb.app.i18n.appString
import com.qwadb.app.model.AdbOperationResult
import com.qwadb.app.model.OperationStatus
import com.qwadb.app.repository.AdbRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ShellUiState(
    val command: String = "",
    val output: String = "",
    val history: List<String> = emptyList(),
    val favorites: List<String> = emptyList(),
    val operationStatus: OperationStatus = OperationStatus.Idle,
    val executeEnabled: Boolean = false,
)

class ShellViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
    private val settingsStore: AppSettingsStore = AppServices.settingsStore,
) : ViewModel() {
    private val state = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = settingsStore.settings.first()
            state.value = state.value.copy(
                history = stored.shellHistory,
                favorites = stored.shellFavorites,
            )
        }
    }

    fun onCommandChanged(value: String) {
        state.value = state.value.copy(
            command = value,
            executeEnabled = value.isNotBlank(),
            operationStatus = OperationStatus.Idle,
        )
    }

    fun onHistoryCommandClick(command: String) {
        onCommandChanged(command)
    }

    fun toggleFavorite(command: String) {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return
        val current = state.value
        val isFavorite = trimmed in current.favorites
        val newFavorites = if (isFavorite) {
            current.favorites - trimmed
        } else {
            (current.favorites + trimmed).distinct().take(20)
        }
        state.value = current.copy(
            favorites = newFavorites,
            operationStatus = OperationStatus.Success(
                appString(if (isFavorite) R.string.shell_favorite_removed else R.string.shell_favorite_added),
            ),
        )
        viewModelScope.launch { settingsStore.updateShellFavorites(newFavorites) }
    }

    fun onFavoriteClick(command: String) {
        val trimmed = command.trim()
        if (trimmed.isBlank()) return
        execute(trimmed)
    }

    fun onExecuteClick() {
        val command = state.value.command.trim()
        if (command.isBlank()) {
            state.value = state.value.copy(
                executeEnabled = false,
                operationStatus = OperationStatus.Failed(
                    text = appString(R.string.shell_cannot_execute),
                    suggestion = appString(R.string.shell_enter_command_hint),
                ),
            )
            return
        }
        execute(command)
    }

    private fun execute(command: String) {
        state.value = state.value.copy(
            executeEnabled = false,
            operationStatus = OperationStatus.Running(appString(R.string.shell_executing, command)),
        )

        viewModelScope.launch {
            if (adbRepository.sessionKind() == AdbSessionKind.UsbFastboot) {
                when (val result = adbRepository.runFastbootCommand(command)) {
                    is AdbOperationResult.Success -> applySuccess(command, result.data, exitCode = 0)
                    is AdbOperationResult.Failure -> applyFailure(result)
                }
                return@launch
            }

            when (val result = adbRepository.runShell(command)) {
                is AdbOperationResult.Success -> {
                    val commandResult = result.data
                    val combinedOutput = buildString {
                        if (commandResult.output.isNotBlank()) append(commandResult.output.trim())
                        if (commandResult.errorOutput.isNotBlank()) {
                            if (isNotEmpty()) appendLine()
                            append(commandResult.errorOutput.trim())
                        }
                        if (isEmpty()) append(appString(R.string.shell_command_no_output))
                    }
                    applySuccess(command, combinedOutput, commandResult.exitCode)
                }
                is AdbOperationResult.Failure -> applyFailure(result)
            }
        }
    }

    private fun applySuccess(command: String, output: String, exitCode: Int) {
        val newHistory = (listOf(command) + state.value.history).distinct().take(20)
        state.value = state.value.copy(
            output = output,
            history = newHistory,
            executeEnabled = true,
            operationStatus = OperationStatus.Success(appString(R.string.shell_execute_success, exitCode)),
        )
        viewModelScope.launch { settingsStore.updateShellHistory(newHistory) }
    }

    private fun applyFailure(result: AdbOperationResult.Failure) {
        state.value = state.value.copy(
            executeEnabled = true,
            operationStatus = OperationStatus.Failed(result.message, result.suggestion),
        )
    }
}
