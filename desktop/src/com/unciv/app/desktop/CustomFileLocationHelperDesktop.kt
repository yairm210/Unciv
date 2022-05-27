package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.unciv.logic.CustomFileLocationHelper
import java.awt.Component
import java.awt.EventQueue
import java.awt.event.WindowEvent
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.swing.JFileChooser
import javax.swing.JFrame

class CustomFileLocationHelperDesktop : CustomFileLocationHelper() {

    override fun createOutputStream(suggestedLocation: String, callback: (String?, OutputStream?, Exception?) -> Unit) {
        pickFile(callback, JFileChooser::showSaveDialog, File::outputStream, suggestedLocation)
    }

    override fun createInputStream(callback: (String?, InputStream?, Exception?) -> Unit) {
        pickFile(callback, JFileChooser::showOpenDialog, File::inputStream)
    }

    private fun <T> pickFile(callback: (String?, T?, Exception?) -> Unit,
                             chooseAction: (JFileChooser, Component) -> Int,
                             createValue: (File) -> T,
                             suggestedLocation: String? = null) {
        EventQueue.invokeLater {
            try {
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
                    result = chooseAction(fileChooser, this@frame)
                    dispatchEvent(WindowEvent(this, WindowEvent.WINDOW_CLOSING))
                }

                frame.dispose()

                if (result == JFileChooser.CANCEL_OPTION) {
                    callback(null, null, null)
                } else {
                    val value = createValue(fileChooser.selectedFile)
                    callback(fileChooser.selectedFile.absolutePath, value, null)
                }
            } catch (ex: Exception) {
                callback(null, null, ex)
            }
        }
    }
}
