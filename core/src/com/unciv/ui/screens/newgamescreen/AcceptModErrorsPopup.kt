package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.ui.components.widgets.ColorMarkupLabel
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.closeAllPopups
import com.unciv.ui.screens.basescreen.BaseScreen

internal class AcceptModErrorsPopup(
    screen: BaseScreen,
    modCheckResult: String,
    restoreDefault: () -> Unit,
    action: () -> Unit
) : ConfirmPopup(
    screen,
    question = "",  // do coloured label instead
    confirmText = "Accept",
    isConfirmPositive = false,
    restoreDefault = restoreDefault,
    action = action
) {
    init {
        clickBehindToClose = false
        row()  // skip the empty question label
        val maxRowWidth = screen.stage.width * 0.9f - 50f  // total padding is 2*(20+5)
        getScrollPane()?.setScrollingDisabled(true, false)

        // Note - using the version of ColorMarkupLabel that supports «color» but it was too garish.
        val question = "Are you really sure you want to play with the following known problems?"
        val label1 = ColorMarkupLabel(question, Constants.headingFontSize)
        val wrapWidth = label1.prefWidth.coerceIn(maxRowWidth / 2, maxRowWidth)
        label1.setAlignment(Align.center)
        if (label1.prefWidth > wrapWidth) {
            label1.wrap = true
            add(label1).width(wrapWidth).padBottom(15f).row()
        } else add(label1).padBottom(15f).row()

        val warnings = modCheckResult.replace("Error:", "«RED»Error«»:")
            .replace("Warning:","«GOLD»Warning«»:")
        val label2 = ColorMarkupLabel(warnings)
        label2.wrap = true
        add(label2).width(wrapWidth)

        screen.closeAllPopups()  // Toasts too
        open(true)
    }
}
