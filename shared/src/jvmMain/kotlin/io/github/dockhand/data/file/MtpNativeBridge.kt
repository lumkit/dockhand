package io.github.dockhand.data.file

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import java.lang.invoke.MethodHandle
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal data class NativeMtpDevice(
    val key: Long,
    val manufacturer: String,
    val model: String,
    val serial: String,
    val storages: List<NativeMtpStorage>,
)

internal data class NativeMtpStorage(
    val id: Long,
    val description: String,
    val freeSpace: Long,
    val totalCapacity: Long,
    val writable: Boolean,
)

internal data class NativeMtpDevices(
    val devices: List<NativeMtpDevice>,
)

internal data class NativeMtpEntry(
    val handle: Long,
    val storageId: Long,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedLocal: String?,
)

internal sealed interface BridgeResult<out T> {
    data class Success<T>(val value: T) : BridgeResult<T>
    data class Failure(val message: String) : BridgeResult<Nothing>
}

/**
 * FFM 绑定到我们自己的 Rust mtp-rs bridge。
 *
 * 原生库来自 native/mtp-bridge，由 Gradle 的 buildMtpBridge 任务构建并打进 resources。
 * 开发时可以设置 DOCKHAND_MTP_BRIDGE 指向任意已编译的 dylib。
 */
internal object MtpNativeBridge {

    private val linker: Linker = Linker.nativeLinker()
    private val lookupArena: Arena = Arena.ofShared()
    private val json = Json { ignoreUnknownKeys = true }

    private val library: SymbolLookup = run {
        val path = resolveLibraryPath()
        SymbolLookup.libraryLookup(path.toAbsolutePath().toString(), lookupArena)
    }

    private val storagesHandle: MethodHandle = downcall(
        "dockhand_mtp_storages",
        FunctionDescriptor.of(ValueLayout.ADDRESS),
    )
    private val listHandle: MethodHandle = downcall(
        "dockhand_mtp_list",
        FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
        ),
    )
    private val fastListHandle: MethodHandle = downcall(
        "dockhand_mtp_list_fast",
        FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
        ),
    )
    private val cancelListHandle: MethodHandle = downcall(
        "dockhand_mtp_cancel_list",
        FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG),
    )
    private val mkdirHandle: MethodHandle = downcall(
        "dockhand_mtp_mkdir",
        FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
        ),
    )
    private val uploadHandle: MethodHandle = downcall(
        "dockhand_mtp_upload",
        FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
        ),
    )
    private val downloadHandle: MethodHandle = downcall(
        "dockhand_mtp_download",
        FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
            ValueLayout.ADDRESS,
        ),
    )
    private val deleteHandle: MethodHandle = downcall(
        "dockhand_mtp_delete",
        FunctionDescriptor.of(
            ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG,
            ValueLayout.JAVA_LONG,
            ValueLayout.ADDRESS,
        ),
    )
    private val freeHandle: MethodHandle = downcall(
        "dockhand_mtp_free",
        FunctionDescriptor.ofVoid(ValueLayout.ADDRESS),
    )

    fun storages(): BridgeResult<NativeMtpDevices> = try {
        val data = callJson(storagesHandle)
        when (val result = parse(data)) {
            is BridgeResult.Failure -> result
            is BridgeResult.Success -> {
                val devices = result.value.jsonObject["devices"]?.jsonArray.orEmpty()
                BridgeResult.Success(
                    NativeMtpDevices(
                        devices = devices.mapNotNull { element ->
                            val item = element.jsonObject
                            val key = item["key"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                            val storages = item["storages"]?.jsonArray.orEmpty()
                            NativeMtpDevice(
                                key = key,
                                manufacturer = item["manufacturer"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                model = item["model"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                serial = item["serial"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                storages = storages.mapNotNull { storageElement ->
                                    val storage = storageElement.jsonObject
                                    val id = storage["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                                    NativeMtpStorage(
                                        id = id,
                                        description = storage["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                                            .ifEmpty { "MTP 存储" },
                                        freeSpace = storage["freeSpace"]?.jsonPrimitive?.longOrNull ?: 0L,
                                        totalCapacity = storage["totalCapacity"]?.jsonPrimitive?.longOrNull ?: 0L,
                                        writable = storage["writable"]?.jsonPrimitive?.booleanOrNull ?: false,
                                    )
                                },
                            )
                        }
                    )
                )
            }
        }
    } catch (error: Throwable) {
        BridgeResult.Failure("MTP 原生桥加载失败：${error.message ?: error.javaClass.simpleName}")
    }

    fun list(
        requestId: Long,
        deviceKey: Long,
        storageId: Long,
        path: String,
    ): BridgeResult<List<NativeMtpEntry>> {
        return withStringArg(path) { pathSegment ->
            parseEntries(
                data = callJson(listHandle, requestId, deviceKey, storageId, pathSegment),
                storageId = storageId,
            )
        }
    }

    /**
     * 只读取名称/目录/大小等索引字段，不逐对象读取修改时间。
     * 用于 MTP 大目录分页前的全量排序索引。
     */
    fun listFast(
        requestId: Long,
        deviceKey: Long,
        storageId: Long,
        path: String,
    ): BridgeResult<List<NativeMtpEntry>> {
        return withStringArg(path) { pathSegment ->
            parseEntries(
                data = callJson(fastListHandle, requestId, deviceKey, storageId, pathSegment),
                storageId = storageId,
            )
        }
    }

    private fun parseEntries(data: String, storageId: Long): BridgeResult<List<NativeMtpEntry>> {
        return when (val result = parse(data)) {
            is BridgeResult.Failure -> result
            is BridgeResult.Success -> BridgeResult.Success(
                result.value.jsonArray.mapNotNull { element ->
                    val item = element.jsonObject
                    val handle = item["handle"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
                    NativeMtpEntry(
                        handle = handle,
                        storageId = item["storageId"]?.jsonPrimitive?.longOrNull ?: storageId,
                        name = item["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        isDirectory = item["isDirectory"]?.jsonPrimitive?.booleanOrNull ?: false,
                        size = item["size"]?.jsonPrimitive?.longOrNull ?: 0L,
                        modifiedLocal = item["modifiedLocal"]?.jsonPrimitive?.contentOrNull,
                    )
                }
            )
        }
    }

    /** 取消一个正在执行的 list 请求；不等待列表完成。 */
    fun cancelList(requestId: Long) {
        runCatching {
            cancelListHandle.invokeWithArguments(requestId)
        }
    }

    fun mkdir(deviceKey: Long, storageId: Long, path: String): BridgeResult<Long> {
        return withStringArg(path) { pathSegment ->
            val data = callJson(mkdirHandle, deviceKey, storageId, pathSegment)
            when (val result = parse(data)) {
                is BridgeResult.Failure -> result
                is BridgeResult.Success -> {
                    val handle = result.value.jsonObject["handle"]?.jsonPrimitive?.longOrNull
                        ?: return@withStringArg BridgeResult.Failure("MTP 新建文件夹没有返回 handle")
                    BridgeResult.Success(handle)
                }
            }
        }
    }

    fun upload(deviceKey: Long, storageId: Long, localPath: String, remotePath: String): BridgeResult<Long> {
        val arena = Arena.ofConfined()
        return try {
            val local = arena.allocateFrom(localPath)
            val remote = arena.allocateFrom(remotePath)
            val data = callJson(uploadHandle, deviceKey, storageId, local, remote)
            when (val result = parse(data)) {
                is BridgeResult.Failure -> result
                is BridgeResult.Success -> {
                    val handle = result.value.jsonObject["handle"]?.jsonPrimitive?.longOrNull
                        ?: return BridgeResult.Failure("MTP 上传没有返回 handle")
                    BridgeResult.Success(handle)
                }
            }
        } catch (error: Throwable) {
            BridgeResult.Failure("MTP 上传失败：${error.message ?: error.javaClass.simpleName}")
        } finally {
            arena.close()
        }
    }

    fun download(
        deviceKey: Long,
        storageId: Long,
        remotePath: String,
        localPath: String,
    ): BridgeResult<Long> {
        val arena = Arena.ofConfined()
        return try {
            val remote = arena.allocateFrom(remotePath)
            val local = arena.allocateFrom(localPath)
            val data = callJson(downloadHandle, deviceKey, storageId, remote, local)
            when (val result = parse(data)) {
                is BridgeResult.Failure -> result
                is BridgeResult.Success -> BridgeResult.Success(
                    result.value.jsonObject["bytes"]?.jsonPrimitive?.longOrNull ?: 0L
                )
            }
        } catch (error: Throwable) {
            BridgeResult.Failure("MTP 下载失败：${error.message ?: error.javaClass.simpleName}")
        } finally {
            arena.close()
        }
    }

    fun delete(deviceKey: Long, storageId: Long, remotePath: String): BridgeResult<Unit> {
        return withStringArg(remotePath) { pathSegment ->
            val data = callJson(deleteHandle, deviceKey, storageId, pathSegment)
            when (val result = parse(data)) {
                is BridgeResult.Failure -> result
                is BridgeResult.Success -> BridgeResult.Success(Unit)
            }
        }
    }

    private fun <T> withStringArg(
        value: String,
        block: (MemorySegment) -> BridgeResult<T>,
    ): BridgeResult<T> {
        val arena = Arena.ofConfined()
        return try {
            block(arena.allocateFrom(value))
        } catch (error: Throwable) {
            BridgeResult.Failure("MTP 原生调用失败：${error.message ?: error.javaClass.simpleName}")
        } finally {
            arena.close()
        }
    }

    private fun callJson(handle: MethodHandle, vararg args: Any): String {
        val pointer = handle.invokeWithArguments(*args) as MemorySegment
        if (pointer.address() == 0L) {
            throw IllegalStateException("MTP native returned null")
        }
        return try {
            pointer.reinterpret(Long.MAX_VALUE).getString(0)
        } finally {
            freeHandle.invokeWithArguments(pointer)
        }
    }

    private fun parse(raw: String): BridgeResult<JsonElement> {
        return try {
            val root = json.parseToJsonElement(raw).jsonObject
            if (root["ok"]?.jsonPrimitive?.booleanOrNull == true) {
                BridgeResult.Success(root["data"] ?: JsonNull)
            } else {
                BridgeResult.Failure(
                    root["error"]?.jsonPrimitive?.contentOrNull
                        ?: "MTP 操作失败"
                )
            }
        } catch (error: Throwable) {
            BridgeResult.Failure("解析 MTP 返回失败：${error.message ?: error.javaClass.simpleName}")
        }
    }

    private fun downcall(name: String, descriptor: FunctionDescriptor): MethodHandle {
        val symbol = library.find(name).orElseThrow {
            IllegalStateException("MTP native bridge missing symbol: $name")
        }
        return linker.downcallHandle(symbol, descriptor)
    }

    private fun resolveLibraryPath(): Path {
        val override = System.getenv("DOCKHAND_MTP_BRIDGE")
        if (!override.isNullOrBlank()) {
            return Path.of(override)
        }

        val resource = "/native/libdockhand_mtp.dylib"
        val stream = MtpNativeBridge::class.java.getResourceAsStream(resource)
            ?: error("MTP native bridge is not packaged: $resource")
        val directory = Files.createTempDirectory("dockhand-mtp-native-")
        val target = directory.resolve("libdockhand_mtp.dylib")
        stream.use { input ->
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING)
        }
        target.toFile().deleteOnExit()
        directory.toFile().deleteOnExit()
        return target
    }
}
