package com.theveloper.pixelplay.presentation.screens

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.database.BluetoothPresetBindingEntity
import com.theveloper.pixelplay.data.database.HeadphonePresetEntity
import com.theveloper.pixelplay.data.database.HeadphonePresetWithBands
import com.theveloper.pixelplay.presentation.viewmodel.HeadphonePresetViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeadphonePresetScreen(
    navController: NavController,
    viewModel: HeadphonePresetViewModel = hiltViewModel()
) {
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val brands by viewModel.brands.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val selectedPreset by viewModel.selectedPreset.collectAsStateWithLifecycle()
    val bindings by viewModel.bindings.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedBrand by viewModel.selectedBrand.collectAsStateWithLifecycle()
    val isApplying by viewModel.isApplying.collectAsStateWithLifecycle()
    val activePreset by viewModel.activePreset.collectAsStateWithLifecycle()

    var showPresetDetail by remember { mutableStateOf(false) }
    var showBluetoothDialog by remember { mutableStateOf(false) }
    var showBindingSheet by remember { mutableStateOf(false) }
    var bluetoothDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var selectedPresetForBinding by remember { mutableStateOf<HeadphonePresetEntity?>(null) }

    val presetDetailSheetState = rememberModalBottomSheetState()
    val bindingSheetState = rememberModalBottomSheetState()

    val context = LocalContext.current

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.headphone_preset_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = { viewModel.refreshPresets() },
                        enabled = !isApplying
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.headphone_preset_refresh),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.headphone_preset_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Rounded.Close, contentDescription = null)
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {}),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                CategoryFilterRow(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { viewModel.setSelectedCategory(it) }
                )
            }

            item {
                BrandFilterRow(
                    brands = brands,
                    selectedBrand = selectedBrand,
                    onBrandSelected = { viewModel.setSelectedBrand(it) }
                )
            }

            items(presets) { preset ->
                PresetCard(
                    preset = preset,
                    onClick = {
                        viewModel.selectPreset(preset.id)
                        selectedPresetForBinding = preset
                        showPresetDetail = true
                    }
                )
            }
        }
    }

    if (showPresetDetail && selectedPreset != null) {
        ModalBottomSheet(
            sheetState = presetDetailSheetState,
            onDismissRequest = { showPresetDetail = false }
        ) {
            PresetDetailSheet(
                preset = selectedPreset!!,
                isApplying = isApplying,
                onApply = { viewModel.applyPreset(it) },
                onBindBluetooth = {
                    showPresetDetail = false
                    showBluetoothDialog = true
                    bluetoothDevices = getPairedBluetoothDevices(context)
                },
                onClose = { showPresetDetail = false },
                onClearPreset = { viewModel.clearPreset() },
                activePresetId = activePreset?.id
            )
        }
    }

    if (showBluetoothDialog) {
        ModalBottomSheet(
            sheetState = rememberModalBottomSheetState(),
            onDismissRequest = { showBluetoothDialog = false }
        ) {
            BluetoothDeviceSelectionDialog(
                devices = bluetoothDevices,
                onSelectDevice = { device ->
                    selectedPresetForBinding?.let { preset ->
                        viewModel.bindPresetToDevice(
                            deviceName = device.name ?: "Unknown",
                            deviceAddress = device.address,
                            presetId = preset.id
                        )
                    }
                    showBluetoothDialog = false
                },
                onClose = { showBluetoothDialog = false },
                onOpenBluetoothSettings = {
                    context.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS))
                }
            )
        }
    }

    if (showBindingSheet) {
        ModalBottomSheet(
            sheetState = bindingSheetState,
            onDismissRequest = { showBindingSheet = false }
        ) {
            BindingManagementSheet(
                bindings = bindings,
                onUnbind = { viewModel.unbindDevice(it) },
                onClose = { showBindingSheet = false }
            )
        }
    }
}

@Composable
fun CategoryFilterRow(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.headphone_preset_categories),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    label = stringResource(R.string.headphone_preset_all),
                    isSelected = selectedCategory == null,
                    onClick = { onCategorySelected(null) }
                )
            }
            items(categories) { category ->
                FilterChip(
                    label = category,
                    isSelected = selectedCategory == category,
                    onClick = { onCategorySelected(category) }
                )
            }
        }
    }
}

@Composable
fun BrandFilterRow(
    brands: List<String>,
    selectedBrand: String?,
    onBrandSelected: (String?) -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.headphone_preset_brands),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    label = stringResource(R.string.headphone_preset_all),
                    isSelected = selectedBrand == null,
                    onClick = { onBrandSelected(null) }
                )
            }
            items(brands) { brand ->
                FilterChip(
                    label = brand,
                    isSelected = selectedBrand == brand,
                    onClick = { onBrandSelected(brand) }
                )
            }
        }
    }
}

@Composable
fun FilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.height(36.dp)
    ) {
        Text(
            text = label,
            style = if (isSelected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PresetCard(
    preset: HeadphonePresetEntity,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    preset.brand?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Text(
                    text = preset.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.headphone_preset_preamp, preset.preamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetDetailSheet(
    preset: HeadphonePresetWithBands,
    isApplying: Boolean,
    onApply: (HeadphonePresetWithBands) -> Unit,
    onBindBluetooth: () -> Unit,
    onClose: () -> Unit,
    onClearPreset: () -> Unit,
    activePresetId: Long?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = preset.preset.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        preset.preset.brand?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        if (activePresetId == preset.preset.id) {
            Text(
                text = stringResource(R.string.headphone_preset_currently_active),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.headphone_preset_preamp, preset.preset.preamp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (preset.bands.isNotEmpty()) {
            Text(
                text = stringResource(R.string.headphone_preset_eq_settings),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.height(200.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(preset.bands) { band ->
                    EqBandItem(band = band)
                }
            }
        } else {
            Text(
                text = stringResource(R.string.headphone_preset_no_eq_bands),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledTonalButton(
                onClick = onBindBluetooth,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Rounded.Bluetooth, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.headphone_preset_bind_bluetooth))
            }
            if (activePresetId != null) {
                FilledTonalButton(
                    onClick = onClearPreset,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Rounded.Cancel, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.headphone_preset_clear))
                }
            }
            FilledTonalButton(
                onClick = { onApply(preset) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                enabled = !isApplying
            ) {
                Text(stringResource(R.string.headphone_preset_apply_now))
            }
        }
    }
}

@Composable
fun EqBandItem(
    band: com.theveloper.pixelplay.data.database.HeadphoneEqBandEntity
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${band.frequency} Hz",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "${band.gain} dB",
            style = MaterialTheme.typography.bodySmall,
            color = if (band.gain >= 0) Color.Green else Color.Red
        )
        Text(
            text = "Q: ${band.q}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothDeviceSelectionDialog(
    devices: List<BluetoothDevice>,
    onSelectDevice: (BluetoothDevice) -> Unit,
    onClose: () -> Unit,
    onOpenBluetoothSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.headphone_preset_select_bluetooth_device),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (devices.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(64.dp).height(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.headphone_preset_no_bluetooth_devices),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onOpenBluetoothSettings) {
                    Text(stringResource(R.string.headphone_preset_bluetooth_settings))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.height(300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices) { device ->
                    Card(
                        onClick = { onSelectDevice(device) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = device.name ?: "Unknown Device",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = device.address ?: "No address",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onClose) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BindingManagementSheet(
    bindings: List<BluetoothPresetBindingEntity>,
    onUnbind: (Long) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.headphone_preset_bluetooth_bindings),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (bindings.isEmpty()) {
            Text(
                text = stringResource(R.string.headphone_preset_no_bindings),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LazyColumn(
                modifier = Modifier.height(300.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(bindings) { binding ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = binding.deviceName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                binding.deviceAddress?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = { onUnbind(binding.id) },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(R.string.headphone_preset_unbind))
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onClose) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

private fun getPairedBluetoothDevices(context: android.content.Context): List<BluetoothDevice> {
    val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
}