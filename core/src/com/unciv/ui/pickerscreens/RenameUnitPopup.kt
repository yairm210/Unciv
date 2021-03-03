package com.unciv.ui.pickerscreens

import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.logic.map.MapUnit
import com.unciv.models.translations.tr
import com.unciv.ui.utils.*

class RenameUnitPopup(unit: MapUnit, screen: CameraStageBaseScreen): Popup(screen) {

    init {
        addGoodSizedLabel("Choose name for [${unit.baseUnit.name}]").row()

        val nameField = TextField("", skin)
        // Disallowed special characters determined with US keyboard and a certain binary
        // copyrighted by Firaxis Games
        val illegal_chars = "%*[]\"\\|<>/?"
        nameField.textFieldFilter = TextField.TextFieldFilter { _, char -> !(char in illegal_chars)}
        // Max name length decided arbitrarily
        nameField.maxLength = 10
        add(nameField)
        row()
        add("OK".toTextButton().onClick {
            if (nameField.text != "") {
                unit.instanceName = nameField.text
            }
            screen.game.setScreen(PromotionPickerScreen(unit))
        })
        add("Close".toTextButton().onClick { close() })

    }
}