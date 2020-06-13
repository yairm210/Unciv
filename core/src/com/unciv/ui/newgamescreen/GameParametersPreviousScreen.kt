package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.Stage
import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.CameraStageBaseScreen

abstract class GameParametersPreviousScreen: PickerScreen() {
    abstract var gameSetupInfo: GameSetupInfo
    abstract val ruleset: Ruleset
}