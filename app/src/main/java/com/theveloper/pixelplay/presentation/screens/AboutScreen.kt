package com.theveloper.pixelplay.presentation.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.theveloper.pixelplay.MainActivity
import dev.chrisbanes.haze.hazeSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.util.lerp as lerpFloat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.github.ApkDownloadInstaller
import com.theveloper.pixelplay.data.github.GitHubContributorService
import com.theveloper.pixelplay.data.github.UpdateChecker
import com.theveloper.pixelplay.presentation.components.CollapsibleCommonTopBar
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight
import com.theveloper.pixelplay.presentation.components.SmartImage
import com.theveloper.pixelplay.presentation.components.UpdateAvailableDialog
import com.theveloper.pixelplay.presentation.navigation.Screen
import com.theveloper.pixelplay.presentation.navigation.navigateSafely
import com.theveloper.pixelplay.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

private data class Contributor(
    val id: String,
    val displayName: String,
    val role: String,
    val detail: String? = null,
    val badge: String? = null,
    val avatarUrl: String? = null,
    @DrawableRes val iconRes: Int? = null,
    val githubUrl: String? = null,
    val telegramUrl: String? = null,
    val contributions: Int? = null,
)

/** 更新检查缓存有效期：5分钟内复用上次请求结果，避免频繁请求 GitHub API（未认证限流 60次/小时） */
private const val UPDATE_CACHE_DURATION_MS = 5 * 60 * 1000L

private val CoreMaintainer = Contributor(
    id = "theovilardo",
    displayName = "Theo Vilardo",
    role = "Creator and maintainer",
    detail = "Building XiangsuPlayer with direct community feedback.",
    avatarUrl = "https://avatars.githubusercontent.com/u/26845343?v=4",
    iconRes = R.drawable.round_developer_board_24,
    githubUrl = "https://github.com/theovilardo",
    telegramUrl = "https://t.me/thevelopersupport",
)

private val PinnedCommunityMembers = listOf(
    Contributor(
        id = "lostf1sh",
        displayName = "@lostf1sh",
        role = "Most active contributor",
        detail = "Has contributed enormously across core features, architecture and reliability.",
        badge = "Top Impact",
        iconRes = R.drawable.rounded_celebration_24,
        githubUrl = "https://github.com/lostf1sh",
    ),
    Contributor(
        id = "cromaguy",
        displayName = "@cromaguy",
        role = "Rhythm developer",
        detail = "Developer of Rhythm (another music app) and key community supporter.",
        badge = "Community Ally",
        iconRes = R.drawable.round_developer_board_24,
        githubUrl = "https://github.com/cromaguy",
    ),
    Contributor(
        id = "colbycabrera",
        displayName = "@ColbyCabrera",
        role = "Early contributor",
        detail = "Helped shape XiangsuPlayer in the first stages of the app.",
        badge = "Early Support",
        iconRes = R.drawable.round_newspaper_24,
        githubUrl = "https://github.com/ColbyCabrera",
    ),
)

private val PinnedAliases = mapOf(
    "cromaguy" to setOf("chroma"),
)

private fun normalizeHandle(handle: String): String {
    return handle.trim().removePrefix("@").lowercase()
}

// AboutTopBar removed, replaced by CollapsibleCommonTopBar

@androidx.annotation.OptIn(UnstableApi::class)
@Suppress("UNUSED_PARAMETER")
@Composable
fun AboutScreen(
    navController: NavController,
    viewModel: PlayerViewModel,
    onNavigationIconClick: () -> Unit,
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    val versionName: String = try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName ?: "N/A"
    } catch (_: Exception) {
        "N/A"
    }

    var contributors by remember { mutableStateOf<List<Contributor>>(emptyList()) }
    var isLoadingContributors by remember { mutableStateOf(true) }
    val githubService = remember { GitHubContributorService() }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var availableUpdate by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    val updateChecker = remember { UpdateChecker() }

    var latestReleaseInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var isLoadingChangelog by remember { mutableStateOf(true) }
    var changelogError by remember { mutableStateOf<String?>(null) }

    // APK 下载状态
    var apkDownloadState by remember { mutableStateOf<ApkDownloadInstaller.DownloadState?>(null) }
    val apkInstaller = remember { ApkDownloadInstaller() }

    // 待安装文件：跳转「安装未知应用」设置页授权后返回时用于重试安装
    var pendingInstallFile by remember { mutableStateOf<File?>(null) }

    // 安装未知应用权限请求：跳转到本应用的设置页，用户返回后自动重试安装
    val installPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        val file = pendingInstallFile
        pendingInstallFile = null
        if (file != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()
            ) {
                apkInstaller.installApk(context, file)
            } else {
                apkDownloadState = ApkDownloadInstaller.DownloadState.Error(
                    context.getString(R.string.update_install_permission_required)
                )
            }
        }
    }

    /**
     * 发起安装：先检查「安装未知应用」权限。
     * - 已授权（或 Android 8.0 以下）→ 直接拉起系统安装器；
     * - 未授权（API 26+）→ 跳转本应用设置页授权，用户返回后自动重试。
     */
    fun startInstall(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallFile = file
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            installPermissionLauncher.launch(intent)
        } else {
            apkInstaller.installApk(context, file)
        }
    }

    // 获取本地版本信息：versionName 用于版本号比较（主判断），lastUpdateTime 用于时间戳兜底
    val (currentVersionName, lastUpdateTime) = remember {
        runCatching {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName.orEmpty() to packageInfo.lastUpdateTime
        }.getOrDefault("" to 0L)
    }

    // 更新检查缓存：记录上次请求时间和结果，5分钟内复用避免重复请求 GitHub API
    var lastCheckTime by remember { mutableStateOf(0L) }
    val updateCacheValid by derivedStateOf {
        lastCheckTime > 0 && System.currentTimeMillis() - lastCheckTime < UPDATE_CACHE_DURATION_MS
    }

    LaunchedEffect(Unit) {
        try {
            val result = githubService.fetchContributors()
            result.onSuccess { githubContributors ->
                contributors = githubContributors
                    .filter { normalizeHandle(it.login) != CoreMaintainer.id }
                    .map { github ->
                        Contributor(
                            id = normalizeHandle(github.login),
                            displayName = "@${github.login}",
                            role = "Community contributor",
                            avatarUrl = github.avatar_url,
                            iconRes = R.drawable.rounded_person_24,
                            githubUrl = github.html_url,
                            contributions = github.contributions,
                        )
                    }
            }
            result.onFailure { exception ->
                Timber.e(exception, "Failed to fetch contributors from GitHub")
                contributors = emptyList()
            }
        } finally {
            isLoadingContributors = false
        }
    }

    val contributorsById = remember(contributors) {
        contributors.associateBy { it.id }
    }

    val spotlightContributors = remember(contributorsById) {
        PinnedCommunityMembers.map { pinned ->
            val primaryMatch = contributorsById[pinned.id]
            val aliasMatch = PinnedAliases[pinned.id]
                ?.firstNotNullOfOrNull { alias -> contributorsById[alias] }
            val match = primaryMatch ?: aliasMatch

            if (match == null) {
                pinned
            } else {
                pinned.copy(
                    avatarUrl = match.avatarUrl ?: pinned.avatarUrl,
                    contributions = match.contributions ?: pinned.contributions,
                    githubUrl = match.githubUrl ?: pinned.githubUrl,
                )
            }
        }
    }

    val excludedIds = remember(spotlightContributors) {
        buildSet {
            add(CoreMaintainer.id)
            spotlightContributors.forEach { spotlight ->
                add(spotlight.id)
                addAll(PinnedAliases[spotlight.id].orEmpty())
            }
        }
    }

    val communityContributors = remember(contributors, excludedIds) {
        contributors.filterNot { it.id in excludedIds }
    }

    val transitionState = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) {
        transitionState.targetState = true
    }
    val transition = rememberTransition(transitionState, label = "AboutAppearTransition")

    val contentAlpha by transition.animateFloat(
        label = "ContentAlpha",
        transitionSpec = { tween(durationMillis = 500) },
    ) { if (it) 1f else 0f }

    val contentOffset by transition.animateDp(
        label = "ContentOffset",
        transitionSpec = { tween(durationMillis = 400, easing = FastOutSlowInEasing) },
    ) { if (it) 0.dp else 40.dp }

    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    /**
     * 统一的更新检查入口。
     * 先从 GitHub API 获取版本信息，然后同步蓝奏云版本（检查版本号是否一致）。
     *
     * @param forceRefresh true=强制发网络请求（手动检查）；false=缓存有效时直接复用
     * @param onResult 回调，传入检查结果（UpdateInfo 或 null）
     */
    suspend fun fetchUpdateInfo(
        forceRefresh: Boolean,
        onResult: (UpdateChecker.UpdateInfo?) -> Unit
    ) {
        // 缓存有效且非强制刷新 → 直接复用缓存结果
        if (!forceRefresh && updateCacheValid) {
            onResult(latestReleaseInfo)
            return
        }
        val result = updateChecker.checkForUpdates()
        result.onSuccess { info ->
            // 同步蓝奏云版本信息
            val syncedInfo = updateChecker.syncLanzouVersions(info)
            latestReleaseInfo = syncedInfo
            lastCheckTime = System.currentTimeMillis()
            onResult(syncedInfo)
        }
        result.onFailure { exception ->
            if (forceRefresh) {
                Timber.e(exception, "Failed to check for updates")
                onResult(null)
            } else {
                // 自动检查失败时静默，保留旧缓存（如有）
                Timber.w(exception, "Auto update check failed, using cached result")
                onResult(latestReleaseInfo)
            }
        }
    }

    fun checkForUpdates() {
        coroutineScope.launch {
            isCheckingUpdate = true
            try {
                fetchUpdateInfo(forceRefresh = true) { info ->
                    if (info != null && info.hasUpdate(currentVersionName, lastUpdateTime)) {
                        availableUpdate = info
                        showUpdateDialog = true
                    } else if (info != null) {
                        android.widget.Toast.makeText(context, R.string.update_no_update, android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(context, R.string.update_check_failed, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } finally {
                isCheckingUpdate = false
            }
        }
    }

    fun startApkDownload(urls: List<String>) {
        coroutineScope.launch {
            apkInstaller.downloadApk(context, urls).collect { state ->
                apkDownloadState = state
                if (state is ApkDownloadInstaller.DownloadState.Downloaded) {
                    apkDownloadState = ApkDownloadInstaller.DownloadState.Installing
                    startInstall(state.file)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        isLoadingChangelog = true
        changelogError = null
        try {
            fetchUpdateInfo(forceRefresh = false) { info ->
                latestReleaseInfo = info
                // 自动检查发现新版本时也弹窗提示
                if (info != null && info.hasUpdate(currentVersionName, lastUpdateTime)) {
                    availableUpdate = info
                    showUpdateDialog = true
                }
            }
            if (latestReleaseInfo == null) {
                changelogError = context.getString(R.string.about_changelog_error)
            }
        } finally {
            isLoadingChangelog = false
        }
    }

    val statusBarHeight = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 170.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction = 1f - (
            (topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)
            ).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                if (
                    !isScrollingDown &&
                    (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)
                ) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch {
                        topBarHeight.snapTo(newHeight)
                    }
                }

                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand =
                lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0
            val targetValue = if (shouldExpand && canExpand) maxTopBarHeightPx else minTopBarHeightPx

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .nestedScroll(nestedScrollConnection)
            .fillMaxSize()
            .graphicsLayer {
                alpha = contentAlpha
                translationY = contentOffset.toPx()
            },
    ) {
        val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }
        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(
                top = currentTopBarHeightDp + 8.dp,
                bottom = MiniPlayerHeight +
                    WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding() + 12.dp,
            ),
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(MainActivity.LocalHazeState.current),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "hero_card") {
                AboutHeroCard(
                    versionName = versionName,
                    onVersionLongPress = {
                        navController.navigateSafely(Screen.EasterEgg.route)
                    },
                    onCheckUpdate = ::checkForUpdates,
                    isCheckingUpdate = isCheckingUpdate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 8.dp),
                )
            }

            item(key = "project_info") {
                ProjectInfoCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                )
            }

            item(key = "qq_group") {
                QQGroupCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                )
            }

            item(key = "maintainer_info") {
                MaintainerInfoCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                )
            }

            item(key = "disclaimer") {
                DisclaimerCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                )
            }

            item(key = "acknowledgements") {
                AcknowledgementsCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 12.dp),
                )
            }

            item(key = "changelog_section_title") {
                AboutSectionHeader(
                    title = "更新日志",
                    subtitle = "Version $versionName",
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            item(key = "changelog_card") {
                ChangelogCard(
                    releaseInfo = latestReleaseInfo,
                    isLoading = isLoadingChangelog,
                    errorMessage = changelogError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            item(key = "maintainer_title") {
                AboutSectionHeader(
                    title = stringResource(R.string.about_maintainer_title),
                    subtitle = stringResource(R.string.about_maintainer_subtitle),
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            item(key = "maintainer_card") {
                ContributorCard(
                    contributor = CoreMaintainer,
                    shape = expressiveListShape(index = 0, count = 1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    showContributionCount = false,
                    onCardClick = CoreMaintainer.githubUrl?.let { url -> { openUrl(context, url) } },
                )
            }

            item(key = "spotlight_title") {
                AboutSectionHeader(
                    title = stringResource(R.string.about_spotlight_title),
                    subtitle = stringResource(R.string.about_spotlight_subtitle),
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            itemsIndexed(
                items = spotlightContributors,
                key = { _, contributor -> "spotlight_${contributor.id}" },
            ) { index, contributor ->
                ContributorCard(
                    contributor = contributor,
                    shape = expressiveListShape(index = index, count = spotlightContributors.size),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = if (index == 0) 0.dp else 3.dp),
                    showContributionCount = true,
                    onCardClick = contributor.githubUrl?.let { url -> { openUrl(context, url) } },
                )
            }

            item(key = "contributors_title") {
                AboutSectionHeader(
                    title = stringResource(R.string.about_contributors_section_title),
                    subtitle = stringResource(R.string.about_contributors_section_subtitle),
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            if (isLoadingContributors) {
                item(key = "contributors_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (communityContributors.isEmpty()) {
                item(key = "contributors_empty") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        shape = expressiveListShape(index = 0, count = 1),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        tonalElevation = 1.dp,
                    ) {
                        Text(
                            text = stringResource(R.string.about_no_contributors),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = communityContributors,
                    key = { _, contributor -> "contributor_${contributor.id}" },
                ) { index, contributor ->
                    ContributorCard(
                        contributor = contributor,
                        shape = expressiveListShape(index = index, count = communityContributors.size),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = if (index == 0) 0.dp else 3.dp),
                        showContributionCount = true,
                        onCardClick = contributor.githubUrl?.let { url -> { openUrl(context, url) } },
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        CollapsibleCommonTopBar(
            title = stringResource(R.string.screen_about),
            collapseFraction = collapseFraction,
            headerHeight = currentTopBarHeightDp,
            onBackClick = onNavigationIconClick,
            expandedTitleStartPadding = 20.dp,
            collapsedTitleStartPadding = 68.dp,
            showBackButton = showBackButton
        )

        if (showUpdateDialog && availableUpdate != null) {
            UpdateAvailableDialog(
                updateInfo = availableUpdate!!,
                downloadState = apkDownloadState,
                onDismiss = {
                    showUpdateDialog = false
                    apkDownloadState = null
                },
                onDownload = { urls ->
                    startApkDownload(urls)
                }
            )
        }
    }
}

@Composable
private fun AboutHeroCard(
    versionName: String,
    onVersionLongPress: () -> Unit,
    onCheckUpdate: () -> Unit,
    isCheckingUpdate: Boolean,
    modifier: Modifier = Modifier,
) {
    val heroShape = AbsoluteSmoothCornerShape(30.dp, 60)
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier,
        shape = heroShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.pixelplay_base_monochrome),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp).size(28.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.about_app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.about_tagline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onVersionLongPress()
                                    },
                                )
                            },
                    ) {
                        Text(
                            text = stringResource(R.string.about_version_format, versionName),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Button(
                        onClick = onCheckUpdate,
                        enabled = !isCheckingUpdate,
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.NewReleases,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.update_check_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                CommunitySignalsRow()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommunitySignalsRow() {
    val labels = listOf(
        stringResource(R.string.about_signal_open_source) to Icons.Rounded.Public,
        stringResource(R.string.about_signal_community_first) to Icons.Rounded.AutoAwesome,
        stringResource(R.string.about_signal_material3) to Icons.Rounded.Palette,
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEach { (label, icon) ->
            Surface(
                shape = AbsoluteSmoothCornerShape(16.dp, 60),
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutSectionHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun ContributorCard(
    contributor: Contributor,
    shape: AbsoluteSmoothCornerShape,
    modifier: Modifier = Modifier,
    showContributionCount: Boolean,
    onCardClick: (() -> Unit)? = null,
) {
    val clickableModifier = if (onCardClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = LocalIndication.current,
            role = Role.Button,
            onClick = onCardClick,
        )
    } else {
        Modifier
    }

    Surface(
        modifier = modifier
            .clip(shape)
            .then(clickableModifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ContributorAvatar(
                name = contributor.displayName,
                avatarUrl = contributor.avatarUrl,
                iconRes = contributor.iconRes ?: R.drawable.rounded_person_24,
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            ) {
                Text(
                    text = contributor.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = contributor.role,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )

                contributor.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    contributor.badge?.let { badge ->
                        ContributorLabel(text = badge)
                    }
                    if (showContributionCount && contributor.contributions != null) {
                        ContributorLabel(
                            text = stringResource(
                                R.string.about_contributions_format,
                                contributor.contributions,
                            ),
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SocialIconButton(
                    painterRes = R.drawable.github,
                    contentDescription = stringResource(R.string.cd_open_github_profile),
                    url = contributor.githubUrl,
                )
                SocialIconButton(
                    painterRes = R.drawable.telegram,
                    contentDescription = stringResource(R.string.cd_open_telegram),
                    url = contributor.telegramUrl,
                )
            }
        }
    }
}

@Composable
private fun ContributorLabel(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ContributorAvatar(
    name: String,
    avatarUrl: String?,
    @DrawableRes iconRes: Int?,
    modifier: Modifier = Modifier,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val iconTint = MaterialTheme.colorScheme.onSurfaceVariant
    val letterBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val letterTint = MaterialTheme.colorScheme.onSurfaceVariant
    val initial = name.removePrefix("@").firstOrNull()?.uppercase() ?: "?"
    var cachedBitmap by remember(avatarUrl) { mutableStateOf<ImageBitmap?>(null) }

    Surface(
        modifier = modifier.size(48.dp),
        shape = CircleShape,
        color = containerColor,
        tonalElevation = 2.dp,
    ) {
        when {
            cachedBitmap != null -> {
                Image(
                    bitmap = cachedBitmap!!,
                    contentDescription = stringResource(R.string.cd_contributor_avatar, name),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            !avatarUrl.isNullOrBlank() -> {
                SmartImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.cd_contributor_avatar, name),
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    contentScale = ContentScale.Crop,
                    placeholderResId = iconRes ?: R.drawable.ic_music_placeholder,
                    errorResId = R.drawable.rounded_broken_image_24,
                    targetSize = Size(96, 96),
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Success) {
                            cachedBitmap = state.result.drawable.toBitmap().asImageBitmap()
                        }
                    },
                )
            }
            iconRes != null -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(letterBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = stringResource(R.string.cd_contributor_icon, name),
                        tint = iconTint,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            else -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(letterBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = initial,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = letterTint,
                    )
                }
            }
        }
    }
}

@Composable
private fun SocialIconButton(
    painterRes: Int,
    contentDescription: String,
    url: String?,
    modifier: Modifier = Modifier,
) {
    if (url.isNullOrBlank()) return
    val context = LocalContext.current
    IconButton(
        onClick = { openUrl(context, url) },
        modifier = modifier.size(40.dp),
    ) {
        Icon(
            painter = painterResource(painterRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun expressiveListShape(index: Int, count: Int): AbsoluteSmoothCornerShape {
    val outer = 22.dp
    val inner = 8.dp

    return when {
        count <= 1 -> AbsoluteSmoothCornerShape(outer, 60)
        index == 0 -> AbsoluteSmoothCornerShape(
            cornerRadiusTL = outer,
            cornerRadiusTR = outer,
            cornerRadiusBL = inner,
            cornerRadiusBR = inner,
            smoothnessAsPercentTL = 60,
            smoothnessAsPercentTR = 60,
            smoothnessAsPercentBL = 60,
            smoothnessAsPercentBR = 60,
        )
        index == count - 1 -> AbsoluteSmoothCornerShape(
            cornerRadiusTL = inner,
            cornerRadiusTR = inner,
            cornerRadiusBL = outer,
            cornerRadiusBR = outer,
            smoothnessAsPercentTL = 60,
            smoothnessAsPercentTR = 60,
            smoothnessAsPercentBL = 60,
            smoothnessAsPercentBR = 60,
        )
        else -> AbsoluteSmoothCornerShape(inner, 60)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProjectInfoCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier,
        shape = AbsoluteSmoothCornerShape(22.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Code,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(10.dp).size(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "关于本项目",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "基于PixelPlayer 开源项目构建的播放器",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QQGroupCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = { openUrl(context, "https://qm.qq.com/q/H0NwnAltuk") },
            ),
        shape = AbsoluteSmoothCornerShape(22.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(10.dp).size(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "用户交流群",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "点击加入群聊【XiangsuPlayer 用户群】",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.cd_open_github_profile),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaintainerInfoCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = AbsoluteSmoothCornerShape(22.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(10.dp).size(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "本项目维护者",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "R3n_011",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DisclaimerCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = AbsoluteSmoothCornerShape(22.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(10.dp).size(28.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "使用声明",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "本软件只提供 JS 加载引擎，用户通过 JS 音源所获取的内容与本项目无关。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AcknowledgementsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val blurProjectUrl = "https://github.com/shiqizhenyes/PixelPlayer/tree/feat/gaussian_blur_effect_playingEqIconV2"
    val miniaudioUrl = "https://github.com/mackron/miniaudio"
    val lanzouApiUrl = "https://github.com/124019/LanzouAPI"

    Surface(
        modifier = modifier,
        shape = AbsoluteSmoothCornerShape(22.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(10.dp).size(28.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.about_acknowledgements_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            AcknowledgementItem(
                description = stringResource(R.string.about_acknowledgements_blur_desc),
                url = blurProjectUrl,
                context = context
            )

            Spacer(modifier = Modifier.height(10.dp))

            AcknowledgementItem(
                description = stringResource(R.string.about_acknowledgements_miniaudio_desc),
                url = miniaudioUrl,
                context = context
            )

            Spacer(modifier = Modifier.height(10.dp))

            AcknowledgementItem(
                description = stringResource(R.string.about_acknowledgements_lanzou_desc),
                url = lanzouApiUrl,
                context = context
            )
        }
    }
}

@Composable
private fun AcknowledgementItem(
    description: String,
    url: String,
    context: android.content.Context
) {
    Column {
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))

        ClickableText(
            text = AnnotatedString(url),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.primary,
            ),
            onClick = {
                openUrl(context, url)
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChangelogCard(
    releaseInfo: UpdateChecker.UpdateInfo?,
    isLoading: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = AbsoluteSmoothCornerShape(22.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                errorMessage != null -> {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
                releaseInfo != null -> {
                    val context = LocalContext.current
                    val dateString = remember(releaseInfo.publishedAt) {
                        formatReleaseDate(context, releaseInfo.publishedAt)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp).size(28.dp),
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = releaseInfo.releaseName.takeIf { it.isNotBlank() } ?: releaseInfo.version,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = dateString,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    val changelogItems = remember(releaseInfo.releaseNotes) {
                        parseReleaseNotes(releaseInfo.releaseNotes)
                    }

                    if (changelogItems.isEmpty()) {
                        Text(
                            text = stringResource(R.string.about_changelog_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    } else {
                        changelogItems.forEach { (prefix, content) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    text = prefix,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(
                                    text = content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseReleaseNotes(body: String): List<Pair<String, String>> {
    val numberedPrefixRegex = Regex("""^\d+[.\)]\s+""")
    return body.lines().mapNotNull { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("- ") -> "•" to trimmed.substring(2)
            trimmed.startsWith("* ") -> "•" to trimmed.substring(2)
            numberedPrefixRegex.containsMatchIn(trimmed) -> {
                val match = numberedPrefixRegex.find(trimmed)!!.value
                "•" to trimmed.removePrefix(match)
            }
            trimmed.isNotBlank() -> "•" to trimmed
            else -> null
        }
    }
}

private fun formatReleaseDate(context: Context, publishedAt: Long): String {
    return if (publishedAt > 0L) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        sdf.format(Date(publishedAt))
    } else {
        context.getString(R.string.about_changelog_unknown_date)
    }
}

private fun openUrl(context: Context, url: String) {
    val uri = try {
        url.toUri()
    } catch (_: Throwable) {
        return
    }

    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // Ignore if no handler is available.
    }
}
