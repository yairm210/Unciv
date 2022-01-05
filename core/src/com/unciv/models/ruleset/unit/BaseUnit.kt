package com.unciv.models.ruleset.unit

import com.unciv.logic.city.*
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
import com.unciv.ui.utils.toPercent
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.pow

// This is BaseUnit because Unit is already a base Kotlin class and to avoid mixing the two up

/** This is the basic info of the units, as specified in Units.json,
 in contrast to MapUnit, which is a specific unit of a certain type that appears on the map */
class BaseUnit : RulesetObject(), INonPerpetualConstruction {

    var cost: Int = 0
    override var hurryCostModifier: Int = 0
    var movement: Int = 0
    var strength: Int = 0
    var rangedStrength: Int = 0
    var religiousStrength: Int = 0
    var range: Int = 2
    var interceptRange = 0
    lateinit var unitType: String
    fun getType() = ruleset.unitTypes[unitType]!!
    var requiredTech: String? = null
    var requiredResource: String? = null

    override fun getUniqueTarget() = UniqueTarget.Unit

    private var replacementTextForUniques = ""
    var promotions = HashSet<String>()
    var obsoleteTech: String? = null
    var upgradesTo: String? = null
    val specialUpgradesTo: String? by lazy { 
        uniqueObjects
        .filter { it.placeholderText == "May upgrade to [] through ruins-like effects"}
        .map { it.params[0] }
        .firstOrNull() 
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
        else for (unique in uniques)
            infoList += unique.tr()
        return infoList.joinToString()
    }

    /** Generate description as multi-line string for Nation description addUniqueUnitsText and CityScreen addSelectedConstructionTable */
    fun getDescription(): String {
        val lines = mutableListOf<String>()
        var strengthLine = ""
        if (strength != 0) {
            strengthLine += "$strength${Fonts.strength}, "
            if (rangedStrength != 0)
                strengthLine += "$rangedStrength${Fonts.rangedStrength}, $range${Fonts.range}, "
        }
        lines += "$strengthLine$movement${Fonts.movement}"

        if (replacementTextForUniques != "") lines += replacementTextForUniques
        else for (unique in uniques.filterNot {
            it.startsWith("Hidden ") && it.endsWith(" disabled") || it == UniqueType.Unbuildable.text
        })
            lines += unique.tr()

        if (promotions.isNotEmpty()) {
            val prefix = "Free promotion${if (promotions.size == 1) "" else "s"}:".tr() + " "
            lines += promotions.joinToString(", ", prefix) { it.tr() }
        }

        return lines.joinToString("\n")
    }

    override fun makeLink() = "Unit/$name"

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

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

        for (unique in uniqueObjects.filter { !it.hasFlag(UniqueFlag.HiddenToUsers) }) when (unique.placeholderText) {
            "Unique to []" -> textList += FormattedLine(unique.text, link="Nation/${unique.params[0]}")
            "Replaces []" -> textList += FormattedLine(unique.text, link = "Unit/${unique.params[0]}", indent = 1)
            "Required tech: []" -> textList += FormattedLine(unique.text, link="Technology/${unique.params[0]}")
            "Obsolete with []"-> textList += FormattedLine(unique.text, link = "Technology/${unique.params[0]}")
            "Upgrades to []"-> textList += FormattedLine("Upgrades to [$upgradesTo]", link = "Unit/${unique.params[0]}")
            UniqueType.RequiresAnotherBuilding.placeholderText -> textList += FormattedLine(unique.text, link="Building/${unique.params[0]}")
            UniqueType.RequiresBuildingInAllCities.placeholderText -> textList += FormattedLine(unique.text, link="Building/${unique.params[0]}")
            UniqueType.ConsumesResources.placeholderText -> textList += FormattedLine(unique.text, link="Resources/${unique.params[1]}", color="#F42" )
            else -> textList += FormattedLine(unique)
        }

        if (replacementTextForUniques != "") {
            textList += FormattedLine()
            textList += FormattedLine(replacementTextForUniques)
        }

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

        if (promotions.isNotEmpty()) {
            textList += FormattedLine()
            promotions.withIndex().forEach {
                textList += FormattedLine(
                    when {
                        promotions.size == 1 -> "{Free promotion:} "
                        it.index == 0 -> "{Free promotions:} "
                        else -> ""
                    } + "{${it.value}}" +
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
        productionCost *= civInfo.gameInfo.gameParameters.gameSpeed.modifier
        return productionCost.toInt()
    }

    override fun canBePurchasedWithStat(cityInfo: CityInfo?, stat: Stat): Boolean {
        if (cityInfo == null) return super.canBePurchasedWithStat(cityInfo, stat)
        val conditionalState = StateForConditionals(civInfo = cityInfo.civInfo, cityInfo = cityInfo)
        
        return (
            cityInfo.getMatchingUniques(UniqueType.BuyUnitsIncreasingCostEra, conditionalState)
                .any {
                    it.params[2] == stat.name
                    && cityInfo.civInfo.getEraNumber() >= ruleset.eras[it.params[4]]!!.eraNumber
                    && matchesFilter(it.params[0])
                    && cityInfo.matchesFilter(it.params[3])
                }
            || cityInfo.getMatchingUniques(UniqueType.BuyUnitsIncreasingCost, conditionalState)
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
            // Deprecated since 3.17.9
                yieldAll(cityInfo.getMatchingUniques(UniqueType.BuyUnitsIncreasingCostEra, conditionalState)
                    .filter {
                        it.params[2] == stat.name
                        && matchesFilter(it.params[0])
                        && cityInfo.matchesFilter(it.params[3])
                        && cityInfo.civInfo.getEraNumber() >= ruleset.eras[it.params[4]]!!.eraNumber
                    }.map {
                        getCostForConstructionsIncreasingInPrice(
                            it.params[1].toInt(),
                            it.params[5].toInt(),
                            cityInfo.civInfo.civConstructions.boughtItemsWithIncreasingPrice[name] ?: 0
                        )
                    }
                )
            //
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
        var cost = getBaseBuyCost(cityInfo, stat)?.toDouble()
        if (cost == null) return null

        for (unique in cityInfo.getMatchingUniques("[] cost of purchasing [] units []%")) {
            if (stat.name == unique.params[0] && matchesFilter(unique.params[1]))
                cost *= unique.params[2].toPercent()
        }
        for (unique in cityInfo.getMatchingUniques("[] cost of purchasing items in cities []%"))
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
                && rejectionReasons.all { it == RejectionReason.Unbuildable }
            )
    }

    override fun getRejectionReasons(cityConstructions: CityConstructions): RejectionReasons {
        val rejectionReasons = RejectionReasons()
        if (isWaterUnit() && !cityConstructions.cityInfo.isCoastal())
            rejectionReasons.add(RejectionReason.WaterUnitsInCoastalCities)
        val civInfo = cityConstructions.cityInfo.civInfo
        for (unique in uniqueObjects) {
            when (unique.placeholderText) {
                UniqueType.NotDisplayedWithout.placeholderText -> {
                    val filter = unique.params[0]
                    if (filter in civInfo.gameInfo.ruleSet.tileResources && !civInfo.hasResource(filter)
                            || filter in civInfo.gameInfo.ruleSet.buildings && !cityConstructions.containsBuildingOrEquivalent(filter))
                        rejectionReasons.add(RejectionReason.ShouldNotBeDisplayed)
                }

                "Requires at least [] population" -> if (unique.params[0].toInt() > cityConstructions.cityInfo.population.population)
                    rejectionReasons.add(RejectionReason.PopulationRequirement.apply { errorMessage = unique.text })
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

        for (unique in uniqueObjects) {
            when (unique.placeholderText) {
                UniqueType.Unbuildable.placeholderText ->
                    rejectionReasons.add(RejectionReason.Unbuildable)

                "Required tech: []" -> if (!civInfo.tech.isResearched(requiredTech!!))
                    rejectionReasons.add(RejectionReason.RequiresTech.apply { errorMessage = unique.text })

                "Obsolete with []" -> if (civInfo.tech.isResearched(obsoleteTech!!))
                    rejectionReasons.add(RejectionReason.Obsoleted.apply { errorMessage = unique.text })

                "Unique to []" -> if (uniqueTo != civInfo.civName)
                    rejectionReasons.add(RejectionReason.UniqueToOtherNation.apply { errorMessage = unique.text })

                UniqueType.FoundCity.placeholderText-> if (civInfo.isCityState() || civInfo.isOneCityChallenger())
                    rejectionReasons.add(RejectionReason.NoSettlerForOneCityPlayers)

                UniqueType.ConsumesResources.placeholderText -> if (!civInfo.isBarbarian() // Barbarians don't need resources
                        && civInfo.getCivResourcesByName()[unique.params[1]]!! < unique.params[0].toInt()) {
                        rejectionReasons.add(RejectionReason.ConsumesResources.apply { errorMessage = unique.text })
                    }

                // "Unlocked with []" has been Deprecated, so we delete it,
                // "Requires []" is only used in the unit unique "Requires [Manhattan Project]" now
                // it may be used in required-policy in the future, but we have a better unique "Required policy: [...]" to replace it
                "Requires []" -> {
                    val filter = unique.params[0]
                    when {
                        ruleSet.policies.contains(filter) ->
                            if (!civInfo.policies.isAdopted(filter))
                                rejectionReasons.add(RejectionReason.RequiresPolicy.apply { errorMessage = unique.text })
                        ruleSet.eras.contains(filter) ->
                            if (civInfo.getEraNumber() < ruleSet.eras[filter]!!.eraNumber)
                                rejectionReasons.add(RejectionReason.UnlockedWithEra.apply { errorMessage = unique.text })
                    }
                }

            }
        }

        if (ruleSet.units.values.any { it.uniqueTo == civInfo.civName && it.replaces == name })
            rejectionReasons.add(RejectionReason.ReplacedByOurUnique.apply { this.errorMessage = "Our unique unit replaces this" })

        if (!civInfo.gameInfo.gameParameters.nuclearWeaponsEnabled && isNuclearWeapon())
            rejectionReasons.add(RejectionReason.DisabledBySetting)

        if (civInfo.getMatchingUniques(UniqueType.CannotBuildUnits, StateForConditionals(civInfo=civInfo))
                        .any { matchesFilter(it.params[0]) }
        )
            rejectionReasons.add(RejectionReason.CannotBeBuilt)
            
        return rejectionReasons
    }

    fun isBuildable(civInfo: CivilizationInfo) = getRejectionReasons(civInfo).isEmpty()

    override fun isBuildable(cityConstructions: CityConstructions): Boolean {
        return getRejectionReasons(cityConstructions).isEmpty()
    }

    fun isBuildableIgnoringTechs(civInfo: CivilizationInfo): Boolean {
        val rejectionReasons = getRejectionReasons(civInfo)
        return rejectionReasons.filterTechPolicyEraWonderRequirements().isEmpty()
    }

    override fun postBuildEvent(cityConstructions: CityConstructions, boughtWith: Stat?): Boolean {
        val civInfo = cityConstructions.cityInfo.civInfo
        val unit = civInfo.placeUnitNearTile(cityConstructions.cityInfo.location, name)
            ?: return false  // couldn't place the unit, so there's actually no unit =(

        //movement penalty
        if (boughtWith != null && !civInfo.gameInfo.gameParameters.godMode && !unit.hasUnique("Can move immediately once bought"))
            unit.currentMovement = 0f

        // If this unit has special abilities that need to be kept track of, start doing so here
        if (unit.hasUnique(UniqueType.ReligiousUnit) && civInfo.gameInfo.isReligionEnabled()) {
            unit.religion =  
                if (unit.hasUnique("Takes your religion over the one in their birth city"))
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
        cityConstructions.cityInfo.getMatchingUniques("New [] units start with [] Experience []")
            .filter { cityConstructions.cityInfo.matchesFilter(it.params[2]) }
        ) {
            if (unit.matchesFilter(unique.params[0]))
                XP += unique.params[1].toInt()
        }
        unit.promotions.XP = XP        

        for (unique in cityConstructions.cityInfo.getMatchingUniques("All newly-trained [] units [] receive the [] promotion")
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

    fun matchesFilter(filter: String): Boolean {
        if (filter.contains('{')) // multiple types at once - AND logic. Looks like:"{Military} {Land}"
            return filter.removePrefix("{").removeSuffix("}").split("} {")
                .all { matchesFilter(it) }

        return when (filter) {
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

    fun isGreatPerson() = uniqueObjects.any { it.placeholderText == "Great Person - []" }

    fun isNuclearWeapon() = uniqueObjects.any { it.placeholderText == "Nuclear weapon of Strength []" }

    fun movesLikeAirUnits() = getType().getMovementType() == UnitMovementType.Air

    override fun getResourceRequirements(): HashMap<String, Int> = resourceRequirementsInternal

    private val resourceRequirementsInternal: HashMap<String, Int> by lazy {
        val resourceRequirements = HashMap<String, Int>()
        for (unique in uniqueObjects.filter { it.isOfType(UniqueType.ConsumesResources) })
            resourceRequirements[unique.params[1]] = unique.params[0].toInt()
        resourceRequirements
    }

    override fun requiresResource(resource: String): Boolean {
        for (unique in getMatchingUniques(UniqueType.ConsumesResources)) {
            if (unique.params[1] == resource) return true
        }
        return false
    }

    fun isRanged() = rangedStrength > 0
    fun isMelee() = !isRanged() && strength > 0
    fun isMilitary() = isRanged() || isMelee()
    fun isCivilian() = !isMilitary()

    fun isLandUnit() = getType().isLandUnit()
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

        if (uniqueObjects.any { it.placeholderText =="Self-destructs when attacking" } )
            power /= 2
        if (uniqueObjects.any { it.placeholderText =="Nuclear weapon of Strength []" } )
            power += 4000

        // Uniques
        val allUniques = uniqueObjects.asSequence() +
            promotions.asSequence()
                .mapNotNull { ruleset.unitPromotions[it] }
                .flatMap { it.uniqueObjects }

        for (unique in allUniques) {
            when {
                unique.isOfType(UniqueType.Strength) && unique.params[0].toInt() > 0 -> {
                    if (unique.conditionals.any { it.isOfType(UniqueType.ConditionalVsUnits) } ) { // Bonus vs some units - a quarter of the bonus
                        power *= (unique.params[0].toInt() / 4f).toPercent()
                    } else if (
                        unique.conditionals.any {
                            it.isOfType(UniqueType.ConditionalVsCity) // City Attack - half the bonus
                            || it.isOfType(UniqueType.ConditionalAttacking) // Attack - half the bonus
                            || it.isOfType(UniqueType.ConditionalDefending) // Defense - half the bonus 
                            || it.isOfType(UniqueType.ConditionalFightingInTiles) } // Bonus in terrain or feature - half the bonus
                    ) {
                        power *= (unique.params[0].toInt() / 2f).toPercent()
                    }
                }
                unique.isOfType(UniqueType.StrengthNearCapital) && unique.params[0].toInt() > 0 ->
                    power *= (unique.params[0].toInt() / 4f).toPercent()  // Bonus decreasing with distance from capital - not worth much most of the map???

                unique.placeholderText == "May Paradrop up to [] tiles from inside friendly territory" // Paradrop - 25% bonus
                    -> power += power / 4
                unique.isOfType(UniqueType.MustSetUp) // Must set up - 20 % penalty
                    -> power -= power / 5
                unique.placeholderText == "[] additional attacks per turn" // Extra attacks - 20% bonus per extra attack
                    -> power += (power * unique.params[0].toInt()) / 5
            }
        }

        cachedForceEvaluation = power.toInt()
    }
}
