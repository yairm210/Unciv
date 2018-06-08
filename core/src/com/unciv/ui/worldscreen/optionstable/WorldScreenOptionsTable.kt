package com.unciv.ui.worldscreen.optionstable

import com.unciv.UnCivGame
import com.unciv.ui.*
import com.unciv.ui.pickerscreens.PolicyPickerScreen
import com.unciv.ui.utils.center

class WorldScreenOptionsTable internal constructor() : OptionsTable() {

    init {
        addButton("Civilopedia"){
            UnCivGame.Current.screen = CivilopediaScreen()
            remove()
        }

        addButton("Load game"){
            UnCivGame.Current.screen = LoadScreen()
            remove()
        }

        addButton("Save game") {
            UnCivGame.Current.screen = SaveScreen()
            remove()
        }

        addButton("Start new game"){ UnCivGame.Current.screen = NewGameScreen() }

        addButton("Victory status") { UnCivGame.Current.screen = VictoryScreen() }

        addButton("Social Policies"){
            UnCivGame.Current.screen = PolicyPickerScreen(UnCivGame.Current.gameInfo.getPlayerCivilization())
        }


        addButton("Display options"){
            UnCivGame.Current.worldScreen.stage.addActor(WorldScreenDisplayOptionsTable())
            remove()
        }

        addButton("Close"){ remove() }

        pack() // Needed to show the background.
        center(UnCivGame.Current.worldScreen.stage)
    }
}

