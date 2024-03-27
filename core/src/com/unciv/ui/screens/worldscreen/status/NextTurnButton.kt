package com.unciv.ui.screens.worldscreen.status

import com.unciv.GUI
import com.unciv.logic.civilization.managers.TurnManager
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.hasOpenPopups
import com.unciv.ui.screens.worldscreen.WorldScreen

class NextTurnButton(
    private val worldScreen: WorldScreen
) : IconTextButton("", null, 30) {
    private var nextTurnAction = NextTurnAction.Default
    init {
//         label.setFontSize(30)
        labelCell.pad(10f)
        onActivation { nextTurnAction.action(worldScreen) }
        onRightClick { NextTurnMenu(stage, this, this, worldScreen) }
        keyShortcuts.add(KeyboardBinding.NextTurn)
        keyShortcuts.add(KeyboardBinding.NextTurnAlternate)
        // Let unit actions override this for command "Wait".
        keyShortcuts.add(KeyboardBinding.Wait, -99)
    }

    fun update() {
        nextTurnAction = getNextTurnAction(worldScreen)
        updateButton(nextTurnAction)
        val settings = GUI.getSettings()
        if (!settings.autoPlay.autoPlayTurnInProgress && settings.autoPlay.isAutoPlaying() && settings.autoPlay.turnsToAutoPlay > 0
            && worldScreen.isPlayersTurn && !worldScreen.waitingForAutosave && !worldScreen.isNextTurnUpdateRunning()) {
            settings.autoPlay.autoPlayTurnInProgress = true
            if (!worldScreen.viewingCiv.isSpectator())
                TurnManager(worldScreen.viewingCiv).automateTurn()
            worldScreen.nextTurn()
            if (!settings.autoPlay.autoPlayUntilEnd)
                settings.autoPlay.turnsToAutoPlay--
            settings.autoPlay.autoPlayTurnInProgress = false
        }
                
        isEnabled = nextTurnAction.getText (worldScreen) == "AutoPlay" 
            || (!worldScreen.hasOpenPopups() && worldScreen.isPlayersTurn
                && !worldScreen.waitingForAutosave && !worldScreen.isNextTurnUpdateRunning())
        if (isEnabled) addTooltip(KeyboardBinding.NextTurn) else addTooltip("")
    }

    internal fun updateButton(nextTurnAction: NextTurnAction) {
        label.setText(nextTurnAction.getText(worldScreen).tr())
        label.color = nextTurnAction.color
        if (nextTurnAction.icon != null && ImageGetter.imageExists(nextTurnAction.icon!!))
            iconCell.setActor(ImageGetter.getImage(nextTurnAction.icon).apply { setSize(30f) })
        else
            iconCell.clearActor()
        pack()
    }

    private fun getNextTurnAction(worldScreen: WorldScreen) =
        // Guaranteed to return a non-null NextTurnAction because the last isChoice always returns true
        NextTurnAction.values().first { it.isChoice(worldScreen) }
}
