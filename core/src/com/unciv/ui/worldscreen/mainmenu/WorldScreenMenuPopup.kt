package com.unciv.ui.worldscreen.mainmenu

import com.badlogic.gdx.Gdx
import com.unciv.MainMenuScreen
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.ui.newgamescreen.NewGameScreen
import com.unciv.ui.saves.LoadGameScreen
import com.unciv.ui.saves.SaveGameScreen
import com.unciv.ui.utils.Popup
import com.unciv.ui.victoryscreen.VictoryScreen
import com.unciv.ui.worldscreen.WorldScreen

class WorldScreenMenuPopup(val worldScreen: WorldScreen) : Popup(worldScreen) {
    init {
        defaults().fillX()

        addButton("Main menu") {
            worldScreen.game.setScreen(MainMenuScreen())
        }
        addButton("Civilopedia") {
            close()
            worldScreen.game.setScreen(CivilopediaScreen(worldScreen.gameInfo.ruleSet, worldScreen))
        }
        addButton("Save game") {
            close()
            worldScreen.game.setScreen(SaveGameScreen(worldScreen.gameInfo))
        }
        addButton("Load game") {
            close()
            worldScreen.game.setScreen(LoadGameScreen(worldScreen))
        }

        addButton("Start new game") {
            close()
            val newGameSetupInfo = GameSetupInfo(worldScreen.gameInfo)
            newGameSetupInfo.mapParameters.reseed()
            val newGameScreen = NewGameScreen(worldScreen, newGameSetupInfo)
            worldScreen.game.setScreen(newGameScreen)
        }

        addButton("Victory status") {
            close()
            worldScreen.game.setScreen(VictoryScreen(worldScreen))
        }
        addButton("Options") {
            close()
            worldScreen.openOptionsPopup()
        }
        addButton("Community") {
            close()
            WorldScreenCommunityPopup(worldScreen).open(force = true)
        }
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
