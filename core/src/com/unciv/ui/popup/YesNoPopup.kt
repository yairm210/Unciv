package com.unciv.ui.popup

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.toLabel

/** Variant of [Popup] pre-populated with one label, plus yes and no buttons
 * @param question The text for the label
 * @param action A lambda to execute when "Yes" is chosen
 * @param screen The parent screen - see [Popup.screen]. Optional, defaults to the current [WorldScreen][com.unciv.ui.worldscreen.WorldScreen]
 * @param restoreDefault A lambda to execute when "No" is chosen
 */
open class YesNoPopup(
    question: String,
    stage: Stage,
    restoreDefault: () -> Unit = {},
    action: () -> Unit
) : Popup(stage) {

    constructor(
        question: String,
        screen: BaseScreen,
        restoreDefault: () -> Unit = {},
        action: () -> Unit
    ) : this(question, screen.stage, restoreDefault, action)

    /** The [Label][com.badlogic.gdx.scenes.scene2d.ui.Label] created for parameter `question` for optional layout tweaking */
    private val promptLabel = question.toLabel()

    init {
        promptLabel.setAlignment(Align.center)
        add(promptLabel).colspan(2).row()
        addOKButton(Constants.yes, KeyCharAndCode('y'), action = action)
        addCloseButton(Constants.no, KeyCharAndCode('n'), restoreDefault)
        equalizeLastTwoButtonWidths()
    }
}

/** Shortcut to open a [YesNoPopup] with the exit game question */
class ExitGamePopup(screen: BaseScreen, force: Boolean = false) : YesNoPopup(
    question = "Do you want to exit the game?",
    screen = screen,
    restoreDefault = { screen.game.musicController.resume() },
    action = { Gdx.app.exit() }
) {
    init {
        screen.game.musicController.pause()
        open(force)
    }
}
