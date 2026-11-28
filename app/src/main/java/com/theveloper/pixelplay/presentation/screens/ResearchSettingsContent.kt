package com.theveloper.pixelplay.presentation.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.research.ResearchPhase
import com.theveloper.pixelplay.data.research.ResearchUiState
import com.theveloper.pixelplay.presentation.viewmodel.ResearchViewModel
import java.util.Locale

/**
 * 设置 → 听歌数据研究计划 活动页。
 *
 * 页面生命周期引导（规范 05 §1.1）：同一套卡片按 [ResearchPhase] 换文案/颜色/主按钮，
 * 顶部只出现一条当前状态提示；天数/剩余量一律来自服务端状态，客户端只展示。
 */
@Composable
fun ResearchSettingsContent(
    onOpenLegal: (consentMode: Boolean) -> Unit,
    onOpenNotices: () -> Unit,
    viewModel: ResearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var showExitDialog by remember { mutableStateOf(false) }
    var toastText by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportRecords(uri) { ok ->
                toastText = context.getString(
                    if (ok) R.string.research_export_success else R.string.research_export_failed
                )
            }
        }
    }

    LaunchedEffect(toastText) {
        toastText?.let {
            kotlinx.coroutines.delay(2500)
            toastText = null
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 公告条：最新 1 条 + 未读红点（规范 §2 结构 1）
        uiState.latestNotice?.let { notice ->
            NoticeBanner(
                title = notice.title,
                showDot = uiState.hasUnreadNotice,
                onClick = onOpenNotices
            )
            Spacer(Modifier.height(10.dp))
        }

        when (uiState.phase) {
            ResearchPhase.NOT_PARTICIPATING -> NotParticipatingCard(
                onJoin = { onOpenLegal(true) }
            )
            ResearchPhase.COOLDOWN -> CooldownCard(uiState)
            ResearchPhase.ENDED -> EndedCard()
            else -> ParticipatingContent(
                uiState = uiState,
                viewModel = viewModel,
                onOpenLegal = { onOpenLegal(false) },
                onOpenNotices = onOpenNotices,
                onExport = { exportLauncher.launch("xiangsplayer_listen_records.json") },
                onCopyKey = {
                    uiState.licenseKeyMasked?.let {
                        clipboard.setText(AnnotatedString(it))
                        toastText = it
                    }
                },
                onConfirmExit = { showExitDialog = true }
            )
        }

        toastText?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showExitDialog) {
        val cooldownDays = ((uiState.cooldownUntil - System.currentTimeMillis() / 1000)
            .coerceAtLeast(0) / 86400L).coerceAtLeast(30)
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.research_exit_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.research_exit_confirm_body_fmt,
                        cooldownDays
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        viewModel.withdraw { }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.research_exit_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.research_cancel))
                }
            }
        )
    }
}

/* ── 未参与 / 冷却 / 已截止 ───────────────────────────────────────────── */

@Composable
private fun NotParticipatingCard(onJoin: () -> Unit) {
    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Insights,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.research_state_not_participating_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onJoin,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.research_join))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.research_not_participating_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CooldownCard(uiState: ResearchUiState) {
    val daysLeft = ((uiState.cooldownUntil - System.currentTimeMillis() / 1000)
        .coerceAtLeast(0) + 86399) / 86400
    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.research_cooldown_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.research_cooldown_left_fmt, daysLeft),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.research_cooldown_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EndedCard() {
    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.research_ended_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.research_ended_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/* ── 参与中的完整内容 ─────────────────────────────────────────────────── */

@Composable
private fun ParticipatingContent(
    uiState: ResearchUiState,
    viewModel: ResearchViewModel,
    onOpenLegal: () -> Unit,
    onOpenNotices: () -> Unit,
    onExport: () -> Unit,
    onCopyKey: () -> Unit,
    onConfirmExit: () -> Unit
) {
    val context = LocalContext.current

    // 状态卡
    SettingsSubsection(title = stringResource(R.string.research_state_label)) {
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = phaseTitle(uiState),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = phaseTitleColor(uiState),
                        modifier = Modifier.weight(1f),
                    )
                    if (uiState.uploadInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    }
                }
                uiState.licenseKeyMasked?.let { masked ->
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.research_key_label) + "：" + masked,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onCopyKey() },
                        )
                    }
                }
                uiState.uploadError?.let { error ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    // 达标卡（规范 §3：名称 → 进度条 → 数值）
    SettingsSubsection(title = stringResource(R.string.research_progress_title)) {
        ProgressCard(uiState, onUploadClick = { viewModel.uploadNow() })
    }

    // 上传设置
    SettingsSubsection(title = stringResource(R.string.research_section_upload)) {
        SettingsCard {
            Column(modifier = Modifier.padding(16.dp)) {
                SwitchRow(
                    title = stringResource(R.string.research_switch_auto_daily),
                    note = stringResource(R.string.research_switch_auto_daily_note),
                    checked = uiState.autoDaily,
                    onCheckedChange = { viewModel.setAutoDaily(it) }
                )
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    title = stringResource(R.string.research_switch_wifi),
                    note = stringResource(R.string.research_switch_wifi_note),
                    checked = uiState.wifiOnly,
                    onCheckedChange = { viewModel.setWifiOnly(it) }
                )
                Spacer(Modifier.height(12.dp))
                SwitchRow(
                    title = stringResource(R.string.research_plaintext_switch),
                    note = stringResource(R.string.research_plaintext_note),
                    checked = uiState.plaintextEnabled,
                    onCheckedChange = { viewModel.setPlaintextEnabled(it) }
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.research_last_upload) + "：" +
                        formatLastUpload(context, uiState),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // 公告入口
    SettingsSubsection(title = stringResource(R.string.research_section_notices)) {
        SettingsCard(modifier = Modifier.clickable { onOpenNotices() }) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.research_section_notices),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (uiState.hasUnreadNotice) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Icon(
                    Icons.Rounded.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }

    // 协议与隐私
    SettingsCard(modifier = Modifier.clickable { onOpenLegal() }) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.research_legal_agreement_title) + " / " +
                    stringResource(R.string.research_legal_privacy_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }

    // 导出
    SettingsCard(modifier = Modifier.clickable { onExport() }) {
        Text(
            text = stringResource(R.string.research_export),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(16.dp),
        )
    }

    Spacer(Modifier.height(8.dp))

    // 退出并删除数据
    SettingsCard(modifier = Modifier.clickable { onConfirmExit() }) {
        Text(
            text = stringResource(R.string.research_exit),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp),
        )
    }

    Spacer(Modifier.height(6.dp))
    Text(
        text = stringResource(R.string.research_progress_autodaily_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun phaseTitle(uiState: ResearchUiState): String = when (uiState.phase) {
    ResearchPhase.COLLECTING -> stringResource(R.string.research_collecting_title)
    ResearchPhase.READY_FIRST_UPLOAD -> stringResource(R.string.research_ready_title)
    ResearchPhase.ACTIVE -> stringResource(
        R.string.research_active_quota_fmt,
        formatTokensWan(uiState.quotaUsedTokens),
        formatTokensWan(uiState.quotaLimitTokens)
    )
    ResearchPhase.EXPIRING_SOON -> {
        val diffSec = (uiState.expiresAt - System.currentTimeMillis() / 1000).coerceAtLeast(0)
        val days = (diffSec / 86400).toInt()
        val hours = ((diffSec % 86400) / 3600).toInt()
        stringResource(
            R.string.research_expiring_title,
            if (days > 0) stringResource(R.string.research_days_hours_fmt, days, hours)
            else stringResource(R.string.research_hours_fmt, hours)
        )
    }
    ResearchPhase.EXPIRED -> stringResource(R.string.research_expired_title)
    ResearchPhase.QUOTA_EXHAUSTED -> stringResource(R.string.research_quota_exhausted_title)
    ResearchPhase.UPLOAD_FAILED -> stringResource(R.string.research_upload_failed_title)
    else -> stringResource(R.string.research_state_not_participating_title)
}

@Composable
private fun phaseTitleColor(uiState: ResearchUiState): Color = when (uiState.phase) {
    ResearchPhase.EXPIRING_SOON, ResearchPhase.UPLOAD_FAILED -> MaterialTheme.colorScheme.error
    ResearchPhase.EXPIRED -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurface
}

/* ── 达标卡（规范 §3：名称（左）→ 进度条（中）→ 数值（右）） ───────────── */

@Composable
private fun ProgressCard(uiState: ResearchUiState, onUploadClick: () -> Unit) {
    val invalid = uiState.phase == ResearchPhase.EXPIRED
    val historyDone = uiState.collectedEvents >= uiState.minEvents
    val tokensRemaining = uiState.quotaRemainingTokens
    val tokensDone = uiState.quotaLimitTokens > 0 && tokensRemaining > 0
    val doneCount = listOf(historyDone, uiState.firstUploadDone, tokensDone).count { it }

    SettingsCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.research_progress_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Badge(
                    text = when {
                        invalid -> stringResource(R.string.research_progress_badge_invalid)
                        doneCount == 3 -> stringResource(R.string.research_progress_badge_done)
                        else -> stringResource(
                            R.string.research_progress_badge_left_fmt,
                            3 - doneCount
                        )
                    },
                    color = when {
                        invalid -> Color(0xFFE5484D)
                        doneCount == 3 -> MaterialTheme.colorScheme.primary
                        else -> Color(0xFFFFB04A)
                    }
                )
            }
            Spacer(Modifier.height(14.dp))

            // 1 历史记录
            ProgressRow(
                label = stringResource(R.string.research_row_history),
                progress = (uiState.collectedEvents.toFloat() / uiState.minEvents.toFloat())
                    .coerceIn(0f, 1f),
                value = stringResource(
                    R.string.research_row_history_value_fmt,
                    uiState.collectedEvents,
                    uiState.minEvents
                ),
                met = historyDone,
                enabled = !invalid,
            )
            Spacer(Modifier.height(10.dp))

            // 2 首次上传
            ProgressRow(
                label = stringResource(R.string.research_row_first_upload),
                progress = if (uiState.firstUploadDone) 1f else 0f,
                value = stringResource(
                    if (uiState.firstUploadDone) {
                        R.string.research_row_first_done
                    } else {
                        R.string.research_row_first_pending
                    }
                ),
                met = uiState.firstUploadDone,
                enabled = !invalid,
                onClick = if (!uiState.firstUploadDone) onUploadClick else null,
            )
            Spacer(Modifier.height(10.dp))

            // 3 本周期上传
            val periodMet = uiState.lastUploadAt > 0 && uiState.phase in setOf(
                ResearchPhase.ACTIVE, ResearchPhase.QUOTA_EXHAUSTED
            )
            val daysLeft = ((uiState.expiresAt - System.currentTimeMillis() / 1000)
                .coerceAtLeast(0) + 86399) / 86400
            ProgressRow(
                label = stringResource(R.string.research_row_period_upload),
                progress = if (uiState.expiresAt <= 0) 0f
                else (1f - daysLeft / 5f).coerceIn(0f, 1f),
                value = if (uiState.expiresAt <= 0) {
                    stringResource(R.string.research_row_first_pending)
                } else if (uiState.expiresAt > System.currentTimeMillis() / 1000) {
                    stringResource(R.string.research_row_period_left_fmt, daysLeft)
                } else {
                    stringResource(R.string.research_row_first_pending)
                },
                met = periodMet,
                enabled = !invalid,
                onClick = onUploadClick,
            )
            Spacer(Modifier.height(10.dp))

            // 4 本周期 Token
            val tokenProgress = if (uiState.quotaLimitTokens > 0) {
                (uiState.quotaUsedTokens.toFloat() / uiState.quotaLimitTokens).coerceIn(0f, 1f)
            } else 0f
            ProgressRow(
                label = stringResource(R.string.research_row_tokens),
                progress = tokenProgress,
                value = formatTokensWan(uiState.quotaUsedTokens) + " / " +
                    formatTokensWan(uiState.quotaLimitTokens),
                met = tokensDone,
                enabled = !invalid,
                invalid = invalid,
                valueNote = when {
                    invalid -> stringResource(R.string.research_row_tokens_invalid_note)
                    uiState.quotaLimitTokens > 0 &&
                        tokensRemaining < uiState.quotaLimitTokens * 5 / 100 ->
                        stringResource(R.string.research_row_tokens_soon)
                    else -> null
                },
                barColor = when {
                    invalid -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    tokenProgress >= 0.95f -> Color(0xFFE5484D)
                    tokenProgress >= 0.8f -> Color(0xFFFFB04A)
                    else -> MaterialTheme.colorScheme.primary
                },
                onClick = if (invalid) null else onUploadClick,
            )
        }
    }
}

@Composable
private fun ProgressRow(
    label: String,
    progress: Float,
    value: String,
    met: Boolean,
    enabled: Boolean,
    invalid: Boolean = false,
    valueNote: String? = null,
    barColor: Color = if (progress >= 1f) {
        MaterialTheme.colorScheme.primary
    } else {
        Color(0xFFFFB04A)
    },
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (invalid) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(88.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    if (invalid) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = if (met && !invalid) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            valueNote?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/* ── 通用小件 ─────────────────────────────────────────────────────────── */

@Composable
private fun SettingsSubsection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        content()
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun SwitchRow(
    title: String,
    note: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun NoticeBanner(title: String, showDot: Boolean, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Rounded.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/* ── 格式化工具 ───────────────────────────────────────────────────────── */

/** 124000 → "12.4 万"；300000 → "30 万"（万为单位保留 1 位小数，规范 05 §3） */
internal fun formatTokensWan(tokens: Long): String {
    if (tokens <= 0) return "0"
    val wan = tokens / 10000.0
    val formatted = if (wan >= 100 || wan % 1.0 == 0.0) {
        wan.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", wan)
    }
    return "$formatted 万"
}

private fun formatRemaining(expiresAtSec: Long): String {
    val diffSec = expiresAtSec - System.currentTimeMillis() / 1000
    if (diffSec <= 0) return "0"
    val days = diffSec / 86400
    val hours = (diffSec % 86400) / 3600
    return if (days > 0) "${days}d ${hours}h" else "${hours}h"
}

private fun formatLastUpload(context: android.content.Context, uiState: ResearchUiState): String {
    if (uiState.lastUploadAt <= 0) {
        return context.getString(R.string.research_last_upload_never)
    }
    val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return fmt.format(java.util.Date(uiState.lastUploadAt * 1000)) +
        " · " + uiState.lastUploadCount
}
