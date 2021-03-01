package com.unciv.models.ruleset

import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.IConstruction
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.Counter
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.stats.NamedStats
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.fillPlaceholders
import com.unciv.models.translations.tr
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.pow


class Building : NamedStats(), IConstruction {

    var requiredTech: String? = null

    var cost: Int = 0
    var maintenance = 0
    private var percentStatBonus: Stats? = null
    var specialistSlots: Counter<String>? = null
    fun newSpecialists(): Counter<String> {
        if (specialistSlots == null) return Counter<String>()
        // Could have old specialist values of "gold", "science" etc - change them to the new specialist names
        val counter = Counter<String>()
        for ((entry, amount) in specialistSlots!!) {
            val equivalentStat = Stat.values().firstOrNull { it.name.toLowerCase(Locale.ENGLISH) == entry }

            if (equivalentStat != null)
                counter[Specialist.specialistNameByStat(equivalentStat)] = amount
            else counter[entry] = amount
        }
        return counter
    }

    var greatPersonPoints: Stats? = null

    /** Extra cost percentage when purchasing */
    private var hurryCostModifier = 0
    var isWonder = false
    var isNationalWonder = false
    var requiredBuilding: String? = null
    var requiredBuildingInAllCities: String? = null

    /** A strategic resource that will be consumed by this building */
    private var requiredResource: String? = null

    /** City can only be built if one of these resources is nearby - it must be improved! */
    private var requiredNearbyImprovedResources: List<String>? = null
    private var cannotBeBuiltWith: String? = null
    var cityStrength = 0
    var cityHealth = 0
    var xpForNewUnits = 0
    var replaces: String? = null
    var uniqueTo: String? = null
    var quote: String = ""
    var providesFreeBuilding: String? = null
    var uniques = ArrayList<String>()
    var replacementTextForUniques = ""
    val uniqueObjects: List<Unique> by lazy { uniques.map { Unique(it) } }

    /**
     * The bonus stats that a resource gets when this building is built
     */
    @Deprecated("Since 3.13.3 - replaced with '[stats] from [resource] tiles in this city'")
    var resourceBonusStats: Stats? = null

    fun getShortDescription(ruleset: Ruleset): String { // should fit in one line
        val infoList = mutableListOf<String>()
        val str = getStats(null).toString()
        if (str.isNotEmpty()) infoList += str
        for (stat in getStatPercentageBonuses(null).toHashMap())
            if (stat.value != 0f) infoList += "+${stat.value.toInt()}% ${stat.key.toString().tr()}"

        val improvedResources = ruleset.tileResources.values.asSequence().filter { it.building == name }.map { it.name.tr() }
        if (improvedResources.any()) {
            // buildings that improve resources
            infoList += improvedResources.joinToString() + " {provide} " + resourceBonusStats.toString()
        }
        if (requiredNearbyImprovedResources != null)
            infoList += "Requires worked [" + requiredNearbyImprovedResources!!.joinToString("/") { it.tr() } + "] near city"
        if (uniques.isNotEmpty()) {
            if (replacementTextForUniques != "") infoList += replacementTextForUniques
            else infoList += getUniquesStrings()
        }
        if (cityStrength != 0) infoList += "{City strength} +$cityStrength"
        if (cityHealth != 0) infoList += "{City health} +$cityHealth"
        if (xpForNewUnits != 0) infoList += "+$xpForNewUnits {XP for new units}"
        return infoList.joinToString("; ") { it.tr() }
    }

    fun getUniquesStrings(): ArrayList<String> {
        val tileBonusHashmap = HashMap<String, ArrayList<String>>()
        val finalUniques = ArrayList<String>()
        for (unique in uniqueObjects)
            if (unique.placeholderText == "[] from [] tiles in this city") {
                val stats = unique.params[0]
                if (!tileBonusHashmap.containsKey(stats)) tileBonusHashmap[stats] = ArrayList()
                tileBonusHashmap[stats]!!.add(unique.params[1])
            } else finalUniques += unique.text
        for ((key, value) in tileBonusHashmap)
            finalUniques += "[stats] from [tileFilter] tiles in this city".fillPlaceholders(key, value.joinToString { it.tr() })
        return finalUniques
    }

    fun getDescription(forBuildingPickerScreen: Boolean, civInfo: CivilizationInfo?, ruleset: Ruleset): String {
        val stats = getStats(civInfo)
        val stringBuilder = StringBuilder()
        if (uniqueTo != null) stringBuilder.appendLine("Unique to [$uniqueTo], replaces [$replaces]".tr())
        if (!forBuildingPickerScreen) stringBuilder.appendLine("{Cost}: $cost".tr())
        if (isWonder) stringBuilder.appendLine("Wonder".tr())
        if (isNationalWonder) stringBuilder.appendLine("National Wonder".tr())
        if (!forBuildingPickerScreen && requiredTech != null)
            stringBuilder.appendLine("Required tech: [$requiredTech]".tr())
        if (!forBuildingPickerScreen && requiredBuilding != null)
            stringBuilder.appendLine("Requires [$requiredBuilding] to be built in the city".tr())
        if (!forBuildingPickerScreen && requiredBuildingInAllCities != null)
            stringBuilder.appendLine("Requires [$requiredBuildingInAllCities] to be built in all cities".tr())
        for ((resource, amount) in getResourceRequirements()) {
            if (amount == 1) stringBuilder.appendLine("Consumes 1 [$resource]".tr()) // For now, to keep the existing translations
            else stringBuilder.appendLine("Consumes [$amount] [$resource]".tr())
        }
        if (providesFreeBuilding != null)
            stringBuilder.appendLine("Provides a free [$providesFreeBuilding] in the city".tr())
        if (uniques.isNotEmpty()) {
            if (replacementTextForUniques != "") stringBuilder.appendLine(replacementTextForUniques)
            else stringBuilder.appendLine(getUniquesStrings().asSequence().map { it.tr() }.joinToString("\n"))
        }
        if (!stats.isEmpty())
            stringBuilder.appendLine(stats.toString())

        val percentStats = getStatPercentageBonuses(civInfo)
        if (percentStats.production != 0f) stringBuilder.append("+" + percentStats.production.toInt() + "% {Production}\n".tr())
        if (percentStats.gold != 0f) stringBuilder.append("+" + percentStats.gold.toInt() + "% {Gold}\n".tr())
        if (percentStats.science != 0f) stringBuilder.append("+" + percentStats.science.toInt() + "% {Science}\r\n".tr())
        if (percentStats.food != 0f) stringBuilder.append("+" + percentStats.food.toInt() + "% {Food}\n".tr())
        if (percentStats.culture != 0f) stringBuilder.append("+" + percentStats.culture.toInt() + "% {Culture}\r\n".tr())

        if (this.greatPersonPoints != null) {
            val gpp = this.greatPersonPoints!!
            if (gpp.production != 0f) stringBuilder.appendLine("+" + gpp.production.toInt() + " " + "[Great Engineer] points".tr())
            if (gpp.gold != 0f) stringBuilder.appendLine("+" + gpp.gold.toInt() + " " + "[Great Merchant] points".tr())
            if (gpp.science != 0f) stringBuilder.appendLine("+" + gpp.science.toInt() + " " + "[Great Scientist] points".tr())
            if (gpp.culture != 0f) stringBuilder.appendLine("+" + gpp.culture.toInt() + " " + "[Great Artist] points".tr())
        }

        for ((specialistName, amount) in newSpecialists())
            stringBuilder.appendLine("+$amount " + "[$specialistName] slots".tr())

        if (resourceBonusStats != null) {
            val resources = ruleset.tileResources.values.filter { name == it.building }.joinToString { it.name.tr() }
            stringBuilder.appendLine("$resources {provide} $resourceBonusStats".tr())
        }

        if (requiredNearbyImprovedResources != null)
            stringBuilder.appendLine(("Requires worked [" + requiredNearbyImprovedResources!!.joinToString("/") { it.tr() } + "] near city").tr())

        if (cityStrength != 0) stringBuilder.appendLine("{City strength} +".tr() + cityStrength)
        if (cityHealth != 0) stringBuilder.appendLine("{City health} +".tr() + cityHealth)
        if (xpForNewUnits != 0) stringBuilder.appendLine("+$xpForNewUnits {XP for new units}".tr())
        if (maintenance != 0)
            stringBuilder.appendLine("{Maintenance cost}: $maintenance {Gold}".tr())
        return stringBuilder.toString().trim()
    }

    fun getStats(civInfo: CivilizationInfo?): Stats {
        val stats = this.clone()
        if (civInfo != null) {
            val baseBuildingName = getBaseBuilding(civInfo.gameInfo.ruleSet).name

            for (unique in civInfo.getMatchingUniques("[] from every []")) {
                if (unique.params[1] != baseBuildingName) continue
                stats.add(unique.stats)
            }

            for (unique in uniqueObjects)
                if (unique.placeholderText == "[] with []" && civInfo.hasResource(unique.params[1])
                        && Stats.isStats(unique.params[0]))
                    stats.add(unique.stats)

            if (!isWonder)
                for (unique in civInfo.getMatchingUniques("[] from all [] buildings")) {
                    if (isStatRelated(Stat.valueOf(unique.params[1])))
                        stats.add(unique.stats)
                }
            else
                for (unique in civInfo.getMatchingUniques("[] from every Wonder"))
                    stats.add(unique.stats)

        }
        return stats
    }

    fun getStatPercentageBonuses(civInfo: CivilizationInfo?): Stats {
        val stats = if (percentStatBonus == null) Stats() else percentStatBonus!!.clone()
        if (civInfo == null) return stats // initial stats

        val baseBuildingName = getBaseBuilding(civInfo.gameInfo.ruleSet).name

        for (unique in civInfo.getMatchingUniques("+[]% [] from every []")) {
            if (unique.params[2] == baseBuildingName)
                stats.add(Stat.valueOf(unique.params[1]), unique.params[0].toFloat())
        }

        if (uniques.contains("+5% Production for every Trade Route with a City-State in the empire"))
            stats.production += 5 * civInfo.citiesConnectedToCapitalToMediums.count { it.key.civInfo.isCityState() }

        return stats
    }

    override fun canBePurchased(): Boolean {
        return !isWonder && !isNationalWonder && "Cannot be purchased" !in uniques
    }


    override fun getProductionCost(civInfo: CivilizationInfo): Int {
        var productionCost = cost.toFloat()

        for (unique in uniqueObjects.filter { it.placeholderText == "Cost increases by [] per owned city" })
            productionCost += civInfo.cities.count() * unique.params[0].toInt()

        if (civInfo.isPlayerCivilization()) {
            if (!isWonder)
                productionCost *= civInfo.getDifficulty().buildingCostModifier
        } else {
            productionCost *= if (isWonder)
                civInfo.gameInfo.getDifficulty().aiWonderCostModifier
            else
                civInfo.gameInfo.getDifficulty().aiBuildingCostModifier
        }

        productionCost *= civInfo.gameInfo.gameParameters.gameSpeed.modifier
        return productionCost.toInt()
    }

    override fun getGoldCost(civInfo: CivilizationInfo): Int {
        // https://forums.civfanatics.com/threads/rush-buying-formula.393892/
        var cost = (30 * getProductionCost(civInfo)).toDouble().pow(0.75) * (1 + hurryCostModifier / 100)

        for (unique in civInfo.getMatchingUniques("Cost of purchasing items in cities reduced by []%"))
            cost *= 1 - (unique.params[0].toFloat() / 100)

        for (unique in civInfo.getMatchingUniques("Cost of purchasing [] buildings reduced by []%")) {
            if (isStatRelated(Stat.valueOf(unique.params[0])))
                cost *= 1 - (unique.params[1].toFloat() / 100)
        }

        return (cost / 10).toInt() * 10
    }


    override fun shouldBeDisplayed(cityConstructions: CityConstructions): Boolean {
        if (cityConstructions.isBeingConstructedOrEnqueued(name))
            return false
        val rejectionReason = getRejectionReason(cityConstructions)
        return rejectionReason == ""
                || rejectionReason.startsWith("Requires")
                || rejectionReason.startsWith("Consumes")
                || rejectionReason == "Wonder is being built elsewhere"
    }

    fun getRejectionReason(construction: CityConstructions): String {
        if (construction.isBuilt(name)) return "Already built"
        // for buildings that are created as side effects of other things, and not directly built
        if (uniques.contains("Unbuildable")) return "Unbuildable"

        val cityCenter = construction.cityInfo.getCenterTile()
        val civInfo = construction.cityInfo.civInfo

        // This overrides the others
        if (uniqueObjects.any {
                    it.placeholderText == "Not displayed as an available construction unless [] is built"
                            && !construction.containsBuildingOrEquivalent(it.params[0])
                })
            return "Should not be displayed"

        for (unique in uniqueObjects.filter { it.placeholderText == "Not displayed as an available construction without []" }) {
            val filter = unique.params[0]
            if (filter in civInfo.gameInfo.ruleSet.tileResources && !construction.cityInfo.civInfo.hasResource(filter)
                    || filter in civInfo.gameInfo.ruleSet.buildings && !construction.containsBuildingOrEquivalent(filter))
                return "Should not be displayed"
        }

        for (unique in uniqueObjects) when (unique.placeholderText) {
            "Must be on []" -> if (!cityCenter.matchesUniqueFilter(unique.params[0], civInfo)) return unique.text
            "Must not be on []" -> if (cityCenter.matchesUniqueFilter(unique.params[0], civInfo)) return unique.text
            "Must be next to []" -> if (!(unique.params[0] == "Fresh water" && cityCenter.isAdjacentToRiver()) // Fresh water is special, in that rivers are not tiles themselves but also fit the filter.
                    && cityCenter.getTilesInDistance(1).none { it.matchesUniqueFilter(unique.params[0], civInfo) }) return unique.text
            "Must not be next to []" -> if (cityCenter.getTilesInDistance(1).any { it.matchesUniqueFilter(unique.params[0], civInfo) }) return unique.text
            "Must have an owned [] within [] tiles" -> if (cityCenter.getTilesInDistance(unique.params[1].toInt()).none {
                        it.matchesUniqueFilter(unique.params[0], civInfo) && it.getOwner() == construction.cityInfo.civInfo
                    }) return unique.text
            "Can only be built in annexed cities" -> if (construction.cityInfo.isPuppet || construction.cityInfo.foundingCiv == ""
                    || construction.cityInfo.civInfo.civName == construction.cityInfo.foundingCiv) return unique.text
            "Obsolete with []" -> if (civInfo.tech.isResearched(unique.params[0])) return unique.text
        }

        if (uniqueTo != null && uniqueTo != civInfo.civName) return "Unique to $uniqueTo"
        if (civInfo.gameInfo.ruleSet.buildings.values.any { it.uniqueTo == civInfo.civName && it.replaces == name })
            return "Our unique building replaces this"
        if (requiredTech != null && !civInfo.tech.isResearched(requiredTech!!)) return "$requiredTech not researched"

        for (unique in uniqueObjects.filter { it.placeholderText == "Unlocked with []" })
            if (civInfo.tech.researchedTechnologies.none { it.era() == unique.params[0] || it.name == unique.params[0] }
                    && !civInfo.policies.isAdopted(unique.params[0]))
                return unique.text

        // Regular wonders
        if (isWonder) {
            if (civInfo.gameInfo.getCities().any { it.cityConstructions.isBuilt(name) })
                return "Wonder is already built"

            if (civInfo.cities.any { it != construction.cityInfo && it.cityConstructions.isBeingConstructedOrEnqueued(name) })
                return "Wonder is being built elsewhere"

            if (civInfo.isCityState())
                return "No world wonders for city-states"
        }


        // National wonders
        if (isNationalWonder) {
            if (civInfo.cities.any { it.cityConstructions.isBuilt(name) })
                return "National Wonder is already built"
            if (requiredBuildingInAllCities != null && civInfo.gameInfo.ruleSet.buildings[requiredBuildingInAllCities!!] == null)
                return "Required building in all cities does not exist in the ruleset!"
            if (requiredBuildingInAllCities != null
                    && civInfo.cities.any {
                        !it.isPuppet && !it.cityConstructions
                                .containsBuildingOrEquivalent(requiredBuildingInAllCities!!)
                    })
                return "Requires a [${civInfo.getEquivalentBuilding(requiredBuildingInAllCities!!)}] in all cities"
            if (civInfo.cities.any { it != construction.cityInfo && it.cityConstructions.isBeingConstructedOrEnqueued(name) })
                return "National Wonder is being built elsewhere"
            if (civInfo.isCityState())
                return "No national wonders for city-states"
        }

        if ("Spaceship part" in uniques) {
            if (!civInfo.hasUnique("Enables construction of Spaceship parts")) return "Apollo project not built!"
            if (civInfo.victoryManager.unconstructedSpaceshipParts()[name] == 0) return "Don't need to build any more of these!"
        }

        for (unique in uniqueObjects) when (unique.placeholderText) {
            "Requires []" -> {
                val filter = unique.params[0]
                if (filter in civInfo.gameInfo.ruleSet.buildings) {
                    if (civInfo.cities.none { it.cityConstructions.containsBuildingOrEquivalent(filter) }) return unique.text // Wonder is not built
                } else if (!civInfo.policies.adoptedPolicies.contains(filter)) return "Policy is not adopted" // this reason should not be displayed
            }

            "Requires a [] in this city" -> {
                val filter = unique.params[0]
                if (civInfo.gameInfo.ruleSet.buildings.containsKey(filter)
                        && !construction.containsBuildingOrEquivalent(filter))
                    return "Requires a [${civInfo.getEquivalentBuilding(filter)}] in this city" // replace with civ-specific building for user
            }

            "Requires a [] in all cities" -> {
                val filter = unique.params[0]
                if (civInfo.gameInfo.ruleSet.buildings.containsKey(filter)
                        && civInfo.cities.any { !it.cityConstructions.containsBuildingOrEquivalent(unique.params[0]) })
                    return "Requires a [${civInfo.getEquivalentBuilding(unique.params[0])}] in all cities"  // replace with civ-specific building for user
            }
        }

        if (requiredBuilding != null && !construction.containsBuildingOrEquivalent(requiredBuilding!!)) {
            if (!civInfo.gameInfo.ruleSet.buildings.containsKey(requiredBuilding!!))
                return "Requires a [${requiredBuilding}] in this city, which doesn't seem to exist in this ruleset!"
            return "Requires a [${civInfo.getEquivalentBuilding(requiredBuilding!!)}] in this city"
        }
        if (cannotBeBuiltWith != null && construction.isBuilt(cannotBeBuiltWith!!))
            return "Cannot be built with $cannotBeBuiltWith"

        for ((resource, amount) in getResourceRequirements())
            if (civInfo.getCivResourcesByName()[resource]!! < amount) {
                if (amount == 1) return "Consumes 1 [$resource]" // Again, to preserve existing translations
                else return "Consumes [$amount] [$resource]"
            }

        if (requiredNearbyImprovedResources != null) {
            val containsResourceWithImprovement = construction.cityInfo.getWorkableTiles()
                    .any {
                        it.resource != null
                                && requiredNearbyImprovedResources!!.contains(it.resource!!)
                                && it.getOwner() == civInfo
                                && (it.getTileResource().improvement == it.improvement || it.getTileImprovement()?.isGreatImprovement() == true || it.isCityCenter())
                    }
            if (!containsResourceWithImprovement) return "Nearby $requiredNearbyImprovedResources required"
        }


        if (!civInfo.gameInfo.gameParameters.victoryTypes.contains(VictoryType.Scientific)
                && "Enables construction of Spaceship parts" in uniques)
            return "Can't construct spaceship parts if scientific victory is not enabled!"

        return ""
    }

    override fun isBuildable(cityConstructions: CityConstructions): Boolean =
            getRejectionReason(cityConstructions) == ""

    override fun postBuildEvent(cityConstructions: CityConstructions, wasBought: Boolean): Boolean {
        val civInfo = cityConstructions.cityInfo.civInfo

        if ("Spaceship part" in uniques) {
            civInfo.victoryManager.currentsSpaceshipParts.add(name, 1)
            return true
        }
        cityConstructions.addBuilding(name)


        val improvement = getImprovement(civInfo.gameInfo.ruleSet)
        if (improvement != null) {
            val tileWithImprovement = cityConstructions.cityInfo.getTiles().firstOrNull { it.improvementInProgress == improvement.name }
            if (tileWithImprovement != null) {
                tileWithImprovement.turnsToImprovement = 0
                tileWithImprovement.improvementInProgress = null
                tileWithImprovement.improvement = improvement.name
            }
        }


        if (providesFreeBuilding != null && !cityConstructions.containsBuildingOrEquivalent(providesFreeBuilding!!)) {
            var buildingToAdd = providesFreeBuilding!!

            for (building in civInfo.gameInfo.ruleSet.buildings.values)
                if (building.replaces == buildingToAdd && building.uniqueTo == civInfo.civName)
                    buildingToAdd = building.name

            cityConstructions.addBuilding(buildingToAdd)
        }

        for (unique in uniqueObjects)
            UniqueTriggerActivation.triggerCivwideUnique(unique, civInfo, cityConstructions.cityInfo)

        if ("Enemy land units must spend 1 extra movement point when inside your territory (obsolete upon Dynamite)" in uniques)
            civInfo.updateHasActiveGreatWall()

        // Korean unique - apparently gives the same as the research agreement
        if (science > 0 && civInfo.hasUnique("Receive a tech boost when scientific buildings/wonders are built in capital"))
            civInfo.tech.addScience(civInfo.tech.scienceOfLast8Turns.sum() / 8)

        cityConstructions.cityInfo.cityStats.update() // new building, new stats
        civInfo.updateDetailedCivResources() // this building/unit could be a resource-requiring one
        civInfo.transients().updateCitiesConnectedToCapital(false) // could be a connecting building, like a harbor

        return true
    }

    fun isStatRelated(stat: Stat): Boolean {
        if (get(stat) > 0) return true
        if (getStatPercentageBonuses(null).get(stat) > 0) return true
        if (resourceBonusStats != null && resourceBonusStats!!.get(stat) > 0) return true
        return false
    }

    fun getBaseBuilding(ruleset: Ruleset): Building {
        if (replaces == null) return this
        else return ruleset.buildings[replaces!!]!!
    }

    fun getImprovement(ruleset: Ruleset): TileImprovement? {
        val improvementUnique = uniqueObjects
                .firstOrNull { it.placeholderText == "Creates a [] improvement on a specific tile" }
        if (improvementUnique == null) return null
        return ruleset.tileImprovements[improvementUnique.params[0]]!!
    }

    fun isSellable() = !isWonder && !isNationalWonder && !uniques.contains("Unsellable")

    override fun getResourceRequirements(): HashMap<String, Int> {
        val resourceRequirements = HashMap<String, Int>()
        if (requiredResource != null) resourceRequirements[requiredResource!!] = 1
        for (unique in uniqueObjects)
            if (unique.placeholderText == "Consumes [] []")
                resourceRequirements[unique.params[1]] = unique.params[0].toInt()
        return resourceRequirements
    }
}