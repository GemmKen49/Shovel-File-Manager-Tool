package com.shovel.filemanager.data

/** Stable, optional UI entry points. Hiding one never changes stored data or running work. */
enum class OptionalFeature(val label: String) {
    ANALYSIS("Analyze"),
    RECENT("Recent files"), ADVANCED_ACCESS("Advanced-user mode"),
    BATCH_RENAME("Batch rename"),
    TOOLBAR_BACK("Back"), TOOLBAR_FORWARD("Forward"), TOOLBAR_UP("Up"),
    TOOLBAR_SEARCH("Search"), TOOLBAR_LAYOUT("Display settings"),
}

data class FeatureVisibility(val hidden: Set<OptionalFeature> = emptySet()) {
    fun isVisible(feature: OptionalFeature): Boolean = feature !in hidden
    fun withVisibility(feature: OptionalFeature, visible: Boolean): FeatureVisibility =
        copy(hidden = if (visible) hidden - feature else hidden + feature)
}
