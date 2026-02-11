package com.unciv.logic.files

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.logic.UncivShowableException
import com.unciv.utils.Log
import org.teavm.jso.typedarrays.ArrayBuffer
import org.teavm.jso.typedarrays.Int8Array
import java.io.FileFilter

object WebFileChooser {
    private val binaryExtensions = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")

    fun openLoadDialog(filter: FileFilter, resultListener: ResultListener?) {
        val extensions = extractExtensions(filter)
        val wantsBinary = extensions.any { binaryExtensions.contains(it) }
        if (wantsBinary) {
            WebFileInterop.loadBinary(
                extensions.toTypedArray(),
                { buffer, name ->
                    val handle = writeBinary(buffer, name)
                    resultListener?.invoke(true, handle)
                },
                { message ->
                    handleError(message, resultListener)
                }
            )
        } else {
            WebFileInterop.loadText(
                extensions.toTypedArray(),
                { data, name ->
                    val handle = writeText(data, name)
                    resultListener?.invoke(true, handle)
                },
                { message ->
                    handleError(message, resultListener)
                }
            )
        }
    }

    private fun extractExtensions(filter: FileFilter): List<String> {
        val extensionFilter = filter as? ExtensionFileFilter
        if (extensionFilter != null) {
            return extensionFilter.extensions.map { it.trimStart('.').lowercase() }
        }
        return emptyList()
    }

    private fun writeText(data: String, name: String): FileHandle {
        val handle = tempHandle(name)
        handle.writeString(data, false, Charsets.UTF_8.name())
        return handle
    }

    private fun writeBinary(buffer: ArrayBuffer, name: String): FileHandle {
        val bytes = bufferToBytes(buffer)
        val handle = tempHandle(name)
        handle.writeBytes(bytes, false)
        return handle
    }

    private fun tempHandle(rawName: String): FileHandle {
        val safeName = sanitizeName(rawName)
        val folder = Gdx.files.local("web-file-chooser")
        folder.mkdirs()
        return folder.child(safeName)
    }

    private fun sanitizeName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
        if (base.isBlank()) return "unciv-file"
        return base.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun bufferToBytes(buffer: ArrayBuffer): ByteArray {
        val view = Int8Array(buffer)
        val length = view.length
        val out = ByteArray(length)
        for (i in 0 until length) {
            out[i] = view[i].toByte()
        }
        return out
    }

    private fun handleError(message: String?, resultListener: ResultListener?) {
        val text = message?.trim().orEmpty()
        if (text.equals("CANCELLED", ignoreCase = true)) {
            resultListener?.invoke(false, Gdx.files.local(""))
            return
        }
        Log.error("Web file chooser failed", UncivShowableException(text.ifEmpty { "File operation failed" }))
        resultListener?.invoke(false, Gdx.files.local(""))
    }
}
