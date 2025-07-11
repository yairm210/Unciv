package com.unciv.ui.popups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toStringSigned
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

/** Simple class for showing a prompt for a positive integer to the user
 * @param screen The previous screen the user was on
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
    screen: BaseScreen,
    label: String = "Please enter a number",
    icon: IconCircleGroup = ImageGetter.getImage("OtherIcons/Pencil").apply { this.color = ImageGetter.CHARCOAL }.surroundWithCircle(80f),
    defaultValue: Int? = null,
    amountButtons: List<Int> = listOf(),
    bounds: IntRange = IntRange(Int.MIN_VALUE, Int.MAX_VALUE),
    errorText: String = "Invalid input! Please enter a valid number.",
    validate: (input: Int) -> Boolean = { true },
    actionOnOk: (input: Int) -> Unit = { },
): Popup(screen) {
    init {
        val wrapper = Table()
        wrapper.add(icon).padRight(10f)
        wrapper.add(label.toLabel())
        add(wrapper).colspan(2).row()

        val nameField = UncivTextField.Integer(label, defaultValue)

        fun clampInBounds(input: Int?): Int? {
            if (input == null) return null

            if (bounds.first > input) {
                return bounds.first
            }
            if (bounds.last < input)
                return bounds.last

            return input
        }

        nameField.onChange {
            nameField.intValue = clampInBounds(nameField.intValue)
        }

        val centerTable = Table(skin)

        fun addValueButton(delta: Int) {
            centerTable.add(
                Button(
                    delta.toStringSigned().toLabel(),
                    skin
                ).apply {
                    onClick {
                        val value = nameField.intValue ?: return@onClick
                        nameField.intValue = value + delta
                    }
                }
            ).pad(5f)
        }

        for (value in amountButtons.reversed()) {
            addValueButton(-value)
        }

        centerTable.add(nameField).growX().pad(10f)

        add(centerTable).colspan(2).row()

        for (value in amountButtons) {
            addValueButton(value)
        }

        val errorLabel = errorText.toLabel()
        errorLabel.color = Color.RED

        addCloseButton()
        addOKButton(
            validate = {
                val errorFound = nameField.intValue?.let { validate(it) } != true
                if (errorFound) add(errorLabel).colspan(2).center()
                !errorFound
            }
        ) {
            actionOnOk(nameField.intValue!!)
        }
        equalizeLastTwoButtonWidths()

        keyboardFocus = nameField
    }
}
