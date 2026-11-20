package com.unciv.ui.screens.pickerscreens

import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.AskTextPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.view.MapUnitView

class UnitRenamePopup(val screen: BaseScreen, val unit: MapUnitView, val actionOnClose: ()->Unit) {
    init {
        val defaultName = unit.getBaseUnit().name.tr(hideIcons = true)
        AskTextPopup(
            screen,
            label = "Choose name for [${unit.getBaseUnit().name}]",
            icon = ImageGetter.getUnitIcon(unit.getBaseUnit()).surroundWithCircle(80f),
            defaultText = unit.instanceName ?: defaultName,
            actionOnOk = { userInput ->
                //If the user inputs an empty string OR the original name, clear the unit instanceName so the base name is used
                unit.trySetInstanceName(if (userInput == "" || userInput == defaultName) null else userInput)
                actionOnClose()
            }
        ).open()
    }
}
