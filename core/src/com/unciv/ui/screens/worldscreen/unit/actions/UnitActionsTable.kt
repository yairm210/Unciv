package com.unciv.ui.screens.worldscreen.unit.actions

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.UpgradeUnitAction
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.popups.UnitUpgradeMenu
import com.unciv.ui.screens.worldscreen.WorldScreen

class UnitActionsTable(val worldScreen: WorldScreen) : Table() {
    /** Distribute UnitActions on "pages" */
    //todo allow keyboard bindings for "other page" actions to work
    //todo left/between padding seems to now be less than before
    //todo First update runs _before_ geometry on WorldScreen is valid - covered by minButtonsPerPage at the moment, and it seems always overridden by a later update
    private var currentPage = 0
    private var buttonsPerPage = Int.MAX_VALUE
    private var numPages = 2

    companion object {
        /** Maximum for how many pages there can be. ([minButtonsPerPage]-2)*[maxAllowedPages]
         *  is the upper bound for how many actions a unit can display. */
        private const val maxAllowedPages = 10
        /** Lower bound for how many buttons to distribute per page, including navigation buttons.
         *  Affects really cramped displays. */
        // less than navigation buttons + 2 makes no sense, and setting it to 5 isn't necessary.
        private const val minButtonsPerPage = 4
        /** Upper bound for how many buttons to distribute per page, including navigation buttons.
         *  Affects large displays, resulting in more map visible between the actions and tech/diplo/policy buttons. */
        private const val maxButtonsPerPage = 7
        /** Maximum number of buttons to present without paging, overriding page preferences (implementation currently limited to merging two pages) */
        private const val maxSinglePageButtons = 5
        /** Vertical padding between the Buttons, not including any padding internal to IconTextButton */
        private const val padBetweenButtons = 2f
    }

    init {
        defaults().left().padBottom(padBetweenButtons)
    }

    fun changePage(delta: Int, unit: MapUnit) {
        if (delta == 0 || numPages <= 1) return
        currentPage = (currentPage + delta) % numPages
        update(unit)
    }

    fun update(unit: MapUnit?) {
        clear()
        if (unit == null) return
        if (!worldScreen.canChangeState) return // No actions when it's not your turn or spectator!

        numPages = 0
        val pageActionBuckets = Array<ArrayDeque<UnitAction>>(maxAllowedPages) { ArrayDeque() }
        fun freeSlotsOnPage(page: Int) = buttonsPerPage -
            pageActionBuckets[page].size -
            (if (page > 0) 1 else 0) - // room for the previousPageButton
            (if (page < numPages - 1) 1 else 0)  // room for the nextPageButton

        val (nextPageAction, previousPageAction) = UnitActions.getPagingActions(unit)
        val nextPageButton = getUnitActionButton(unit, nextPageAction)
        val previousPageButton = getUnitActionButton(unit, previousPageAction)
        updateButtonsPerPage(nextPageButton)

        // Distribute sequentially into the buckets
        for (unitAction in UnitActions.getUnitActions(unit)) {
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

        for (unitAction in pageActionBuckets[currentPage]) {
            val button = getUnitActionButton(unit, unitAction)
            if (unitAction is UpgradeUnitAction) {
                button.onRightClick {
                    UnitUpgradeMenu(worldScreen.stage, button, unit, unitAction, callbackAfterAnimation = true) {
                        worldScreen.shouldUpdate = true
                    }
                }
            }
            add(button).row()
        }
        if (currentPage > 0)
            add(previousPageButton).row()
        if (currentPage < numPages - 1)
            add(nextPageButton).row()
        pack()
    }

    private fun updateButtonsPerPage(button: Button) {
        val upperLimit = if (worldScreen.fogOfWarButton.isVisible)
            worldScreen.fogOfWarButton.y else worldScreen.techPolicyAndDiplomacy.y
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

        if (unitAction.type == UnitActionType.Promote && unitAction.action != null)
            actionButton.color = Color.GREEN.cpy().lerp(Color.WHITE, 0.5f)

        actionButton.pack()

        if (unitAction.action == null) {
            actionButton.disable()
        } else {
            actionButton.onActivation(unitAction.uncivSound, binding) {
                unitAction.action.invoke()
                GUI.setUpdateWorldOnNextRender()
                // We keep the unit action/selection overlay from the previous unit open even when already selecting another unit
                // so you need less clicks/touches to do things, but once we do an action with the new unit, we want to close this
                // overlay, since the user definitely wants to interact with the new unit.
                worldScreen.mapHolder.removeUnitActionOverlay()
                if (UncivGame.Current.settings.autoUnitCycle
                        && (unit.isDestroyed || (unit.isMoving() && unit.currentMovement == 0f && unitAction.type.isSkippingToNextUnit) || (!unit.isMoving() && unitAction.type.isSkippingToNextUnit))) {
                    worldScreen.switchToNextUnit()
                }
            }
        }

         return actionButton
    }

}
