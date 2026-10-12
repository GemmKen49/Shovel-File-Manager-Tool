package com.shovel.filemanager.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ShovelDocumentIdRulesTest {
    @Test
    fun roundTripsUnicodeRelativePath() {
        val id = ShovelDocumentIdRules.encode("primary", "Pictures/Šeima/nuotrauka.jpg")
        assertEquals(ShovelDocumentId("primary", "Pictures/Šeima/nuotrauka.jpg"), ShovelDocumentIdRules.parse(id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTraversal() {
        ShovelDocumentIdRules.encode("primary", "../secret")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPathsDeeperThanProviderLimit() {
        val path = List(ShovelDocumentIdRules.MAX_PATH_SEGMENTS + 1) { "folder" }.joinToString("/")
        ShovelDocumentIdRules.encode("primary", path)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedEncodedDocumentIdBeforeDecoding() {
        ShovelDocumentIdRules.parse("primary:" + "A".repeat(13 * 1024))
    }

    @Test
    fun blocksProtectedAndroidTrees() {
        assertTrue(ShovelDocumentIdRules.isAllowedRelative("Android/media"))
        assertFalse(ShovelDocumentIdRules.isAllowedRelative("Android/data"))
        assertFalse(ShovelDocumentIdRules.isAllowedRelative("Android/obb/game"))
    }

    @Test
    fun resolvesOnlyInsideTheExportedRoot() {
        val root = createTempDirectory("shovel-provider-").toFile()
        try {
            val nested = File(root, "Documents/report.txt")
            nested.parentFile!!.mkdirs()
            nested.writeText("ok")
            val id = ShovelDocumentIdRules.encode("primary", "Documents/report.txt")
            assertEquals(nested.canonicalFile, ShovelDocumentIdRules.resolve(root, id))
        } finally {
            root.deleteRecursively()
        }
    }
}
