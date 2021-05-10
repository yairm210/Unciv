package com.unciv.ui.newgamescreen

import com.badlogic.gdx.scenes.scene2d.Stage
import com.unciv.models.ruleset.Ruleset

/**
 * Interface to implement for all screens using [GameOptionsTable] and [PlayerPickerTable]
 * for universal usage of those two tables.
 */
interface IPreviousScreen {
    val gameSetupInfo: GameSetupInfo
    var stage: Stage
    val ruleset: Ruleset

    /**
     * Method added for compatibility with [PlayerPickerTable] which addresses
     * [setRightSideButtonEnabled] method of previous screen
     */
    fun setRightSideButtonEnabled(boolean: Boolean)
}