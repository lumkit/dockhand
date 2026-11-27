package io.github.lumkit.dockhand.data.clipboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.File
import java.net.URI
import java.nio.file.Path

private val uriListFlavor: DataFlavor by lazy {
    DataFlavor("text/uri-list;class=java.lang.String")
}

class AwtFileClipboard : FileClipboard {
    override suspend fun copyLocalPaths(paths: List<String>): Boolean = withContext(Dispatchers.Swing) {
        runCatching {
            val files = paths.map(::File).filter { it.exists() }
            if (files.isEmpty()) return@runCatching false
            Toolkit.getDefaultToolkit().systemClipboard.setContents(FileListTransferable(files), null)
            true
        }.getOrDefault(false)
    }

    override suspend fun readLocalPaths(): List<String> = withContext(Dispatchers.Swing) {
        runCatching { readPaths(Toolkit.getDefaultToolkit().systemClipboard) }
            .getOrDefault(emptyList())
    }

    private fun readPaths(clipboard: Clipboard): List<String> {
        val files = runCatching {
            val data = clipboard.getData(DataFlavor.javaFileListFlavor) as? List<*>
            data.orEmpty().mapNotNull { (it as? File)?.absolutePath }
        }.getOrDefault(emptyList())
        if (files.isNotEmpty()) return files

        val uriList = runCatching {
            clipboard.getData(uriListFlavor) as? String
        }.getOrNull() ?: return emptyList()

        return uriList.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line -> runCatching { Path.of(URI(line)).toString() }.getOrNull() }
            .toList()
    }
}

private class FileListTransferable(
    private val files: List<File>,
) : Transferable {
    override fun getTransferDataFlavors(): Array<DataFlavor> =
        arrayOf(DataFlavor.javaFileListFlavor, uriListFlavor)

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
        flavor == DataFlavor.javaFileListFlavor || flavor == uriListFlavor

    override fun getTransferData(flavor: DataFlavor): Any = when {
        flavor == DataFlavor.javaFileListFlavor -> files
        flavor == uriListFlavor ->
            files.joinToString("\r\n") { it.toURI().toASCIIString() }
        else -> throw UnsupportedFlavorException(flavor)
    }
}