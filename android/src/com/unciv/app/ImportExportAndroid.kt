package com.unciv.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.documentfile.provider.DocumentFile
import com.unciv.logic.GameSaver
import com.unciv.ui.utils.IImportExport
import com.unciv.ui.utils.ImportExportParameters
import com.unciv.ui.utils.ImportExportStatus
import java.io.File
import kotlin.concurrent.thread

class ImportExportAndroid(private val activity: Activity): IImportExport {
    companion object {
        const val REQUESTCODE_EXPORT = 4201     // arbitrary choice
        const val REQUESTCODE_IMPORT = 4202
    }

    // These we persist from the request call to the activityResult callback
    private var parameters = ImportExportParameters()
    private var callback: ((status:ImportExportStatus, msg:String)->Unit)? = null
    
    private var _isInProgress = false

    // Fulfill the IImportExport Interface
    override fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP

    override fun requestExport(params: ImportExportParameters, notify: ((status:ImportExportStatus, msg:String)->Unit)?) {
        startRequest(REQUESTCODE_EXPORT, params, notify)
    }

    override fun requestImport(params: ImportExportParameters, notify: ((status:ImportExportStatus, msg:String)->Unit)?) {
        startRequest(REQUESTCODE_IMPORT, params, notify)
    }

    override fun isInProgress(): Boolean = _isInProgress

    // Helper to kick off both kinds of request 
    private fun startRequest(requestCode: Int, params: ImportExportParameters, notify: ((status:ImportExportStatus, msg:String)->Unit)?) {
        if (!isSupported()) return
        if (!(params.config || params.autosave || params.saves || params.maps)) return
        parameters = params
        callback = notify
        _isInProgress = true
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, if (params.suggestFolder.isEmpty()) "Documents/Unciv" else params.suggestFolder)

        startActivityForResult(activity, intent, requestCode, null)
    }

    //    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun activityResult(resultCode: Int, requestCode: Int, uri: Uri) {
        // We don't dispatch a request under API 21 Lollipop, this is just so the compiler does not demand a static KITKAT dependency 
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return

        // OK, the user has reacted and either approved access to an external directory or cancelled 
        println("activityResult: resultCode=$resultCode, requestCode=$requestCode, uri=$uri")
        if (requestCode != REQUESTCODE_EXPORT && requestCode != REQUESTCODE_IMPORT) return
        // resultCodes other than OK or CANCEL should never happen as there are no more constants, just to be safe
        if (resultCode == Activity.RESULT_CANCELED) {
            callback?.invoke(ImportExportStatus.Failure, "Cancelled")
        }
        if (resultCode != Activity.RESULT_OK) {
            _isInProgress = false
            return
        }
        callback?.invoke(ImportExportStatus.ChosenFolder, uri.toString())

        // Ask to keep these permissions as long as the selected folder lives
        activity.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        
        // Instantiate Document from URI            --- TODO: Check when this can return null
        var externalDir= DocumentFile.fromTreeUri(activity, uri) 
        if (externalDir == null) {
            _isInProgress = false
            return
        }

        // Decouple even though this is a callback
        thread(start = true,  name = "ImportExport", isDaemon = false) {
            try {
                // Subitem: The Autosave
                if (parameters.autosave) {
                    val internalFile = File(activity.filesDir.path + File.separator + GameSaver.saveFilesFolder + File.separator + GameSaver.autosaveName)
                    val extSaveDir = getExternalSubDir(externalDir, GameSaver.saveFilesFolder,requestCode == REQUESTCODE_EXPORT)
                    if (extSaveDir != null)
                        copyItem(requestCode, extSaveDir, internalFile, GameSaver.autosaveName, false)
                }

                // Subitem: Savegames except Autosaves
                callback?.invoke(ImportExportStatus.Progress, "{Working}")
                if (parameters.saves) 
                    copyItem(requestCode, externalDir, GameSaver.saveFilesFolder, true, true)
                
                // Subitem: Maps folder
                callback?.invoke(ImportExportStatus.Progress, "{Working}.")
                if (parameters.maps) 
                    copyItem(requestCode, externalDir, "maps", true)
                
                // Subitem: The mods folder     --- TODO: We cannot copy recursively yet
                callback?.invoke(ImportExportStatus.Progress, "{Working}..")
                if (parameters.mods) 
                    copyItem(requestCode, externalDir, "mods", true)
                
                // Subitem: The music folder
                callback?.invoke(ImportExportStatus.Progress, "{Working}...")
                if (parameters.music) 
                    copyItem(requestCode, externalDir, "music", true)

                // Subitem: Settings - do this last, as the Success status is what will trigger a settings reload
                callback?.invoke(ImportExportStatus.Progress, "{Working}....")
                if (parameters.config)
                    copyItem(requestCode, externalDir, GameSaver.settingsFileName, false)

                // Done
                callback?.invoke( if(requestCode == REQUESTCODE_EXPORT) ImportExportStatus.ExportSuccess else ImportExportStatus.ImportSuccess, "OK" )
            } catch (ex: Exception) {
                println("Import/Export exception: ${ex.localizedMessage}")
                ex.printStackTrace()
                callback?.invoke(ImportExportStatus.Failure, "Sorry, there was an error.")
            } finally {
                _isInProgress = false
                println("ImportExport thread ends")
            }
        }
    }

    // Helper to get a SAF subfolder a little easier 
    private fun getExternalSubDir(externalDir: DocumentFile, name: String, create: Boolean = false): DocumentFile? {
        val extSubDir = externalDir.findFile(name)
        return if (extSubDir!= null && extSubDir.exists()) {
            if (!extSubDir.isDirectory) throw Exception("$name exists but is not a directory")
            extSubDir
        } else if(create) {
            externalDir.createDirectory(name)
        } else {
            null
        }
    }

    // Helper to copy a single file from internal to SAF
    private fun copyFileToSAF (inFile: File, externalDir: DocumentFile, name: String) {
        val inStream = inFile.inputStream()
        val existing = externalDir.findFile(name)
        if (existing != null && existing.exists()) existing.delete()
        val outFile = externalDir.createFile("application/octet-stream", name)!!
        val outStream = activity.contentResolver.openOutputStream(outFile.uri)!!
        inStream.copyTo(outStream, DEFAULT_BUFFER_SIZE)
    }

    // Helper to copy a single file from SAF to internal
    private fun copyFileFromSAF(externalDir: DocumentFile, name: String, outFile: File) {
        val inFile = externalDir.findFile(name)
        if (inFile == null) {
            callback?.invoke(ImportExportStatus.Progress, "$name not found")
            return
        }
        outFile.parentFile?.mkdirs()
        val inStream = activity.contentResolver.openInputStream(inFile.uri)!!
        if (outFile.exists()) outFile.delete()
        val outStream = outFile.outputStream()
        inStream.copyTo(outStream, DEFAULT_BUFFER_SIZE)
    }

    // Generalized one-item processor where items are what we allow to select in the parameters
    // Parameters are somewhat redundant but passing is cheaper
    private fun copyItem(requestCode: Int, externalDir: DocumentFile, internalFile: File, name: String, isDir: Boolean, omitAutosave: Boolean = false) {
        if (requestCode == REQUESTCODE_EXPORT) {
            if (!internalFile.exists()) {
                callback?.invoke(ImportExportStatus.Progress, "$name not found")
                return
            }
            if (!isDir) return copyFileToSAF(internalFile, externalDir, name)
            if (!internalFile.isDirectory) return             // Lazy shortcut: reaction to a file where a directory should be
            val extSubDir = getExternalSubDir(externalDir, name, true)!!
            internalFile.listFiles()?.forEach { file ->
                if (!omitAutosave || !file.name.startsWith(GameSaver.autosaveName)) {
                    copyFileToSAF(file, extSubDir, file.name)
                }
            }
        } else {
            if (!isDir) return copyFileFromSAF(externalDir, name, internalFile)
            val extSubDir = getExternalSubDir(externalDir, name, false) ?: return
            extSubDir.listFiles().forEach { doc ->
                if (doc.name != null && !omitAutosave || doc.name?.startsWith(GameSaver.autosaveName) != true) {
                    val out = File(internalFile.path + File.separator + doc.name)
                    copyFileFromSAF( extSubDir, doc.name!!, out)
                }
            }
        }
    }

    // Overload tho shorten calls referring to one item directly under the internal app-files-root
    private fun copyItem(requestCode: Int, externalDir: DocumentFile, name: String, isDir: Boolean, omitAutosave: Boolean = false) {
        val internalFile = File(activity.filesDir.path + File.separator + name)
        copyItem (requestCode, externalDir, internalFile, name, isDir, omitAutosave)
    }

}
