package com.unciv.ui.screens

import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.unciv.ui.components.extensions.center
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

class GameStartScreen : BaseScreen() {
    init {
        val logoImage = ImageGetter.getExternalImage("banner.png")
        logoImage.center(stage)
        stage.addActor(logoImage)
    }
}
