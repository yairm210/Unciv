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
    private val soundMap = HashMap<UncivSound, Sound?>()
    
    private val separator = File.separator      // just a shorthand for readability
    
    private var modListHash = Int.MIN_VALUE
    /** Ensure cache is not outdated _and_ build list of folders to look for sounds */
    private fun getFolders(): Sequence<String> {
        if (!UncivGame.isCurrentInitialized())  // Sounds before main menu shouldn't happen
            return sequenceOf("")
        val game = UncivGame.Current
        // Allow mod sounds - preferentially so they can override built-in sounds
        val modList: MutableSet<String> = game.settings.visualMods
        if (game.isGameInfoInitialized())  // Allow sounds from main menu
            modList.addAll(game.gameInfo.ruleSet.mods)
        val newHash = modList.hashCode()
        if (modListHash == Int.MIN_VALUE || modListHash != newHash) {
            // Seems the mod list has changed - start over
            for (sound in soundMap.values) sound?.dispose()
            soundMap.clear()
            modListHash = newHash
        }
        // Should we also look in UncivGame.Current.settings.visualMods?
        return modList.asSequence()
            .map { "mods$separator$it$separator" } +
            sequenceOf("")
    } 

    fun get(sound: UncivSound): Sound? {
        if (sound in soundMap) return soundMap[sound]
        val fileName = sound.value
        var file: FileHandle? = null
        for (modFolder in getFolders()) {
            val path = "${modFolder}sounds$separator$fileName.mp3"
            file = Gdx.files.internal(path)
            if (file.exists()) break
        }
        val newSound =
            if (file == null || !file.exists()) null
            else Gdx.audio.newSound(file)
        if (newSound == null)
            println("Sound ${sound.value} not found.")
        else
            println("Sound ${sound.value} loaded from ${file!!.path()}.")
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