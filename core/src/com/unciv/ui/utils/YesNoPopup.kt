package com.unciv.ui.utils

import com.badlogic.gdx.Gdx
import com.unciv.Constants
import com.unciv.UncivGame

/** Variant of [Popup] pre-populated with one label, plus yes and no buttons
 * @param question The text for the label
 * @param action A lambda to execute when "Yes" is chosen
 * @param screen The parent screen - see [Popup.screen]. Optional, defaults to the current [WorldScreen][com.unciv.ui.worldscreen.WorldScreen]
 * @param restoreDefault A lambda to execute when "No" is chosen
 */
open class YesNoPopup (
            question:String,
            action:()->Unit,
            screen: CameraStageBaseScreen = UncivGame.Current.worldScreen,
            restoreDefault:()->Unit = {}
        ) : Popup(screen) {

    init {
        add(question.toLabel()).colspan(2).row()
        addOKButton(Constants.yes, KeyCharAndCode('y'), action)
        addCloseButton(Constants.no, KeyCharAndCode('n'), restoreDefault)
        equalizeLastTwoButtonWidths()
    }
}

/** Shortcut to open a [YesNoPopup] with the exit game question */
class ExitGamePopup(screen: CameraStageBaseScreen, force: Boolean = false)
    : YesNoPopup (
        question = "Do you want to exit the game?",
        action = { Gdx.app.exit() },
        screen = screen
    ) {
    init {
        open(force)
    }
}
