package com.unciv.ui.screens.savescreens

import com.badlogic.gdx.utils.Base64Coder
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream


object Gzip {

    fun zip(data: String): String = encode(compress(data))
    fun unzip(data: String): String = decompress(decode(data))

    private fun compress(data: String): ByteArray {
        val bos = ByteArrayOutputStream(data.length)
        val gzip = object : GZIPOutputStream(bos) {
            init {
                def.setLevel(Deflater.BEST_COMPRESSION)
            }
        }
        gzip.write(data.toByteArray())
        gzip.close()
        val compressed = bos.toByteArray()
        bos.close()
        return compressed
    }

    private fun decompress(compressed: ByteArray): String {
        val bis = ByteArrayInputStream(compressed)
        val gis = GZIPInputStream(bis)
        val br = BufferedReader(InputStreamReader(gis, Charsets.UTF_8))
        val sb = StringBuilder()
        var line: String? = br.readLine()
        while (line != null) {
            sb.append(line)
            line = br.readLine()
        }
        br.close()
        gis.close()
        bis.close()
        return sb.toString()
    }


    fun encode(bytes: ByteArray): String {
        return String(Base64Coder.encode(bytes))
    }

    private fun decode(base64Str: String): ByteArray {
        return Base64Coder.decode(base64Str)
    }
}
