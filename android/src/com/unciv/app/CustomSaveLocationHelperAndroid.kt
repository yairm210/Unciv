package com.unciv.app

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.unciv.logic.CustomSaveLocationHelper
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.GameSaver.json
import java.util.concurrent.atomic.AtomicReference

const val REQ_SAVE_GAME = 1
const val REQ_LOAD_GAME = 2
const val REQ_WRITE_STORAGE = 3

// The Storage Access Framework is available from API 19 and up:
// https://developer.android.com/guide/topics/providers/document-provider
@RequiresApi(Build.VERSION_CODES.KITKAT)
class CustomSaveLocationHelperAndroid(private val activity: Activity) : CustomSaveLocationHelper {
    private val callback = AtomicReference<Pair<Thread, (Uri?) -> Unit>?>()
    private var cachedSaveRequest: SaveRequest? = null
        @Synchronized get
        @Synchronized set

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

        // For some reason it seems you can save to an existing file that you open without the
        // permission, but you can't write to a file that you've requested be created so if this is
        // a "Save as" operation then we need to get permission to write first
        if (ContextCompat.checkSelfPermission(activity, WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cachedSaveRequest = SaveRequest(gameInfo, gameName, forcePrompt, block)
            activity.requestPermissions(arrayOf(WRITE_EXTERNAL_STORAGE), REQ_WRITE_STORAGE)
            return
        }

        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, gameName)
            activity.startActivityForResult(this, REQ_SAVE_GAME)
        }
    }

    fun continuePreviousRequest() {
        cachedSaveRequest?.let {
            saveGame(it.gameInfo, it.gameName, it.forcePrompt, it.block)
            cachedSaveRequest = null
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
                        } ?: return@let
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

data class SaveRequest(
        val gameInfo: GameInfo,
        val gameName: String,
        val forcePrompt: Boolean,
        val block: (() -> Unit)?
)