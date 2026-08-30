package com.theveloper.pixelplay.presentation.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.github.GitHubRelease
import com.theveloper.pixelplay.data.github.UpdateChecker
import com.theveloper.pixelplay.presentation.components.subcomps.SineWaveLine
import com.theveloper.pixelplay.ui.theme.ExpTitleTypography
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import io.noties.markwon.Markwon
import io.noties.markwon.image.ImagesPlugin
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChangelogBottomSheet(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 从 GitHub 拉取全部 Release 作为更新日志
    val updateChecker = remember { UpdateChecker() }
    var releases by remember { mutableStateOf<List<GitHubRelease>?>(null) } // null = 加载中
    var loadError by remember { mutableStateOf<String?>(null) }
    // 当前展开的版本（默认自动展开最新一版），点击标题行切换
    var expandedVersion by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        updateChecker.fetchReleases().onSuccess { list ->
            releases = list
            expandedVersion = list.firstOrNull()?.tag_name
        }.onFailure { e ->
            loadError = e.message ?: "更新日志加载失败"
            releases = emptyList()
        }
    }

    val fabCornerRadius = 16.dp
    val changelogUrl = "https://github.com/${UpdateChecker.GITHUB_REPO_OWNER}/${UpdateChecker.GITHUB_REPO_NAME}/releases"

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.presentation_batch_g_changelog_title),
                fontFamily = GoogleSansRounded,
                style = ExpTitleTypography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            SineWaveLine(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterHorizontally)
                    .height(32.dp)
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 4.dp),
                animate = true,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                alpha = 0.95f,
                strokeWidth = 4.dp,
                amplitude = 4.dp,
                waves = 7.6f,
                phase = 0f
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                val current = releases
                when {
                    current == null -> {
                        // 加载中
                        item(key = "loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    current.isEmpty() -> {
                        item(key = "empty") {
                            Text(
                                text = loadError
                                    ?: stringResource(R.string.about_changelog_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp)
                            )
                        }
                    }
                    else -> {
                        // 所有版本都展示：默认只展开最新一版，其余显示标题、点击展开详细内容
                        itemsIndexed(current, key = { _, r -> r.tag_name }) { index, release ->
                            ChangelogReleaseItem(
                                release = release,
                                expanded = expandedVersion == release.tag_name,
                                onClick = {
                                    expandedVersion =
                                        if (expandedVersion == release.tag_name) null else release.tag_name
                                }
                            )
                            if (index != current.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }

        MediumExtendedFloatingActionButton(
            onClick = { openUrl(context, changelogUrl) },
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusBR = fabCornerRadius,
                smoothnessAsPercentBR = 60,
                cornerRadiusBL = fabCornerRadius,
                smoothnessAsPercentBL = 60,
                cornerRadiusTR = fabCornerRadius,
                smoothnessAsPercentTR = 60,
                cornerRadiusTL = fabCornerRadius,
                smoothnessAsPercentTL = 60
            ),
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.github),
                    contentDescription = null
                )
            },
            text = { Text(text = stringResource(R.string.presentation_batch_g_changelog_view_github)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(30.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
        ) {
        }
    }
}

@Composable
fun ChangelogReleaseItem(
    release: GitHubRelease,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 标题行整行可点击：展开 / 收起发布说明
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    role = Role.Button
                ) { onClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            VersionBadge(versionNumber = release.tag_name.removePrefix("v"))
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = formatReleaseDate(release.published_at),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }

        AnimatedVisibility(visible = expanded) {
            ReleaseBody(release = release)
        }
    }
}

@Composable
private fun ReleaseBody(release: GitHubRelease) {
    if (release.body.isNullOrBlank()) {
        Text(
            text = stringResource(R.string.about_changelog_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        return
    }
    // 用 Markdown 渲染 GitHub 发布说明（标题/列表/粗体/链接等）
    val context = LocalContext.current
    val markwon = remember {
        Markwon.builder(context)
            .usePlugin(ImagesPlugin.create())
            .build()
    }
    val spannable = remember(release.body) { markwon.toMarkdown(release.body) }
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                textSize = 14f
                movementMethod = LinkMovementMethod.getInstance()
                highlightColor = android.graphics.Color.TRANSPARENT
            }
        },
        update = { tv ->
            tv.setTextColor(textColor)
            tv.setLinkTextColor(linkColor)
            tv.text = spannable
        }
    )
}

@Composable
fun VersionBadge(
    versionNumber: String
) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            )
    ) {
        Text(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 12.dp),
            text = versionNumber,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatReleaseDate(iso: String): String {
    return try {
        Instant.parse(iso.trim()).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    } catch (e: Exception) {
        iso
    }
}

private fun openUrl(context: Context, url: String) {
    val uri = try { url.toUri() } catch (_: Throwable) { return }
    val intent = Intent(Intent.ACTION_VIEW, uri)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
    }
}