package com.theveloper.pixelplay.presentation.components.listentogether

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.listentogether.ListenTogetherMember
import com.theveloper.pixelplay.data.listentogether.ListenTogetherState
import com.theveloper.pixelplay.presentation.components.SmartImage

/**
 * 一起听成员头像（无头像时退回昵称首字母）。
 */
@Composable
fun ListenTogetherAvatar(
    member: ListenTogetherMember,
    size: Dp,
    modifier: Modifier = Modifier,
    ringColor: Color? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    letterColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val shape = CircleShape
    val initial = remember(member.name) {
        member.name.trim().firstOrNull()?.uppercase() ?: "?"
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .then(
                if (ringColor != null) {
                    Modifier.border(width = size * 0.06f, color = ringColor, shape = shape)
                } else {
                    Modifier
                }
            )
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        val avatarUrl = member.avatarUrl
        if (!avatarUrl.isNullOrBlank()) {
            SmartImage(
                model = avatarUrl,
                contentDescription = stringResource(R.string.listen_together_cd_avatar, member.name),
                modifier = Modifier.fillMaxSize(),
                shape = shape,
            )
        } else {
            Text(
                text = initial,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
                color = letterColor,
                maxLines = 1,
            )
        }
    }
}

/**
 * 播放器内的紧凑头像条：把「我」和一起听对象叠在一起显示，点按查看完整成员列表。
 * 房主离开时改为醒目的"等待房主回来…"。
 */
@Composable
fun ListenTogetherAvatarRow(
    state: ListenTogetherState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 胶囊底色（播放器内传入封面取色，未传则用主题色） */
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    /** 文字/首字母颜色 */
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val room = state.room ?: return
    val members = room.users
    // 自己排最前，随后是一起听对象；最多展示 3 个（再多也看不清）
    val ordered = remember(members, state.selfUserId) {
        val self = members.filter { it.id == state.selfUserId }
        self + members.filter { it.id != state.selfUserId }
    }
    val shown = ordered.take(3)
    val waiting = state.waitingForHost
    val label = when {
        waiting -> stringResource(R.string.listen_together_waiting_host)
        state.otherMembers.size == 1 ->
            stringResource(R.string.listen_together_with, state.otherMembers.first().name)
        else -> stringResource(R.string.listen_together_member_count, members.size)
    }
    val shape = RoundedCornerShape(50)

    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 头像叠放：重叠量固定 8dp，末位之后留 2dp，避免压到文字
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                shown.forEach { member ->
                    ListenTogetherAvatar(
                        member = member,
                        size = 26.dp,
                        ringColor = containerColor,
                        containerColor = containerColor,
                        letterColor = contentColor,
                    )
                }
            }
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 一起听成员弹窗：分别展示「我」与一起听对象的头像 / 昵称 / 房主标识，
 * 并可直接退出房间。
 */
@Composable
fun ListenTogetherMembersDialog(
    state: ListenTogetherState,
    onDismiss: () -> Unit,
    onLeave: () -> Unit,
    /** 快速邀请好友（打开网易云好友选人面板）；null 表示不提供 */
    onInviteFriends: (() -> Unit)? = null,
) {
    val room = state.room ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.listen_together_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.waitingForHost) {
                    Text(
                        text = stringResource(R.string.listen_together_host_left),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                val self = state.selfMember
                if (self != null) {
                    ListenTogetherMemberRow(
                        member = self,
                        isSelf = true,
                        isHost = self.id == room.creatorId,
                    )
                }

                state.otherMembers.forEach { member ->
                    ListenTogetherMemberRow(
                        member = member,
                        isSelf = false,
                        isHost = member.id == room.creatorId,
                    )
                }

            }
        },
        confirmButton = {
            TextButton(onClick = onLeave) {
                Text(
                    text = if (state.isHost) {
                        stringResource(R.string.listen_together_end)
                    } else {
                        stringResource(R.string.listen_together_leave)
                    },
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // ⚡ 快速邀请好友：直接在弹窗里发起邀请，不必先退出播放器
                if (onInviteFriends != null) {
                    TextButton(onClick = onInviteFriends) {
                        Text(stringResource(R.string.chat_invite_friend))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.confirm))
                }
            }
        },
    )
}

@Composable
private fun ListenTogetherMemberRow(
    member: ListenTogetherMember,
    isSelf: Boolean,
    isHost: Boolean,
) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ListenTogetherAvatar(member = member, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    isSelf -> stringResource(R.string.listen_together_me)
                    isHost -> stringResource(R.string.listen_together_host_badge)
                    else -> stringResource(R.string.listen_together_partner)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (isHost) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
