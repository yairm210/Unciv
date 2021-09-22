package com.unciv.models.ruleset.unit

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.city.*
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.Unique
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.INamed
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.ICivilopediaText
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.toPercent
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.math.pow

// This is BaseUnit because Unit is already a base Kotlin class and to avoid mixing the two up

/** This is the basic info of the units, as specified in Units.json,
 in contrast to MapUnit, which is a specific unit of a certain type that appears on the map */
class BaseUnit : INamed, INonPerpetualConstruction, ICivilopediaText {

    override lateinit var name: String
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
    private var requiredResource: String? = null
    override var uniques = ArrayList<String>() // Can not be a hashset as that would remove doubles
    override val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it, UniqueTarget.Unit, name) } }
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

    override var civilopediaText = listOf<FormattedLine>()

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
        for ((resource, amount) in getResourceRequirements()) {
            lines += if (amount == 1) "Consumes 1 [$resource]".tr()
                     else "Consumes [$amount] [$resource]".tr()
        }
        var strengthLine = ""
        if (strength != 0) {
            strengthLine += "$strength${Fonts.strength}, "
            if (rangedStrength != 0)
                strengthLine += "$rangedStrength${Fonts.rangedStrength}, $range${Fonts.range}, "
        }
        lines += "$strengthLine$movement${Fonts.movement}"

        if (replacementTextForUniques != "") lines += replacementTextForUniques
        else for (unique in uniques.filterNot {
            it.startsWith("Hidden ") && it.endsWith(" disabled") || it == "Unbuildable"
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
            if (canBePurchasedWithStat(null, Stat.Gold))
                stats += "${getBaseGoldCost(UncivGame.Current.gameInfo.currentPlayerCiv).toInt() / 10 * 10}${Fonts.gold}"
            textList += FormattedLine(stats.joinToString(", ", "{Cost}: "))
        }

        if (replacementTextForUniques != "") {
            textList += FormattedLine()
            textList += FormattedLine(replacementTextForUniques)
        } else if (uniques.isNotEmpty()) {
            textList += FormattedLine()
            for (uniqueObject in uniqueObjects.sortedBy { it.text })
                textList += FormattedLine(uniqueObject)
        }

        val resourceRequirements = getResourceRequirements()
        if (resourceRequirements.isNotEmpty()) {
            textList += FormattedLine()
            for ((resource, amount) in resourceRequirements) {
                textList += FormattedLine(
                    if (amount == 1) "Consumes 1 [$resource]" else "Consumes [$amount] [$resource]",
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
        // May buy [unitFilter] units for [amount] [Stat] [cityFilter] starting from the [eraName] at an increasing price ([amount])
        if (cityInfo != null && cityInfo.civInfo.getMatchingUniques("May buy [] units for [] [] [] starting from the [] at an increasing price ([])")
            .any { 
                matchesFilter(it.params[0])
                && cityInfo.matchesFilter(it.params[3])        
                && cityInfo.civInfo.getEraNumber() >= ruleset.eras[it.params[4]]!!.eraNumber 
                && it.params[2] == stat.name
            }
        ) return true
        
        // May buy [unitFilter] units for [amount] [Stat] [cityFilter] at an increasing price ([amount])
        if (cityInfo != null && cityInfo.civInfo.getMatchingUniques("May buy [] units for [] [] [] at an increasing price ([])")
            .any {
                matchesFilter(it.params[0])
                && cityInfo.matchesFilter(it.params[3])
                && it.params[2] == stat.name
            }
        ) return true
        
        return super.canBePurchasedWithStat(cityInfo, stat)
    }
    
    private fun getCostForConstructionsIncreasingInPrice(baseCost: Int, increaseCost: Int, previouslyBought: Int): Int {
        return (baseCost + increaseCost / 2f * ( previouslyBought * previouslyBought + previouslyBought )).toInt()        
    }

    override fun getBaseBuyCost(cityInfo: CityInfo, stat: Stat): Int? {
        if (stat == Stat.Gold) return getBaseGoldCost(cityInfo.civInfo).toInt()
        return (
            sequenceOf(super.getBaseBuyCost(cityInfo, stat)).filterNotNull()
                // May buy [unitFilter] units for [amount] [Stat] starting from the [eraName] at an increasing price ([amount])
            + (cityInfo.civInfo.getMatchingUniques("May buy [] units for [] [] [] starting from the [] at an increasing price ([])")
                .filter {
                    matchesFilter(it.params[0])
                    && cityInfo.matchesFilter(it.params[3])
                    && cityInfo.civInfo.getEraNumber() >= ruleset.eras[it.params[4]]!!.eraNumber
                    && it.params[2] == stat.name
                }.map {
                    getCostForConstructionsIncreasingInPrice(
                        it.params[1].toInt(),
                        it.params[5].toInt(),
                        cityInfo.civInfo.civConstructions.boughtItemsWithIncreasingPrice[name] ?: 0
                    )
                }
            )
            + (cityInfo.civInfo.getMatchingUniques("May buy [] units for [] [] [] at an increasing price ([])")
                .filter {
                    matchesFilter(it.params[0])
                    && cityInfo.matchesFilter(it.params[3])
                    && it.params[2] == stat.name
                }.map {
                    getCostForConstructionsIncreasingInPrice(
                        it.params[1].toInt(),
                        it.params[4].toInt(),
                        cityInfo.civInfo.civConstructions.boughtItemsWithIncreasingPrice[name] ?: 0
                    )        
                }
            )
        ).minOrNull()
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
        for (unique in uniqueObjects.filter { it.placeholderText == "Not displayed as an available construction without []" }) {
            val filter = unique.params[0]
            if (filter in civInfo.gameInfo.ruleSet.tileResources && !civInfo.hasResource(filter)
                    || filter in civInfo.gameInfo.ruleSet.buildings && !cityConstructions.containsBuildingOrEquivalent(filter))
                rejectionReasons.add(RejectionReason.ShouldNotBeDisplayed)
        }
        val civRejectionReasons = getRejectionReasons(civInfo)
        if (civRejectionReasons.isNotEmpty()) {
            rejectionReasons.addAll(civRejectionReasons)
        }
        for (unique in uniqueObjects.filter { it.placeholderText == "Requires at least [] population" })
            if (unique.params[0].toInt() > cityConstructions.cityInfo.population.population)
                rejectionReasons.add(RejectionReason.PopulationRequirement)
        return rejectionReasons
    }

    fun getRejectionReasons(civInfo: CivilizationInfo): RejectionReasons {
        val rejectionReasons = RejectionReasons()
        val ruleSet = civInfo.gameInfo.ruleSet

        if (uniques.contains("Unbuildable")) 
            rejectionReasons.add(RejectionReason.Unbuildable)

        if (requiredTech != null && !civInfo.tech.isResearched(requiredTech!!)) 
            rejectionReasons.add(RejectionReason.RequiresTech.apply { this.errorMessage = "$requiredTech not researched" }) 
        if (obsoleteTech != null && civInfo.tech.isResearched(obsoleteTech!!))
            rejectionReasons.add(RejectionReason.Obsoleted.apply { this.errorMessage = "Obsolete by $obsoleteTech" })

        if (uniqueTo != null && uniqueTo != civInfo.civName) 
            rejectionReasons.add(RejectionReason.UniqueToOtherNation.apply { this.errorMessage = "Unique to $uniqueTo" })
        if (ruleSet.units.values.any { it.uniqueTo == civInfo.civName && it.replaces == name })
            rejectionReasons.add(RejectionReason.ReplacedByOurUnique.apply { this.errorMessage = "Our unique unit replaces this" })

        if (!civInfo.gameInfo.gameParameters.nuclearWeaponsEnabled && isNuclearWeapon()) 
            rejectionReasons.add(RejectionReason.DisabledBySetting)

        for (unique in uniqueObjects) {
            if (unique.placeholderText != "Unlocked with []" && unique.placeholderText != "Requires []") continue
            val filter = unique.params[0]
            when {
                ruleSet.technologies.contains(filter) -> 
                    if (!civInfo.tech.isResearched(filter)) 
                        rejectionReasons.add(RejectionReason.RequiresTech.apply { errorMessage = unique.text })
                ruleSet.policies.contains(filter) ->
                    if (!civInfo.policies.isAdopted(filter))
                        rejectionReasons.add(RejectionReason.RequiresPolicy.apply { errorMessage = unique.text })
                ruleSet.eras.contains(filter) ->
                    if (civInfo.getEraNumber() < ruleSet.eras[filter]!!.eraNumber)
                        rejectionReasons.add(RejectionReason.UnlockedWithEra.apply { errorMessage = unique.text })
                ruleSet.buildings.contains(filter) ->
                    if (civInfo.cities.none { it.cityConstructions.containsBuildingOrEquivalent(filter) })
                        rejectionReasons.add(RejectionReason.RequiresBuildingInSomeCity.apply { errorMessage = unique.text })
            }
        }

        for ((resource, amount) in getResourceRequirements())
            if (civInfo.getCivResourcesByName()[resource]!! < amount) {
                rejectionReasons.add(RejectionReason.ConsumesResources.apply {
                    errorMessage = "Consumes [$amount] [$resource]"
                })
            }

        if (uniques.contains(Constants.settlerUnique) &&
            (civInfo.isCityState() || civInfo.isOneCityChallenger())
        )
            rejectionReasons.add(RejectionReason.NoSettlerForOneCityPlayers)
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
        if (unit.hasUnique("Religious Unit") && civInfo.gameInfo.isReligionEnabled()) {
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

    override fun toString(): String = name

    fun getReplacedUnit(ruleset: Ruleset): BaseUnit {
        return if (replaces == null) this
        else ruleset.units[replaces!!]!!
    }

    fun matchesFilter(filter: String): Boolean {

        return when (filter) {
            unitType -> true
            name -> true
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
            "Religious" -> uniques.contains("Religious Unit")
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

    override fun getResourceRequirements(): HashMap<String, Int> {
        val resourceRequirements = HashMap<String, Int>()
        if (requiredResource != null) resourceRequirements[requiredResource!!] = 1
        for (unique in uniqueObjects)
            if (unique.isOfType(UniqueType.ConsumesResources))
                resourceRequirements[unique.params[1]] = unique.params[0].toInt()
        return resourceRequirements
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
                .any { it.placeholderText == "+[]% Strength vs []" && it.params[1] == "City" }
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

        var power = strength.toFloat().pow(1.5f).toInt()
        var rangedPower = rangedStrength.toFloat().pow(1.45f).toInt()

        // Value ranged naval units less
        if (isWaterUnit()) {
            rangedPower /= 2
        }
        if (rangedPower > 0)
            power = rangedPower

        // Replicates the formula from civ V, which is a lower multiplier than probably intended, because math
        // They did fix it in BNW so it was completely bugged and always 1, again math
        power = (power * movement.toFloat().pow(0.3f)).toInt()

        if (uniqueObjects.any { it.placeholderText =="Self-destructs when attacking" } )
            power /= 2
        if (uniqueObjects.any { it.placeholderText =="Nuclear weapon of Strength []" } )
            power += 4000

        // Uniques
        for (unique in uniqueObjects) {

            when {
                unique.placeholderText == "+[]% Strength vs []" && unique.params[1] == "City" // City Attack - half the bonus
                    -> power += (power * unique.params[0].toInt()) / 200
                unique.placeholderText == "+[]% Strength vs []" && unique.params[1] != "City" // Bonus vs something else - a quarter of the bonus
                    -> power += (power * unique.params[0].toInt()) / 400
                unique.placeholderText == "+[]% Strength when attacking" // Attack - half the bonus
                    -> power += (power * unique.params[0].toInt()) / 200
                unique.placeholderText == "+[]% Strength when defending" // Defense - half the bonus
                    -> power += (power * unique.params[0].toInt()) / 200
                unique.placeholderText == "May Paradrop up to [] tiles from inside friendly territory" // Paradrop - 25% bonus
                    -> power += power / 4
                unique.placeholderText == "Must set up to ranged attack" // Must set up - 20 % penalty
                    -> power -= power / 5
                unique.placeholderText == "+[]% Strength in []" // Bonus in terrain or feature - half the bonus
                    -> power += (power * unique.params[0].toInt()) / 200
            }
        }

        // Base promotions
        for (promotionName in promotions) {
            for (unique in ruleset.unitPromotions[promotionName]!!.uniqueObjects) {
                when {
                    unique.placeholderText == "+[]% Strength vs []" && unique.params[1] == "City" // City Attack - half the bonus
                        -> power += (power * unique.params[0].toInt()) / 200
                    unique.placeholderText == "+[]% Strength vs []" && unique.params[1] != "City" // Bonus vs something else - a quarter of the bonus
                        -> power += (power * unique.params[0].toInt()) / 400
                    unique.placeholderText == "+[]% Strength when attacking" // Attack - half the bonus
                        -> power += (power * unique.params[0].toInt()) / 200
                    unique.placeholderText == "+[]% Strength when defending" // Defense - half the bonus
                        -> power += (power * unique.params[0].toInt()) / 200
                    unique.placeholderText == "[] additional attacks per turn" // Extra attacks - 20% bonus per extra attack
                        -> power += (power * unique.params[0].toInt()) / 5
                    unique.placeholderText == "+[]% Strength in []" // Bonus in terrain or feature - half the bonus
                        -> power += (power * unique.params[0].toInt()) / 200
                }
            }

        }
        cachedForceEvaluation = power
    }
}
