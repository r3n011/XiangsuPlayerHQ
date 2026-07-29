package com.theveloper.pixelplay.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.service.usb.UsbDeviceInfo
import com.theveloper.pixelplay.data.service.usb.UsbDacManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch

/**
 * USB 设备选择对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsbDeviceSelectorDialog(
    onDismiss: () -> Unit,
    onDeviceSelected: (UsbDeviceInfo) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    
    val sheetState = rememberModalBottomSheetState()
    var devices by remember { mutableStateOf<List<UsbDeviceInfo>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<UsbDeviceInfo?>(null) }

    // 获取 UsbDacManager 实例
    val usbManager = remember {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            UsbDacEntryPoint::class.java
        )
        entryPoint.usbDacManager
    }

    // 扫描设备
    LaunchedEffect(Unit) {
        isScanning = true
        devices = usbManager.scanDevices()
        isScanning = false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.usb_device_selector_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {
                    scope.launch {
                        isScanning = true
                        devices = usbManager.scanDevices()
                        isScanning = false
                    }
                }) {
                    Text(stringResource(R.string.usb_device_selector_refresh))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 状态提示
            AnimatedVisibility(
                visible = isScanning,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.usb_device_selector_scanning),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            // 设备列表
            if (devices.isEmpty() && !isScanning) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        modifier = Modifier.padding(bottom = 16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.usb_device_selector_no_devices),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.usb_device_selector_hint),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(devices) { device ->
                        UsbDeviceItem(
                            device = device,
                            isSelected = selectedDevice?.deviceName == device.deviceName,
                            onSelect = { selectedDevice = device }
                        )
                    }
                }
            }

            // 确认按钮
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        selectedDevice?.let { device ->
                            onDeviceSelected(device)
                        }
                    },
                    enabled = selectedDevice != null
                ) {
                    Text(stringResource(R.string.common_confirm))
                }
            }
        }
    }
}

/**
 * USB 设备列表项
 */
@Composable
private fun UsbDeviceItem(
    device: UsbDeviceInfo,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        onClick = onSelect,
        shape = MaterialTheme.shapes.medium,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        ListItem(
            leadingContent = {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Usb,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
            },
            headlineContent = {
                Text(
                    text = device.displayName,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                )
            },
            supportingContent = {
                Text(
                    text = buildString {
                        append("VID:${device.vendorId.toString(16).uppercase().padStart(4, '0')}")
                        append(" PID:${device.productId.toString(16).uppercase().padStart(4, '0')}")
                        if (device.supportedSampleRates.isNotEmpty()) {
                            append(" ${device.supportedSampleRates.first() / 1000}kHz+")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            },
            trailingContent = {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.Usb,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )
    }
}

/**
 * Hilt EntryPoint for UsbDacManager
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UsbDacEntryPoint {
    val usbDacManager: UsbDacManager
}
