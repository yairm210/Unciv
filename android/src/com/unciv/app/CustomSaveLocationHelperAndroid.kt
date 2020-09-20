package com.unciv.app

import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.unciv.logic.CustomSaveLocationHelper
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.GameSaver.json

const val REQ_WRITE_STORAGE = 3

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

    /**
     * Used to keep track of the callback that's saved before requesting write permissions so we can
     * continue where we left off once the permission is granted
     */
    @GuardedBy("this")
    private var pendingCallbackIndex: Pair<Int, String>? = null
        @Synchronized get
        @Synchronized set

    override fun saveGame(gameInfo: GameInfo, gameName: String, forcePrompt: Boolean, block: (() -> Unit)?) {
        val callbackIndex = synchronized(this) {
            val index = callbackIndex++
            callbacks.add(IndexedCallback(
                    index,
                    { uri ->
                        uri?.let {
                            saveGame(gameInfo, it)
                        }
                        block?.invoke()
                    }
            ))
            index
        }
        gameInfo.customSaveLocation?.let { customSaveLocation ->
            if (!forcePrompt) {
                handleIntentData(callbackIndex, Uri.parse(customSaveLocation))
                return
            }
        }

        // For some reason it seems you can save to an existing file that you open without the
        // permission, but you can't write to a file that you've requested be created so if this is
        // a "Save as" operation then we need to get permission to write first
        if (ContextCompat.checkSelfPermission(activity, WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingCallbackIndex = callbackIndex to gameName
            activity.requestPermissions(arrayOf(WRITE_EXTERNAL_STORAGE), REQ_WRITE_STORAGE)
            return
        }

        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, gameName)
            activity.startActivityForResult(this, callbackIndex)
        }
    }

    fun continuePreviousRequest() {
        pendingCallbackIndex?.let {
            Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, it.second)
                activity.startActivityForResult(this, it.first)
            }
            pendingCallbackIndex = null
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

    override fun loadGame(block: (GameInfo) -> Unit) {
        val callbackIndex = synchronized(this) {
            val index = callbackIndex++
            callbacks.add(IndexedCallback(
                    index,
                    { uri ->
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
