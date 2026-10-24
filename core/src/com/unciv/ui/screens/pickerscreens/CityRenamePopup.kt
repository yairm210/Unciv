package com.unciv.ui.screens.pickerscreens

import com.unciv.models.translations.tr
import com.unciv.ui.popups.AskTextPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.view.CityView

/** Popup to allow renaming a [cityView].
 *
 *  Note - The translated name will be offered, and translation markers are removed.
 *  The saved name will not treat translation in any way, so possibly the user will see his text unexpectedly translated if there is a translation entry for it.
 */
class CityRenamePopup(val screen: BaseScreen, val cityView: CityView, val actionOnClose: ()->Unit) {
    init {
        AskTextPopup(
            screen,
            label = "Please enter a new name for your city",
            defaultText = cityView.name.tr(hideIcons = true),
            validate = { it != "" },
            actionOnOk = { text ->
                cityView.tryRenameCity(text)
                actionOnClose()
            }
        ).open()
    }

}
