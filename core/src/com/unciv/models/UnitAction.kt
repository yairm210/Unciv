package com.unciv.models

import com.badlogic.gdx.scenes.scene2d.Actor
import com.unciv.Constants
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.images.ImageGetter
import com.unciv.models.ruleset.unique.UniqueType


/** Unit Actions - class - carries dynamic data and actual execution.
 * Static properties are in [UnitActionType].
 * Note this is for the buttons offering actions, not the ongoing action stored with a [MapUnit][com.unciv.logic.map.mapunit.MapUnit]
 */
open class UnitAction(
    val type: UnitActionType,
    /** How often this action is used, a higher value means more often and that it should be on an earlier page.
     * 100 is very frequent, 50 is somewhat frequent, less than 25 is press one time for multi-turn movement.
     * A Rare case is > 100 if a button is something like add in capital, promote or something,
     * we need to inform the player that taking the action is an option. */
    val useFrequency: Float,
    val title: String = type.value,
    val isCurrentAction: Boolean = false,
    val uncivSound: UncivSound = type.uncivSound,
    val associatedUnique: Unique? = null,
    /** Action is Null if this unit *can* execute the action but *not right now* - it's embarked, out of moves, etc */
    val action: (() -> Unit)? = null
) {
    fun getIcon(size: Float = 20f): Actor {
        if (type.imageGetter != null)
            return type.imageGetter.invoke()
        return when (type) {
            UnitActionType.CreateImprovement -> {
                ImageGetter.getImprovementPortrait(title.getPlaceholderParameters()[0], size)
            }
            UnitActionType.SpreadReligion -> {
                val religionName = title.getPlaceholderParameters()[0]
                ImageGetter.getReligionPortrait(
                    if (ImageGetter.religionIconExists(religionName)) religionName
                    else "Pantheon", size
                )
            }
            UnitActionType.TriggerUnique -> {
                when (associatedUnique?.type) {
                    UniqueType.OneTimeEnterGoldenAge, UniqueType.OneTimeEnterGoldenAgeTurns -> ImageGetter.getUnitActionPortrait("StartGoldenAge", size)
                    UniqueType.GainFreeBuildings, UniqueType.RemoveBuilding, UniqueType.OneTimeSellBuilding, UniqueType.OneTimeFreeUnit, UniqueType.FreeSpecificBuildings -> ImageGetter.getConstructionPortrait(associatedUnique.params[0], size)
                    UniqueType.OneTimeAmountFreeUnits -> ImageGetter.getConstructionPortrait(associatedUnique.params[1], size)
                    UniqueType.OneTimeFreePolicy, UniqueType.OneTimeAmountFreePolicies, UniqueType.OneTimeAdoptPolicy, UniqueType.OneTimeRemovePolicy, UniqueType.OneTimeRemovePolicyRefund -> ImageGetter.getUnitActionPortrait("HurryPolicy", size)
                    UniqueType.OneTimeRevealEntireMap, UniqueType.OneTimeRevealSpecificMapTiles, UniqueType.OneTimeRevealCrudeMap -> ImageGetter.getUnitActionPortrait("Explore", size)
                    UniqueType.OneTimeConsumeResources, UniqueType.OneTimeProvideResources, UniqueType.OneTimeGainResource -> ImageGetter.getResourcePortrait(associatedUnique.params[1], size)
                    UniqueType.OneTimeChangeTerrain -> ImageGetter.getUnitActionPortrait("Transform", size)
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

    //TODO remove once sure they're unused
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UnitAction) return false

        if (type != other.type) return false
        if (isCurrentAction != other.isCurrentAction) return false
        if (action != other.action) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + isCurrentAction.hashCode()
        result = 31 * result + (action?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "UnitAction(type=$type, title='$title', isCurrentAction=$isCurrentAction)"
    }
}

/** Specialized [UnitAction] for upgrades
 *
 *  Transports [unitToUpgradeTo] from [creation][com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsUpgrade.getUpgradeActions]
 *  to [UI][com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsTable.update]
 */
class UpgradeUnitAction(
    title: String,
    val unitToUpgradeTo: BaseUnit,
    val goldCostOfUpgrade: Int,
    val newResourceRequirements: Counter<String>,
    action: (() -> Unit)?
) : UnitAction(UnitActionType.Upgrade, 120f, title, action = action)

/** Unit Actions - generic enum with static properties
 *
 * @param value         _default_ label to display, can be overridden in UnitAction instantiation
 * @param imageGetter   optional lambda to get an Icon - `null` if icon is dependent on outside factors and needs special handling
 * @param binding       keyboard binding - omitting it will look up the KeyboardBinding of the same name (recommended)
 * @param isSkippingToNextUnit if "Auto Unit Cycle" setting and this bit are on, this action will skip to the next unit
 * @param uncivSound    _default_ sound, can be overridden in UnitAction instantiation
 */

// Note for Creators of new UnitActions: If your action uses a dynamic label overriding UnitActionType.value,
// then you need to teach [com.unciv.testing.TranslationTests.allUnitActionsHaveTranslation] how to deal with it!

enum class UnitActionType(
    val value: String,
    val imageGetter: (()-> Actor)?,
    binding: KeyboardBinding? = null,
    val isSkippingToNextUnit: Boolean = true,
    val uncivSound: UncivSound = UncivSound.Click,
    /** UI "page" preference, 0-based - Dynamic overrides to this are in `UnitActions.actionTypeToPageGetter` */
    val defaultPage: Int
) {
    StopEscortFormation("Stop Escort formation",
        { ImageGetter.getUnitActionPortrait("StopEscort") }, false, defaultPage = 1),
    EscortFormation("Escort formation",
        { ImageGetter.getUnitActionPortrait("Escort") }, false, defaultPage = 1),
    SwapUnits("Swap units",
        { ImageGetter.getUnitActionPortrait("Swap") }, false, defaultPage = 0),
    Automate("Automate",
        { ImageGetter.getUnitActionPortrait("Automate") }),
    ConnectRoad("Connect road",
        { ImageGetter.getUnitActionPortrait("RoadConnection") }, false),
    StopAutomation("Stop automation",
        { ImageGetter.getUnitActionPortrait("Stop") }, false),
    StopMovement("Stop movement",
        { ImageGetter.getUnitActionPortrait("StopMove") }, false),
    ShowUnitDestination("Show unit destination",
        { ImageGetter.getUnitActionPortrait("ShowUnitDestination")}, false, defaultPage = 1),
    Sleep("Sleep",
        { ImageGetter.getUnitActionPortrait("Sleep") }),
    SleepUntilHealed("Sleep until healed",
        { ImageGetter.getUnitActionPortrait("Sleep") }),
    Fortify("Fortify",
        { ImageGetter.getUnitActionPortrait("Fortify") }, UncivSound.Fortify),
    FortifyUntilHealed("Fortify until healed",
        { ImageGetter.getUnitActionPortrait("FortifyUntilHealed") }, UncivSound.Fortify),
    Guard("Guard",
        { ImageGetter.getUnitActionPortrait("Guard") }, UncivSound.Fortify, defaultPage = 0),
    Explore("Explore",
        { ImageGetter.getUnitActionPortrait("Explore") }),
    StopExploration("Stop exploration",
        { ImageGetter.getUnitActionPortrait("Stop") }, false),
    Promote("Promote",
        { ImageGetter.getUnitActionPortrait("Promote") }, false, UncivSound.Promote),
    Upgrade("Upgrade",
        { ImageGetter.getUnitActionPortrait("Upgrade") }, UncivSound.Upgrade),
    Transform("Transform",
        { ImageGetter.getUnitActionPortrait("Transform") }, UncivSound.Upgrade),
    Pillage("Pillage",
        { ImageGetter.getUnitActionPortrait("Pillage") }, false),
    Paradrop("Paradrop",
        { ImageGetter.getUnitActionPortrait("Paradrop") }, false),
    AirSweep("Air Sweep",
        { ImageGetter.getUnitActionPortrait("AirSweep") }, false),
    SetUp("Set up",
        { ImageGetter.getUnitActionPortrait("SetUp") }, false, UncivSound.Setup),
    FoundCity("Found city",
        { ImageGetter.getUnitActionPortrait("FoundCity") }, UncivSound.Silent),
    ConstructImprovement("Construct improvement",
        { ImageGetter.getUnitActionPortrait("ConstructImprovement") }, false),
    Repair(Constants.repair,
        { ImageGetter.getUnitActionPortrait("Repair") }, UncivSound.Construction),
    CreateImprovement("Create",
        null, false, UncivSound.Chimes),
    HurryResearch("{Hurry Research} (${Fonts.death})",
        { ImageGetter.getUnitActionPortrait("HurryResearch") }, UncivSound.Chimes),
    HurryPolicy("{Hurry Policy} (${Fonts.death})",
        { ImageGetter.getUnitActionPortrait("HurryPolicy") }, UncivSound.Chimes),
    HurryWonder("{Hurry Wonder} (${Fonts.death})",
        { ImageGetter.getUnitActionPortrait("HurryConstruction") }, UncivSound.Chimes),
    HurryBuilding("{Hurry Construction} (${Fonts.death})",
        { ImageGetter.getUnitActionPortrait("HurryConstruction") }, UncivSound.Chimes),
    ConductTradeMission("{Conduct Trade Mission} (${Fonts.death})",
        { ImageGetter.getUnitActionPortrait("ConductTradeMission") }, UncivSound.Chimes),
    FoundReligion("Found a Religion",
        { ImageGetter.getUnitActionPortrait("FoundReligion") }, UncivSound.Choir),
    TriggerUnique("Trigger unique",
        null, false, UncivSound.Chimes),
    SpreadReligion("Spread Religion",
        null, UncivSound.Choir),
    RemoveHeresy("Remove Heresy",
        { ImageGetter.getUnitActionPortrait("RemoveHeresy") }, UncivSound.Fire),
    EnhanceReligion("Enhance a Religion",
        { ImageGetter.getUnitActionPortrait("EnhanceReligion") }, UncivSound.Choir),
    DisbandUnit("Disband unit",
        { ImageGetter.getUnitActionPortrait("DisbandUnit") }, false, defaultPage = 1),
    GiftUnit("Gift unit",
        { ImageGetter.getUnitActionPortrait("Present") }, UncivSound.Silent, defaultPage = 1),
    Skip("Skip turn",
        { ImageGetter.getUnitActionPortrait("Skip") }, UncivSound.Silent, defaultPage = 0),
    ShowAdditionalActions("Show more",
        { ImageGetter.getUnitActionPortrait("ShowMore") }, false),
    HideAdditionalActions("Back",
        { ImageGetter.getUnitActionPortrait("HideMore") }, false, defaultPage = 1),
    AddInCapital( "Add in capital",
        { ImageGetter.getUnitActionPortrait("AddInCapital")}, UncivSound.Chimes),
    ;

    // Allow shorter initializations
    constructor(value: String, imageGetter: (() -> Actor)?, uncivSound: UncivSound = UncivSound.Click, defaultPage: Int = 0)
            : this(value, imageGetter, null, true, uncivSound, defaultPage)
    constructor(value: String, imageGetter: (() -> Actor)?, isSkippingToNextUnit: Boolean = true, uncivSound: UncivSound = UncivSound.Click, defaultPage: Int = 0)
            : this(value, imageGetter, null, isSkippingToNextUnit, uncivSound, defaultPage)

    val binding: KeyboardBinding =
            binding ?:
            KeyboardBinding.entries.firstOrNull { it.name == name } ?:
            KeyboardBinding.None
}
