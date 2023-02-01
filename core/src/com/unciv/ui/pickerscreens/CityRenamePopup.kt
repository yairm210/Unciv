package com.unciv.ui.pickerscreens

import com.unciv.logic.city.City
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.AskTextPopup
import com.unciv.ui.utils.BaseScreen

class CityRenamePopup(val screen: BaseScreen, val city: City, val actionOnClose: ()->Unit) {
    init {
        AskTextPopup(
            screen,
            label = "Please enter a new name for your city",
            defaultText = city.name.tr(),
            validate = { it != "" },
            actionOnOk = { text ->
                city.name = text
                actionOnClose()
            }
        ).open()
    }

}

