package com.unciv.ui.screens.worldscreen.unit.actions

import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.actions.UnitActions
import com.unciv.models.UncivSound
import com.unciv.models.UnitAction
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.getPlaceholderParameters
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
        unitAction = unitAction, action = unitAction.action
    )

    fun getIcon(size: Float = 20f): Actor {
        uiType.imageGetter?.let { return it.invoke() }
        val associatedUnique = unitAction?.associatedUnique
        return when (uiType) {
            UiUnitActionType.CreateImprovement -> {
                ImageGetter.getImprovementPortrait(title.getPlaceholderParameters()[0], size)
            }
            UiUnitActionType.SpreadReligion -> {
                val religionName = title.getPlaceholderParameters()[0]
                ImageGetter.getReligionPortrait(
                    if (ImageGetter.religionIconExists(religionName)) religionName
                    else "Pantheon", size
                )
            }
            UiUnitActionType.TriggerUnique -> {
                when (associatedUnique?.type) {
                    UniqueType.OneTimeEnterGoldenAge, UniqueType.OneTimeEnterGoldenAgeTurns -> ImageGetter.getUnitActionPortrait("StartGoldenAge", size)
                    UniqueType.GainFreeBuildings, UniqueType.RemoveBuilding, UniqueType.OneTimeSellBuilding, UniqueType.OneTimeFreeUnit, UniqueType.FreeSpecificBuildings -> ImageGetter.getConstructionPortrait(associatedUnique.params[0], size)
                    UniqueType.OneTimeAmountFreeUnits -> ImageGetter.getConstructionPortrait(associatedUnique.params[1], size)
                    UniqueType.OneTimeFreePolicy, UniqueType.OneTimeAmountFreePolicies, UniqueType.OneTimeAdoptPolicyOrBelief, UniqueType.OneTimeRemovePolicy, UniqueType.OneTimeRemovePolicyRefund -> ImageGetter.getUnitActionPortrait("HurryPolicy", size)
                    UniqueType.OneTimeRevealEntireMap, UniqueType.OneTimeRevealSpecificMapTiles, UniqueType.OneTimeRevealCrudeMap -> ImageGetter.getUnitActionPortrait("Explore", size)
                    UniqueType.OneTimeConsumeResources, UniqueType.OneTimeProvideResources, UniqueType.OneTimeGainResource -> ImageGetter.getResourcePortrait(associatedUnique.params[1], size)
                    UniqueType.OneTimeChangeTerrain -> ImageGetter.getUnitActionPortrait("Transform", size)
                    UniqueType.OneTimeAddResource -> ImageGetter.getResourcePortrait(associatedUnique.params[0], size)
                    UniqueType.OneTimeRemoveResourcesFromTile, UniqueType.OneTimeRemoveImprovementsFromTile -> ImageGetter.getUnitActionPortrait("Pillage", size)
                    UniqueType.OneTimeGainPopulation, UniqueType.OneTimeGainPopulationRandomCity -> ImageGetter.getStatIcon("Population", size)
                    UniqueType.OneTimeGainStat -> ImageGetter.getStatIcon(associatedUnique.params[1], size)
                    UniqueType.OneTimeGainStatRange -> ImageGetter.getStatIcon(associatedUnique.params[2], size)
                    UniqueType.OneTimeUnitHeal -> ImageGetter.getPromotionPortrait("Heal Instantly", size)
                    UniqueType.OneTimeUnitGainXP, UniqueType.OneTimeSpiesLevelUp -> ImageGetter.getUnitActionPortrait("Promote", size)
                    UniqueType.OneTimeUnitUpgrade, UniqueType.OneTimeUnitSpecialUpgrade -> ImageGetter.getUnitActionPortrait("Upgrade", size)
                    UniqueType.UnitsGainPromotion, UniqueType.OneTimeUnitGainPromotion, UniqueType.OneTimeUnitRemovePromotion, UniqueType.OneTimeUnitGainStatus, UniqueType.OneTimeUnitLoseStatus -> ImageGetter.getPromotionPortrait(associatedUnique.params[1], size)
                    UniqueType.OneTimeFreeBelief, UniqueType.OneTimeGainPantheon, UniqueType.OneTimeGainProphet -> ImageGetter.getUnitActionPortrait("EnhanceReligion", size)
                    UniqueType.OneTimeUnitDamage -> ImageGetter.getUnitActionPortrait("Pillage", size)
                    UniqueType.FreeStatBuildings -> ImageGetter.getUnitActionPortrait("HurryConstruction", size)
                    UniqueType.TriggerEvent -> ImageGetter.getUniquePortrait(associatedUnique.params[0], size)
                    UniqueType.OneTimeUnitGainMovement -> ImageGetter.getUnitActionPortrait("MoveTo", size)
                    UniqueType.OneTimeUnitLoseMovement -> ImageGetter.getUnitActionPortrait("StopMove", size)
                    UniqueType.OneTimeUnitDestroyed -> ImageGetter.getUnitActionPortrait("DisbandUnit", size)
                    UniqueType.OneTimeFreeTechRuins, UniqueType.OneTimeAmountFreeTechs, UniqueType.OneTimeFreeTech -> ImageGetter.getUnitActionPortrait("HurryResearch", size)
                    UniqueType.OneTimeGainTechPercent -> ImageGetter.getTechIconPortrait(associatedUnique.params[1], size)
                    UniqueType.OneTimeDiscoverTech -> ImageGetter.getTechIconPortrait(associatedUnique.params[0], size)
                    else -> ImageGetter.getUnitActionPortrait("Star", size)
                }
            }
            else -> ImageGetter.getUnitActionPortrait("Star", size)
        }
    }
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

private fun portrait(name: String): () -> Actor = { ImageGetter.getUnitActionPortrait(name) }

/**
 *  The unit action types the UI knows about, with all their UI properties: icon, sound, key binding and page.
 *  Most are backed by a logic [UnitActionType] (taking [value] from there), the UI-only ones (see [UiOnlyUnitActions]) are not.
 *  Types whose page depends on the unit's state override [getPage].
 *
 *  Note for creators of new UI-only actions: [value] must be a translation template (`TranslationTests.allUnitActionsHaveTemplate` checks that).
 *
 *  @param type         the backing logic type, `null` for UI-only
 *  @param value        _default_ label to display, can be overridden in [UiUnitAction] instantiation
 *  @param imageGetter  optional lambda to get an Icon - `null` if icon is dependent on outside factors and needs special handling (see [UiUnitAction.getIcon])
 *  @param isSkippingToNextUnit if "Auto Unit Cycle" setting and this bit are on, this action will skip to the next unit
 *  @param uncivSound   sound played on activation
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
    StopEscortFormation(UnitActionType.StopEscortFormation, portrait("StopEscort"), false, page = 1),
    EscortFormation(UnitActionType.EscortFormation, portrait("Escort"), false, page = 1),
    SwapUnits("Swap units", portrait("Swap"), false),
    Automate(UnitActionType.Automate, portrait("Automate")) {
        override fun getPage(unit: MapUnit) =
            if (unit.cache.hasUniqueToBuildImprovements || unit.hasUnique(UniqueType.AutomationPrimaryAction)) 0 else 1
    },
    ConnectRoad("Connect road", portrait("RoadConnection"), false),
    StopAutomation(UnitActionType.StopAutomation, portrait("Stop"), false),
    StopMovement(UnitActionType.StopMovement, portrait("StopMove"), false),
    ShowUnitDestination("Show unit destination", portrait("ShowUnitDestination"), false, page = 1),
    Sleep(UnitActionType.Sleep, portrait("Sleep")) {
        // Sleep moves to second page if current action is SleepUntilHealed or if unit is wounded and it's not already the current action
        override fun getPage(unit: MapUnit) =
            if (unit.isSleepingUntilHealed() || unit.health < 100 && !(unit.isSleeping() && !unit.isActionUntilHealed())) 1 else 0
    },
    SleepUntilHealed(UnitActionType.SleepUntilHealed, portrait("Sleep")) {
        // SleepUntilHealed only moves to the second page if Sleep is the current action
        override fun getPage(unit: MapUnit) =
            if (unit.isSleeping() && !unit.isActionUntilHealed()) 1 else 0
    },
    Fortify(UnitActionType.Fortify, portrait("Fortify"), uncivSound = UncivSound.Fortify) {
        // Fortify moves to second page if current action is FortifyUntilHealed or if unit is wounded and it's not already the current action
        override fun getPage(unit: MapUnit) =
            if (unit.isFortifyingUntilHealed() || unit.health < 100 && !(unit.isFortified() && !unit.isActionUntilHealed())) 1 else 0
    },
    FortifyUntilHealed(UnitActionType.FortifyUntilHealed, portrait("FortifyUntilHealed"), uncivSound = UncivSound.Fortify) {
        // FortifyUntilHealed only moves to the second page if Fortify is the current action
        override fun getPage(unit: MapUnit) =
            if (unit.isFortified() && !unit.isActionUntilHealed()) 1 else 0
    },
    Guard(UnitActionType.Guard, portrait("Guard"), uncivSound = UncivSound.Fortify),
    Explore(UnitActionType.Explore, portrait("Explore")) {
        override fun getPage(unit: MapUnit) = if (unit.isCivilian()) 1 else 0
    },
    StopExploration(UnitActionType.StopExploration, portrait("Stop"), false),
    Promote("Promote", portrait("Promote"), false, UncivSound.Promote),
    Upgrade(UnitActionType.Upgrade, portrait("Upgrade"), uncivSound = UncivSound.Upgrade),
    Transform(UnitActionType.Transform, portrait("Transform"), uncivSound = UncivSound.Upgrade),
    Pillage(UnitActionType.Pillage, portrait("Pillage"), false),
    Paradrop(UnitActionType.Paradrop, portrait("Paradrop"), false),
    AirSweep(UnitActionType.AirSweep, portrait("AirSweep"), false),
    SetUp(UnitActionType.SetUp, portrait("SetUp"), false, UncivSound.Setup),
    FoundCity(UnitActionType.FoundCity, portrait("FoundCity"), uncivSound = UncivSound.Chimes),
    ConstructImprovement("Construct improvement", portrait("ConstructImprovement"), false),
    Repair(UnitActionType.Repair, portrait("Repair"), uncivSound = UncivSound.Construction),
    CreateImprovement(UnitActionType.CreateImprovement, null, false, UncivSound.Chimes),
    HurryResearch(UnitActionType.HurryResearch, portrait("HurryResearch"), uncivSound = UncivSound.Chimes),
    HurryPolicy(UnitActionType.HurryPolicy, portrait("HurryPolicy"), uncivSound = UncivSound.Chimes),
    HurryWonder(UnitActionType.HurryWonder, portrait("HurryConstruction"), uncivSound = UncivSound.Chimes),
    HurryBuilding(UnitActionType.HurryBuilding, portrait("HurryConstruction"), uncivSound = UncivSound.Chimes),
    ConductTradeMission(UnitActionType.ConductTradeMission, portrait("ConductTradeMission"), uncivSound = UncivSound.Chimes),
    FoundReligion(UnitActionType.FoundReligion, portrait("FoundReligion"), uncivSound = UncivSound.Choir),
    TriggerUnique(UnitActionType.TriggerUnique, null, false, UncivSound.Chimes),
    SpreadReligion(UnitActionType.SpreadReligion, null, uncivSound = UncivSound.Choir),
    RemoveHeresy(UnitActionType.RemoveHeresy, portrait("RemoveHeresy"), uncivSound = UncivSound.Fire),
    EnhanceReligion(UnitActionType.EnhanceReligion, portrait("EnhanceReligion"), uncivSound = UncivSound.Choir),
    DisbandUnit(UnitActionType.DisbandUnit, portrait("DisbandUnit"), false, page = 1),
    GiftUnit(UnitActionType.GiftUnit, portrait("Present"), uncivSound = UncivSound.Silent, page = 1),
    Skip(UnitActionType.Skip, portrait("Skip"), uncivSound = UncivSound.Silent),
    ShowAdditionalActions("Show more", portrait("ShowMore"), false),
    HideAdditionalActions("Back", portrait("HideMore"), false, page = 1),
    AddInCapital(UnitActionType.AddInCapital, portrait("AddInCapital"), uncivSound = UncivSound.Chimes),
    ;

    /** For types backed by a logic [UnitActionType] */
    constructor(type: UnitActionType, imageGetter: (() -> Actor)?, isSkippingToNextUnit: Boolean = true, uncivSound: UncivSound = UncivSound.Click, page: Int = 0)
        : this(type, type.value, imageGetter, isSkippingToNextUnit, uncivSound, page)

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
