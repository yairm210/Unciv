package com.unciv.ui.audio

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.models.UncivSound
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.debug
import kotlinx.coroutines.delay
import java.io.File

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

    private val soundMap = HashMap<UncivSound, Sound?>()

    private val separator = File.separator      // just a shorthand for readability

    private var modListHash = Int.MIN_VALUE

    /** Ensure cache is not outdated */
    private fun checkCache() {
        if (!UncivGame.isCurrentInitialized()) return
        val game = UncivGame.Current

        // Get a hash covering all mods - quickly, so don't map, cast or copy the Set types
        val gameInfo = game.gameInfo
        val hash1 = if (gameInfo != null) gameInfo.ruleSet.mods.hashCode() else 0
        val newHash = hash1.xor(game.settings.visualMods.hashCode())

        // If hash the same, leave the cache as is
        if (modListHash != Int.MIN_VALUE && modListHash == newHash) return

        // Seems the mod list has changed - clear the cache
        clearCache()
        modListHash = newHash
        debug("Sound cache cleared")
    }

    /** Release cached Sound resources */
    // Called from UncivGame.dispose() to honor Gdx docs
    fun clearCache() {
        for (sound in soundMap.values) sound?.dispose()
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
            modList.addAll(gameInfo.ruleSet.mods)  // Sounds from game mods
        }
        modList.addAll(game.settings.visualMods)

        // Translate the basic mod list into relative folder names so only sounds/name.ext needs
        // to be added. Thus the empty string, added last, represents the builtin sounds folder.
        return modList.asSequence()
            .map { "mods$separator$it$separator" } +
            sequenceOf("")
    }

    /** Holds a Gdx Sound and a flag indicating the sound is freshly loaded and not from cache */
    private data class GetSoundResult(val resource: Sound, val isFresh: Boolean)

    /** Retrieve (if not cached create from resources) a Gdx Sound from an UncivSound
     * @param sound The sound to fetch
     * @return `null` if file cannot be found, a [GetSoundResult] otherwise
     */
    private fun get(sound: UncivSound): GetSoundResult? {
        checkCache()
        // Look for cached sound
        if (sound in soundMap)
            return if(soundMap[sound] == null) null
            else GetSoundResult(soundMap[sound]!!, false)

        // Not cached - try loading it
        val fileName = sound.fileName
        var file: FileHandle? = null
        for ( (modFolder, extension) in getFolders().flatMap {
            // This is essentially a cross join. To operate on all combinations, we pack both lambda
            // parameters into a Pair (using `to`) and unwrap that in the loop using automatic data
            // class deconstruction `(,)`. All this avoids a double break when a match is found.
            folder -> SupportedExtensions.values().asSequence().map { folder to it }
        } ) {
            val path = "${modFolder}sounds$separator$fileName.${extension.name}"
            file = Gdx.files.local(path)
            if (file.exists()) break
            file = Gdx.files.internal(path)
            if (file.exists()) break
        }

        @Suppress("LiftReturnOrAssignment")
        if (file == null || !file.exists()) {
            debug("Sound %s not found!", sound.fileName)
            // remember that the actual file is missing
            soundMap[sound] = null
            return null
        } else {
            debug("Sound %s loaded from %s", sound.fileName, file.path())
            val newSound = Gdx.audio.newSound(file)
            // Store Sound for reuse
            soundMap[sound] = newSound
            return GetSoundResult(newSound, true)
        }
    }

    /**
     * Find, cache and play a Sound.
     *
     * **Attention:** The [GameSounds] object has been set up to control playing all sounds of the game. Chances are that you shouldn't be calling this method
     * from anywhere but [GameSounds].
     *
     * Sources are mods from a loaded game, then mods marked as permanent audiovisual,
     * and lastly Unciv's own assets/sounds. Will fail silently if the sound file cannot be found.
     *
     * This will wait for the Stream to become ready (Android issue) if necessary, and do so on a
     * separate thread. No new thread is created if the sound can be played immediately.
     *
     * @param sound The sound to play
     */
    fun play(sound: UncivSound) {
        val volume = UncivGame.Current.settings.soundEffectsVolume
        if (sound == UncivSound.Silent || volume < 0.01) return
        val (resource, isFresh) = get(sound) ?: return
        val initialDelay = if (isFresh && Gdx.app.type == Application.ApplicationType.Android) 40 else 0

        if (initialDelay > 0 || resource.play(volume) == -1L) {
            Concurrency.run("DelayedSound") {
                delay(initialDelay.toLong())
                while (resource.play(volume) == -1L) {
                    delay(20L)
                }
            }
        }
    }
}
