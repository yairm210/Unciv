package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField

/** Simple class for showing a prompt for a string to the user
 * @param screen The previous screen the user was on
 * @param label A line of text shown to the user
 * @param icon Icon at the top, should have size 80f
 * @param defaultText The text that should be in the prompt at the start
 * @param maxLength The maximal amount of characters the user may input
 * @param actionOnOk Lambda that will be executed after pressing 'OK'. 
 * Gets the text the user inputted as a parameter, and will automatically close() afterwards
 */
class AskTextPopup(
    screen: CameraStageBaseScreen,
    label: String = "Please enter some text",
    icon: IconCircleGroup = ImageGetter.getImage("OtherIcons/Pencil").apply { this.color = Color.BLACK }.surroundWithCircle(80f),
    defaultText: String = "",
    maxLength: Int = 32,
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
        
        addOKButton { actionOnOk(nameField.text); close() }
        addCloseButton()
        equalizeLastTwoButtonWidths()
        keyboardFocus = nameField
    }
}