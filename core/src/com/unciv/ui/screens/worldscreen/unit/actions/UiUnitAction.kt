package com.unciv.ui.screens.worldscreen.unit.actions

import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.actions.UnitActions
import com.unciv.models.UncivSound
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import yairm210.purity.annotations.Readonly

/**
 *  UI Unit Action - what the UI works with, instead of calling [UnitActions] directly.
 *
 *  Currently a pass-through of the logic [UnitAction] ([unitAction]); UI-specific behavior (paging, etc.) is meant to be added here.
 */
class UiUnitAction(val unitAction: UnitAction) {
    val type: UnitActionType get() = unitAction.type
    val useFrequency: Float get() = unitAction.useFrequency
    val title: String get() = unitAction.title
    val isCurrentAction: Boolean get() = unitAction.isCurrentAction
    val uncivSound: UncivSound get() = unitAction.uncivSound
    /** null if the action is currently disabled */
    val action: (() -> Unit)? get() = unitAction.action

    fun getIcon(size: Float = 20f): Actor = unitAction.getIcon(size)
}

/** Entry point for the UI to get [UiUnitAction]s */
object UiUnitActions {
    fun getUnitActions(unit: MapUnit): Sequence<UiUnitAction> =
        UnitActions.getUnitActions(unit).map { UiUnitAction(it) }

    @Readonly
    fun getActionDefaultPage(unit: MapUnit, unitActionType: UnitActionType) =
        UnitActions.getActionDefaultPage(unit, unitActionType)

    /** The "paging" actions: [first][Pair.first] page forward, [second][Pair.second] page back. Not part of [getUnitActions]. */
    internal fun getPagingActions(unit: MapUnit, actionsTable: UnitActionsTable): Pair<UiUnitAction, UiUnitAction> {
        val (next, previous) = UnitActions.getPagingActions(unit, actionsTable)
        return UiUnitAction(next) to UiUnitAction(previous)
    }
}
