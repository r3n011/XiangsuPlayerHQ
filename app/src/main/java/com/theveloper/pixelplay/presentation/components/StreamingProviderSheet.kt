package com.theveloper.pixelplay.presentation.components

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.jellyfin.auth.JellyfinLoginActivity
import com.theveloper.pixelplay.presentation.navidrome.auth.NavidromeLoginActivity
import com.theveloper.pixelplay.presentation.netease.auth.NeteaseLoginActivity
import com.theveloper.pixelplay.presentation.qqmusic.auth.QqMusicLoginActivity
import com.theveloper.pixelplay.presentation.telegram.auth.TelegramLoginActivity
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * Bottom sheet that lets the user choose between streaming providers.
 * Uses a segmented Material 3 Expressive list that matches the other
 * bottom sheets in the app while keeping provider order and icon colors intact.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun StreamingProviderSheet(
    onDismissRequest: () -> Unit,
    isNeteaseLoggedIn: Boolean = false,
    onNavigateToNeteaseDashboard: () -> Unit = {},
    isQqMusicLoggedIn: Boolean = false,
    onNavigateToQqMusicDashboard: () -> Unit = {},
    isNavidromeLoggedIn: Boolean = false,
    onNavigateToNavidromeDashboard: () -> Unit = {},
    isJellyfinLoggedIn: Boolean = false,
    onNavigateToJellyfinDashboard: () -> Unit = {},
    // ⚡ 酷狗账号：登录状态 + 打开酷狗登录页 / 已登录时打开账号面板页
    isKugouLoggedIn: Boolean = false,
    onOpenKugouLogin: () -> Unit = {},
    onOpenKugouDashboard: () -> Unit = {},
    // ⚡ 一起听快速入口：显示房间状态，点击打开一起听面板
    isListenTogetherActive: Boolean = false,
    listenTogetherSubtitle: String? = null,
    onOpenListenTogether: () -> Unit = {},
    sheetState: SheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
) {
    val context = LocalContext.current
    val providerSegmentContainerShape = RoundedCornerShape(20.dp)
    val providerSegmentItemShape = RoundedCornerShape(8.dp)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        // ⚡ 入口已经有 8 个（一起听 / Telegram / Drive / Subsonic / Jellyfin / 网易云 /
        //   QQ音乐 / 酷狗），小屏上内容会超出弹窗高度、最后几行被挤出屏幕点不到；
        //   这里让整块内容可上下滑动（弹窗本身仍可下拉关闭）。
        // ⚡ 禁用内容列表的 stretch overscroll：material3（1.5.0-alpha）弹窗里，列表滚到顶部
        //   后继续下拉的位移会先被系统拉伸效果吃掉（列表先"拉丝"），剩下的才交给 sheet，
        //   两个动画互相争抢就是「下拉关闭时抽搐」的来源。关掉 overscroll 后下拉位移
        //   全部直达 sheet 的嵌套滚动，跟手不抖。
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.presentation_batch_g_streaming_title),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = GoogleSansRounded,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.presentation_batch_g_streaming_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(18.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = providerSegmentContainerShape,
                color = Color.Transparent,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .clip(providerSegmentContainerShape),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // ⚡ 一起听（网易云）：置于列表首位，展示房间状态便于快速进入
                    ProviderRow(
                        iconPainter = painterResource(R.drawable.ic_navidrome_md3),
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        title = stringResource(R.string.listen_together_title),
                        subtitle = listenTogetherSubtitle
                            ?: stringResource(R.string.listen_together_desc),
                        shape = providerSegmentItemShape,
                        isConnected = isListenTogetherActive,
                        onClick = {
                            onDismissRequest()
                            onOpenListenTogether()
                        }
                    )

                    if (com.theveloper.pixelplay.BuildConfig.TELEGRAM_ENABLED) {
                        ProviderRow(
                            iconPainter = painterResource(R.drawable.telegram),
                            iconTint = Color(0xFF2AABEE),
                            title = "Telegram",
                            subtitle = stringResource(R.string.streaming_provider_telegram_subtitle),
                            shape = providerSegmentItemShape,
                            onClick = {
                                context.startActivity(Intent(context, TelegramLoginActivity::class.java))
                                onDismissRequest()
                            }
                        )
                    }

                    ProviderRow(
                        iconPainter = painterResource(R.drawable.rounded_drive_export_24),
                        iconTint = Color(0xFF4285F4),
                        title = "Google Drive",
                        subtitle = stringResource(R.string.streaming_provider_coming_soon),
                        shape = providerSegmentItemShape,
                        enabled = false,
                        onClick = { }
                    )

                    ProviderRow(
                        iconPainter = painterResource(R.drawable.ic_navidrome_md3),
                        iconTint = Color(0xFFE8A54B),
                        title = "Subsonic",
                        subtitle = if (isNavidromeLoggedIn) {
                            stringResource(R.string.streaming_provider_subsonic_connected)
                        } else {
                            stringResource(R.string.streaming_provider_subsonic_connect)
                        },
                        shape = providerSegmentItemShape,
                        isConnected = isNavidromeLoggedIn,
                        onClick = {
                            if (isNavidromeLoggedIn) {
                                onNavigateToNavidromeDashboard()
                            } else {
                                context.startActivity(Intent(context, NavidromeLoginActivity::class.java))
                            }
                            onDismissRequest()
                        }
                    )

                    ProviderRow(
                        iconPainter = painterResource(R.drawable.ic_jellyfin),
                        iconTint = Color(0xFF00A4DC),
                        title = "Jellyfin",
                        subtitle = if (isJellyfinLoggedIn) {
                            stringResource(R.string.streaming_provider_connected)
                        } else {
                            stringResource(R.string.streaming_provider_jellyfin_connect)
                        },
                        shape = providerSegmentItemShape,
                        isConnected = isJellyfinLoggedIn,
                        onClick = {
                            if (isJellyfinLoggedIn) {
                                onNavigateToJellyfinDashboard()
                            } else {
                                context.startActivity(Intent(context, JellyfinLoginActivity::class.java))
                            }
                            onDismissRequest()
                        }
                    )

                    ProviderRow(
                        iconPainter = painterResource(R.drawable.netease_cloud_music_logo_icon_206716__1_),
                        iconTint = Color(0xFFE85959),
                        title = "Netease Music",
                        subtitle = if (isNeteaseLoggedIn) {
                            stringResource(R.string.streaming_provider_connected)
                        } else {
                            stringResource(R.string.streaming_provider_sign_in_to_stream)
                        },
                        shape = providerSegmentItemShape,
                        isConnected = isNeteaseLoggedIn,
                        onClick = {
                            if (isNeteaseLoggedIn) {
                                onNavigateToNeteaseDashboard()
                            } else {
                                context.startActivity(Intent(context, NeteaseLoginActivity::class.java))
                            }
                            onDismissRequest()
                        }
                    )

                    ProviderRow(
                        iconPainter = painterResource(R.drawable.qq_music),
                        iconTint = Color(0xFF31C27C),
                        title = "QQ Music",
                        subtitle = if (isQqMusicLoggedIn) {
                            stringResource(R.string.streaming_provider_connected)
                        } else {
                            stringResource(R.string.streaming_provider_sign_in_to_stream)
                        },
                        shape = providerSegmentItemShape,
                        isConnected = isQqMusicLoggedIn,
                        onClick = {
                            if (isQqMusicLoggedIn) {
                                onNavigateToQqMusicDashboard()
                            } else {
                                context.startActivity(Intent(context, QqMusicLoginActivity::class.java))
                            }
                            onDismissRequest()
                        }
                    )

                    // ⚡ 酷狗音乐：显示登录状态，未登录点进酷狗登录页（登录后可拿会员/无损音源 + 同步歌单）
                    ProviderRow(
                        iconPainter = painterResource(R.drawable.ic_kugou),
                        iconTint = Color.Unspecified,
                        iconTileColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        title = stringResource(R.string.kugou_service_name),
                        subtitle = if (isKugouLoggedIn) {
                            stringResource(R.string.streaming_provider_connected)
                        } else {
                            stringResource(R.string.streaming_provider_sign_in_to_stream)
                        },
                        shape = providerSegmentItemShape,
                        isConnected = isKugouLoggedIn,
                        onClick = {
                            if (isKugouLoggedIn) {
                                onOpenKugouDashboard()
                            } else {
                                onOpenKugouLogin()
                            }
                            onDismissRequest()
                        }
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun ProviderRow(
    iconPainter: Painter,
    iconTint: Color,
    title: String,
    subtitle: String,
    shape: RoundedCornerShape,
    isConnected: Boolean = false,
    enabled: Boolean = true,
    /** 图标底色；默认由 iconTint 派生（带品牌色的图标用），传 null 时用 tint 派生色 */
    iconTileColor: Color? = null,
    onClick: () -> Unit
) {
    val containerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerLowest
        isConnected -> MaterialTheme.colorScheme.surfaceContainerHighest
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val titleColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
    }
    val subtitleColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
        isConnected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val arrowContainerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest
        isConnected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceBright
    }
    val arrowTint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        isConnected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    }
    val iconTileShape = RoundedCornerShape(14.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.62f)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
        shape = shape,
        color = containerColor
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Medium,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = subtitleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(iconTileShape)
                        .background(
                            iconTileColor
                                ?: iconTint.copy(alpha = if (enabled) 0.14f else 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = iconTint
                    )
                }
            },
            trailingContent = {
                Surface(
                    shape = CircleShape,
                    color = arrowContainerColor
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(horizontal = 6.dp, vertical = 6.dp)
                            .size(26.dp),
                        tint = arrowTint
                    )
                }
            }
        )
    }
}
