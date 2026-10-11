package com.unciv.ui.screens.worldscreen.unit.actions

import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.actions.UnitActionModifiers
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
    val useFrequency: Float = uiType.defaultUseFrequency,
    val title: String = uiType.value,
    val isCurrentAction: Boolean = false,
    val uncivSound: UncivSound = uiType.uncivSound,
    val unitAction: UnitAction? = null,
    val action: (() -> Unit)? = null
) {
    constructor(unit: MapUnit, unitAction: UnitAction) : this(
        UiUnitActionType.of(unitAction.type), UiUnitActionType.of(unitAction.type).getUseFrequency(unit, unitAction),
        unitAction.title, unitAction.isCurrentAction, unitAction = unitAction, action = unitAction.action
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
        UnitActions.getUnitActions(unit).map { UiUnitAction(unit, it) } + UiOnlyUnitActions.getUnitActions(unit)

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
    private val page: Int,
    val defaultUseFrequency: Float
) {
    StopEscortFormation(UnitActionType.StopEscortFormation, portrait("StopEscort"), false, page = 1, useFrequency = 50f),
    EscortFormation(UnitActionType.EscortFormation, portrait("Escort"), false, page = 1, useFrequency = 50f),
    SwapUnits("Swap units", portrait("Swap"), false, useFrequency = 60f),
    Automate(UnitActionType.Automate, portrait("Automate"), useFrequency = 25f) {
        override fun getPage(unit: MapUnit) =
            if (unit.cache.hasUniqueToBuildImprovements || unit.hasUnique(UniqueType.AutomationPrimaryAction)) 0 else 1
    },
    ConnectRoad("Connect road", portrait("RoadConnection"), false, useFrequency = 25f),
    StopAutomation(UnitActionType.StopAutomation, portrait("Stop"), false, useFrequency = 10f),
    StopMovement(UnitActionType.StopMovement, portrait("StopMove"), false, useFrequency = 20f),
    ShowUnitDestination("Show unit destination", portrait("ShowUnitDestination"), false, page = 1, useFrequency = 30f),
    Sleep(UnitActionType.Sleep, portrait("Sleep"), useFrequency = 29f) {
        // Sleep moves to second page if current action is SleepUntilHealed or if unit is wounded and it's not already the current action
        override fun getPage(unit: MapUnit) =
            if (unit.isSleepingUntilHealed() || unit.health < 100 && !(unit.isSleeping() && !unit.isActionUntilHealed())) 1 else 0
        override fun getDefaultUseFrequency(unit: MapUnit, unitAction: UnitAction?) = if (unit.isSleeping()) 21f else defaultUseFrequency
    },
    SleepUntilHealed(UnitActionType.SleepUntilHealed, portrait("Sleep"), useFrequency = 44f) {
        // SleepUntilHealed only moves to the second page if Sleep is the current action
        override fun getPage(unit: MapUnit) =
            if (unit.isSleeping() && !unit.isActionUntilHealed()) 1 else 0
        override fun getDefaultUseFrequency(unit: MapUnit, unitAction: UnitAction?) = if (unit.isSleepingUntilHealed()) 20f else defaultUseFrequency
    },
    Fortify(UnitActionType.Fortify, portrait("Fortify"), uncivSound = UncivSound.Fortify, useFrequency = 30f) {
        // Fortify moves to second page if current action is FortifyUntilHealed or if unit is wounded and it's not already the current action
        override fun getPage(unit: MapUnit) =
            if (unit.isFortifyingUntilHealed() || unit.health < 100 && !(unit.isFortified() && !unit.isActionUntilHealed())) 1 else 0
        override fun getDefaultUseFrequency(unit: MapUnit, unitAction: UnitAction?) = if (unitAction?.isCurrentAction == true) 10f else defaultUseFrequency
    },
    FortifyUntilHealed(UnitActionType.FortifyUntilHealed, portrait("FortifyUntilHealed"), uncivSound = UncivSound.Fortify, useFrequency = 45f) {
        // FortifyUntilHealed only moves to the second page if Fortify is the current action
        override fun getPage(unit: MapUnit) =
            if (unit.isFortified() && !unit.isActionUntilHealed()) 1 else 0
        override fun getDefaultUseFrequency(unit: MapUnit, unitAction: UnitAction?) = if (unitAction?.isCurrentAction == true) 10f else defaultUseFrequency
    },
    Guard(UnitActionType.Guard, portrait("Guard"), uncivSound = UncivSound.Fortify, useFrequency = 0f),
    Explore(UnitActionType.Explore, portrait("Explore"), useFrequency = 5f) {
        override fun getPage(unit: MapUnit) = if (unit.isCivilian()) 1 else 0
    },
    StopExploration(UnitActionType.StopExploration, portrait("Stop"), false, useFrequency = 20f),
    Promote("Promote", portrait("Promote"), false, UncivSound.Promote, useFrequency = 150f),
    Upgrade(UnitActionType.Upgrade, portrait("Upgrade"), uncivSound = UncivSound.Upgrade, useFrequency = 120f),
    Transform(UnitActionType.Transform, portrait("Transform"), uncivSound = UncivSound.Upgrade, useFrequency = 70f),
    Pillage(UnitActionType.Pillage, portrait("Pillage"), false, useFrequency = 65f),
    Paradrop(UnitActionType.Paradrop, portrait("Paradrop"), false, useFrequency = 60f),
    AirSweep(UnitActionType.AirSweep, portrait("AirSweep"), false, useFrequency = 90f),
    SetUp(UnitActionType.SetUp, portrait("SetUp"), false, UncivSound.Setup, useFrequency = 85f),
    FoundCity(UnitActionType.FoundCity, portrait("FoundCity"), uncivSound = UncivSound.Chimes, useFrequency = 80f),
    ConstructImprovement("Construct improvement", portrait("ConstructImprovement"), false, useFrequency = 85f),
    Repair(UnitActionType.Repair, portrait("Repair"), uncivSound = UncivSound.Construction, useFrequency = 90f),
    CreateImprovement(UnitActionType.CreateImprovement, null, false, UncivSound.Chimes, useFrequency = 85f) {
        override fun getDefaultUseFrequency(unit: MapUnit, unitAction: UnitAction?) =
            if (unitAction?.associatedUnique?.type == UniqueType.CreateWaterImprovements) 82f else defaultUseFrequency
    },
    HurryResearch(UnitActionType.HurryResearch, portrait("HurryResearch"), uncivSound = UncivSound.Chimes, useFrequency = 76f),
    HurryPolicy(UnitActionType.HurryPolicy, portrait("HurryPolicy"), uncivSound = UncivSound.Chimes, useFrequency = 76f),
    HurryWonder(UnitActionType.HurryWonder, portrait("HurryConstruction"), uncivSound = UncivSound.Chimes, useFrequency = 75f),
    HurryBuilding(UnitActionType.HurryBuilding, portrait("HurryConstruction"), uncivSound = UncivSound.Chimes, useFrequency = 75f),
    ConductTradeMission(UnitActionType.ConductTradeMission, portrait("ConductTradeMission"), uncivSound = UncivSound.Chimes, useFrequency = 70f),
    FoundReligion(UnitActionType.FoundReligion, portrait("FoundReligion"), uncivSound = UncivSound.Choir, useFrequency = 80f),
    TriggerUnique(UnitActionType.TriggerUnique, null, false, UncivSound.Chimes, useFrequency = 80f),
    SpreadReligion(UnitActionType.SpreadReligion, null, uncivSound = UncivSound.Choir, useFrequency = 68f),
    RemoveHeresy(UnitActionType.RemoveHeresy, portrait("RemoveHeresy"), uncivSound = UncivSound.Fire, useFrequency = 69f),
    EnhanceReligion(UnitActionType.EnhanceReligion, portrait("EnhanceReligion"), uncivSound = UncivSound.Choir, useFrequency = 79f),
    DisbandUnit(UnitActionType.DisbandUnit, portrait("DisbandUnit"), false, page = 1, useFrequency = 0f),
    GiftUnit(UnitActionType.GiftUnit, portrait("Present"), uncivSound = UncivSound.Silent, page = 1, useFrequency = 5f) {
        override fun getDefaultUseFrequency(unit: MapUnit, unitAction: UnitAction?) =
            if (unitAction?.action == null) 1f else defaultUseFrequency
    },
    Skip(UnitActionType.Skip, portrait("Skip"), uncivSound = UncivSound.Silent, useFrequency = 0f),
    ShowAdditionalActions("Show more", portrait("ShowMore"), false, useFrequency = 0f),
    HideAdditionalActions("Back", portrait("HideMore"), false, page = 1, useFrequency = 0f),
    AddInCapital(UnitActionType.AddInCapital, portrait("AddInCapital"), uncivSound = UncivSound.Chimes, useFrequency = 80f),
    ;

    /** For types backed by a logic [UnitActionType] */
    constructor(type: UnitActionType, imageGetter: (() -> Actor)?, isSkippingToNextUnit: Boolean = true, uncivSound: UncivSound = UncivSound.Click, page: Int = 0, useFrequency: Float)
        : this(type, type.value, imageGetter, isSkippingToNextUnit, uncivSound, page, useFrequency)

    /** For UI-only types */
    constructor(value: String, imageGetter: (() -> Actor)?, isSkippingToNextUnit: Boolean = true, uncivSound: UncivSound = UncivSound.Click, page: Int = 0, useFrequency: Float)
        : this(null, value, imageGetter, isSkippingToNextUnit, uncivSound, page, useFrequency)

    /** Keyboard binding of the same name, if any - see the note in [KeyboardBinding] */
    val binding: KeyboardBinding =
        KeyboardBinding.entries.firstOrNull { it.name == name } ?: KeyboardBinding.None

    /** The use frequency for this action type, before [UnitActionModifiers.getUseFrequency] applies the modifiers of the action's unique. */
    @Readonly
    open fun getDefaultUseFrequency(unit: MapUnit, unitAction: UnitAction?): Float = defaultUseFrequency

    /**
     *  How often this action is used, a higher value means more often and that it should be on an earlier page.
     *  100 is very frequent, 50 is somewhat frequent, less than 25 is press one time for multi-turn movement.
     *  A Rare case is > 100 if a button is something like add in capital, promote or something,
     *  we need to inform the player that taking the action is an option.
     */
    @Readonly
    fun getUseFrequency(unit: MapUnit, unitAction: UnitAction?): Float =
        UnitActionModifiers.getUseFrequency(unit, unitAction?.associatedUnique, getDefaultUseFrequency(unit, unitAction))

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
