package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.CivilopediaScreen
import com.unciv.ui.LoadScreen
import com.unciv.ui.SaveScreen
import com.unciv.ui.VictoryScreen
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
        add(openCivilopediaButton).pad(10f).row()

        val loadGameButton = TextButton("Load game", CameraStageBaseScreen.skin)
        loadGameButton .addClickListener {
            worldScreen.game.screen = LoadScreen()
            isVisible=false
        }
        add(loadGameButton ).pad(10f).row()


        val saveGameButton = TextButton("Save game", CameraStageBaseScreen.skin)
        saveGameButton .addClickListener {
            worldScreen.game.screen = SaveScreen()
            isVisible=false
        }
        add(saveGameButton ).pad(10f).row()
        val startNewGameButton = TextButton("Start new game", CameraStageBaseScreen.skin)
        startNewGameButton.addClickListener { worldScreen.game.startNewGame(true) }
        add(startNewGameButton).pad(10f).row()

        val openVictoryScreen = TextButton("Victory status", CameraStageBaseScreen.skin)
        openVictoryScreen.addClickListener {
            worldScreen.game.screen = VictoryScreen()
        }
        add(openVictoryScreen).pad(10f).row()

        val openPolicyPickerScreen = TextButton("Social Policies", CameraStageBaseScreen.skin)
        openPolicyPickerScreen.addClickListener {
            worldScreen.game.screen = PolicyPickerScreen(this@WorldScreenOptionsTable.civInfo)
        }
        add(openPolicyPickerScreen).pad(10f).row()

        val closeButton = TextButton("Close", CameraStageBaseScreen.skin)
        closeButton.addClickListener { isVisible = false }
        add(closeButton).pad(10f)

        pack() // Needed to show the background.
        setPosition(worldScreen.stage.width / 2 - width / 2,
                worldScreen.stage.height / 2 - height / 2)
    }
}
