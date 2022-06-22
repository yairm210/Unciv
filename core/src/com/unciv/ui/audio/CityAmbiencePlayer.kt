package com.unciv.ui.audio

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.utils.Disposable
import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.utils.Log

/** Must be [disposed][dispose]. Starts playing an ambience sound for the city when created. Stops playing the ambience sound when [disposed][dispose]. */
class CityAmbiencePlayer(
    city: CityInfo
) : Disposable {
    private var playingCitySound: Music? = null

    init {
        play(city)
    }

    private fun getFile(path: String): FileHandle {
        val internal = Gdx.files.internal(path)
        if (internal.exists()) return internal
        return Gdx.files.local(path)
    }

    private fun getSoundFolders() = sequence {
        val visualMods = UncivGame.Current.settings.visualMods
        val mods = UncivGame.Current.gameInfo!!.gameParameters.getModsAndBaseRuleset()
        val modSoundFolders = (visualMods + mods).asSequence()
            .map { modName ->
                getFile("mods")
                    .child(modName)
                    .child("sounds")
            }

        yieldAll(modSoundFolders)
        yield(getFile("sounds"))
    }

    private fun getSoundFile(fileName: String): FileHandle? {
        return getSoundFolders()
            .filter { it.exists() && it.isDirectory }
            .flatMap { it.list().asSequence() }
            // ensure only normal files with common sound extension
            .filter { !it.isDirectory && it.extension() in MusicController.gdxSupportedFileExtensions }
            .firstOrNull { it.nameWithoutExtension() == fileName }
    }

    private fun play(city: CityInfo) {
        if (UncivGame.Current.settings.citySoundsVolume == 0f) return

        if (playingCitySound != null)
            stop()
        try {
            playingCitySound = Gdx.audio.newMusic(getSoundFile(city.civInfo.getEra().citySound))
            playingCitySound?.volume = UncivGame.Current.settings.citySoundsVolume
            playingCitySound?.isLooping = true
            playingCitySound?.play()
        } catch (ex: Throwable) {
            playingCitySound?.dispose()
            Log.error("Error while playing city sound: ", ex)
        }
    }

    private fun stop() {
        playingCitySound?.dispose()
    }

    override fun dispose() {
        stop()
    }
}
