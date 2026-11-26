package io.github.lumkit.dockhand.data.update

import kotlin.test.Test
import kotlin.test.assertEquals

class GithubUpdateRepositoryTest {

    @Test
    fun parsesReleaseAndPrefersDmgAsset() {
        val json = """
            {
              "tag_name": "v0.2.0",
              "name": "Dockhand 0.2.0",
              "body": "Changes",
              "html_url": "https://github.com/lumkit/dockhand/releases/tag/v0.2.0",
              "draft": false,
              "assets": [
                {"name": "Dockhand-0.2.0.zip", "browser_download_url": "https://example.com/app.zip"},
                {"name": "Dockhand-0.2.0.dmg", "browser_download_url": "https://example.com/app.dmg"}
              ]
            }
        """.trimIndent()

        val update = parseGithubReleaseJson(json)

        assertEquals("0.2.0", update?.version)
        assertEquals("v0.2.0", update?.tagName)
        assertEquals("https://example.com/app.dmg", update?.downloadUrl)
        assertEquals("Changes", update?.body)
    }

    @Test
    fun fallsBackToReleasePageWithoutAssets() {
        val json = """
            {
              "tag_name": "0.3.0",
              "html_url": "https://github.com/lumkit/dockhand/releases/tag/0.3.0",
              "body": "",
              "assets": []
            }
        """.trimIndent()

        val update = parseGithubReleaseJson(json)

        assertEquals("0.3.0", update?.version)
        assertEquals("https://github.com/lumkit/dockhand/releases/tag/0.3.0", update?.downloadUrl)
    }
}
