package com.unciv.logic.map.mapunit

import com.unciv.models.Counter
import com.unciv.models.ruleset.RejectionReasonType
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.components.extensions.toPercent
import com.unciv.logic.map.mapunit.actions.UnitActionsUpgrade
import yairm210.purity.annotations.Readonly
import kotlin.math.pow

/** A unit this [MapUnit] could upgrade to, as found by [getUpgrades]
 *  @property upgradedUnit civ-specific equivalent of the target unit
 *  @property unique the [UniqueType.CanUpgrade] or [UniqueType.RuinsUpgrade] unique granting this upgrade, `null` for the base unit's normal `upgradesTo`
 *  @property isSpecial from [UniqueType.RuinsUpgrade] (ancient ruins)
 *  @property availability how far this upgrade is available
 *  @property isShownToPlayer worth listing for the player - disabled if we only lack resources
 *  @property goldCostOfUpgrade regular gold cost - free upgrades pay 0 instead
 *  @property newResourceRequirements additional resources per turn the upgraded unit needs (never negative)
 */
enum class UpgradeAvailability {
    /** Passes all requirements, including resources */
    Available,
    /** Passes all requirements (tech, policy, etc) except resources - shown, but can't be performed */
    AvailableExceptResources,
    /** Passes only when tech/policy/era/wonder requirements and resources are ignored - only free upgrades can be performed */
    AvailableForFree,
    Unavailable
}

class PossibleUpgrade(
    val upgradedUnit: BaseUnit,
    val unique: Unique?,
    val isSpecial: Boolean,
    val availability: UpgradeAvailability,
    val goldCostOfUpgrade: Int,
    val newResourceRequirements: Counter<String>
) {
    val isShownToPlayer get() = availability == UpgradeAvailability.Available
        || availability == UpgradeAvailability.AvailableExceptResources
}

class UnitUpgradeManager(val unit: MapUnit) {

    @Readonly
    private fun getNewResourceRequirements(upgradedUnit: BaseUnit): Counter<String> {
        val resourceRequirementsDelta = Counter<String>()
        for ((resource, amount) in unit.getResourceRequirementsPerTurn())
            resourceRequirementsDelta.add(resource, -amount)
        for ((resource, amount) in upgradedUnit.getResourceRequirementsPerTurn(unit.cache.state))
            resourceRequirementsDelta.add(resource, amount)
        for ((resource, _) in resourceRequirementsDelta.filter { it.value < 0 })  // filter copies, so no CCM
            resourceRequirementsDelta[resource] = 0
        return resourceRequirementsDelta
    }

    /** Lists every upgrade this unit has, regardless of location, cost or movement. Callers filter on the flags. */
    @Readonly
    fun getUpgrades(): List<PossibleUpgrade> {
        val specialUpgrades = unit.baseUnit.getMatchingUniques(UniqueType.RuinsUpgrade, unit.cache.state)
            .map { Triple(it.params[0], it, true) }
        val baseUpgrade = unit.baseUnit.upgradesTo?.let { sequenceOf(Triple(it, null, false)) } ?: emptySequence()
        val uniqueUpgrades = unit.baseUnit.getMatchingUniques(UniqueType.CanUpgrade, unit.cache.state)
            .map { Triple(it.params[0], it, false) }

        return (specialUpgrades + uniqueUpgrades + baseUpgrade)
            .toList()
            .map { (name, unique, isSpecial) ->
                val upgradedUnit = unit.civ.getEquivalentUnit(name)
                PossibleUpgrade(
                    upgradedUnit = upgradedUnit,
                    unique = unique,
                    isSpecial = isSpecial,
                    availability = canUpgrade(upgradedUnit),
                    goldCostOfUpgrade = getCostOfUpgrade(upgradedUnit),
                    newResourceRequirements = getNewResourceRequirements(upgradedUnit)
                )
            }
    }

    /** [getUpgrades] narrowed to the kind requested: if [isSpecial] and a special (ruins) upgrade exists, only the first of those,
     *  otherwise only the normal ones. */
    @Readonly
    fun getUpgradesOfKind(isSpecial: Boolean): List<PossibleUpgrade> {
        val allUpgrades = getUpgrades()
        return allUpgrades.filter { it.isSpecial }.take(1).takeIf { isSpecial && it.isNotEmpty() }
            ?: allUpgrades.filter { !it.isSpecial }
    }

    /** Check whether this unit can upgrade to [unitToUpgradeTo]. This does not check or follow the
     *  normal upgrade chain defined by [BaseUnit.getUpgradeUnits]
     *  @return the strictest [UpgradeAvailability] that holds:
     *          [UpgradeAvailability.AvailableExceptResources] is used to display a disabled Upgrade button,
     *          [UpgradeAvailability.AvailableForFree] ignores tech/policy/building requirements (e.g. ancient ruins).
     */
    @Readonly
    fun canUpgrade(unitToUpgradeTo: BaseUnit): UpgradeAvailability {
        if (unit.name == unitToUpgradeTo.name) return UpgradeAvailability.Unavailable

        val rejectionReasons = unitToUpgradeTo.getRejectionReasons(unit.civ, additionalResources = unit.getResourceRequirementsPerTurn())
            .filterNot { it.isConstructionRejection() || it.type == RejectionReasonType.Obsoleted }

        val withoutResources = rejectionReasons.filterNot { it.type == RejectionReasonType.ConsumesResources }
        return when {
            rejectionReasons.none() -> UpgradeAvailability.Available
            withoutResources.none() -> UpgradeAvailability.AvailableExceptResources
            withoutResources.none { !it.techPolicyEraWonderRequirements() } -> UpgradeAvailability.AvailableForFree
            else -> UpgradeAvailability.Unavailable
        }
    }

    /** Determine gold cost of a Unit Upgrade, potentially over several steps.
     *  @param unitToUpgradeTo the final BaseUnit. Must be reachable via normal upgrades or else
     *         the function will return the cost to upgrade to the last possible and researched normal upgrade.
     *  @return Gold cost in increments of 5, never negative. Will return 0 for invalid inputs (unit can't upgrade or is is already a [unitToUpgradeTo])
     *  @see   <a href="https://github.com/dmnd/CvGameCoreSource/blob/6501d2398113a5100ffa854c146fb6f113992898/CvGameCoreDLL_Expansion1/CvUnit.cpp#L7728">CvUnit::upgradePrice</a>
     */
    // Only one use from getUpgradeAction at the moment, so AI-specific rules omitted
    //todo Does the AI never buy upgrades???
    @Readonly
    fun getCostOfUpgrade(unitToUpgradeTo: BaseUnit): Int {
        // Source rounds to int every step, we don't
        //TODO From the source, this should apply _Production_ modifiers (Temple of Artemis? GameSpeed! StartEra!), at the moment it doesn't

        var goldCostOfUpgrade = 0

        val ruleset = unit.civ.gameInfo.ruleset
        val constants = ruleset.modOptions.constants.unitUpgradeCost
        // apply modifiers: Wonders (Pentagon), Policies (Professional Army). Cached outside loop despite
        // the UniqueType being allowed on a BaseUnit - we don't have a MapUnit in the loop.
        // Actually instantiating every intermediate to support such mods: todo
        var civModifier = 1f
        val stateForConditionals = unit.cache.state
        unit.civ.forEachMatchingUnique(UniqueType.UnitUpgradeCost, stateForConditionals) { unique ->
            civModifier *= unique.params[0].toPercent()
        }

        var cost = constants.base
        cost += (constants.perProduction * (unitToUpgradeTo.cost - unit.baseUnit.cost)).coerceAtLeast(0f)
        val era = unitToUpgradeTo.era(ruleset)
        if (era != null)
            cost *= (1f + era.eraNumber * constants.eraMultiplier)
        cost = (cost * civModifier).pow(constants.exponent)
        cost *= unit.civ.gameInfo.speed.modifier
        goldCostOfUpgrade += (cost / constants.roundTo).toInt() * constants.roundTo

        return goldCostOfUpgrade
    }

    /** Whether [upgrade] can be performed right now: free upgrades only need to be available at all,
     *  paid ones also need gold, movement, own territory, not being embarked and all requirements and resources met. */
    @Readonly
    fun canPerformUpgrade(upgrade: PossibleUpgrade, isFree: Boolean): Boolean {
        if (isFree) return upgrade.availability != UpgradeAvailability.Unavailable
        return upgrade.availability == UpgradeAvailability.Available
            && unit.civ.gold >= upgrade.goldCostOfUpgrade
            && unit.hasMovement()
            && unit.currentTile.getOwner() == unit.civ
            && !unit.isEmbarked()
    }

    /** _Perform_ an upgrade, assuming validity checks were already passed.
     *
     *  Continuing to use a reference to this manager or its unit after this call is invalid!
     *
     *  Please use [UnitActionsUpgrade.getUpgradeActions] instead if at all possible.
     *  @throws IllegalStateException if [canPerformUpgrade] is false
     *
     *  Note - the upgraded unit is a new instance, and it's possible this method will need to place it on a different tile.
     *  It is also possible the placement fails and the original is resurrected - in which case it is a **new instance** as well.
     *  It might be desirable to return `newUnit` (or `resurrectedUnit`) if needed -
     *  but then the lambda in UnitActionsUpgrade will complain and need to be forced back to Unit type.
     */
    fun performUpgrade(upgrade: PossibleUpgrade, isFree: Boolean) {
        val upgradedUnit = upgrade.upgradedUnit
        // When mashing the upgrade button, you can 'queue' 2 upgrade actions
        //  If both are performed, what you get is the unit is doubled
        //  This prevents this, since we lack another way to do so -_-'  
        if (unit.isDestroyed) return  

        if (!canPerformUpgrade(upgrade, isFree))
            throw IllegalStateException("Cannot upgrade ${unit.name} to ${upgrade.upgradedUnit.name}")

        val wasEscorting = unit.isEscorting()
        unit.destroy(destroyTransportedUnit = false)
        val civ = unit.civ
        val position = unit.currentTile.position
        val newUnit = civ.units.placeUnitNearTile(position, upgradedUnit, unit.id, copiedFrom = unit)

        /** We were UNABLE to place the new unit, which means that the unit failed to upgrade!
         * The only known cause of this currently is "land units upgrading to water units" which fail to be placed.
         */
        if (newUnit == null) {
            civ.units.placeUnitNearTile(position, unit.baseUnit, copiedFrom = unit)!!
            return
        }

        // Managed to upgrade
        if (!isFree) civ.addGold(-upgrade.goldCostOfUpgrade)
        newUnit.currentMovement = 0f
        // wake up if lost ability to fortify
        if (newUnit.isFortified() && !newUnit.canFortify(ignoreAlreadyFortified = true))
            newUnit.action = null
        // wake up from Guarding if can't Withdraw
        if (newUnit.isGuarding() && !newUnit.hasUnique(UniqueType.WithdrawsBeforeMeleeCombat))
            newUnit.action = null
        // Re-escort if it was escorting
        if (newUnit.currentTile.position == position && wasEscorting)
            newUnit.startEscorting()
    }
}
