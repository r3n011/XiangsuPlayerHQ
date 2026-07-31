package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.utils.TranscodeCacheManager

@Composable
fun TranscodeCacheListDialog(
    entries: List<TranscodeCacheManager.TranscodeCacheEntry>,
    onDismiss: () -> Unit,
    onDelete: (TranscodeCacheManager.TranscodeCacheEntry) -> Unit,
    onClearAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(id = R.string.transcode_cache_list_title)) },
        text = {
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(id = R.string.transcode_cache_list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn {
                    items(entries, key = { it.cacheKey }) { entry ->
                        CacheEntryItem(
                            entry = entry,
                            onDelete = { onDelete(entry) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.cancel))
            }
        },
        dismissButton = {
            if (entries.isNotEmpty()) {
                TextButton(
                    onClick = onClearAll,
                    enabled = entries.isNotEmpty()
                ) {
                    Text(text = stringResource(id = R.string.transcode_cache_list_clear_all))
                }
            }
        }
    )
}

@Composable
private fun CacheEntryItem(
    entry: TranscodeCacheManager.TranscodeCacheEntry,
    onDelete: () -> Unit
) {
    val sizeMb = entry.fileSize / (1024f * 1024f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = entry.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "%.1f MB".format(sizeMb),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(id = R.string.transcode_cache_list_delete),
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}
