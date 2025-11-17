package com.unciv.ui.audio

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.models.UncivSound
import com.unciv.utils.Concurrency
import com.unciv.utils.debug
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/*
 * Problems on Android
 *
 * Essentially the freshly created Gdx Sound object from newSound() is not immediately usable, it
 * needs some preparation time - buffering, decoding, whatever. Calling play() immediately will result
 * in no sound, a logcat warning (not ready), and nothing else - specifically no exceptions. Also,
 * keeping a Sound object for longer than necessary to play it once will trigger CloseGuard warnings
 * (resource failed to clean up). Also, Gdx will attempt fast track, which will cause logcat entries,
 * and these will be warnings if the sound file's sample rate (not bitrate) does not match the device's
 * hardware preferred bitrate. On a Xiaomi Mi8 that is 48kHz, not 44.1kHz. Channel count also must match.
 *
 * @see "https://github.com/libgdx/libgdx/issues/1775"
 * logcat entry "W/System: A resource failed to call end.":
 *  unavoidable as long as we cache Gdx Sound objects loaded from assets
 * logcat entry "W/SoundPool: sample X not READY":
 *  could be avoided by preloading the 'cache' or otherwise ensuring a minimum delay between
 *  newSound() and play() - there's no test function that does not trigger logcat warnings.
 *
 * Current approach: Cache on demand as before, catch stream not ready and retry. This maximizes
 * logcat messages but user experience is acceptable. Empiric delay needed was measured a 40ms
 * so that is the minimum wait before attempting play when we know the sound is freshly cached
 * and the system is Android.
 */

/**
 * Generates Gdx [Sound] objects from [UncivSound] ones on demand, only once per key
 * (two UncivSound custom instances with the same filename are considered equal).
 *
 * Gdx asks Sound usage to respect the Disposable contract, but since we're only caching
 * a handful of them in memory we should be able to get away with keeping them alive for the
 * app lifetime - and we do dispose them when the app is disposed.
 */
object SoundPlayer {
    @Suppress("EnumEntryName")
    private enum class SupportedExtensions { mp3, ogg, wav }    // Per Gdx docs, no aac/m4a

    private val soundMap = Cache()

    private val separator = File.separator      // just a shorthand for readability

    private var modListHash = Int.MIN_VALUE

    private var preloader: Preloader? = null
        // Starting an instance here is pointless - object initialization happens on-demand

    /** Ensure SoundPlayer has its cache initialized and can start preloading */
    fun initializeForMainMenu() = checkCache()

    /** Ensure cache is not outdated */
    private fun checkCache() {
        if (!UncivGame.isCurrentInitialized()) return
        val game = UncivGame.Current

        // Get a hash covering all mods - quickly, so don't map, cast or copy the Set types
        val gameInfo = game.gameInfo
        @Suppress("IfThenToElvis")
        val hash1 = if (gameInfo != null) gameInfo.ruleset.mods.hashCode() else 0
        val newHash = hash1.xor(game.settings.visualMods.hashCode())

        // If hash the same, leave the cache as is
        if (modListHash != Int.MIN_VALUE && modListHash == newHash) return

        // Seems the mod list has changed - clear the cache
        clearCache()
        modListHash = newHash
        debug("Sound cache cleared")
        Preloader.restart()
    }

    /** Release cached Sound resources */
    // Called from UncivGame.dispose() to honor Gdx docs
    fun clearCache() {
        Preloader.abort()
        soundMap.clear()
        modListHash = Int.MIN_VALUE
    }

    /** Build list of folders to look for sounds */
    private fun getFolders(): Sequence<String> {
        if (!UncivGame.isCurrentInitialized())
            // Sounds before main menu shouldn't happen, but just in case return a default ""
            // which translates to the built-in assets/sounds folder
            return sequenceOf("")
        val game = UncivGame.Current

        // Allow mod sounds - preferentially so they can override built-in sounds
        // audiovisual mods after game mods but before built-in sounds
        // (these can already be available when game.gameInfo is not)
        val modList: MutableSet<String> = mutableSetOf()
        val gameInfo = game.gameInfo
        if (gameInfo != null) {
            modList.addAll(gameInfo.ruleset.mods)  // Sounds from game mods
        }
        modList.addAll(game.settings.visualMods)

        // Translate the basic mod list into relative folder names so only sounds/name.ext needs
        // to be added. Thus the empty string, added last, represents the builtin sounds folder.
        return modList.asSequence()
            .map { "mods$separator$it$separator" } +
            sequenceOf("")
    }

    /** Holds a Gdx Sound and a flag indicating the sound is freshly loaded and not from cache */
    data class GetSoundResult(val resource: Sound, val isFresh: Boolean)

    /** Retrieve (if not cached create from resources) a Gdx Sound from an UncivSound
     * @param sound The sound to fetch
     * @return `null` if file cannot be found, a [GetSoundResult] otherwise
     */
    fun get(sound: UncivSound): GetSoundResult? {
        checkCache()

        // Look for cached sound
        if (sound in soundMap)
            return if(soundMap[sound] == null) null
            else GetSoundResult(soundMap[sound]!!, false)

        // Not cached - try loading it
        return createAndCacheResult(sound, getFile(sound))
    }

    private fun getFile(sound: UncivSound): FileHandle? {
        val fileName = sound.fileName
        for (modFolder in getFolders()) {
            for (extension in SupportedExtensions.entries) {
                val path = "${modFolder}sounds$separator$fileName.${extension.name}"
                val localFile = UncivGame.Current.files.getLocalFile(path)
                if (localFile.exists()) return localFile
                val internalFile = Gdx.files.internal(path)
                if (internalFile.exists()) return internalFile
            }
        }
        return null
    }

    private fun createAndCacheResult(sound: UncivSound, file: FileHandle?): GetSoundResult? {
        if (file == null || !file.exists()) {
            debug("Sound %s not found!", sound.fileName)
            // remember that the actual file is missing
            soundMap[sound] = null
            return null
        }

        debug("Sound %s loaded from %s", sound.fileName, file.path())
        try {
            val newSound = Gdx.audio.newSound(file)
            // Store Sound for reuse
            soundMap[sound] = newSound
            return GetSoundResult(newSound, true)
        } catch (e: Exception) {
            debug("Failed to create a sound %s from %s: %s", sound.fileName, file.path(), e.message)
            // remember that the actual file is missing
            soundMap[sound] = null
            return null
        }
    }

    /** Play a sound once. Will not play if the sound is [UncivSound.Silent] or the volume is too low.
     * Sources are mods from a loaded game, then mods marked as permanent audiovisual,
     * and lastly Unciv's own assets/sounds. Will fail silently if the sound file cannot be found.
     *
     * This will wait for the Stream to become ready (Android issue) if necessary, and do so on a
     * separate thread. **No new thread is created** if the sound can be played immediately.
     *
     * That also means that it's the caller's responsibility to ensure calling this only on the GL thread.
     *
     * @param sound The sound to play
     * @see playRepeated
     *
     * @return True if the sound was processed correctly.
     */
    fun play(sound: UncivSound): Boolean {
        val volume = UncivGame.Current.settings.soundEffectsVolume
        if (sound == UncivSound.Silent || volume < 0.01) return true
        val (resource, isFresh) = get(sound) ?: return false
        if (Gdx.app.type == Application.ApplicationType.Android)
            return playAndroid(resource, isFresh, volume)
        else
            return playDesktop(resource, volume)
    }

    // Android needs time for a newly created sound to become ready, but in turn AndroidSound.play seems thread-safe.
    private fun playAndroid(resource: Sound, isFresh: Boolean, volume: Float): Boolean {
        /** Tested with a 2022 Android "S" and libGdx 1.12.1 - still required */
        // See also: https://github.com/libgdx/libgdx/issues/694
        // Typical logcat (effectively means we tried play before even the codec got loaded):
/*
        Unciv SoundPlayer       com.unciv.app                        D  [GLThread 8951] Sound click loaded from mods/Higher quality builtin sounds/sounds/click.ogg
        SoundPool               com.unciv.app                        W  play soundID 1 not READY
        MediaCodecList          com.unciv.app                        D  codecHandlesFormat: no format, so no extra checks
        MediaCodecList          com.unciv.app                        D  codecHandlesFormat: no format, so no extra checks
        CCodec                  com.unciv.app                        D  allocate(c2.android.vorbis.decoder)
        Codec2Client            com.unciv.app                        I  Available Codec2 services: "software"
        CCodec                  com.unciv.app                        I  Created component [c2.android.vorbis.decoder]
        CCodecConfig            com.unciv.app                        D  read media type: audio/vorbis
 */
        if (!isFresh && resource.play(volume) != -1L) return true // If it's already cached we should be able to play immediately
        return !Concurrency.run("DelayedSound") {
            delay(40L)
            var repeatCount = 0
            while (resource.play(volume) == -1L && ++repeatCount < 12) // quarter second tops
                delay(20L)
        }.isCancelled
    }

    // Let's do just one silent retry on Desktop. In turn, OpenALSound.play is not thread-safe.
    private fun playDesktop(resource: Sound, volume: Float): Boolean {
        if (resource.play(volume) != -1L) return true
        return !Concurrency.runOnGLThread("SoundRetry") {
            delay(20L)
            resource.play(volume)
        }.isCancelled
    }

    /** Play a sound repeatedly - e.g. to express that an action was applied multiple times or to multiple targets.
     *
     *  Runs the actual sound player decoupled on the GL thread unlike [SoundPlayer.play], which leaves that responsibility to the caller.
     */
    fun playRepeated(sound: UncivSound, count: Int = 2, delay: Long = 200) {
        Concurrency.runOnGLThread {
            play(sound)
            if (count > 1) Concurrency.run {
                repeat(count - 1) {
                    delay(delay)
                    Concurrency.runOnGLThread { play(sound) }
                }
            }
        }
    }

    /** Manages background loading of sound files into Gdx.Sound instances */
    private class Preloader {
        private fun UncivSound.Companion.getPreloadList() =
            listOf(Click, Whoosh, Construction, Promote, Upgrade, Coin, Chimes, Choir)

        private val job: Job = Concurrency.run("SoundPreloader") { preload() }.apply {
            invokeOnCompletion { preloader = null }
        }

        private suspend fun CoroutineScope.preload() {
            for (sound in UncivSound.getPreloadList()) {
                delay(10L)
                if (!isActive) break
                // This way skips minor things as compared to get(sound) - the cache stale check and result instantiation on cache hit
                if (sound in soundMap) continue
                debug("Preload $sound")
                createAndCacheResult(sound, getFile(sound))
                if (!isActive) break
            }
        }

        companion object {
            // This Companion is only used as a "namespace" thing and to keep Preloader control in one place

            fun abort() {
                preloader?.job?.cancel()
                preloader = null
            }

            fun restart() {
                preloader?.job?.cancel()
                preloader = Preloader()
            }
        }
    }

    /** A wrapper for a HashMap<UncivSound, Sound?> with synchronized writes */
    private class Cache {
        private val cache = HashMap<UncivSound, Sound?>(20)  // Enough for all standard sounds

        operator fun contains(key: UncivSound) = cache.containsKey(key)
        operator fun get(key: UncivSound) = cache[key]

        operator fun set(key: UncivSound, value: Sound?) {
            synchronized(cache) {
                cache[key] = value
            }
        }

        fun clear() {
            val oldSounds: List<Sound?>
            synchronized(cache) {
                oldSounds = cache.values.toList()
                cache.clear()
            }
            for (sound in oldSounds) sound?.dispose()
        }
    }
}
