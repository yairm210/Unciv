package com.unciv.ui.screens.worldscreen.status

import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.utils.Disposable
import com.unciv.GUI
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen

class AutoPlayStatusButton(
    val worldScreen: WorldScreen,
    nextTurnButton: NextTurnButton
) : Button(BaseScreen.skin), Disposable {
    private val autoPlayImage = createAutoplayImage()


    init {
        add(Stack(autoPlayImage)).pad(5f)
        val settings = GUI.getSettings()
        onActivation(binding = KeyboardBinding.AutoPlayMenu) {
            if (settings.autoPlay.isAutoPlaying())
                settings.autoPlay.stopAutoPlay()
            else if (worldScreen.viewingCiv == worldScreen.gameInfo.currentPlayerCiv)
                AutoPlayMenu(stage,this, nextTurnButton, worldScreen)
        }
        val directAutoPlay = {
            if (!worldScreen.gameInfo.gameParameters.isOnlineMultiplayer
                && worldScreen.viewingCiv == worldScreen.gameInfo.currentPlayerCiv) {
                settings.autoPlay.startAutoPlay()
                nextTurnButton.update()
            }
        }
        onRightClick(action = directAutoPlay)
        keyShortcuts.add(KeyboardBinding.AutoPlay, action = directAutoPlay)
    }

    private fun createAutoplayImage(): Image {
        val img = ImageGetter.getImage("OtherIcons/NationSwap")
        img.setSize(40f)
        return img
    }

    override fun dispose() {
        val settings = GUI.getSettings()
        if (isPressed && settings.autoPlay.isAutoPlaying()) {
            settings.autoPlay.stopAutoPlay()
        }
    }
}
