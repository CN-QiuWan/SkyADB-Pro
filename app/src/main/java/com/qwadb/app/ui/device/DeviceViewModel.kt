package com.qwadb.app.ui.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qwadb.app.AppServices
import com.qwadb.app.R
import com.qwadb.app.adb.AdbSessionKind
import com.qwadb.app.i18n.appString
import com.qwadb.app.model.AdbDevice
import com.qwadb.app.model.AdbOperationResult
import com.qwadb.app.model.ConnectionState
import com.qwadb.app.model.DeviceInfo
import com.qwadb.app.model.OperationStatus
import com.qwadb.app.repository.AdbRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceUiState(
    val deviceName: String = appString(R.string.device_no_device_selected),
    val connectionState: ConnectionState = ConnectionState.Disconnected,
    val sessionKind: AdbSessionKind = AdbSessionKind.None,
    val info: DeviceInfo = DeviceInfo(),
    val refreshing: Boolean = false,
    val refreshStatus: OperationStatus = OperationStatus.Idle,
    val infoExpanded: Boolean = false,
    val connectionAddress: String? = null,
    val storageLoading: Boolean = false,
    val storageOverview: String? = null,
)

class DeviceViewModel(
    private val adbRepository: AdbRepository = AppServices.adbRepository,
) : ViewModel() {
    private val state = MutableStateFlow(DeviceUiState())
    val uiState: StateFlow<DeviceUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            adbRepository.selectedDeviceInfo.collect { info ->
                state.value = state.value.copy(info = info)
            }
        }

        viewModelScope.launch {
            adbRepository.recentDevices.collect { devices ->
                val connected = devices.firstOrNull { it.connectionState == ConnectionState.Connected }
                if (connected != null) {
                    state.value = state.value.copy(
                        deviceName = connected.name,
                        connectionState = connected.connectionState,
                        sessionKind = adbRepository.sessionKind(),
                        connectionAddress = currentConnectionAddress(connected),
                    )
                } else {
                    state.value = state.value.copy(
                        deviceName = appString(R.string.device_no_device_selected),
                        connectionState = ConnectionState.Disconnected,
                        sessionKind = AdbSessionKind.None,
                        info = DeviceInfo(),
                        refreshing = false,
                        refreshStatus = OperationStatus.Idle,
                        connectionAddress = null,
                    )
                }
            }
        }
    }

    /** 当前可分享的 adb connect 地址（仅 WiFi 会话），格式 host:port。 */
    private fun currentConnectionAddress(connected: AdbDevice): String? {
        if (adbRepository.sessionKind() != AdbSessionKind.Wifi) return null
        return AppServices.kadbManager.currentEndpoint()
            ?.takeIf { it.contains(":") }
            ?: connected.host.takeIf { it.isNotBlank() }?.let { host ->
                connected.port.takeIf { it > 0 }?.let { port -> "$host:$port" }
            }
    }

    fun toggleInfoExpanded() {
        state.value = state.value.copy(infoExpanded = !state.value.infoExpanded)
    }

    /**
     * 一键开启设备的固定 5555 无线 ADB 常连端口。
     *
     * 通过 `setprop service.adb.tcp.port <port>` 写入常连端口后重启 adbd 生效。
     * 注意：重启 adbd 会中断当前会话，因此失败也按「指令已下发」反馈。
     */
    fun enableAdbTcpPort(port: Int) {
        if (state.value.sessionKind == AdbSessionKind.None) {
            state.value = state.value.copy(
                refreshStatus = OperationStatus.Failed(
                    text = appString(R.string.kadb_not_connected),
                    suggestion = appString(R.string.adb_5555_connect_first),
                ),
            )
            return
        }
        state.value = state.value.copy(
            refreshStatus = OperationStatus.Running(appString(R.string.adb_5555_running, port)),
        )
        viewModelScope.launch {
            // 必须在重启 adbd 之前拿到设备 IP，重启后就查不到了
            val ip = resolveDeviceIp()
            val endpoint = ip?.let { "$it:$port" }

            // 优先用标准主机命令 `adb tcpip <port>`（全版本可靠，Android 11+ 也生效）
            // 失败再退回 setprop + 重启 adbd（部分旧设备/ROM 只认这种）
            val switched = adbRepository.enableTcpIp(port)
            if (switched !is AdbOperationResult.Success) {
                adbRepository.runShell("setprop service.adb.tcp.port $port; stop adbd; start adbd")
            }

            if (ip == null || endpoint == null) {
                state.value = state.value.copy(
                    refreshStatus = OperationStatus.Success(
                        appString(R.string.adb_5555_no_ip, port),
                    ),
                )
                return@launch
            }

            state.value = state.value.copy(
                refreshStatus = OperationStatus.Running(
                    appString(R.string.adb_5555_reconnecting, endpoint),
                ),
            )

            var connected = false
            var attempt = 0
            while (!connected && attempt < AutoReconnectAttempts) {
                attempt++
                delay(AutoReconnectDelayMillis)
                if (adbRepository.connect(ip, port) is AdbOperationResult.Success) {
                    connected = true
                }
            }

            state.value = state.value.copy(
                refreshStatus = OperationStatus.Success(
                    if (connected) {
                        appString(R.string.adb_5555_success, endpoint, port)
                    } else {
                        appString(R.string.adb_5555_triggered, endpoint)
                    },
                ),
            )
            if (connected) {
                refreshDeviceInfo()
            }
        }
    }

    /**
     * 取设备局域网 IP：优先复用当前 WiFi 会话的 host，
     * 否则在 adbd 重启前向设备查询（`ip` 不可用则退化为 `ifconfig`）。
     */
    private suspend fun resolveDeviceIp(): String? {
        AppServices.kadbManager.currentEndpoint()
            ?.substringBefore(':')
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        for (command in IpLookupCommands) {
            val result = adbRepository.runShell(command)
            if (result !is AdbOperationResult.Success) continue
            val ip = IpRegex.find(result.data.output)?.value
            if (ip != null) return ip
        }
        return null
    }

    /** 扫码解析出 host:port 后发起无线连接，成功后自动刷新设备信息。 */
    fun connectToScanResult(host: String, port: Int) {
        val endpoint = "$host:$port"
        state.value = state.value.copy(
            refreshStatus = OperationStatus.Running(appString(R.string.qr_scan_connecting, endpoint)),
        )
        viewModelScope.launch {
            when (val result = adbRepository.connect(host, port)) {
                is AdbOperationResult.Success -> {
                    state.value = state.value.copy(
                        refreshStatus = OperationStatus.Success(
                            appString(R.string.qr_scan_connect_success, endpoint),
                        ),
                    )
                    refreshDeviceInfo()
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        refreshStatus = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }

    /** 读取设备各分区占用（df -h），结果通过 dialog 展示。 */
    fun loadStorageOverview() {
        if (state.value.sessionKind == AdbSessionKind.None) {
            state.value = state.value.copy(
                storageOverview = appString(R.string.kadb_not_connected),
                storageLoading = false,
            )
            return
        }
        state.value = state.value.copy(storageLoading = true, storageOverview = null)
        viewModelScope.launch {
            when (val result = adbRepository.runShell("df -h 2>/dev/null")) {
                is AdbOperationResult.Success -> {
                    val text = result.data.output.ifBlank { result.data.errorOutput }
                    state.value = state.value.copy(
                        storageLoading = false,
                        storageOverview = text.ifBlank { appString(R.string.storage_overview_empty) },
                    )
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        storageLoading = false,
                        storageOverview = result.message,
                    )
                }
            }
        }
    }

    fun dismissStorageOverview() {
        state.value = state.value.copy(storageOverview = null, storageLoading = false)
    }

    fun refreshDeviceInfo() {
        state.value = state.value.copy(
            refreshing = true,
            refreshStatus = OperationStatus.Running(appString(R.string.device_refreshing)),
        )
        viewModelScope.launch {
            when (val result = adbRepository.refreshDeviceInfo()) {
                is AdbOperationResult.Success -> {
                    state.value = state.value.copy(
                        refreshing = false,
                        info = result.data,
                        refreshStatus = OperationStatus.Success(appString(R.string.device_refresh_success)),
                    )
                }
                is AdbOperationResult.Failure -> {
                    state.value = state.value.copy(
                        refreshing = false,
                        refreshStatus = OperationStatus.Failed(result.message, result.suggestion),
                    )
                }
            }
        }
    }

    private companion object {
        /** adbd 重启后自动重连的尝试次数与间隔。 */
        const val AutoReconnectAttempts = 6
        const val AutoReconnectDelayMillis = 2_000L

        val IpRegex = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")

        /** 查询设备局域网 IP 的候选命令，按顺序尝试。 */
        val IpLookupCommands = listOf(
            "ip -4 addr show scope global",
            "ifconfig wlan0",
        )
    }
}
