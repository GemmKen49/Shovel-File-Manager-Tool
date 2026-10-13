package com.shovel.filemanager.ui.screens
import com.shovel.filemanager.ui.components.ShovelActionRow

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesomeMotion
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import com.shovel.filemanager.ui.theme.ShovelAlertDialog as AlertDialog
import com.shovel.filemanager.ui.theme.ShovelButton as Button
import com.shovel.filemanager.ui.theme.ShovelCard as Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import com.shovel.filemanager.ui.theme.ShovelFilterChip as FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.shovel.filemanager.data.OptionalFeature
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shovel.filemanager.BuildConfig
import com.shovel.filemanager.advanced.AdvancedAccessMode
import com.shovel.filemanager.advanced.CapabilityState
import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.operations.OperationSnapshot
import com.shovel.filemanager.operations.OperationStatus
import com.shovel.filemanager.ui.MainViewModel
import com.shovel.filemanager.ui.components.ShovelModalDialog
import com.shovel.filemanager.ui.components.ProviderAppVisual
import com.shovel.filemanager.ui.localization.LText
import com.shovel.filemanager.ui.localization.uiText
import com.shovel.filemanager.ui.theme.AppColorPalette
import com.shovel.filemanager.ui.theme.AppThemeMode
import com.shovel.filemanager.ui.theme.AppearanceRules
import com.shovel.filemanager.ui.theme.AppearanceSettings
import com.shovel.filemanager.ui.theme.palettePreviewColors
import com.shovel.filemanager.ui.theme.CustomThemeColors
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ToolsScreen(
    viewModel: MainViewModel,
    contentPadding: PaddingValues,
    onAddSafLocation: () -> Unit,
    onToggleAppLock: (Boolean) -> Unit,
) {
    val operations by viewModel.operations.collectAsStateWithLifecycle()
    val trash by viewModel.trashItems.collectAsStateWithLifecycle()
    val appLockEnabled by viewModel.appLockEnabled.collectAsStateWithLifecycle()
    val appearanceSettings by viewModel.appearanceSettings.collectAsStateWithLifecycle()
    val featureVisibility by viewModel.featureVisibility.collectAsStateWithLifecycle()
    var expandedSections by rememberSaveable { mutableStateOf(emptyList<String>()) }
    fun toggleSection(id: String) {
        expandedSections = if (id in expandedSections) expandedSections - id else expandedSections + id
    }
    val advancedAccess by viewModel.advancedAccess.collectAsStateWithLifecycle()
    val active = viewModel.activePanelState()
    val selectedEntry = active.entries.singleOrNull { it.absolutePath in active.selectedPaths }
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            LText("Įrankiai ir saugumas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            LText("Jokių reklamų, sekimo SDK ar privalomos paskyros.", style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("tools_list"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        item { SettingsSectionHeader("Išvaizda", "appearance", "appearance" in expandedSections) { toggleSection("appearance") } }
        if ("appearance" in expandedSections) {
        item {
            AppearanceSettingsCard(
                settings = appearanceSettings,
                onThemeMode = viewModel::setThemeMode,
                onPalette = viewModel::setColorPalette,
                onAmoledBlack = viewModel::setAmoledBlack,
                onCustomColors = viewModel::setCustomColors,
                onInterfaceScale = viewModel::setInterfaceScalePercent,
            )
        }
        item {
            FeatureVisibilitySettings(featureVisibility, viewModel::setFeatureVisible, viewModel::resetFeatureVisibility)
        }
        }

        item { SettingsSectionHeader("Operacijų centras", "actions", "actions" in expandedSections) { toggleSection("actions") } }
        if ("actions" in expandedSections) {
        item { SectionHeader("Operacijų centras", operations.size.toString()) }
        if (operations.isEmpty()) {
            item { InfoCard("Nėra operacijų", "Kopijavimas ir archyvavimas bus rodomi čia.", Icons.Rounded.CheckCircle) }
        } else {
            items(operations, key = OperationSnapshot::id) { operation ->
                OperationCard(
                    operation = operation,
                    onPause = { viewModel.pauseOperation(operation.id) },
                    onResume = { viewModel.resumeOperation(operation.id) },
                    onCancel = { viewModel.cancelOperation(operation.id) },
                    onRetry = { viewModel.retryOperation(operation.id) },
                )
            }
            item {
                TextButton(onClick = viewModel::dismissFinishedOperations) { LText("Paslėpti užbaigtas") }
            }
        }

        item { SectionHeader("Šiukšliadėžė", trash.size.toString()) }
        item {
            Card(
                onClick = viewModel::openTrashBrowser,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        LText("Atidaryti šiukšliadėžę", fontWeight = FontWeight.SemiBold)
                        LText(
                            if (trash.isEmpty()) "Šiukšliadėžė tuščia" else "${trash.size} elementų · galima atkurti arba išvalyti viską",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    LText("Atidaryti", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        }
        item { SettingsSectionHeader("Privatumas", "privacy", "privacy" in expandedSections) { toggleSection("privacy") } }
        if ("privacy" in expandedSections) {
        item { SectionHeader("Programos užraktas", if (appLockEnabled) "Įjungtas" else "Išjungtas") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        LText("Biometrinis arba įrenginio užraktas", fontWeight = FontWeight.SemiBold)
                        LText("Grįžus iš fono failų langas bus užrakintas. Keitimą patvirtina Android.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = appLockEnabled, onCheckedChange = onToggleAppLock)
                }
            }
        }
        if (featureVisibility.isVisible(OptionalFeature.ADVANCED_ACCESS) || advancedAccess.connected) {
        item { SectionHeader("Pažengusio naudotojo režimas", "Neprivalomas") }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LText("Apsaugotų Android aplankų prieiga", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf(AdvancedAccessMode.OFF, AdvancedAccessMode.AUTO).forEach { mode ->
                            FilterChip(
                                selected = advancedAccess.selectedMode == mode,
                                onClick = { viewModel.setAdvancedAccessMode(mode) },
                                label = { LText(advancedModeLabel(mode)) },
                                modifier = Modifier.testTag("advanced_mode_${mode.name.lowercase()}"),
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(AdvancedAccessMode.SHIZUKU, AdvancedAccessMode.ROOT).forEach { mode ->
                            FilterChip(
                                selected = advancedAccess.selectedMode == mode,
                                onClick = { viewModel.setAdvancedAccessMode(mode) },
                                label = { LText(advancedModeLabel(mode), maxLines = 1) },
                                leadingIcon = { Icon(if (mode == AdvancedAccessMode.SHIZUKU) Icons.Rounded.Security else Icons.Rounded.Terminal, contentDescription = null) },
                                modifier = Modifier.weight(1f).testTag("advanced_mode_${mode.name.lowercase()}"),
                            )
                        }
                    }
                    StatusLine(
                        "Shizuku",
                        shizukuStatus(
                            managerDetected = advancedAccess.shizukuManagerDetected,
                            running = advancedAccess.shizukuRunning,
                            permission = advancedAccess.shizukuPermission,
                        ),
                    )
                    StatusLine("Root", capabilityLabel(advancedAccess.rootPermission))
                    StatusLine("Aktyvi prieiga", if (advancedAccess.connected) "Prisijungta · UID ${advancedAccess.serviceUid}" else "Neprisijungta")
                    advancedAccess.androidDataAccessible?.let { accessible ->
                        StatusLine("Android/data", if (accessible) "Pasiekiamas" else "Nepasiekiamas šiuo režimu")
                    }
                    if (!advancedAccess.connected) Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = viewModel::requestShizukuAccess,
                            enabled = !advancedAccess.connecting,
                        ) {
                            LText(
                                when {
                                    !advancedAccess.shizukuRunning -> "Patikrinti Shizuku"
                                    advancedAccess.shizukuPermission == CapabilityState.GRANTED -> "Jungtis per Shizuku"
                                    else -> "Suteikti Shizuku leidimą"
                                },
                            )
                        }
                        OutlinedButton(
                            onClick = viewModel::requestRootAccess,
                            enabled = advancedAccess.rootPermission != CapabilityState.UNAVAILABLE && !advancedAccess.connecting,
                        ) { LText("Jungtis per root") }
                    }
                    if (advancedAccess.connected) {
                        listOf("data", "obb").forEach { folder ->
                            Button(
                                onClick = { viewModel.openAdvancedBrowser("${android.os.Environment.getExternalStorageDirectory().absolutePath}/Android/$folder") },
                                enabled = !advancedAccess.connecting,
                                modifier = Modifier.fillMaxWidth().testTag("open_android_$folder"),
                            ) {
                                Icon(Icons.Rounded.FolderSpecial, contentDescription = null)
                                LText(if (folder == "data") "Atidaryti Android/data" else "Atidaryti Android/obb", modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                    if (advancedAccess.connecting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    advancedAccess.error?.let { LText(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    LText(
                        "Pagal nutylėjimą ši prieiga išjungta. Įprastas režimas neapeina Android ribojimų. Shizuku ir root naudojami tik pasirinkus šį režimą ir suteikus leidimą.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        }

        item { SectionHeader("Privatumas", "Be reklamų") }
        item {
            InfoCard(
                "Vietiniai duomenys lieka įrenginyje",
                "Programa neturi interneto prieigos. Analitika ir reklamos SDK nepridėti.",
                Icons.Rounded.Security,
            )
        }
        }
        }
    }

}

@Composable
internal fun SettingsSectionHeader(title: String, id: String, expanded: Boolean, onToggle: () -> Unit) {
    Card(onClick = onToggle, modifier = Modifier.fillMaxWidth().testTag("settings_section_$id")) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LText(title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = uiText(if (expanded) "Sutraukti" else "Išskleisti"))
        }
    }
}

@Composable
internal fun FeatureVisibilitySettings(
    settings: com.shovel.filemanager.data.FeatureVisibility,
    onVisibility: (OptionalFeature, Boolean) -> Unit,
    onReset: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().testTag("feature_visibility_settings")) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            LText("Matomos funkcijos", fontWeight = FontWeight.Bold)
            LText("Paslėpimas nekeičia failų ar nustatymų ir nestabdo vykstančių užduočių.", style = MaterialTheme.typography.bodySmall)
            OptionalFeature.entries.forEach { feature ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    LText(feature.label, modifier = Modifier.weight(1f).padding(end = 8.dp))
                    Switch(checked = settings.isVisible(feature), onCheckedChange = { onVisibility(feature, it) },
                        modifier = Modifier.testTag("feature_${feature.name.lowercase()}"))
                }
            }
            TextButton(onClick = onReset, modifier = Modifier.testTag("feature_visibility_reset")) { LText("Atkurti numatytąsias") }
        }
    }
}

private fun advancedModeLabel(mode: AdvancedAccessMode): String = when (mode) {
    AdvancedAccessMode.OFF -> "Išjungta"
    AdvancedAccessMode.AUTO -> "Automatiškai"
    AdvancedAccessMode.SHIZUKU -> "Shizuku"
    AdvancedAccessMode.ROOT -> "Root"
}

private fun capabilityLabel(state: CapabilityState): String = when (state) {
    CapabilityState.UNAVAILABLE -> "Neaptikta"
    CapabilityState.AVAILABLE -> "Galima paprašyti leidimo"
    CapabilityState.GRANTED -> "Leidimas suteiktas"
    CapabilityState.DENIED -> "Leidimas atmestas"
}

private fun shizukuStatus(managerDetected: Boolean, running: Boolean, permission: CapabilityState): String = when {
    running -> capabilityLabel(permission)
    managerDetected -> "Programa aptikta, tarnyba nepaleista"
    else -> "Tarnyba nepaleista"
}

@Composable
internal fun AppearanceSettingsCard(
    settings: AppearanceSettings,
    onThemeMode: (AppThemeMode) -> Unit,
    onPalette: (AppColorPalette) -> Unit,
    onAmoledBlack: (Boolean) -> Unit,
    onCustomColors: (CustomThemeColors) -> Boolean,
    onInterfaceScale: (Int) -> Unit = {},
) {
    var editCustom by remember { mutableStateOf(false) }
    var interfaceScaleDraft by remember(settings.interfaceScalePercent) {
        mutableStateOf(settings.interfaceScalePercent.toFloat())
    }
    if (editCustom) CustomPaletteDialog(settings.customColors, onCustomColors, onDismiss = { editCustom = false })
    Card(
        modifier = Modifier.fillMaxWidth().testTag("appearance_settings"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LText("Išvaizda", fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                LText("Dydis", style = MaterialTheme.typography.labelLarge)
                Text("${interfaceScaleDraft.roundToInt()}%", style = MaterialTheme.typography.labelLarge)
            }
            val scaleRange = AppearanceRules.MIN_INTERFACE_SCALE_PERCENT.toFloat()..
                AppearanceRules.MAX_INTERFACE_SCALE_PERCENT.toFloat()
            val scaleSteps = ((AppearanceRules.MAX_INTERFACE_SCALE_PERCENT - AppearanceRules.MIN_INTERFACE_SCALE_PERCENT) /
                AppearanceRules.INTERFACE_SCALE_STEP_PERCENT) - 1
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("interface_scale")
                    .semantics {
                        progressBarRangeInfo = ProgressBarRangeInfo(interfaceScaleDraft, scaleRange, scaleSteps)
                        setProgress { requested ->
                            val normalized = AppearanceRules.normalizeInterfaceScale(requested.roundToInt())
                            val changed = normalized.toFloat() != interfaceScaleDraft
                            if (changed) {
                                interfaceScaleDraft = normalized.toFloat()
                                onInterfaceScale(normalized)
                            }
                            changed
                        }
                    },
            ) {
                Slider(
                    value = interfaceScaleDraft,
                    onValueChange = { value ->
                        interfaceScaleDraft = AppearanceRules.normalizeInterfaceScale(value.roundToInt()).toFloat()
                    },
                    onValueChangeFinished = { onInterfaceScale(interfaceScaleDraft.roundToInt()) },
                    valueRange = scaleRange,
                    steps = scaleSteps,
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                )
            }
            LText("Temos režimas", style = MaterialTheme.typography.labelLarge)
            ShovelActionRow {
                AppThemeMode.entries.forEach { mode ->
                    val label = when (mode) {
                        AppThemeMode.SYSTEM -> "Sistema"
                        AppThemeMode.LIGHT -> "Šviesi"
                        AppThemeMode.DARK -> "Tamsi"
                    }
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { onThemeMode(mode) },
                        label = { LText(label) },
                        modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}"),
                    )
                }
            }

            LText("Spalvų paletė", style = MaterialTheme.typography.labelLarge)
            AppColorPalette.entries.chunked(2).forEach { paletteRow ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    paletteRow.forEach { palette ->
                        val supported = AppearanceRules.paletteSupported(palette, Build.VERSION.SDK_INT)
                        val selected = settings.colorPalette == palette
                        Card(
                            onClick = { if (palette == AppColorPalette.CUSTOM) editCustom = true else onPalette(palette) },
                            enabled = supported,
                            modifier = Modifier
                                .weight(1f)
                                .then(
                                    if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                    else Modifier,
                                )
                                .testTag("palette_${palette.name.lowercase()}"),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().height(24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    palettePreviewColors(palette, settings.customColors).forEach { color ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(24.dp)
                                                .background(color, RoundedCornerShape(6.dp)),
                                        )
                                    }
                                }
                                LText(
                                    when (palette) {
                                        AppColorPalette.DEFAULT -> "Numatytoji"
                                        AppColorPalette.DYNAMIC -> "Dinaminė"
                                        AppColorPalette.CATPPUCCIN -> "Catppuccin"
                                        AppColorPalette.ORANGE -> "Oranžinė"
                                        AppColorPalette.MATERIAL_BLUE -> "Material mėlyna"
                                        AppColorPalette.AURA -> "Aura"
                                        AppColorPalette.TOKYO -> "Tokyo"
                                        AppColorPalette.YIN_YANG -> "Yin Yang"
                                        AppColorPalette.RED -> "Raudona"
                                        AppColorPalette.CUSTOM -> "Pasirinktinė paletė"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                if (!supported) {
                                    LText("Reikia Android 12+", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    if (paletteRow.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    LText("AMOLED juodas režimas", fontWeight = FontWeight.Medium)
                    LText("Tamsioje temoje fonas tampa visiškai juodas.", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = settings.amoledBlack,
                    onCheckedChange = onAmoledBlack,
                    modifier = Modifier.testTag("amoled_black"),
                )
            }
        }
    }
}

@Composable
private fun OperationCard(
    operation: OperationSnapshot,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    LText(operation.title, fontWeight = FontWeight.SemiBold)
                    operation.currentName?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    } ?: LText(operation.message ?: operation.status.name, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
                when (operation.status) {
                    OperationStatus.RUNNING -> IconButton(onClick = onPause) { Icon(Icons.Rounded.Pause, contentDescription = uiText("Pauzė")) }
                    OperationStatus.PAUSED -> IconButton(onClick = onResume) { Icon(Icons.Rounded.PlayArrow, contentDescription = uiText("Tęsti")) }
                    else -> Unit
                }
                if (operation.status == OperationStatus.RUNNING || operation.status == OperationStatus.PAUSED || operation.status == OperationStatus.QUEUED) {
                    IconButton(onClick = onCancel) { Icon(Icons.Rounded.Cancel, contentDescription = uiText("Atšaukti")) }
                }
                if (operation.retryable && operation.status !in setOf(OperationStatus.RUNNING, OperationStatus.PAUSED, OperationStatus.QUEUED)) {
                    IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, contentDescription = uiText("Bandyti dar kartą")) }
                }
            }
            val totalBytes = operation.totalBytes
            if (totalBytes != null && totalBytes > 0) {
                LinearProgressIndicator(
                    progress = { (operation.completedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                LText("${FileSystemRules.humanBytes(operation.completedBytes)} / ${FileSystemRules.humanBytes(totalBytes)}", style = MaterialTheme.typography.labelSmall)
                val startedAt = operation.startedAtMillis
                if (startedAt != null && operation.completedBytes > 0 && operation.status == OperationStatus.RUNNING) {
                    val elapsedSeconds = ((System.currentTimeMillis() - startedAt).coerceAtLeast(1L) / 1_000.0)
                    val bytesPerSecond = (operation.completedBytes / elapsedSeconds).toLong().coerceAtLeast(1L)
                    val remainingSeconds = ((totalBytes - operation.completedBytes).coerceAtLeast(0L) / bytesPerSecond)
                    LText(
                        "${FileSystemRules.humanBytes(bytesPerSecond)}/s · liko apie ${remainingSeconds}s",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            } else if (operation.status == OperationStatus.RUNNING) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (operation.errorCount > 0) {
                LText("Klaidų: ${operation.errorCount}. Galima bandyti dar kartą.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, detail: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        LText(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        LText(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

@Composable
private fun InfoCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                LText(title, fontWeight = FontWeight.SemiBold)
                LText(description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun StatusLine(name: String, status: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        LText(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        LText(status, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    explanation: String,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val valid = password.length >= 8 && password == repeated
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { LText(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LText(explanation, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { LText("Slaptafrazė") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = repeated,
                    onValueChange = { repeated = it },
                    label = { LText("Pakartokite") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(password.toCharArray())
                    password = ""
                    repeated = ""
                },
                enabled = valid,
            ) { LText("Šifruoti") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { LText("Atšaukti") } },
    )
}
