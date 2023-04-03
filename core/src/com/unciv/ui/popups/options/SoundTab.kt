package com.unciv.ui.popups.options

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.models.UncivSound
import com.unciv.models.metadata.GameSettings
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicController
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.UncivSlider
import com.unciv.ui.components.WrappableLabel
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.extensions.toImageButton
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
import kotlin.math.floor

fun soundTab(
    optionsPopup: OptionsPopup
): Table = Table(BaseScreen.skin).apply {
    pad(10f)
    defaults().pad(5f)

    val settings = optionsPopup.settings
    val music = UncivGame.Current.musicController

    addSoundEffectsVolumeSlider(this, settings)
    addCitySoundsVolumeSlider(this, settings)

    if (UncivGame.Current.musicController.isMusicAvailable()) {
        addMusicControls(this, settings, music)
    }

    if (!UncivGame.Current.musicController.isDefaultFileAvailable())
        addDownloadMusic(this, optionsPopup)
}

private fun addDownloadMusic(table: Table, optionsPopup: OptionsPopup) {
    val downloadMusicButton = "Download music".toTextButton()
    table.add(downloadMusicButton).colspan(2).row()
    val errorTable = Table()
    table.add(errorTable).colspan(2).row()

    downloadMusicButton.onClick {
        downloadMusicButton.disable()
        errorTable.clear()
        errorTable.add("Downloading...".toLabel())

        // So the whole game doesn't get stuck while downloading the file
        Concurrency.run("MusicDownload") {
            try {
                UncivGame.Current.musicController.downloadDefaultFile()
                launchOnGLThread {
                    optionsPopup.tabs.replacePage("Sound", soundTab(optionsPopup))
                    UncivGame.Current.musicController.chooseTrack(flags = MusicTrackChooserFlags.setPlayDefault)
                }
            } catch (ex: Exception) {
                launchOnGLThread {
                    errorTable.clear()
                    errorTable.add("Could not download music!".toLabel(Color.RED))
                }
            }
        }
    }
}

private fun addSoundEffectsVolumeSlider(table: Table, settings: GameSettings) {
    table.add("Sound effects volume".tr()).left().fillX()

    val soundEffectsVolumeSlider = UncivSlider(
        0f, 1.0f, 0.05f,
        initial = settings.soundEffectsVolume,
        getTipText = UncivSlider::formatPercent
    ) {
        settings.soundEffectsVolume = it
    }
    table.add(soundEffectsVolumeSlider).pad(5f).row()
}

private fun addCitySoundsVolumeSlider(table: Table, settings: GameSettings) {
    table.add("City ambient sound volume".tr()).left().fillX()

    val citySoundVolumeSlider = UncivSlider(
        0f, 1.0f, 0.05f,
        initial = settings.citySoundsVolume,
        getTipText = UncivSlider::formatPercent
    ) {
        settings.citySoundsVolume = it
    }
    table.add(citySoundVolumeSlider).pad(5f).row()
}

private fun addMusicVolumeSlider(table: Table, settings: GameSettings, music: MusicController) {
    table.add("Music volume".tr()).left().fillX()

    val musicVolumeSlider = UncivSlider(
        0f, 1.0f, 0.05f,
        initial = settings.musicVolume,
        sound = UncivSound.Silent,
        getTipText = UncivSlider::formatPercent
    ) {
        settings.musicVolume = it

        music.setVolume(it)
        if (!music.isPlaying())
            music.chooseTrack(flags = MusicTrackChooserFlags.setPlayDefault)
    }
    table.add(musicVolumeSlider).pad(5f).row()
}

private fun addMusicPauseSlider(table: Table, settings: GameSettings, music: MusicController) {
    // map to/from 0-1-2..10-12-14..30-35-40..60-75-90-105-120
    fun posToLength(pos: Float): Float = when (pos) {
        in 0f..10f -> pos
        in 11f..20f -> pos * 2f - 10f
        in 21f..26f -> pos * 5f - 70f
        else -> pos * 15f - 330f
    }

    fun lengthToPos(length: Float): Float = floor(
        when (length) {
            in 0f..10f -> length
            in 11f..30f -> (length + 10f) / 2f
            in 31f..60f -> (length + 10f) / 5f
            else -> (length + 330f) / 15f
        }
    )

    val getTipText: (Float) -> String = {
        "%.0f".format(posToLength(it))
    }

    table.add("Pause between tracks".tr()).left().fillX()

    val pauseLengthSlider = UncivSlider(
        0f, 30f, 1f,
        initial = lengthToPos(music.silenceLength),
        sound = UncivSound.Silent,
        getTipText = getTipText
    ) {
        music.silenceLength = posToLength(it)
        settings.pauseBetweenTracks = music.silenceLength.toInt()
    }
    table.add(pauseLengthSlider).pad(5f).row()
}

private fun addMusicCurrentlyPlaying(table: Table, music: MusicController) {
    val label = WrappableLabel("", table.width - 10f, Color(-0x2f5001), 16)
    label.wrap = true
    table.add(label).padTop(20f).colspan(2).fillX().row()
    music.onChange {
        Concurrency.runOnGLThread {
            label.setText("Currently playing: [$it]".tr())
        }
    }
}

private fun addSimplePlayerControls(table: Table, music: MusicController) {
    fun String.toImageButton(overColor: Color) = toImageButton(30f, 30f, Color.CLEAR, overColor)
    table.add(Table().apply {
        defaults().space(25f)
        add("OtherIcons/Pause".toImageButton(Color.GOLD).onClick { music.pause(0.5f) })
        add("OtherIcons/ForwardArrow".toImageButton(Color.LIME).onClick { music.resume(0.5f) })
        add("OtherIcons/Loading".toImageButton(Color.VIOLET).onClick { music.chooseTrack(flags = MusicTrackChooserFlags.none) })
    }).colspan(2).center().row()
}

/** Adds music volume/pause sliders, currently playing label and player controls to a [table] */
// public - used here and in WorldScreenMenuPopup
fun addMusicControls(table: Table, settings: GameSettings, music: MusicController) {
    addMusicVolumeSlider(table, settings, music)
    addMusicPauseSlider(table, settings, music)
    addMusicCurrentlyPlaying(table, music)
    addSimplePlayerControls(table, music)
}
