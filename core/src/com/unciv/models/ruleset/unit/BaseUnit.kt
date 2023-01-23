package com.unciv.models.ruleset.unit

import com.unciv.logic.city.City
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.INonPerpetualConstruction
import com.unciv.logic.city.RejectionReason
import com.unciv.logic.city.RejectionReasons
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.utils.extensions.filterAndLogic
import com.unciv.ui.utils.extensions.getNeedMoreAmountString
import com.unciv.ui.utils.extensions.toPercent
import kotlin.math.pow

// This is BaseUnit because Unit is already a base Kotlin class and to avoid mixing the two up

/** This is the basic info of the units, as specified in Units.json,
 in contrast to MapUnit, which is a specific unit of a certain type that appears on the map */
class BaseUnit : RulesetObject(), INonPerpetualConstruction {

    override var cost: Int = 0
    override var hurryCostModifier: Int = 0
    var movement: Int = 0
    var strength: Int = 0
    var rangedStrength: Int = 0
    var religiousStrength: Int = 0
    var range: Int = 2
    var interceptRange = 0
    var unitType: String = ""
    fun getType() = ruleset.unitTypes[unitType]!!
    override var requiredTech: String? = null
    private var requiredResource: String? = null

    override fun getUniqueTarget() = UniqueTarget.Unit

    var replacementTextForUniques = ""
    var promotions = HashSet<String>()
    var obsoleteTech: String? = null
    var upgradesTo: String? = null
    val specialUpgradesTo: String? by lazy {
        getMatchingUniques(UniqueType.RuinsUpgrade).map { it.params[0] }.firstOrNull()
    }
    var replaces: String? = null
    var uniqueTo: String? = null
    var attackSound: String? = null

    @Transient
    var cachedForceEvaluation: Int = -1

    @Transient
    val costFunctions = BaseUnitCost(this)

    lateinit var ruleset: Ruleset


    fun getShortDescription() = BaseUnitDescriptions.getShortDescription(this)

    /** Generate description as multi-line string for CityScreen addSelectedConstructionTable
     * @param city Supplies civInfo to show available resources after resource requirements */
    fun getDescription(city: City): String = BaseUnitDescriptions.getDescription(this, city)

    override fun makeLink() = "Unit/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> =
            BaseUnitDescriptions.getCivilopediaTextLines(this, ruleset)

    fun getMapUnit(civInfo: Civilization): MapUnit {
        val unit = MapUnit()
        unit.name = name
        unit.civInfo = civInfo
        unit.owner = civInfo.civName

        // must be after setting name & civInfo because it sets the baseUnit according to the name
        // and the civInfo is required for using `hasUnique` when determining its movement options
        unit.setTransients(civInfo.gameInfo.ruleSet)

        return unit
    }

    override fun getProductionCost(civInfo: Civilization): Int  = costFunctions.getProductionCost(civInfo)

    override fun canBePurchasedWithStat(city: City?, stat: Stat): Boolean {
        if (city == null) return super.canBePurchasedWithStat(city, stat)
        if (costFunctions.canBePurchasedWithStat(city, stat)) return true
        return super.canBePurchasedWithStat(city, stat)
    }

    override fun getBaseBuyCost(city: City, stat: Stat): Int? {
        if (stat == Stat.Gold) return getBaseGoldCost(city.civInfo).toInt()

        return sequence {
            val baseCost = super.getBaseBuyCost(city, stat)
            if (baseCost != null)
                yield(baseCost)
            yieldAll(costFunctions.getBaseBuyCosts(city, stat))
        }.minOrNull()
    }

    override fun getStatBuyCost(city: City, stat: Stat): Int? = costFunctions.getStatBuyCost(city, stat)

    fun getDisbandGold(civInfo: Civilization) = getBaseGoldCost(civInfo).toInt() / 20

    override fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean {
        val rejectionReasons = getRejectionReasons(cityConstructions)
        return rejectionReasons.none { !it.shouldShow }
            || (
                canBePurchasedWithAnyStat(cityConstructions.city)
                && rejectionReasons.all { it.rejectionReason == RejectionReason.Unbuildable }
            )
    }

    override fun getRejectionReasons(cityConstructions: CityConstructions): RejectionReasons {
        val rejectionReasons = RejectionReasons()
        if (isWaterUnit() && !cityConstructions.city.isCoastal())
            rejectionReasons.add(RejectionReason.WaterUnitsInCoastalCities)
        if (isAirUnit()) {
            val fakeUnit = getMapUnit(cityConstructions.city.civInfo)
            val canUnitEnterTile = fakeUnit.movement.canMoveTo(cityConstructions.city.getCenterTile())
            if (!canUnitEnterTile)
                rejectionReasons.add(RejectionReason.NoPlaceToPutUnit)
        }
        val civInfo = cityConstructions.city.civInfo
        for (unique in uniqueObjects) {
            when (unique.type) {
                UniqueType.OnlyAvailableWhen -> if (!unique.conditionalsApply(civInfo, cityConstructions.city))
                    rejectionReasons.add(RejectionReason.ShouldNotBeDisplayed)

                UniqueType.RequiresPopulation -> if (unique.params[0].toInt() > cityConstructions.city.population.population)
                    rejectionReasons.add(RejectionReason.PopulationRequirement.toInstance(unique.text))

                else -> {}
            }
        }

        val civRejectionReasons = getRejectionReasons(civInfo)
        if (civRejectionReasons.isNotEmpty()) {
            rejectionReasons.addAll(civRejectionReasons)
        }
        return rejectionReasons
    }

    fun getRejectionReasons(civInfo: Civilization): RejectionReasons {
        val rejectionReasons = RejectionReasons()
        val ruleSet = civInfo.gameInfo.ruleSet

        if (requiredTech != null && !civInfo.tech.isResearched(requiredTech!!))
            rejectionReasons.add(RejectionReason.RequiresTech.toInstance("$requiredTech not researched"))
        if (obsoleteTech != null && civInfo.tech.isResearched(obsoleteTech!!))
            rejectionReasons.add(RejectionReason.Obsoleted.toInstance("Obsolete by $obsoleteTech"))

        if (uniqueTo != null && uniqueTo != civInfo.civName)
            rejectionReasons.add(RejectionReason.UniqueToOtherNation.toInstance("Unique to $uniqueTo"))
        if (ruleSet.units.values.any { it.uniqueTo == civInfo.civName && it.replaces == name })
            rejectionReasons.add(RejectionReason.ReplacedByOurUnique.toInstance("Our unique unit replaces this"))

        if (!civInfo.gameInfo.gameParameters.nuclearWeaponsEnabled && isNuclearWeapon())
            rejectionReasons.add(RejectionReason.DisabledBySetting)

        for (unique in uniqueObjects) {
            when (unique.type) {
                UniqueType.Unbuildable ->
                    rejectionReasons.add(RejectionReason.Unbuildable)

                UniqueType.FoundCity -> if (civInfo.isCityState() || civInfo.isOneCityChallenger())
                    rejectionReasons.add(RejectionReason.NoSettlerForOneCityPlayers)

                UniqueType.MaxNumberBuildable -> if (civInfo.civConstructions.countConstructedObjects(this) >= unique.params[0].toInt())
                    rejectionReasons.add(RejectionReason.MaxNumberBuildable)

                else -> {}
            }
        }

        if (!civInfo.isBarbarian()) { // Barbarians don't need resources
            for ((resource, requiredAmount) in getResourceRequirements()) {
                val availableAmount = civInfo.getCivResourcesByName()[resource]!!
                if (availableAmount < requiredAmount) {
                    rejectionReasons.add(RejectionReason.ConsumesResources.toInstance(resource.getNeedMoreAmountString(requiredAmount - availableAmount)))
                }
            }
        }

        for (unique in civInfo.getMatchingUniques(UniqueType.CannotBuildUnits))
            if (this.matchesFilter(unique.params[0])) {
                if (unique.conditionals.any { it.type == UniqueType.ConditionalBelowHappiness }){
                    rejectionReasons.add(RejectionReason.CannotBeBuilt.toInstance(unique.text, true))
                }
                else rejectionReasons.add(RejectionReason.CannotBeBuilt)
            }

        return rejectionReasons
    }

    fun isBuildable(civInfo: Civilization) = getRejectionReasons(civInfo).isEmpty()

    override fun isBuildable(cityConstructions: CityConstructions): Boolean =
            getRejectionReasons(cityConstructions).isEmpty()

    override fun postBuildEvent(cityConstructions: CityConstructions, boughtWith: Stat?): Boolean {
        val civInfo = cityConstructions.city.civInfo
        val unit = civInfo.units.placeUnitNearTile(cityConstructions.city.location, name)
            ?: return false  // couldn't place the unit, so there's actually no unit =(

        //movement penalty
        if (boughtWith != null && !civInfo.gameInfo.gameParameters.godMode && !unit.hasUnique(UniqueType.MoveImmediatelyOnceBought))
            unit.currentMovement = 0f

        // If this unit has special abilities that need to be kept track of, start doing so here
        if (unit.hasUnique(UniqueType.ReligiousUnit) && civInfo.gameInfo.isReligionEnabled()) {
            unit.religion =
                if (unit.hasUnique(UniqueType.TakeReligionOverBirthCity))
                    civInfo.religionManager.religion?.name
                else cityConstructions.city.religion.getMajorityReligionName()

            unit.setupAbilityUses(cityConstructions.city)
        }

        if (this.isCivilian()) return true // tiny optimization makes save files a few bytes smaller

        addConstructionBonuses(unit, cityConstructions)

        return true
    }

    fun addConstructionBonuses(unit: MapUnit, cityConstructions: CityConstructions) {
        val civInfo = cityConstructions.city.civInfo

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
                    && civInfo.gameInfo.ruleSet.unitPromotions.values
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

    /** Implements [UniqueParameterType.BaseUnitFilter][com.unciv.models.ruleset.unique.UniqueParameterType.BaseUnitFilter] */
    fun matchesFilter(filter: String): Boolean {
        return filter.filterAndLogic { matchesFilter(it) } // multiple types at once - AND logic. Looks like:"{Military} {Land}"
            ?: when (filter) {

            unitType -> true
            name -> true
            replaces -> true
            "All" -> true

            "Melee" -> isMelee()
            "Ranged" -> isRanged()
            "Civilian" -> isCivilian()
            "Military" -> isMilitary()
            "Land" -> isLandUnit()
            "Water" -> isWaterUnit()
            "Air" -> isAirUnit()
            "non-air" -> !movesLikeAirUnits()

            "Nuclear Weapon" -> isNuclearWeapon()
            // "Great" should be deprecated, replaced by "Great Person".
            "Great Person", "Great" -> isGreatPerson()
            "Religious" -> hasUnique(UniqueType.ReligiousUnit)
            else -> {
                if (getType().matchesFilter(filter)) return true
                if (
                    // Uniques using these kinds of filters should be deprecated and replaced with adjective-only parameters
                    filter.endsWith(" units")
                    // "military units" --> "Military", using invariant locale
                    && matchesFilter(filter.removeSuffix(" units").lowercase().replaceFirstChar { it.uppercaseChar() })
                ) return true
                return uniques.contains(filter)
            }
        }
    }

    fun isGreatPerson() = getMatchingUniques(UniqueType.GreatPerson).any()
    fun isGreatPersonOfType(type: String) = getMatchingUniques(UniqueType.GreatPerson).any { it.params[0] == type }

    fun isNuclearWeapon() = hasUnique(UniqueType.NuclearWeapon)

    fun movesLikeAirUnits() = getType().getMovementType() == UnitMovementType.Air

    /** Returns resource requirements from both uniques and requiredResource field */
    override fun getResourceRequirements(): HashMap<String, Int> = resourceRequirementsInternal

    private val resourceRequirementsInternal: HashMap<String, Int> by lazy {
        val resourceRequirements = HashMap<String, Int>()
        if (requiredResource != null) resourceRequirements[requiredResource!!] = 1
        for (unique in getMatchingUniques(UniqueType.ConsumesResources))
            resourceRequirements[unique.params[1]] = unique.params[0].toInt()
        resourceRequirements
    }

    override fun requiresResource(resource: String) = getResourceRequirements().containsKey(resource)

    fun isRanged() = rangedStrength > 0
    fun isMelee() = !isRanged() && strength > 0
    fun isMilitary() = isRanged() || isMelee()
    fun isCivilian() = !isMilitary()

    val isLandUnitInternal by lazy { getType().isLandUnit() }
    fun isLandUnit() = isLandUnitInternal
    fun isWaterUnit() = getType().isWaterUnit()
    fun isAirUnit() = getType().isAirUnit()

    fun isProbablySiegeUnit() =
        (
            isRanged()
            && (uniqueObjects + getType().uniqueObjects)
                .any { it.isOfType(UniqueType.Strength)
                    && it.params[0].toInt() > 0
                    && it.conditionals.any { conditional -> conditional.isOfType(UniqueType.ConditionalVsCity) }
                }
        )

    fun getForceEvaluation(): Int {
        if (cachedForceEvaluation < 0)    evaluateForce()
        return  cachedForceEvaluation
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
                    if (unique.params[0].toInt() > 0) {
                        if (unique.conditionals.any { it.isOfType(UniqueType.ConditionalVsUnits) }) { // Bonus vs some units - a quarter of the bonus
                            power *= (unique.params[0].toInt() / 4f).toPercent()
                        } else if (
                            unique.conditionals.any {
                                it.isOfType(UniqueType.ConditionalVsCity) // City Attack - half the bonus
                                        || it.isOfType(UniqueType.ConditionalAttacking) // Attack - half the bonus
                                        || it.isOfType(UniqueType.ConditionalDefending) // Defense - half the bonus
                                        || it.isOfType(UniqueType.ConditionalFightingInTiles)
                            } // Bonus in terrain or feature - half the bonus
                        ) {
                            power *= (unique.params[0].toInt() / 2f).toPercent()
                        }
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
