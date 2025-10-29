package com.unciv.ui.audio

import com.badlogic.gdx.Files.FileType
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Disposable
import com.unciv.UncivGame
import com.unciv.logic.files.IMediaFinder
import com.unciv.logic.multiplayer.storage.DropBox
import com.unciv.models.metadata.GameSettings
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import games.rednblack.miniaudio.MiniAudio
import yairm210.purity.annotations.Readonly
import java.util.EnumSet
import java.util.Timer
import kotlin.concurrent.timer
import kotlin.math.roundToInt

/* TODO:
       - Use MASound.fadeIn and fadeOut
       - MA has a separate master volume
       - Use MASound.cursorPosition / length for Player progressbar
       - Use MASound's own end callback
       - 
 */

/**
 * Play, choose, fade-in/out and generally manage music track playback.
 *
 * Main methods: [chooseTrack], [pause], [resume], [setModList], [isPlaying], [gracefulShutdown]
 *
 * City ambience / Leader voice feature: [playOverlay], [stopOverlay], [playVoice]
 * * This plays entirely independent of all other functionality as linked above.
 * * Can load from internal (jar,apk) - music is always local, nothing is packaged into a release.
 */
class MusicController(private val miniAudio: MiniAudio) : Disposable {
    @Suppress("ConstPropertyName")
    companion object {
        /** Mods live in Local - but this file prepares for music living in External just in case */
        private val musicLocation = FileType.Local
        internal const val musicPath = "music"
        internal const val modPath = "mods"
        /** Dropbox path of default download offer */
        private const val musicFallbackLocation = "/music/thatched-villagers.mp3"
        /** Name we save the default download offer to */
        private const val musicFallbackLocalName = "music/Thatched Villagers - Ambient.mp3"
        /** baseVolume has range 0.0-1.0, which is multiplied by this for the API */
        private const val maxVolume = 0.6f
        /** Timer frequency when we use our own */
        private const val ticksPerSecond = 20f
        /** Default fade duration in seconds used to calculate the step per tick */
        private const val defaultFadeDuration = 0.9f
        internal const val defaultFadingStep = 1f / (defaultFadeDuration * ticksPerSecond)
        /** Number of names to keep, to avoid playing the same in short succession */
        private const val musicHistorySize = 8
        /** All Gdx-supported sound formats (file extensions) */
        internal val gdxSupportedFileExtensions = IMediaFinder.SupportedAudioExtensions.names
        /** Since we have MASound.cursorPosition, we can update listeners with smooth progress - but not every tick */
        private val ticksPerOnChange = 4

        private fun getFile(path: String) =
            if (musicLocation == FileType.External && Gdx.files.isExternalStorageAvailable)
                Gdx.files.external(path)
            else UncivGame.Current.files.getLocalFile(path)
    }

    init {
        val oldFallbackFile = UncivGame.Current.files.getLocalFile(musicFallbackLocation.removePrefix("/"))
        if (oldFallbackFile.exists()) {
            val newFallbackFile = UncivGame.Current.files.getLocalFile(musicFallbackLocalName)
            if (!newFallbackFile.exists())
                oldFallbackFile.moveTo(newFallbackFile)
        }
    }

    //region Fields
    /** mirrors [GameSettings.musicVolume] - use [setVolume] to update */
    private var baseVolume: Float = settings.musicVolume

    /** Pause in seconds between tracks unless [chooseTrack] is called to force a track change */
    var silenceLength: Float
        get() = silenceLengthInTicks.toFloat() / ticksPerSecond
        set(value) { silenceLengthInTicks = (ticksPerSecond * value).toInt() }

    private var silenceLengthInTicks =
        (settings.pauseBetweenTracks * ticksPerSecond).roundToInt()

    private var mods = HashSet<String>()

    private var state = ControllerState.Idle

    private var ticksOfSilence: Int = 0

    private var musicTimer: Timer? = null

    private enum class ControllerState(val canPause: Boolean = false, val showTrack: Boolean = false) {
        /** Own timer stopped, if using the HardenedGdxAudio callback just do nothing */
        Idle,
        /** Loop will release everything and go [Idle] if it encounters this state. */
        Cleanup,
        /** Play a track to its end, then silence for a while, then choose another track */
        Playing(true, true),
        /** Play a track to its end, then [Cleanup] */
        PlaySingle(true, true),
        /** Wait for a while in silence to start next track */
        Silence(true),
        /** Music fades to pause or is paused. Continue with chooseTrack or resume. */
        Pause(showTrack = true),
        /** Indicates that the music should resume when reopened */
        PauseOnShutdown,
        /** Fade out then [Cleanup] */
        Shutdown,
        /** Is shut down and ignores all requests */
        Disposed
    }

    /** Simple two-entry only queue, for smooth fade-overs from one track to another */
    private var current: MusicTrackController? = null
    private var next: MusicTrackController? = null

    /** One entry only for 'overlay' tracks in addition to and independent of normal music */
    private var overlay: MusicTrackController? = null
    /** `overlay` state needs to be separate - but apart from Idle, which is `overlay==null`,
     *   it only needs to know whether a fade-out was meant as stop or as pause */
    private var overlayPausing = false

    /** Keeps paths of recently played track to reduce repetition */
    private val musicHistory = ArrayDeque<String>(musicHistorySize)

    /** These listeners get notified when track changes */
    private var onTrackChangeListeners = mutableListOf<(MusicTrackInfo)->Unit>()

    /** count to [ticksPerOnChange] and fire onChange listeners only once per cycle */
    private var onChangeTicks = 0

    //endregion
    //region Pure functions

    /** @return the path of the playing track or empty string if none playing */
    private fun currentlyPlaying(): String =
        if (state.showTrack) musicHistory.lastOrNull() ?: ""
        else ""

    /**
     * Determines whether any music tracks are available for the options menu
     */
    fun isMusicAvailable() = getAllMusicFiles().any()

    /** @return `true` if there's a current music track and if it's actively playing */
    fun isPlaying(): Boolean {
        return current?.isPlaying() == true
    }

    @Readonly
    fun isInactive() = state == ControllerState.Disposed

    /** @return Sequence of most recently played tracks, oldest first, current last */
    @Readonly
    fun getHistory() = musicHistory.asSequence().map { MusicTrackInfo.parse(it) }

    /** @return Sequence of all available and enabled music tracks */
    fun getAllMusicFileInfo() = getAllMusicFiles().map {
        MusicTrackInfo.parse(it.path())
    }

    //endregion
    //region Internal helpers

    private val settings get() = UncivGame.Current.settings

    private fun clearCurrent() {
        current?.dispose()
        current = null
        onChangeTicks = 0
    }
    private fun clearNext() {
        next?.dispose()
        next = null
    }

    private fun startTimer() {
        if (musicTimer != null) return
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
                    // This is the one constantly updating listeners with the current position
                    if (onChangeTicks == 0) fireOnChange()
                    onChangeTicks = (onChangeTicks + 1) % ticksPerOnChange
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
            ControllerState.Pause, ControllerState.PauseOnShutdown ->
                current?.timerTick()

            ControllerState.Disposed -> Unit
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
            (settings.visualMods + mods).asSequence()
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
        val fileName = current?.getPath()
        val info = if (fileName == null) MusicTrackInfo.parse("")
            else MusicTrackInfo.parse(fileName,current!!.music)
        Concurrency.runOnGLThread {
            fireOnChange(info)
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
        if (baseVolume == 0f || isInactive()) return false

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

        next?.dispose()
        next = MusicTrackController(miniAudio, baseVolume * maxVolume)

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
        if (isInactive()) return false
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
    fun pause(speedFactor: Float = 1f, onShutdown: Boolean = false) {
        Log.debug("MusicTrackController.pause called")

        if (!state.canPause) return // for example, we're already in "pause" and we activated "pause on shutdown"
        state = if (onShutdown) ControllerState.PauseOnShutdown else ControllerState.Pause

        val fadingStep = defaultFadingStep * speedFactor.coerceIn(0.001f..1000f)
        current?.startFade(MusicTrackController.State.FadeOut, fadingStep)
        if (next?.state == MusicTrackController.State.FadeIn)
            next!!.startFade(MusicTrackController.State.FadeOut)

        pauseOverlay()
    }

    fun resumeFromShutdown(){
        if (state == ControllerState.PauseOnShutdown) resume()
    }

    /**
     * Resume playback with fade-in - from a pause will resume where playback left off,
     * otherwise it will start a new ambient track choice.
     *
     * @param speedFactor accelerate (>1) or slow down (<1) the fade-in. Clamped to 1/1000..1000.
     */
    fun resume(speedFactor: Float = 1f) {
        Log.debug("MusicTrackController.resume called")
        if (isInactive()) return
        if ((state == ControllerState.Pause || state == ControllerState.PauseOnShutdown)
                && current != null
                && current!!.state.canPlay
                && current!!.music != null) {
            val fadingStep = defaultFadingStep * speedFactor.coerceIn(0.001f..1000f)
            current!!.startFade(MusicTrackController.State.FadeIn, fadingStep)
            // this may circumvent a PlaySingle, but -
            // currently only the main menu resumes, and then it's perfect:
            state = ControllerState.Playing
            current!!.play()
        } else if (state == ControllerState.Cleanup || state == ControllerState.Pause) {
            chooseTrack()
        }

        resumeOverlay()
    }

    /** Fade out then shutdown with a given [duration] in seconds,
     *  defaults to a 'slow' fade (4.5s) */
    @Suppress("MemberVisibilityCanBePrivate")
    fun fadeoutToSilence(duration: Float = defaultFadeDuration * 5) {
        if (isInactive()) return
        val fadingStep = 1f / ticksPerSecond / duration
        current?.startFade(MusicTrackController.State.FadeOut, fadingStep)
        next?.startFade(MusicTrackController.State.FadeOut, fadingStep)
        overlay?.startFade(MusicTrackController.State.FadeOut, fadingStep)
        state = ControllerState.Shutdown
    }

    /** Update playback volume, to be called from options popup */
    fun setVolume(volume: Float) {
        if (isInactive()) return
        baseVolume = volume
        if ( volume < 0.01 ) shutdown()
        else if (isPlaying()) current?.setVolume(baseVolume * maxVolume)
    }

    fun setCurrentPosition(position: Float) {
        val music = current?.music ?: return
        if (!state.showTrack || !current!!.isPlaying()) return
        music.seekTo(position)
    }

    /** Soft shutdown of music playback, with fadeout */
    fun gracefulShutdown() {
        if (isInactive()) return
        if (state == ControllerState.Cleanup) shutdown()
        else fadeoutToSilence(defaultFadeDuration)
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
                // Don't list() here - no work on packaged internal
                gdxSupportedFileExtensions.asSequence().map { extension ->
                    it.child("$name.$extension")
                }.filter { file ->
                    file.exists() && !file.isDirectory
                }
            }

    /** Play [name] from any mod's [folder] or internal assets,
     *  fading in to [volume] then looping if [isLooping] is set.
     *  does nothing if no such file is found.
     *  Note that [volume] intentionally defaults to soundEffectsVolume, not musicVolume
     *  as that fits the "Leader voice" usecase better.
     */
    fun playOverlay(
        name: String,
        folder: String = "sounds",
        volume: Float = settings.soundEffectsVolume,
        isLooping: Boolean = false,
        fadeIn: Boolean = false
    ) {
        if (isInactive()) return
        Concurrency.run { // no reason for this to run on GL thread
            val file = getMatchingFiles(folder, name).firstOrNull() ?: return@run
            playOverlay(file, volume, isLooping, fadeIn)
        }
    }

    /** Called for Leader Voices */
    fun playVoice(name: String) = playOverlay(name, "voices", settings.voicesVolume)
    /** Determines if any 'voices' folder exists in any currently active mod */
    fun isVoicesAvailable() = getMusicFolders("voices").any()

    /** Play [file], [optionally][fadeIn] fading in to [volume] then looping if [isLooping] is set */
    @Suppress("MemberVisibilityCanBePrivate")  // open to future use
    fun playOverlay(file: FileHandle, volume: Float, isLooping: Boolean, fadeIn: Boolean) {
        if (isInactive()) return
        clearOverlay()
        MusicTrackController(miniAudio, volume, initialFadeVolume = if (fadeIn) 0f else 1f).load(file) {
            it.music?.isLooping = isLooping
            it.play()
            //todo Needs to be called even when no fade desired to correctly set state - Think about changing that
            it.startFade(MusicTrackController.State.FadeIn)
            overlay = it
        }
    }

    /** Fade out any playing overlay then clean up */
    fun stopOverlay() {
        if (isInactive()) return
        overlayPausing = false
        overlay?.startFade(MusicTrackController.State.FadeOut)
    }

    private fun pauseOverlay() {
        overlayPausing = true
        overlay?.startFade(MusicTrackController.State.FadeOut)
    }

    private fun resumeOverlay() {
        overlay?.run {
            if (!state.canPlay || state == MusicTrackController.State.Playing) return
            startFade(MusicTrackController.State.FadeIn)
            play()
        }
    }

    private fun MusicTrackController.overlayTick() {
        if (!timerTick().canPlay) return
        if (!overlayPausing && !isPlaying())
            clearOverlay()  // means FadeOut-to-stop finished
    }

    private fun clearOverlay() {
        overlay?.dispose()
        overlay = null
    }

    override fun dispose() {
        shutdown()
        state = ControllerState.Disposed
    }

    //endregion
}
