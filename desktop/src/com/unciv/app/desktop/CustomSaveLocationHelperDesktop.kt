package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.unciv.json.json
import com.unciv.logic.CustomSaveLocationHelper
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import java.awt.event.WindowEvent
import java.io.File
import java.util.concurrent.CancellationException
import javax.swing.JFileChooser
import javax.swing.JFrame

class CustomSaveLocationHelperDesktop : CustomSaveLocationHelper {
    override fun saveGame(gameSaver: GameSaver, gameInfo: GameInfo, gameName: String, forcePrompt: Boolean, saveCompleteCallback: ((Exception?) -> Unit)?) {
        val customSaveLocation = gameInfo.customSaveLocation
        if (customSaveLocation != null && !forcePrompt) {
            try {
                File(customSaveLocation).outputStream()
                        .writer()
                        .use { writer ->
                            writer.write(gameSaver.gameInfoToString(gameInfo))
                        }
                saveCompleteCallback?.invoke(null)
            } catch (e: Exception) {
                saveCompleteCallback?.invoke(e)
            }
            return
        }

        val fileChooser = JFileChooser().apply fileChooser@{
            currentDirectory = Gdx.files.local("").file()
            selectedFile = File(gameInfo.customSaveLocation ?: gameName)
        }

        JFrame().apply frame@{
            setLocationRelativeTo(null)
            isVisible = true
            toFront()
            fileChooser.showSaveDialog(this@frame)
            dispatchEvent(WindowEvent(this, WindowEvent.WINDOW_CLOSING))
        }
        val file = fileChooser.selectedFile
        var exception: Exception? = null
        if (file != null) {
            gameInfo.customSaveLocation = file.absolutePath
            try {
                file.outputStream()
                        .writer()
                        .use {
                            it.write(json().toJson(gameInfo))
                        }
            } catch (e: Exception) {
                exception = e
            }
        } else {
            exception = CancellationException()
        }
        saveCompleteCallback?.invoke(exception)
    }

    override fun loadGame(gameSaver: GameSaver, loadCompleteCallback: (GameInfo?, Exception?) -> Unit) {
        val fileChooser = JFileChooser().apply fileChooser@{
            currentDirectory = Gdx.files.local("").file()
        }

        JFrame().apply frame@{
            setLocationRelativeTo(null)
            isVisible = true
            toFront()
            fileChooser.showOpenDialog(this@frame)
            dispatchEvent(WindowEvent(this, WindowEvent.WINDOW_CLOSING))
        }
        val file = fileChooser.selectedFile
        var exception: Exception? = null
        var gameInfo: GameInfo? = null
        if (file != null) {
            try {
                file.inputStream()
                        .reader()
                        .readText()
                        .run { gameSaver.gameInfoFromString(this) }
                        .apply {
                            // If the user has saved the game from another platform (like Android),
                            // then the save location might not be right so we have to correct for that
                            // here
                            customSaveLocation = file.absolutePath
                            gameInfo = this
                        }
            } catch (e: Exception) {
                exception = e
            }
        } else {
            exception = CancellationException()
        }
        loadCompleteCallback(gameInfo, exception)
    }
}
