package com.shovel.filemanager.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

internal val LocalAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }
internal val LocalOpaqueColors = staticCompositionLocalOf { androidx.compose.material3.lightColorScheme() }

@Composable
internal fun AppearanceBackground() {
    Box(Modifier.fillMaxSize().background(LocalOpaqueColors.current.background))
}

/** Page-sized dialogs and Activities share the same plain background as the root. */
@Composable
internal fun AppearancePage(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier) {
        AppearanceBackground()
        AppearanceContentOn(LocalOpaqueColors.current.background) {
        Surface(color = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = LocalOpaqueColors.current.onBackground, modifier = Modifier.fillMaxSize(), content = content)
        }
    }
}
