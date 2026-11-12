package com.theveloper.pixelplay.presentation.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.plugin.XSPluginRecord
import com.theveloper.pixelplay.data.plugin.XSStorePlugin
import com.theveloper.pixelplay.data.plugin.XS_HIGH_RISK_PERMISSIONS
import com.theveloper.pixelplay.data.plugin.XS_PERMISSION_LABELS
import com.theveloper.pixelplay.data.plugin.XsItemState
import com.theveloper.pixelplay.data.plugin.XsManifestValidator
import com.theveloper.pixelplay.presentation.plugin.XsPluginWebActivity
import com.theveloper.pixelplay.presentation.components.ExpressiveButtonGroup
import com.theveloper.pixelplay.presentation.components.PixelAlertDialog
import com.theveloper.pixelplay.presentation.viewmodel.PluginViewModel
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * 插件中心：已安装 / 商店 / 详情 / 协议门槛 / 指令坞。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginCenterScreen(
    onBackClick: () -> Unit,
    viewModel: PluginViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val storePlugins by viewModel.storePlugins.collectAsStateWithLifecycle()
    val storeLoading by viewModel.storeLoading.collectAsStateWithLifecycle()
    val storeError by viewModel.storeError.collectAsStateWithLifecycle()
    val itemStates by viewModel.itemStates.collectAsStateWithLifecycle()
    val storeAgreed by viewModel.storeAgreed.collectAsStateWithLifecycle()
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()

    var tab by remember { mutableIntStateOf(0) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var showStoreAgreement by remember { mutableStateOf(false) }
    var pendingPluginAgreement by remember { mutableStateOf<String?>(null) }
    var pendingEnableAfterAgreement by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.installFromUri(uri)
    }

    LaunchedEffect(Unit) {
        if (!viewModel.storeAgreed.value) showStoreAgreement = true
        viewModel.refreshStore()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_category_plugins_title),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    FilledIconButton(
                        modifier = Modifier.padding(start = 6.dp),
                        onClick = onBackClick,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.auth_cd_back))
                    }
                },
                actions = {
                    if (tab == 1) {
                        FilledIconButton(
                            modifier = Modifier.padding(end = 6.dp),
                            onClick = { viewModel.refreshStore() },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        ) {
                            Icon(Icons.Rounded.Refresh, stringResource(R.string.plugin_refresh))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ExpressiveButtonGroup(
                items = listOf(
                    stringResource(R.string.plugin_tab_installed),
                    stringResource(R.string.plugin_tab_store),
                ),
                selectedIndex = tab,
                onItemClick = { tab = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )

            if (tab == 0) {
                InstalledTab(
                    installed = installed,
                    onOpen = { detailId = it },
                    onEnableChange = { id, enabled ->
                        if (enabled) {
                            val record = installed.firstOrNull { it.id == id }
                            val needsAgreement = record?.manifest?.agreement != null && record.agreed.not()
                            if (needsAgreement) {
                                pendingPluginAgreement = id
                                pendingEnableAfterAgreement = true
                            } else {
                                if (record?.manifest?.agreement != null && !record.agreed) {
                                    viewModel.agree(id)
                                }
                                viewModel.enable(id)
                            }
                        } else {
                            viewModel.disable(id)
                        }
                    },
                )
            } else {
                StoreTab(
                    plugins = storePlugins,
                    loading = storeLoading,
                    error = storeError,
                    itemStates = itemStates,
                    localVersion = { viewModel.localVersion(it) },
                    hasUpdate = { viewModel.hasUpdate(it) },
                    onInstall = { plugin ->
                        if (!viewModel.storeAgreed.value) {
                            showStoreAgreement = true
                        } else {
                            viewModel.install(plugin)
                        }
                    },
                    onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                )
            }
        }
    }

    // 商店协议
    if (showStoreAgreement) {
        XsAgreementDialog(
            title = stringResource(R.string.plugin_store_agreement_title),
            tabs = listOf(
                stringResource(R.string.plugin_terms) to (viewModel.storeTerms() ?: ""),
                stringResource(R.string.plugin_privacy) to (viewModel.storePrivacy() ?: ""),
            ),
            permissions = emptyList(),
            onAgree = {
                viewModel.setStoreAgreed(true)
                showStoreAgreement = false
            },
            onDismiss = { showStoreAgreement = false },
        )
    }

    // 插件单独协议
    pendingPluginAgreement?.let { pluginId ->
        val record = installed.firstOrNull { it.id == pluginId }
        val text = remember(pluginId) { viewModel.agreementText(pluginId).orEmpty() }
        if (record != null) {
            XsAgreementDialog(
                title = record.manifest.name,
                tabs = listOf(stringResource(R.string.plugin_agreement_title) to text),
                permissions = record.permissionsList(),
                onAgree = {
                    viewModel.agree(pluginId)
                    if (pendingEnableAfterAgreement) viewModel.enable(pluginId)
                    pendingPluginAgreement = null
                    pendingEnableAfterAgreement = false
                },
                onDismiss = {
                    pendingPluginAgreement = null
                    pendingEnableAfterAgreement = false
                },
            )
        }
    }

    detailId?.let { id ->
        val record = installed.firstOrNull { it.id == id }
        if (record == null) {
            detailId = null
        } else {
            PluginDetailSheet(
                record = record,
                receipts = receipts[id].orEmpty(),
                onDismiss = { detailId = null },
                onEnableChange = { enabled ->
                    if (enabled) {
                        val needsAgreement = record.manifest.agreement != null && !record.agreed
                        if (needsAgreement) {
                            pendingPluginAgreement = id
                            pendingEnableAfterAgreement = true
                        } else {
                            if (record.manifest.agreement != null && !record.agreed) viewModel.agree(id)
                            viewModel.enable(id)
                        }
                    } else {
                        viewModel.disable(id)
                    }
                },
                onAction = { actionId -> viewModel.runAction(id, actionId) },
                onClearData = { viewModel.clearData(id) },
                onUninstall = {
                    viewModel.uninstall(id)
                    detailId = null
                },
                onOpenPanel = {
                    context.startActivity(
                        Intent(context, XsPluginWebActivity::class.java)
                            .putExtra(XsPluginWebActivity.EXTRA_PLUGIN_ID, id)
                    )
                },
            )
        }
    }
}

// ── 已安装 ──────────────────────────────────────────────────────────────

@Composable
private fun InstalledTab(
    installed: List<XSPluginRecord>,
    onOpen: (String) -> Unit,
    onEnableChange: (String, Boolean) -> Unit,
) {
    if (installed.isEmpty()) {
        EmptyHint(stringResource(R.string.plugin_empty_installed))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(installed, key = { it.id }) { record ->
            PluginRow(
                name = record.manifest.name,
                version = record.version,
                description = record.manifest.description.orEmpty(),
                enabled = record.enabled,
                onOpen = { onOpen(record.id) },
                onEnableChange = { onEnabledChange -> onEnableChange(record.id, onEnabledChange) },
            )
        }
    }
}

// ── 商店 ────────────────────────────────────────────────────────────────

@Composable
private fun StoreTab(
    plugins: List<XSStorePlugin>,
    loading: Boolean,
    error: String?,
    itemStates: Map<String, XsItemState>,
    localVersion: (String) -> String?,
    hasUpdate: (XSStorePlugin) -> Boolean,
    onInstall: (XSStorePlugin) -> Unit,
    onImport: () -> Unit,
) {
    if (loading && plugins.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (error != null && plugins.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(error, color = MaterialTheme.colorScheme.error, fontFamily = GoogleSansRounded)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onImport) {
                Text(stringResource(R.string.plugin_import_local), fontFamily = GoogleSansRounded)
            }
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.plugin_store_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = GoogleSansRounded,
            )
        }
        items(plugins, key = { it.id }) { plugin ->
            val state = itemStates[plugin.id]
            val local = localVersion(plugin.id)
            StoreRow(
                plugin = plugin,
                state = state,
                localVersion = local,
                hasUpdate = hasUpdate(plugin),
                onInstall = { onInstall(plugin) },
            )
        }
        item {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.plugin_import_local), fontFamily = GoogleSansRounded)
            }
        }
    }
}

// ── 行组件 ──────────────────────────────────────────────────────────────

@Composable
private fun PluginRow(
    name: String,
    version: String,
    description: String,
    enabled: Boolean,
    onOpen: () -> Unit,
    onEnableChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Extension, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "v$version",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = GoogleSansRounded,
                    )
                }
                if (description.isNotBlank()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        fontFamily = GoogleSansRounded,
                    )
                }
            }
            Switch(checked = enabled, onCheckedChange = onEnableChange)
        }
    }
}

@Composable
private fun StoreRow(
    plugin: XSStorePlugin,
    state: XsItemState?,
    localVersion: String?,
    hasUpdate: Boolean,
    onInstall: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Extension, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = plugin.name,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "v${plugin.version}" + localVersion?.let { " · 已安装 v$it" }.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = GoogleSansRounded,
                    )
                }
                StoreActionButton(state, plugin, localVersion, hasUpdate, onInstall)
            }
            if (plugin.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = plugin.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = GoogleSansRounded,
                )
            }
            if (state is XsItemState.Downloading) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun StoreActionButton(
    state: XsItemState?,
    plugin: XSStorePlugin,
    localVersion: String?,
    hasUpdate: Boolean,
    onInstall: () -> Unit,
) {
    when (state) {
        is XsItemState.Downloading -> FilledTonalButton(onClick = {}, enabled = false) {
            Text("${(state.progress * 100).toInt()}%", fontFamily = GoogleSansRounded)
        }
        is XsItemState.Verifying, is XsItemState.Installing -> FilledTonalButton(onClick = {}, enabled = false) {
            Text(stringResource(R.string.plugin_installing), fontFamily = GoogleSansRounded)
        }
        is XsItemState.Failed -> Button(onClick = onInstall) {
            Text(stringResource(R.string.plugin_retry), fontFamily = GoogleSansRounded)
        }
        else -> {
            if (localVersion != null && !hasUpdate) {
                FilledTonalButton(onClick = {}, enabled = false) {
                    Text(stringResource(R.string.plugin_installed), fontFamily = GoogleSansRounded)
                }
            } else {
                Button(onClick = onInstall) {
                    Text(
                        text = if (hasUpdate) stringResource(R.string.plugin_update)
                        else stringResource(R.string.plugin_install),
                        fontFamily = GoogleSansRounded,
                    )
                }
            }
        }
    }
}

// ── 详情 ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PluginDetailSheet(
    record: XSPluginRecord,
    receipts: List<com.theveloper.pixelplay.data.plugin.XSPluginEvent>,
    onDismiss: () -> Unit,
    onEnableChange: (Boolean) -> Unit,
    onAction: (String) -> Unit,
    onClearData: () -> Unit,
    onUninstall: () -> Unit,
    onOpenPanel: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
        ) {
            // ── Hero 卡片 ──
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Extension,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = record.manifest.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                fontFamily = GoogleSansRounded,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "v${record.version} · ${record.manifest.author?.name ?: "XIANGSUDEV"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = GoogleSansRounded,
                            )
                        }
                        StatusPill(enabled = record.enabled)
                    }

                    val caps = record.manifest.capabilities.map { it.id }.distinct()
                    if (caps.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            caps.forEach { CapabilityChip(it) }
                        }
                    }

                    record.manifest.description?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, fontFamily = GoogleSansRounded)
                    }
                    record.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            fontFamily = GoogleSansRounded,
                        )
                    }
                }
            }

            // ── 统计卡 ──
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(
                    icon = Icons.Rounded.Verified,
                    value = if (record.agreed) stringResource(R.string.plugin_stat_agreed) else stringResource(R.string.plugin_stat_not_agreed),
                    label = stringResource(R.string.plugin_stat_agreement),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    icon = Icons.Rounded.Bolt,
                    value = record.manifest.playerActions.size.toString(),
                    label = stringResource(R.string.plugin_stat_actions),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    icon = Icons.Rounded.Forum,
                    value = receipts.size.toString(),
                    label = stringResource(R.string.plugin_stat_receipts),
                    modifier = Modifier.weight(1f),
                )
            }

            // ── 运行开关 / 面板 ──
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(
                                if (record.enabled) R.string.plugin_state_running else R.string.plugin_state_disabled
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = GoogleSansRounded,
                        )
                        Text(
                            text = stringResource(R.string.plugin_toggle_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = GoogleSansRounded,
                        )
                    }
                    Switch(checked = record.enabled, onCheckedChange = onEnableChange)
                }
            }
            if (record.manifest.ui != null) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onOpenPanel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Extension, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.plugin_open_panel), fontFamily = GoogleSansRounded)
                }
            }

            // ── 指令坞 ──
            if (record.manifest.playerActions.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                SectionHeader(
                    title = stringResource(R.string.plugin_dock_title),
                    hint = stringResource(R.string.plugin_dock_hint),
                    count = record.manifest.playerActions.size,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        record.manifest.playerActions.forEachIndexed { index, action ->
                            ActionDockRow(
                                title = action.title.ifBlank { action.id },
                                onRun = { onAction(action.id) },
                            )
                            if (index < record.manifest.playerActions.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                )
                            }
                        }
                    }
                }
            }

            // ── 最近回执 ──
            Spacer(Modifier.height(18.dp))
            SectionHeader(
                title = stringResource(R.string.plugin_receipts),
                hint = null,
                count = receipts.size,
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (receipts.isEmpty()) {
                    Text(
                        text = stringResource(R.string.plugin_no_receipts),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = GoogleSansRounded,
                        modifier = Modifier.padding(16.dp),
                    )
                } else {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        receipts.takeLast(8).reversed().forEach { event ->
                            ReceiptRow(event)
                        }
                    }
                }
            }

            // ── 底部操作 ──
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClearData, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.plugin_clear_data), fontFamily = GoogleSansRounded)
                }
                OutlinedButton(onClick = onUninstall, modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.plugin_uninstall),
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = GoogleSansRounded,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(enabled: Boolean) {
    val color = if (enabled) Color(0xFF4ADE80) else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = stringResource(
                if (enabled) R.string.plugin_status_online else R.string.plugin_status_offline
            ),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontFamily = GoogleSansRounded,
        )
    }
}

@Composable
private fun CapabilityChip(id: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = capabilityIcon(id),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = capabilityLabel(id),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = GoogleSansRounded,
        )
    }
}

@Composable
private fun StatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = GoogleSansRounded,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = GoogleSansRounded,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, hint: String?, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = GoogleSansRounded,
        )
        if (hint != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = GoogleSansRounded,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = GoogleSansRounded,
        )
    }
}

@Composable
private fun ActionDockRow(title: String, onRun: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRun)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GoogleSansRounded,
            )
            Text(
                text = stringResource(R.string.plugin_action_once),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = GoogleSansRounded,
            )
        }
        FilledTonalIconButton(onClick = onRun, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun ReceiptRow(event: com.theveloper.pixelplay.data.plugin.XSPluginEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = event.kind.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(64.dp),
            fontFamily = GoogleSansRounded,
        )
        Text(
            text = event.message,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            fontFamily = GoogleSansRounded,
        )
    }
}

@Composable
private fun capabilityLabel(id: String): String = stringResource(
    when (id) {
        "media" -> R.string.plugin_cap_media
        "library" -> R.string.plugin_cap_library
        "notification" -> R.string.plugin_cap_notification
        "storage" -> R.string.plugin_cap_storage
        "share" -> R.string.plugin_cap_share
        "ai" -> R.string.plugin_cap_ai
        "theme" -> R.string.plugin_cap_theme
        "network" -> R.string.plugin_cap_network
        "lyrics" -> R.string.plugin_cap_lyrics
        "cast" -> R.string.plugin_cap_cast
        "device-bridge" -> R.string.plugin_cap_device
        "music-source" -> R.string.plugin_cap_source
        "clipboard" -> R.string.plugin_cap_clipboard
        "navigation" -> R.string.plugin_cap_navigation
        else -> R.string.plugin_cap_other
    }
)

private fun capabilityIcon(id: String) = when (id) {
    "media" -> Icons.Rounded.GraphicEq
    "library" -> Icons.Rounded.LibraryMusic
    "notification" -> Icons.Rounded.Notifications
    "storage" -> Icons.Rounded.Storage
    "share" -> Icons.Rounded.Share
    "ai" -> Icons.Rounded.AutoAwesome
    "theme" -> Icons.Rounded.Palette
    "network" -> Icons.Rounded.Cloud
    "lyrics" -> Icons.Rounded.Article
    "cast" -> Icons.Rounded.Cast
    "device-bridge" -> Icons.Rounded.Devices
    "music-source" -> Icons.Rounded.MusicNote
    "clipboard" -> Icons.Rounded.ContentCopy
    "navigation" -> Icons.Rounded.OpenInNew
    else -> Icons.Rounded.Extension
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = GoogleSansRounded,
    )
    Spacer(Modifier.height(6.dp))
}

// ── 协议弹窗 ────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun XsAgreementDialog(
    title: String,
    tabs: List<Pair<String, String>>,
    permissions: List<String>,
    onAgree: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tabIndex by remember { mutableIntStateOf(0) }
    PixelAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontFamily = GoogleSansRounded, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (tabs.size > 1) {
                    ExpressiveButtonGroup(
                        items = tabs.map { it.first },
                        selectedIndex = tabIndex,
                        onItemClick = { tabIndex = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (permissions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.plugin_permissions),
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = GoogleSansRounded,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        permissions.forEach { permission ->
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        text = XS_PERMISSION_LABELS[permission] ?: permission,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = GoogleSansRounded,
                                    )
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = tabs.getOrNull(tabIndex)?.second.orEmpty().toPlainMarkdown(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    modifier = Modifier
                        .height(320.dp)
                        .verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onAgree) {
                Text(stringResource(R.string.plugin_agree), fontFamily = GoogleSansRounded)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.plugin_decline), fontFamily = GoogleSansRounded)
            }
        },
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = GoogleSansRounded,
        )
    }
}

private fun XSPluginRecord.permissionsList(): List<String> =
    manifest.capabilities.flatMap { it.permissions }.distinct()

/** 极简 Markdown → 纯文本（标题/列表/粗体），避免额外依赖。 */
private fun String.toPlainMarkdown(): String = lineSequence()
    .joinToString("\n") { line ->
        line
            .replace(Regex("^#{1,6}\\s*"), "")
            .replace("**", "")
            .replace("__", "")
            .replace(Regex("^\\s*[-*]\\s+"), "· ")
            .trimEnd()
    }
    .trim()
