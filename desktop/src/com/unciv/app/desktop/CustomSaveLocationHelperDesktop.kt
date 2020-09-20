package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.unciv.logic.CustomSaveLocationHelper
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.GameSaver.json
import java.awt.event.WindowEvent
import java.io.File
import javax.swing.JFileChooser
import javax.swing.JFrame

class CustomSaveLocationHelperDesktop : CustomSaveLocationHelper {
    override fun saveGame(gameInfo: GameInfo, gameName: String, forcePrompt: Boolean, block: (() -> Unit)?) {
        gameInfo.customSaveLocation?.let {
            if (!forcePrompt) {
                File(it).outputStream()
                        .writer()
                        .use { writer ->
                            writer.write(json().toJson(gameInfo))
                        }
                block?.invoke()
                return
            }
        }

        val fileChooser = JFileChooser().apply fileChooser@{
            currentDirectory = Gdx.files.local("").file()
            selectedFile = File(gameInfo.customSaveLocation?: gameName)
        }

        JFrame().apply frame@{
            setLocationRelativeTo(null)
            isVisible = true
            toFront()
            fileChooser.showSaveDialog(this@frame)
            dispatchEvent(WindowEvent(this, WindowEvent.WINDOW_CLOSING))
        }
        fileChooser.selectedFile?.let { file ->
            gameInfo.customSaveLocation = file.absolutePath
            file.outputStream()
                    .writer()
                    .use {
                        it.write(json().toJson(gameInfo))
                    }
        }
        block?.invoke()
    }

    override fun loadGame(block: (GameInfo) -> Unit) {
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
        fileChooser.selectedFile?.let { file ->
            file.inputStream()
                    .reader()
                    .readText()
                    .run { GameSaver.gameInfoFromString(this) }
                    .apply {
                        // If the user has saved the game from another platform (like Android),
                        // then the save location might not be right so we have to correct for that
                        // here
                        customSaveLocation = file.absolutePath
                        block(this)
                    }
        }
    }
}