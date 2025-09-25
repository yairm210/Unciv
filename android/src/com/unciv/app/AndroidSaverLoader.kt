package com.unciv.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.unciv.logic.files.PlatformSaverLoader
import com.unciv.utils.Log
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

    override fun saveGame(
        data: String,
        suggestedLocation: String,
        onSaved: (location: String) -> Unit,
        onError: (ex: Exception) -> Unit
    ) {

        // When we loaded, we returned a "content://" URI as file location.
        val suggestedUri = suggestedLocation.toUri()
        val fileName = getFilename(suggestedUri, suggestedLocation)

        val onFileChosen = { uri: Uri ->
            var stream: OutputStream? = null
            try {
                stream = contentResolver.openOutputStream(uri, "rwt")
                stream!!.writer().use { it.write(data) }
                onSaved(uri.toString())
            } catch (ex: Exception) {
                onError(ex)
            } finally {
                stream?.close()
            }
        }

        requests[requestCode] = Request(onFileChosen, onError)
        openSaveFileChooser(fileName, suggestedUri, requestCode)
        requestCode += 1
    }

    override fun loadGame(
        onLoaded: (data: String, location: String) -> Unit,
        onError: (ex: Exception) -> Unit
    ) {

        val onFileChosen = {uri: Uri ->
            var stream: InputStream? = null
            try {
                stream = contentResolver.openInputStream(uri)
                val text = stream!!.reader().use { it.readText() }
                onLoaded(text, uri.toString())
            } catch (ex: Exception) {
                onError(ex)
            } finally {
                stream?.close()
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

    private fun openSaveFileChooser(fileName: String, uri: Uri, requestCode: Int) {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        intent.type = "application/json"
        intent.putExtra(Intent.EXTRA_TITLE, fileName)
        if (uri.scheme == "content")
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
                if (it?.moveToFirst() == true)
                    return it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                else
                    return ""
            }
        } catch(ex: Exception) {
            Log.error("Failed to get filename from Uri", ex)
            return suggestedLocation.split("2F").last() // I have no idea why but the content path ends with this before the filename
        }

    }
}
