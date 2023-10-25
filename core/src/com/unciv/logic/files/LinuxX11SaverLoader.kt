package com.unciv.logic.files

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.utils.Concurrency
import java.awt.GraphicsEnvironment
import java.io.File

class LinuxX11SaverLoader : PlatformSaverLoader {
    override fun saveGame(
        data: String,
        suggestedLocation: String,
        onSaved: (location: String) -> Unit,
        onError: (ex: Exception) -> Unit
    ) {
        Concurrency.runOnGLThread {
            val startLocation = if (suggestedLocation.startsWith(File.separator)) Gdx.files.absolute(suggestedLocation)
                else Gdx.files.local(suggestedLocation)
            FileChooser.createSaveDialog(stage, "Save game", startLocation) {
                success, file ->
                if (!success) return@createSaveDialog
                try {
                    file.writeString(data, false, Charsets.UTF_8.name())
                    onSaved(file.path())
                } catch (ex: Exception) {
                    onError(ex)
                }
            }.open(true)
        }
    }

    override fun loadGame(
        onLoaded: (data: String, location: String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        Concurrency.runOnGLThread {
            FileChooser.createLoadDialog(stage, "Load game") { success, file ->
                if (!success) return@createLoadDialog
                try {
                    val data = file.readString(Charsets.UTF_8.name())
                    onLoaded(data, file.path())
                } catch (ex: Exception) {
                    onError(ex)
                }
            }.open(true)
        }
    }

    val stage get() = UncivGame.Current.screen!!.stage

    companion object {
        fun isRequired() = System.getProperty("os.name", "") == "Linux" &&
            // System.getenv("XDG_SESSION_TYPE") == "x11" - below seems safer
            GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.javaClass.simpleName == "X11GraphicsDevice"
    }
}
