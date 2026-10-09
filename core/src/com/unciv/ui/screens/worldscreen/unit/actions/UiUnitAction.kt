package com.unciv.ui.screens.worldscreen.unit.actions

import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.actions.UnitActions
import com.unciv.models.UncivSound
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.images.ImageGetter
import yairm210.purity.annotations.Readonly

/**
 *  UI Unit Action - what the UI works with, instead of calling [UnitActions] directly.
 *
 *  Either wraps a logic [UnitAction] (see the secondary constructor), or is UI-only (see [UiOnlyUnitActions]).
 *
 *  @property unitAction The logic action this was created from, if any
 *  @property action null if the action is currently disabled
 */
class UiUnitAction(
    val uiType: UiUnitActionType,
    val useFrequency: Float,
    val title: String = uiType.value,
    val isCurrentAction: Boolean = false,
    val uncivSound: UncivSound = uiType.uncivSound,
    val unitAction: UnitAction? = null,
    val action: (() -> Unit)? = null
) {
    constructor(unitAction: UnitAction) : this(
        UiUnitActionType.of(unitAction.type), unitAction.useFrequency, unitAction.title, unitAction.isCurrentAction,
        unitAction.uncivSound, unitAction, unitAction.action
    )

    fun getIcon(size: Float = 20f): Actor =
        unitAction?.getIcon(size) ?: uiType.imageGetter?.invoke() ?: ImageGetter.getUnitActionPortrait("Star", size)
}

/** Entry point for the UI to get [UiUnitAction]s */
object UiUnitActions {
    fun getUnitActions(unit: MapUnit): Sequence<UiUnitAction> =
        UnitActions.getUnitActions(unit).map { UiUnitAction(it) } + UiOnlyUnitActions.getUnitActions(unit)

    /** Gets the preferred "page" to display a [UiUnitAction] of type [uiUnitActionType] on, possibly dynamic depending on the state or situation [unit] is in.
     *  Note the returned "page numbers" are treated as suggestions, buttons may get redistributed when screen space is scarce. */
    @Readonly
    fun getActionDefaultPage(unit: MapUnit, uiUnitActionType: UiUnitActionType): Int =
        uiUnitActionType.getPage(unit)

    /** The "paging" actions: [first][Pair.first] page forward, [second][Pair.second] page back. Not part of [getUnitActions]. */
    internal fun getPagingActions(unit: MapUnit, actionsTable: UnitActionsTable): Pair<UiUnitAction, UiUnitAction> =
        UiOnlyUnitActions.getPagingActions(unit, actionsTable)
}

/**
 *  The unit action types the UI knows about, with their UI properties.
 *  Most are backed by a logic [UnitActionType] (taking value, icon, sound from there), the UI-only ones (see [UiOnlyUnitActions]) are not.
 *  Types whose page depends on the unit's state override [getPage].
 *
 *  Note for creators of new UI-only actions: [value] must be a translation template (`TranslationTests.allUnitActionsHaveTemplate` checks that).
 *
 *  @param type         the backing logic type, `null` for UI-only
 *  @param value        _default_ label to display, can be overridden in UiUnitAction instantiation
 *  @param imageGetter  optional lambda to get an Icon - `null` if icon is dependent on outside factors and needs special handling
 *  @param isSkippingToNextUnit if "Auto Unit Cycle" setting and this bit are on, this action will skip to the next unit
 *  @param page         preferred "page", 0-based
 */
enum class UiUnitActionType private constructor(
    val type: UnitActionType?,
    val value: String,
    val imageGetter: (() -> Actor)?,
    val isSkippingToNextUnit: Boolean,
    val uncivSound: UncivSound,
    private val page: Int
) {
    StopEscortFormation(UnitActionType.StopEscortFormation, 1),
    EscortFormation(UnitActionType.EscortFormation, 1),
    SwapUnits("Swap units", { ImageGetter.getUnitActionPortrait("Swap") }, false),
    Automate(UnitActionType.Automate) {
        override fun getPage(unit: MapUnit) =
            if (unit.cache.hasUniqueToBuildImprovements || unit.hasUnique(UniqueType.AutomationPrimaryAction)) 0 else 1
    },
    ConnectRoad("Connect road", { ImageGetter.getUnitActionPortrait("RoadConnection") }, false),
    StopAutomation(UnitActionType.StopAutomation),
    StopMovement(UnitActionType.StopMovement),
    ShowUnitDestination("Show unit destination", { ImageGetter.getUnitActionPortrait("ShowUnitDestination") }, false, page = 1),
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
    Promote("Promote", { ImageGetter.getUnitActionPortrait("Promote") }, false, UncivSound.Promote),
    Upgrade(UnitActionType.Upgrade),
    Transform(UnitActionType.Transform),
    Pillage(UnitActionType.Pillage),
    Paradrop(UnitActionType.Paradrop),
    AirSweep(UnitActionType.AirSweep),
    SetUp(UnitActionType.SetUp),
    FoundCity(UnitActionType.FoundCity),
    ConstructImprovement("Construct improvement", { ImageGetter.getUnitActionPortrait("ConstructImprovement") }, false),
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
    ShowAdditionalActions("Show more", { ImageGetter.getUnitActionPortrait("ShowMore") }, false),
    HideAdditionalActions("Back", { ImageGetter.getUnitActionPortrait("HideMore") }, false, page = 1),
    AddInCapital(UnitActionType.AddInCapital),
    ;

    /** For types backed by a logic [UnitActionType] */
    constructor(type: UnitActionType, page: Int = 0)
        : this(type, type.value, type.imageGetter, type.isSkippingToNextUnit, type.uncivSound, page)

    /** For UI-only types */
    constructor(value: String, imageGetter: (() -> Actor)?, isSkippingToNextUnit: Boolean = true, uncivSound: UncivSound = UncivSound.Click, page: Int = 0)
        : this(null, value, imageGetter, isSkippingToNextUnit, uncivSound, page)

    /** Keyboard binding of the same name, if any - see the note in [KeyboardBinding] */
    val binding: KeyboardBinding =
        KeyboardBinding.entries.firstOrNull { it.name == name } ?: KeyboardBinding.None

    /** The preferred page for this action type, for [unit] in its current state. */
    @Readonly
    open fun getPage(unit: MapUnit): Int = page

    companion object {
        private val byType = entries.filter { it.type != null }.associateBy { it.type }
        @Readonly
        fun forType(type: UnitActionType): UiUnitActionType? = byType[type]
        @Readonly
        fun of(type: UnitActionType): UiUnitActionType =
            forType(type) ?: throw IllegalStateException("UiUnitActionType is missing an entry for $type")
    }
}
