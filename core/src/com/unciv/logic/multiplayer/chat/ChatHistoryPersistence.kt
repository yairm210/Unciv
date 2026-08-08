package com.unciv.logic.multiplayer.chat

import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.utils.Log
import java.util.UUID

/**
 * Local on-device persistence for multiplayer chat history.
 * Never uploads to the multiplayer server — files live under [FOLDER] in the local files root
 * (one JSON file per game id).
 */
object ChatHistoryPersistence {
    const val FOLDER = "MultiplayerChat"
    const val MAX_MESSAGES = 200

    class StoredChat {
        var messages: ArrayList<Chat.Line> = arrayListOf()
    }

    fun isEnabled(): Boolean {
        if (!UncivGame.isCurrentInitialized()) return false
        return UncivGame.Current.settings.multiplayer.saveChatHistory
    }

    fun loadMessages(gameId: UUID): List<Chat.Line>? {
        if (!isEnabled()) return null
        return readFrom(fileFor(gameId))
    }

    fun saveMessages(gameId: UUID, messages: List<Chat.Line>) {
        if (!isEnabled()) return
        writeTo(fileFor(gameId), messages)
    }

    fun deleteForGame(gameId: UUID) {
        if (!UncivGame.isCurrentInitialized()) return
        try {
            val file = fileFor(gameId)
            if (file.exists()) file.delete()
        } catch (ex: Exception) {
            Log.error("Failed to delete chat history for $gameId", ex)
        }
    }

    /** Round-trip helpers used by production I/O and unit tests. */
    fun trimMessages(messages: List<Chat.Line>): List<Chat.Line> {
        if (messages.size <= MAX_MESSAGES) return messages
        return messages.takeLast(MAX_MESSAGES)
    }

    fun readFrom(file: FileHandle): List<Chat.Line>? {
        if (!file.exists()) return null
        return try {
            val stored = json().fromJson(StoredChat::class.java, file)
            stored.messages.toList()
        } catch (ex: Exception) {
            Log.error("Failed to load chat history from ${file.path()}", ex)
            null
        }
    }

    fun writeTo(file: FileHandle, messages: List<Chat.Line>) {
        try {
            val trimmed = trimMessages(messages)
            val stored = StoredChat().apply {
                this.messages = ArrayList(trimmed)
            }
            file.parent().mkdirs()
            file.writeString(json().toJson(stored), false, Charsets.UTF_8.name())
        } catch (ex: Exception) {
            Log.error("Failed to save chat history to ${file.path()}", ex)
        }
    }

    private fun fileFor(gameId: UUID): FileHandle =
        UncivGame.Current.files.getLocalFile("$FOLDER/$gameId.json")
}
