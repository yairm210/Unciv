package com.unciv.ui.screens.worldscreen.unit.actions

import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.actions.UnitActions
import com.unciv.models.UncivSound
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueType
import yairm210.purity.annotations.Readonly

/**
 *  UI Unit Action - what the UI works with, instead of calling [UnitActions] directly.
 *
 *  Currently a pass-through of the logic [UnitAction] ([unitAction]); UI-specific behavior (paging, etc.) is meant to be added here.
 */
class UiUnitAction(val unitAction: UnitAction) {
    val type: UnitActionType get() = unitAction.type
    val uiType: UiUnitActionType = UiUnitActionType.forType(unitAction.type)
        ?: throw IllegalStateException("UiUnitActionType is missing an entry for ${unitAction.type}")
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

    /** Gets the preferred "page" to display a [UiUnitAction] of type [uiUnitActionType] on, possibly dynamic depending on the state or situation [unit] is in.
     *  Note the returned "page numbers" are treated as suggestions, buttons may get redistributed when screen space is scarce. */
    @Readonly
    fun getActionDefaultPage(unit: MapUnit, uiUnitActionType: UiUnitActionType): Int =
        uiUnitActionType.getPage(unit)

    /** The "paging" actions: [first][Pair.first] page forward, [second][Pair.second] page back. Not part of [getUnitActions]. */
    internal fun getPagingActions(unit: MapUnit, actionsTable: UnitActionsTable): Pair<UiUnitAction, UiUnitAction> {
        val (next, previous) = UnitActions.getPagingActions(unit, actionsTable)
        return UiUnitAction(next) to UiUnitAction(previous)
    }
}

/**
 *  The unit action types the UI knows about (optionally backed by a logic [UnitActionType], for UI-only ones without), with their preferred "page" (0-based, default 0).
 *  Types whose page depends on the unit's state override [getPage].
 */
enum class UiUnitActionType(val type: UnitActionType? = null, private val page: Int = 0) {
    StopEscortFormation(UnitActionType.StopEscortFormation, 1),
    EscortFormation(UnitActionType.EscortFormation, 1),
    SwapUnits(UnitActionType.SwapUnits),
    Automate(UnitActionType.Automate) {
        override fun getPage(unit: MapUnit) =
            if (unit.cache.hasUniqueToBuildImprovements || unit.hasUnique(UniqueType.AutomationPrimaryAction)) 0 else 1
    },
    ConnectRoad(UnitActionType.ConnectRoad),
    StopAutomation(UnitActionType.StopAutomation),
    StopMovement(UnitActionType.StopMovement),
    ShowUnitDestination(UnitActionType.ShowUnitDestination, 1),
    Sleep(UnitActionType.Sleep) {
        // Sleep moves to second page if current action is SleepUntilHealed or if unit is wounded and it's not already the current action
        override fun getPage(unit: MapUnit) =
            if (unit.isSleepingUntilHealed() || unit.health < 100 && !(unit.isSleeping() && !unit.isActionUntilHealed())) 1 else 0
    },
    SleepUntilHealed(UnitActionType.SleepUntilHealed) {
        // SleepUntilHealed only moves to the second page if Sleep is the current action
        override fun getPage(unit: MapUnit) =
            if (unit.isSleeping() && !unit.isActionUntilHealed()) 1 else 0
    },
    Fortify(UnitActionType.Fortify) {
        // Fortify moves to second page if current action is FortifyUntilHealed or if unit is wounded and it's not already the current action
        override fun getPage(unit: MapUnit) =
            if (unit.isFortifyingUntilHealed() || unit.health < 100 && !(unit.isFortified() && !unit.isActionUntilHealed())) 1 else 0
    },
    FortifyUntilHealed(UnitActionType.FortifyUntilHealed) {
        // FortifyUntilHealed only moves to the second page if Fortify is the current action
        override fun getPage(unit: MapUnit) =
            if (unit.isFortified() && !unit.isActionUntilHealed()) 1 else 0
    },
    Guard(UnitActionType.Guard),
    Explore(UnitActionType.Explore) {
        override fun getPage(unit: MapUnit) = if (unit.isCivilian()) 1 else 0
    },
    StopExploration(UnitActionType.StopExploration),
    Promote(UnitActionType.Promote),
    Upgrade(UnitActionType.Upgrade),
    Transform(UnitActionType.Transform),
    Pillage(UnitActionType.Pillage),
    Paradrop(UnitActionType.Paradrop),
    AirSweep(UnitActionType.AirSweep),
    SetUp(UnitActionType.SetUp),
    FoundCity(UnitActionType.FoundCity),
    ConstructImprovement(UnitActionType.ConstructImprovement),
    Repair(UnitActionType.Repair),
    CreateImprovement(UnitActionType.CreateImprovement),
    HurryResearch(UnitActionType.HurryResearch),
    HurryPolicy(UnitActionType.HurryPolicy),
    HurryWonder(UnitActionType.HurryWonder),
    HurryBuilding(UnitActionType.HurryBuilding),
    ConductTradeMission(UnitActionType.ConductTradeMission),
    FoundReligion(UnitActionType.FoundReligion),
    TriggerUnique(UnitActionType.TriggerUnique),
    SpreadReligion(UnitActionType.SpreadReligion),
    RemoveHeresy(UnitActionType.RemoveHeresy),
    EnhanceReligion(UnitActionType.EnhanceReligion),
    DisbandUnit(UnitActionType.DisbandUnit, 1),
    GiftUnit(UnitActionType.GiftUnit, 1),
    Skip(UnitActionType.Skip),
    ShowAdditionalActions(UnitActionType.ShowAdditionalActions),
    HideAdditionalActions(UnitActionType.HideAdditionalActions, 1),
    AddInCapital(UnitActionType.AddInCapital),
    ;

    /** The preferred page for this action type, for [unit] in its current state. */
    @Readonly
    open fun getPage(unit: MapUnit): Int = page

    companion object {
        private val byType = entries.filter { it.type != null }.associateBy { it.type }
        @Readonly
        fun forType(type: UnitActionType): UiUnitActionType? = byType[type]
    }
}
