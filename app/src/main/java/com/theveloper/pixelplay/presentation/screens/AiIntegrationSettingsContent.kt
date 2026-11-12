package com.theveloper.pixelplay.presentation.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.ai.provider.AiProvider
import com.theveloper.pixelplay.presentation.viewmodel.McpBridgeUiState
import com.theveloper.pixelplay.presentation.viewmodel.McpBridgeViewModel
import com.theveloper.pixelplay.presentation.viewmodel.SettingsViewModel

/**
 * AI 集成设置页。
 *
 * 复用其他设置页的同一套视觉语言：SettingsSubsection 分区标题、10dp 圆角的
 * surfaceContainer 卡片、SwitchSettingItem / ThemeSelectorItem。不要在这里另起一套
 * 「hero + 大圆角」的语言，否则会和其他设置页明显割裂。
 */
@Composable
fun AiIntegrationSettingsContent(
    settingsViewModel: SettingsViewModel,
    mcpViewModel: McpBridgeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val aiProvider by settingsViewModel.aiProvider.collectAsStateWithLifecycle()
    val apiKey by settingsViewModel.currentAiApiKey.collectAsStateWithLifecycle()
    val model by settingsViewModel.currentAiModel.collectAsStateWithLifecycle()
    val baseUrl by settingsViewModel.currentAiBaseUrl.collectAsStateWithLifecycle()
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val safeToken by settingsViewModel.isSafeTokenLimitEnabled.collectAsStateWithLifecycle()
    val autoPlaylist by settingsViewModel.isAutoPlaylistEnabled.collectAsStateWithLifecycle()
    val recommendationCard by settingsViewModel.isAiRecommendationCardEnabled.collectAsStateWithLifecycle()
    val manualOnly by settingsViewModel.isAiRecommendationManualOnly.collectAsStateWithLifecycle()
    val companionEnabled by settingsViewModel.isCompanionEnabled.collectAsStateWithLifecycle()
    val companionVoice by settingsViewModel.companionVoice.collectAsStateWithLifecycle()
    val companionSpeed by settingsViewModel.companionSpeed.collectAsStateWithLifecycle()
    val ttsKey by settingsViewModel.mimoTtsApiKey.collectAsStateWithLifecycle()

    val mcpState by mcpViewModel.uiState.collectAsStateWithLifecycle()
    val provider = AiProvider.fromString(aiProvider)

    var showKey by remember { mutableStateOf(false) }

    LaunchedEffect(aiProvider, apiKey) {
        settingsViewModel.loadModelsForCurrentProvider()
    }

    LaunchedEffect(mcpState.message) {
        mcpState.message?.let { text ->
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            mcpViewModel.clearMessage()
        }
    }

    // A 方向配对没有客户端回调，轮询刷新已连接列表。
    LaunchedEffect(mcpState.enabled) {
        if (mcpState.enabled) {
            while (true) {
                kotlinx.coroutines.delay(5_000)
                mcpViewModel.refresh()
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSubsection(title = "当前配置") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = provider.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = when {
                                uiState.isLoadingModels -> "正在获取模型列表…"
                                apiKey.isBlank() -> "未配置 API Key"
                                model.isBlank() -> "已配置，尚未选择模型"
                                else -> model
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (apiKey.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Rounded.Science,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        SettingsSubsection(title = "服务商") {
            ThemeSelectorItem(
                label = "服务商",
                description = provider.displayName,
                options = AiProvider.entries.associate { it.name to it.displayName },
                selectedKey = aiProvider,
                onSelectionChanged = { settingsViewModel.onAiProviderChange(it) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Science,
                        null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                },
            )
        }

        SettingsSubsection(title = "连接凭证") {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "API Key",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (apiKey.isNotBlank()) "已配置" else "未配置",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (apiKey.isNotBlank()) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "只保存在本机；输入完成后自动获取可用模型。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { settingsViewModel.onAiApiKeyChange(it) },
                        placeholder = { Text("粘贴 ${provider.displayName} 的 API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        visualTransformation = if (showKey) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(
                                        imageVector = if (showKey) Icons.Rounded.VisibilityOff
                                        else Icons.Rounded.Visibility,
                                        contentDescription = if (showKey) "隐藏" else "显示",
                                    )
                                }
                                if (apiKey.isNotBlank()) {
                                    IconButton(onClick = { settingsViewModel.onAiApiKeyChange("") }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "清空")
                                    }
                                }
                            }
                        },
                    )
                    if (provider.hasConfigurableUrl) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { settingsViewModel.onAiBaseUrlChange(it) },
                            label = { Text("Base URL（OpenAI 兼容）") },
                            placeholder = { Text("https://api.example.com/v1") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { settingsViewModel.refreshModels() },
                            enabled = !uiState.isLoadingModels,
                        ) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (uiState.isLoadingModels) "正在获取…" else "刷新模型列表")
                        }
                        Spacer(Modifier.weight(1f))
                        if (!uiState.modelsFetchError.isNullOrBlank()) {
                            Text(
                                text = uiState.modelsFetchError!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }

        SettingsSubsection(title = "模型") {
            if (provider.hasConfigurableUrl) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "模型名称",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "手动填写，例如 deepseek-chat、qwen-max",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = model,
                            onValueChange = { settingsViewModel.onAiModelChange(it) },
                            placeholder = { Text("deepseek-chat") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                        )
                    }
                }
            } else if (uiState.availableModels.isNotEmpty()) {
                ThemeSelectorItem(
                    label = "默认模型",
                    description = if (model.isBlank()) "尚未选择，建议选择一个可用模型" else "当前：$model",
                    options = uiState.availableModels.associate { it.name to it.displayName },
                    selectedKey = model,
                    onSelectionChanged = { settingsViewModel.onAiModelChange(it) },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            null,
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    },
                )
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "默认模型",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (apiKey.isBlank()) "填写 API Key 后会自动获取可用模型。"
                            else if (uiState.isLoadingModels) "正在获取模型列表…"
                            else "暂无模型列表，可点击「刷新模型列表」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        SettingsSubsection(title = "生成偏好") {
            SwitchSettingItem(
                title = "安全 Token 限制",
                subtitle = if (safeToken) "限制上下文长度，降低费用与超时" else "允许使用更长的上下文",
                checked = safeToken,
                onCheckedChange = { settingsViewModel.setSafeTokenLimitEnabled(it) },
                leadingIcon = {
                    Icon(Icons.Rounded.Tag, null, tint = MaterialTheme.colorScheme.secondary)
                },
            )
        }

        SettingsSubsection(title = "AI 功能") {
            SwitchSettingItem(
                title = "自动生成歌单",
                subtitle = "根据当前播放与口味自动补充播放队列",
                checked = autoPlaylist,
                onCheckedChange = { settingsViewModel.setAutoPlaylistEnabled(it) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.generate_playlist_ai),
                        null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                },
            )
            SwitchSettingItem(
                title = "首页推荐卡片",
                subtitle = "在首页显示 AI 推荐入口",
                checked = recommendationCard,
                onCheckedChange = { settingsViewModel.setAiRecommendationCardEnabled(it) },
                leadingIcon = {
                    Icon(
                        painterResource(R.drawable.generate_playlist_ai),
                        null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                },
            )
            SwitchSettingItem(
                title = "仅手动触发推荐",
                subtitle = "不自动生成，只有点按钮时才请求 AI",
                checked = manualOnly,
                onCheckedChange = { settingsViewModel.setAiRecommendationManualOnly(it) },
                enabled = recommendationCard,
                leadingIcon = {
                    Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.secondary)
                },
            )
        }

        SettingsSubsection(title = "AI 陪伴") {
            SwitchSettingItem(
                title = "AI DJ 语音陪伴",
                subtitle = "切歌时生成衔接语并用 TTS 播报",
                checked = companionEnabled,
                onCheckedChange = { settingsViewModel.setCompanionEnabled(it) },
                leadingIcon = {
                    Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.secondary)
                },
            )
            AnimatedVisibility(visible = companionEnabled) {
                Column {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "MiMo TTS API Key",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "用于 AI DJ 语音播报，只保存在本机。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = ttsKey,
                                onValueChange = { settingsViewModel.setMimoTtsApiKey(it) },
                                placeholder = { Text("sk-…") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                visualTransformation = PasswordVisualTransformation(),
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    val voices = com.theveloper.pixelplay.data.ai.MiMoTtsClient.BUILT_IN_VOICES
                    ThemeSelectorItem(
                        label = "音色",
                        description = voices.find { it.id == companionVoice }?.displayName ?: companionVoice,
                        options = voices.associate { it.id to it.displayName },
                        selectedKey = companionVoice,
                        onSelectionChanged = { settingsViewModel.setCompanionVoice(it) },
                        leadingIcon = {
                            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.secondary)
                        },
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "语速",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "${"%.2f".format(companionSpeed)}x",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            androidx.compose.material3.Slider(
                                value = companionSpeed,
                                onValueChange = { settingsViewModel.setCompanionSpeed(it) },
                                valueRange = 0.5f..2.0f,
                                steps = 5,
                            )
                        }
                    }
                }
            }
        }

        SettingsSubsection(title = "AI 外接") {
            SwitchSettingItem(
                title = "AI 外接（MCP）",
                subtitle = "点一下打开 AI 客户端，对方确认即可配对",
                checked = mcpState.enabled,
                onCheckedChange = mcpViewModel::setEnabled,
                leadingIcon = {
                    Icon(Icons.Rounded.Link, null, tint = MaterialTheme.colorScheme.secondary)
                },
            )
            if (mcpState.enabled) {
                McpStatusCard(
                    state = mcpState,
                    onRefresh = mcpViewModel::refresh,
                    onConnect = mcpViewModel::connect,
                    onRevoke = mcpViewModel::revoke,
                )
            }
        }

        SettingsSubsection(title = "MiMo 开放平台", addBottomSpace = false) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "新用户福利",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "邀请码 LAGBWT，双方各得 ¥10 体验金。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(
                                        "https://platform.xiaomimimo.com?ref=LAGBWT",
                                    ),
                                )
                            )
                        },
                    ) {
                        Text("去了解")
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    mcpState.pendingInvite?.let { invite ->
        AlertDialog(
            onDismissRequest = mcpViewModel::dismissInvite,
            title = { Text("配对请求") },
            text = {
                Text(
                    "${invite.client?.name ?: "AI 客户端"} 想连接到像素播放器。\n" +
                        "允许后，它可以读取你的曲库、控制播放并创建歌单。",
                )
            },
            confirmButton = {
                TextButton(onClick = { mcpViewModel.approveInvite() }) { Text("允许") }
            },
            dismissButton = {
                TextButton(onClick = mcpViewModel::dismissInvite) { Text("拒绝") }
            },
        )
    }

    mcpState.pendingRequest?.let { request ->
        AlertDialog(
            onDismissRequest = mcpViewModel::denyRequest,
            title = { Text("设备请求连接") },
            text = {
                Text("${request.clientName} 请求连接到本机。\n允许后它可以读取曲库并控制播放。")
            },
            confirmButton = {
                TextButton(onClick = { mcpViewModel.approveRequest() }) { Text("允许") }
            },
            dismissButton = {
                TextButton(onClick = mcpViewModel::denyRequest) { Text("拒绝") }
            },
        )
    }
}

@Composable
private fun McpStatusCard(
    state: McpBridgeUiState,
    onRefresh: () -> Unit,
    onConnect: (com.theveloper.pixelplay.data.mcp.CompatibleClient) -> Unit,
    onRevoke: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (state.running) Color(0xFF32C759)
                            else MaterialTheme.colorScheme.outline,
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (state.running) {
                        "运行中 · ${state.endpoint ?: "获取地址中…"}"
                    } else {
                        "服务未运行，关闭开关后重试"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "刷新", Modifier.size(18.dp))
                }
            }

            if (state.availableClients.isEmpty()) {
                Text(
                    text = "未检测到兼容的 AI 客户端。客户端需声明 com.pixelplayer.mcp.CLIENT。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                state.availableClients.forEach { client ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = client.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        FilledTonalButton(onClick = { onConnect(client) }) {
                            Text("连接")
                        }
                    }
                }
            }

            val activeClients = state.pairedClients.filterNot { it.revoked }
            if (activeClients.isNotEmpty()) {
                Text(
                    text = "已连接",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                activeClients.forEach { record ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(record.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "${record.scopes.size} 项权限",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onRevoke(record.id) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "断开")
                        }
                    }
                }
            }
        }
    }
}
