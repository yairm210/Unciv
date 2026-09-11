package com.unciv.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.unciv.logic.files.PlatformSaverLoader
import com.unciv.utils.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import androidx.core.net.toUri


class AndroidSaverLoader(private val activity: Activity) : PlatformSaverLoader {

    private val contentResolver = activity.contentResolver
    private val requests = HashMap<Int, Request>()
    private var requestCode = 100

    private class Request(
        val onFileChosen: (Uri) -> Unit,
        onError: (ex: Exception) -> Unit
    ) {
        val onCancel: () -> Unit = { onError(PlatformSaverLoader.Cancelled()) }
    }

    override fun requestSaveLocation(
        suggestedLocation: String,
        mimeType: String,
        onLocationChosen: (stream: OutputStream, location: String) -> Unit,
        onError: (ex: Exception) -> Unit
    ) {
        // When we loaded, we returned a "content://" URI as file location.
        val suggestedUri = suggestedLocation.toUri()
        val fileName = getFilename(suggestedUri, suggestedLocation)

        val onFileChosen = { uri: Uri ->
            try {
                val stream = contentResolver.openOutputStream(uri, "rwt")
                    ?: throw IOException("openOutputStream returned null for $uri")
                onLocationChosen(stream, uri.toString())
            } catch (ex: Exception) {
                onError(ex)
            }
        }

        requests[requestCode] = Request(onFileChosen, onError)
        openSaveFileChooser(fileName, suggestedUri, mimeType, requestCode)
        requestCode += 1
    }

    override fun requestLoadLocation(
        onLocationChosen: (stream: InputStream, location: String) -> Unit,
        onError: (ex: Exception) -> Unit
    ) {
        val onFileChosen = { uri: Uri ->
            try {
                val stream = contentResolver.openInputStream(uri)
                    ?: throw IOException("openInputStream returned null for $uri")
                onLocationChosen(stream, uri.toString())
            } catch (ex: Exception) {
                onError(ex)
            }
        }

        requests[requestCode] = Request(onFileChosen, onError)
        openLoadFileChooser(requestCode)
        requestCode += 1
    }

    fun onActivityResult(requestCode: Int, data: Intent?) {
        val request = requests.remove(requestCode) ?: return
        // data is null if the user back out of the activity without choosing a file
        if (data == null) return request.onCancel()
        val uri: Uri = data.data ?: return
        request.onFileChosen(uri)
    }

    private fun openSaveFileChooser(fileName: String, uri: Uri, mimeType: String, requestCode: Int) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.type = mimeType
        intent.putExtra(Intent.EXTRA_TITLE, fileName)
        if (uri.scheme == "content" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
        activity.startActivityForResult(intent, requestCode)
    }

    private fun openLoadFileChooser(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.type = "*/*"
        /* It is theoretically possible to use an initial URI here,
         however, the only Android URIs we have are obtained from here, so, no dice */
        activity.startActivityForResult(intent, requestCode)
    }

    private fun getFilename(uri: Uri, suggestedLocation: String): String {
        if (uri.scheme != "content")
            return suggestedLocation

        try {
            contentResolver.query(uri, null, null, null, null).use {
                return if (it?.moveToFirst() == true)
                    it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                else
                    ""
            }
        } catch(ex: Exception) {
            Log.error("Failed to get filename from Uri \"$uri\"", ex)
            return suggestedLocation.split("2F").last() // I have no idea why but the content path ends with this before the filename
        }
    }
}
