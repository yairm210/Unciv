package com.unciv.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.annotation.GuardedBy
import com.unciv.logic.CustomFileLocationHelper
import com.unciv.logic.SuccessfulLoadResult
import com.unciv.ui.crashhandling.postCrashHandlingRunnable

class CustomFileLocationHelperAndroid(private val activity: Activity) : CustomFileLocationHelper {

    @GuardedBy("this")
    private val callbacks = mutableListOf<ActivityCallback>()
    @GuardedBy("this")
    private var curActivityRequestCode = 100

    override fun saveGame(
        gameData: String,
        suggestedLocation: String,
        saveCompleteCallback: ((String?, Exception?) -> Unit)?
    ) {

        val requestCode = createActivityCallback { uri ->
            if (uri == null) {
                saveCompleteCallback?.invoke(null, null)
                return@createActivityCallback
            }

            try {
                activity.contentResolver.openOutputStream(uri, "rwt")
                    ?.writer()
                    ?.use {
                        it.write(gameData)
                    }
                saveCompleteCallback?.invoke(uri.toString(), null)
            } catch (ex: Exception) {
                saveCompleteCallback?.invoke(null, ex)
            }
        }

        // When we loaded, we returned a "content://" URI as file location.
        val uri = Uri.parse(suggestedLocation)
        val fileName = if (uri.scheme == "content") {
            val cursor = activity.contentResolver.query(uri, null, null, null, null)
            cursor.use {
                // we should have a direct URI to a file, so first is enough
                if (it?.moveToFirst() == true) {
                    it.getString(it.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                } else {
                    ""
                }
            }
        } else {
            // if we didn't load, this is some file name entered by the user
            suggestedLocation
        }

        Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, fileName)
            if (uri.scheme == "content") {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
            }
            activity.startActivityForResult(this, requestCode)
        }
    }


    override fun loadGame(loadCompleteCallback: (SuccessfulLoadResult?, Exception?) -> Unit) {
        val callbackIndex = createActivityCallback { uri ->
            if (uri == null) {
                loadCompleteCallback(null, null)
                return@createActivityCallback
            }

            var gameData: String? = null
            var exception: Exception? = null
            try {
                activity.contentResolver.openInputStream(uri)
                    ?.reader()
                    ?.use {
                        gameData = it.readText()
                    }
            } catch (e: Exception) {
                exception = e
            }

            if (exception != null) {
                loadCompleteCallback(null, Exception("Failed to load save game", exception))
            } else if (gameData != null) {
                loadCompleteCallback(SuccessfulLoadResult(uri.toString(), gameData!!), null)
            }
        }

        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            // It is theoretically possible to use an initial URI here, however, the only Android URIs we have are obtained from here, so, no dice
            activity.startActivityForResult(this, callbackIndex)
        }
    }

    private fun createActivityCallback(
        callback: (Uri?) -> Unit
    ): Int {
        synchronized(this) {
            val requestCode = curActivityRequestCode++
            val activityCallback = ActivityCallback(requestCode, callback)
            callbacks.add(activityCallback)
            return requestCode
        }
    }

    fun onActivityResult(requestCode: Int, data: Intent?) {
        val callback = synchronized(this) {
            val index = callbacks.indexOfFirst { it.requestCode == requestCode }
            if (index == -1) return
            callbacks.removeAt(index)
        }
        postCrashHandlingRunnable {
            callback.callback(data?.data)
        }
    }
}

private class ActivityCallback(
    val requestCode: Int,
    val callback: (Uri?) -> Unit
)
