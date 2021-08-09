package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.logic.map.MapUnit
import com.unciv.ui.utils.*

class RenameUnitPopup(unit: MapUnit, screen: CameraStageBaseScreen): Popup(screen) {

    init {
        val wrapper = Table()
        wrapper.add(ImageGetter.getUnitIcon(unit.name).surroundWithCircle(80f)).padRight(10f)
        wrapper.add("Choose name for [${unit.baseUnit.name}]".toLabel())
        add(wrapper).colspan(2).row()

        val nameField = TextField("", skin)
        // Disallowed special characters - used by tr() or simply precaution
        val illegalChars = "[]{}\"\\<>"
        nameField.textFieldFilter = TextField.TextFieldFilter { _, char -> char !in illegalChars}
        nameField.maxLength = unit.civInfo.gameInfo.ruleSet.units.values.maxOf { it.name.length }
        add(nameField).growX().colspan(2).row()
        addOKButton {
            if (nameField.text != "" && nameField.text != unit.name) {
                unit.instanceName = nameField.text
            }
            screen.game.setScreen(PromotionPickerScreen(unit))
        }
        addCloseButton()
        equalizeLastTwoButtonWidths()
        keyboardFocus = nameField
    }
}