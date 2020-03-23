package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.unciv.UncivGame
import com.unciv.models.UncivSound

object Sounds {
    private val soundMap = HashMap<UncivSound, Sound>()

    fun get(sound: UncivSound): Sound {
        if (!soundMap.containsKey(sound)) {
            soundMap[sound] = Gdx.audio.newSound(Gdx.files.internal("sounds/${sound.value}.mp3"))
        }
        return soundMap[sound]!!
    }

    fun play(sound: UncivSound) {
        val volume = UncivGame.Current.settings.soundEffectsVolume
        if (sound == UncivSound.Silent || volume < 0.01) return
        get(sound).play(volume)
    }
}