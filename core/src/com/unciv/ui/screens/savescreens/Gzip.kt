package com.unciv.ui.screens.savescreens

import com.badlogic.gdx.utils.Base64Coder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object Gzip {

    fun zip(data: String): String = encode(compress(data))
    fun unzip(data: String): String  = decompress(decode(data))

    private fun compress(data: String): ByteArray {
        val bos = ByteArrayOutputStream(data.length)
        val gzip = GZIPOutputStream(bos)
        gzip.write(data.toByteArray())
        gzip.close()
        val compressed = bos.toByteArray()
        bos.close()
        return compressed
    }

    private fun decompress(compressed: ByteArray): String {
        val bis = ByteArrayInputStream(compressed)
        val gis = GZIPInputStream(bis)
        val buffer = ByteArray(8192)
        val bos = ByteArrayOutputStream(compressed.size.coerceAtLeast(8192))
        var read = gis.read(buffer)
        while (read > 0) {
            bos.write(buffer, 0, read)
            read = gis.read(buffer)
        }
        gis.close()
        bis.close()
        return String(bos.toByteArray(), Charsets.UTF_8)
    }


    fun encode(bytes: ByteArray): String {
        return String(Base64Coder.encode(bytes))
    }

    private fun decode(base64Str: String): ByteArray {
        return Base64Coder.decode(base64Str)
    }
}
