package com.unciv.ui.screens.worldscreen.unit.actions

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.UncivGame
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.UpgradeUnitAction
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.popups.UnitUpgradeMenu
import com.unciv.ui.screens.worldscreen.WorldScreen

class UnitActionsTable(val worldScreen: WorldScreen) : Table() {
    /** Distribute UnitActions on "pages" */
    private var currentPage = 0
    private var buttonsPerPage = Int.MAX_VALUE
    private var numPages = 2
    private var shownForUnitHash = 0
    private var animationNotFinished = false

    companion object {
        /** Maximum for how many pages there can be. ([minButtonsPerPage]-1)*[maxAllowedPages]
         *  is the upper bound for how many actions a unit can display. */
        private const val maxAllowedPages = 10
        /** Lower bound for how many buttons to distribute per page, including navigation buttons.
         *  Affects really cramped displays. */
        // less than navigation buttons + 2 makes little sense, and setting it to 4 isn't necessary.
        private const val minButtonsPerPage = 3
        /** Upper bound for how many buttons to distribute per page, including navigation buttons.
         *  Affects large displays, resulting in more map visible between the actions and tech/diplo/policy buttons. */
        private const val maxButtonsPerPage = 7
        /** Maximum number of buttons to present without paging, overriding page preferences (implementation currently limited to merging two pages) */
        private const val maxSinglePageButtons = 5
        /** Padding between and to the left of the Buttons */
        private const val padBetweenButtons = 2f
    }

    init {
        defaults().left().padLeft(padBetweenButtons).padBottom(padBetweenButtons)
    }

    fun changePage(delta: Int, unit: MapUnit) {
        if (delta == 0 || numPages <= 1) return
        currentPage = (currentPage + delta) % numPages
        update(unit)
    }

    fun update(unit: MapUnit?) {
        val newUnitHash = unit?.hashCode() ?: 0
        var unitChanged = false
        if (shownForUnitHash != newUnitHash) {
            unitChanged = true
            currentPage = 0
            shownForUnitHash = newUnitHash
        }

        clear()
        keyShortcuts.clear()
        if (unit == null) return
        if (!worldScreen.canChangeState) return // No actions when it's not your turn or spectator!

        numPages = 0
        val pageActionBuckets = Array<ArrayDeque<UnitAction>>(maxAllowedPages) { ArrayDeque() }
        fun freeSlotsOnPage(page: Int) = buttonsPerPage -
            pageActionBuckets[page].size -
            (if (numPages > 1) 1 else 0) // room for the navigation buttons

        val (nextPageAction, previousPageAction) = UnitActions.getPagingActions(unit, this)
        val nextPageButton = getUnitActionButton(unit, nextPageAction)
        val previousPageButton = getUnitActionButton(unit, previousPageAction)
        updateButtonsPerPage(nextPageButton)

        val sortedUnitActions = UnitActions.getUnitActions(unit).sortedByDescending { it.useFrequency }
        // Distribute sequentially into the buckets
        for (unitAction in sortedUnitActions) {
            var actionPage = UnitActions.getActionDefaultPage(unit, unitAction.type)
            while (actionPage < maxAllowedPages && freeSlotsOnPage(actionPage) <= 0)
                actionPage++
            if (actionPage >= maxAllowedPages) break
            if (actionPage >= numPages) numPages = actionPage + 1
            pageActionBuckets[actionPage].addLast(unitAction)
        }
        // Due to room reserved for paging buttons changing, buckets may now be too full
        for (page in 0 until maxAllowedPages - 1) {
            while (freeSlotsOnPage(page) < 0) {
                val element = pageActionBuckets[page].removeLast()
                pageActionBuckets[page + 1].addFirst(element)
                if (numPages < page + 2) numPages = page + 2
            }
        }
        // Special case: Only the default two pages used and all actions would fit in one
        if (numPages == 2 && buttonsPerPage >= maxSinglePageButtons && pageActionBuckets[0].size + pageActionBuckets[1].size <= maxSinglePageButtons) {
            pageActionBuckets[0].addAll(pageActionBuckets[1])
            pageActionBuckets[1].clear()
            numPages = 1
        }

        // clamp currentPage
        if (currentPage !in 0 until numPages) currentPage = 0
        // actually show the buttons of the currentPage
        for (unitAction in pageActionBuckets[currentPage]) {
            val button = getUnitActionButton(unit, unitAction)
            if (unitAction is UpgradeUnitAction) {
                // This is bound even when the button is disabled, but Actor.activate in ActivationExtensions will block any activation for disabled actors...
                // But the menu is built to be useful even when you can't upgrade - so **hack** it to get the handler through.
                // Works because our disable() extension also changes style, and because the normal click is ignored due to unitAction.action being null.
                button.isDisabled = false
                button.touchable = Touchable.enabled
                button.onRightClick {
                    UnitUpgradeMenu(worldScreen.stage, button, unit, unitAction, enable = unitAction.action != null, callbackAfterAnimation = true) {
                        worldScreen.shouldUpdate = true
                    }
                }
            }
            add(button).colspan(2).row()
        }

        // show page navigation
        if (currentPage > 0)
            add(previousPageButton)
        if (currentPage < numPages - 1)
            add(nextPageButton)
        
        // Animate buttons
        if (UncivGame.Current.settings.experimentalUIAnimations
            && UncivGame.Current.settings.unitActionsTableAnimation) {
            if (unitChanged || animationNotFinished) {
                val buttons = children.filterIsInstance<Button>()
                var delay = 0f
                for (button in buttons) {
                    animateButton(button, delay)
                    delay += 0.05f
                }
            }
        }

        pack()

        // Bind all currently invisible actions to their keys
        for (page in pageActionBuckets.indices) {
            if (page == currentPage) continue // these are already done
            for (unitAction in pageActionBuckets[page]) {
                if (unitAction.action == null) continue
                keyShortcuts.add(unitAction.type.binding) {
                    activateAction(unitAction, unit)
                }
            }
        }
    }

    private fun updateButtonsPerPage(button: Button) {
        val upperLimit = worldScreen.techPolicyAndDiplomacy.y
        val lowerLimit = this.y
        val availableHeight = upperLimit - lowerLimit - padBetweenButtons
        val buttonHeight = button.height + padBetweenButtons
        buttonsPerPage = (availableHeight / buttonHeight).toInt().coerceIn(minButtonsPerPage, maxButtonsPerPage)
    }

    private fun getUnitActionButton(unit: MapUnit, unitAction: UnitAction): Button {
        val icon = unitAction.getIcon()
        // If peripheral keyboard not detected, hotkeys will not be displayed
        val binding = unitAction.type.binding

        val fontColor = if (unitAction.isCurrentAction) Color.YELLOW else Color.WHITE
        val actionButton = IconTextButton(unitAction.title, icon, fontColor = fontColor)
        actionButton.labelCell.padTop(0f) // aligned with icon 

        if (unitAction.type == UnitActionType.Promote && unitAction.action != null)
            actionButton.color = Color.GREEN.brighten(0.5f)

        actionButton.pack()

        if (unitAction.action == null) {
            actionButton.disable()
        } else {
            actionButton.onActivation(unitAction.uncivSound, binding) {
                activateAction(unitAction, unit)
            }
        }

        return actionButton
    }

    private fun activateAction(unitAction: UnitAction, unit: MapUnit) {
        unitAction.action!!.invoke()
        worldScreen.shouldUpdate = true
        // We keep the unit action/selection overlay from the previous unit open even when already selecting another unit
        // so you need less clicks/touches to do things, but once we do an action with the new unit, we want to close this
        // overlay, since the user definitely wants to interact with the new unit.
        worldScreen.mapHolder.removeUnitActionOverlay()
        if (!UncivGame.Current.settings.autoUnitCycle) return
        if (unit.isDestroyed || 
            unitAction.type.isSkippingToNextUnit && (!unit.isMoving() || !unit.hasMovement()))
            worldScreen.switchToNextUnit()
        else worldScreen.bottomUnitTable.shouldUpdate = true
    }

    private fun animateButton(button: Button, delay: Float) {
        button.color.a = 0f
        button.addAction(Actions.sequence(
            Actions.run { animationNotFinished = true },
            Actions.run {
                button.addAction(Actions.moveBy(-150f, 0f))
                button.addAction(Actions.alpha(1f, 0.15f))
                button.addAction(Actions.delay(delay, Actions.moveBy(150f, 0f, 0.15f, Interpolation.smooth)))
            },
            Actions.run { animationNotFinished = false }
        ))
    }
}
