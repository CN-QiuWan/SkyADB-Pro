package com.qwadb.app.ui.mirror

import android.view.KeyEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeDown
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HideSource
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qwadb.app.R
import com.qwadb.app.model.OperationStatus
import com.qwadb.app.ui.ImmersiveFullscreen
import com.qwadb.app.ui.shared.screenStreamSurface

@Composable
fun MirrorScreen(
    onBackClick: () -> Unit,
    viewModel: MirrorViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var surface by remember { mutableStateOf<Surface?>(null) }
    // 右上角控制按钮（隐藏全部 / 菜单 / 退出）是否显示。
    var controlsVisible by remember { mutableStateOf(true) }
    // 底部按键条是否显示（由右上角菜单按钮单独控制）。
    var bottomVisible by remember { mutableStateOf(true) }
    // 上一次在“隐藏全部按钮”状态下按返回键的时间，用于区分单击回显 / 双击退出。
    var lastHiddenBackAt by remember { mutableLongStateOf(0L) }

    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    // 隐藏全部按钮后：单击返回键/侧滑返回一次 -> 重新显示右上角按钮；
    // 双击返回键/侧滑返回两次 -> 快捷退出屏幕镜像。
    BackHandler {
        if (controlsVisible) {
            onBackClick()
        } else {
            val now = System.currentTimeMillis()
            if (now - lastHiddenBackAt <= DoubleBackExitIntervalMillis) {
                onBackClick()
            } else {
                lastHiddenBackAt = now
                controlsVisible = true
            }
        }
    }

    ImmersiveFullscreen()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val surfaceModifier = Modifier.screenStreamSurface(
                containerWidth = maxWidth.value,
                containerHeight = maxHeight.value,
                videoWidth = uiState.videoWidth,
                videoHeight = uiState.videoHeight,
            )
            AndroidView(
                modifier = surfaceModifier,
                factory = { context ->
                    SurfaceView(context).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                surface = holder.surface
                                viewModel.onSurfaceCreated(holder.surface)
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int,
                            ) = Unit

                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                surface = null
                                viewModel.onSurfaceDestroyed()
                            }
                        })
                        setOnTouchListener { view, event ->
                            if (surface != null) {
                                viewModel.sendTouch(event, view.width, view.height)
                            }
                            true
                        }
                    }
                },
            )
        }

        if (controlsVisible) {
            MirrorTopActions(
                bottomVisible = bottomVisible,
                onHideAll = {
                    controlsVisible = false
                    bottomVisible = false
                },
                onToggleControls = { bottomVisible = !bottomVisible },
                onClose = {
                    viewModel.stop()
                    onBackClick()
                },
            )
        }

        if (controlsVisible && bottomVisible) {
            MirrorControls(
                modifier = Modifier.align(Alignment.BottomCenter),
                screenPowerOn = uiState.screenPowerOn,
                audioEnabled = uiState.audioEnabled,
                onScreenPowerClick = viewModel::toggleScreenPower,
                onAudioClick = viewModel::toggleAudioEnabled,
                onKey = viewModel::sendKey,
            )
        }

        if (uiState.status is OperationStatus.Running || uiState.status is OperationStatus.Failed) {
            MirrorStatus(
                modifier = Modifier.align(Alignment.Center),
                status = uiState.status,
            )
        }
    }
}

private const val DoubleBackExitIntervalMillis = 400L

@Composable
private fun MirrorTopActions(
    bottomVisible: Boolean,
    onHideAll: () -> Unit,
    onToggleControls: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 隐藏全部按钮：进入纯净操作界面，单击返回键/侧滑返回一次即可恢复。
        MirrorLabeledButton(
            icon = Icons.Outlined.HideSource,
            label = stringResource(R.string.mirror_label_hide),
            contentDescription = stringResource(R.string.mirror_hide_all_desc),
            onClick = onHideAll,
        )
        MirrorLabeledButton(
            icon = Icons.Outlined.MoreVert,
            label = stringResource(R.string.mirror_label_controls),
            contentDescription = stringResource(
                if (bottomVisible) R.string.mirror_hide_controls_desc else R.string.mirror_show_controls_desc,
            ),
            onClick = onToggleControls,
        )
        MirrorLabeledButton(
            icon = Icons.Outlined.Close,
            label = stringResource(R.string.mirror_label_exit),
            contentDescription = stringResource(R.string.mirror_close_desc),
            onClick = onClose,
        )
    }
}

@Composable
private fun MirrorControls(
    modifier: Modifier,
    screenPowerOn: Boolean,
    audioEnabled: Boolean,
    onScreenPowerClick: () -> Unit,
    onAudioClick: () -> Unit,
    onKey: (Int) -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(12.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.50f)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MirrorLabeledButton(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.action_back), stringResource(R.string.action_back)) { onKey(KeyEvent.KEYCODE_BACK) }
                MirrorLabeledButton(Icons.Outlined.Home, stringResource(R.string.mirror_label_home), "Home") { onKey(KeyEvent.KEYCODE_HOME) }
                MirrorLabeledButton(Icons.Outlined.Apps, stringResource(R.string.mirror_label_recent), stringResource(R.string.mirror_recent_tasks_desc)) { onKey(KeyEvent.KEYCODE_APP_SWITCH) }
                MirrorLabeledButton(Icons.Outlined.PowerSettingsNew, stringResource(R.string.mirror_label_power), stringResource(R.string.remote_power_title)) { onKey(KeyEvent.KEYCODE_POWER) }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MirrorLabeledButton(Icons.AutoMirrored.Outlined.VolumeDown, stringResource(R.string.mirror_label_volume_down), stringResource(R.string.mirror_volume_down_desc)) { onKey(KeyEvent.KEYCODE_VOLUME_DOWN) }
                MirrorLabeledButton(Icons.AutoMirrored.Outlined.VolumeUp, stringResource(R.string.mirror_label_volume_up), stringResource(R.string.mirror_volume_up_desc)) { onKey(KeyEvent.KEYCODE_VOLUME_UP) }
                // 接管音频开关：开/关设备音频输出，避免设备无声音；与观看模式共用同一设置。
                MirrorLabeledButton(
                    icon = if (audioEnabled) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                    label = stringResource(R.string.mirror_label_audio),
                    contentDescription = stringResource(
                        if (audioEnabled) R.string.mirror_audio_on_desc else R.string.mirror_audio_off_desc,
                    ),
                    tint = if (audioEnabled) Color.White else Color.White.copy(alpha = 0.45f),
                    onClick = onAudioClick,
                )
                MirrorLabeledButton(
                    icon = if (screenPowerOn) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    label = stringResource(R.string.mirror_label_privacy),
                    contentDescription = stringResource(
                        if (screenPowerOn) R.string.mirror_privacy_on_desc else R.string.mirror_privacy_off_desc,
                    ),
                    onClick = onScreenPowerClick,
                )
            }
        }
    }
}

@Composable
private fun MirrorLabeledButton(
    icon: ImageVector,
    label: String,
    contentDescription: String,
    enabled: Boolean = true,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    val contentColor = if (enabled) tint else tint.copy(alpha = 0.38f)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = contentColor,
        )
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun MirrorStatus(
    modifier: Modifier,
    status: OperationStatus,
) {
    Card(
        modifier = modifier.padding(24.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (status) {
                is OperationStatus.Running -> Text(status.text)
                is OperationStatus.Failed -> {
                    Text(status.text, color = MaterialTheme.colorScheme.error)
                    Text(
                        status.suggestion,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> Unit
            }
        }
    }
}
