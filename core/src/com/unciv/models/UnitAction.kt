package com.unciv.models

import com.unciv.Constants
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.components.fonts.Fonts
import com.unciv.utils.hashOf


/** Unit Actions - class - carries dynamic data and actual execution.
 * Static properties are in [UnitActionType].
 * Note this is for the buttons offering actions, not the ongoing action stored with a [MapUnit][com.unciv.logic.map.mapunit.MapUnit]
 */
open class UnitAction(
    val type: UnitActionType,
    val title: String = type.value,
    val isCurrentAction: Boolean = false,
    /** The unique this action stems from, if any - the UI takes its `UnitActionPriority` modifiers from here */
    val associatedUnique: Unique? = null,
    /** Action is Null if this unit *can* execute the action but *not right now* - it's embarked, out of moves, etc */
    val action: (() -> Unit)? = null
) {
    //TODO remove once sure they're unused
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UnitAction) return false

        if (type != other.type) return false
        if (isCurrentAction != other.isCurrentAction) return false
        if (action != other.action) return false

        return true
    }

    override fun hashCode(): Int = hashOf(type.hashCode(), isCurrentAction.hashCode(), action.hashCode())

    override fun toString(): String {
        return "UnitAction(type=$type, title='$title', isCurrentAction=$isCurrentAction)"
    }
}

/** Specialized [UnitAction] for upgrades
 *
 *  Transports [unitToUpgradeTo] from [creation][com.unciv.logic.map.mapunit.actions.UnitActionsUpgrade.getUpgradeActions]
 *  to [UI][com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsTable.update]
 */
class UpgradeUnitAction(
    title: String,
    val unitToUpgradeTo: BaseUnit,
    val goldCostOfUpgrade: Int,
    val newResourceRequirements: Counter<String>,
    associatedUnique: Unique?,
    action: (() -> Unit)?,
) : UnitAction(UnitActionType.Upgrade, title, associatedUnique = associatedUnique, action = action)

/**
 * Unit Actions - generic enum with static, UI-independent properties
 * (icon, sound, key binding and page preference are in the UI layer, see `UiUnitActionType`)
 *
 * Note for Creators of new UnitActions:
 * - If your action uses a dynamic label overriding `UnitActionType.value`,
 *   then make sure `value` is the required translation template (if it requires multiple templates, leave it empty or use any example template).
 * - If you use `tr()` language, make sure you use **at most one** pair of `{}` curly braces.
 * - Add a matching entry to `UiUnitActionType`.
 *
 * Reason: `TranslationTests.allUnitActionsHaveTemplate` will check the existence of a matching template.
 *
 * @param value _default_ label to display, can be overridden in UnitAction instantiation. In that case, this must be the translation template or empty.
*/
enum class UnitActionType(val value: String) {
    StopEscortFormation("Stop Escort formation"),
    EscortFormation("Escort formation"),
    Automate("Automate"),
    StopAutomation("Stop automation"),
    StopMovement("Stop movement"),
    Sleep("Sleep"),
    SleepUntilHealed("Sleep until healed"),
    Fortify("Fortify"),
    FortifyUntilHealed("Fortify until healed"),
    Guard("Guard"),
    Explore("Explore"),
    StopExploration("Stop exploration"),
    Upgrade("Upgrade to [unitType] ([goldCost] gold)"),
    Transform("Transform"),
    Pillage("Pillage"),
    Paradrop("Paradrop"),
    AirSweep("Air Sweep"),
    SetUp("Set up"),
    FoundCity("Found city"),
    Repair(Constants.repair),
    CreateImprovement("Create"),
    HurryResearch("{Hurry Research} (${Fonts.death})"),
    HurryPolicy("{Hurry Policy} (${Fonts.death})"),
    HurryWonder("{Hurry Wonder} (${Fonts.death})"),
    HurryBuilding("{Hurry Construction} (${Fonts.death})"),
    ConductTradeMission("{Conduct Trade Mission} (${Fonts.death})"),
    FoundReligion("Found a Religion"),
    TriggerUnique("Trigger unique"),
    SpreadReligion("Spread [religionName]"),
    RemoveHeresy("Remove Heresy"),
    EnhanceReligion("Enhance a Religion"),
    DisbandUnit("Disband unit"),
    GiftUnit("Gift unit"),
    Skip("Skip turn"),
    AddInCapital("Add in capital"),
}
