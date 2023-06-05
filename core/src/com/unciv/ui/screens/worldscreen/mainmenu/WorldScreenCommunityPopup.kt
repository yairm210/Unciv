package com.unciv.ui.screens.worldscreen.mainmenu

import com.badlogic.gdx.Gdx
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.worldscreen.WorldScreen

class WorldScreenCommunityPopup(val worldScreen: WorldScreen) : Popup(worldScreen, scrollable = Scrollability.All) {
    init {
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
