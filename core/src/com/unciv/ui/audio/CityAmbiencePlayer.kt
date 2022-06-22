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

    private fun getModsFolder(): FileHandle {
        val path = "mods"
        val internal = Gdx.files.internal(path)
        if (internal.exists()) return internal
        return Gdx.files.local(path)
    }

    private fun getModSoundFolders(): Sequence<FileHandle> {
        val visualMods = UncivGame.Current.settings.visualMods
        val mods = UncivGame.Current.gameInfo!!.gameParameters.getModsAndBaseRuleset()
        return (visualMods + mods).asSequence()
            .map { modName ->
                getModsFolder()
                    .child(modName)
                    .child("sounds")
            }
    }

    private fun getSoundFile(fileName: String): FileHandle {
        val fileFromMods = getModSoundFolders()
            .filter { it.isDirectory }
            .flatMap { it.list().asSequence() }
            .filter { !it.isDirectory && it.extension() in MusicController.gdxSupportedFileExtensions }
            .firstOrNull { it.nameWithoutExtension() == fileName }

        return fileFromMods ?: Gdx.files.internal("sounds/$fileName.ogg")
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
