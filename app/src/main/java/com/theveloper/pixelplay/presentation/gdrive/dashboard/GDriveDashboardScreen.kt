package com.theveloper.pixelplay.presentation.gdrive.dashboard

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.theveloper.pixelplay.data.database.GDriveFolderEntity
import com.theveloper.pixelplay.presentation.gdrive.auth.GDriveLoginActivity
import com.theveloper.pixelplay.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.theveloper.pixelplay.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GDriveDashboardScreen(
    viewModel: GDriveDashboardViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()

    val cardShape = AbsoluteSmoothCornerShape(
        cornerRadiusTR = 20.dp, cornerRadiusTL = 20.dp,
        cornerRadiusBR = 20.dp, cornerRadiusBL = 20.dp,
        smoothnessAsPercentTR = 60, smoothnessAsPercentTL = 60,
        smoothnessAsPercentBR = 60, smoothnessAsPercentBL = 60
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.auth_gdrive_title),
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.auth_cd_back))
                    }
                },
                actions = {
                    // Add folder button
                    IconButton(onClick = {
                        context.startActivity(Intent(context, GDriveLoginActivity::class.java))
                    }) {
                        Icon(
                            Icons.Rounded.Add,
                            contentDescription = stringResource(R.string.cd_add_drive_folder),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    // Sync button
                    IconButton(
                        onClick = { viewModel.syncAllFoldersAndSongs() },
                        enabled = !isSyncing
                    ) {
                        Icon(
                            Icons.Rounded.CloudSync,
                            contentDescription = stringResource(R.string.cd_sync_all),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    // Logout button
                    IconButton(onClick = {
                        viewModel.logout()
                        onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = stringResource(R.string.cd_logout)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Sync status banner
            AnimatedVisibility(
                visible = syncMessage != null,
                enter = slideInVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                ) + fadeIn(),
                exit = fadeOut()
            ) {
                syncMessage?.let { message ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = cardShape,
                        colors = CardDefaults.cardColors(
                            containerColor = if (message.contains("failed"))
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.width(12.dp))
                            }
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = GoogleSansRounded
                            )
                        }
                    }
                }
            }

            // User info header
            viewModel.userEmail?.let { email ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = cardShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.CloudQueue,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(
                                text = email,
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = GoogleSansRounded,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.dash_folders_synced_count, folders.size),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = GoogleSansRounded,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Folders header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.dash_music_folders),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.Bold
                )
                if (folders.isEmpty()) {
                    TextButton(onClick = { viewModel.syncAllFoldersAndSongs() }) {
                        Icon(
                            Icons.Rounded.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.dash_action_sync), fontFamily = GoogleSansRounded, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            // Folder list
            if (folders.isEmpty() && !isSyncing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.dash_no_folders_yet),
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.dash_tap_plus_add_drive_folder),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = GoogleSansRounded,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = folders,
                        key = { it.id }
                    ) { folder ->
                        GDriveFolderCard(
                            folder = folder,
                            onSyncClick = { viewModel.syncFolder(folder.id) },
                            onDeleteClick = { viewModel.removeFolder(folder.id) },
                            cardShape = cardShape,
                            isSyncing = isSyncing
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GDriveFolderCard(
    folder: GDriveFolderEntity,
    onSyncClick: () -> Unit,
    onDeleteClick: () -> Unit,
    cardShape: AbsoluteSmoothCornerShape,
    isSyncing: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = GoogleSansRounded,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.dash_song_count, folder.songCount),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = GoogleSansRounded,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Sync button
            IconButton(
                onClick = onSyncClick,
                enabled = !isSyncing
            ) {
                Icon(
                    Icons.Rounded.Sync,
                    contentDescription = stringResource(R.string.cd_sync),
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Delete button
            IconButton(onClick = onDeleteClick) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.cd_remove),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
