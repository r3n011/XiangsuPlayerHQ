package com.theveloper.pixelplay.presentation.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.presentation.components.MiniPlayerHeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenSourceLicensesScreen(
    onBackClick: () -> Unit,
) {
    val context = LocalContext.current
    val loadError = stringResource(R.string.third_party_notices_load_error)
    val noticeText = remember(context, loadError) {
        runCatching {
            context.assets.open("THIRD_PARTY_NOTICES.md")
                .bufferedReader()
                .use { it.readText() }
        }.getOrElse { loadError }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.open_source_licenses_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        },
    ) { contentPadding ->
        SelectionContainer {
            Text(
                text = noticeText,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = contentPadding.calculateTopPadding(),
                        bottom = contentPadding.calculateBottomPadding() + MiniPlayerHeight,
                        start = 16.dp,
                        end = 16.dp,
                    )
                    .padding(vertical = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
