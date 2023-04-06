package com.unciv.ui.screens.worldscreen.mainmenu

import com.badlogic.gdx.Gdx
import com.unciv.UncivGame
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen
import com.unciv.ui.screens.newgamescreen.NewGameScreen
import com.unciv.ui.popups.options.addMusicControls
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.savescreens.LoadGameScreen
import com.unciv.ui.screens.savescreens.SaveGameScreen
import com.unciv.ui.screens.victoryscreen.VictoryScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class WorldScreenMenuPopup(val worldScreen: WorldScreen) : Popup(worldScreen) {
    init {
        defaults().fillX()

        addButton("Main menu") {
            worldScreen.game.goToMainMenu()
        }.row()
        addButton("Civilopedia") {
            close()
            worldScreen.game.pushScreen(CivilopediaScreen(worldScreen.gameInfo.ruleset))
        }.row()
        addButton("Save game") {
            close()
            worldScreen.game.pushScreen(SaveGameScreen(worldScreen.gameInfo))
        }.row()
        addButton("Load game") {
            close()
            worldScreen.game.pushScreen(LoadGameScreen())
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
        addMusicControls(this, settings, musicController)
        addCloseButton().colspan(2)
    }
}
