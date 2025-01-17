package com.unciv.ui.screens.worldscreen.status

import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.worldscreen.WorldScreen

class SmallUnitButton(
    private val worldScreen: WorldScreen,
    private val statusButtons: StatusButtons
) : IconTextButton("", null, fontColor = NextTurnAction.NextUnit.color) {

    private val nextLabel = "Cycle"
    private val waitLabel = "Wait"
    private var isWait = worldScreen.game.settings.checkForDueUnitsCycles
    
    init {
        onActivation { 
            worldScreen.switchToNextUnit(resetDue = isWait)
        }
    }

    fun update() {
        keyShortcuts.clear()
        isWait = worldScreen.game.settings.checkForDueUnitsCycles // refresh value
        if(isWait) {
            label.setText(waitLabel.tr())
            iconCell.setActor(ImageGetter.getUnitActionPortrait(waitLabel, 20f))
            keyShortcuts.add(KeyboardBinding.Wait)
            addTooltip(KeyboardBinding.Wait)
        } else {
            label.setText(nextLabel.tr())
            iconCell.setActor(ImageGetter.getImage("OtherIcons/Loading").apply { setSize(20f) })
            keyShortcuts.add(KeyboardBinding.Cycle)
            addTooltip(KeyboardBinding.Cycle)
        }
        val nextTurnButton = statusButtons.nextTurnButton
        val visible = nextTurnButton.isVisible
            && nextTurnButton.isNextUnitAction()
            && worldScreen.bottomUnitTable.selectedUnit != null
        statusButtons.smallUnitButton = if (visible) this else null
        isEnabled = visible && nextTurnButton.isEnabled
            && worldScreen.bottomUnitTable.selectedUnit?.run { due && isIdle() } == true
        pack()
    }

}
