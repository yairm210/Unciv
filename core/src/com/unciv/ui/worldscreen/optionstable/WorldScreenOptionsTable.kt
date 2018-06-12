package com.unciv.ui.worldscreen.optionstable

import com.unciv.UnCivGame
import com.unciv.ui.*
import com.unciv.ui.pickerscreens.PolicyPickerScreen
import com.unciv.ui.utils.center
import com.unciv.ui.utils.tr

class WorldScreenOptionsTable internal constructor() : OptionsTable() {

    init {
        addButton("Civilopedia".tr()){
            UnCivGame.Current.screen = CivilopediaScreen()
            remove()
        }

        addButton("Load game".tr()){
            UnCivGame.Current.screen = LoadScreen()
            remove()
        }

        addButton("Save game".tr()) {
            UnCivGame.Current.screen = SaveScreen()
            remove()
        }

        addButton("Start new game".tr()){ UnCivGame.Current.screen = NewGameScreen() }

        addButton("Victory status".tr()) { UnCivGame.Current.screen = VictoryScreen() }

        addButton("Social Policies".tr()){
            UnCivGame.Current.screen = PolicyPickerScreen(UnCivGame.Current.gameInfo.getPlayerCivilization())
        }


        addButton("Display options".tr()){
            UnCivGame.Current.worldScreen.stage.addActor(WorldScreenDisplayOptionsTable())
            remove()
        }

        addButton("Close".tr()){ remove() }

        pack() // Needed to show the background.
        center(UnCivGame.Current.worldScreen.stage)
    }
}

