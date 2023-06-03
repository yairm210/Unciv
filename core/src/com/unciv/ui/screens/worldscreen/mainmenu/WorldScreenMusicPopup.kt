package com.unciv.ui.screens.worldscreen.mainmenu

import com.unciv.UncivGame
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.options.addMusicControls
import com.unciv.ui.screens.worldscreen.WorldScreen

class WorldScreenMusicPopup(val worldScreen: WorldScreen) : Popup(worldScreen) {
    init {
        val musicController = UncivGame.Current.musicController
        val settings = UncivGame.Current.settings

        defaults().fillX()
        addMusicControls(this, settings, musicController)
        addCloseButton().colspan(2)
    }
}
