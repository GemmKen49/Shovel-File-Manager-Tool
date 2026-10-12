package com.shovel.filemanager.data

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.shovel.filemanager.ShovelApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafFileRepositoryTest {
    private val repository = SafFileRepository(
        ApplicationProvider.getApplicationContext<ShovelApplication>(),
    )

    @Test
    fun treeRootAndChildDocumentUrisAreDistinguished() {
        val root = Uri.parse("content://com.google.android.apps.docs.storage/tree/drive-root")
        val child = Uri.parse(
            "content://com.google.android.apps.docs.storage/tree/drive-root/document/drive-child",
        )

        assertTrue(repository.isTreeRootUri(root))
        assertFalse(repository.isTreeRootUri(child))
        assertTrue(repository.usesPersistedTreeAccess(root))
        assertTrue(repository.usesPersistedTreeAccess(child))
    }
}
