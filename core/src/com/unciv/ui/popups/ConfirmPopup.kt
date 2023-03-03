package com.unciv.ui.popups

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle
import com.badlogic.gdx.utils.Align
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.KeyboardBinding
import com.unciv.ui.components.extensions.toLabel

/** Variant of [Popup] pre-populated with one label, plus confirm and cancel buttons
 * @param stageToShowOn Parent [Stage], see [Popup.stageToShowOn]
 * @param question The text for the label
 * @param confirmText The text for the "Confirm" button
 * @param isConfirmPositive If the action to be performed is positive or not (i.e. buy = positive, delete = negative), default false
 * @param action A lambda to execute when "Yes" is chosen
 * @param restoreDefault A lambda to execute when "No" is chosen
 */
open class ConfirmPopup(
    stageToShowOn: Stage,
    question: String,
    confirmText: String,
    isConfirmPositive: Boolean = false,
    restoreDefault: () -> Unit = {},
    action: () -> Unit
) : Popup(stageToShowOn) {

    constructor(
        screen: BaseScreen,
        question: String,
        confirmText: String,
        isConfirmPositive: Boolean = false,
        restoreDefault: () -> Unit = {},
        action: () -> Unit
    ) : this(screen.stage, question, confirmText, isConfirmPositive, restoreDefault, action)

    /** The [Label][com.badlogic.gdx.scenes.scene2d.ui.Label] created for parameter `question` for optional layout tweaking */
    private val promptLabel = question.toLabel()

    init {
        promptLabel.setAlignment(Align.center)
        add(promptLabel).colspan(2).row()
        addCloseButton("Cancel", KeyboardBinding.Cancel, action = restoreDefault)
        val confirmStyleName = if (isConfirmPositive) "positive" else "negative"
        val confirmStyle = BaseScreen.skin.get(confirmStyleName, TextButtonStyle::class.java)
        addOKButton(confirmText, KeyboardBinding.Confirm, confirmStyle, action = action)
        equalizeLastTwoButtonWidths()
    }

}
