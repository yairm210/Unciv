package com.unciv.ui.audio

import com.unciv.ui.audio.MusicController.Companion.gdxSupportedFileExtensions
import com.unciv.ui.audio.MusicController.Companion.modPath
import com.unciv.ui.audio.MusicController.Companion.musicPath
import games.rednblack.miniaudio.MASound
import yairm210.purity.annotations.Readonly

/** Container for track info - used for [onChange][MusicController.onChange] and [getHistory][MusicController.getHistory].
 *
 *  [toString] returns a prettified label: "Modname: Track".
 *  No track playing is reported as a MusicTrackInfo instance with all
 *  fields empty, for which _`toString`_ returns "—Paused—".
 */
data class MusicTrackInfo(
    val mod: String,
    val track: String,
    val type: String,
    val length: Float? = null,
    val position: Float? = null
) {
    /** Used for display, not only debugging */
    override fun toString() = if (track.isEmpty()) "—Paused—"  // using em-dash U+2014
    else if (mod.isEmpty()) track else "$mod: $track"

    companion object {
        /** Parse a path - must be relative to `UncivGame.Current.files.getLocalFile` */
        @Readonly
        internal fun parse(fileName: String, sound: MASound? = null): MusicTrackInfo {
            if (fileName.isEmpty())
                return MusicTrackInfo("", "", "")
            val fileNameParts = fileName.split('/')
            val modName = if (fileNameParts.size > 1 && fileNameParts[0] == modPath)
                fileNameParts[1] else ""
            var trackName = fileNameParts[
                if (fileNameParts.size > 3 && fileNameParts[2] == musicPath) 3 else 1
            ]
            val type = gdxSupportedFileExtensions
                .firstOrNull {trackName.endsWith(".$it") } ?: ""
            trackName = trackName.removeSuffix(".$type")
            if (sound == null)
                return MusicTrackInfo(modName, trackName, type)
            return MusicTrackInfo(modName, trackName, type, sound.length, sound.cursorPosition)
        }
    }
}
