package com.unciv.ui.screens.pickerscreens

import com.unciv.logic.city.City
import com.unciv.models.translations.tr
import com.unciv.ui.popups.AskTextPopup
import com.unciv.ui.screens.basescreen.BaseScreen

class CityRenamePopup(val screen: BaseScreen, val city: City, val actionOnClose: ()->Unit) {
    init {
        AskTextPopup(
            screen,
            label = "Please enter a new name for your city",
            defaultText = city.name.tr(hideIcons = true),
            validate = { it != "" },
            actionOnOk = { text ->
                city.name = text
                actionOnClose()
            }
        ).open()
    }

}
