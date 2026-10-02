@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.theveloper.pixelplay.presentation.components

import androidx.compose.foundation.layout.Arrangement
import com.theveloper.pixelplay.presentation.components.PixelAlertDialog
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.widget.Toast
import android.view.HapticFeedbackConstants
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.utils.LocalAppHapticsConfig
import com.theveloper.pixelplay.presentation.utils.performAppCompatHapticFeedback
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 首页内容卡片编辑弹窗：按住拖拽手柄调整顺序 + 每张卡片眼睛开关控制显示/隐藏
 * （对齐 Rhythm 的 HomeSectionOrderBottomSheet），保存后写回 DataStore。
 */
@Composable
fun HomeCardOrderSheet(
    entries: List<HomeCardOrderEntry>,
    /** 当前隐藏的卡片 id 集合 */
    hiddenCards: Set<String>,
    onReorder: (List<String>) -> Unit,
    /** 保存显隐：参数为隐藏卡片 id 集合 */
    onSaveHidden: (Set<String>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }
    var localEntries by remember { mutableStateOf(entries) }
    var localHidden by remember { mutableStateOf(hiddenCards) }
    val defaultEntries = remember { entries }

    LaunchedEffect(entries) {
        localEntries = entries
    }
    LaunchedEffect(hiddenCards) {
        localHidden = hiddenCards
    }

    if (showResetDialog) {
        PixelAlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.home_card_order_reset_dialog_title)) },
            text = { Text(stringResource(R.string.home_card_order_reset_dialog_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onReset()
                        localEntries = defaultEntries
                        localHidden = emptySet()
                        showResetDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_reset), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showResetDialog = false }
                ) {
                    Text(stringResource(R.string.cancel), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val view = LocalView.current
    val appHapticsConfig = LocalAppHapticsConfig.current

    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            localEntries = localEntries.toMutableList().apply {
                val moved = removeAt(from.index)
                add(to.index.coerceAtMost(size), moved)
            }
            performAppCompatHapticFeedback(
                view,
                appHapticsConfig,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
        },
        lazyListState = listState
    )
    var isLoading by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 26.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            stringResource(R.string.home_card_order_sheet_title),
                            style = MaterialTheme.typography.displaySmall,
                            fontFamily = GoogleSansRounded
                        )
                        Text(
                            stringResource(R.string.home_card_order_sheet_visibility_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            floatingActionButton = {
                FloatingToolBar(
                    modifier = Modifier,
                    onReset = { showResetDialog = true },
                    onDismiss = onDismiss,
                    onClick = {
                        scope.launch {
                            isLoading = true
                            delay(700) // 模拟保存操作
                            onReorder(localEntries.map { it.cardId })
                            onSaveHidden(localHidden)
                            isLoading = false
                            onDismiss()
                        }
                    }
                )
            },
            floatingActionButtonPosition = FabPosition.Center,
            containerColor = MaterialTheme.colorScheme.surface
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                if (isLoading) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ContainedLoadingIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.reorder_tabs_reordering))
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        contentPadding = PaddingValues(bottom = 100.dp, top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(localEntries, key = { it.cardId }) { entry ->
                            val isVisible = entry.cardId !in localHidden
                            ReorderableItem(reorderableState, key = entry.cardId) { isDragging ->
                                LaunchedEffect(isDragging) {
                                    if (isDragging) {
                                        performAppCompatHapticFeedback(
                                            view,
                                            appHapticsConfig,
                                            HapticFeedbackConstants.GESTURE_START
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(CircleShape),
                                    shadowElevation = if (isDragging) 4.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragIndicator,
                                            contentDescription = stringResource(R.string.cd_drag_handle),
                                            modifier = Modifier.draggableHandle()
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            text = entry.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (isVisible) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        // ⚡ 显隐开关（对齐 Rhythm 的眼睛图标）：
                                        //   不允许隐藏最后一张可见卡片，避免主页彻底空掉
                                        IconButton(
                                            onClick = {
                                                if (isVisible && localHidden.size >= localEntries.size - 1) {
                                                    Toast.makeText(
                                                        context,
                                                        R.string.home_card_order_at_least_one_visible,
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                    return@IconButton
                                                }
                                                localHidden = if (isVisible) {
                                                    localHidden + entry.cardId
                                                } else {
                                                    localHidden - entry.cardId
                                                }
                                                performAppCompatHapticFeedback(
                                                    view,
                                                    appHapticsConfig,
                                                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                                                )
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (isVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                                contentDescription = if (isVisible) {
                                                    stringResource(R.string.home_card_hide)
                                                } else {
                                                    stringResource(R.string.home_card_show)
                                                },
                                                tint = if (isVisible) {
                                                    MaterialTheme.colorScheme.primary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                },
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 首页内容卡片条目：cardId 与显示名称。
 */
data class HomeCardOrderEntry(
    val cardId: String,
    val title: String
)
