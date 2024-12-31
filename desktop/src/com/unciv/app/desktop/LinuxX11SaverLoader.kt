package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.logic.files.FileChooser
import com.unciv.logic.files.PlatformSaverLoader
import com.unciv.utils.Concurrency
import java.awt.GraphicsEnvironment
import java.io.File


/**
 *  A dedicated PlatformSaverLoader for X11-based Linux boxes, as using the Java AWT/Swing file chooser dialog will kill the App after closing that dialog
 *
 *  Tested as required from Mint 20.1 up to Mint 21.2, seems independent of Java runtime (mostly tested with adoptium temurin 11 and 17 versions).
 */
class LinuxX11SaverLoader : PlatformSaverLoader {
    override fun saveGame(
        data: String,
        suggestedLocation: String,
        onSaved: (location: String) -> Unit,
        onError: (ex: Exception) -> Unit
    ) {
        Concurrency.runOnGLThread {
            val startLocation =
                if (suggestedLocation.startsWith(File.separator)) Gdx.files.absolute(suggestedLocation)
                else if (Gdx.files.external(suggestedLocation).parent().exists()) Gdx.files.external(suggestedLocation)
                else UncivGame.Current.files.getLocalFile(suggestedLocation)
            
            FileChooser.createSaveDialog(stage, "Save game", startLocation) { success, file ->
                if (!success)
                    onError(PlatformSaverLoader.Cancelled())
                else
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
                if (!success)
                    onError(PlatformSaverLoader.Cancelled())
                else
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
