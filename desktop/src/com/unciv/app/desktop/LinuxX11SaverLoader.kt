package com.unciv.app.desktop

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.logic.files.FileChooser
import com.unciv.logic.files.PlatformSaverLoader
import com.unciv.utils.Concurrency
import java.awt.GraphicsEnvironment
import java.io.File
import java.io.InputStream
import java.io.OutputStream


class LinuxX11SaverLoader : PlatformSaverLoader {
    override fun requestSaveLocation(
        suggestedLocation: String,
        mimeType: String,   // unused: Gdx FileChooser has no MIME-type concept
        onLocationChosen: (stream: OutputStream, location: String) -> Unit,
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
                        onLocationChosen(file.write(false), file.path())
                    } catch (ex: Exception) {
                        onError(ex)
                    }
            }.open(true)
        }
    }

    override fun requestLoadLocation(
        onLocationChosen: (stream: InputStream, location: String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        Concurrency.runOnGLThread {
            FileChooser.createLoadDialog(stage, "Load game") { success, file ->
                if (!success)
                    onError(PlatformSaverLoader.Cancelled())
                else
                    try {
                        onLocationChosen(file.read(), file.path())
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
