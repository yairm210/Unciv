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

        val file = pickFile(suggestedLocation)
        if (file == null) {
            saveCompleteCallback?.invoke(null, null)
            return
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
        postCrashHandlingRunnable {
            if (exception != null) {
                saveCompleteCallback?.invoke(null, exception)
            } else if (saveLocation != null) {
                saveCompleteCallback?.invoke(saveLocation, null)
            }
        }
    }

    override fun loadGame(loadCompleteCallback: (SuccessfulLoadResult?, Exception?) -> Unit) {
        val file = pickFile()
        if (file == null) {
            loadCompleteCallback(null, null)
            return
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
        postCrashHandlingRunnable {
            if (exception != null) {
                loadCompleteCallback(null, exception)
            } else if (gameData != null) {
                loadCompleteCallback(SuccessfulLoadResult(file.absolutePath, gameData!!), null)
            }
        }
    }

    private fun pickFile(suggestedLocation: String? = null): File? {
        var file: File? = null
        EventQueue.invokeAndWait {
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
                return@invokeAndWait
            }

            file = fileChooser.selectedFile
        }
        return file
    }
}
