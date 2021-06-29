package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.models.UncivSound
import java.io.File

/**
 * Generates Gdx [Sound] objects from [UncivSound] ones on demand, only once per key
 * (two UncivSound custom instances with the same filename are considered equal).
 * 
 * Gdx asks Sound usage to respect the Disposable contract, but since we're only caching
 * a handful of them in memory we should be able to get away with keeping them alive for the
 * app lifetime.
 */
object Sounds {
    private enum class SupportedExtensions { mp3, ogg, wav }    // Gdx won't do aac/m4a
    
    private val soundMap = HashMap<UncivSound, Sound?>()
    
    private val separator = File.separator      // just a shorthand for readability
    
    private var modListHash = Int.MIN_VALUE

    /** Ensure cache is not outdated */
    private fun checkCache() {
        if (!UncivGame.isCurrentInitialized()) return
        val game = UncivGame.Current

        // Get a hash covering all mods - quickly, so don't map cast or copy the Set types
        val hash1 = if (game.isGameInfoInitialized()) game.gameInfo.ruleSet.mods.hashCode() else 0
        val newHash = hash1.xor(game.settings.visualMods.hashCode())
        // If hash the same, leave the cache as is
        if (modListHash != Int.MIN_VALUE && modListHash == newHash) return

        // Seems the mod list has changed - clear the cache
        for (sound in soundMap.values) sound?.dispose()
        soundMap.clear()
        modListHash = newHash
    }

    /** Build list of folders to look for sounds */
    private fun getFolders(): Sequence<String> {
        if (!UncivGame.isCurrentInitialized())
            // Sounds before main menu shouldn't happen, but just in case return a default ""
            // which translates to the built-in assets/sounds folder
            return sequenceOf("")
        val game = UncivGame.Current

        // Allow mod sounds - preferentially so they can override built-in sounds
        // audiovisual mods first, these are already available when game.gameInfo is not 
        val modList: MutableSet<String> = game.settings.visualMods
        if (game.isGameInfoInitialized())
            modList.addAll(game.gameInfo.ruleSet.mods)  // Sounds from game mods

        return modList.asSequence()
            .map { "mods$separator$it$separator" } +
            sequenceOf("")  // represents builtin sounds folder
    } 

    fun get(sound: UncivSound): Sound? {
        checkCache()
        if (sound in soundMap) return soundMap[sound]
        val fileName = sound.value
        var file: FileHandle? = null
        for ( (modFolder, extension) in getFolders().flatMap { 
            folder -> SupportedExtensions.values().asSequence().map { folder to it } 
        } ) {
            val path = "${modFolder}sounds$separator$fileName.${extension.name}"
            file = Gdx.files.internal(path)
            if (file.exists()) break
        }
        val newSound =
            if (file == null || !file.exists()) null
            else Gdx.audio.newSound(file)

        // Store Sound for reuse or remember that the actual file is missing
        soundMap[sound] = newSound
        return newSound
    }

    fun play(sound: UncivSound) {
        val volume = UncivGame.Current.settings.soundEffectsVolume
        if (sound == UncivSound.Silent || volume < 0.01) return
        get(sound)?.play(volume)
    }
}