package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.CivilopediaScreen
import com.unciv.ui.ScienceVictoryScreen
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.pickerscreens.PolicyPickerScreen
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter

class WorldScreenOptionsTable internal constructor(worldScreen: WorldScreen, private val civInfo: CivilizationInfo) : Table() {

    init {
        val tileTableBackground = ImageGetter.getDrawable("skin/tileTableBackground.png")
                .tint(Color(0x004085bf))
        background = tileTableBackground

        isVisible = false

        val openCivilopediaButton = TextButton("Civilopedia", CameraStageBaseScreen.skin)
        openCivilopediaButton.addClickListener {
            worldScreen.game.screen = CivilopediaScreen()
            isVisible = false
        }
        add(openCivilopediaButton).pad(10f)
        row()

        val StartNewGameButton = TextButton("Start new game", CameraStageBaseScreen.skin)
        StartNewGameButton.addClickListener { worldScreen.game.startNewGame(true) }

        add(StartNewGameButton).pad(10f)
        row()

        val OpenScienceVictoryScreen = TextButton("Science victory status", CameraStageBaseScreen.skin)
        OpenScienceVictoryScreen.addClickListener {
            worldScreen.game.screen = ScienceVictoryScreen(this@WorldScreenOptionsTable.civInfo)
        }
        add(OpenScienceVictoryScreen).pad(10f)
        row()

        val OpenPolicyPickerScreen = TextButton("Social Policies", CameraStageBaseScreen.skin)
        OpenPolicyPickerScreen.addClickListener {
            worldScreen.game.screen = PolicyPickerScreen(this@WorldScreenOptionsTable.civInfo)
        }
        add(OpenPolicyPickerScreen).pad(10f)
        row()

        val closeButton = TextButton("Close", CameraStageBaseScreen.skin)
        closeButton.addClickListener { isVisible = false }
        add(closeButton).pad(10f)
        pack() // Needed to show the background.
        setPosition(worldScreen.stage.width / 2 - width / 2,
                worldScreen.stage.height / 2 - height / 2)
    }
}
