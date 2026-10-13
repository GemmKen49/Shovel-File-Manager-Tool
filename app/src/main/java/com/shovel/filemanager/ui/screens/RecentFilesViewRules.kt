package com.shovel.filemanager.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.shovel.filemanager.data.RecentFileItem
import com.shovel.filemanager.ui.MainViewModel
import java.util.Locale

@Composable
fun RecentFilesScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onOpen: (RecentFileItem) -> Unit = {},
    onShare: (List<RecentFileItem>) -> Unit = {},
    onReveal: (RecentFileItem) -> Unit = {},
    onBack: () -> Unit = {},
) {
    // State from the view model
    val recentState by viewModel.recentFiles.collectAsState()
    var tab by remember { mutableStateOf(RecentFilesTab.ADDED) }
    var sort by remember { mutableStateOf(RecentFilesSort.RECENT) }
    var ascending by remember { mutableStateOf(false) }
    var dateRange by remember { mutableStateOf(RecentFilesDateRange.ALL) }
    var query by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var dateMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshRecentFiles() } // initial load

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Naujausi failai") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowDownward, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { searchVisible = !searchVisible }) { Icon(Icons.Rounded.Search, contentDescription = "Search") }
                }
            )
        },
        content = { padding ->
            Column(modifier = modifier.fillMaxSize().padding(padding)) {
                // Filters & controls
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = tab == RecentFilesTab.ADDED,
                        onClick = { tab = RecentFilesTab.ADDED },
                        label = { Text("Neseniai pridėti") }
                    )
                    FilterChip(
                        selected = tab == RecentFilesTab.OPENED,
                        onClick = { tab = RecentFilesTab.OPENED },
                        label = { Text("Neseniai atidaryti") }
                    )

                    // Sort direction toggle + menu
                    IconButton(onClick = { ascending = !ascending }) {
                        Icon(if (ascending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward, contentDescription = "Toggle sort")
                    }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Text(sort.name.lowercase(Locale.ROOT)) }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            RecentFilesSort.values().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.name.lowercase(Locale.ROOT)) },
                                    onClick = { sort = option; sortMenu = false }
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { dateMenu = true }) { Icon(Icons.Rounded.CalendarMonth, contentDescription = "Date filter") }
                        DropdownMenu(expanded = dateMenu, onDismissRequest = { dateMenu = false }) {
                            RecentFilesDateRange.values().forEach { option ->
                                DropdownMenuItem(text = { Text(option.name.lowercase(Locale.ROOT)) }, onClick = { dateRange = option; dateMenu = false })
                            }
                        }
                    }
                    if (searchVisible) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth().padding(start = 8.dp)
                        )
                    }
                }

                if (recentState.loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (recentState.error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(recentState.error ?: "Error")
                    }
                } else {
                    val items = when (tab) {
                        RecentFilesTab.ADDED -> recentState.addedItems
                        RecentFilesTab.OPENED -> recentState.openedItems
                    }
                    // Apply view rules (the repo has RecentFilesViewRules.apply)
                    val filtered = remember(items, query, sort, ascending, dateRange) {
                        RecentFilesViewRules.apply(items, query, sort, ascending, dateRange)
                    }

                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        items(filtered, key = { it.entry.absolutePath }) { item ->
                            RecentFileRow(item = item,
                                onOpen = { onOpen(item) },
                                onReveal = { onReveal(item) },
                                onShare = { onShare(listOf(item)) }
                            )
                            Divider()
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun RecentFileRow(
    item: RecentFileItem,
    onOpen: () -> Unit,
    onReveal: () -> Unit,
    onShare: () -> Unit
) {
    ListItem(
        headlineContent = { Text(item.entry.name) },
        supportingContent = { Text(item.entry.absolutePath, maxLines = 1) },
        leadingContent = { /* optional icon or thumbnail */ },
        trailingContent = {
            Row {
                IconButton(onClick = onShare) { Text("Share") }
                IconButton(onClick = onReveal) { Text("Reveal") }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onOpen)
    )
}
