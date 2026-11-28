package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.research.ResearchNoticeSummary
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.presentation.viewmodel.ResearchViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 协议 / 隐私条款阅读页（规范 05 §2.1）。
 *
 * 正文来自服务端 /legal/:key（本地缓存兜底，断网可读）；
 * [consentMode]=true 时底部「同意并继续」触发 enroll（CONSENT_REQUIRED 自动重开本页）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchLegalScreen(
    consentMode: Boolean,
    onBack: () -> Unit,
    viewModel: ResearchViewModel = hiltViewModel()
) {
    val agreement by viewModel.legalAgreement.collectAsStateWithLifecycle()
    val privacy by viewModel.legalPrivacy.collectAsStateWithLifecycle()
    val loading by viewModel.legalLoading.collectAsStateWithLifecycle()
    val loadFailed by viewModel.legalLoadFailed.collectAsStateWithLifecycle()
    val legalDiag by viewModel.legalDiag.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(consentMode) {
        viewModel.loadLegalDocuments()
        if (consentMode) viewModel.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.research_legal_agreement_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        bottomBar = {
            if (consentMode) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            // 避让悬浮 mini player（app 级覆盖层，高约 72dp）
                            .padding(bottom = 76.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.consentAndEnroll { ok -> if (ok) onBack() }
                            },
                            enabled = agreement != null && privacy != null && !uiState.uploadInProgress,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            if (uiState.uploadInProgress) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            } else {
                                Text(stringResource(R.string.research_legal_agree))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.research_legal_required_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (loading && agreement == null && privacy == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (loadFailed && agreement == null && privacy == null) {
                // 加载失败（无网络且无缓存）：显式提示 + 重试，不再静默空白
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.research_legal_load_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { viewModel.loadLegalDocuments() }) {
                        Text(stringResource(R.string.research_legal_retry))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        // 底部多留 120dp：避开悬浮 mini player，末段文字不被遮挡
                        .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 120.dp),
                ) {
                    uiState.uploadError?.takeIf { consentMode }?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    if (legalDiag.isNotBlank()) {
                        Text(
                            text = legalDiag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    agreement?.let { doc ->
                        LegalSection(
                            title = doc.title.ifBlank {
                                stringResource(R.string.research_legal_agreement_title)
                            },
                            version = doc.version,
                            content = doc.content,
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(16.dp))
                    }
                    privacy?.let { doc ->
                        LegalSection(
                            title = doc.title.ifBlank {
                                stringResource(R.string.research_legal_privacy_title)
                            },
                            version = doc.version,
                            content = doc.content,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun LegalSection(title: String, version: String, content: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    if (version.isNotBlank()) {
        Spacer(Modifier.height(2.dp))
        Text(
            text = "v$version",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(8.dp))
    // 纯文本分段渲染（规范允许）：不依赖任何 Markdown 渲染器，保证一定出内容
    content.split("\n").forEach { rawLine ->
        val line = rawLine.trimStart('#', ' ', '\t', '>', '-')
        Text(
            text = line.ifBlank { " " },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 20.sp,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}

/**
 * 活动公告独立页（规范 05 §4）：列表 + 详情，置顶在前，已撤回灰标，
 * 离线展示缓存；进入详情即本地标记已读。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchNoticesScreen(
    onBack: () -> Unit,
    viewModel: ResearchViewModel = hiltViewModel()
) {
    val notices by viewModel.notices.collectAsStateWithLifecycle()
    val loading by viewModel.noticesLoading.collectAsStateWithLifecycle()
    val offline by viewModel.noticesOffline.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf<ResearchNoticeSummary?>(null) }

    LaunchedEffect(Unit) { viewModel.loadNotices() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.research_section_notices) +
                            (notices?.let { " · ${it.total}" } ?: "")
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selected != null) selected = null else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        val current = selected
        if (current != null) {
            NoticeDetailContent(notice = current, modifier = Modifier.padding(padding))
            LaunchedEffect(current.id) { viewModel.markNoticeRead(current) }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (offline) {
                    item {
                        Text(
                            text = stringResource(R.string.research_notices_offline),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(notices?.notices.orEmpty(), key = { it.id }) { notice ->
                    NoticeListItem(notice = notice, onClick = { selected = notice })
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f),
                    )
                }
                if ((notices?.notices?.size ?: 0) == 0 && !loading) {
                    item {
                        Text(
                            text = stringResource(R.string.research_notices_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                if (loading) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
                notices?.nextCursor?.takeIf { it.isNotBlank() }?.let { cursor ->
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                            Button(
                                onClick = { viewModel.loadNotices(cursor) },
                                modifier = Modifier.align(Alignment.Center),
                            ) {
                                Text(
                                    stringResource(R.string.research_notices_load_more),
                                    Modifier.padding(horizontal = 12.dp)
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
private fun NoticeListItem(notice: ResearchNoticeSummary, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NoticeTypeTag(notice.type)
            if (notice.pinned) {
                Spacer(Modifier.width(6.dp))
                NoticeTag(
                    text = stringResource(R.string.research_notice_pinned),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (notice.status == "withdrawn") {
                Spacer(Modifier.width(6.dp))
                NoticeTag(
                    text = stringResource(R.string.research_notice_withdrawn),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = notice.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (notice.status == "withdrawn") MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        )
        notice.summary.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        if (notice.publishedAt > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatDate(notice.publishedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoticeDetailContent(notice: ResearchNoticeSummary, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NoticeTypeTag(notice.type)
            notice.version?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "v$it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = notice.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (notice.publishedAt > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatDate(notice.publishedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        // 公告正文只支持纯文本与换行（规范 §6.2：不做 Markdown/HTML）
        notice.content?.takeIf { it.isNotBlank() }?.let { content ->
            content.split("\n").forEach { line ->
                Text(
                    text = line.ifBlank { " " },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        } ?: notice.summary.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = if (notice.status == "withdrawn") {
                stringResource(
                    R.string.research_notice_withdrawn_note_fmt,
                    formatDate(notice.withdrawnAt),
                    notice.withdrawReason.ifBlank { "-" }
                )
            } else {
                stringResource(R.string.research_notice_effective_note)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoticeTypeTag(type: String) {
    val label = when (type) {
        "rule" -> stringResource(R.string.research_notice_type_rule)
        "deadline" -> stringResource(R.string.research_notice_type_deadline)
        else -> stringResource(R.string.research_notice_type_system)
    }
    NoticeTag(text = label, color = MaterialTheme.colorScheme.secondary)
}

@Composable
private fun NoticeTag(text: String, color: androidx.compose.ui.graphics.Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun formatDate(epochSec: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(epochSec * 1000))

/**
 * AI 页额度条（规范 05 §3.1）：仅参与活动时常驻在输入框上方，细进度条 + 剩余量；
 * 数据来自活动页共享的 ResearchRepository（响应头 / 末帧 quota 就地刷新）。
 */
@Composable
fun ResearchQuotaBar(viewModel: ResearchViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val participating = uiState.phase in setOf(
        com.theveloper.pixelplay.data.research.ResearchPhase.ACTIVE,
        com.theveloper.pixelplay.data.research.ResearchPhase.EXPIRING_SOON,
        com.theveloper.pixelplay.data.research.ResearchPhase.QUOTA_EXHAUSTED
    )
    if (!participating) return
    val limit = uiState.quotaLimitTokens
    if (limit <= 0) return
    val remaining = uiState.quotaRemainingTokens
    val ratio = (remaining.toFloat() / limit).coerceIn(0f, 1f)
    val barColor = when {
        ratio < 0.05f -> Color(0xFFE5484D)
        ratio < 0.2f -> Color(0xFFFFB04A)
        else -> MaterialTheme.colorScheme.primary
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.research_quota_bar_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(
                    R.string.research_quota_bar_remaining_fmt,
                    formatTokensWan(remaining)
                ),
                style = MaterialTheme.typography.labelSmall,
                color = barColor,
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                    RoundedCornerShape(2.dp)
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(1f - ratio)
                    .height(4.dp)
                    .background(barColor, RoundedCornerShape(2.dp)),
            )
        }
        if (remaining <= 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.research_quota_exhausted_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
