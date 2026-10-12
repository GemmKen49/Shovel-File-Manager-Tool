package com.shovel.filemanager.update

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubReleaseParserTest {
    private val digest = "a".repeat(64)

    @Test
    fun acceptsMatchingStableApkWithGitHubDigest() {
        val release = GitHubReleaseParser.parse(json(), "GemmKen49/Shovel-File-Manager-Tool")

        assertEquals("0.9.5", release.version)
        assertEquals("Shovel-0.9.5.apk", release.asset.name)
        assertEquals(digest, release.asset.sha256)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAssetFromAnotherRepository() {
        GitHubReleaseParser.parse(
            json().replace("GemmKen49/Shovel-File-Manager-Tool/releases/download", "attacker/Other/releases/download"),
            "GemmKen49/Shovel-File-Manager-Tool",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsReleaseWithoutSha256Digest() {
        GitHubReleaseParser.parse(json().replace("sha256:$digest", ""), "GemmKen49/Shovel-File-Manager-Tool")
    }

    private fun json(): String = """
        {
          "tag_name": "v0.9.5",
          "html_url": "https://github.com/GemmKen49/Shovel-File-Manager-Tool/releases/tag/v0.9.5",
          "draft": false,
          "prerelease": false,
          "body": "Saugus leidimas",
          "assets": [
            {
              "name": "Shovel-0.9.5.apk",
              "browser_download_url": "https://github.com/GemmKen49/Shovel-File-Manager-Tool/releases/download/v0.9.5/Shovel-0.9.5.apk",
              "size": 1024,
              "digest": "sha256:$digest"
            }
          ]
        }
    """.trimIndent()
}
