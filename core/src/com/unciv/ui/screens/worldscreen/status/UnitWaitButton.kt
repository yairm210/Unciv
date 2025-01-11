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
    private val nextTurnButton: NextTurnButton
) : IconTextButton("Wait", ImageGetter.getUnitActionPortrait("Wait")) {

    private val originalWidth = width
    private val originalHeight = height
    
    private var isGone: Boolean = true
        set(value) {
            field = value
            isVisible = isGone
            if (isGone) {
                setSize(0f, 0f) // TODO
            } else {
                setSize(originalWidth, originalHeight)
            }
        }
    
    init {
        onActivation { 
            worldScreen.switchToNextUnit(resetDue = true)
        }
        // Let unit actions override this for command "Wait".
        keyShortcuts.add(KeyboardBinding.Wait, -99)
    }

    fun update() {
        isGone = nextTurnButton.isVisible && nextTurnButton.isNextUnitAction()
        isEnabled = nextTurnButton.isEnabled 
        if (isVisible && isEnabled) addTooltip(KeyboardBinding.Wait) else addTooltip("")
        pack()
    }
    
    

}
