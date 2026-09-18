package com.theveloper.pixelplay.presentation.components.autoeq

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Earbuds
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HeadsetMic
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.autoeq.AutoEQImportExport
import com.theveloper.pixelplay.data.autoeq.AutoEQProfile
import com.theveloper.pixelplay.data.autoeq.UserAudioDevice
import com.theveloper.pixelplay.presentation.viewmodel.AutoEqViewModel

/**
 * Bottom sheet to manage user audio devices (ported 1:1 from Rhythm,
 * using Material 3 components instead of Rhythm-specific ones).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConfigurationBottomSheet(
    autoEqViewModel: AutoEqViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current

    // States
    val userDevices by autoEqViewModel.userAudioDevices.collectAsState()
    val activeDeviceId by autoEqViewModel.activeAudioDeviceId.collectAsState()
    val autoEQProfiles by autoEqViewModel.autoEQProfiles.collectAsState()
    val currentAutoEQProfile by autoEqViewModel.autoEQProfile.collectAsState()
    val connectedDeviceName by autoEqViewModel.connectedDeviceName.collectAsState()

    var showAddDeviceDialog by remember { mutableStateOf(false) }
    var deviceToEdit by remember { mutableStateOf<UserAudioDevice?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<UserAudioDevice?>(null) }
    var showAutoEQSelector by remember { mutableStateOf(false) }
    var deviceForAutoEQ by remember { mutableStateOf<UserAudioDevice?>(null) }

    // Import/Export states
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val content = AutoEQImportExport.readFromUri(context, it)
            if (content != null) {
                importText = content
            } else {
                Toast.makeText(context, R.string.autoeq_failed_read_file, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Load AutoEQ profiles if not loaded
    LaunchedEffect(Unit) {
        if (autoEQProfiles.isEmpty()) {
            autoEqViewModel.loadAutoEQProfiles()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.primary) },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.autoeq_manage),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.device_configuration_devices_configured, userDevices.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column {
                // Description text
                Text(
                    text = stringResource(R.string.autoeq_configure_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Current device detection hint
                if (!connectedDeviceName.isNullOrBlank()) {
                    val matchedDevice = autoEqViewModel.findMatchingUserDevice(connectedDeviceName!!)

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (matchedDevice != null) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                            }
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (matchedDevice != null) Icons.Rounded.Check else Icons.Rounded.Info,
                                contentDescription = null,
                                tint = if (matchedDevice != null) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (matchedDevice != null) stringResource(R.string.device_configuration_device_recognized) else stringResource(R.string.device_configuration_new_device_detected),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (matchedDevice != null) {
                                        if (matchedDevice.autoEQProfileName != null) {
                                            stringResource(R.string.device_configuration_configured_with, connectedDeviceName!!, matchedDevice.autoEQProfileName)
                                        } else {
                                            stringResource(R.string.device_configuration_configured_no_profile, connectedDeviceName!!)
                                        }
                                    } else {
                                        stringResource(R.string.device_configuration_connected_not_configured, connectedDeviceName!!)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Add / Import / Export buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showAddDeviceDialog = true
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.button_add))
                    }
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showImportDialog = true
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.button_import))
                    }
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showExportDialog = true
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.FileUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.button_export))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Devices List
                if (userDevices.isEmpty()) {
                    // Empty state
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Headphones,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.autoeq_no_devices),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.autoeq_add_prompt),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(userDevices, key = { it.id }) { device ->
                            DeviceCard(
                                device = device,
                                isActive = device.autoEQProfileName == currentAutoEQProfile && currentAutoEQProfile.isNotEmpty(),
                                onSelect = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    autoEqViewModel.setActiveAudioDevice(device)
                                },
                                onEdit = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    deviceToEdit = device
                                    showAddDeviceDialog = true
                                },
                                onDelete = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showDeleteConfirmDialog = device
                                },
                                onConfigureAutoEQ = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    deviceForAutoEQ = device
                                    showAutoEQSelector = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Device Dialog
    if (showAddDeviceDialog) {
        AddEditDeviceDialog(
            existingDevice = deviceToEdit,
            onDismiss = {
                showAddDeviceDialog = false
                deviceToEdit = null
            },
            onSave = { device ->
                autoEqViewModel.saveUserAudioDevice(device)
                showAddDeviceDialog = false
                deviceToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    showDeleteConfirmDialog?.let { device ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text(stringResource(R.string.autoeq_delete_device_title)) },
            text = { Text(stringResource(R.string.deviceconfiguration_delete_confirm, device.name)) },
            confirmButton = {
                Button(
                    onClick = {
                        autoEqViewModel.deleteUserAudioDevice(device.id)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.button_delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text(stringResource(R.string.ui_cancel))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // AutoEQ Profile Selector for Device
    if (showAutoEQSelector && deviceForAutoEQ != null) {
        DeviceAutoEQSelector(
            autoEqViewModel = autoEqViewModel,
            device = deviceForAutoEQ!!,
            onDismiss = {
                showAutoEQSelector = false
                deviceForAutoEQ = null
            },
            onProfileSelected = { profile ->
                // Save profile to device
                val updatedDevice = deviceForAutoEQ!!.copy(
                    autoEQProfileName = profile.name
                )
                autoEqViewModel.saveUserAudioDevice(updatedDevice)

                // Apply the profile immediately
                autoEqViewModel.applyAutoEQProfile(profile)

                // Show feedback
                Toast.makeText(
                    context,
                    context.getString(R.string.device_configuration_applied_profile, profile.name),
                    Toast.LENGTH_SHORT
                ).show()

                showAutoEQSelector = false
                deviceForAutoEQ = null
            }
        )
    }

    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = {
                showImportDialog = false
                importText = ""
                importError = null
            },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(stringResource(R.string.autoeq_import_profile))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.autoeq_import_paste_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = stringResource(R.string.deviceconfigurationbottomsheet_supported_formats_fixedbandeq_text),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedTextField(
                        value = importText,
                        onValueChange = {
                            importText = it
                            importError = null
                        },
                        label = { Text(stringResource(R.string.autoeq_eq_settings_label)) },
                        placeholder = { Text(stringResource(R.string.deviceconfigurationbottomsheet_paste_eq_data_here)) },
                        minLines = 6,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                        isError = importError != null,
                        supportingText = if (importError != null) {
                            { Text(importError!!, color = MaterialTheme.colorScheme.error) }
                        } else null,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilledTonalButton(
                            onClick = { filePickerLauncher.launch("*/*") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.FileUpload, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.text_file), style = MaterialTheme.typography.labelLarge)
                        }
                        FilledTonalButton(
                            onClick = {
                                clipboardManager.primaryClip?.getItemAt(0)?.text?.let {
                                    importText = it.toString()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.text_paste), style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, ("https://autoeq.app").toUri())
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Link, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.deviceconfigurationbottomsheet_open_autoeqapp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedProfiles = AutoEQImportExport.autoDetectAndParse(importText, context.getString(R.string.device_configuration_imported_profile))

                        if (parsedProfiles.isNotEmpty()) {
                            val profile = parsedProfiles.first()
                            autoEqViewModel.applyAutoEQProfile(profile)
                            showImportDialog = false
                            importText = ""
                            importError = null
                            Toast.makeText(context, context.getString(R.string.deviceconfiguration_profile_imported, profile.name), Toast.LENGTH_SHORT).show()
                        } else {
                            importError = context.getString(R.string.device_configuration_parse_error)
                        }
                    },
                    enabled = importText.isNotBlank()
                ) {
                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.button_import))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showImportDialog = false
                        importText = ""
                        importError = null
                    }
                ) {
                    Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_cancel))
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // Export Dialog
    if (showExportDialog) {
        val profileToExport = if (currentAutoEQProfile.isNotEmpty() && currentAutoEQProfile != "None") {
            // Try to find in database
            autoEQProfiles.find { it.name == currentAutoEQProfile }
                ?: run {
                    // AutoEQ profile exists but not in current database, create from current band levels
                    val levels = autoEqViewModel.currentBandLevels.value
                    if (levels.size == 10) {
                        AutoEQProfile(
                            name = currentAutoEQProfile,
                            brand = "",
                            type = "",
                            bands = levels
                        )
                    } else null
                }
        } else {
            // Custom/manual EQ settings
            val levels = autoEqViewModel.currentBandLevels.value
            if (levels.size == 10 && autoEqViewModel.equalizerEnabled.value) {
                AutoEQProfile(
                    name = context.getString(R.string.device_configuration_custom_eq),
                    brand = "",
                    type = context.getString(R.string.device_configuration_custom),
                    bands = levels
                )
            } else null
        }

        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.FileUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(stringResource(R.string.deviceconfigurationbottomsheet_export_eq_profile))
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (profileToExport != null) {
                        val exportText = AutoEQImportExport.generateShareableText(profileToExport)

                        Text(
                            text = stringResource(R.string.bottomsheet_export_eq),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = context.getString(R.string.device_configuration_active_profile_label, profileToExport.name),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = exportText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    clipboardManager.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.deviceconfiguration_clip_label), exportText))
                                    Toast.makeText(context, R.string.deviceconfigurationbottomsheet_copied_to_clipboard, Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.deviceconfigurationbottomsheet_copy), style = MaterialTheme.typography.labelLarge)
                            }
                            FilledTonalButton(
                                onClick = {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.device_configuration_share_subject, profileToExport.name))
                                        putExtra(Intent.EXTRA_TEXT, exportText)
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.device_configuration_share_title)))
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.action_share), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.bottomsheet_no_eq_export),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showExportDialog = false }
                ) {
                    Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.ui_done))
                }
            },
            dismissButton = null,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun DeviceCard(
    device: UserAudioDevice,
    isActive: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConfigureAutoEQ: () -> Unit
) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Device Info Section
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isActive)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getDeviceIcon(device.type),
                        contentDescription = null,
                        tint = if (isActive)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = device.type.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (device.autoEQProfileName != null) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = device.autoEQProfileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        }
                        if (device.monoAudioEnabled) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.settings_mono_audio),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Active Indicator
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = stringResource(R.string.bottomsheet_active_device),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onConfigureAutoEQ,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HeadsetMic,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.license_autoeq_name),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.button_edit),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.button_delete),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun AddEditDeviceDialog(
    existingDevice: UserAudioDevice?,
    onDismiss: () -> Unit,
    onSave: (UserAudioDevice) -> Unit
) {
    var deviceName by remember { mutableStateOf(existingDevice?.name ?: "") }
    var deviceBrand by remember { mutableStateOf(existingDevice?.brand ?: "") }
    var selectedType by remember { mutableStateOf(existingDevice?.type ?: UserAudioDevice.DeviceType.HEADPHONES) }
    var monoAudioEnabled by remember { mutableStateOf(existingDevice?.monoAudioEnabled ?: false) }

    val isEditing = existingDevice != null

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isEditing) Icons.Rounded.Edit else Icons.Rounded.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(if (isEditing) stringResource(R.string.device_configuration_edit_device) else stringResource(R.string.device_configuration_add_device))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text(stringResource(R.string.deviceconfigurationbottomsheet_device_name)) },
                    placeholder = { Text(stringResource(R.string.deviceconfigurationbottomsheet_eg_sony_wh1000xm4)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = deviceBrand,
                    onValueChange = { deviceBrand = it },
                    label = { Text(stringResource(R.string.deviceconfigurationbottomsheet_brand_optional)) },
                    placeholder = { Text(stringResource(R.string.deviceconfigurationbottomsheet_eg_sony)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    text = stringResource(R.string.bottomsheet_device_type),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(UserAudioDevice.DeviceType.entries.toList()) { type ->
                        val isSelected = selectedType == type
                        val cornerRadius by animateDpAsState(
                            targetValue = if (isSelected) 24.dp else 12.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "chipCornerRadius"
                        )
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedType = type },
                            label = { Text(type.displayName) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isSelected) Icons.Rounded.Check else getDeviceIcon(type),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            shape = RoundedCornerShape(cornerRadius),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.device_mono_audio_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.device_mono_audio_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = monoAudioEnabled,
                        onCheckedChange = { monoAudioEnabled = it },
                        colors = SwitchDefaults.colors()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (deviceName.isNotBlank()) {
                        val device = if (isEditing) {
                            existingDevice.copy(
                                name = deviceName,
                                brand = deviceBrand,
                                type = selectedType,
                                monoAudioEnabled = monoAudioEnabled
                            )
                        } else {
                            UserAudioDevice(
                                name = deviceName,
                                brand = deviceBrand,
                                type = selectedType,
                                monoAudioEnabled = monoAudioEnabled
                            )
                        }
                        onSave(device)
                    }
                },
                enabled = deviceName.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isEditing) stringResource(R.string.device_configuration_save) else stringResource(R.string.device_configuration_add))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.ui_cancel))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceAutoEQSelector(
    autoEqViewModel: AutoEqViewModel,
    device: UserAudioDevice,
    onDismiss: () -> Unit,
    onProfileSelected: (AutoEQProfile) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val profiles by autoEqViewModel.autoEQProfiles.collectAsState()
    var searchQuery by remember { mutableStateOf(device.brand.ifEmpty { device.name }) }
    var selectedBrand by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }

    // Extract unique brands and types
    val brands = remember(profiles) {
        profiles.map { it.brand }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    val types = remember(profiles) {
        profiles.map { it.type }.filter { it.isNotEmpty() }.distinct().sorted()
    }

    val filteredProfiles = remember(profiles, searchQuery, selectedBrand, selectedType) {
        var result = profiles

        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            result = result.filter {
                it.name.lowercase().contains(query) ||
                    it.brand.lowercase().contains(query) ||
                    it.type.lowercase().contains(query)
            }
        }

        if (selectedBrand != null) {
            result = result.filter { it.brand == selectedBrand }
        }

        if (selectedType != null) {
            result = result.filter { it.type == selectedType }
        }

        result
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.HeadsetMic,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Column {
                Text(
                    text = stringResource(R.string.deviceconfigurationbottomsheet_select_autoeq_profile),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.device_configuration_for_device, device.name),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                // Enhanced Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.deviceconfigurationbottomsheet_find_a_profile_for)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.ui_clear),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    singleLine = true
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.device_configuration_profiles_available, filteredProfiles.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                // Profile List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        val isNoneSelected = device.autoEQProfileName.isNullOrBlank() || device.autoEQProfileName == "None"
                        EQProfileCard(
                            profile = AutoEQProfile(
                                name = context.getString(R.string.common_none),
                                brand = context.getString(R.string.device_configuration_disable_compensation),
                                type = "",
                                bands = List(10) { 0f }
                            ),
                            isSelected = isNoneSelected,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onProfileSelected(AutoEQProfile("None", "", "", List(10) { 0f }))
                            }
                        )
                    }

                    if (filteredProfiles.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.HeadsetMic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.deviceconfigurationbottomsheet_no_matching_profiles_found),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredProfiles, key = { it.name }) { profile ->
                            EQProfileCard(
                                profile = profile,
                                isSelected = device.autoEQProfileName == profile.name,
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onProfileSelected(profile)
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ui_cancel))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
private fun EQProfileCard(
    profile: AutoEQProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.HeadsetMic,
                    contentDescription = null,
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (isSelected) 30.dp else 26.dp)
                )

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        if (profile.brand.isNotEmpty()) {
                            Text(
                                text = profile.brand,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (profile.brand.isNotEmpty() && profile.type.isNotEmpty()) {
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (profile.type.isNotEmpty()) {
                            Text(
                                text = profile.type,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Selection Indicator
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.streaming_selected),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

private fun getDeviceIcon(type: UserAudioDevice.DeviceType): ImageVector {
    return when (type) {
        UserAudioDevice.DeviceType.HEADPHONES -> Icons.Rounded.Headphones
        UserAudioDevice.DeviceType.EARBUDS -> Icons.Rounded.Earbuds
        UserAudioDevice.DeviceType.IEM -> Icons.Rounded.HeadsetMic
        UserAudioDevice.DeviceType.SPEAKERS -> Icons.Rounded.Speaker
        UserAudioDevice.DeviceType.BLUETOOTH_SPEAKER -> Icons.Rounded.BluetoothAudio
        UserAudioDevice.DeviceType.CAR_AUDIO -> Icons.Rounded.DirectionsCar
        UserAudioDevice.DeviceType.STUDIO_MONITORS -> Icons.Rounded.SpeakerGroup
        UserAudioDevice.DeviceType.OTHER -> Icons.Rounded.AudioFile
    }
}
