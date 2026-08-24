package com.unciv.ui.screens.savescreens

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object Gzip {

    fun zip(data: String): String {
        val bos = ByteArrayOutputStream()
        zippedOutputStream(bos).use { it.write(data.toByteArray(Charsets.UTF_8)) }
        return bos.toString(Charsets.UTF_8.name())
    }

    fun unzip(data: String): String {
        return unzippedInputStream(ByteArrayInputStream(data.toByteArray(Charsets.UTF_8))).use {
            it.readBytes().toString(Charsets.UTF_8)
        }
    }

    /** Wraps [output]: bytes written to the result are gzip-compressed, then base64-encoded into [output]. */
    fun zippedOutputStream(output: OutputStream): OutputStream =
        GZIPOutputStream(Base64.getEncoder().wrap(output))

    /** Wraps [input], which must contain base64-encoded gzip data: bytes read from the result are the decompressed original. */
    fun unzippedInputStream(input: InputStream): InputStream =
        GZIPInputStream(Base64.getDecoder().wrap(input))

    /** Plain base64 encoding, no gzip - e.g. for encoding a checksum digest as text. */
    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}
