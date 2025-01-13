package com.unciv.ui.screens.worldscreen.status

import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
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
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.status.NextTurnAction.Default
import com.unciv.ui.screens.worldscreen.status.NextTurnAction.NextTurn
import com.unciv.ui.screens.worldscreen.status.NextTurnAction.NextUnit
import com.unciv.utils.Concurrency

class NextTurnButton(
    private val worldScreen: WorldScreen
) : IconTextButton("", null, 30) {
    private var nextTurnAction = Default
    private val unitsDueLabel = Label("", BaseScreen.skin)
    private val unitsDueCell: Cell<Label>
    init {
        pad(15f)
        onActivation { nextTurnAction.action(worldScreen) }
        onRightClick { NextTurnMenu(stage, this, this, worldScreen) }
        keyShortcuts.add(KeyboardBinding.NextTurn)
        keyShortcuts.add(KeyboardBinding.NextTurnAlternate)
        labelCell.row()
        unitsDueCell = add(unitsDueLabel).padTop(6f).colspan(2).center()
    }

    fun update() {
        nextTurnAction = getNextTurnAction(worldScreen)
        updateButton(nextTurnAction)
        val autoPlay = worldScreen.autoPlay
        if (autoPlay.shouldContinueAutoPlaying() && worldScreen.isPlayersTurn
            && !worldScreen.waitingForAutosave && !worldScreen.isNextTurnUpdateRunning()) {
            autoPlay.runAutoPlayJobInNewThread("MultiturnAutoPlay", worldScreen, false) {
                TurnManager(worldScreen.viewingCiv).automateTurn()
                Concurrency.runOnGLThread { worldScreen.nextTurn() }
                autoPlay.endTurnMultiturnAutoPlay()
            }
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
        
        // Show due units in next-unit and next-turn phase, encouraging the player to give order to
        // idle units.
        // It also serves to inform new players that the NextUnit-Button cycles units. That's easy
        // to grasp, because the number doesn't change when repeatedly clicking the button.
        // We also show due units on the NextTurn button, so players see due units in case the
        // the NextTurn phase is disabled.
        val count = worldScreen.viewingCiv.units.getDueUnits().count()
        if (count > 0 && nextTurnAction in listOf(NextUnit, NextTurn)) {
            unitsDueLabel.setText("[$count] units due".tr())
            unitsDueCell.setActor(unitsDueLabel)
        }
        else {
            unitsDueCell.clearActor()
        }
        
        pack()
    }

    private fun getNextTurnAction(worldScreen: WorldScreen) =
        // Guaranteed to return a non-null NextTurnAction because the last isChoice always returns true
        NextTurnAction.entries.first { it.isChoice(worldScreen) }
}
