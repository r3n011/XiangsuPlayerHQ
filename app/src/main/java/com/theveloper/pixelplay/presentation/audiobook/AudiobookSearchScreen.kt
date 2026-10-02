package com.theveloper.pixelplay.presentation.audiobook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.kugou.KugouAudiobookAlbum
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import kotlinx.coroutines.delay

/**
 * 听书搜索：输入关键词（防抖 350ms）按书名 / 作者 / 演播者搜专辑，
 * 结果行与免费书库一致（复用歌单列表样式）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookSearchScreen(
    onBack: () -> Unit,
    onOpenAlbum: (KugouAudiobookAlbum) -> Unit,
    viewModel: AudiobookViewModel = hiltViewModel(),
) {
    val search by viewModel.search.collectAsStateWithLifecycle()
    var keyword by rememberSaveable { mutableStateOf("") }

    // 输入防抖：停止输入 350ms 后自动搜索；清空则回到初始提示态
    LaunchedEffect(keyword) {
        if (keyword.isBlank()) {
            viewModel.clearSearch()
            return@LaunchedEffect
        }
        delay(350)
        viewModel.search(keyword)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.audiobook_search_title),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.auth_cd_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.audiobook_search_hint),
                            fontFamily = GoogleSansRounded,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (keyword.isNotEmpty()) {
                            IconButton(onClick = { keyword = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.audiobook_search_clear),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search(keyword) }),
                )

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        search.searching -> CircularProgressIndicator()

                        !search.searched -> AudiobookEmptyState(
                            text = stringResource(R.string.audiobook_search_initial),
                        )

                        search.results.isEmpty() -> AudiobookEmptyState(
                            text = stringResource(R.string.audiobook_search_empty, search.query),
                        )

                        else -> LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(search.results, key = { it.id }) { album ->
                                AudiobookAlbumRow(
                                    album = album,
                                    onClick = { onOpenAlbum(album) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
