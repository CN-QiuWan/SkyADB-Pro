package com.qwadb.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.qwadb.app.ui.components.AppTopBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qwadb.app.R
import com.qwadb.app.model.OperationStatus
import com.qwadb.app.ui.components.EmptyState
import com.qwadb.app.ui.components.SectionHeader
import com.qwadb.app.ui.theme.AdbManagerTheme
import com.qwadb.app.ui.theme.AppDimens

@Composable
fun ShellScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    viewModel: ShellViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    ShellContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onBackClick = onBackClick,
        onCommandChanged = viewModel::onCommandChanged,
        onExecuteClick = viewModel::onExecuteClick,
        onHistoryCommandClick = viewModel::onHistoryCommandClick,
        onToggleFavoriteClick = viewModel::toggleFavorite,
        onFavoriteClick = viewModel::onFavoriteClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellContent(
    bottomPadding: Dp = 0.dp,
    uiState: ShellUiState,
    onBackClick: () -> Unit,
    onCommandChanged: (String) -> Unit,
    onExecuteClick: () -> Unit,
    onHistoryCommandClick: (String) -> Unit,
    onToggleFavoriteClick: (String) -> Unit = {},
    onFavoriteClick: (String) -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.shell_title)) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(imageVector = Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = AppDimens.ScreenPadding,
                top = AppDimens.ScreenPadding,
                end = AppDimens.ScreenPadding,
                bottom = AppDimens.ScreenPadding + bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(AppDimens.SectionGap),
        ) {
            item {
                ShellCommandCard(
                    uiState = uiState,
                    onCommandChanged = onCommandChanged,
                    onExecuteClick = onExecuteClick,
                    onToggleFavoriteClick = onToggleFavoriteClick,
                )
            }
            item { ShellOutputCard(output = uiState.output) }
            item {
                ShellFavoritesCard(
                    favorites = uiState.favorites,
                    onFavoriteClick = onFavoriteClick,
                    onRemoveFavoriteClick = onToggleFavoriteClick,
                )
            }
            item { ShellHistoryCard(history = uiState.history, onHistoryCommandClick = onHistoryCommandClick) }
        }
    }
}

@Composable
private fun ShellCommandCard(
    uiState: ShellUiState,
    onCommandChanged: (String) -> Unit,
    onExecuteClick: () -> Unit,
    onToggleFavoriteClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(title = stringResource(R.string.shell_command_input_title))
            OutlinedTextField(
                value = uiState.command,
                onValueChange = onCommandChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.shell_command_label)) },
                placeholder = { Text(stringResource(R.string.shell_command_placeholder)) },
                minLines = 1,
                maxLines = 4,
                trailingIcon = {
                    if (uiState.command.isNotBlank()) {
                        val isFavorite = uiState.command.trim() in uiState.favorites
                        IconButton(onClick = { onToggleFavoriteClick(uiState.command) }) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                                contentDescription = stringResource(
                                    if (isFavorite) R.string.shell_remove_favorite else R.string.shell_add_favorite,
                                ),
                            )
                        }
                    }
                },
            )
            ShellStatusMessage(status = uiState.operationStatus)
            Button(
                onClick = onExecuteClick,
                enabled = uiState.executeEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(imageVector = Icons.Outlined.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.action_execute))
            }
        }
    }
}

@Composable
private fun ShellOutputCard(output: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(title = stringResource(R.string.shell_output_title))
            if (output.isBlank()) {
                EmptyState(title = stringResource(R.string.shell_no_output))
            } else {
                Text(
                    text = output,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp)
                        .horizontalScroll(rememberScrollState())
                        .background(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(AppDimens.CardRadius),
                        )
                        .padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ShellFavoritesCard(
    favorites: List<String>,
    onFavoriteClick: (String) -> Unit,
    onRemoveFavoriteClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(title = stringResource(R.string.shell_favorites_title))
            if (favorites.isEmpty()) {
                EmptyState(title = stringResource(R.string.shell_no_favorites))
            } else {
                favorites.forEach { command ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(AppDimens.CardRadius),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { onFavoriteClick(command) }
                                .padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = command,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 10.dp),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            IconButton(onClick = { onRemoveFavoriteClick(command) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = stringResource(R.string.shell_remove_favorite),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShellHistoryCard(
    history: List<String>,
    onHistoryCommandClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(title = stringResource(R.string.shell_history_title))
            if (history.isEmpty()) {
                EmptyState(title = stringResource(R.string.shell_no_history))
            } else {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    history.forEach { command ->
                        AssistChip(
                            onClick = { onHistoryCommandClick(command) },
                            label = { Text(command) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShellStatusMessage(status: OperationStatus) {
    when (status) {
        OperationStatus.Idle -> Unit
        is OperationStatus.Running -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(text = status.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is OperationStatus.Success -> Text(text = status.text, color = MaterialTheme.colorScheme.primary)
        is OperationStatus.Failed -> Text(
            text = stringResource(R.string.device_status_error_format, status.text, status.suggestion),
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Preview(name = "Shell - 空状态", showBackground = true, widthDp = 390)
@Composable
private fun ShellContentEmptyPreview() {
    AdbManagerTheme(dynamicColor = false) {
        ShellContent(
            uiState = ShellUiState(),
            onBackClick = {},
            onCommandChanged = {},
            onExecuteClick = {},
            onHistoryCommandClick = {},
        )
    }
}

@Preview(name = "Shell - 输出态", showBackground = true, widthDp = 390)
@Composable
private fun ShellContentOutputPreview() {
    AdbManagerTheme(dynamicColor = false) {
        ShellContent(
            uiState = ShellUiState(
                command = "getprop ro.product.model",
                output = "Android TV",
                history = listOf("getprop ro.product.model", "wm size", "dumpsys battery"),
                operationStatus = OperationStatus.Success("命令执行完成，退出码 0"),
                executeEnabled = true,
            ),
            onBackClick = {},
            onCommandChanged = {},
            onExecuteClick = {},
            onHistoryCommandClick = {},
        )
    }
}
