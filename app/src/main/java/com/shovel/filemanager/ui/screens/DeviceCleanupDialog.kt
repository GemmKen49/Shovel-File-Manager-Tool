package com.shovel.filemanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Stop
import com.shovel.filemanager.ui.theme.ShovelButton as Button
import com.shovel.filemanager.ui.theme.ShovelCard as Card
import com.shovel.filemanager.ui.theme.ShovelFilterChip as FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.shovel.filemanager.cleanup.DeviceCleanupApp
import com.shovel.filemanager.core.FileSystemRules
import com.shovel.filemanager.ui.DeviceCleanupUiState
import com.shovel.filemanager.ui.components.ShovelModalDialog
import com.shovel.filemanager.ui.components.ShovelPullToRefresh
import com.shovel.filemanager.ui.components.ProviderAppVisual
import com.shovel.filemanager.ui.localization.LText
import com.shovel.filemanager.ui.localization.rememberLocalizedDateTimeFormat
import com.shovel.filemanager.ui.localization.uiText
import java.text.DateFormat
import java.util.Date

private enum class DeviceCleanupPage { UNUSED_APPS, APP_CACHE, RUNNING_APPS }

@Composable
internal fun DeviceCleanupDialog(
    state: DeviceCleanupUiState,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onGrantUsageAccess: () -> Unit,
    onOpenAppSettings: (String) -> Unit,
    onUninstall: (String) -> Unit,
    onForceStop: (String) -> Unit,
) {
    if (!state.open) return
    var page by remember { mutableStateOf(DeviceCleanupPage.UNUSED_APPS) }
    var clearCacheApp by remember { mutableStateOf<DeviceCleanupApp?>(null) }
    var forceStopApp by remember { mutableStateOf<DeviceCleanupApp?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val refresh by rememberUpdatedState(onRefresh)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val snapshot = state.snapshot
    val apps = when (page) {
        DeviceCleanupPage.UNUSED_APPS -> snapshot?.unusedApps.orEmpty()
        DeviceCleanupPage.APP_CACHE -> snapshot?.cachedApps.orEmpty()
        DeviceCleanupPage.RUNNING_APPS -> snapshot?.runningApps.orEmpty()
    }
    ShovelModalDialog(
        title = "Device cleanup",
        subtitle = "Review only — Shovel removes nothing without your confirmation",
        icon = Icons.Rounded.CleaningServices,
        onDismissRequest = onDismiss,
        expandedContent = true,
        modifier = Modifier.testTag("device_cleanup_dialog"),
        actions = {
            TextButton(
                onClick = onRefresh,
                enabled = !state.loading,
            ) { LText("Refresh") }
            TextButton(onClick = onDismiss) { LText("Close") }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = page == DeviceCleanupPage.UNUSED_APPS,
                    onClick = { page = DeviceCleanupPage.UNUSED_APPS },
                    label = { LText("Unused apps") },
                    modifier = Modifier.testTag("cleanup_unused_tab"),
                )
                FilterChip(
                    selected = page == DeviceCleanupPage.APP_CACHE,
                    onClick = { page = DeviceCleanupPage.APP_CACHE },
                    label = { LText("App cache") },
                    modifier = Modifier.testTag("cleanup_cache_tab"),
                )
                FilterChip(
                    selected = page == DeviceCleanupPage.RUNNING_APPS,
                    onClick = { page = DeviceCleanupPage.RUNNING_APPS },
                    label = { LText("Running apps") },
                    modifier = Modifier.testTag("cleanup_running_tab"),
                )
            }
            if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (state.loading && snapshot == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LText("Scanning apps…")
                }
            } else if (state.error != null && snapshot == null) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LText(state.error, color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = onRefresh) { LText("Try again") }
                    }
                }
            } else if (snapshot == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = onRefresh) { LText("Try again") }
                }
            } else if (page != DeviceCleanupPage.RUNNING_APPS && !snapshot.usageAccessGranted) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LText("Android usage access is required to calculate unused apps and provider-reported cache sizes.")
                        Button(onClick = onGrantUsageAccess) { LText("Open usage access settings") }
                        LText("Shovel does not clear another app’s cache and does not uninstall anything without Android confirmation.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else if (page == DeviceCleanupPage.RUNNING_APPS && !snapshot.privilegedAppAccessAvailable) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LText("Android allows app memory inspection and force stopping only through active Root or Shizuku access.")
                        LText("Enable Root or Shizuku under More, then refresh this list.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                state.error?.let { LText(it, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                if (page == DeviceCleanupPage.RUNNING_APPS) {
                    snapshot.privilegedAppAccessError?.let {
                        LText(it, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
                    }
                    if (snapshot.runningAppsTruncated) {
                        LText("Showing the first 500 running apps", modifier = Modifier.padding(horizontal = 16.dp))
                    }
                } else if (snapshot.appsTruncated) {
                    LText("Showing the first 500 apps", modifier = Modifier.padding(horizontal = 16.dp))
                }
                if (page == DeviceCleanupPage.UNUSED_APPS && !snapshot.usageHistoryAvailable) {
                    LText("Usage statistics are unavailable", modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error)
                }
                if (page == DeviceCleanupPage.APP_CACHE && snapshot.cacheSizesAvailable.not()) {
                    LText(
                        "This Android build does not expose other apps’ cache sizes. Open an app’s system settings to review it.",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                ShovelPullToRefresh(
                    isRefreshing = state.loading,
                    onRefresh = onRefresh,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    testTag = "pull_to_refresh_device_cleanup",
                ) {
                    if (apps.isEmpty() && !state.loading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LText(if (page == DeviceCleanupPage.UNUSED_APPS) {
                                if (snapshot.usageHistoryAvailable) "No apps meet the 90-day rule" else "Usage statistics are unavailable"
                            } else if (page == DeviceCleanupPage.APP_CACHE) {
                                "No cache sizes are available"
                            } else if (snapshot.privilegedAppAccessError != null) {
                                "Could not scan running apps"
                            } else {
                                "There are no running user apps"
                            })
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(apps, key = DeviceCleanupApp::packageName) { app ->
                                DeviceCleanupAppRow(
                                    app = app,
                                    showCache = page == DeviceCleanupPage.APP_CACHE,
                                    showRam = page == DeviceCleanupPage.RUNNING_APPS,
                                    onOpenSettings = { onOpenAppSettings(app.packageName) },
                                    onUninstall = { onUninstall(app.packageName) },
                                    onClearCache = { clearCacheApp = app },
                                    onForceStop = { forceStopApp = app },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    clearCacheApp?.let { app ->
        ShovelModalDialog(
            title = "Clear cache",
            subtitle = app.label,
            icon = Icons.Rounded.CleaningServices,
            onDismissRequest = { clearCacheApp = null },
            modifier = Modifier.testTag("cleanup_cache_instructions"),
            actions = {
                TextButton(onClick = { clearCacheApp = null }) { LText("Cancel") }
                TextButton(onClick = { clearCacheApp = null; onOpenAppSettings(app.packageName) }, modifier = Modifier.testTag("cleanup_cache_settings")) {
                    LText("Open settings")
                }
            },
        ) {
            LText(
                "In Android settings, open Storage and cache, then Clear cache. Do not choose Clear data. Shovel cannot clear other apps' caches directly.",
                modifier = Modifier.padding(20.dp),
            )
        }
    }
    forceStopApp?.let { app ->
        ShovelModalDialog(
            title = "Force stop app?",
            subtitle = app.label,
            icon = Icons.Rounded.Stop,
            onDismissRequest = { forceStopApp = null },
            modifier = Modifier.testTag("cleanup_force_stop_confirmation"),
            actions = {
                TextButton(onClick = { forceStopApp = null }) { LText("Cancel") }
                TextButton(
                    onClick = {
                        forceStopApp = null
                        onForceStop(app.packageName)
                    },
                    enabled = !state.loading,
                    modifier = Modifier.testTag("cleanup_force_stop_confirm"),
                ) { LText("Stop", color = MaterialTheme.colorScheme.error) }
            },
        ) {
            LText(
                "The app will be force stopped. Its unsaved work may be lost.",
                modifier = Modifier.padding(20.dp),
            )
        }
    }
}

@Composable
private fun DeviceCleanupAppRow(
    app: DeviceCleanupApp,
    showCache: Boolean,
    showRam: Boolean,
    onOpenSettings: () -> Unit,
    onUninstall: () -> Unit,
    onClearCache: () -> Unit,
    onForceStop: () -> Unit,
) {
    val dateFormat = rememberLocalizedDateTimeFormat(DateFormat.MEDIUM, DateFormat.SHORT)
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProviderAppVisual(
                packageName = app.packageName,
                fallbackIcon = Icons.Rounded.Apps,
                targetSize = 40.dp,
                modifier = Modifier.size(40.dp).testTag("cleanup_icon_${app.packageName}"),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(app.label, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                LText(
                    if (showRam) {
                        app.ramBytes?.let(FileSystemRules::humanBytes) ?: "RAM usage is unavailable"
                    } else if (showCache) {
                        app.cacheBytes?.let(FileSystemRules::humanBytes) ?: "Cache size unavailable"
                    } else {
                        app.lastUsedMillis?.let { dateFormat.format(Date(it)) }
                            ?: "No recorded use in the available history"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("cleanup_settings_${app.packageName}")) {
                Icon(Icons.Rounded.Settings, contentDescription = uiText("Open settings"))
            }
            if (showRam) {
                IconButton(onClick = onForceStop, modifier = Modifier.testTag("cleanup_force_stop_${app.packageName}")) {
                    Icon(Icons.Rounded.Stop, contentDescription = uiText("Force stop app"), tint = MaterialTheme.colorScheme.error)
                }
            } else if (!showCache) {
                IconButton(onClick = onUninstall, modifier = Modifier.testTag("cleanup_uninstall_${app.packageName}")) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = uiText("Uninstall app"))
                }
            } else {
                IconButton(onClick = onClearCache, modifier = Modifier.testTag("cleanup_cache_${app.packageName}")) {
                    Icon(Icons.Rounded.CleaningServices, contentDescription = uiText("Clear cache"))
                }
            }
        }
    }
}
