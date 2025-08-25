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
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.components.extensions.getNeedMoreAmountString
import com.unciv.ui.components.extensions.toPercent
import com.unciv.ui.objectdescriptions.BaseUnitDescriptions
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.utils.yieldIfNotNull
import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Readonly
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

    val type by lazy { ruleset.unitTypes[unitType]
        ?: throw Exception("Unit $name has unit type $unitType which is not present in ruleset!") }
    override var requiredTech: String? = null
    var requiredResource: String? = null

    override fun getUniqueTarget() = UniqueTarget.Unit

    var replacementTextForUniques = ""
    var promotions = HashSet<String>()
    var obsoleteTech: String? = null
    
    @Readonly fun techsThatObsoleteThis(): Sequence<String> = if (obsoleteTech == null) emptySequence() else sequenceOf(obsoleteTech!!)
    @Readonly fun techsAtWhichAutoUpgradeInProduction(): Sequence<String> = techsThatObsoleteThis()
    @Readonly fun techsAtWhichNoLongerAvailable(): Sequence<String> = techsThatObsoleteThis()
    @Suppress("unused") // Keep the how-to around
    fun isObsoletedBy(techName: String): Boolean = techsThatObsoleteThis().contains(techName)
    var upgradesTo: String? = null
    var replaces: String? = null
    var uniqueTo: String? = null
    var attackSound: String? = null


    @Transient
    val costFunctions = BaseUnitCost(this)

    lateinit var ruleset: Ruleset
        private set

    fun setRuleset(ruleset: Ruleset) {
        this.ruleset = ruleset
        val list = ArrayList(uniques)
        list.addAll(ruleset.globalUniques.unitUniques)
        list.addAll(type.uniques)
        rulesetUniqueObjects = uniqueObjectsProvider(list)
        rulesetUniqueMap = uniqueMapProvider(rulesetUniqueObjects) // Has global uniques by the unique objects already
    }

    @Transient
    var rulesetUniqueObjects: List<Unique> = ArrayList()
        private set

    @Transient
    var rulesetUniqueMap: UniqueMap = UniqueMap()
        private set

    /** Generate short description as comma-separated string for Technology description "Units enabled" and GreatPersonPickerScreen */
    fun getShortDescription(uniqueExclusionFilter: Unique.() -> Boolean = {false}) = BaseUnitDescriptions.getShortDescription(this, uniqueExclusionFilter)

    /** Generate description as multi-line string for CityScreen addSelectedConstructionTable
     * @param city Supplies civInfo to show available resources after resource requirements */
    fun getDescription(city: City): String = BaseUnitDescriptions.getDescription(this, city)

    override fun makeLink() = "Unit/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> =
            BaseUnitDescriptions.getCivilopediaTextLines(this, ruleset)

    @Readonly
    override fun isUnavailableBySettings(gameInfo: GameInfo) =
        super<INonPerpetualConstruction>.isUnavailableBySettings(gameInfo) ||
        (!gameInfo.gameParameters.nuclearWeaponsEnabled && isNuclearWeapon())

    @Readonly
    fun getUpgradeUnits(gameContext: GameContext = GameContext.EmptyState): Sequence<String> {
        return sequence {
            yieldIfNotNull(upgradesTo)
            for (unique in getMatchingUniques(UniqueType.CanUpgrade, gameContext))
                yield(unique.params[0])
        }
    }

    @Readonly
    fun getRulesetUpgradeUnits(gameContext: GameContext = GameContext.EmptyState): Sequence<BaseUnit> {
        return sequence {
            for (unit in getUpgradeUnits(gameContext))
                yieldIfNotNull(ruleset.units[unit])
        }
    }

    @Readonly @Suppress("purity") // technically DOES increase gameInfo.lastUnitId...
    fun newMapUnit(civInfo: Civilization, unitId: Int? = null): MapUnit {
        @LocalState val unit = MapUnit()
        unit.name = name
        unit.civ = civInfo
        unit.owner = civInfo.civName
        unit.id = unitId ?: ++civInfo.gameInfo.lastUnitId

        // must be after setting name & civInfo because it sets the baseUnit according to the name
        // and the civInfo is required for using `hasUnique` when determining its movement options
        unit.setTransients(civInfo.gameInfo.ruleset)

        return unit
    }

    @Readonly
    override fun hasUnique(uniqueType: UniqueType, state: GameContext?): Boolean {
        val gameContext = state ?: GameContext.EmptyState
        return if (::ruleset.isInitialized) rulesetUniqueMap.hasUnique(uniqueType, gameContext)
        else super<RulesetObject>.hasUnique(uniqueType, gameContext)
    }

    @Readonly
    override fun hasUnique(uniqueTag: String, state: GameContext?): Boolean {
        val gameContext = state ?: GameContext.EmptyState
        return if (::ruleset.isInitialized) rulesetUniqueMap.hasUnique(uniqueTag, gameContext)
        else super<RulesetObject>.hasUnique(uniqueTag, gameContext)
    }

    @Readonly
    override fun hasTagUnique(tagUnique: String): Boolean {
        return if (::ruleset.isInitialized) rulesetUniqueMap.hasTagUnique(tagUnique)
        else super<RulesetObject>.hasTagUnique(tagUnique)
    }

    /** Allows unique functions (getMatchingUniques, hasUnique) to "see" uniques from the UnitType */
    @Readonly
    override fun getMatchingUniques(uniqueType: UniqueType, state: GameContext): Sequence<Unique> {
        return if (::ruleset.isInitialized) rulesetUniqueMap.getMatchingUniques(uniqueType, state)
        else super<RulesetObject>.getMatchingUniques(uniqueType, state)
    }

    /** Allows unique functions (getMatchingUniques, hasUnique) to "see" uniques from the UnitType */
    @Readonly
    override fun getMatchingUniques(uniqueTag: String, state: GameContext): Sequence<Unique> {
        return if (::ruleset.isInitialized) rulesetUniqueMap.getMatchingUniques(uniqueTag, state)
        else super<RulesetObject>.getMatchingUniques(uniqueTag, state)
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

    override fun getBaseBuyCost(city: City, stat: Stat): Float? {
        return sequence {
            val baseCost = super.getBaseBuyCost(city, stat)
            if (baseCost != null)
                yield(baseCost)
            yieldAll(costFunctions.getBaseBuyCosts(city, stat))
        }.minOrNull()
    }

    override fun getStatBuyCost(city: City, stat: Stat): Int? = costFunctions.getStatBuyCost(city, stat)

    @Readonly fun getDisbandGold(civInfo: Civilization) = getBaseGoldCost(civInfo, null).toInt() / 20

    override fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean {
        val rejectionReasons = getRejectionReasons(cityConstructions)

        if (hasUnique(UniqueType.ShowsWhenUnbuilable, cityConstructions.city.state) &&
            rejectionReasons.none { it.isNeverVisible() })
            return true

        if (rejectionReasons.none { !it.shouldShow }) return true
        if (canBePurchasedWithAnyStat(cityConstructions.city)
            && rejectionReasons.all { it.type == RejectionReasonType.Unbuildable }) return true
        return false
    }

    override fun getRejectionReasons(cityConstructions: CityConstructions): Sequence<RejectionReason> =
        getRejectionReasons(cityConstructions.city.civ, cityConstructions.city)

    @Readonly
    fun getRejectionReasons(
        civ: Civilization,
        city: City? = null,
        additionalResources: Counter<String> = Counter.ZERO
    ): Sequence<RejectionReason> = sequence {

        val stateForConditionals = city?.state ?: civ.state

        if (city != null && isWaterUnit && !city.isCoastal())
            yield(RejectionReasonType.WaterUnitsInCoastalCities.toInstance())

        for (unique in getMatchingUniques(UniqueType.OnlyAvailable, GameContext.IgnoreConditionals))
            yieldAll(notMetRejections(unique, civ, city))

        for (unique in getMatchingUniques(UniqueType.CanOnlyBeBuiltWhen, GameContext.IgnoreConditionals))
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

        if (uniqueTo != null && !civ.matchesFilter(uniqueTo!!, stateForConditionals))
            yield(RejectionReasonType.UniqueToOtherNation.toInstance("Unique to $uniqueTo"))
        if (civ.cache.uniqueUnits.any { it.replaces == name })
            yield(RejectionReasonType.ReplacedByOurUnique.toInstance("Our unique unit replaces this"))

        if (isUnavailableBySettings(civ.gameInfo))
            yield(RejectionReasonType.DisabledBySetting.toInstance())

        if (hasUnique(UniqueType.Unbuildable, stateForConditionals))
            yield(RejectionReasonType.Unbuildable.toInstance())

        if ((civ.isCityState || civ.isOneCityChallenger()) && hasUnique(UniqueType.FoundCity, GameContext.IgnoreConditionals))
            yield(RejectionReasonType.NoSettlerForOneCityPlayers.toInstance())

        if (getMatchingUniques(UniqueType.MaxNumberBuildable, stateForConditionals).any {
                civ.civConstructions.countConstructedObjects(this@BaseUnit) >= it.params[0].toInt()
            })
            yield(RejectionReasonType.MaxNumberBuildable.toInstance())

        if (!civ.isBarbarian) { // Barbarians don't need resources
            val civResources = Counter(civ.getCivResourcesByName()) + additionalResources
            for ((resource, requiredAmount) in getResourceRequirementsPerTurn(stateForConditionals)) {
                val availableAmount = civResources[resource]
                if (availableAmount < requiredAmount) {
                    val message = resource.getNeedMoreAmountString(requiredAmount - availableAmount)
                    yield(RejectionReasonType.ConsumesResources.toInstance(message))
                }
            }

            // If we've already paid the unit costs, we don't need to pay it again
            if (city == null || city.cityConstructions.getWorkDone(name) == 0)
                for ((resourceName, amount) in getStockpiledResourceRequirements(stateForConditionals)) {
                    val availableResources = city?.getAvailableResourceAmount(resourceName) ?: civ.getResourceAmount(resourceName)
                    if (availableResources < amount)
                        yield(RejectionReasonType.ConsumesResources.toInstance(resourceName.getNeedMoreAmountString(amount - availableResources)))
                }
        }

        for (unique in civ.getMatchingUniques(UniqueType.CannotBuildUnits, stateForConditionals))
            if (this@BaseUnit.matchesFilter(unique.params[0], stateForConditionals)) {
                yield(RejectionReasonType.CannotBeBuilt.toInstance())
            }

        if (city != null && isAirUnit() && !canUnitEnterTile(city)) {
            // Not actually added to civ so doesn't require destroy
            yield(RejectionReasonType.NoPlaceToPutUnit.toInstance())
        }
    }
    
    @Readonly @Suppress("purity") // Good suppression - we cheat by creating a unit
    fun canUnitEnterTile(city: City): Boolean {
        val fakeUnit = newMapUnit(city.civ, Constants.NO_ID)
        return fakeUnit.movement.canMoveTo(city.getCenterTile())
    }

    /**
     * Copy of [com.unciv.models.ruleset.Building.notMetRejections] to handle inverted conditionals.
     * Also custom handles [UniqueType.ConditionalBuildingBuiltAmount], and
     * [UniqueType.ConditionalBuildingBuiltAll]
     */
    @Readonly
    private fun notMetRejections(unique: Unique, civ: Civilization, city: City?, built: Boolean=false): Sequence<RejectionReason> = sequence {
        for (conditional in unique.modifiers) {
            // We yield a rejection only when conditionals are NOT met
            if (Conditionals.conditionalApplies(unique, conditional, city?.state ?: civ.state))
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

    @Readonly fun isBuildable(civInfo: Civilization) = getRejectionReasons(civInfo).none()

    override fun isBuildable(cityConstructions: CityConstructions): Boolean =
            getRejectionReasons(cityConstructions).none()

    fun construct(cityConstructions: CityConstructions, boughtWith: Stat?): MapUnit? {
        val civInfo = cityConstructions.city.civ
        val unit = civInfo.units.addUnit(this, cityConstructions.city)
            ?: return null  // couldn't place the unit, so there's actually no unit =(

        //movement penalty
        if (boughtWith != null && !civInfo.gameInfo.gameParameters.godMode && !unit.hasUnique(UniqueType.CanMoveImmediatelyOnceBought))
            unit.currentMovement = 0f

        addConstructionBonuses(unit, cityConstructions)

        return unit
    }

    // This returns the name of the unit this tech upgrades this unit to,
    // or null if there is no automatic upgrade at that tech.
    @Readonly
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

        for (unique in cityConstructions.city.getMatchingUniques(UniqueType.UnitStartingExperience)) {
            if (unit.matchesFilter(unique.params[0]) && cityConstructions.city.matchesFilter(unique.params[2]))
                XP += unique.params[1].toInt()
        }
        unit.promotions.XP = XP

        for (unique in cityConstructions.city.getMatchingUniques(UniqueType.UnitStartingPromotions)
            .filter { cityConstructions.city.matchesFilter(it.params[1]) }) {
            val filter = unique.params[0]
            val promotion = unique.params.last()

            val isRelevantPromotion = filter == "relevant"
                    && civInfo.gameInfo.ruleset.unitPromotions.values
                .any { it.name == promotion && unit.type.name in it.unitTypes }
            
            if (isRelevantPromotion || unit.matchesFilter(filter)) {
                unit.promotions.addPromotion(promotion, isFree = true)
            }
        }
    }

    @Readonly
    fun getReplacedUnit(ruleset: Ruleset): BaseUnit {
        return if (replaces == null) this
        else ruleset.units[replaces!!]!!
    }


    @Cache private val cachedMatchesFilterResult = HashMap<String, Boolean>()

    /** Implements [UniqueParameterType.BaseUnitFilter][com.unciv.models.ruleset.unique.UniqueParameterType.BaseUnitFilter] */
    @Readonly
    fun matchesFilter(filter: String, state: GameContext? = null, multiFilter: Boolean = true): Boolean {
        return if (multiFilter) MultiFilter.multiFilter(filter, {
            cachedMatchesFilterResult.getOrPut(it) { matchesSingleFilter(it) } ||
                state != null && hasUnique(it, state) ||
                state == null && hasTagUnique(it)
        })
        else cachedMatchesFilterResult.getOrPut(filter) { matchesSingleFilter(filter) } ||
            state != null && hasUnique(filter, state) ||
            state == null && hasTagUnique(filter)
    }
    
    @Readonly
    fun matchesSingleFilter(filter: String): Boolean {
        // all cases are constants for performance
        return when (filter) {
            "all", "All" -> true
            "Melee" -> isMelee()
            "Ranged" -> isRanged()
            "Civilian" -> isCivilian()
            "Military" -> isMilitary
            "Land" -> isLandUnit
            "Water" -> isWaterUnit
            "Air" -> isAirUnit()
            "non-air" -> !movesLikeAirUnits

            "Nuclear Weapon" -> isNuclearWeapon()
            "Great Person" -> isGreatPerson
            "Religious" -> hasUnique(UniqueType.ReligiousUnit)

            else -> {
                if (filter == unitType) return true
                else if (filter == name) return true
                else if (filter == replaces) return true
                
                for (requiredTech: String in requiredTechs())
                    if (ruleset.technologies[requiredTech]?.matchesFilter(filter, multiFilter = false) == true) return true
                if (
                // Uniques using these kinds of filters should be deprecated and replaced with adjective-only parameters
                    filter.endsWith(" units")
                    // "military units" --> "Military", using invariant locale
                    && matchesFilter(filter.removeSuffix(" units").lowercase().replaceFirstChar { it.uppercaseChar() },
                        multiFilter = false)
                ) return true
                return false
            }
        }
    }

    /** Determine whether this is a City-founding unit - abstract, **without any game context**.
     *  Use other methods for MapUnits or when there is a better StateForConditionals available. */
    @Readonly fun isCityFounder() = hasUnique(UniqueType.FoundCity, GameContext.IgnoreConditionals)

    val isGreatPerson by lazy { getMatchingUniques(UniqueType.GreatPerson).any() }
    @Readonly fun isGreatPersonOfType(type: String) = getMatchingUniques(UniqueType.GreatPerson).any { it.params[0] == type }

    /** Has a MapUnit implementation that does not ignore conditionals, which should be usually used */
    @Readonly private fun isNuclearWeapon() = hasUnique(UniqueType.NuclearWeapon, GameContext.IgnoreConditionals)

    val movesLikeAirUnits by lazy { type.getMovementType() == UnitMovementType.Air }

    /** Returns resource requirements from both uniques and requiredResource field */
    override fun getResourceRequirementsPerTurn(state: GameContext?): Counter<String> {
        val resourceRequirements = Counter<String>()
        if (requiredResource != null) resourceRequirements[requiredResource!!] = 1
        for (unique in getMatchingUniques(UniqueType.ConsumesResources, state ?: GameContext.EmptyState))
            resourceRequirements.add(unique.params[1], unique.params[0].toInt())
        return resourceRequirements
    }


    @Readonly fun isRanged() = rangedStrength > 0
    @Readonly fun isMelee() = !isRanged() && strength > 0
    val isMilitary by lazy { isRanged() || isMelee() }
    @Readonly fun isCivilian() = !isMilitary

    val isLandUnit by lazy { type.isLandUnit() }
    val isWaterUnit by lazy { type.isWaterUnit() }
    @Readonly fun isAirUnit() = type.isAirUnit()

    @Readonly
    fun isProbablySiegeUnit() = isRanged()
            && getMatchingUniques(UniqueType.Strength, GameContext.IgnoreConditionals)
                .any { it.params[0].toInt() > 0 && it.hasModifier(UniqueType.ConditionalVsCity) }


    @Transient @Cache
    private var cachedForceEvaluation: Int = -1
    
    @Readonly
    fun getForceEvaluation(): Int {
        if (cachedForceEvaluation < 0) 
            cachedForceEvaluation = evaluateForce()
        return cachedForceEvaluation
    }

    @Readonly
    private fun evaluateForce(): Int {
        if (strength == 0 && rangedStrength == 0) return 0

        var power = strength.toFloat().pow(1.5f)
        var rangedPower = rangedStrength.toFloat().pow(1.45f)

        // Value ranged naval units less
        if (isWaterUnit) {
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
        val allUniques = rulesetUniqueObjects.asSequence() +
            promotions.asSequence()
                .mapNotNull { ruleset.unitPromotions[it] }
                .flatMap { it.uniqueObjects }
        
        // When we have multiple conditional strength bonuses, only the highest one counts
        // Otherwise we get massive overvaluation of units with many conflicting conditionals
        var highestConditionalPowerBonus = 1f

        for (unique in allUniques) {
            when (unique.type) {
                UniqueType.Strength -> {
                    if (unique.params[0].toInt() <= 0) continue
                    
                    if (unique.hasModifier(UniqueType.ConditionalVsUnits)) { // Bonus vs some units - a quarter of the bonus
                        highestConditionalPowerBonus = (unique.params[0].toInt() / 4f).toPercent()
                    } else if (
                        unique.modifiers.any {
                            it.type == UniqueType.ConditionalVsCity // City Attack - half the bonus
                                || it.type == UniqueType.ConditionalAttacking // Attack - half the bonus
                                || it.type == UniqueType.ConditionalDefending // Defense - half the bonus
                                || it.type == UniqueType.ConditionalFightingInTiles
                        } // Bonus in terrain or feature - half the bonus
                    ) {
                        highestConditionalPowerBonus = (unique.params[0].toInt() / 2f).toPercent()
                    } else {
                        highestConditionalPowerBonus = (unique.params[0].toInt()).toPercent() // Static bonus
                    }
                }
                UniqueType.StrengthNearCapital ->
                    if (unique.params[0].toInt() > 0)
                        power *= (unique.params[0].toInt() / 4f).toPercent()  // Bonus decreasing with distance from capital - not worth much most of the map???

                UniqueType.MayParadrop // Paradrop - 25% bonus
                    -> power *= 1.25f
                UniqueType.MayParadropOld // ParadropOld - 25% bonus
                    -> power *= 1.25f
                UniqueType.MustSetUp // Must set up - 20 % penalty
                    -> power /= 1.20f
                UniqueType.AdditionalAttacks // Extra attacks - 20% bonus per extra attack
                    -> power *= (unique.params[0].toInt() * 20f).toPercent()
                else -> {}
            }
        }
        power *= highestConditionalPowerBonus

        return power.toInt()
    }
}
