package com.theveloper.pixelplay.presentation.netease.chat

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.netease.chat.ChatContact
import com.theveloper.pixelplay.data.netease.chat.NeteaseChatRepository
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 「分享歌曲给网易云好友」候选列表状态 */
@HiltViewModel
class ShareSongToFriendViewModel @Inject constructor(
    private val repository: NeteaseChatRepository
) : ViewModel() {

    private val _candidates = MutableStateFlow<List<ChatContact>>(emptyList())
    val candidates: StateFlow<List<ChatContact>> = _candidates.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _sending = MutableStateFlow<Set<Long>>(emptySet())
    val sending: StateFlow<Set<Long>> = _sending.asStateFlow()

    private val _sent = MutableStateFlow<Set<Long>>(emptySet())
    val sent: StateFlow<Set<Long>> = _sent.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    /** 每次打开面板时重新拉取候选（复用一起听邀请的好友候选：关注 + 最近会话去重） */
    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getInviteCandidates()
                .onSuccess { _candidates.value = it }
                .onFailure { _errorEvents.tryEmit(it.message ?: "获取好友列表失败") }
            _isLoading.value = false
        }
    }

    /** 发送歌曲卡片私信 */
    fun sendSong(userId: Long, songId: Long, songTitle: String) {
        if (userId in _sending.value) return
        _sending.update { it + userId }
        viewModelScope.launch {
            repository.sendSong(userId, songId, message = songTitle)
                .onSuccess {
                    _sent.update { it + userId }
                    _errorEvents.tryEmit("已分享")
                }
                .onFailure { _errorEvents.tryEmit(it.message ?: "分享失败") }
            _sending.update { it - userId }
        }
    }
}

/**
 * 「分享歌曲给网易云好友」选人面板：选中好友后把歌曲以资源卡片私信发出去。
 *
 * 与 [FriendPickerSheet]（一起听邀请）共用候选列表与交互结构，
 * 区别只在于发送的是歌曲卡片而不是邀请文本。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSongToFriendSheet(
    songId: Long,
    songTitle: String,
    onDismiss: () -> Unit,
    viewModel: ShareSongToFriendViewModel = hiltViewModel()
) {
    val candidates by viewModel.candidates.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val sending by viewModel.sending.collectAsStateWithLifecycle()
    val sent by viewModel.sent.collectAsStateWithLifecycle()

    // ⚡ 加载/发送失败不再静默：以 Toast 提示，避免"点了没反应"的不稳定观感
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.errorEvents.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) { viewModel.load() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Groups,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.player_share_to_friend_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = songTitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(4.dp))

            when {
                isLoading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }

                candidates.isEmpty() -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.chat_empty_friends),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = GoogleSansRounded,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                ) {
                    items(candidates, key = { it.userId }) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Avatar(url = contact.avatarUrl, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = contact.nickname,
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = GoogleSansRounded,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(
                                onClick = { viewModel.sendSong(contact.userId, songId, songTitle) },
                                enabled = contact.userId !in sending
                            ) {
                                when {
                                    contact.userId in sent -> Text(
                                        text = stringResource(R.string.player_share_to_friend_sent),
                                        fontFamily = GoogleSansRounded
                                    )

                                    else -> Text(
                                        text = stringResource(R.string.action_share),
                                        fontFamily = GoogleSansRounded
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
