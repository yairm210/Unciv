package com.unciv.ui.utils

import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.models.metadata.GameSettings

/** Interface to support various platform-specific tools */
interface GeneralPlatformSpecificHelpers {
    /** Pass a Boolean setting as used in [allowAndroidPortrait][GameSettings.allowAndroidPortrait] to the OS.
     *
     *  You can turn a mobile device on its side or upside down, and a mobile OS may or may not allow the
     *  position changes to automatically result in App orientation changes. This is about limiting that feature.
     *  @param allow `true`: allow all orientations (follows sensor as limited by OS settings)
     *          `false`: allow only landscape orientations (both if supported, otherwise default landscape only)
     */
    fun allowPortrait(allow: Boolean) {}

    fun hasDisplayCutout(): Boolean { return false }
    fun toggleDisplayCutout(androidCutout: Boolean) {}

    /**
     * Notifies the user that it's their turn while the game is running
     */
    fun notifyTurnStarted() {}

    /**
     * If the GDX [com.badlogic.gdx.Files.getExternalStoragePath] should be preferred for this platform,
     * otherwise uses [com.badlogic.gdx.Files.getLocalStoragePath]
     */
    fun shouldPreferExternalStorage(): Boolean

    /**
     * Handle an uncaught throwable.
     * @return true if the throwable was handled.
     */
    fun handleUncaughtThrowable(ex: Throwable): Boolean = false

    /**
     * Adds platform-specific improvements to the given text field, making it nicer to interact with on this platform.
     */
    fun addImprovements(textField: TextField): TextField = textField

}
