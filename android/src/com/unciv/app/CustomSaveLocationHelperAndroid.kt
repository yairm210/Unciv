package com.unciv.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.unciv.logic.CustomSaveLocationHelper
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.GameSaver.json
import java.util.concurrent.atomic.AtomicReference

const val REQ_SAVE_GAME = 1
const val REQ_LOAD_GAME = 2

// The Storage Access Framework is available from API 19 and up:
// https://developer.android.com/guide/topics/providers/document-provider
@RequiresApi(Build.VERSION_CODES.KITKAT)
class CustomSaveLocationHelperAndroid(private val activity: Activity) : CustomSaveLocationHelper {
    private val callback = AtomicReference<Pair<Thread, (Uri?) -> Unit>?>()

    override fun saveGame(gameInfo: GameInfo, gameName: String, forcePrompt: Boolean, block: (() -> Unit)?) {
        callback.set(Thread.currentThread() to { uri ->
            uri?.let {
                saveGame(gameInfo, it)
            }
            block?.invoke()
        })
        gameInfo.customSaveLocation?.let { customSaveLocation ->
            if (!forcePrompt) {
                handleIntentData(Uri.parse(customSaveLocation))
                return
            }
        }
        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, gameName)
            activity.startActivityForResult(this, REQ_SAVE_GAME)
        }
    }


    // This will be called on the main thread
    fun handleIntentData(uri: Uri?) {
        callback.getAndSet(null)?.let {
            it.first.run {
                it.second.invoke(uri)
            }
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

    override fun loadGame(block: (GameInfo) -> Unit) {
        callback.set(Thread.currentThread() to { uri ->
            uri?.let {
                val game = activity.contentResolver.openInputStream(it)
                                ?.reader()
                                ?.readText()
                                ?.run {
                                    GameSaver.gameInfoFromString(this)
                                }?: return@let
                // If the user has saved the game from another platform (like Android),
                // then the save location might not be right so we have to correct for that
                // here
                game.customSaveLocation = it.toString()
                block(game)
            }
        })

        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            activity.startActivityForResult(this, REQ_LOAD_GAME)
        }
    }
}