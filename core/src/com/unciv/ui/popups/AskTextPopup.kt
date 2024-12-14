package com.unciv.ui.popups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel

/** Simple class for showing a prompt for a string to the user
 * @param screen The previous screen the user was on
 * @param label A line of text shown to the user
 * @param icon Icon at the top, should have size 80f
 * @param defaultText The text that should be in the prompt at the start
 * @param errorText Text that will be shown when an error is detected
 * @param maxLength The maximal amount of characters the user may input
 * @param validate Function that should return `true` when a valid input is entered, false otherwise
 * @param actionOnOk Lambda that will be executed after pressing 'OK'.
 * Gets the text the user inputted as a parameter.
 */
class AskTextPopup(
    screen: BaseScreen,
    label: String = "Please enter some text",
    icon: Group = ImageGetter.getImage("OtherIcons/Pencil").apply { this.color = ImageGetter.CHARCOAL }.surroundWithCircle(80f),
    defaultText: String = "",
    errorText: String = "Invalid input! Please enter a different string.",
    maxLength: Int = 32,
    validate: (input: String) -> Boolean = { true },
    actionOnOk: (input: String) -> Unit = {},
) : Popup(screen) {

    val illegalChars = "[]{}\"\\<>"

    init {
        val wrapper = Table()
        wrapper.add(icon).padRight(10f)
        wrapper.add(label.toLabel())
        add(wrapper).colspan(2).row()

        val nameField = UncivTextField(label, defaultText)
        nameField.textFieldFilter = TextField.TextFieldFilter { _, char -> char !in illegalChars}
        nameField.maxLength = maxLength

        add(nameField).growX().colspan(2).row()

        val errorLabel = errorText.toLabel()
        errorLabel.color = Color.RED

        addCloseButton()
        addOKButton(
            validate = {
                val errorFound = !validate(nameField.text)
                if (errorFound) {
                    row()
                    add(errorLabel).colspan(2).center()
                }
                !errorFound
            }
        ) {
            actionOnOk(nameField.text)
        }
        equalizeLastTwoButtonWidths()
        keyboardFocus = nameField
    }
}
