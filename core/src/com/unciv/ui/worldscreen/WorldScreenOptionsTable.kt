package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.*
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.pickerscreens.PolicyPickerScreen
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.center

class WorldScreenOptionsTable internal constructor(worldScreen: WorldScreen, private val civInfo: CivilizationInfo) : Table() {

    init {
        val tileTableBackground = ImageGetter.getDrawable("skin/whiteDot.png")
                .tint(Color(0x004085bf))
        background = tileTableBackground
        isVisible = false

        pad(20f)
        defaults().pad(5f)

        val openCivilopediaButton = TextButton("Civilopedia", CameraStageBaseScreen.skin).apply { color=ImageGetter.getBlue(); background=null }
        openCivilopediaButton.addClickListener {
            worldScreen.game.screen = CivilopediaScreen()
            isVisible = false
        }
        add(openCivilopediaButton).row()

        val loadGameButton = TextButton("Load game", CameraStageBaseScreen.skin).apply { color=ImageGetter.getBlue() }
        loadGameButton .addClickListener {
            worldScreen.game.screen = LoadScreen()
            isVisible=false
        }
        add(loadGameButton ).row()


        val saveGameButton = TextButton("Save game", CameraStageBaseScreen.skin).apply { color=ImageGetter.getBlue() }
        saveGameButton .addClickListener {
            worldScreen.game.screen = SaveScreen()
            isVisible=false
        }
        add(saveGameButton ).row()

        val startNewGameButton = TextButton("Start new game", CameraStageBaseScreen.skin).apply { color=ImageGetter.getBlue() }
        startNewGameButton.addClickListener { worldScreen.game.screen = NewGameScreen() }
        add(startNewGameButton).row()

        val openVictoryScreen = TextButton("Victory status", CameraStageBaseScreen.skin).apply { color=ImageGetter.getBlue() }
        openVictoryScreen.addClickListener {
            worldScreen.game.screen = VictoryScreen()
        }
        add(openVictoryScreen).row()

        val openPolicyPickerScreen = TextButton("Social Policies", CameraStageBaseScreen.skin).apply { color=ImageGetter.getBlue() }
        openPolicyPickerScreen.addClickListener {
            worldScreen.game.screen = PolicyPickerScreen(this@WorldScreenOptionsTable.civInfo)
        }
        add(openPolicyPickerScreen).row()

        val closeButton = TextButton("Close", CameraStageBaseScreen.skin).apply { color=ImageGetter.getBlue() }
        closeButton.addClickListener { isVisible = false }
        add(closeButton)

        pack() // Needed to show the background.
        center(worldScreen.stage)
    }
}
