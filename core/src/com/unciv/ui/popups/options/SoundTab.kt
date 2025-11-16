package com.unciv.ui.popups.options

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.components.extensions.MusicControls
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onClick
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread

internal class SoundTab(
    optionsPopup: OptionsPopup
) : OptionsPopupTab(optionsPopup), MusicControls {
    override fun lateInitialize() {
        val music = game.musicController

        addSoundEffectsVolumeSlider(settings)
        addCitySoundsVolumeSlider(settings)

        if (music.isVoicesAvailable())
            addVoicesVolumeSlider(settings)

        if (music.isMusicAvailable())
            addMusicControls(settings, music)

        if (!music.isDefaultFileAvailable())
            addDownloadMusic()

        super.lateInitialize()
    }

    private fun addDownloadMusic() {
        val downloadMusicButton = "Download music".toTextButton()
        add(downloadMusicButton).colspan(2).row()
        val errorTable = Table()
        add(errorTable).colspan(2).row()

        downloadMusicButton.onClick {
            downloadMusicButton.disable()
            errorTable.clear()
            errorTable.add("Downloading...".toLabel())

            // So the whole game doesn't get stuck while downloading the file
            Concurrency.run("MusicDownload") {
                try {
                    game.musicController.downloadDefaultFile()
                    launchOnGLThread {
                        replacePage { optionsPopup -> SoundTab(optionsPopup) }
                        game.musicController.chooseTrack(flags = MusicTrackChooserFlags.setPlayDefault)
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
}
