package com.qwadb.app.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.qwadb.app.ui.components.AppTopBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qwadb.app.R
import com.qwadb.app.ui.components.SectionHeader
import com.qwadb.app.ui.theme.AppDimens
import java.util.Locale

private val CategoryColors = mapOf(
    StorageCategory.Images to Color(0xFF3B82F6),
    StorageCategory.Videos to Color(0xFF8B5CF6),
    StorageCategory.Audio to Color(0xFFEC4899),
    StorageCategory.Documents to Color(0xFF10B981),
    StorageCategory.Apk to Color(0xFFF59E0B),
    StorageCategory.Archives to Color(0xFFEF4444),
    StorageCategory.Downloads to Color(0xFF06B6D4),
    StorageCategory.Other to Color(0xFF6B7280),
)

@Composable
fun StorageOverviewScreen(
    bottomPadding: Dp = 0.dp,
    onBackClick: () -> Unit,
    onOpenCategory: (path: String) -> Unit = {},
    viewModel: StorageOverviewViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    StorageOverviewContent(
        bottomPadding = bottomPadding,
        uiState = uiState,
        onBackClick = onBackClick,
        onOpenCategory = onOpenCategory,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorageOverviewContent(
    bottomPadding: Dp = 0.dp,
    uiState: StorageOverviewUiState,
    onBackClick: () -> Unit,
    onOpenCategory: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.storage_overview_title)) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                    )
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
            if (uiState.loading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            item {
                UsageHeaderCard(uiState)
            }

            if (uiState.categories.isNotEmpty()) {
                item { SectionHeader(title = stringResource(R.string.storage_section_user)) }
                item { CategoryListCard(uiState.categories, onOpenCategory) }
            }

            uiState.error?.let { error ->
                item {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageHeaderCard(uiState: StorageOverviewUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(AppDimens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatSize(uiState.usedBytes),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.storage_of_total_format, formatSize(uiState.totalBytes)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            SegmentedBar(uiState)
            Text(
                text = stringResource(
                    R.string.storage_free_format,
                    formatSize(uiState.freeBytes),
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SegmentedBar(uiState: StorageOverviewUiState) {
    val total = uiState.totalBytes.coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = RoundedCornerShape(7.dp),
            ),
    ) {
        if (uiState.categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(uiState.usedRatio.coerceIn(0f, 1f))
                    .height(14.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(7.dp)),
            )
        } else {
            uiState.categories.forEach { item ->
                val fraction = (item.sizeBytes.toDouble() / total).toFloat().coerceAtLeast(0f)
                if (fraction > 0f) {
                    Box(
                        modifier = Modifier
                            .weight(fraction)
                            .height(14.dp)
                            .background(CategoryColors[item.category] ?: Color.Gray),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryListCard(
    categories: List<StorageCategoryItem>,
    onOpenCategory: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AppDimens.CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            categories.forEach { item ->
                val path = item.category.path
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = path != null) { path?.let(onOpenCategory) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = CategoryColors[item.category] ?: Color.Gray,
                                shape = CircleShape,
                            ),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(item.category.labelRes),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = formatSize(item.sizeBytes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    return when {
        kb < 1024 -> String.format(Locale.US, "%.1f KB", kb)
        kb < 1024 * 1024 -> String.format(Locale.US, "%.1f MB", kb / 1024.0)
        kb < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", kb / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f TB", kb / (1024.0 * 1024.0 * 1024.0))
    }
}
