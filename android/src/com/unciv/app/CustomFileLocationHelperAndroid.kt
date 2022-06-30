package com.unciv.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.annotation.GuardedBy
import com.unciv.logic.CustomFileLocationHelper
import java.io.InputStream
import java.io.OutputStream

class CustomFileLocationHelperAndroid(private val activity: Activity) : CustomFileLocationHelper() {

    @GuardedBy("this")
    private val callbacks = mutableListOf<ActivityCallback>()
    @GuardedBy("this")
    private var curActivityRequestCode = 100

    override fun createOutputStream(suggestedLocation: String, callback: (String?, OutputStream?, Exception?) -> Unit) {
        val requestCode = createActivityCallback(callback) { activity.contentResolver.openOutputStream(it, "rwt") }

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

    override fun createInputStream(callback: (String?, InputStream?, Exception?) -> Unit) {
        val callbackIndex = createActivityCallback(callback, activity.contentResolver::openInputStream)

        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            // It is theoretically possible to use an initial URI here, however, the only Android URIs we have are obtained from here, so, no dice
            activity.startActivityForResult(this, callbackIndex)
        }
    }

    private fun <T> createActivityCallback(callback: (String?, T?, Exception?) -> Unit,
                                           createValue: (Uri) -> T): Int {
        synchronized(this) {
            val requestCode = curActivityRequestCode++
            val activityCallback = ActivityCallback(requestCode) { uri ->
                if (uri == null) {
                    callback(null, null, null)
                    return@ActivityCallback
                }

                try {
                    val outputStream = createValue(uri)
                    callback(uri.toString(), outputStream, null)
                } catch (ex: Exception) {
                    callback(null, null, ex)
                }
            }
            callbacks.add(activityCallback)
            return requestCode
        }
    }

    fun onActivityResult(requestCode: Int, data: Intent?) {
        val activityCallback = synchronized(this) {
            val index = callbacks.indexOfFirst { it.requestCode == requestCode }
            if (index == -1) return
            callbacks.removeAt(index)
        }
        activityCallback.callback(data?.data)
    }
}

private class ActivityCallback(
    val requestCode: Int,
    val callback: (Uri?) -> Unit
)
