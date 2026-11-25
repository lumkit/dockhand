package io.github.lumkit.dockhand.core.platform

import io.github.lumkit.dockhand.domain.model.FileBrowserException
import io.github.lumkit.dockhand.domain.model.FileLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

class DesktopFileOpener : FileOpener {
    private val cacheDir: File = File(defaultAppCacheDirectory(), "open-with").apply { mkdirs() }
    private val iconDir: File = File(cacheDir, "app-icons").apply { mkdirs() }
    private val cachedHelper: File? by lazy { extractHelper() }

    override suspend fun open(location: FileLocation) = withContext(Dispatchers.IO) {
        val file = File(location.path)
        if (!file.exists()) throw FileBrowserException("文件不存在")
        try {
            if (Desktop.isDesktopSupported() &&
                Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
            ) {
                Desktop.getDesktop().open(file)
            } else {
                val process = ProcessBuilder("open", file.absolutePath).start()
                if (process.waitFor() != 0) {
                    throw FileBrowserException("无法打开文件")
                }
            }
        } catch (error: FileBrowserException) {
            throw error
        } catch (error: Throwable) {
            throw FileBrowserException(error.message ?: "无法打开文件")
        }
    }

    override suspend fun applications(location: FileLocation): List<ExternalApplication> =
        withContext(Dispatchers.IO) {
            val file = File(location.path)
            if (!file.exists()) throw FileBrowserException("文件不存在")
            helper()?.let { helper ->
                val process = ProcessBuilder(
                    helper.absolutePath, "list", file.absolutePath, iconDir.absolutePath,
                ).redirectErrorStream(true).start()
                if (process.waitFor(10, TimeUnit.SECONDS)) {
                    val output = process.inputStream.bufferedReader().use { it.readText() }
                    runCatching {
                        Json.parseToJsonElement(output).jsonArray.mapNotNull { element ->
                            val obj = element.jsonObject
                            val path = obj["path"]?.jsonPrimitive?.content ?: return@mapNotNull null
                            val name = obj["name"]?.jsonPrimitive?.contentOrNull
                                ?: File(path).name.removeSuffix(".app")
                            ExternalApplication(
                                name = name,
                                path = path,
                                iconPath = obj["icon"]?.jsonPrimitive?.contentOrNull
                                    ?.let { File(it).toURI().toString() },
                            )
                        }
                    }.getOrNull()?.let { return@withContext it }
                } else {
                    process.destroyForcibly()
                }
            }
            val script = """
                ObjC.import("AppKit");
                function run(argv) {
                    var url = $.NSURL.fileURLWithPath(argv[0]);
                    var apps = $.NSWorkspace.sharedWorkspace.URLsForApplicationsToOpenURL(url);
                    var result = [];
                    for (var i = 0; i < apps.count; i++) {
                        result.push(apps.objectAtIndex(i).path.js);
                    }
                    return result.join("\n");
                }
            """.trimIndent()
            val process = ProcessBuilder(
                "/usr/bin/osascript",
                "-l",
                "JavaScript",
                "-e",
                script,
                file.absolutePath,
            ).redirectErrorStream(true).start()
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw FileBrowserException("读取打开方式超时")
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            output.lineSequence()
                .filter { it.endsWith(".app", ignoreCase = true) }
                .distinct()
                .map { path ->
                    ExternalApplication(
                        name = File(path).name.removeSuffix(".app"),
                        path = path,
                    )
                }
                .sortedBy { it.name.lowercase() }
                .toList()
        }

    private fun helper(): File? = cachedHelper

    private fun extractHelper(): File? {
        val resource = javaClass.getResourceAsStream("/native/open-with-helper") ?: return null
        return runCatching {
            val dir = File(cacheDir, "native").apply { mkdirs() }
            val target = File(dir, "open-with-helper")
            resource.use { input -> target.outputStream().use { input.copyTo(it) } }
            target.setExecutable(true)
            target
        }.getOrNull()
    }

    override suspend fun icon(application: ExternalApplication): String? =
        withContext(Dispatchers.IO) {
            val target = File(iconDir, application.path.hashCode().toString() + ".png")
            if (target.isFile) return@withContext target.toURI().toString()
            val script = """
                ObjC.import("AppKit");
                function run(argv) {
                    var icon = $.NSWorkspace.sharedWorkspace.iconForFile(argv[0]);
                    var tiff = icon.TIFFRepresentation;
                    var rep = $.NSBitmapImageRep.imageRepWithData(tiff);
                    var png = rep.representationUsingTypeProperties($.NSPNGFileType, $());
                    return png.base64EncodedStringWithOptions(0).js;
                }
            """.trimIndent()
            runCatching {
                val process = ProcessBuilder(
                    "/usr/bin/osascript", "-l", "JavaScript", "-e", script, application.path,
                ).redirectErrorStream(true).start()
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    return@runCatching null
                }
                val base64 = process.inputStream.bufferedReader().use { it.readText() }.trim()
                if (base64.isEmpty()) return@runCatching null
                target.writeBytes(Base64.getDecoder().decode(base64))
                target.toURI().toString()
            }.getOrNull()
        }

    override suspend fun openWith(
        location: FileLocation,
        application: ExternalApplication,
    ) = withContext(Dispatchers.IO) {
        val file = File(location.path)
        if (!file.exists()) throw FileBrowserException("文件不存在")
        try {
            val process = ProcessBuilder(
                "/usr/bin/open",
                "-a",
                application.path,
                file.absolutePath,
            ).inheritIO().start()
            if (process.waitFor() != 0) {
                throw FileBrowserException("无法使用 " + application.name + " 打开文件")
            }
        } catch (error: FileBrowserException) {
            throw error
        } catch (error: Throwable) {
            throw FileBrowserException(error.message ?: "无法打开文件")
        }
    }
}
