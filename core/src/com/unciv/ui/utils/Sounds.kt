package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.models.UncivSound

object Sounds {
    private val soundMap = HashMap<String, Sound?>()

    fun get(sound: UncivSound): Sound? {
        val fileName = if (sound == UncivSound.Custom) sound.custom!! else sound.value
        if (!soundMap.containsKey(fileName)) {
            val mods = mutableSetOf<String>()
            if (UncivGame.Current.isInitialized) {
                mods.addAll(UncivGame.Current.settings.visualMods)
                mods.addAll(UncivGame.Current.gameInfo.ruleSet.mods)
            }
            mods.add("")
            var file: FileHandle? = null
            for (mod in mods) {
                val path = if(mod.isEmpty()) "sounds/$fileName.mp3"
                    else "mods/$mod/sounds/$fileName.mp3"
                file = Gdx.files.internal(path)
                if (file.exists()) break
            }
            if (file != null && file.exists())
                soundMap[fileName] = Gdx.audio.newSound(file)
            else
                soundMap[fileName] = null  // remember file is missing
        }
        return soundMap[fileName]
    }

    fun play(sound: UncivSound) {
        val volume = UncivGame.Current.settings.soundEffectsVolume
        if (sound == UncivSound.Silent || volume < 0.01) return
        get(sound)?.play(volume)
    }
}