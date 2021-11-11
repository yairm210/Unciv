package com.unciv.app

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import com.badlogic.gdx.files.FileHandle
import com.unciv.logic.GameSaver
import com.unciv.ui.utils.LimitOrientationsHelper
import java.io.File

/** See also interface [LimitOrientationsHelper].
 *
 *  The Android implementation (currently the only one) effectively ends up doing
 *  [Activity.setRequestedOrientation]
 */
class LimitOrientationsHelperAndroid(private val activity: Activity) : LimitOrientationsHelper {
/*
    companion object {
        // from android.content.res.Configuration.java
        // applicable to activity.resources.configuration
        const val ORIENTATION_UNDEFINED = 0
        const val ORIENTATION_PORTRAIT = 1
        const val ORIENTATION_LANDSCAPE = 2
    }
*/

    private class GameSettingsPreview(var allowAndroidPortrait: Boolean = false)

    override fun allowPortrait(allow: Boolean) {
        val orientation = when {
            allow -> ActivityInfo.SCREEN_ORIENTATION_USER
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        // Comparison ensures ActivityTaskManager.getService().setRequestedOrientation isn't called unless necessary
        if (activity.requestedOrientation != orientation) activity.requestedOrientation = orientation
    }

    override fun limitOrientations(newOrientation: Int) {
// Sources for Info about current orientation in case need:
//            val windowManager = (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
//            val displayRotation = windowManager.defaultDisplay.rotation
//            val currentOrientation = activity.resources.configuration.orientation
        if (newOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            // Currently only the AndroidLauncher onCreate calls this with 'unspecified'.
            // determine whether to allow portrait from our settings file...
            // Gdx.files at this point is null, UncivGame.Current worse, so we'll do it classically.
            // Gdx parts used that *do* work: FileHandle (constructor, exists, reader) and Json
            val settingsPath = activity.applicationContext.filesDir.absolutePath + File.separator + GameSaver.settingsFileName
            val settingsFile = FileHandle(settingsPath)
            val setting =
                if (!settingsFile.exists()) {
                    GameSettingsPreview()
                } else try {
                    GameSaver.json().fromJson(GameSettingsPreview::class.java, settingsFile.reader())
                } catch (throwable: Throwable) {
                    GameSettingsPreview()
                }
            allowPortrait(setting.allowAndroidPortrait)
        } else {
            // Currently unused
            if (activity.requestedOrientation != newOrientation) activity.requestedOrientation = newOrientation
        }
    }
}
