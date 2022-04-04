package com.unciv.ui.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Files.FileType
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSettings
import com.unciv.logic.multiplayer.DropBox
import com.unciv.ui.utils.crashHandlingThread
import java.util.*
import kotlin.concurrent.thread


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
        const val musicFallbackLocation = "music/thatched-villagers.mp3"
        const val maxVolume = 0.6f                  // baseVolume has range 0.0-1.0, which is multiplied by this for the API
        private const val musicHistorySize = 8      // number of names to keep to avoid playing the same in short succession
        private val fileExtensions = listOf("mp3", "ogg")   // flac, opus, m4a... blocked by Gdx, `wav` we don't want

        internal const val consoleLog = false

        private fun getFile(path: String) =
            if (musicLocation == FileType.External && Gdx.files.isExternalStorageAvailable)
                Gdx.files.external(path)
            else Gdx.files.local(path)
    }

    //region Fields
    private var music: Music? = null

    private var loaderThread: Thread? = null

    private var mods = HashSet<String>()

    /** mirrors [GameSettings.musicVolume] - use [setVolume] to update */
    private var baseVolume: Float = UncivGame.Current.settings.musicVolume

    /** Keeps paths of recently played track to reduce repetition */
    private val musicHistory = ArrayDeque<String>(musicHistorySize)

    /** One potential listener gets notified when track changes */
    private var onTrackChangeListener: ((String)->Unit)? = null

    //endregion
    //region Pure functions

    /** @return the path of the playing track or null if none playing */
    private fun currentlyPlaying(): String = if (isPlaying()) musicHistory.peekLast() ?: ""
        else ""

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
        var trackName = fileNameParts.last()
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
        return music?.isPlaying == true
    }

    /** @return `true` if there's a current music playing _or_ a background load is underway.
     * 
     *  Exists to alleviate a race condition when the music volume slider in options is just
     *  moved up from zero, and done so quickly so several changes in succession register.
     *  The handling of interrupt() and isInterrupted helps, but an effect is still audible sometimes.
     */
    fun isPlayingOrLoading(): Boolean {
        return music?.isPlaying == true || loaderThread?.isInterrupted == false
    }

    //endregion
    //region Internal helpers

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

        clearLoader()
        loaderThread = crashHandlingThread(name = "MusicLoader") {
            val newMusic = try {
                Gdx.audio.newMusic(musicFile)
            } catch (ex: Throwable) {
                println("Exception loading ${musicFile.name()}: ${ex.message}")
                if (consoleLog)
                    ex.printStackTrace()
                null
            }
            if (loaderThread?.isInterrupted == true) {
                newMusic?.dispose()
            } else if (newMusic != null) {
                if (consoleLog)
                    println("Music loaded: ${musicFile.path()} for prefix=$prefix, suffix=$suffix, flags=$flags")
                newMusic.volume = baseVolume * maxVolume
                if (tryPlay(newMusic)) {
                    clearMusic()
                    if (consoleLog)
                        println("Music successfully started.")
                    if (musicHistory.size >= musicHistorySize) musicHistory.removeFirst()
                    musicHistory.addLast(musicFile.path())
                    newMusic.setOnCompletionListener(Music.OnCompletionListener {
                        if (consoleLog)
                            println("MusicController: OnCompletionListener called")
                        it.dispose()
                        clear()
                        chooseTrack()
                    })
                    music = newMusic
                }
            }
            fireOnChange()
            loaderThread = null
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

    private fun tryPlay(): Boolean {
        // Version for this.music
        if (music == null) return false
        if (tryPlay(music!!)) return true
        music = null
        return false
    }

    private fun tryPlay(music: Music): Boolean {
        // Version for newly created Music from chooseTrack
        return try {
            music.play()
            true
        } catch (ex: Throwable) {
            audioExceptionHandler(ex, music)
            false
        }
    }

    /**
     * Pause playback
     */
    fun pause() {
        if (consoleLog)
            println("MusicTrackController.pause called")
        if (isPlaying())
            music!!.pause()
    }

    /**
     * Resume playback with fade-in - from a pause will resume where playback left off,
     * otherwise it will start a new ambient track choice.
     */
    fun resume() {
        if (consoleLog)
            println("MusicTrackController.resume called")
        if (isPlaying()) return
        if (!tryPlay())
            chooseTrack()
    }

    /** Update playback volume, to be called from options popup */
    fun setVolume(volume: Float) {
        baseVolume = volume
        if ( volume < 0.01 ) shutdown()
        else if (isPlaying()) music!!.volume = baseVolume * maxVolume
    }

    /** Soft shutdown of music playback, with fadeout */
    fun gracefulShutdown() {
        shutdown()
    }

    /** Forceful shutdown of music playback and timers - see [gracefulShutdown] */
    private fun shutdown() {
        clear()
        fireOnChange()
        // keep onTrackChangeListener! OptionsPopup will want to know when we start up again
        musicHistory.clear()
        if (consoleLog)
            println("MusicController shut down.")
    }

    private fun clearLoader() {
        if (loaderThread == null) return
        loaderThread!!.interrupt()
        loaderThread = null
    }
    private fun clearMusic() {
        if (music == null) return
        music!!.dispose()
        music = null
    }
    private fun clear() {
        clearLoader()
        clearMusic()
    }

    fun downloadDefaultFile() {
        val file = DropBox.downloadFile(musicFallbackLocation)
        getFile(musicFallbackLocation).write(file, false)
    }

    private fun audioExceptionHandler(ex: Throwable, music: Music) {
        // Gdx _will_ try to read more data from file in Lwjgl3Application.loop even for
        // Music instances that already have thrown an exception.
        // disposing as quickly as possible is a feeble attempt to prevent that.
        music.dispose()
        if (music == this.music)
            this.music = null
        clearLoader()

        if (consoleLog) {
            println("${ex.javaClass.simpleName} playing music: ${ex.message}")
            if (ex.stackTrace != null) ex.printStackTrace()
        } else {
            println("Error playing music: ${ex.message ?: ""}")
        }

        thread {
            Thread.sleep(2000)
            Gdx.app.postRunnable {
                this.chooseTrack()
            }
        }
    }
    fun getAudioExceptionHandler() = {
        ex: Throwable, music: Music ->
        audioExceptionHandler(ex, music)
    }

    //endregion
}
