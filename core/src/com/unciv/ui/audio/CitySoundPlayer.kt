package com.unciv.ui.audio

import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.files.FileHandle
import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo

class CitySoundPlayer: MusicController() {
    private lateinit var playingCitySound: Music

    private fun getFile(path: String) =
            if (Files.FileType.Local == Files.FileType.External && Gdx.files.isExternalStorageAvailable)
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
        try {
            val file: FileHandle = if (city.isWeLoveTheKingDayActive()) {
                FileHandle(getSoundFile(city.civInfo.getEra().citySound).toString())
            } else {
                FileHandle(getSoundFile(city.civInfo.getEra().citySound).toString())
            }
            playingCitySound = Gdx.audio.newMusic(file)
        } catch (ex: Throwable) {
            ex.printStackTrace()
        }

        try {
            playingCitySound.play()
        } catch (ex: Throwable) {
            ex.printStackTrace()
        }
    }

    fun stopCitySound() {
        try {
            playingCitySound.stop()
        } catch (ex: Throwable) {
            ex.printStackTrace()
        }
    }
}
