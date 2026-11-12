package com.shovel.filemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.shovel.filemanager.ui.components.ShovelModalDialog
import com.shovel.filemanager.ui.localization.LText
import com.shovel.filemanager.ui.theme.CustomThemeColors
import com.shovel.filemanager.ui.theme.CustomThemeRules

@Composable
internal fun CustomPaletteDialog(
    initial: CustomThemeColors,
    onSave: (CustomThemeColors) -> Boolean,
    onDismiss: () -> Unit,
) {
    var fields by remember { mutableStateOf(initial.values().map(CustomThemeRules::hex)) }
    var saveFailed by remember { mutableStateOf(false) }
    val parsed = remember(fields) { CustomThemeRules.parseDraft(fields) }
    val labels = listOf("Primary color", "Secondary color", "Tertiary color", "Background color", "Card color",
        "Menu and dialog color", "Button and selection color")
    ShovelModalDialog(
        title = "Custom palette",
        icon = Icons.Rounded.Palette,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("custom_palette_dialog"),
        actions = {
            TextButton(onClick = { fields = CustomThemeColors().values().map(CustomThemeRules::hex); saveFailed = false }) { LText("Reset") }
            TextButton(onClick = onDismiss) { LText("Cancel") }
            TextButton(
                onClick = { parsed?.let { if (onSave(it)) onDismiss() else saveFailed = true } },
                enabled = parsed != null,
                modifier = Modifier.testTag("custom_palette_save"),
            ) { LText("Save") }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LText("Enter colors as #RRGGBB. Text contrast is adjusted automatically.", style = MaterialTheme.typography.bodySmall)
            fields.forEachIndexed { index, value ->
                val color = CustomThemeRules.parseHex(value)
                OutlinedTextField(
                    value = value,
                    onValueChange = { updated ->
                        if (updated.length <= 9) { fields = fields.toMutableList().also { it[index] = updated }; saveFailed = false }
                    },
                    label = { LText(labels[index]) },
                    singleLine = true,
                    isError = color == null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
                    leadingIcon = { Box(Modifier.size(24.dp).background(color?.let(::Color) ?: Color.Transparent, CircleShape)) },
                    modifier = Modifier.fillMaxWidth().testTag("custom_color_$index"),
                )
            }
            if (saveFailed) LText("Could not save appearance settings", color = MaterialTheme.colorScheme.error)
        }
    }
}
