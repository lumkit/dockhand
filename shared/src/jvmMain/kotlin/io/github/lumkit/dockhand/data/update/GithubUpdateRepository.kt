package io.github.lumkit.dockhand.data.update

import io.github.lumkit.dockhand.core.app.APP_VERSION
import io.github.lumkit.dockhand.core.update.AppUpdate
import io.github.lumkit.dockhand.core.update.UpdateRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 通过 GitHub Release API 检测更新，HTTP 请求使用 Ktor Client + CIO engine。
 *
 * 优先请求 /releases/latest；仓库只有预发布 Release 时该接口返回 404，
 * 再退回到 /releases 取最新非 draft。公开仓库不需要 token。
 */
class GithubUpdateRepository(
    private val owner: String = "lumkit",
    private val repo: String = "dockhand",
) : UpdateRepository {

    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 10_000
            }
            defaultRequest {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                header(HttpHeaders.UserAgent, "Dockhand/$APP_VERSION")
            }
        }
    }

    override suspend fun latestUpdate(): AppUpdate? = try {
        fetchLatest()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

    private suspend fun fetchLatest(): AppUpdate? {
        val base = "https://api.github.com/repos/$owner/$repo"
        val latestResponse = client.get("$base/releases/latest")
        val latestBody = latestResponse.bodyAsText()
        if (latestResponse.status.value == 200) {
            return parseGithubReleaseJson(latestBody)
        }
        if (latestResponse.status.value != 404) return null

        // 只有预发布 Release 时 /latest 返回 404，退回到列表取最新非 draft。
        val listResponse = client.get("$base/releases?per_page=10")
        val listBody = listResponse.bodyAsText()
        if (listResponse.status.value != 200) return null
        val release = Json.parseToJsonElement(listBody).jsonArray.firstOrNull { element ->
            element.jsonObject["draft"]?.jsonPrimitive?.booleanOrNull != true
        } ?: return null
        return parseGithubReleaseElement(release.jsonObject)
    }
}

/** 测试用：直接解析 GitHub Release JSON。 */
internal fun parseGithubReleaseJson(body: String): AppUpdate? =
    parseGithubReleaseElement(Json.parseToJsonElement(body).jsonObject)

private fun parseGithubReleaseElement(obj: JsonObject): AppUpdate? {
    val tag = obj["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
    val htmlUrl = obj["html_url"]?.jsonPrimitive?.contentOrNull ?: return null
    if (obj["draft"]?.jsonPrimitive?.booleanOrNull == true) return null
    val name = obj["name"]?.jsonPrimitive?.contentOrNull
    val bodyText = obj["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val assets = obj["assets"]?.jsonArray?.mapNotNull { assetElement ->
        val asset = assetElement.jsonObject
        val assetName = asset["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val assetUrl = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull
            ?: return@mapNotNull null
        assetName to assetUrl
    }.orEmpty()
    val downloadUrl = assets.firstOrNull { it.first.endsWith(".dmg", ignoreCase = true) }?.second
        ?: assets.firstOrNull { it.first.endsWith(".zip", ignoreCase = true) }?.second
        ?: htmlUrl
    return AppUpdate(
        version = tag.removePrefix("v").removePrefix("V"),
        tagName = tag,
        name = name?.takeIf { it.isNotBlank() },
        body = bodyText,
        pageUrl = htmlUrl,
        downloadUrl = downloadUrl,
    )
}
