package com.unciv.logic.multiplayer.chat

import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.json.json
import com.unciv.utils.Log
import java.util.UUID

/**
 * Local on-device persistence for multiplayer chat history.
 * Never uploads to the multiplayer server — files live under [FOLDER] next to GameSettings.
 */
object ChatHistoryPersistence {
    const val FOLDER = "MultiplayerChat"
    const val MAX_MESSAGES = 200

    class StoredChat {
        var messages: ArrayList<StoredMessage> = arrayListOf()
    }

    class StoredMessage {
        var civName: String = ""
        var message: String = ""
    }

    fun isEnabled(): Boolean {
        if (!UncivGame.isCurrentInitialized()) return false
        return try {
            UncivGame.Current.settings.multiplayer.saveChatHistory
        } catch (_: UninitializedPropertyAccessException) {
            false
        }
    }

    fun loadMessages(gameId: UUID): List<Pair<String, String>>? {
        if (!isEnabled()) return null
        return readFrom(fileFor(gameId))
    }

    fun saveMessages(gameId: UUID, messages: List<Pair<String, String>>) {
        if (!isEnabled()) return
        writeTo(fileFor(gameId), messages)
    }

    /** Round-trip helpers used by production I/O and unit tests. */
    fun trimMessages(messages: List<Pair<String, String>>): List<Pair<String, String>> {
        if (messages.size <= MAX_MESSAGES) return messages
        return messages.takeLast(MAX_MESSAGES)
    }

    fun readFrom(file: FileHandle): List<Pair<String, String>>? {
        if (!file.exists()) return null
        return try {
            val stored = json().fromJson(StoredChat::class.java, file)
            stored.messages.map { it.civName to it.message }
        } catch (ex: Exception) {
            Log.error("Failed to load chat history from ${file.path()}", ex)
            null
        }
    }

    fun writeTo(file: FileHandle, messages: List<Pair<String, String>>) {
        try {
            val trimmed = trimMessages(messages)
            val stored = StoredChat().apply {
                this.messages = ArrayList(trimmed.map { pair ->
                    StoredMessage().apply {
                        civName = pair.first
                        message = pair.second
                    }
                })
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
