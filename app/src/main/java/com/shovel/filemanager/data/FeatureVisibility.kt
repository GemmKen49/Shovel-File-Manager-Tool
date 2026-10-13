package com.shovel.filemanager.data

/** Stable, optional UI entry points. Hiding one never changes stored data or running work. */
enum class OptionalFeature(val label: String) {
    ANALYSIS("Analizė"),
    RECENT("Naujausi failai"), ADVANCED_ACCESS("Pažengusio naudotojo režimas"),
    BATCH_RENAME("Masinis pervadinimas"), COMPARE("Aplankų palyginimas"),
    TOOLBAR_BACK("Atgal"), TOOLBAR_FORWARD("Pirmyn"), TOOLBAR_UP("Aukštyn"),
    TOOLBAR_SEARCH("Paieška"), TOOLBAR_LAYOUT("Rodinio nustatymai"),
}

data class FeatureVisibility(val hidden: Set<OptionalFeature> = emptySet()) {
    fun isVisible(feature: OptionalFeature): Boolean = feature !in hidden
    fun withVisibility(feature: OptionalFeature, visible: Boolean): FeatureVisibility =
        copy(hidden = if (visible) hidden - feature else hidden + feature)
}
