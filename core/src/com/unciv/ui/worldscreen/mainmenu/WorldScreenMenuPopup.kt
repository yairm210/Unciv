package com.unciv.ui.worldscreen.mainmenu

import com.badlogic.gdx.Gdx
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.newgamescreen.NewGameScreen
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
            Gdx.net.openURI("https://github.com/yairm210/UnCiv")
            close()
        }.row()

        addButton("Reddit") {
            Gdx.net.openURI("https://www.reddit.com/r/Unciv/")
            close()
        }.row()

        addCloseButton()
    }
}
