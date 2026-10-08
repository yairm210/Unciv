package com.unciv.ui.popups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toStringSigned
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter

/** Simple class for showing a prompt for a positive integer to the user
 * @param stageToShowOn The stage of the previous screen the user was on
 * @param label A line of text shown to the user
 * @param icon Icon at the top, should have size 80f
 * @param defaultValue The number that should be in the prompt at the start
 * @param amountButtons Buttons that when clicked will add/subtract these amounts to the number
 * @param bounds The bounds in which the number must lie. Defaults to [Int.MIN_VALUE, Int.MAX_VALUE]
 * @param errorText Text that will be shown when an error is detected
 * @param validate Function that should return `true` when a valid input is detected
 * @param actionOnOk Lambda that will be executed after pressing 'OK'.
 */

class AskNumberPopup(
    stageToShowOn: Stage,
    label: String,
    icon: IconCircleGroup = ImageGetter.getImage("OtherIcons/Pencil").apply { this.color = ImageGetter.CHARCOAL }.surroundWithCircle(80f),
    defaultValue: Int? = null,
    amountButtons: List<Int> = listOf(),
    bounds: IntRange = IntRange(Int.MIN_VALUE, Int.MAX_VALUE),
    errorText: String = "Invalid input! Please enter a valid number.",
    validate: (input: Int) -> Boolean = { true },
    actionOnOk: (input: Int) -> Unit = { },
): Popup(stageToShowOn) {
    init {
        val wrapper = Table()
        wrapper.add(icon).padRight(10f)
        wrapper.add(label.toLabel())
        add(wrapper).colspan(2).row()

        val valueField = UncivTextField.Integer(label, defaultValue)

        val centerTable = Table(skin)

        fun addValueButton(delta: Int) {
            centerTable.add(
                Button(
                    delta.toStringSigned().toLabel(),
                    skin
                ).apply {
                    onClick {
                        val value = valueField.intValue ?: return@onClick
                        valueField.intValue = (value + delta).coerceIn(bounds)
                    }
                }
            ).pad(5f)
        }

        for (value in amountButtons.reversed()) {
            addValueButton(-value)
        }

        centerTable.add(valueField).growX().pad(10f)

        add(centerTable).colspan(2).row()

        for (value in amountButtons) {
            addValueButton(value)
        }

        val errorLabel = errorText.toLabel()
        errorLabel.color = Color.RED

        addCloseButton()
        val okButton = addOKButton(
            validate = {
                val errorFound = valueField.intValue?.let { validate(it) } != true
                if (errorFound) add(errorLabel).colspan(2).center()
                !errorFound
            }
        ) {
            actionOnOk(valueField.intValue!!)
        }.actor
        equalizeLastTwoButtonWidths()

        valueField.onChange {
            okButton.isEnabled = valueField.intValue?.let { it in bounds } == true
        }

        keyboardFocus = valueField
    }
}
