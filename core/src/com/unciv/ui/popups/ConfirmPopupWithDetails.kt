package com.unciv.ui.popups

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.widgets.AutoScrollPane
import com.unciv.ui.components.widgets.WrappableLabel
import com.unciv.ui.screens.basescreen.BaseScreen

/**
 * Variant of [Popup] pre-populated with one header, plus a scrollable (without header or buttons) details pane, plus confirm and cancel buttons.
 * @param stageToShowOn Parent [Stage], see [Popup.stageToShowOn]
 * @param title The text for the header, displayed at header font size and centered. Wraps and distributes evenly if it needs to.
 * @param details Each entry is converted to a text line via `.toString()` and displayed left-aligned in the scroll pane
 * @param confirmText The text for the "Confirm" button
 * @param isConfirmPositive If the action to be performed is positive or not (i.e. buy = positive, delete = negative), default false
 * @param cancelText The text for the "Cancel" button
 * @param confirmAction A lambda to execute when "Confirm" is chosen
 * @param cancelAction A lambda to execute when "Cancel" is chosen
 */
class ConfirmPopupWithDetails(
    stageToShowOn: Stage,
    title: String,
    details: Sequence<Any> = emptySequence(),
    confirmText: String = Constants.OK,
    isConfirmPositive: Boolean = false,
    cancelText: String = Constants.cancel,
    cancelAction: () -> Unit = {},
    confirmAction: () -> Unit
) : Popup(stageToShowOn, scrollable = Scrollability.None) {
    init {
        val label = WrappableLabel(title, stageToShowOn.width / 2, fontSize = Constants.headingFontSize).apply {
            setAlignment(Align.center)
            wrap = true
            optimizePrefWidth()
        }
        val detailTable = Table()
        detailTable.defaults().left()
        for (line in details) {
            detailTable.add(line.toString().toLabel()).row()
        }
        val scroll = AutoScrollPane(detailTable, BaseScreen.skin)
        val confirmStyleName = if (isConfirmPositive) "positive" else "negative"
        val confirmStyle = BaseScreen.skin.get(confirmStyleName, TextButton.TextButtonStyle::class.java)

        add(label).colspan(2).center().row()
        add(scroll).colspan(2).left().grow().row()
        addCloseButton(cancelText, KeyboardBinding.Cancel, action = cancelAction)
        addOKButton(confirmText, KeyboardBinding.Confirm, confirmStyle, action = confirmAction)
        equalizeLastTwoButtonWidths()
        open()
    }
}
