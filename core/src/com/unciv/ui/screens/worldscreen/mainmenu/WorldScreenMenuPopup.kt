package com.unciv.ui.screens.worldscreen.mainmenu

import com.unciv.UncivGame
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.onLongPress
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.civilopediascreen.CivilopediaScreen
import com.unciv.ui.screens.savescreens.LoadGameScreen
import com.unciv.ui.screens.victoryscreen.VictoryScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class WorldScreenMenuPopup(val worldScreen: WorldScreen) : Popup(worldScreen, scrollable = Scrollability.All) {
    init {
        worldScreen.autoPlay.stopAutoPlay()
        defaults().fillX()

        addButton("Main menu") {
            worldScreen.game.goToMainMenu()
        }.row()
        addButton("Civilopedia", KeyboardBinding.Civilopedia) {
            close()
            worldScreen.game.pushScreen(CivilopediaScreen(worldScreen.gameInfo.ruleset))
        }.row()
        if (!worldScreen.gameInfo.gameParameters.isOnlineMultiplayer)
            addButton("Save game", KeyboardBinding.SaveGame) {
                close()
                worldScreen.openSaveGameScreen()
            }.row()
        addButton("Load game", KeyboardBinding.LoadGame) {
            close()
            worldScreen.game.pushScreen(LoadGameScreen())
        }.row()
        addButton("Start new game", KeyboardBinding.NewGame) {
            close()
            worldScreen.openNewGameScreen()
        }.row()
        addButton("Victory status", KeyboardBinding.VictoryScreen) {
            close()
            worldScreen.game.pushScreen(VictoryScreen(worldScreen))
        }.row()
        val optionsCell = addButton("Options", KeyboardBinding.Options) {
            close()
            worldScreen.openOptionsPopup()
        }
        optionsCell.actor.onLongPress {
            close()
            worldScreen.openOptionsPopup(withDebug = true)
        }
        optionsCell.row()
        addButton("Community") {
            close()
            WorldScreenCommunityPopup(worldScreen).open(force = true)
        }.row()
        addButton("Music", KeyboardBinding.MusicPlayer) {
            close()
            WorldScreenMusicPopup(worldScreen).open(force = true)
        }.row()

        addCloseButton()
        pack()
    }
}
