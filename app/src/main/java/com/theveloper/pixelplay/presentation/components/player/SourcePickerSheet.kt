package com.theveloper.pixelplay.presentation.components.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.playback.OnlineSourceOption
import androidx.compose.ui.res.stringResource

/**
 * 手动指定音源（对齐 lx-music 的换源选择）：点选后重新解析当前歌曲。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcePickerSheet(
    sources: List<OnlineSourceOption>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    scheme: ColorScheme = MaterialTheme.colorScheme,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    MaterialTheme(colorScheme = scheme) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = scheme.surfaceContainerLow,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.player_source_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                sources.forEach { source ->
                    SourceRow(
                        source = source,
                        scheme = scheme,
                        onSelect = { onSelect(source.id) },
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: OnlineSourceOption,
    scheme: ColorScheme,
    onSelect: () -> Unit,
) {
    Surface(
        color = scheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = source.available, onClick = onSelect),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = source.isCurrent,
                enabled = source.available,
                onClick = onSelect,
            )
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = source.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (source.available) scheme.onSurface else scheme.onSurfaceVariant,
                )
                if (!source.available) {
                    Text(
                        text = stringResource(R.string.player_source_unavailable),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            if (source.isCurrent) {
                Text(
                    text = stringResource(R.string.player_source_current),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.primary,
                )
            }
        }
    }
}
