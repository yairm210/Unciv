package com.unciv.ui.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Files.FileType
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSettings
import com.unciv.logic.multiplayer.DropBox
import java.util.*
import kotlin.concurrent.timer


/**
 * Play, choose, fade-in/out and generally manage music track playback.
 * 
 * Main methods: [chooseTrack], [pause], [resume], [setModList], [isPlaying], [gracefulShutdown]
 */
class MusicController {
    companion object {
        /** Mods live in Local - but this file prepares for music living in External just in case */
        private val musicLocation = FileType.Local
        const val musicPath = "music"
        const val modPath = "mods"
        const val musicFallbackLocation = "/music/thatched-villagers.mp3"
        const val maxVolume = 0.6f                  // baseVolume has range 0.0-1.0, which is multiplied by this for the API
        private const val ticksPerSecond = 20       // Timer frequency defines smoothness of fade-in/out
        private const val timerPeriod = 1000L / ticksPerSecond
        const val defaultFadingStep = 0.08f         // this means fade is 12 ticks or 0.6 seconds
        private const val musicHistorySize = 8      // number of names to keep to avoid playing the same in short succession
        private val fileExtensions = listOf("mp3", "ogg")   // flac, opus, m4a... blocked by Gdx, `wav` we don't want

        internal const val consoleLog = false

        private fun getFile(path: String) =
            if (musicLocation == FileType.External && Gdx.files.isExternalStorageAvailable)
                Gdx.files.external(path)
            else Gdx.files.local(path)

        internal fun showPlayingException(ex: Throwable) {
            if (consoleLog) {
                println("${ex.javaClass.simpleName} playing music: ${ex.message}")
                if (ex.stackTrace != null) ex.printStackTrace()
            } else {
                println("Error playing music: ${ex.message}")
            }
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
    private var silenceLengthInTicks = UncivGame.Current.settings.pauseBetweenTracks * ticksPerSecond

    private var mods = HashSet<String>()

    private var state = ControllerState.Idle

    private var ticksOfSilence: Int = 0

    private var musicTimer: Timer? = null

    private enum class ControllerState {
        /** As the name says. Timer will stop itself if it encounters this state. */
        Idle,
        /** Play a track to its end, then silence for a while, then choose another track */
        Playing,
        /** Play a track to its end, then go [Idle] */
        PlaySingle,
        /** Wait for a while in silence to start next track */
        Silence,
        /** Music fades to pause or is paused. Continue with chooseTrack or resume. */
        Pause,
        /** Fade out then stop */
        Shutdown
    }

    /** Simple two-entry only queue, for smooth fade-overs from one track to another */
    var current: MusicTrackController? = null
    var next: MusicTrackController? = null

    /** Keeps paths of recently played track to reduce repetition */
    private val musicHistory = ArrayDeque<String>(musicHistorySize)

    /** One potential listener gets notified when track changes */
    private var onTrackChangeListener: ((String)->Unit)? = null

    //endregion
    //region Pure functions

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
        for (extension in fileExtensions)
            trackName = trackName.removeSuffix(".$extension")
        fireOnChange(modName + (if (modName.isEmpty()) "" else ": ") + trackName)
    }
    private fun fireOnChange(trackLabel: String) {
        try {
            onTrackChangeListener?.invoke(trackLabel)
        } catch (ex: Throwable) {
            if (consoleLog)
                println("onTrackChange event invoke failed: ${ex.message}")
            onTrackChangeListener = null
        }
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

    private fun musicTimerTask() {
        // This ticks [ticksPerSecond] times per second
        when (state) {
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
                    state = ControllerState.Idle
            }
            ControllerState.Idle ->
                shutdown()  // stops timer so this will not repeat
            ControllerState.Pause ->
                current?.timerTick()
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
        .filter { it.exists() && !it.isDirectory && it.extension() in fileExtensions }

    /** Choose adequate entry from [getAllMusicFiles] */
    private fun chooseFile(prefix: String, suffix: String, flags: EnumSet<MusicTrackChooserFlags>): FileHandle? {
        if (flags.contains(MusicTrackChooserFlags.PlayDefaultFile))
            return getFile(musicFallbackLocation)
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
        suffix: String = "Ambient", 
        flags: EnumSet<MusicTrackChooserFlags> = EnumSet.noneOf(MusicTrackChooserFlags::class.java)
    ): Boolean {
        if (baseVolume == 0f) return false

        val musicFile = chooseFile(prefix, suffix, flags)

        if (musicFile == null) {
            // MustMatch flags at work or Music folder empty
            if (consoleLog)
                println("No music found for prefix=$prefix, suffix=$suffix, flags=$flags")
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
            if (consoleLog)
                println("Music queued: ${musicFile.path()} for prefix=$prefix, suffix=$suffix, flags=$flags")

            if (musicHistory.size >= musicHistorySize) musicHistory.removeFirst()
            musicHistory.addLast(musicFile.path())

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

        // Start background TimerTask which manages track changes
        if (musicTimer == null)
            musicTimer = timer("MusicTimer", true, 0, timerPeriod) {
                musicTimerTask()
            }

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
        if (consoleLog)
            println("MusicTrackController.pause called")
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
        if (consoleLog)
            println("MusicTrackController.resume called")
        if (state == ControllerState.Pause && current != null) {
            val fadingStep = defaultFadingStep * speedFactor.coerceIn(0.001f..1000f)
            current!!.startFade(MusicTrackController.State.FadeIn, fadingStep)
            state = ControllerState.Playing  // this may circumvent a PlaySingle, but, currently only the main menu resumes, and then it's perfect
            current!!.play()
        } else if (state == ControllerState.Idle) {
            chooseTrack()
        }
    }

    /** Fade out then shutdown with a given [duration] in seconds */
    fun fadeoutToSilence(duration: Float = 4.0f) {
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
        if (state == ControllerState.Idle) shutdown()
        else state = ControllerState.Shutdown
    }

    /** Forceful shutdown of music playback and timers - see [gracefulShutdown] */
    private fun shutdown() {
        state = ControllerState.Idle
        fireOnChange()
        // keep onTrackChangeListener! OptionsPopup will want to know when we start up again
        if (musicTimer != null) {
            musicTimer!!.cancel()
            musicTimer = null
        }
        clearNext()
        clearCurrent()
        musicHistory.clear()
        if (consoleLog)
            println("MusicController shut down.")
    }

    fun downloadDefaultFile() {
        val file = DropBox.downloadFile(musicFallbackLocation)
        getFile(musicFallbackLocation).write(file, false)
    }

    fun getAudioExceptionHandler(): ((Throwable, Music) -> Unit) {
        return {
            ex: Throwable, music: Music ->
            // Just clean up, recovery will be handled by the timer
            if (music == next?.music) clearNext()
            else if (music == current?.music) clearCurrent()
            showPlayingException(ex)
        }
    }

    //endregion
}
