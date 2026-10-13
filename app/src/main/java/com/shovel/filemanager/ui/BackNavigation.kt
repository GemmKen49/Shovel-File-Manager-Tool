package com.shovel.filemanager.ui

internal enum class SystemBackAction {
    CLOSE_PREVIEW,
    SHOW_FILES,
    CLEAR_SELECTION,
    NAVIGATE_BACK,
    NAVIGATE_UP,
    DEFER_TO_SYSTEM,
}

internal object BackNavigationRules {
    fun decide(
        previewOpen: Boolean,
        section: AppSection,
        filesHomeVisible: Boolean,
        selectedCount: Int,
        hasBackHistory: Boolean,
        hasParent: Boolean,
        returnToHomeAtBoundary: Boolean = false,
    ): SystemBackAction = when {
        previewOpen -> SystemBackAction.CLOSE_PREVIEW
        section != AppSection.FILES -> SystemBackAction.SHOW_FILES
        filesHomeVisible -> SystemBackAction.DEFER_TO_SYSTEM
        selectedCount > 0 -> SystemBackAction.CLEAR_SELECTION
        hasBackHistory -> SystemBackAction.NAVIGATE_BACK
        returnToHomeAtBoundary -> SystemBackAction.SHOW_FILES
        hasParent -> SystemBackAction.NAVIGATE_UP
        else -> SystemBackAction.SHOW_FILES
    }
}

internal object SectionNavigationRules {
    fun shouldShowFilesHome(current: AppSection, requested: AppSection): Boolean =
        current == AppSection.FILES && requested == AppSection.FILES
}
