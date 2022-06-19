package com.unciv.ui.audio

import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.models.UncivSound
import com.unciv.utils.Log

class CitySoundPlayer {
    private val soundsLocation = Files.FileType.Local
    private var playingCitySound: Music? = null
    val fileExtensions = listOf("mp3", "ogg", "wav")   // All Gdx formats

    private fun getFile(path: String) =
            if (soundsLocation == Files.FileType.External && Gdx.files.isExternalStorageAvailable)
                Gdx.files.external(path)
            else Gdx.files.local(path)

    private fun getSoundFolders() = sequence {
        val visualMods = UncivGame.Current.settings.visualMods
        val mods = UncivGame.Current.gameInfo!!.gameParameters.getModsAndBaseRuleset()
        yieldAll(
            (visualMods + mods).asSequence()
                .map { getFile("mods")
                    .child(it).child("sounds") }
        )
        yield(getFile("sounds"))
    }

    private fun getSoundFile(fileName: String): FileHandle? = getSoundFolders()
        .filter { it.exists() && it.isDirectory }
        .flatMap { it.list().asSequence() }
        // ensure only normal files with common sound extension
        .filter { it.exists() && !it.isDirectory && it.extension() in fileExtensions }
        .firstOrNull { it.name().contains(fileName) }

    fun playCitySound(city: CityInfo) {
        if (playingCitySound != null)
            stopCitySound()
        try {
            val file = FileHandle(getSoundFile(city.civInfo.getEra().citySound).toString())
            playingCitySound = Gdx.audio.newMusic(file)
            playingCitySound?.volume = UncivGame.Current.settings.citySoundsVolume
            playingCitySound?.isLooping = true
            playingCitySound?.play()
        } catch (ex: Throwable) {
            playingCitySound?.dispose()
            Log.error("Error while playing city sound: ", ex)
        }
    }

    fun stopCitySound() {
        try {
            if (playingCitySound?.isPlaying == true)
                playingCitySound?.stop()
        } catch (ex: Throwable) {
            playingCitySound?.dispose()
            Log.error("Error while stopping city sound: ", ex)
        }
    }
}
