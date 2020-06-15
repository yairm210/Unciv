package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.Stage
import com.unciv.models.ruleset.Ruleset
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.CameraStageBaseScreen

/**
 * Interface to use as a previous screen for GameOptionsTable and PlayerPickerTable
 * It should be a child of the PickerScreen class during new game creation
 * or CameraBackStageScreen class for map editing
 */

interface PreviousScreenInterface {
    var gameSetupInfo: GameSetupInfo
    var stage: Stage

    // added for compatibility with NewGameScreen: PickerScreen
    fun setRightSideButtonEnabled(boolean: Boolean)
}

//abstract class GameParametersPreviousScreen: PickerScreen() {
//    abstract var gameSetupInfo: GameSetupInfo
//    abstract val ruleset: Ruleset
//}