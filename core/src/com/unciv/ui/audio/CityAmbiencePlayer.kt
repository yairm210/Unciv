package com.unciv.ui.audio

import com.badlogic.gdx.utils.Disposable
import com.unciv.UncivGame
import com.unciv.logic.city.City

/** Must be [disposed][dispose].
 *  Starts playing an ambience sound for the city when created.
 *  Stops playing the ambience sound when [disposed][dispose]. */
class CityAmbiencePlayer(
    city: City
) : Disposable {
    init {
        val volume = UncivGame.Current.settings.citySoundsVolume
        if (volume > 0f) {
            UncivGame.Current.musicController
                .playOverlay(city.civ.getEra().citySound, volume = volume, isLooping = true, fadeIn = true)
        }
    }

    override fun dispose() {
        UncivGame.Current.musicController.stopOverlay()
    }
}
