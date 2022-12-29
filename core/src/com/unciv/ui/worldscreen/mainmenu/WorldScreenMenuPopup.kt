package com.unciv.ui.worldscreen.mainmenu

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.newgamescreen.NewGameScreen
import com.unciv.ui.options.addMusicCurrentlyPlaying
import com.unciv.ui.options.addMusicPauseSlider
import com.unciv.ui.options.addMusicVolumeSlider
import com.unciv.ui.popup.Popup
import com.unciv.ui.saves.LoadGameScreen
import com.unciv.ui.saves.SaveGameScreen
import com.unciv.ui.victoryscreen.VictoryScreen
import com.unciv.ui.worldscreen.WorldScreen

class WorldScreenMenuPopup(val worldScreen: WorldScreen) : Popup(worldScreen) {
    init {
        defaults().fillX()

        addButton("Main menu") {
            worldScreen.game.goToMainMenu()
        }.row()
        addButton("Civilopedia") {
            close()
            worldScreen.game.pushScreen(CivilopediaScreen(worldScreen.gameInfo.ruleSet))
        }.row()
        addButton("Save game") {
            close()
            worldScreen.game.pushScreen(SaveGameScreen(worldScreen.gameInfo))
        }.row()
        addButton("Load game") {
            close()
            worldScreen.game.pushScreen(LoadGameScreen(worldScreen))
        }.row()

        addButton("Start new game") {
            close()
            val newGameSetupInfo = GameSetupInfo(worldScreen.gameInfo)
            newGameSetupInfo.mapParameters.reseed()
            val newGameScreen = NewGameScreen(newGameSetupInfo)
            worldScreen.game.pushScreen(newGameScreen)
        }.row()

        addButton("Victory status") {
            close()
            worldScreen.game.pushScreen(VictoryScreen(worldScreen))
        }.row()
        addButton("Options") {
            close()
            worldScreen.openOptionsPopup()
        }.row()
        addButton("Community") {
            close()
            WorldScreenCommunityPopup(worldScreen).open(force = true)
        }.row()
        addButton("Music") {
            close()
            WorldScreenMusicButton(worldScreen).open(force = true)
        }.row()
        addCloseButton()
        pack()
    }
}

class WorldScreenCommunityPopup(val worldScreen: WorldScreen) : Popup(worldScreen) {
    init {
        defaults().fillX()
        addButton("Discord") {
            Gdx.net.openURI("https://discord.gg/bjrB4Xw")
            close()
        }.row()

        addButton("Github") {
            Gdx.net.openURI("https://github.com/yairm210/Unciv")
            close()
        }.row()

        addButton("Reddit") {
            Gdx.net.openURI("https://www.reddit.com/r/Unciv/")
            close()
        }.row()

        addCloseButton()
    }
}

class WorldScreenMusicButton(val worldScreen: WorldScreen) : Popup(worldScreen) {
    init {
        val musicController = UncivGame.Current.musicController
        val settings = UncivGame.Current.settings

        defaults().fillX()
        addMusicVolumeSlider(this, settings, musicController)
        row()
        addMusicPauseSlider(this , settings, musicController)
        row()
        addMusicCurrentlyPlaying(this, musicController)
        row()
        addButton("Pause", action = { musicController.pause(0.5f) })
        addButton("Resume", action = { musicController.resume(0.5f) })
        addButton("Skip", action = { musicController.chooseTrack(flags = MusicTrackChooserFlags.none) }).row()

        addCloseButton()
    }
}
