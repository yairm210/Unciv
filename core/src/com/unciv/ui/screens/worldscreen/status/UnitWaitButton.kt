package com.unciv.ui.screens.worldscreen.status

import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.worldscreen.WorldScreen

class UnitWaitButton(
    private val worldScreen: WorldScreen,
    private val statusButtons: StatusButtons
) : IconTextButton("Wait", ImageGetter.getUnitActionPortrait("Wait")) {

    init {
        onActivation { 
            worldScreen.switchToNextUnit(resetDue = true)
        }
        // Let unit actions override this for command "Wait".
        keyShortcuts.add(KeyboardBinding.Wait, -99)
    }

    fun update() {
        val nextTurnButton = statusButtons.nextTurnButton
        val visible = nextTurnButton.isVisible
            && nextTurnButton.isNextUnitAction()
            && worldScreen.bottomUnitTable.selectedUnit?.run { due && hasMovement() } == true
        statusButtons.unitWaitButton = if (visible) this else null
        isEnabled = nextTurnButton.isEnabled
        if (isEnabled) addTooltip(KeyboardBinding.Wait) else addTooltip("")
        pack()
    }

}
