package com.unciv.ui.popups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.models.translations.tr
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
    defaultValue: String = "",
    amountButtons: List<Int> = listOf(),
    bounds: IntRange = IntRange(Int.MIN_VALUE, Int.MAX_VALUE),
    errorText: String = "Invalid input! Please enter a valid number.",
    validate: (input: Int) -> Boolean = { true },
    actionOnOk: (input: Int) -> Unit = { },
): Popup(screen) {
    /** Note for future developers: Why this class only accepts positive digits and not negative.
     *
     * The problems is the minus sign. This might not seem like a large obstacle, but problems
     * arrive quickly. First is that our clean `DigitsOnlyFilter()` must be replaced with a check
     * that allows for adding a minus sign, but only when it is the first character. So far so good,
     * until a user starts typing numbers before an already placed - sign --> crash. Fix that
     * by disallowing any character being typed in front of a - sign. All is fixed right? Wrong!
     * Because you now also disallowed writing two minus signs at the same time, copying over a
     * new number after clamping now disallows overwriting the existing minus sign with a new minus
     * sign, as there is already a minus sign in the number. Well, no problem, you can just remove
     * the number before overwriting it with the clamped variant. But now you reset your cursor
     * position every time you type a character. You might start trying to cache the cursor position
     * as well, but at that point you're basically rewriting the setText() function, and when I
     * reached this point I decided to stop.
     *
     * P.S., if you do decide to go on this quest of adding minus signs, don't forget that
     * `"-".toInt()` also crashes, so you need to exclude that before checking to clamp.
     */

    init {
        val wrapper = Table()
        wrapper.add(icon).padRight(10f)
        wrapper.add(label.toLabel())
        add(wrapper).colspan(2).row()

        val nameField = UncivTextField(label, defaultValue)
        nameField.textFieldFilter = TextField.TextFieldFilter { _, char -> char.isDigit() || char == '-' }

        fun isValidInt(input: String): Boolean {
            return input.toIntOrNull() != null
        }


        fun clampInBounds(input: String): String {
            val int = input.toIntOrNull() ?: return input

            if (bounds.first > int) {
                return bounds.first.tr()
            }
            if (bounds.last < int)
                return bounds.last.tr()

            return input
        }

        nameField.onChange {
            nameField.text = clampInBounds(nameField.text)
        }

        val centerTable = Table(skin)

        fun addValueButton(value: Int) {
            centerTable.add(
                Button(
                    value.toStringSigned().toLabel(),
                    skin
                ).apply {
                    onClick {
                        if (isValidInt(nameField.text))
                            nameField.text = clampInBounds((nameField.text.toInt() + value).tr())
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
                val errorFound = !isValidInt(nameField.text) || !validate(nameField.text.toInt())
                if (errorFound) add(errorLabel).colspan(2).center()
                !errorFound
            }
        ) {
            actionOnOk(nameField.text.toInt())
        }
        equalizeLastTwoButtonWidths()

        keyboardFocus = nameField
    }
}
