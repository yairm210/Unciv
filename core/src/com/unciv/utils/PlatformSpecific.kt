package com.unciv.utils

import com.unciv.UncivGame
import java.util.Locale

interface PlatformSpecific {

    /** Whether online multiplayer transport features are available on this platform. */
    val supportsOnlineMultiplayer: Boolean get() = true

    /** Notifies player that his multiplayer turn started */
    fun notifyTurnStarted() {}

    /** Install system audio hooks */
    fun installAudioHooks() {}

    /** If not null, this is the path to the directory in which to store the local files - mods, saves, maps, etc */
    var customDataDirectory: String?

    /** If the OS localizes all error messages, this should provide a lookup */
    fun getSystemErrorMessage(errorCode: Int): String? = null

    /** Completed garbage collections, or null when the platform does not expose this metric. */
    fun getGcCount(): Int?

    /** Get system locale, on Android 13+ app-specific locale */
    fun getDefaultLocale(): Locale = Locale.getDefault()

    /** FileHandle.file() works, so java.io.File operations (rename, canonical paths) are available. */
    val hasJavaFileAccess: Boolean get() = true

    /** Arbitrary HTTP downloads work (browsers are limited by CORS). */
    val canDownloadFromUrl: Boolean get() = true

    /** Whether dropping files from an external file manager is supported
     *
     *  If this is true, the platform will need a window listener that dispatches to the [FileDrop][com.unciv.ui.filedrop.FileDrop] methods,
     *  then individual screens can implement [FileDropTarget][com.unciv.ui.filedrop.FileDropTarget]
     */
    //TODO Check whether an Android "share" destination can serve as equivalent
    val supportsFileDrop: Boolean get() = false

    /** e.g. `CrashScreen` can offer "Close Unciv" */
    val canExitApp: Boolean get() = true

    companion object {
        private val noPlatform by lazy {
            object : PlatformSpecific {
                override var customDataDirectory: String?
                    get() = null
                    set(value) {}
                override fun getGcCount(): Int? = null
            }
        }
        val current: PlatformSpecific
            get() = (if (UncivGame.isCurrentInitialized()) UncivGame.Current as? PlatformSpecific else null)
                ?: noPlatform
    }
}
