package com.shovel.filemanager.ui

internal object EditSessionLifecycleRules {
    fun closeAfterSuccessfulSave(temporaryDownload: Boolean, closeRequested: Boolean): Boolean =
        temporaryDownload || closeRequested
}
