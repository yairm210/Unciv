package com.unciv.models.ruleset.unit

import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.INonPerpetualConstruction
import com.unciv.logic.city.RejectionReason
import com.unciv.logic.city.RejectionReasons
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetObject
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueFlag
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.extensions.filterAndLogic
import com.unciv.ui.utils.extensions.getConsumesAmountString
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
    lateinit var unitType: String
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

    lateinit var ruleset: Ruleset


    fun getShortDescription(): String {
        val infoList = mutableListOf<String>()
        if (strength != 0) infoList += "$strength${Fonts.strength}"
        if (rangedStrength != 0) infoList += "$rangedStrength${Fonts.rangedStrength}"
        if (movement != 2) infoList += "$movement${Fonts.movement}"
        for (promotion in promotions)
            infoList += promotion.tr()
        if (replacementTextForUniques != "") infoList += replacementTextForUniques
        else for (unique in uniqueObjects) if(!unique.hasFlag(UniqueFlag.HiddenToUsers))
            infoList += unique.text.tr()
        return infoList.joinToString()
    }

    /** Generate description as multi-line string for CityScreen addSelectedConstructionTable
     * @param cityInfo Supplies civInfo to show available resources after resource requirements */
    fun getDescription(cityInfo: CityInfo): String {
        val lines = mutableListOf<String>()
        val availableResources = cityInfo.civInfo.getCivResources().associate { it.resource.name to it.amount }
        for ((resource, amount) in getResourceRequirements()) {
            val available = availableResources[resource] ?: 0
            lines += "{${resource.getConsumesAmountString(amount)}} ({[$available] available})".tr()
        }
        var strengthLine = ""
        if (strength != 0) {
            strengthLine += "$strength${Fonts.strength}, "
            if (rangedStrength != 0)
                strengthLine += "$rangedStrength${Fonts.rangedStrength}, $range${Fonts.range}, "
        }
        lines += "$strengthLine$movement${Fonts.movement}"

        if (replacementTextForUniques != "") lines += replacementTextForUniques
        else for (unique in uniqueObjects.filterNot {
            it.type == UniqueType.Unbuildable
                    || it.type == UniqueType.ConsumesResources  // already shown from getResourceRequirements
                    || it.type?.flags?.contains(UniqueFlag.HiddenToUsers) == true
        })
            lines += unique.text.tr()

        if (promotions.isNotEmpty()) {
            val prefix = "Free promotion${if (promotions.size == 1) "" else "s"}:".tr() + " "
            lines += promotions.joinToString(", ", prefix) { it.tr() }
        }

        return lines.joinToString("\n")
    }

    override fun makeLink() = "Unit/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()
        textList += FormattedLine("{Unit type}: ${unitType.tr()}")

        val stats = ArrayList<String>()
        if (strength != 0) stats += "$strength${Fonts.strength}"
        if (rangedStrength != 0) {
            stats += "$rangedStrength${Fonts.rangedStrength}"
            stats += "$range${Fonts.range}"
        }
        if (movement != 0 && ruleset.unitTypes[unitType]?.isAirUnit() != true) stats += "$movement${Fonts.movement}"
        if (stats.isNotEmpty())
            textList += FormattedLine(stats.joinToString(", "))

        if (cost > 0) {
            stats.clear()
            stats += "$cost${Fonts.production}"
            if (canBePurchasedWithStat(null, Stat.Gold)) {
                // We need what INonPerpetualConstruction.getBaseGoldCost calculates but without any game- or civ-specific modifiers
                val buyCost = (30.0 * cost.toFloat().pow(0.75f) * hurryCostModifier.toPercent()).toInt() / 10 * 10
                stats += "$buyCost${Fonts.gold}"
            }
            textList += FormattedLine(stats.joinToString(", ", "{Cost}: "))
        }

        if (replacementTextForUniques.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine(replacementTextForUniques)
        } else if (uniques.isNotEmpty()) {
            textList += FormattedLine()
            for (unique in uniqueObjects.sortedBy { it.text }) {
                if (unique.hasFlag(UniqueFlag.HiddenToUsers)) continue
                if (unique.type == UniqueType.ConsumesResources) continue  // already shown from getResourceRequirements
                textList += FormattedLine(unique)
            }
        }

        val resourceRequirements = getResourceRequirements()
        if (resourceRequirements.isNotEmpty()) {
            textList += FormattedLine()
            for ((resource, amount) in resourceRequirements) {
                textList += FormattedLine(
                    resource.getConsumesAmountString(amount),
                    link = "Resource/$resource", color = "#F42"
                )
            }
        }

        if (uniqueTo != null) {
            textList += FormattedLine()
            textList += FormattedLine("Unique to [$uniqueTo]", link = "Nation/$uniqueTo")
            if (replaces != null)
                textList += FormattedLine(
                    "Replaces [$replaces]",
                    link = "Unit/$replaces",
                    indent = 1
                )
        }

        if (requiredTech != null || upgradesTo != null || obsoleteTech != null) textList += FormattedLine()
        if (requiredTech != null) textList += FormattedLine(
            "Required tech: [$requiredTech]",
            link = "Technology/$requiredTech"
        )

        val canUpgradeFrom = ruleset.units
            .filterValues {
                (it.upgradesTo == name || it.upgradesTo != null && it.upgradesTo == replaces)
                        && (it.uniqueTo == uniqueTo || it.uniqueTo == null)
            }.keys
        if (canUpgradeFrom.isNotEmpty()) {
            if (canUpgradeFrom.size == 1)
                textList += FormattedLine(
                    "Can upgrade from [${canUpgradeFrom.first()}]",
                    link = "Unit/${canUpgradeFrom.first()}"
                )
            else {
                textList += FormattedLine()
                textList += FormattedLine("Can upgrade from:")
                for (unitName in canUpgradeFrom.sorted())
                    textList += FormattedLine(unitName, indent = 2, link = "Unit/$unitName")
                textList += FormattedLine()
            }
        }

        if (upgradesTo != null) textList += FormattedLine(
            "Upgrades to [$upgradesTo]",
            link = "Unit/$upgradesTo"
        )
        if (obsoleteTech != null) textList += FormattedLine(
            "Obsolete with [$obsoleteTech]",
            link = "Technology/$obsoleteTech"
        )

        if (promotions.isNotEmpty()) {
            textList += FormattedLine()
            promotions.withIndex().forEach {
                textList += FormattedLine(
                    when {
                        promotions.size == 1 -> "{Free promotion:} "
                        it.index == 0 -> "{Free promotions:} "
                        else -> ""
                    } + "{${it.value.tr()}}" +   // tr() not redundant as promotion names now can use []
                            (if (promotions.size == 1 || it.index == promotions.size - 1) "" else ","),
                    link = "Promotions/${it.value}",
                    indent = if (it.index == 0) 0 else 1
                )
            }
        }

        val seeAlso = ArrayList<FormattedLine>()
        for ((other, unit) in ruleset.units) {
            if (unit.replaces == name || uniques.contains("[$name]")) {
                seeAlso += FormattedLine(other, link = "Unit/$other", indent = 1)
            }
        }
        if (seeAlso.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{See also}:")
            textList += seeAlso
        }

        return textList
    }

    fun getMapUnit(civInfo: CivilizationInfo): MapUnit {
        val unit = MapUnit()
        unit.name = name
        unit.civInfo = civInfo
        unit.owner = civInfo.civName

        // must be after setting name & civInfo because it sets the baseUnit according to the name
        // and the civInfo is required for using `hasUnique` when determining its movement options
        unit.setTransients(civInfo.gameInfo.ruleSet)

        return unit
    }

    override fun getProductionCost(civInfo: CivilizationInfo): Int {
        var productionCost = cost.toFloat()
        if (civInfo.isCityState())
            productionCost *= 1.5f
        productionCost *= if (civInfo.isPlayerCivilization())
                civInfo.getDifficulty().unitCostModifier
            else
                civInfo.gameInfo.getDifficulty().aiUnitCostModifier
        productionCost *= civInfo.gameInfo.speed.productionCostModifier
        return productionCost.toInt()
    }

    override fun canBePurchasedWithStat(cityInfo: CityInfo?, stat: Stat): Boolean {
        if (cityInfo == null) return super.canBePurchasedWithStat(cityInfo, stat)
        val conditionalState = StateForConditionals(civInfo = cityInfo.civInfo, cityInfo = cityInfo)

        return (cityInfo.getMatchingUniques(UniqueType.BuyUnitsIncreasingCost, conditionalState)
                .any {
                    it.params[2] == stat.name
                    && matchesFilter(it.params[0])
                    && cityInfo.matchesFilter(it.params[3])
                }
            || cityInfo.getMatchingUniques(UniqueType.BuyUnitsByProductionCost, conditionalState)
                .any { it.params[1] == stat.name && matchesFilter(it.params[0]) }
            || cityInfo.getMatchingUniques(UniqueType.BuyUnitsWithStat, conditionalState)
                .any {
                    it.params[1] == stat.name
                    && matchesFilter(it.params[0])
                    && cityInfo.matchesFilter(it.params[2])
                }
            || cityInfo.getMatchingUniques(UniqueType.BuyUnitsForAmountStat, conditionalState)
                .any {
                    it.params[2] == stat.name
                    && matchesFilter(it.params[0])
                    && cityInfo.matchesFilter(it.params[3])
                }
            || return super.canBePurchasedWithStat(cityInfo, stat)
        )
    }

    override fun getBaseBuyCost(cityInfo: CityInfo, stat: Stat): Int? {
        if (stat == Stat.Gold) return getBaseGoldCost(cityInfo.civInfo).toInt()
        val conditionalState = StateForConditionals(civInfo = cityInfo.civInfo, cityInfo = cityInfo)

        return sequence {
            val baseCost = super.getBaseBuyCost(cityInfo, stat)
            if (baseCost != null)
                yield(baseCost)
            yieldAll(cityInfo.getMatchingUniques(UniqueType.BuyUnitsIncreasingCost, conditionalState)
                .filter {
                    it.params[2] == stat.name
                    && matchesFilter(it.params[0])
                    && cityInfo.matchesFilter(it.params[3])
                }.map {
                    getCostForConstructionsIncreasingInPrice(
                        it.params[1].toInt(),
                        it.params[4].toInt(),
                        cityInfo.civInfo.civConstructions.boughtItemsWithIncreasingPrice[name] ?: 0
                    )
                }
            )
            yieldAll(cityInfo.getMatchingUniques(UniqueType.BuyUnitsByProductionCost, conditionalState)
                .filter { it.params[1] == stat.name && matchesFilter(it.params[0]) }
                .map { getProductionCost(cityInfo.civInfo) * it.params[2].toInt() }
            )
            if (cityInfo.getMatchingUniques(UniqueType.BuyUnitsWithStat, conditionalState)
                .any {
                    it.params[1] == stat.name
                    && matchesFilter(it.params[0])
                    && cityInfo.matchesFilter(it.params[2])
                }
            ) {
                yield(cityInfo.civInfo.getEra().baseUnitBuyCost)
            }
            yieldAll(cityInfo.getMatchingUniques(UniqueType.BuyUnitsForAmountStat, conditionalState)
                .filter {
                    it.params[2] == stat.name
                    && matchesFilter(it.params[0])
                    && cityInfo.matchesFilter(it.params[3])
                }.map { it.params[1].toInt() }
            )
        }.minOrNull()
    }

    override fun getStatBuyCost(cityInfo: CityInfo, stat: Stat): Int? {
        var cost = getBaseBuyCost(cityInfo, stat)?.toDouble() ?: return null

        for (unique in cityInfo.getMatchingUniques(UniqueType.BuyUnitsDiscount)) {
            if (stat.name == unique.params[0] && matchesFilter(unique.params[1]))
                cost *= unique.params[2].toPercent()
        }
        for (unique in cityInfo.getMatchingUniques(UniqueType.BuyItemsDiscount))
            if (stat.name == unique.params[0])
                cost *= unique.params[1].toPercent()

        return (cost / 10f).toInt() * 10
    }

    fun getDisbandGold(civInfo: CivilizationInfo) = getBaseGoldCost(civInfo).toInt() / 20

    override fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean {
        val rejectionReasons = getRejectionReasons(cityConstructions)
        return rejectionReasons.none { !it.shouldShow }
            || (
                canBePurchasedWithAnyStat(cityConstructions.cityInfo)
                && rejectionReasons.all { it.rejectionReason == RejectionReason.Unbuildable }
            )
    }

    override fun getRejectionReasons(cityConstructions: CityConstructions): RejectionReasons {
        val rejectionReasons = RejectionReasons()
        if (isWaterUnit() && !cityConstructions.cityInfo.isCoastal())
            rejectionReasons.add(RejectionReason.WaterUnitsInCoastalCities)
        if (isAirUnit()) {
            val fakeUnit = getMapUnit(cityConstructions.cityInfo.civInfo)
            val canUnitEnterTile = fakeUnit.movement.canMoveTo(cityConstructions.cityInfo.getCenterTile())
            if (!canUnitEnterTile)
                rejectionReasons.add(RejectionReason.NoPlaceToPutUnit)
        }
        val civInfo = cityConstructions.cityInfo.civInfo
        for (unique in uniqueObjects) {
            when (unique.type) {
                UniqueType.OnlyAvailableWhen -> if (!unique.conditionalsApply(civInfo, cityConstructions.cityInfo))
                    rejectionReasons.add(RejectionReason.ShouldNotBeDisplayed)

                UniqueType.RequiresPopulation -> if (unique.params[0].toInt() > cityConstructions.cityInfo.population.population)
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

    fun getRejectionReasons(civInfo: CivilizationInfo): RejectionReasons {
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

    fun isBuildable(civInfo: CivilizationInfo) = getRejectionReasons(civInfo).isEmpty()

    override fun isBuildable(cityConstructions: CityConstructions): Boolean {
        return getRejectionReasons(cityConstructions).isEmpty()
    }

    fun isBuildableIgnoringTechs(civInfo: CivilizationInfo): Boolean {
        val rejectionReasons = getRejectionReasons(civInfo)
        return rejectionReasons.isOKIgnoringRequirements(ignoreTechPolicyEraWonderRequirements = true)
    }

    override fun postBuildEvent(cityConstructions: CityConstructions, boughtWith: Stat?): Boolean {
        val civInfo = cityConstructions.cityInfo.civInfo
        val unit = civInfo.placeUnitNearTile(cityConstructions.cityInfo.location, name)
            ?: return false  // couldn't place the unit, so there's actually no unit =(

        //movement penalty
        if (boughtWith != null && !civInfo.gameInfo.gameParameters.godMode && !unit.hasUnique(UniqueType.MoveImmediatelyOnceBought))
            unit.currentMovement = 0f

        // If this unit has special abilities that need to be kept track of, start doing so here
        if (unit.hasUnique(UniqueType.ReligiousUnit) && civInfo.gameInfo.isReligionEnabled()) {
            unit.religion =
                if (unit.hasUnique(UniqueType.TakeReligionOverBirthCity))
                    civInfo.religionManager.religion?.name
                else cityConstructions.cityInfo.religion.getMajorityReligionName()

            unit.setupAbilityUses(cityConstructions.cityInfo)
        }

        if (this.isCivilian()) return true // tiny optimization makes save files a few bytes smaller

        addConstructionBonuses(unit, cityConstructions)

        return true
    }

    fun addConstructionBonuses(unit: MapUnit, cityConstructions: CityConstructions) {
        val civInfo = cityConstructions.cityInfo.civInfo

        @Suppress("LocalVariableName")
        var XP = 0

        for (unique in
        cityConstructions.cityInfo.getMatchingUniques(UniqueType.UnitStartingExperience)
            .filter { cityConstructions.cityInfo.matchesFilter(it.params[2]) }
        ) {
            if (unit.matchesFilter(unique.params[0]))
                XP += unique.params[1].toInt()
        }
        unit.promotions.XP = XP

        for (unique in cityConstructions.cityInfo.getMatchingUniques(UniqueType.UnitStartingPromotions)
            .filter { cityConstructions.cityInfo.matchesFilter(it.params[1]) }) {
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


    fun getDirectUpgradeUnit(civInfo: CivilizationInfo): BaseUnit {
        return civInfo.getEquivalentUnit(upgradesTo!!)
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
