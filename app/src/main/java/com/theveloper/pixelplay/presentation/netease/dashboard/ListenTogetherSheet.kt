package com.theveloper.pixelplay.presentation.netease.dashboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.theveloper.pixelplay.data.database.NeteasePlaylistEntity
import com.theveloper.pixelplay.data.listentogether.ListenTogetherPhase
import com.theveloper.pixelplay.data.listentogether.ListenTogetherState
import com.theveloper.pixelplay.presentation.components.listentogether.ListenTogetherAvatar
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded

/**
 * 网易云一起听面板：
 * - 空闲：从已同步的网易云歌单创建房间 / 粘贴邀请链接加入
 * - 房内：房间成员、连接状态、分享邀请、结束（房主）或退出（成员）
 * 状态由 [ListenTogetherCoordinator] 进程级持有，面板关闭后同步继续。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherSheet(
    state: ListenTogetherState,
    playlists: List<NeteasePlaylistEntity>,
    inviteTextProvider: () -> String?,
    onDismiss: () -> Unit,
    onCreateRoom: (Long) -> Unit,
    onStartRoaming: () -> Unit = {},
    onJoin: (String) -> Boolean,
    onLeave: () -> Unit,
    /** 「分享给好友消息」入口：打开好友选择面板，由用户自己挑要发给谁 */
    onInviteFriend: (() -> Unit)? = null,
    /** 网易云官方短链正在联网还原（显示"正在解析链接…"） */
    resolving: Boolean = false,
    /** ⚡ 从播放器入口打开：直接用当前播放队列开房，不需要先选歌单 */
    startFromCurrentQueue: Boolean = false,
    onCreateRoomFromCurrentQueue: () -> Unit = {}
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.listen_together_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.listen_together_desc),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            val room = state.room
            if (room == null) {
                IdleContent(
                    playlists = playlists,
                    busy = state.phase == ListenTogetherPhase.Connecting,
                    resolving = resolving,
                    onCreateRoom = onCreateRoom,
                    onStartRoaming = onStartRoaming,
                    onJoin = onJoin,
                    startFromCurrentQueue = startFromCurrentQueue,
                    onCreateRoomFromCurrentQueue = onCreateRoomFromCurrentQueue
                )
            } else {
                RoomContent(
                    state = state,
                    inviteText = inviteTextProvider,
                    onShare = { text, context ->
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(Intent.EXTRA_TEXT, text),
                                    context.getString(R.string.listen_together_share)
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    onLeave = onLeave,
                    onInviteFriend = onInviteFriend
                )
            }

            state.lastError?.let { error ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun IdleContent(
    playlists: List<NeteasePlaylistEntity>,
    busy: Boolean,
    resolving: Boolean,
    onCreateRoom: (Long) -> Unit,
    onStartRoaming: () -> Unit,
    onJoin: (String) -> Boolean,
    startFromCurrentQueue: Boolean = false,
    onCreateRoomFromCurrentQueue: () -> Unit = {}
) {
    var selectedPlaylist by remember { mutableStateOf<NeteasePlaylistEntity?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var inviteText by remember { mutableStateOf("") }
    var joinError by remember { mutableStateOf(false) }
    // 是否来自剪贴板自动读取（用于给出提示，而不是静默填好）
    var fromClipboard by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // ⚡ 打开面板时自动读取剪贴板里的一起听邀请链接：
    //    网易云官方分享的是短网址（不带 roomId/inviterId），这里只做识别与填入，
    //    真正的还原（跟随跳转拿长链）由 ViewModel 在点「加入」时联网完成。
    LaunchedEffect(Unit) {
        val clipText = readClipboardText(context) ?: return@LaunchedEffect
        if (inviteText.isNotBlank()) return@LaunchedEffect
        val recognizable = com.theveloper.pixelplay.data.listentogether
            .parseListenTogetherInvitation(clipText) != null ||
            com.theveloper.pixelplay.data.listentogether.looksLikeListenTogetherLink(clipText)
        if (recognizable) {
            inviteText = clipText.trim()
            fromClipboard = true
            joinError = false
        }
    }

    // ── 创建房间卡 ──────────────────────────────────────────────
    ListenTogetherActionCard(
        icon = Icons.Rounded.Groups,
        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
        iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        title = stringResource(R.string.listen_together_create),
        subtitle = stringResource(R.string.listen_together_create_hint)
    ) {
        if (startFromCurrentQueue) {
            // ⚡ 播放器入口：不选歌，直接用当前播放队列（仅网易云歌曲）开房
            Text(
                text = stringResource(R.string.listen_together_create_from_current_hint),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            ListenTogetherPrimaryButton(
                text = stringResource(R.string.listen_together_create_from_current),
                enabled = true,
                busy = busy,
                onClick = onCreateRoomFromCurrentQueue
            )

            Spacer(Modifier.height(8.dp))
        } else if (playlists.isEmpty()) {
            Text(
                text = stringResource(R.string.listen_together_open_dashboard),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // 歌单选择用可点击胶囊行而不是 OutlinedTextField：避免"填表单"的观感
            Box {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = !busy) { expanded = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedPlaylist?.name
                                ?: stringResource(R.string.listen_together_pick_playlist),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = GoogleSansRounded,
                            color = if (selectedPlaylist != null) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    playlists.forEach { playlist ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = playlist.name,
                                    fontFamily = GoogleSansRounded,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                selectedPlaylist = playlist
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            ListenTogetherPrimaryButton(
                text = stringResource(R.string.listen_together_create),
                enabled = selectedPlaylist != null,
                busy = busy,
                onClick = { selectedPlaylist?.let { onCreateRoom(it.id) } }
            )

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onStartRoaming,
                enabled = !busy,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
            ) {
                Icon(
                    Icons.Rounded.Explore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.listen_together_roaming),
                    fontFamily = GoogleSansRounded
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    // ── 加入房间卡 ──────────────────────────────────────────────
    ListenTogetherActionCard(
        icon = Icons.Rounded.Person,
        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
        iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        title = stringResource(R.string.listen_together_join),
        subtitle = stringResource(R.string.listen_together_join_hint)
    ) {
        OutlinedTextField(
            value = inviteText,
            onValueChange = {
                inviteText = it
                joinError = false
                fromClipboard = false
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            isError = joinError,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = GoogleSansRounded),
            placeholder = {
                Text(
                    text = stringResource(R.string.listen_together_paste_link_placeholder),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded
                )
            }
        )
        when {
            joinError -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.listen_together_invite_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.error
                )
            }

            resolving -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.listen_together_resolving_link),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            fromClipboard -> {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.listen_together_clipboard_detected),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        ListenTogetherPrimaryButton(
            text = stringResource(R.string.listen_together_join),
            enabled = inviteText.isNotBlank() && !resolving,
            busy = busy || resolving,
            onClick = {
                fromClipboard = false
                joinError = !onJoin(inviteText)
            }
        )
    }
}

/**
 * 一起听面板的功能卡：与软件其它卡片一致（圆角 22dp + surfaceContainer + tonal 2dp），
 * 头部为圆形图标 + 标题 + 副标题，内容由调用方提供。
 */
@Composable
private fun ListenTogetherActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AbsoluteSmoothCornerShape(22.dp, 60),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = iconContainerColor) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconContentColor,
                        modifier = Modifier
                            .padding(9.dp)
                            .size(22.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            content()
        }
    }
}

/** 全宽主操作按钮：忙碌时按钮内联转圈并禁用，避免额外占位导致布局跳动 */
@Composable
private fun ListenTogetherPrimaryButton(
    text: String,
    enabled: Boolean,
    busy: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = text,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * 读取剪贴板里的文本。
 * Android 10+ 只允许前台应用读取剪贴板，本面板在前台时可用；
 * 无剪贴板 / 非文本 / 为空一律返回 null。
 */
private fun readClipboardText(context: Context): String? = runCatching {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return null
    if (!manager.hasPrimaryClip()) return null
    manager.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(context)
        ?.toString()
}.getOrNull()?.takeIf { it.isNotBlank() }

@Composable
private fun RoomContent(
    state: ListenTogetherState,
    inviteText: () -> String?,
    onShare: (String, android.content.Context) -> Unit,
    onLeave: () -> Unit,
    onInviteFriend: (() -> Unit)? = null
) {
    val room = state.room ?: return
    val context = LocalContext.current
    val statusText = when (state.phase) {
        ListenTogetherPhase.Connected -> stringResource(R.string.listen_together_status_connected)
        ListenTogetherPhase.Connecting -> stringResource(R.string.listen_together_status_connecting)
        ListenTogetherPhase.Reconnecting -> stringResource(R.string.listen_together_status_reconnecting)
        ListenTogetherPhase.Idle -> stringResource(R.string.listen_together_status_connecting)
    }

    Column {
        Text(
            text = "Room #${room.id}",
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = GoogleSansRounded,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.listen_together_members),
            style = MaterialTheme.typography.titleSmall,
            fontFamily = GoogleSansRounded,
            fontWeight = FontWeight.SemiBold
        )
        // ⚡ 房主暂离：网易云没有 waiting 字段，用"房主不在成员列表"推导（见 ListenTogetherState.waitingForHost）
        if (state.waitingForHost) {
            Text(
                text = stringResource(R.string.listen_together_host_left),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = GoogleSansRounded,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
        }
        // 成员列表带头像：自己排最前，随后是一起听对象
        val orderedMembers = room.users.filter { it.id == state.selfUserId } +
            room.users.filter { it.id != state.selfUserId }
        orderedMembers.forEach { member ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ListenTogetherAvatar(member = member, size = 32.dp)
                Text(
                    text = member.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = GoogleSansRounded,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (member.id == state.selfUserId) {
                    Text(
                        text = stringResource(R.string.listen_together_me),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (member.id == room.creatorId) {
                    Text(
                        text = stringResource(R.string.listen_together_host_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // ⚡ 开房后分享邀请：打开好友选择面板，由用户自己挑要发给谁
        //    （不再"一键群发全部好友"）
        if (onInviteFriend != null) {
            ListenTogetherPrimaryButton(
                text = stringResource(R.string.chat_invite_friend),
                enabled = true,
                busy = false,
                onClick = onInviteFriend
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { inviteText()?.let { text -> onShare(text, context) } }) {
                Text(
                    text = stringResource(R.string.listen_together_share),
                    fontFamily = GoogleSansRounded
                )
            }
            TextButton(onClick = onLeave) {
                Text(
                    text = if (state.isHost) {
                        stringResource(R.string.listen_together_end)
                    } else {
                        stringResource(R.string.listen_together_leave)
                    },
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}


