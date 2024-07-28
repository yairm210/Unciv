package com.unciv.app.desktop

import com.unciv.UncivGame
import com.unciv.logic.files.PlatformSaverLoader
import java.awt.Component
import java.awt.EventQueue
import java.awt.event.WindowEvent
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.swing.JFileChooser
import javax.swing.JFrame

class DesktopSaverLoader : PlatformSaverLoader {

    override fun saveGame(
        data: String,
        suggestedLocation: String,
        onSaved: (location: String) -> Unit,
        onError: (ex: Exception) -> Unit
    ) {
        val onFileChosen = { stream: OutputStream, location: String ->
            try {
                stream.writer().use { it.write(data) }
                onSaved(location)
            } catch (ex: Exception) {
                onError(ex)
            }
        }

        pickFile(onFileChosen, onError, JFileChooser::showSaveDialog, File::outputStream, suggestedLocation)

    }

    override fun loadGame(
        onLoaded: (data: String, location: String) -> Unit,
        onError: (ex: Exception) -> Unit
    ) {
        val onFileChosen = { stream: InputStream, location: String ->
            try {
                val data = stream.reader().use { it.readText() }
                onLoaded(data, location)
            } catch (ex: Exception) {
                onError(ex)
            }
        }

        pickFile(onFileChosen, onError, JFileChooser::showOpenDialog, File::inputStream)
    }

    private fun <T> pickFile(onSuccess: (T, String) -> Unit,
                             onError: (Exception) -> Unit,
                             chooseAction: (JFileChooser, Component) -> Int,
                             createValue: (File) -> T,
                             suggestedLocation: String? = null) {
        EventQueue.invokeLater {
            try {
                val fileChooser = JFileChooser().apply fileChooser@{
                    if (suggestedLocation == null) {
                        currentDirectory = UncivGame.Current.files.getDataFolder().file()
                    } else {
                        selectedFile = File(suggestedLocation)
                    }
                }

                val result: Int
                val frame = JFrame().apply frame@{
                    setLocationRelativeTo(null)
                    isVisible = true
                    toFront()
                    result = chooseAction(fileChooser, this@frame)
                    dispatchEvent(WindowEvent(this, WindowEvent.WINDOW_CLOSING))
                }

                frame.dispose()

                if (result == JFileChooser.CANCEL_OPTION) {
                    onError(PlatformSaverLoader.Cancelled())
                } else {
                    val value = createValue(fileChooser.selectedFile)
                    onSuccess(value, fileChooser.selectedFile.absolutePath)
                }
            } catch (ex: Exception) {
                onError(ex)
            }
        }
    }
}
