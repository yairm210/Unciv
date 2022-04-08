package com.unciv.ui.popup

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.ui.images.IconCircleGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.utils.*

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
    icon: IconCircleGroup = ImageGetter.getImage("OtherIcons/Pencil").apply { this.color = Color.BLACK }.surroundWithCircle(80f),
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
        
        val nameField = TextField(defaultText, skin)
        nameField.textFieldFilter = TextField.TextFieldFilter { _, char -> char !in illegalChars}
        nameField.maxLength = maxLength
        
        add(nameField).growX().colspan(2).row()
        
        val errorLabel = errorText.toLabel()
        errorLabel.color = Color.RED

        addOKButton(
            validate = { 
                val errorFound = nameField.text == "" || !validate(nameField.text)
                if (errorFound) add(errorLabel).colspan(2).center()
                !errorFound
            }
        ) {
            actionOnOk(nameField.text)
        }
        addCloseButton()
        equalizeLastTwoButtonWidths()
        keyboardFocus = nameField
    }
}
