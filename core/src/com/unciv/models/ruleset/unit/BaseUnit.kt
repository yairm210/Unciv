package com.unciv.models.ruleset.unit

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.INonPerpetualConstruction
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.Unique
import com.unciv.models.stats.INamed
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.civilopedia.FormattedLine
import com.unciv.ui.civilopedia.ICivilopediaText
import com.unciv.ui.utils.Fonts
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

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
    var requiredResource: String? = null
    override var uniques = ArrayList<String>() // Can not be a hashset as that would remove doubles
    override val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }
    var replacementTextForUniques = ""
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
    override fun replacesCivilopediaDescription() = true
    override fun hasCivilopediaTextLines() = true

    override fun getCivilopediaTextLines(ruleset: Ruleset): List<FormattedLine> {
        val textList = ArrayList<FormattedLine>()

        val stats = ArrayList<String>()
        if (strength != 0) stats += "$strength${Fonts.strength}"
        if (rangedStrength != 0) {
            stats += "$rangedStrength${Fonts.rangedStrength}"
            stats += "$range${Fonts.range}"
        }
        if (movement != 0 && !movesLikeAirUnits()) stats += "$movement${Fonts.movement}"
        if (stats.isNotEmpty())
            textList += FormattedLine(stats.joinToString(", "))

        if (cost > 0) {
            stats.clear()
            stats += "$cost${Fonts.production}"
            if (canBePurchasedWithStat(CityInfo(), Stat.Gold, true))
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
                    link="Resource/$resource", color="#F42")
            }
        }

        if (uniqueTo != null) {
            textList += FormattedLine()
            textList += FormattedLine("Unique to [$uniqueTo]", link="Nation/$uniqueTo")
            if (replaces != null)
                textList += FormattedLine("Replaces [$replaces]", link="Unit/$replaces", indent=1)
        }

        if (requiredTech != null || upgradesTo != null || obsoleteTech != null) textList += FormattedLine()
        if (requiredTech != null) textList += FormattedLine("Required tech: [$requiredTech]", link="Technology/$requiredTech")
        if (upgradesTo != null) textList += FormattedLine("Upgrades to [$upgradesTo]", link="Unit/$upgradesTo")
        if (obsoleteTech != null) textList += FormattedLine("Obsolete with [$obsoleteTech]", link="Technology/$obsoleteTech")

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
                        link="Promotions/${it.value}",
                        indent=if(it.index==0) 0 else 1)
            }
        }

        val seeAlso = ArrayList<FormattedLine>()
        for ((other, unit) in ruleset.units) {
            if (unit.replaces == name || uniques.contains("[$name]") ) {
                seeAlso += FormattedLine(other, link="Unit/$other", indent=1)
            }
        }
        if (seeAlso.isNotEmpty()) {
            textList += FormattedLine()
            textList += FormattedLine("{See also}:")
            textList += seeAlso
        }

        return textList
    }

    fun getMapUnit(ruleset: Ruleset): MapUnit {
        val unit = MapUnit()
        unit.name = name

        unit.setTransients(ruleset) // must be after setting name because it sets the baseUnit according to the name

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

    override fun getStatBuyCost(cityInfo: CityInfo, stat: Stat): Int? {
        var cost = getBaseBuyCost(cityInfo, stat)?.toDouble()
        if (cost == null) return null


        // Deprecated since 3.15.15
            if (stat == Stat.Gold) {
                for (unique in cityInfo.getMatchingUniques("Cost of purchasing items in cities reduced by []%"))
                    cost *= 1f - (unique.params[0].toFloat() / 100f)
            }
        //

        for (unique in cityInfo.getMatchingUniques("[] cost of purchasing [] units []%")) {
            if (stat.name == unique.params[0] && matchesFilter(unique.params[1]))
                cost *= 1f + unique.params[2].toFloat() / 100f
        }
        for (unique in cityInfo.getMatchingUniques("[] cost of purchasing items in cities []%"))
            if (stat.name == unique.params[0])
                cost *= 1f + (unique.params[1].toFloat() / 100f)

        return (cost / 10f).toInt() * 10
    }

    fun getDisbandGold(civInfo: CivilizationInfo) = getBaseGoldCost(civInfo).toInt() / 20

    override fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean {
        val rejectionReason = getRejectionReason(cityConstructions)
        return rejectionReason == ""
                || rejectionReason.startsWith("Requires")
                || rejectionReason.startsWith("Consumes")
                || rejectionReason == "Can only be purchased"
    }

    override fun getRejectionReason(cityConstructions: CityConstructions): String {
        if (isWaterUnit() && !cityConstructions.cityInfo.isCoastal())
            return "Can only build water units in coastal cities"
        val civInfo = cityConstructions.cityInfo.civInfo
        for (unique in uniqueObjects.filter { it.placeholderText == "Not displayed as an available construction without []" }) {
            val filter = unique.params[0]
            if (filter in civInfo.gameInfo.ruleSet.tileResources && !civInfo.hasResource(filter)
                    || filter in civInfo.gameInfo.ruleSet.buildings && !cityConstructions.containsBuildingOrEquivalent(filter))
                return "Should not be displayed"
        }
        val civRejectionReason = getRejectionReason(civInfo)
        if (civRejectionReason != "") {
            if (civRejectionReason == "Unbuildable" && canBePurchasedWithAnyStat(cityConstructions.cityInfo))
                return "Can only be purchased"
            return civRejectionReason
        }
        for (unique in uniqueObjects.filter { it.placeholderText == "Requires at least [] population" })
            if (unique.params[0].toInt() > cityConstructions.cityInfo.population.population)
                return unique.text
        return ""
    }

    /** @param ignoreTechPolicyRequirements: its `true` value is used when upgrading via ancient ruins,
     * as there we don't care whether we have the required tech, policy or building for the unit,
     * but do still care whether we have the resources required for the unit
     */
    fun getRejectionReason(civInfo: CivilizationInfo, ignoreTechPolicyRequirements: Boolean = false): String {
        if (uniques.contains("Unbuildable")) return "Unbuildable"
        if (!ignoreTechPolicyRequirements && requiredTech != null && !civInfo.tech.isResearched(requiredTech!!)) return "$requiredTech not researched"
        if (!ignoreTechPolicyRequirements && obsoleteTech != null && civInfo.tech.isResearched(obsoleteTech!!)) return "Obsolete by $obsoleteTech"
        if (uniqueTo != null && uniqueTo != civInfo.civName) return "Unique to $uniqueTo"
        if (civInfo.gameInfo.ruleSet.units.values.any { it.uniqueTo == civInfo.civName && it.replaces == name })
            return "Our unique unit replaces this"
        if (!civInfo.gameInfo.gameParameters.nuclearWeaponsEnabled && isNuclearWeapon()
        ) return "Disabled by setting"

        for (unique in uniqueObjects.filter { it.placeholderText == "Unlocked with []" })
            if (civInfo.tech.researchedTechnologies.none { it.era() == unique.params[0] || it.name == unique.params[0] }
                    && !civInfo.policies.isAdopted(unique.params[0]))
                return unique.text

        for (unique in uniqueObjects.filter { it.placeholderText == "Requires []" }) {
            val filter = unique.params[0]
            if (!ignoreTechPolicyRequirements && filter in civInfo.gameInfo.ruleSet.buildings) {
                if (civInfo.cities.none { it.cityConstructions.containsBuildingOrEquivalent(filter) }) return unique.text // Wonder is not built
            } else if (!ignoreTechPolicyRequirements && !civInfo.policies.adoptedPolicies.contains(filter)) return "Policy is not adopted"
        }

        for ((resource, amount) in getResourceRequirements())
            if (civInfo.getCivResourcesByName()[resource]!! < amount) {
                return if (amount == 1) "Consumes 1 [$resource]" // Again, to preserve existing translations
                    else "Consumes [$amount] [$resource]"
            }

        if (uniques.contains(Constants.settlerUnique) && civInfo.isCityState()) return "No settler for city-states"
        if (uniques.contains(Constants.settlerUnique) && civInfo.isOneCityChallenger()) return "No settler for players in One City Challenge"
        return ""
    }

    fun isBuildable(civInfo: CivilizationInfo) = getRejectionReason(civInfo) == ""

    override fun isBuildable(cityConstructions: CityConstructions): Boolean {
        return getRejectionReason(cityConstructions) == ""
    }

    /** Preemptively as in: buildable without actually having the tech and/or policy required for it.
     * Still checks for resource use and other things
     */
    fun isBuildableIgnoringTechs(civInfo: CivilizationInfo) =
        getRejectionReason(civInfo, true) == ""

    override fun postBuildEvent(cityConstructions: CityConstructions, wasBought: Boolean): Boolean {
        val civInfo = cityConstructions.cityInfo.civInfo
        val unit = civInfo.placeUnitNearTile(cityConstructions.cityInfo.location, name)
                ?: return false  // couldn't place the unit, so there's actually no unit =(

        //movement penalty
        if (wasBought && !civInfo.gameInfo.gameParameters.godMode && !unit.hasUnique("Can move immediately once bought"))
            unit.currentMovement = 0f

        if (unit.hasUnique("Religious Unit")) {
            unit.religion = cityConstructions.cityInfo.religion.getMajorityReligionName()
        }

        if (this.isCivilian()) return true // tiny optimization makes save files a few bytes smaller

        var XP = cityConstructions.getBuiltBuildings().sumBy { it.xpForNewUnits }


        for (unique in
            cityConstructions.cityInfo.getMatchingUniques("New [] units start with [] Experience []")
                .filter { cityConstructions.cityInfo.matchesFilter(it.params[2]) } +
            // Deprecated since 3.15.9
                cityConstructions.cityInfo.getMatchingUniques("New [] units start with [] Experience") +
                cityConstructions.cityInfo.getLocalMatchingUniques("New [] units start with [] Experience in this city")
            //
        ) {
            if (unit.matchesFilter(unique.params[0]))
                XP += unique.params[1].toInt()
        }
        unit.promotions.XP = XP

        for (unique in
            cityConstructions.cityInfo.getMatchingUniques("All newly-trained [] units [] receive the [] promotion")
                .filter { cityConstructions.cityInfo.matchesFilter(it.params[1]) } +
            // Deprecated since 3.15.9
                cityConstructions.cityInfo.getLocalMatchingUniques("All newly-trained [] units in this city receive the [] promotion")
            //
        ) {
            val filter = unique.params[0]
            val promotion = unique.params.last()

            if (unit.matchesFilter(filter) ||
                (
                    filter == "relevant" &&
                        civInfo.gameInfo.ruleSet.unitPromotions.values
                        .any {
                            it.name == promotion
                            && unit.type.name in it.unitTypes
                        }
                )
            ) {
                unit.promotions.addPromotion(promotion, isFree = true)
            }
        }

        return true
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
            // Deprecated as of 3.15.2
            "military water" -> isMilitary() && isWaterUnit()
            else -> {
                if (getType().matchesFilter(filter)) return true
                if (
                    filter.endsWith(" units")
                    // "military units" --> "Military"
                    && matchesFilter(filter.removeSuffix(" units").toLowerCase(Locale.ENGLISH).capitalize(Locale.ENGLISH))
                ) return true
                return uniques.contains(filter)
            }
        }
    }

    fun isGreatPerson() = uniqueObjects.any { it.placeholderText == "Great Person - []" }

    // "Nuclear Weapon" unique deprecated since 3.15.4
    fun isNuclearWeapon() = uniqueObjects.any { it.placeholderText == "Nuclear Weapon" || it.placeholderText == "Nuclear weapon of Strength []" }

    fun movesLikeAirUnits() = getType().getMovementType() == UnitMovementType.Air

    override fun getResourceRequirements(): HashMap<String, Int> {
        val resourceRequirements = HashMap<String, Int>()
        if (requiredResource != null) resourceRequirements[requiredResource!!] = 1
        for (unique in uniqueObjects)
            if (unique.placeholderText == "Consumes [] []")
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
}
