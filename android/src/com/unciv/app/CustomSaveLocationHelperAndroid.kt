package com.unciv.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import com.unciv.logic.CustomSaveLocationHelper
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.GameSaver.json

// The Storage Access Framework is available from API 19 and up:
// https://developer.android.com/guide/topics/providers/document-provider
@RequiresApi(Build.VERSION_CODES.KITKAT)
class CustomSaveLocationHelperAndroid(private val activity: Activity) : CustomSaveLocationHelper {
    // This looks a little scary but it's really not so bad. Whenever a load or save operation is
    // attempted, the game automatically autosaves as well (but on a separate thread), so we end up
    // with a race condition when trying to handle both operations in parallel. In order to work
    // around that, the callbacks are given an arbitrary index beginning at 100 and incrementing
    // each time, and this index is used as the requestCode for the call to startActivityForResult()
    // so that we can map it back to the corresponding callback when onActivityResult is called
    @GuardedBy("this")
    @Volatile
    private var callbackIndex = 100

    @GuardedBy("this")
    private val callbacks = ArrayList<IndexedCallback>()

    override fun saveGame(gameInfo: GameInfo, gameName: String, forcePrompt: Boolean, saveCompleteCallback: ((Exception?) -> Unit)?) {
        val callbackIndex = synchronized(this) {
            val index = callbackIndex++
            callbacks.add(IndexedCallback(
                    index,
                    { uri ->
                        if (uri != null) {
                            saveGame(gameInfo, uri)
                            saveCompleteCallback?.invoke(null)
                        } else {
                            saveCompleteCallback?.invoke(RuntimeException("Uri was null"))
                        }
                    }
            ))
            index
        }
        if (!forcePrompt && gameInfo.customSaveLocation != null) {
            handleIntentData(callbackIndex, Uri.parse(gameInfo.customSaveLocation))
            return
        }

        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, gameName)
            activity.startActivityForResult(this, callbackIndex)
        }
    }


    // This will be called on the main thread
    fun handleIntentData(requestCode: Int, uri: Uri?) {
        val callback = synchronized(this) {
            val index = callbacks.indexOfFirst { it.index == requestCode }
            if (index == -1) return
            callbacks.removeAt(index)
        }
        callback.thread.run {
            callback.callback(uri)
        }
    }

    private fun saveGame(gameInfo: GameInfo, uri: Uri) {
        gameInfo.customSaveLocation = uri.toString()
        activity.contentResolver.openOutputStream(uri, "rwt")
                ?.writer()
                ?.use {
                    it.write(json().toJson(gameInfo))
                }
    }

    override fun loadGame(loadCompleteCallback: (GameInfo?, Exception?) -> Unit) {
        val callbackIndex = synchronized(this) {
            val index = callbackIndex++
            callbacks.add(IndexedCallback(
                    index,
                    callback@{ uri ->
                        if (uri == null) return@callback
                        var exception: Exception? = null
                        val game = try {
                            activity.contentResolver.openInputStream(uri)
                                    ?.reader()
                                    ?.readText()
                                    ?.run {
                                        GameSaver.gameInfoFromString(this)
                                    }
                        } catch (e: Exception) {
                            exception = e
                            null
                        }
                        if (game != null) {
                            // If the user has saved the game from another platform (like Android),
                            // then the save location might not be right so we have to correct for that
                            // here
                            game.customSaveLocation = uri.toString()
                            loadCompleteCallback(game, null)
                        } else {
                            loadCompleteCallback(null, RuntimeException("Failed to load save game", exception))
                        }
                    }
            ))
            index
        }

        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            activity.startActivityForResult(this, callbackIndex)
        }
    }
}

data class IndexedCallback(
        val index: Int,
        val callback: (Uri?) -> Unit,
        val thread: Thread = Thread.currentThread()
)
