package com.unciv.ui.audio

import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.logic.files.IMediaFinder
import com.unciv.models.UncivSound
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import games.rednblack.miniaudio.MASound
import games.rednblack.miniaudio.MiniAudioException
import games.rednblack.miniaudio.effect.MADelayNode
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

/* TODO
    - Use MASoundPool instead of MASound for concurrency? (that's a pool for one single file)
 */

/**
 * Generates MiniAudio [MASound] objects from [UncivSound] ones on demand, only once per key
 * (two UncivSound custom instances with the same filename are considered equal).
 *
 * Gdx asks Sound usage to respect the Disposable contract, but since we're only caching
 * a handful of them in memory we should be able to get away with keeping them alive for the
 * app lifetime - and we do dispose them when the app is disposed.
 */
object SoundPlayer {
    private const val maxEchoes = 5

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
        Log.debug("Sound cache cleared")
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
    @Readonly
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
    data class GetSoundResult(val resource: MASound, val isFresh: Boolean)

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
            for (extension in IMediaFinder.SupportedAudioExtensions.names) {
                val path = "${modFolder}sounds$separator$fileName.$extension"
                val localFile = UncivGame.Current.files.getLocalFile(path)
                if (localFile.exists()) return localFile
                val internalFile = Gdx.files.internal(path)
                if (internalFile.exists()) return internalFile
            }
        }
        return null
    }

    private fun createAndCacheResult(sound: UncivSound, file: FileHandle?): GetSoundResult? {
        if (!UncivGame.isCurrentInitialized()) return null

        fun logAndMarkFailure(msg: String): GetSoundResult? {
            Log.debug(msg)
            // remember that the actual file is missing
            soundMap[sound] = null
            return null
        }
        fun logAndMarkFailure(e: Exception) =
            logAndMarkFailure("Failed to create a sound ${sound.fileName} from ${file!!.path()}: ${e.javaClass.simpleName} ${e.message}")

        fun storeAndReturn(newSound: MASound): GetSoundResult {
            Log.debug("Sound %s loaded from %s", sound.fileName, file!!.path())
            // Store Sound for reuse
            soundMap[sound] = newSound
            return GetSoundResult(newSound, true)
        }

        if (file == null || !file.exists())
            return logAndMarkFailure("Sound ${sound.fileName} not found!")

        val ma = UncivGame.Current.miniAudio
        try {
            // Use Flags.MA_SOUND_FLAG_DECODE: keep decoded in memory?
            return storeAndReturn(ma.createSound(file.path()))
        } catch (e: Exception) {
            // TODO follow https://github.com/rednblackgames/gdx-miniaudio/issues/2 - this may become obsolete?
            if (e is MiniAudioException && file.type() == Files.FileType.Internal && IMediaFinder.isRunFromJar()) {
                try {
                    val bytes = file.readBytes()
                    val newSound = ma.createSound(ma.decodeBytes(bytes, 2))
                    return storeAndReturn(newSound)
                } catch (e: Exception) {
                    return logAndMarkFailure(e)
                }
            }
            return logAndMarkFailure(e)
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
     */
    fun play(sound: UncivSound) {
        val volume = UncivGame.Current.settings.soundEffectsVolume
        if (sound == UncivSound.Silent || volume < 0.01) return
        val (resource, _) = get(sound) ?: return
        resource.setVolume(volume)
        resource.seekTo(0f)
        resource.play()
    }

    /** Play a sound repeatedly - e.g. to express that an action was applied multiple times or to multiple targets.
     *
     *  Runs the actual sound player decoupled on the GL thread unlike [SoundPlayer.play], which leaves that responsibility to the caller.
     */
    fun playRepeated(sound: UncivSound, count: Int = 2, delay: Long = 200) {
        if (count <= 1) return play(sound)
        val volume = UncivGame.Current.settings.soundEffectsVolume
        if (sound == UncivSound.Silent || volume < 0.01) return
        val (template, _) = get(sound) ?: return
        // MiniAudio doesn't rely on GL, so this is just a little postponing
        Concurrency.runOnGLThread {
            val ma = UncivGame.Current.miniAudio
            // Can't modify that instance, or else all normal plays will have the echo
            val resource = ma.createSound(template.dataSource)
            val echo = MADelayNode(ma, delay * 0.001f, 0.9f)
            ma.attachToEngineOutput(echo, 0)
            echo.attachToThisNode(resource, 0)
            echo.setOutputBusVolume(0, volume)
            //TODO won't stop echoing...
            val stopAt = (resource.length * 1000).toLong() + (count.coerceAtMost(maxEchoes) - 1) * delay
            Concurrency.run {
                delay(stopAt)
                resource.fadeOut(delay * 0.5f)
                delay(delay)
                resource.stop()
                resource.dispose()
                resource.chainSound(resource)
            }
            resource.seekTo(0f)
            resource.play()
        }
    }

    /** Manages background loading of sound files into Gdx.Sound instances */
    private class Preloader {
        @Pure
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
                Log.debug("Preload $sound")
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
        private val cache = HashMap<UncivSound, MASound?>(20)  // Enough for all standard sounds

        @Readonly
        operator fun contains(key: UncivSound) = cache.containsKey(key)
        @Readonly
        operator fun get(key: UncivSound) = cache[key]

        operator fun set(key: UncivSound, value: MASound?) {
            synchronized(cache) {
                cache[key] = value
            }
        }

        fun clear() {
            val oldSounds: List<MASound?>
            synchronized(cache) {
                oldSounds = cache.values.toList()
                cache.clear()
            }
            for (sound in oldSounds) sound?.dispose()
        }
    }
}
