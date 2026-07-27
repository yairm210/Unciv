package com.unciv.logic.multiplayer.chat

import com.badlogic.gdx.files.FileHandle
import com.unciv.testing.GdxTestRunner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.file.Files

@RunWith(GdxTestRunner::class)
class ChatHistoryPersistenceTests {

    @Test
    fun `round-trip save and load`() {
        val dir = FileHandle(Files.createTempDirectory("chat-hist-rt").toFile())
        val file = dir.child("game.json")
        val messages = listOf(
            "System" to "Welcome to Chat!",
            "Rome" to "hello",
            "Egypt" to "hi there",
        )

        ChatHistoryPersistence.writeTo(file, messages)
        assertTrue(file.exists())

        val loaded = ChatHistoryPersistence.readFrom(file)
        assertEquals(messages, loaded)

        dir.deleteDirectory()
    }

    @Test
    fun `trim keeps only the last MAX_MESSAGES`() {
        val overflow = ChatHistoryPersistence.MAX_MESSAGES + 50
        val messages = (1..overflow).map { "Civ$it" to "msg$it" }

        val trimmed = ChatHistoryPersistence.trimMessages(messages)
        assertEquals(ChatHistoryPersistence.MAX_MESSAGES, trimmed.size)
        assertEquals("Civ${overflow - ChatHistoryPersistence.MAX_MESSAGES + 1}", trimmed.first().first)
        assertEquals("Civ$overflow", trimmed.last().first)

        val dir = FileHandle(Files.createTempDirectory("chat-hist-trim").toFile())
        val file = dir.child("game.json")
        ChatHistoryPersistence.writeTo(file, messages)
        val loaded = ChatHistoryPersistence.readFrom(file)!!
        assertEquals(ChatHistoryPersistence.MAX_MESSAGES, loaded.size)
        assertEquals(trimmed, loaded)

        dir.deleteDirectory()
    }

    @Test
    fun `missing file returns null`() {
        val dir = FileHandle(Files.createTempDirectory("chat-hist-miss").toFile())
        assertNull(ChatHistoryPersistence.readFrom(dir.child("nope.json")))
        dir.deleteDirectory()
    }
}
