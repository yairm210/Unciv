package com.unciv.ui.audio

import com.badlogic.gdx.Files.FileType
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.storage.DropBox
import com.unciv.models.metadata.GameSettings
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import java.io.File
import java.util.EnumSet
import java.util.Timer
import kotlin.concurrent.thread
import kotlin.concurrent.timer
import kotlin.math.roundToInt


/**
 * Play, choose, fade-in/out and generally manage music track playback.
 *
 * Main methods: [chooseTrack], [pause], [resume], [setModList], [isPlaying], [gracefulShutdown]
 *
 * City ambience feature: [playOverlay], [stopOverlay]
 * * This plays entirely independent of all other functionality as linked above.
 * * Can load from internal (jar,apk) - music is always local, nothing is packaged into a release.
 */
class MusicController {
    companion object {
        /** Mods live in Local - but this file prepares for music living in External just in case */
        private val musicLocation = FileType.Local
        private const val musicPath = "music"
        private const val modPath = "mods"
        /** Dropbox path of default download offer */
        private const val musicFallbackLocation = "/music/thatched-villagers.mp3"
        /** Name we save the default download offer to */
        private const val musicFallbackLocalName = "music/Thatched Villagers - Ambient.mp3"
        /** baseVolume has range 0.0-1.0, which is multiplied by this for the API */
        private const val maxVolume = 0.6f
        /** *Observed* frequency of Gdx app loop - theoretically this should reach 60fps */
        private const val ticksPerSecondGdx = 58.3f
        /** Timer frequency when we use our own */
        private const val ticksPerSecondOwn = 20f
        /** Default fade duration in seconds used to calculate the step per tick */
        private const val defaultFadeDuration = 0.9f
        private const val defaultFadingStepGdx = 1f / (defaultFadeDuration * ticksPerSecondGdx)
        private const val defaultFadingStepOwn = 1f / (defaultFadeDuration * ticksPerSecondOwn)
        /** Number of names to keep, to avoid playing the same in short succession */
        private const val musicHistorySize = 8
        /** All Gdx-supported sound formats (file extensions) */
        val gdxSupportedFileExtensions = listOf("mp3", "ogg", "wav")

        private fun getFile(path: String) =
            if (musicLocation == FileType.External && Gdx.files.isExternalStorageAvailable)
                Gdx.files.external(path)
            else Gdx.files.local(path)

        // These are replaced when we _know_ we're attached to Gdx.audio.update
        private var needOwnTimer = true
        private var ticksPerSecond = ticksPerSecondOwn
        internal var defaultFadingStep = defaultFadingStepOwn  // Used by MusicTrackController too
    }

    init {
        val oldFallbackFile = Gdx.files.local(musicFallbackLocation.removePrefix("/"))
        if (oldFallbackFile.exists()) {
            val newFallbackFile = Gdx.files.local(musicFallbackLocalName)
            if (!newFallbackFile.exists())
                oldFallbackFile.moveTo(newFallbackFile)
        }
    }

    /** Container for track info - used for [onChange] and [getHistory].
     *
     *  [toString] returns a prettified label: "Modname: Track".
     *  No track playing is reported as a MusicTrackInfo instance with all
     *  fields empty, for which _`toString`_ returns "—Paused—".
     */
    data class MusicTrackInfo(val mod: String, val track: String, val type: String) {
        /** Used for display, not only debugging */
        override fun toString() = if (track.isEmpty()) "—Paused—"  // using em-dash U+2014
            else if (mod.isEmpty()) track else "$mod: $track"

        companion object {
            /** Parse a path - must be relative to `Gdx.files.local` */
            fun parse(fileName: String): MusicTrackInfo {
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
                return MusicTrackInfo(modName, trackName, type)
            }
        }
    }

    //region Fields
    /** mirrors [GameSettings.musicVolume] - use [setVolume] to update */
    private var baseVolume: Float = UncivGame.Current.settings.musicVolume

    /** Pause in seconds between tracks unless [chooseTrack] is called to force a track change */
    var silenceLength: Float
        get() = silenceLengthInTicks.toFloat() / ticksPerSecond
        set(value) { silenceLengthInTicks = (ticksPerSecond * value).toInt() }

    private var silenceLengthInTicks =
        (UncivGame.Current.settings.pauseBetweenTracks * ticksPerSecond).roundToInt()

    private var mods = HashSet<String>()

    private var state = ControllerState.Idle

    private var ticksOfSilence: Int = 0

    private var musicTimer: Timer? = null

    private enum class ControllerState(val canPause: Boolean = false) {
        /** Own timer stopped, if using the HardenedGdxAudio callback just do nothing */
        Idle,
        /** Loop will release everything and go [Idle] if it encounters this state. */
        Cleanup,
        /** Play a track to its end, then silence for a while, then choose another track */
        Playing(true),
        /** Play a track to its end, then [Cleanup] */
        PlaySingle(true),
        /** Wait for a while in silence to start next track */
        Silence(true),
        /** Music fades to pause or is paused. Continue with chooseTrack or resume. */
        Pause,
        /** Fade out then [Cleanup] */
        Shutdown
    }

    /** Simple two-entry only queue, for smooth fade-overs from one track to another */
    private var current: MusicTrackController? = null
    private var next: MusicTrackController? = null

    /** One entry only for 'overlay' tracks in addition to and independent of normal music */
    private var overlay: MusicTrackController? = null

    /** Keeps paths of recently played track to reduce repetition */
    private val musicHistory = ArrayDeque<String>(musicHistorySize)

    /** These listeners get notified when track changes */
    private var onTrackChangeListeners = mutableListOf<(MusicTrackInfo)->Unit>()

    //endregion
    //region Pure functions

    fun getAudioLoopCallback(): ()->Unit {
        needOwnTimer = false
        ticksPerSecond = ticksPerSecondGdx
        defaultFadingStep = defaultFadingStepGdx
        return { musicTimerTask() }
    }

    fun getAudioExceptionHandler(): (Throwable, Music) -> Unit = {
        ex: Throwable, music: Music ->
        audioExceptionHandler(ex, music)
    }

    /** @return the path of the playing track or empty string if none playing */
    private fun currentlyPlaying(): String =
        if (state.canPause) musicHistory.lastOrNull() ?: ""
        else ""

    /** Registers a callback that will be called with the new track every time it changes.
     *
     *  The track is given as [MusicTrackInfo], which has a `toString` that returns it prettified.
     *
     *  Several callbacks can be registered, but a onChange(null) clears them all.
     *
     *  Callbacks will be safely called on the GL thread.
     */
    fun onChange(listener: ((MusicTrackInfo)->Unit)?) {
        if (listener == null) onTrackChangeListeners.clear()
        else onTrackChangeListeners.add(listener)
        fireOnChange()
    }

    /**
     * Determines whether any music tracks are available for the options menu
     */
    fun isMusicAvailable() = getAllMusicFiles().any()

    /** @return `true` if there's a current music track and if it's actively playing */
    fun isPlaying(): Boolean {
        return current?.isPlaying() == true
    }

    /** @return Sequence of most recently played tracks, oldest first, current last */
    fun getHistory() = musicHistory.asSequence().map { MusicTrackInfo.parse(it) }

    /** @return Sequence of all available and enabled music tracks */
    fun getAllMusicFileInfo() = getAllMusicFiles().map {
        MusicTrackInfo.parse(it.path())
    }

    //endregion
    //region Internal helpers

    private fun clearCurrent() {
        current?.clear()
        current = null
    }
    private fun clearNext() {
        next?.clear()
        next = null
    }

    private fun startTimer() {
        if (!needOwnTimer || musicTimer != null) return
        // Start background TimerTask which manages track changes and fades -
        // on desktop, we get callbacks from the app.loop instead
        val timerPeriod = (1000f / ticksPerSecond).roundToInt().toLong()
        musicTimer = timer("MusicTimer", daemon = true, period = timerPeriod ) {
            musicTimerTask()
        }
    }

    private fun stopTimer() {
        if (musicTimer == null) return
        musicTimer?.cancel()
        musicTimer = null
    }

    private fun musicTimerTask() {
        // This ticks [ticksPerSecond] times per second. Runs on Gdx main thread in desktop only

        overlay?.overlayTick()

        when (state) {
            ControllerState.Idle -> return

            ControllerState.Playing, ControllerState.PlaySingle ->
                if (current == null) {
                    if (next == null) {
                        // no music to play - begin silence or shut down
                        ticksOfSilence = 0
                        state = if (state == ControllerState.PlaySingle) ControllerState.Shutdown
                                else ControllerState.Silence
                        fireOnChange()
                    } else if (next!!.state.canPlay) {
                        // Next track -
                        // if top slot empty and a next exists, move it to top and start
                        current = next
                        next = null
                        if (!current!!.play()) {
                            // Retry another track if playback start fails, after an extended pause
                            ticksOfSilence = -silenceLengthInTicks - 1000
                            state = ControllerState.Silence
                        } else {
                            fireOnChange()
                        }
                    } // else wait for the thread of next.load() to finish
                } else if (!current!!.isPlaying()) {
                    // normal end of track - or the OS stopped the playback (Android pause)
                    clearCurrent()
                    // rest handled next tick
                } else {
                    if (current?.timerTick() == MusicTrackController.State.Idle)
                        clearCurrent()
                    next?.timerTick()
                }
            ControllerState.Silence ->
                if (++ticksOfSilence > silenceLengthInTicks) {
                    ticksOfSilence = 0
                    chooseTrack()
                }
            ControllerState.Shutdown -> {
                // Fade out first, when all queue entries are idle set up for real shutdown
                if (current?.shutdownTick() != false && next?.shutdownTick() != false)
                    state = ControllerState.Cleanup
            }
            ControllerState.Cleanup ->
                shutdown()  // stops timer/sets Idle so this will not repeat
            ControllerState.Pause ->
                current?.timerTick()
        }
    }

    /** Forceful shutdown of music playback and timers - see [gracefulShutdown] */
    private fun shutdown() {
        state = ControllerState.Idle
        fireOnChange()
        // keep onTrackChangeListener! OptionsPopup will want to know when we start up again
        stopTimer()
        clearNext()
        clearCurrent()
        clearOverlay()
        musicHistory.clear()
        Log.debug("MusicController shut down.")
    }

    private fun audioExceptionHandler(ex: Throwable, music: Music) {
        // Should run only in exceptional cases when the Gdx codecs actually have trouble with a
        // file. Most playback problems are caught by the similar handler in MusicTrackController.

        // Gdx _will_ try to read more data from file in Lwjgl3Application.loop even for
        // Music instances that already have thrown an exception.
        // disposing as quickly as possible is a feeble attempt to prevent that.
        music.dispose()
        if (music == next?.music) clearNext()
        if (music == current?.music) clearCurrent()
        if (music == overlay?.music) clearOverlay()

        Log.error("Error playing music", ex)

        // Since this is a rare emergency, go a simple way to reboot music later
        thread(isDaemon = true) {
            Thread.sleep(2000)
            Gdx.app.postRunnable {
                this.chooseTrack()
            }
        }
    }

    /** Get sequence of potential music locations when called without parameters.
     *  @param folder a folder name relative to mod/assets/local root
     *  @param getDefault builds the default (not modded) `FileHandle`,
     *          allows fallback to internal assets
     *  @return a Sequence of `FileHandle`s describing potential existing directories
     */
    private fun getMusicFolders(
        folder: String = musicPath,
        getDefault: () -> FileHandle = { getFile(folder) }
    ) = sequence<FileHandle> {
        yieldAll(
            (UncivGame.Current.settings.visualMods + mods).asSequence()
                .map { getFile(modPath).child(it).child(folder) }
        )
        yield(getDefault())
    }.filter { it.exists() && it.isDirectory }

    /** Get a sequence of all existing music files */
    private fun getAllMusicFiles() = getMusicFolders()
        .flatMap { it.list().asSequence() }
        // ensure only normal files with common sound extension
        .filter { it.exists() && !it.isDirectory && it.extension() in gdxSupportedFileExtensions }

    /** Choose adequate entry from [getAllMusicFiles] */
    private fun chooseFile(
        prefix: String,
        suffix: String,
        flags: EnumSet<MusicTrackChooserFlags>
    ): FileHandle? {
        if (flags.contains(MusicTrackChooserFlags.PlayDefaultFile)) {
            val defaultFile = getFile(musicFallbackLocalName)
            // Test so if someone never downloaded Thatched Villagers,
            // their volume slider will still play music
            if (defaultFile.exists()) return defaultFile
        }
        // Scan whole music folder and mods to find best match for desired prefix and/or suffix
        // get a path list (as strings) of music folder candidates - existence unchecked
        val prefixMustMatch = flags.contains(MusicTrackChooserFlags.PrefixMustMatch)
        val suffixMustMatch = flags.contains(MusicTrackChooserFlags.SuffixMustMatch)
        return getAllMusicFiles()
            .filter {
                (!prefixMustMatch || it.nameWithoutExtension().startsWith(prefix))
                && (!suffixMustMatch || it.nameWithoutExtension().endsWith(suffix))
            }
            // randomize
            .shuffled()
            // sort them by prefix match / suffix match / not last played
            .sortedWith(compareBy(
                { if (it.nameWithoutExtension().startsWith(prefix)) 0 else 1 }
                , { if (it.nameWithoutExtension().endsWith(suffix)) 0 else 1 }
                , { if (it.path() in musicHistory) 1 else 0 }
            // Then just pick the first one.
            // Not as wasteful as it looks - need to check all names anyway
            )).firstOrNull()
        // Note: shuffled().sortedWith(), ***not*** .sortedWith(.., Random)
        // the latter worked with older JVM's,
        // current ones *crash* you when a compare is not transitive.
    }

    private fun fireOnChange() {
        if (onTrackChangeListeners.isEmpty()) return
        Concurrency.runOnGLThread {
            fireOnChange(MusicTrackInfo.parse(currentlyPlaying()))
        }
    }
    private fun fireOnChange(trackInfo: MusicTrackInfo) {
        try {
            for (listener in onTrackChangeListeners)
                listener.invoke(trackInfo)
        } catch (ex: Throwable) {
            Log.debug("onTrackChange event invoke failed", ex)
            onTrackChangeListeners.clear()
        }
    }

    //endregion
    //region State changing methods

    /** This tells the music controller about active mods - all are allowed to provide tracks */
    fun setModList ( newMods: HashSet<String> ) {
        // This is hooked in most places where ImageGetter.setNewRuleset is called.
        // Changes in permanent audiovisual mods are effective without this notification.
        // Only the map editor isn't hooked, so if we wish to play mod-nation-specific tunes in the
        // editor when e.g. a starting location is picked, that will have to be added.
        mods = newMods
    }

    /**
     * Chooses and plays a music track using an adaptable approach - for details see the wiki.
     * Called without parameters it will choose a new ambient music track
     * and start playing it with fade-in/out.
     * Will do nothing when no music files exist or the master volume is zero.
     *
     * @param prefix file name prefix, meant to represent **Context** - in most cases a Civ name
     * @param suffix file name suffix, meant to represent **Mood** -
     *          e.g. Peace, War, Theme, Defeat, Ambient (Ambient is the default when
     *          a track ends and exists so War Peace and the others are not chosen in that case)
     * @param flags a set of optional flags to tune the choice and playback.
     * @return `true` = success, `false` = no match, no playback change
     */
    fun chooseTrack(
        prefix: String = "",
        suffix: String = MusicMood.Ambient,
        flags: EnumSet<MusicTrackChooserFlags> = MusicTrackChooserFlags.default
    ): Boolean {
        if (baseVolume == 0f) return false

        val musicFile = chooseFile(prefix, suffix, flags)

        if (musicFile == null) {
            // MustMatch flags at work or Music folder empty
            Log.debug("No music found for prefix=%s, suffix=%s, flags=%s",
                prefix, suffix, flags)
            return false
        }
        Log.debug("Track chosen: %s for prefix=%s, suffix=%s, flags=%s",
            musicFile.path(), prefix, suffix, flags)
        return startTrack(musicFile, flags)
    }

    /** Initiate playback of a _specific_ track by handle - part of [chooseTrack].
     *  Manages fade-over from the previous track.
     */
    private fun startTrack(
        musicFile: FileHandle,
        flags: EnumSet<MusicTrackChooserFlags> = MusicTrackChooserFlags.default
    ): Boolean {
        if (musicFile.path() == currentlyPlaying())
            return true  // picked file already playing
        if (!musicFile.exists())
            return false  // Safety check - nothing to play found?

        next?.clear()
        next = MusicTrackController(baseVolume * maxVolume)

        next!!.load(musicFile, onError = {
            ticksOfSilence = 0
            state = ControllerState.Silence // will retry after one silence period
            next = null
        }, onSuccess = {
            Log.debug("Music queued: %s", musicFile.path())

            if (musicHistory.size >= musicHistorySize) musicHistory.removeFirst()
            musicHistory.addLast(musicFile.path())

            // This is what makes a track change fade _over_:
            // current fading out and next fading in at the same time.
            it.play()

            val fadingStep = defaultFadingStep /
                (if (flags.contains(MusicTrackChooserFlags.SlowFade)) 5 else 1)
            it.startFade(MusicTrackController.State.FadeIn, fadingStep)

            when (state) {
                ControllerState.Playing, ControllerState.PlaySingle ->
                    current?.startFade(MusicTrackController.State.FadeOut, fadingStep)
                ControllerState.Pause ->
                    if (current?.state == MusicTrackController.State.Idle) clearCurrent()
                else -> Unit
            }
        })

        // Yes while the loader is doing its thing we wait for it in a Playing state
        state = if (flags.contains(MusicTrackChooserFlags.PlaySingle)) ControllerState.PlaySingle
                else ControllerState.Playing
        startTimer()
        return true
    }

    /** Variant of [chooseTrack] that tries several moods ([suffixes]) until a match is chosen */
    fun chooseTrack(
        prefix: String = "",
        suffixes: List<String>,
        flags: EnumSet<MusicTrackChooserFlags> = MusicTrackChooserFlags.none
    ): Boolean {
        for (suffix in suffixes) {
            if (chooseTrack(prefix, suffix, flags)) return true
        }
        return false
    }

    /** Initiate playback of a _specific_ track by a [MusicTrackInfo] instance */
    fun startTrack(trackInfo: MusicTrackInfo): Boolean {
        if (trackInfo.track.isEmpty()) return false
        val path = trackInfo.run {
            if (mod.isEmpty()) "$musicPath/$track.$type"
            else "mods/$mod/$musicPath/$track.$type"
        }
        return startTrack(getFile(path))
    }

    /**
     * Pause playback with fade-out
     *
     * @param speedFactor accelerate (>1) or slow down (<1) the fade-out. Clamped to 1/1000..1000.
     */
    fun pause(speedFactor: Float = 1f) {
        Log.debug("MusicTrackController.pause called")

        if (!state.canPause) return
        state = ControllerState.Pause

        val fadingStep = defaultFadingStep * speedFactor.coerceIn(0.001f..1000f)
        current?.startFade(MusicTrackController.State.FadeOut, fadingStep)
        if (next?.state == MusicTrackController.State.FadeIn)
            next!!.startFade(MusicTrackController.State.FadeOut)
    }

    /**
     * Resume playback with fade-in - from a pause will resume where playback left off,
     * otherwise it will start a new ambient track choice.
     *
     * @param speedFactor accelerate (>1) or slow down (<1) the fade-in. Clamped to 1/1000..1000.
     */
    fun resume(speedFactor: Float = 1f) {
        Log.debug("MusicTrackController.resume called")
        if (state == ControllerState.Pause && current != null) {
            val fadingStep = defaultFadingStep * speedFactor.coerceIn(0.001f..1000f)
            current!!.startFade(MusicTrackController.State.FadeIn, fadingStep)
            // this may circumvent a PlaySingle, but -
            // currently only the main menu resumes, and then it's perfect:
            state = ControllerState.Playing
            current!!.play()
        } else if (state == ControllerState.Cleanup || state == ControllerState.Pause) {
            chooseTrack()
        }
    }

    /** Fade out then shutdown with a given [duration] in seconds,
     *  defaults to a 'slow' fade (4.5s) */
    @Suppress("unused")  // might be useful instead of gracefulShutdown
    fun fadeoutToSilence(duration: Float = defaultFadeDuration * 5) {
        val fadingStep = 1f / ticksPerSecond / duration
        current?.startFade(MusicTrackController.State.FadeOut, fadingStep)
        next?.startFade(MusicTrackController.State.FadeOut, fadingStep)
        state = ControllerState.Shutdown
    }

    /** Update playback volume, to be called from options popup */
    fun setVolume(volume: Float) {
        baseVolume = volume
        if ( volume < 0.01 ) shutdown()
        else if (isPlaying()) current!!.setVolume(baseVolume * maxVolume)
    }

    /** Soft shutdown of music playback, with fadeout */
    fun gracefulShutdown() {
        if (state == ControllerState.Cleanup) shutdown()
        else state = ControllerState.Shutdown
    }

    /** Download Thatched Villagers */
    fun downloadDefaultFile() {
        val file = DropBox.downloadFile(musicFallbackLocation)
        getFile(musicFallbackLocalName).write(file, false)
    }

    /** @return `true` if Thatched Villagers is present */
    fun isDefaultFileAvailable() =
        getFile(musicFallbackLocalName).exists()

    //endregion
    //region Overlay track

    /** Scans all mods [folder]s for [name] with a supported sound extension,
     *  with fallback to _internal_ assets [folder] */
    private fun getMatchingFiles(folder: String, name: String) =
        getMusicFolders(folder) { Gdx.files.internal(folder) }
            .flatMap {
                it.list { file: File ->
                    file.nameWithoutExtension == name && file.exists() && !file.isDirectory &&
                    file.extension in gdxSupportedFileExtensions
                }.asSequence()
            }

    /** Play [name] from any mod's [folder] or internal assets,
     *  fading in to [volume] then looping */
    fun playOverlay(folder: String, name: String, volume: Float) {
        val file = getMatchingFiles(folder, name).firstOrNull() ?: return
        playOverlay(file, volume)
    }

    /** Play [file], fading in to [volume] then looping */
    @Suppress("MemberVisibilityCanBePrivate")  // open to future use
    fun playOverlay(file: FileHandle, volume: Float) {
        clearOverlay()
        MusicTrackController(volume, initialFadeVolume = 0f).load(file) {
            it.music?.isLooping = true
            it.play()
            it.startFade(MusicTrackController.State.FadeIn)
            overlay = it
        }
    }

    /** Fade out any playing overlay then clean up */
    fun stopOverlay() {
        overlay?.startFade(MusicTrackController.State.FadeOut)
    }

    private fun MusicTrackController.overlayTick() {
        if (timerTick() == MusicTrackController.State.Idle)
            clearOverlay()  // means FadeOut finished
    }

    private fun clearOverlay() {
        overlay?.clear()
        overlay = null
    }

    //endregion
}
