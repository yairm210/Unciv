package com.unciv.ui.popups.options

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.components.extensions.MusicControls.addCitySoundsVolumeSlider
import com.unciv.ui.components.extensions.MusicControls.addMusicControls
import com.unciv.ui.components.extensions.MusicControls.addSoundEffectsVolumeSlider
import com.unciv.ui.components.extensions.MusicControls.addVoicesVolumeSlider
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

fun soundTab(
    optionsPopup: OptionsPopup
): Table = Table(BaseScreen.skin).apply {
    pad(10f)
    defaults().pad(5f)

    val settings = optionsPopup.settings
    val music = UncivGame.Current.musicController

        addSoundEffectsVolumeSlider(settings)
        addCitySoundsVolumeSlider(settings)

        if (music.isVoicesAvailable())
            addVoicesVolumeSlider(settings)

        if (music.isMusicAvailable())
            addMusicControls(settings, music)

        if (!music.isDefaultFileAvailable())
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
            } catch (_: Exception) {
                launchOnGLThread {
                    errorTable.clear()
                    errorTable.add("Could not download music!".toLabel(Color.RED))
                }
            }
        }
    }
}
