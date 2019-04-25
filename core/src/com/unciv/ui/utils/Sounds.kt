package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Sound
import com.unciv.UnCivGame

object Sounds{
    val soundMap = HashMap<String, Sound>()

    fun get(name:String):Sound{
        if(!soundMap.containsKey(name))
            soundMap[name] = Gdx.audio.newSound(Gdx.files.internal("sounds/$name.mp3"))
        return soundMap[name]!!
    }


    fun play(name:String){
        get(name).play(UnCivGame.Current.settings.soundEffectsVolume)
    }
}