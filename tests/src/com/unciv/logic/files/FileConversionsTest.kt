package com.unciv.logic.files

import com.badlogic.gdx.files.FileHandle
import com.unciv.json.json
import com.unciv.testing.BaseTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

@RunWith(BaseTestRunner::class)
class FileConversionsTest {
    class Payload {
        var name = ""
        var number = 0
    }

    /** Tracks whether [close] was called */
    private class TrackingStream(bytes: ByteArray) : ByteArrayInputStream(bytes) {
        var closed = false
        override fun close() { closed = true; super.close() }
    }

    private fun payload(nameLength: Int) = Payload().apply { name = "x".repeat(nameLength); number = 42 }
    private fun plainJson(p: Payload): String = json().toJson(p)
    private fun stream(text: String): InputStream = ByteArrayInputStream(text.toByteArray(Charsets.UTF_8))

    private fun assertRoundTrip(expected: Payload, actual: Payload?) {
        assertEquals(expected.name, actual!!.name)
        assertEquals(expected.number, actual.number)
    }

    @Test
    fun readsZippedJson() {
        val expected = payload(10)
        assertRoundTrip(expected, FileConversions.readJson(stream(FileConversions.zip(plainJson(expected))), Payload::class.java))
    }

    @Test
    fun readsPlainJsonShorterThanMarkWindow() {
        val expected = payload(10)
        assertRoundTrip(expected, FileConversions.readJson(stream(plainJson(expected)), Payload::class.java))
    }

    @Test
    fun readsPlainJsonLongerThanMarkWindow() {
        val expected = payload(5000)
        assertTrue(plainJson(expected).length > 1024)
        assertRoundTrip(expected, FileConversions.readJson(stream(plainJson(expected)), Payload::class.java))
    }

    @Test
    fun readsLargeZippedJson() {
        val expected = payload(2_000_000)
        assertRoundTrip(expected, FileConversions.readJson(stream(FileConversions.zip(plainJson(expected))), Payload::class.java))
    }

    @Test
    fun emptyStreamReturnsNull() {
        assertNull(FileConversions.readJson(stream(""), Payload::class.java))
    }

    @Test
    fun closesStream() {
        val zipped = TrackingStream(FileConversions.zip(plainJson(payload(10))).toByteArray())
        FileConversions.readJson(zipped, Payload::class.java)
        assertTrue(zipped.closed)

        val plain = TrackingStream(plainJson(payload(10)).toByteArray())
        FileConversions.readJson(plain, Payload::class.java)
        assertTrue(plain.closed)
    }

    private fun withTempFile(content: String, block: (FileHandle) -> Unit) {
        val file = File.createTempFile("FileConversionsTest", ".json")
        try {
            file.writeText(content, Charsets.UTF_8)
            block(FileHandle(file))
        } finally {
            file.delete()
        }
    }

    @Test
    fun readsZippedJsonFromFile() {
        val expected = payload(5000)
        withTempFile(FileConversions.zip(plainJson(expected))) {
            assertRoundTrip(expected, FileConversions.readJson(it, Payload::class.java))
        }
    }

    @Test
    fun readsPlainJsonFromFile() {
        val expected = payload(5000)
        withTempFile(plainJson(expected)) {
            assertRoundTrip(expected, FileConversions.readJson(it, Payload::class.java))
        }
    }

    @Test
    fun readsShortPlainJsonFromFile() {
        val expected = payload(10)
        withTempFile(plainJson(expected)) {
            assertRoundTrip(expected, FileConversions.readJson(it, Payload::class.java))
        }
    }

    @Test
    fun writeJsonThenReadJsonRoundTripsZippedAndPlain() {
        val expected = payload(5000)
        for (zip in listOf(true, false)) {
            val file = File.createTempFile("FileConversionsTest", ".json")
            try {
                val handle = FileHandle(file)
                FileConversions.writeJson(handle, expected, zip)
                assertRoundTrip(expected, FileConversions.readJson(handle, Payload::class.java))
            } finally {
                file.delete()
            }
        }
    }
}
