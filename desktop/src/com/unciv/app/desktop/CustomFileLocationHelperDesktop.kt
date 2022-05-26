package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.unciv.logic.CustomFileLocationHelper
import com.unciv.logic.SuccessfulLoadResult
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import java.awt.EventQueue
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JFrame

class CustomFileLocationHelperDesktop : CustomFileLocationHelper {

    override fun saveGame(
        gameData: String,
        suggestedLocation: String,
        saveCompleteCallback: ((String?, Exception?) -> Unit)?
    ) {
        pickFile(suggestedLocation) { file ->
            if (file == null) {
                callSaveCallback(saveCompleteCallback)
                return@pickFile
            }

            var saveLocation: String? = null
            var exception: Exception? = null
            try {
                file.outputStream()
                    .writer()
                    .use {
                        it.write(gameData)
                    }
                saveLocation = file.absolutePath
            } catch (e: Exception) {
                exception = e
            }
            callSaveCallback(saveCompleteCallback, saveLocation, exception)
        }
    }

    private fun callSaveCallback(saveCompleteCallback: ((String?, Exception?) -> Unit)?,
                                 saveLocation: String? = null,
                                 exception: Exception? = null) {
        postCrashHandlingRunnable {
            saveCompleteCallback?.invoke(saveLocation, exception)
        }
    }

    override fun loadGame(loadCompleteCallback: (SuccessfulLoadResult?, Exception?) -> Unit) {
        pickFile { file ->
            if (file == null) {
                callLoadCallback(loadCompleteCallback)
                return@pickFile
            }

            var gameData: String? = null
            var exception: Exception? = null
            try {
                file.inputStream()
                    .reader()
                    .use {
                        gameData = it.readText()
                    }
            } catch (e: Exception) {
                exception = e
            }
            callLoadCallback(loadCompleteCallback, file, gameData, exception)
        }
    }

    private fun callLoadCallback(loadCompleteCallback: (SuccessfulLoadResult?, Exception?) -> Unit,
                                 file: File? = null,
                                 gameData: String? = null,
                                 exception: Exception? = null) {
        postCrashHandlingRunnable {
            if (exception != null) {
                loadCompleteCallback(null, exception)
            } else if (file != null && gameData != null) {
                loadCompleteCallback(SuccessfulLoadResult(file.absolutePath, gameData), null)
            } else {
                loadCompleteCallback(null, null)
            }
        }
    }

    private fun pickFile(suggestedLocation: String? = null, selectCallback: (File?) -> Unit) {
        EventQueue.invokeLater {
            val fileChooser = JFileChooser().apply fileChooser@{
                if (suggestedLocation == null) {
                    currentDirectory = Gdx.files.local("").file()
                } else {
                    selectedFile = File(suggestedLocation)
                }
            }

            val result: Int
            val frame = JFrame().apply frame@{
                setLocationRelativeTo(null)
                isVisible = true
                toFront()
                result = fileChooser.showSaveDialog(this@frame)
                dispatchEvent(WindowEvent(this, WindowEvent.WINDOW_CLOSING))
            }

            frame.dispose()

            if (result == JFileChooser.CANCEL_OPTION) {
                selectCallback(null)
            } else {
                selectCallback(fileChooser.selectedFile)
            }

        }
    }
}
