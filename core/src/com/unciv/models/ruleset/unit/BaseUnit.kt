package com.unciv.models.ruleset.unit

import com.unciv.Constants
import com.unciv.logic.GameInfo
import com.unciv.logic.MultiFilter
import com.unciv.logic.city.City
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.Counter
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.RejectionReason
import com.unciv.models.ruleset.RejectionReasonType
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.Conditionals
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.components.extensions.getNeedMoreAmountString
import com.unciv.ui.components.extensions.toPercent
import com.unciv.ui.components.extensions.yieldIfNotNull
import com.unciv.ui.objectdescriptions.BaseUnitDescriptions
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import kotlin.math.pow

// This is BaseUnit because Unit is already a base Kotlin class and to avoid mixing the two up

/** This is the basic info of the units, as specified in Units.json,
 in contrast to MapUnit, which is a specific unit of a certain type that appears on the map */
class BaseUnit : RulesetObject(), INonPerpetualConstruction {

    override var cost: Int = -1
    override var hurryCostModifier: Int = 0
    var movement: Int = 0
    var strength: Int = 0
    var rangedStrength: Int = 0
    var religiousStrength: Int = 0
    var range: Int = 2
    var interceptRange = 0
    var unitType: String = ""

    val type by lazy { ruleset.unitTypes[unitType]!! }
    override var requiredTech: String? = null
    var requiredResource: String? = null

    override fun getUniqueTarget() = UniqueTarget.Unit

    var replacementTextForUniques = ""
    var promotions = HashSet<String>()
    var obsoleteTech: String? = null
    fun techsThatObsoleteThis(): Sequence<String> = if (obsoleteTech == null) sequenceOf() else sequenceOf(obsoleteTech!!)
    fun techsAtWhichAutoUpgradeInProduction(): Sequence<String> = techsThatObsoleteThis()
    fun techsAtWhichNoLongerAvailable(): Sequence<String> = techsThatObsoleteThis()
    @Suppress("unused") // Keep the how-to around
    fun isObsoletedBy(techName: String): Boolean = techsThatObsoleteThis().contains(techName)
    var upgradesTo: String? = null
    var replaces: String? = null
    var uniqueTo: String? = null
    var attackSound: String? = null

    @Transient
    var cachedForceEvaluation: Int = -1

    @Transient
    val costFunctions = BaseUnitCost(this)

    lateinit var ruleset: Ruleset


    /** Generate short description as comma-separated string for Technology description "Units enabled" and GreatPersonPickerScreen */
    fun getShortDescription(uniqueExclusionFilter: Unique.() -> Boolean = {false}) = BaseUnitDescriptions.getShortDescription(this, uniqueExclusionFilter)

    /** Generate description as multi-line string for CityScreen addSelectedConstructionTable
     * @param city Supplies civInfo to show available resources after resource requirements */
    fun getDescription(city: City): String = BaseUnitDescriptions.getDescription(this, city)

    override fun makeLink() = "Unit/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> =
            BaseUnitDescriptions.getCivilopediaTextLines(this, ruleset)

    override fun isHiddenBySettings(gameInfo: GameInfo) =
        super<INonPerpetualConstruction>.isHiddenBySettings(gameInfo) ||
        (!gameInfo.gameParameters.nuclearWeaponsEnabled && isNuclearWeapon())

    fun getUpgradeUnits(stateForConditionals: StateForConditionals? = null): Sequence<String> {
        return sequence {
            yieldIfNotNull(upgradesTo)
            for (unique in getMatchingUniques(UniqueType.CanUpgrade, stateForConditionals))
                yield(unique.params[0])
        }
    }

    fun getRulesetUpgradeUnits(stateForConditionals: StateForConditionals? = null): Sequence<BaseUnit> {
        return sequence {
            for (unit in getUpgradeUnits(stateForConditionals))
                yieldIfNotNull(ruleset.units[unit])
        }
    }

    fun getMapUnit(civInfo: Civilization, unitId: Int? = null): MapUnit {
        val unit = MapUnit()
        unit.name = name
        unit.civ = civInfo
        unit.owner = civInfo.civName
        unit.id = unitId ?: ++civInfo.gameInfo.lastUnitId

        // must be after setting name & civInfo because it sets the baseUnit according to the name
        // and the civInfo is required for using `hasUnique` when determining its movement options
        unit.setTransients(civInfo.gameInfo.ruleset)

        return unit
    }

    /** Allows unique functions (getMatchingUniques, hasUnique) to "see" uniques from the UnitType */
    override fun getMatchingUniques(uniqueType: UniqueType, stateForConditionals: StateForConditionals?): Sequence<Unique> {
        val ourUniques = super<RulesetObject>.getMatchingUniques(uniqueType, stateForConditionals)
        if (! ::ruleset.isInitialized) { // Not sure if this will ever actually happen, but better safe than sorry
            return ourUniques
        }
        val typeUniques = type.getMatchingUniques(uniqueType, stateForConditionals)
        // Memory optimization - very rarely do we actually get uniques from both sources,
        //   and sequence addition is expensive relative to the rare case that we'll actually need it
        if (ourUniques.none()) return typeUniques
        if (typeUniques.none()) return ourUniques
        return ourUniques + type.getMatchingUniques(uniqueType, stateForConditionals)
    }

    override fun getProductionCost(civInfo: Civilization, city: City?): Int  = costFunctions.getProductionCost(civInfo, city)

    override fun canBePurchasedWithStat(city: City?, stat: Stat): Boolean {
        if (city == null) return super.canBePurchasedWithStat(null, stat)
        if (hasUnique(UniqueType.CannotBePurchased)) return false
        if (getRejectionReasons(city.cityConstructions).any { it.type != RejectionReasonType.Unbuildable  })
            return false
        if (costFunctions.canBePurchasedWithStat(city, stat)) return true
        return super.canBePurchasedWithStat(city, stat)
    }

    /** Whenever we call .hasUniques() or .getMatchingUniques(), we also want to return the uniques from the unit type
     * All of the IHasUniques functions converge to getMatchingUniques, so overriding this one function gives us all of them */
    override fun getMatchingUniques(uniqueTemplate: String, stateForConditionals: StateForConditionals?): Sequence<Unique> {
        val baseUnitMatchingUniques = super<RulesetObject>.getMatchingUniques(uniqueTemplate, stateForConditionals)
        return if (::ruleset.isInitialized) baseUnitMatchingUniques +
                type.getMatchingUniques(uniqueTemplate, stateForConditionals)
        else baseUnitMatchingUniques // for e.g. Mod Checker, we may check a BaseUnit's uniques without initializing ruleset
    }

    override fun getBaseBuyCost(city: City, stat: Stat): Float? {
        return sequence {
            val baseCost = super.getBaseBuyCost(city, stat)
            if (baseCost != null)
                yield(baseCost)
            yieldAll(costFunctions.getBaseBuyCosts(city, stat))
        }.minOrNull()
    }

    override fun getStatBuyCost(city: City, stat: Stat): Int? = costFunctions.getStatBuyCost(city, stat)

    fun getDisbandGold(civInfo: Civilization) = getBaseGoldCost(civInfo, null).toInt() / 20

    override fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean {
        val rejectionReasons = getRejectionReasons(cityConstructions)

        if (rejectionReasons.none { !it.shouldShow }) return true
        if (canBePurchasedWithAnyStat(cityConstructions.city)
            && rejectionReasons.all { it.type == RejectionReasonType.Unbuildable }) return true
        return false
    }

    override fun getRejectionReasons(cityConstructions: CityConstructions): Sequence<RejectionReason> =
        getRejectionReasons(cityConstructions.city.civ, cityConstructions.city)

    fun getRejectionReasons(
        civ: Civilization,
        city: City? = null,
        additionalResources: Counter<String> = Counter.ZERO
    ): Sequence<RejectionReason> = sequence {

        val stateForConditionals = StateForConditionals(civ, city)

        if (city != null && isWaterUnit() && !city.isCoastal())
            yield(RejectionReasonType.WaterUnitsInCoastalCities.toInstance())

        for (unique in getMatchingUniques(UniqueType.OnlyAvailable, StateForConditionals.IgnoreConditionals))
            yieldAll(notMetRejections(unique, civ, city))

        for (unique in getMatchingUniques(UniqueType.CanOnlyBeBuiltWhen, StateForConditionals.IgnoreConditionals))
            yieldAll(notMetRejections(unique, civ, city, true))

        for (unique in getMatchingUniques(UniqueType.Unavailable, stateForConditionals))
            yield(RejectionReasonType.ShouldNotBeDisplayed.toInstance())

        if (city != null)
            for (unique in getMatchingUniques(UniqueType.RequiresPopulation))
                if (unique.params[0].toInt() > city.population.population)
                    yield(RejectionReasonType.PopulationRequirement.toInstance(unique.getDisplayText()))

        for (requiredTech: String in requiredTechs())
            if (!civ.tech.isResearched(requiredTech))
                yield(RejectionReasonType.RequiresTech.toInstance("$requiredTech not researched"))
        for (obsoleteTech: String in techsAtWhichNoLongerAvailable())
            if (civ.tech.isResearched(obsoleteTech))
                yield(RejectionReasonType.Obsoleted.toInstance("Obsolete by $obsoleteTech"))

        if (uniqueTo != null && uniqueTo != civ.civName)
            yield(RejectionReasonType.UniqueToOtherNation.toInstance("Unique to $uniqueTo"))
        if (civ.cache.uniqueUnits.any { it.replaces == name })
            yield(RejectionReasonType.ReplacedByOurUnique.toInstance("Our unique unit replaces this"))

        if (isHiddenBySettings(civ.gameInfo))
            yield(RejectionReasonType.DisabledBySetting.toInstance())

        if (hasUnique(UniqueType.Unbuildable, stateForConditionals))
            yield(RejectionReasonType.Unbuildable.toInstance())

        if ((civ.isCityState() || civ.isOneCityChallenger()) && hasUnique(UniqueType.FoundCity, stateForConditionals))
            yield(RejectionReasonType.NoSettlerForOneCityPlayers.toInstance())

        if (getMatchingUniques(UniqueType.MaxNumberBuildable, stateForConditionals).any {
                civ.civConstructions.countConstructedObjects(this@BaseUnit) >= it.params[0].toInt()
            })
            yield(RejectionReasonType.MaxNumberBuildable.toInstance())

        if (!civ.isBarbarian()) { // Barbarians don't need resources
            val civResources = Counter(civ.getCivResourcesByName()) + additionalResources
            for ((resource, requiredAmount) in getResourceRequirementsPerTurn(StateForConditionals(civ))) {
                val availableAmount = civResources[resource]
                if (availableAmount < requiredAmount) {
                    val message = resource.getNeedMoreAmountString(requiredAmount - availableAmount)
                    yield(RejectionReasonType.ConsumesResources.toInstance(message))
                }
            }
        }

        for (unique in civ.getMatchingUniques(UniqueType.CannotBuildUnits, stateForConditionals))
            if (this@BaseUnit.matchesFilter(unique.params[0])) {
                val hasHappinessCondition = unique.conditionals.any {
                    it.type == UniqueType.ConditionalBelowHappiness || it.type == UniqueType.ConditionalBetweenHappiness
                }
                if (hasHappinessCondition)
                    yield(RejectionReasonType.CannotBeBuiltUnhappiness.toInstance(unique.getDisplayText()))
                else yield(RejectionReasonType.CannotBeBuilt.toInstance())
            }

        if (city != null && isAirUnit()) {
            // Not actually added to civ so doesn't require destroy
            val fakeUnit = getMapUnit(civ, Constants.NO_ID)
            val canUnitEnterTile = fakeUnit.movement.canMoveTo(city.getCenterTile())
            if (!canUnitEnterTile)
                yield(RejectionReasonType.NoPlaceToPutUnit.toInstance())
        }
    }

    /**
     * Copy of [com.unciv.models.ruleset.Building.notMetRejections] to handle inverted conditionals.
     * Also custom handles [UniqueType.ConditionalBuildingBuiltAmount], and
     * [UniqueType.ConditionalBuildingBuiltAll]
     */
    private fun notMetRejections(unique: Unique, civ: Civilization, city: City?, built: Boolean=false): Sequence<RejectionReason> = sequence {
        for (conditional in unique.conditionals) {
            // We yield a rejection only when conditionals are NOT met
            if (Conditionals.conditionalApplies(unique, conditional, StateForConditionals(civ, city)))
                continue
            when (conditional.type) {
                UniqueType.ConditionalBuildingBuiltAmount -> {
                    val building = civ.getEquivalentBuilding(conditional.params[0]).name
                    val amount = conditional.params[1].toInt()
                    val cityFilter = conditional.params[2]
                    val numberOfCities = civ.cities.count {
                        it.cityConstructions.containsBuildingOrEquivalent(building) && it.matchesFilter(cityFilter)
                    }
                    if (numberOfCities < amount)
                    {
                        yield(RejectionReasonType.RequiresBuildingInSomeCities.toInstance(
                            "Requires a [$building] in at least [$amount] cities" +
                                " ($numberOfCities/$numberOfCities)"))
                    }
                }
                UniqueType.ConditionalBuildingBuiltAll -> {
                    val building = civ.getEquivalentBuilding(conditional.params[0]).name
                    val cityFilter = conditional.params[1]
                    if(civ.cities.any { it.matchesFilter(cityFilter)
                            !it.isPuppet && !it.cityConstructions.containsBuildingOrEquivalent(building)
                        }) {
                        yield(RejectionReasonType.RequiresBuildingInAllCities.toInstance(
                            "Requires a [${building}] in all cities"))
                    }
                }
                else -> {
                    if (built)
                        yield(RejectionReasonType.CanOnlyBeBuiltInSpecificCities.toInstance(unique.getDisplayText()))
                    else
                        yield(RejectionReasonType.ShouldNotBeDisplayed.toInstance())
                }
            }
        }
    }

    fun isBuildable(civInfo: Civilization) = getRejectionReasons(civInfo).none()

    override fun isBuildable(cityConstructions: CityConstructions): Boolean =
            getRejectionReasons(cityConstructions).none()

    override fun postBuildEvent(cityConstructions: CityConstructions, boughtWith: Stat?): Boolean {
        val civInfo = cityConstructions.city.civ
        val unit = civInfo.units.addUnit(this, cityConstructions.city)
            ?: return false  // couldn't place the unit, so there's actually no unit =(

        //movement penalty
        if (boughtWith != null && !civInfo.gameInfo.gameParameters.godMode && !unit.hasUnique(UniqueType.MoveImmediatelyOnceBought))
            unit.currentMovement = 0f

        addConstructionBonuses(unit, cityConstructions)

        return true
    }

    // This returns the name of the unit this tech upgrades this unit to,
    // or null if there is no automatic upgrade at that tech.
    fun automaticallyUpgradedInProductionToUnitByTech(techName: String): String? {
        for (obsoleteTech: String in techsAtWhichAutoUpgradeInProduction())
            if (obsoleteTech == techName)
                return upgradesTo
        return null
    }

    fun addConstructionBonuses(unit: MapUnit, cityConstructions: CityConstructions) {
        val civInfo = cityConstructions.city.civ

        @Suppress("LocalVariableName")
        var XP = 0

        for (unique in
        cityConstructions.city.getMatchingUniques(UniqueType.UnitStartingExperience)
            .filter { cityConstructions.city.matchesFilter(it.params[2]) }
        ) {
            if (unit.matchesFilter(unique.params[0]))
                XP += unique.params[1].toInt()
        }
        unit.promotions.XP = XP

        for (unique in cityConstructions.city.getMatchingUniques(UniqueType.UnitStartingPromotions)
            .filter { cityConstructions.city.matchesFilter(it.params[1]) }) {
            val filter = unique.params[0]
            val promotion = unique.params.last()

            if (unit.matchesFilter(filter)
                || (
                    filter == "relevant"
                    && civInfo.gameInfo.ruleset.unitPromotions.values
                        .any {
                            it.name == promotion
                            && unit.type.name in it.unitTypes
                        }
                    )
            ) {
                unit.promotions.addPromotion(promotion, isFree = true)
            }
        }
    }

    fun getReplacedUnit(ruleset: Ruleset): BaseUnit {
        return if (replaces == null) this
        else ruleset.units[replaces!!]!!
    }


    private val cachedMatchesFilterResult = HashMap<String, Boolean>()

    /** Implements [UniqueParameterType.BaseUnitFilter][com.unciv.models.ruleset.unique.UniqueParameterType.BaseUnitFilter] */
    fun matchesFilter(filter: String): Boolean =
        cachedMatchesFilterResult.getOrPut(filter) { MultiFilter.multiFilter(filter, ::matchesSingleFilter ) }

    fun matchesSingleFilter(filter: String): Boolean {
        return when (filter) {
            unitType -> true
            name -> true
            replaces -> true
            in Constants.all -> true

            "Melee" -> isMelee()
            "Ranged" -> isRanged()
            "Civilian" -> isCivilian()
            "Military" -> isMilitary()
            "Land" -> isLandUnit()
            "Water" -> isWaterUnit()
            "Air" -> isAirUnit()
            "non-air" -> !movesLikeAirUnits()

            "Nuclear Weapon" -> isNuclearWeapon()
            "Great Person" -> isGreatPerson()
            "Religious" -> hasUnique(UniqueType.ReligiousUnit)

            else -> {
                if (type.matchesFilter(filter)) return true
                for (requiredTech: String in requiredTechs())
                    if (ruleset.technologies[requiredTech]?.matchesFilter(filter) == true) return true
                if (
                // Uniques using these kinds of filters should be deprecated and replaced with adjective-only parameters
                    filter.endsWith(" units")
                    // "military units" --> "Military", using invariant locale
                    && matchesFilter(filter.removeSuffix(" units").lowercase().replaceFirstChar { it.uppercaseChar() })
                ) return true
                return uniqueMap.contains(filter)
            }
        }
    }

    /** Determine whether this is a City-founding unit - abstract, **without any game context**.
     *  Use other methods for MapUnits or when there is a better StateForConditionals available. */
    fun isCityFounder() = hasUnique(UniqueType.FoundCity, StateForConditionals.IgnoreConditionals)

    fun isGreatPerson() = getMatchingUniques(UniqueType.GreatPerson).any()
    fun isGreatPersonOfType(type: String) = getMatchingUniques(UniqueType.GreatPerson).any { it.params[0] == type }

    /** Has a MapUnit implementation that does not ignore conditionals, which should be usually used */
    private fun isNuclearWeapon() = hasUnique(UniqueType.NuclearWeapon, StateForConditionals.IgnoreConditionals)

    fun movesLikeAirUnits() = type.getMovementType() == UnitMovementType.Air

    /** Returns resource requirements from both uniques and requiredResource field */
    override fun getResourceRequirementsPerTurn(stateForConditionals: StateForConditionals?): Counter<String> {
        val resourceRequirements = Counter<String>()
        if (requiredResource != null) resourceRequirements[requiredResource!!] = 1
        for (unique in getMatchingUniques(UniqueType.ConsumesResources, stateForConditionals))
            resourceRequirements[unique.params[1]] += unique.params[0].toInt()
        return resourceRequirements
    }


    fun isRanged() = rangedStrength > 0
    fun isMelee() = !isRanged() && strength > 0
    fun isMilitary() = isRanged() || isMelee()
    fun isCivilian() = !isMilitary()

    private val isLandUnitInternal by lazy { type.isLandUnit() }
    fun isLandUnit() = isLandUnitInternal
    fun isWaterUnit() = type.isWaterUnit()
    fun isAirUnit() = type.isAirUnit()

    fun isProbablySiegeUnit() = isRanged()
            && getMatchingUniques(UniqueType.Strength, StateForConditionals.IgnoreConditionals)
                .any { it.params[0].toInt() > 0
                    && it.conditionals.any { conditional -> conditional.type == UniqueType.ConditionalVsCity }
                }

    fun getForceEvaluation(): Int {
        if (cachedForceEvaluation < 0) evaluateForce()
        return cachedForceEvaluation
    }

    private fun evaluateForce() {
        if (strength == 0 && rangedStrength == 0) {
            cachedForceEvaluation = 0
            return
        }

        var power = strength.toFloat().pow(1.5f)
        var rangedPower = rangedStrength.toFloat().pow(1.45f)

        // Value ranged naval units less
        if (isWaterUnit()) {
            rangedPower /= 2
        }
        if (rangedPower > 0)
            power = rangedPower

        // Replicates the formula from civ V, which is a lower multiplier than probably intended, because math
        // They did fix it in BNW so it was completely bugged and always 1, again math
        power = (power * movement.toFloat().pow(0.3f))

        if (hasUnique(UniqueType.SelfDestructs))
            power /= 2
        if (isNuclearWeapon())
            power += 4000

        // Uniques
        val allUniques = uniqueObjects.asSequence() +
            promotions.asSequence()
                .mapNotNull { ruleset.unitPromotions[it] }
                .flatMap { it.uniqueObjects }

        for (unique in allUniques) {
            when (unique.type) {
                UniqueType.Strength -> {
                    if (unique.params[0].toInt() <= 0) continue
                    if (unique.conditionals.any { it.type == UniqueType.ConditionalVsUnits }) { // Bonus vs some units - a quarter of the bonus
                        power *= (unique.params[0].toInt() / 4f).toPercent()
                    } else if (
                        unique.conditionals.any {
                            it.type == UniqueType.ConditionalVsCity // City Attack - half the bonus
                                || it.type == UniqueType.ConditionalAttacking // Attack - half the bonus
                                || it.type == UniqueType.ConditionalDefending // Defense - half the bonus
                                || it.type == UniqueType.ConditionalFightingInTiles
                        } // Bonus in terrain or feature - half the bonus
                    ) {
                        power *= (unique.params[0].toInt() / 2f).toPercent()
                    }
                }
                UniqueType.StrengthNearCapital ->
                    if (unique.params[0].toInt() > 0)
                        power *= (unique.params[0].toInt() / 4f).toPercent()  // Bonus decreasing with distance from capital - not worth much most of the map???

                UniqueType.MayParadrop // Paradrop - 25% bonus
                    -> power += power / 4
                UniqueType.MustSetUp // Must set up - 20 % penalty
                    -> power -= power / 5
                UniqueType.AdditionalAttacks // Extra attacks - 20% bonus per extra attack
                    -> power += (power * unique.params[0].toInt()) / 5
                else -> {}
            }
        }

        cachedForceEvaluation = power.toInt()
    }
}
