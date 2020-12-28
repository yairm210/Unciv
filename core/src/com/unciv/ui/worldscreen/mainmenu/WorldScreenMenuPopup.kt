package com.unciv.ui.worldscreen.mainmenu

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.MainMenuScreen
import com.unciv.ui.CivilopediaScreen
import com.unciv.ui.newgamescreen.GameSetupInfo
import com.unciv.ui.newgamescreen.NewGameScreen
import com.unciv.ui.saves.LoadGameScreen
import com.unciv.ui.saves.SaveGameScreen
import com.unciv.ui.utils.Popup
import com.unciv.ui.utils.addSeparator
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.toLabel
import com.unciv.ui.victoryscreen.VictoryScreen
import com.unciv.ui.worldscreen.WorldScreen

class WorldScreenMenuPopup(val worldScreen: WorldScreen) : Popup(worldScreen) {

    val buttonWidth = 200f
    val buttonHeight = 30f

    init {
        addMenuButton("Main menu") { worldScreen.game.setScreen(MainMenuScreen()) }
        addMenuButton("Civilopedia") { worldScreen.game.setScreen(CivilopediaScreen(worldScreen.gameInfo.ruleSet)) }
        addMenuButton("Save game") { worldScreen.game.setScreen(SaveGameScreen()) }
        addMenuButton("Load game") { worldScreen.game.setScreen(LoadGameScreen(worldScreen)) }

        addMenuButton("Start new game") {
            val newGameScreen = NewGameScreen(worldScreen, GameSetupInfo(worldScreen.gameInfo))
            worldScreen.game.setScreen(newGameScreen)
        }

        addMenuButton("Victory status") { worldScreen.game.setScreen(VictoryScreen(worldScreen)) }
        addMenuButton("Options") { OptionsPopup(worldScreen).open(force = true) }
        addMenuButton("Community") { WorldScreenCommunityPopup(worldScreen).open(force = true) }

        addSquareButton(Constants.close) {
            close()
        }.size(buttonWidth, buttonHeight)
    }

    fun addMenuButton(text: String, action: () -> Unit) {
        addSquareButton(text) {
            action()
            close()
        }.size(buttonWidth, buttonHeight)
        innerTable.addSeparator()
    }


    fun addSquareButton(text: String, action: () -> Unit): Cell<Table> {
        val button = Table()
        button.add(text.toLabel())
        button.onClick(action)
        button.touchable = Touchable.enabled
        return add(button).apply { row() }
    }
}

class WorldScreenCommunityPopup(val worldScreen: WorldScreen) : Popup(worldScreen) {
    init {
        addButton("Discord") {
            Gdx.net.openURI("https://discord.gg/bjrB4Xw")
            close()
        }

        addButton("Github") {
            Gdx.net.openURI("https://github.com/yairm210/UnCiv")
            close()
        }

        addButton("Reddit") {
            Gdx.net.openURI("https://www.reddit.com/r/Unciv/")
            close()
        }

        addCloseButton()
    }
}