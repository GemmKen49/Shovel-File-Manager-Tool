package com.shovel.filemanager.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.shovel.filemanager.data.FeatureVisibility
import com.shovel.filemanager.data.OptionalFeature

val LocalFeatureVisibility = staticCompositionLocalOf { FeatureVisibility() }

@Composable
fun featureVisible(feature: OptionalFeature): Boolean = LocalFeatureVisibility.current.isVisible(feature)
