package com.unciv.ui.saves

import com.badlogic.gdx.utils.Base64Coder
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object Gzip {

    fun zip(data:String):String = encoder(compress(data))
    fun unzip(data:String):String  = decompress(decoder(data))

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
        val br = BufferedReader(InputStreamReader(gis, "UTF-8"))
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


    private fun encoder(bytes:ByteArray): String{
        return String(Base64Coder.encode(bytes))
    }

    private fun decoder(base64Str: String): ByteArray{
        return Base64Coder.decode(base64Str)
    }
}