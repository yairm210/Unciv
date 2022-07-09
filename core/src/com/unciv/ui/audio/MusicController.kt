package com.unciv.ui.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Files.FileType
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSettings
import com.unciv.logic.multiplayer.storage.DropBox
import com.unciv.utils.Log
import com.unciv.utils.debug
import java.util.*
import kotlin.concurrent.thread
import kotlin.concurrent.timer
import kotlin.math.roundToInt


/**
 * Play, choose, fade-in/out and generally manage music track playback.
 *
 * Main methods: [chooseTrack], [pause], [resume], [setModList], [isPlaying], [gracefulShutdown]
 */
class MusicController {
    companion object {
        /** Mods live in Local - but this file prepares for music living in External just in case */
        private val musicLocation = FileType.Local
        private const val musicPath = "music"
        private const val modPath = "mods"
        private const val musicFallbackLocation = "/music/thatched-villagers.mp3"  // Dropbox path
        private const val musicFallbackLocalName = "music/Thatched Villagers - Ambient.mp3"  // Name we save it to
        private const val maxVolume = 0.6f                  // baseVolume has range 0.0-1.0, which is multiplied by this for the API
        private const val ticksPerSecondGdx = 58.3f  // *Observed* frequency of Gdx app loop
        private const val ticksPerSecondOwn = 20f    // Timer frequency when we use our own
        private const val defaultFadeDuration = 0.9f  // in seconds
        private const val defaultFadingStepGdx = 1f / (defaultFadeDuration * ticksPerSecondGdx)
        private const val defaultFadingStepOwn = 1f / (defaultFadeDuration * ticksPerSecondOwn)
        private const val musicHistorySize = 8      // number of names to keep to avoid playing the same in short succession
        val gdxSupportedFileExtensions = listOf("mp3", "ogg", "wav")   // All Gdx formats

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

    //region Fields
    /** mirrors [GameSettings.musicVolume] - use [setVolume] to update */
    var baseVolume: Float = UncivGame.Current.settings.musicVolume
        private set

    /** Pause in seconds between tracks unless [chooseTrack] is called to force a track change */
    var silenceLength: Float
        get() = silenceLengthInTicks.toFloat() / ticksPerSecond
        set(value) { silenceLengthInTicks = (ticksPerSecond * value).toInt() }

    private var silenceLengthInTicks = (UncivGame.Current.settings.pauseBetweenTracks * ticksPerSecond).roundToInt()

    private var mods = HashSet<String>()

    private var state = ControllerState.Idle

    private var ticksOfSilence: Int = 0

    private var musicTimer: Timer? = null

    private enum class ControllerState {
        /** Own timer stopped, if using the HardenedGdxAudio callback just do nothing */
        Idle,
        /** As the name says. Loop will release everything and go [Idle] if it encounters this state. */
        Cleanup,
        /** Play a track to its end, then silence for a while, then choose another track */
        Playing,
        /** Play a track to its end, then [Cleanup] */
        PlaySingle,
        /** Wait for a while in silence to start next track */
        Silence,
        /** Music fades to pause or is paused. Continue with chooseTrack or resume. */
        Pause,
        /** Fade out then [Cleanup] */
        Shutdown
    }

    /** Simple two-entry only queue, for smooth fade-overs from one track to another */
    private var current: MusicTrackController? = null
    private var next: MusicTrackController? = null

    /** Keeps paths of recently played track to reduce repetition */
    private val musicHistory = ArrayDeque<String>(musicHistorySize)

    /** One potential listener gets notified when track changes */
    private var onTrackChangeListener: ((String)->Unit)? = null

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

    /** @return the path of the playing track or null if none playing */
    private fun currentlyPlaying(): String = when(state) {
        ControllerState.Playing, ControllerState.PlaySingle, ControllerState.Pause ->
            musicHistory.peekLast() ?: ""
        else -> ""
    }

    /** Registers a callback that will be called with the new track name every time it changes.
     *  The track name will be prettified ("Modname: Track" instead of "mods/Modname/music/Track.ogg").
     *
     *  Will be called on a background thread, so please decouple UI access on the receiving side.
     */
    fun onChange(listener: ((String)->Unit)?) {
        onTrackChangeListener = listener
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
        // Start background TimerTask which manages track changes - on desktop, we get callbacks from the app.loop instead
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
        when (state) {
            ControllerState.Idle -> return

            ControllerState.Playing, ControllerState.PlaySingle ->
                if (current == null) {
                    if (next == null) {
                        // no music to play - begin silence or shut down
                        ticksOfSilence = 0
                        state = if (state == ControllerState.PlaySingle) ControllerState.Shutdown else ControllerState.Silence
                        fireOnChange()
                    } else if (next!!.state.canPlay) {
                        // Next track - if top slot empty and a next exists, move it to top and start
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
                    // normal end of track
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
        musicHistory.clear()
        debug("MusicController shut down.")
    }

    private fun audioExceptionHandler(ex: Throwable, music: Music) {
        // Should run only in exceptional cases when the Gdx codecs actually have trouble with a file.
        // Most playback problems are caught by the similar handler in MusicTrackController

        // Gdx _will_ try to read more data from file in Lwjgl3Application.loop even for
        // Music instances that already have thrown an exception.
        // disposing as quickly as possible is a feeble attempt to prevent that.
        music.dispose()
        if (music == next?.music) clearNext()
        if (music == current?.music) clearCurrent()

        Log.error("Error playing music", ex)

        // Since this is a rare emergency, go a simple way to reboot music later
        thread(isDaemon = true) {
            Thread.sleep(2000)
            Gdx.app.postRunnable {
                this.chooseTrack()
            }
        }
    }

    /** Get sequence of potential music locations */
    private fun getMusicFolders() = sequence {
        yieldAll(
            (UncivGame.Current.settings.visualMods + mods).asSequence()
                .map { getFile(modPath).child(it).child(musicPath) }
        )
        yield(getFile(musicPath))
    }

    /** Get sequence of all existing music files */
    private fun getAllMusicFiles() = getMusicFolders()
        .filter { it.exists() && it.isDirectory }
        .flatMap { it.list().asSequence() }
        // ensure only normal files with common sound extension
        .filter { it.exists() && !it.isDirectory && it.extension() in gdxSupportedFileExtensions }

    /** Choose adequate entry from [getAllMusicFiles] */
    private fun chooseFile(prefix: String, suffix: String, flags: EnumSet<MusicTrackChooserFlags>): FileHandle? {
        if (flags.contains(MusicTrackChooserFlags.PlayDefaultFile)) {
            val defaultFile = getFile(musicFallbackLocalName)
            // Test so if someone never downloaded Thatched Villagers, their volume slider will still play music
            if (defaultFile.exists()) return defaultFile
        }
        // Scan whole music folder and mods to find best match for desired prefix and/or suffix
        // get a path list (as strings) of music folder candidates - existence unchecked
        return getAllMusicFiles()
            .filter {
                (!flags.contains(MusicTrackChooserFlags.PrefixMustMatch) || it.nameWithoutExtension().startsWith(prefix))
                        && (!flags.contains(MusicTrackChooserFlags.SuffixMustMatch) || it.nameWithoutExtension().endsWith(suffix))
            }
            // randomize
            .shuffled()
            // sort them by prefix match / suffix match / not last played
            .sortedWith(compareBy(
                { if (it.nameWithoutExtension().startsWith(prefix)) 0 else 1 }
                , { if (it.nameWithoutExtension().endsWith(suffix)) 0 else 1 }
                , { if (it.path() in musicHistory) 1 else 0 }
            // Then just pick the first one. Not as wasteful as it looks - need to check all names anyway
            )).firstOrNull()
        // Note: shuffled().sortedWith(), ***not*** .sortedWith(.., Random)
        // the latter worked with older JVM's, current ones *crash* you when a compare is not transitive.
    }

    private fun fireOnChange() {
        if (onTrackChangeListener == null) return
        val fileName = currentlyPlaying()
        if (fileName.isEmpty()) {
            fireOnChange(fileName)
            return
        }
        val fileNameParts = fileName.split('/')
        val modName = if (fileNameParts.size > 1 && fileNameParts[0] == "mods") fileNameParts[1] else ""
        var trackName = fileNameParts[if (fileNameParts.size > 3 && fileNameParts[2] == "music") 3 else 1]
        for (extension in gdxSupportedFileExtensions)
            trackName = trackName.removeSuffix(".$extension")
        fireOnChange(modName + (if (modName.isEmpty()) "" else ": ") + trackName)
    }
    private fun fireOnChange(trackLabel: String) {
        try {
            onTrackChangeListener?.invoke(trackLabel)
        } catch (ex: Throwable) {
            debug("onTrackChange event invoke failed", ex)
            onTrackChangeListener = null
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
     * Called without parameters it will choose a new ambient music track and start playing it with fade-in/out.
     * Will do nothing when no music files exist or the master volume is zero.
     *
     * @param prefix file name prefix, meant to represent **Context** - in most cases a Civ name
     * @param suffix file name suffix, meant to represent **Mood** - e.g. Peace, War, Theme, Defeat, Ambient
     * (Ambient is the default when a track ends and exists so War Peace and the others are not chosen in that case)
     * @param flags a set of optional flags to tune the choice and playback.
     * @return `true` = success, `false` = no match, no playback change
     */
    fun chooseTrack (
        prefix: String = "",
        suffix: String = MusicMood.Ambient,
        flags: EnumSet<MusicTrackChooserFlags> = EnumSet.of(MusicTrackChooserFlags.SuffixMustMatch)
    ): Boolean {
        if (baseVolume == 0f) return false

        val musicFile = chooseFile(prefix, suffix, flags)

        if (musicFile == null) {
            // MustMatch flags at work or Music folder empty
            debug("No music found for prefix=%s, suffix=%s, flags=%s", prefix, suffix, flags)
            return false
        }
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
            debug("Music queued: %s for prefix=%s, suffix=%s, flags=%s", musicFile.path(), prefix, suffix, flags)

            if (musicHistory.size >= musicHistorySize) musicHistory.removeFirst()
            musicHistory.addLast(musicFile.path())

            // This is what makes a track change fade _over_ current fading out and next fading in at the same time.
            it.play()

            val fadingStep = defaultFadingStep / (if (flags.contains(MusicTrackChooserFlags.SlowFade)) 5 else 1)
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
        state = if (flags.contains(MusicTrackChooserFlags.PlaySingle)) ControllerState.PlaySingle else ControllerState.Playing
        startTimer()
        return true
    }

    /** Variant of [chooseTrack] that tries several moods ([suffixes]) until a match is chosen */
    fun chooseTrack (
        prefix: String = "",
        suffixes: List<String>,
        flags: EnumSet<MusicTrackChooserFlags> = EnumSet.noneOf(MusicTrackChooserFlags::class.java)
    ): Boolean {
        for (suffix in suffixes) {
            if (chooseTrack(prefix, suffix, flags)) return true
        }
        return false
    }

    /**
     * Pause playback with fade-out
     *
     * @param speedFactor accelerate (>1) or slow down (<1) the fade-out. Clamped to 1/1000..1000.
     */
    fun pause(speedFactor: Float = 1f) {
        debug("MusicTrackController.pause called")
        if ((state != ControllerState.Playing && state != ControllerState.PlaySingle) || current == null) return
        val fadingStep = defaultFadingStep * speedFactor.coerceIn(0.001f..1000f)
        current!!.startFade(MusicTrackController.State.FadeOut, fadingStep)
        if (next?.state == MusicTrackController.State.FadeIn)
            next!!.startFade(MusicTrackController.State.FadeOut)
        state = ControllerState.Pause
    }

    /**
     * Resume playback with fade-in - from a pause will resume where playback left off,
     * otherwise it will start a new ambient track choice.
     *
     * @param speedFactor accelerate (>1) or slow down (<1) the fade-in. Clamped to 1/1000..1000.
     */
    fun resume(speedFactor: Float = 1f) {
        debug("MusicTrackController.resume called")
        if (state == ControllerState.Pause && current != null) {
            val fadingStep = defaultFadingStep * speedFactor.coerceIn(0.001f..1000f)
            current!!.startFade(MusicTrackController.State.FadeIn, fadingStep)
            state = ControllerState.Playing  // this may circumvent a PlaySingle, but, currently only the main menu resumes, and then it's perfect
            current!!.play()
        } else if (state == ControllerState.Cleanup) {
            chooseTrack()
        }
    }

    /** Fade out then shutdown with a given [duration] in seconds, defaults to a 'slow' fade (4.5s) */
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
}
