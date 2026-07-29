package com.theveloper.pixelplay.presentation.screens

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.database.TranscodeCacheEntity
import com.theveloper.pixelplay.presentation.viewmodel.TranscodeCacheViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscodeCacheManagementScreen(
    navController: NavController,
    viewModel: TranscodeCacheViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val cacheEntries by viewModel.cacheEntries.collectAsStateWithLifecycle(initialValue = emptyList())
    val totalSizeBytes by viewModel.totalSizeBytes.collectAsStateWithLifecycle(initialValue = 0L)
    val ttlKey by viewModel.ttlKey.collectAsStateWithLifecycle(initialValue = "3_days")

    var showClearAllDialog by remember { mutableStateOf(false) }
    var showDeleteEntryDialog by remember { mutableStateOf<TranscodeCacheEntity?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshSize()
    }

    val ttlLabels = mapOf(
        "1_day" to stringResource(R.string.transcode_cache_ttl_1_day),
        "3_days" to stringResource(R.string.transcode_cache_ttl_3_days),
        "7_days" to stringResource(R.string.transcode_cache_ttl_7_days),
        "30_days" to stringResource(R.string.transcode_cache_ttl_30_days),
        "never" to stringResource(R.string.transcode_cache_ttl_never)
    )

    val currentTtlLabel = ttlLabels[ttlKey] ?: ttlLabels["3_days"]!!

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = stringResource(R.string.transcode_cache_title))
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_close_notice)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.cleanExpiredNow() }) {
                        Icon(
                            imageVector = Icons.Rounded.Sensors,
                            contentDescription = stringResource(R.string.transcode_cache_action_cleanup)
                        )
                    }
                    IconButton(onClick = { showClearAllDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.transcode_cache_action_clear_all)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 顶部统计卡片
            item {
                StatsCard(
                    entryCount = cacheEntries.size,
                    totalSizeBytes = totalSizeBytes,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // TTL 设置卡片
            item {
                TtlSettingsCard(
                    currentTtlLabel = currentTtlLabel,
                    onSelectTtl = { key -> viewModel.setTtl(key) },
                    ttlLabels = ttlLabels,
                    currentTtlKey = ttlKey,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 列表标题
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.transcode_cache_list_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.transcode_cache_count_format,
                            cacheEntries.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (cacheEntries.isEmpty()) {
                item {
                    EmptyStateContent(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                    )
                }
            } else {
                items(
                    items = cacheEntries,
                    key = { it.cacheKey }
                ) { entry ->
                    CacheEntryItem(
                        entry = entry,
                        onDelete = { showDeleteEntryDialog = entry },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // 确认删除单个条目对话框
    if (showDeleteEntryDialog != null) {
        val entry = showDeleteEntryDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteEntryDialog = null },
            title = {
                Text(text = stringResource(R.string.transcode_cache_delete_title))
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.transcode_cache_delete_message,
                        entry.songTitle ?: entry.filePath.substringAfterLast('/')
                            .ifBlank { entry.filePath }
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEntry(entry.cacheKey)
                    showDeleteEntryDialog = null
                }) {
                    Text(text = stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteEntryDialog = null }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 清空全部对话框
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = {
                Text(text = stringResource(R.string.transcode_cache_clear_all_title))
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.transcode_cache_clear_all_message,
                        cacheEntries.size
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearAllDialog = false
                }) {
                    Text(text = stringResource(R.string.common_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) {
                    Text(text = stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/**
 * 顶部统计卡片
 */
@Composable
private fun StatsCard(
    entryCount: Int,
    totalSizeBytes: Long,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                tonalElevation = 2.dp
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(12.dp).size(32.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.transcode_cache_stats_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.transcode_cache_stats_subtitle,
                        entryCount,
                        Formatter.formatShortFileSize(LocalContext.current, totalSizeBytes)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * TTL 设置卡片
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TtlSettingsCard(
    currentTtlLabel: String,
    currentTtlKey: String,
    ttlLabels: Map<String, String>,
    onSelectTtl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.transcode_cache_ttl_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.transcode_cache_ttl_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ttlLabels.forEach { (key, label) ->
                    AssistChip(
                        onClick = { onSelectTtl(key) },
                        label = { Text(text = label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (key == currentTtlKey)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                            labelColor = if (key == currentTtlKey)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.transcode_cache_ttl_current_format,
                    currentTtlLabel
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 单个缓存条目
 */
@Composable
private fun CacheEntryItem(
    entry: TranscodeCacheEntity,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                tonalElevation = 1.dp
            ) {
                Icon(
                    imageVector = Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(10.dp).size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.songTitle
                        ?: entry.filePath.substringAfterLast('/').ifBlank { entry.filePath },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!entry.artistName.isNullOrBlank()) {
                    Text(
                        text = entry.artistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(Formatter.formatShortFileSize(LocalContext.current, entry.fileSizeBytes))
                        append("  ·  ")
                        append(stringResource(R.string.transcode_cache_last_played))
                        append(": ")
                        append(formatRelativeTime(entry.lastPlayed))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.transcode_cache_action_delete),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptyStateContent(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp
        ) {
            Icon(
                imageVector = Icons.Rounded.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp).size(56.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.transcode_cache_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.transcode_cache_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 相对时间格式化
 */
private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    val diffSec = diffMs / 1000
    val diffMin = diffSec / 60
    val diffHour = diffMin / 60
    val diffDay = diffHour / 24

    return when {
        diffSec < 60 -> "${diffSec}s 前"
        diffMin < 60 -> "${diffMin}分钟前"
        diffHour < 24 -> "${diffHour}小时前"
        diffDay < 7 -> "${diffDay}天前"
        else -> {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
