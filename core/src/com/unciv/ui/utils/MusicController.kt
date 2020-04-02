package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.unciv.ui.worldscreen.mainmenu.DropBox
import java.util.*
import kotlin.concurrent.timer

private enum class MusicStatus {
    Idle,
    Playing,        // Play a track to its end, then silence for a while, then choose another track
    Silence,        // wait for a while in silence to start next track
    PlaySingle,     // Play a track to its end, then go idle
    Pause,          // Music fades to pause or is paused. Continue with chooseTrack or resume.
    Shutdown        // Fade out then stop
}
private enum class FadeStatus {
    None,
    FadeOut,
    FadeIn
}
enum class MusicTrackChooserFlags {
    PrefixMustMatch,
    SuffixMustMatch,
    SlowFade,
    PlaySingle,
    PlayDefaultFile         // directly choose fallback file
}
class MusicController(var baseVolume: Float) {
    val musicPath = "music"
    val musicFallbackLocation = "music/Ambient_Thatched-Villagers.mp3"
    private val musicOldLocation = "music/thatched-villagers.mp3"
    val maxVolume = 0.75f                // baseVolume has range 0.0-1.0, which is multiplied by this for the API
    val silenceLengthInTicks = 100
    val defaultFadingStep = 0.08f              // this means fade is 12 ticks or 0.6 seconds
    private var currentFadingStep = defaultFadingStep
    val musicHistorySize = 5            // number of names to keep to avoid playing the same in short succession
    private val fileExtensions = listOf("mp3", "ogg", "m4a")        // flac, opus blocked by Gdx
    private var mods = HashSet<String>()

    private var status = MusicStatus.Idle
    private var isFading = FadeStatus.None     // Fade-out or -in current track. Fade-end action depends on status. -in only used for resumePaused.
    private var musicTimer: Timer? = null
    private var currentMusic: Music? = null
    private var nextMusic: Music? = null
    private var fadingFactor: Float = 1f
    private var ticksOfSilence: Int = 0
    private val musicHistory = ArrayDeque<String>(5)

    init {
        // I'd like to prefix the default track if already downloaded under the old name with the default prefix
        val musicFile = Gdx.files.local(musicFallbackLocation)      // reused below as default
        val oldMusic = Gdx.files.local(musicOldLocation)
        if (oldMusic.exists() && !musicFile.exists()) {
            oldMusic.moveTo(musicFile)
        }
    }

    private fun cleanupCurrent() {
        currentMusic!!.stop()
        currentMusic!!.dispose()
        currentMusic = null
    }

    private fun musicTimerTask() {
        // This ticks 20 times per second
        // For smooth changes I made a poor man's two-slot queue (currentMusic,nextMusic)

        if (isFading == FadeStatus.FadeIn) {
            // fade-in: linearly ramp fadingFactor to 1.0, then continue playing
            fadingFactor += currentFadingStep
            if (fadingFactor < 1f  && currentMusic != null && currentMusic!!.isPlaying) {
                currentMusic!!.volume = baseVolume * maxVolume * fadingFactor
                return
            }
            // End of fade-in: Currently Status is already reset to Playing
            currentMusic!!.volume = baseVolume * maxVolume
            fadingFactor = 1f
            currentFadingStep = defaultFadingStep;
            isFading = FadeStatus.None
            return
        }
        if (isFading == FadeStatus.FadeOut) {
            // fade-out: linearly ramp fadingFactor to 0.0, then act according to Status (Playing->Silence/Pause/Shutdown)
            fadingFactor -= currentFadingStep
            if (fadingFactor >= 0.001f && currentMusic != null && currentMusic!!.isPlaying) {
                currentMusic!!.volume = baseVolume * maxVolume * fadingFactor
                return
            }
            // End of fade-out
            fadingFactor = 0f
            currentFadingStep = defaultFadingStep;
            isFading = FadeStatus.None
            when (status) {
                MusicStatus.Playing, MusicStatus.PlaySingle -> cleanupCurrent()   // Next Tick will do what's appropriate
                MusicStatus.Pause -> currentMusic!!.pause()
                else -> Unit
            }
            return
        }

        when (status) {
            MusicStatus.Playing, MusicStatus.PlaySingle ->
                if (currentMusic == null) {
                    fadingFactor = 1f
                    isFading = FadeStatus.None
                    if (nextMusic != null) {
                        // Next track - if top slot empty and a next exists, move it to top and start
                        currentMusic = nextMusic
                        nextMusic = null
                        currentMusic!!.volume = baseVolume * maxVolume
                        try {
                            currentMusic!!.play()
                        } catch (ex: Exception) {
                            println("Exception starting music: ${ex.localizedMessage}")
                            println("  (Source file ${ex.stackTrace[0].fileName} line ${ex.stackTrace[0].lineNumber})")
                            status = MusicStatus.Shutdown
                        }
                    } else {
                        ticksOfSilence = 0
                        status = if (status==MusicStatus.PlaySingle) MusicStatus.Shutdown else MusicStatus.Silence
                    }
                } else if (!currentMusic!!.isPlaying) {
                    // normal end of track
                    cleanupCurrent()
                    if (nextMusic == null) {
                        ticksOfSilence = 0
                        status = if (status==MusicStatus.PlaySingle) MusicStatus.Shutdown else MusicStatus.Silence
                    }
                }
            MusicStatus.Silence ->
                if (++ticksOfSilence > silenceLengthInTicks) {
                    status = MusicStatus.Idle
                    chooseTrack()
                }
            MusicStatus.Shutdown -> {
                status = MusicStatus.Idle
                shutdown()
            }
            else -> Unit            // Pause or Idle
        }
    }

    fun setModList ( newMods: HashSet<String> ) {
        //todo: Ensure this gets updated where appropriate.
        // loadGame: OK; newGame: Choose Map with Mods:?; map editor: not yet implemented
        // check against "ImageGetter.ruleset=" ?
        mods = newMods
    }

    private fun getMusicFolders(): List<String> {
        val pathList = mutableListOf(musicPath)
        pathList.addAll (
                Gdx.files.local("mods")
                        .list()
                        .filter { it.name() in mods }
                        .map { it.path() + "/music" } )
        return pathList
    }

    fun chooseTrack (
            prefix: String = "Ambient",   // Prefix is meant as representing Context - in most cases a Civ name or default "Ambient"
            suffix: String = "",          // Suffix is meant as representing Mood - Peace, War and Theme (picker in new game dialog) currently used
            flags: EnumSet<MusicTrackChooserFlags> = EnumSet.noneOf(MusicTrackChooserFlags::class.java)
    ): Boolean {
        // Return Value: true = success, false = no match, no change
        val musicFile =
                (if (flags.contains(MusicTrackChooserFlags.PlayDefaultFile))
                    Gdx.files.local(musicFallbackLocation)
                else
                // Scan whole music folder and mods to find best match for desired prefix and/or suffix
                // get a path list (as strings) of music folder candidates - existence unchecked
                    getMusicFolders()
                            // map to Gdx FileHandle list
                            .map { Gdx.files.local(it) }
                            // existing directories only, then select all their contents as FileHandle list
                            .filter { it.exists() && it.isDirectory }.flatMap { it.list().toList() }
                            // ensure only normal files with common sound extension
                            .filter { it.exists() && !it.isDirectory
                                    && it.extension() in fileExtensions
                                    && (!flags.contains(MusicTrackChooserFlags.PrefixMustMatch) || it.nameWithoutExtension().startsWith(prefix))
                                    && (!flags.contains(MusicTrackChooserFlags.SuffixMustMatch) || it.nameWithoutExtension().endsWith(suffix))
                            }
                            // sort them by prefix match / suffix match / not last played / random
                            .sortedWith(compareBy(
                                    { if (it.nameWithoutExtension().startsWith(prefix)) 0 else 1 }
                                    , { if (it.nameWithoutExtension().endsWith(suffix)) 0 else 1 }
                                    , { if (it.path() in musicHistory) 1 else 0 }
                                    , { Random().nextInt() }))
                            // Then just pick the first one. Not so wasteful as it looks - need to check all names anyway
                            .firstOrNull())
        if (musicFile==null) {
            // MustMatch flags at work or Music folder empty
            println("No music found for prefix=$prefix, suffix=$suffix, flags=$flags")
            return false
        }

        if (musicFile.path() == musicHistory.peekLast()) return true    // picked file already playing
        if (!musicFile.exists()) return false        // Safety check - nothing to play found?
        currentFadingStep = defaultFadingStep /
                (if (flags.contains(MusicTrackChooserFlags.SlowFade)) 5 else 1)
        try {
            val music = Gdx.audio.newMusic(musicFile)
            if (musicHistory.size >= musicHistorySize) musicHistory.remove()
            musicHistory.add(musicFile.path())
            //music.isLooping = false           // choosing more after a track finishes is handled later
            nextMusic = music
            when (status) {
                MusicStatus.Playing, MusicStatus.PlaySingle -> isFading = FadeStatus.FadeOut
                MusicStatus.Pause -> if (isFading==FadeStatus.None) cleanupCurrent();
                else -> Unit
            }
            status = if (flags.contains(MusicTrackChooserFlags.PlaySingle)) MusicStatus.PlaySingle else MusicStatus.Playing
            println("Music queued: ${musicFile.path()} for prefix=$prefix, suffix=$suffix, flags=$flags")
        } catch (ex: Exception) {
            println("Exception loading music: ${ex.localizedMessage}")
            println("  (Source file ${ex.stackTrace[0].fileName} line ${ex.stackTrace[0].lineNumber})")
            ticksOfSilence = 0
            status = MusicStatus.Silence // will retry after one silence period
        }

        // Start background TimerTask which manages track changes
        if (musicTimer == null)
            musicTimer = timer("musicTimer", true, 0, 50) { musicTimerTask() }
        return true
    }

    fun pause(speedFactor: Float = 1f) {
        if ((status != MusicStatus.Playing && status != MusicStatus.PlaySingle) || currentMusic == null) return
        isFading = FadeStatus.FadeOut        // we do not set fadingFactor: can pause before the fadein from resume() is over
        currentFadingStep = defaultFadingStep * (if (speedFactor in 0.001f..1000f) speedFactor else 1f)
        status = MusicStatus.Pause
    }
    fun resume(speedFactor: Float = 1f) {
        if (status == MusicStatus.Pause && currentMusic != null) {
            isFading = FadeStatus.FadeIn        // we do not set fadingFactor: can resume before the fadeout from pause() is over
            currentFadingStep = defaultFadingStep * (if (speedFactor in 0.001f..1000f) speedFactor else 1f)
            status = MusicStatus.Playing        // this may circumvent a PlaySingle, but, currently only the main menu resumes, and then it's perfect
            currentMusic!!.play()
        } else if (status == MusicStatus.Idle) {
            chooseTrack()
        }
    }

    fun isMusicAvailable(): Boolean {
        return getMusicFolders()
                .map { Gdx.files.local(it) }
                .filter { it.exists() && it.isDirectory }.flatMap { it.list().toList() }
                .filter { it.exists() && !it.isDirectory && it.extension() in fileExtensions }
                .any()
    }

    fun fadeoutToSilence(duration: Float = 4.0f) {
        currentFadingStep = 0.05f / duration
        isFading = FadeStatus.FadeOut
        status = MusicStatus.Shutdown
    }

    fun setVolume(volume: Float) {
        baseVolume = volume
        if ( volume < 0.01 ) shutdown()
        else if (isPlaying()) currentMusic!!.volume = baseVolume * maxVolume * fadingFactor
    }

    fun isPlaying(): Boolean {
        return currentMusic?.isPlaying ?: false
    }

    fun gracefulShutdown() {
        if (status==MusicStatus.Idle) shutdown()
        else status = MusicStatus.Shutdown
    }

    fun shutdown() {
        status = MusicStatus.Idle
        if (musicTimer != null) {
            musicTimer!!.cancel()
            musicTimer = null
        }
        if (nextMusic!=null) {
            nextMusic!!.dispose()
            nextMusic = null
        }
        if (currentMusic!=null) {
            currentMusic!!.dispose()
            currentMusic = null
        }
        musicHistory.clear()
        println("MusicController shut down.")
    }

    fun downloadDefaultFile() {
        val file = DropBox().downloadFile(musicFallbackLocation)
        Gdx.files.local(musicFallbackLocation).write(file, false)
    }

}