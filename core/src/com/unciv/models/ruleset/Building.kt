package com.unciv.models.ruleset

import com.unciv.Constants
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.IConstruction
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.models.stats.NamedStats
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import kotlin.math.pow


class Building : NamedStats(), IConstruction {

    var requiredTech: String? = null

    var cost: Int = 0
    var maintenance = 0
    private var percentStatBonus: Stats? = null
    var specialistSlots: Stats? = null
    var greatPersonPoints: Stats? = null
    /** Extra cost percentage when purchasing */
    private var hurryCostModifier = 0
    var isWonder = false
    var isNationalWonder = false
    private var requiredBuilding: String? = null
    var requiredBuildingInAllCities: String? = null
    /** A strategic resource that will be consumed by this building */
    var requiredResource: String? = null
    /** City can only be built if one of these resources is nearby - it must be improved! */
    private var requiredNearbyImprovedResources: List<String>? = null
    private var cannotBeBuiltWith: String? = null
    var cityStrength=0
    var cityHealth=0
    var xpForNewUnits=0
    var replaces:String?=null
    var uniqueTo:String?=null
    var quote:String=""
    private var providesFreeBuilding: String? = null
    var uniques = ArrayList<String>()
    val uniqueObjects:List<Unique> by lazy { uniques.map { Unique(it) } }

    /**
     * The bonus stats that a resource gets when this building is built
     */
    var resourceBonusStats: Stats? = null

    fun getShortDescription(ruleset: Ruleset): String { // should fit in one line
        val infoList= mutableListOf<String>()
        val str = getStats(null).toString()
        if(str.isNotEmpty()) infoList += str
        for(stat in getStatPercentageBonuses(null).toHashMap())
            if(stat.value!=0f) infoList+="+${stat.value.toInt()}% ${stat.key.toString().tr()}"

        val improvedResources = ruleset.tileResources.values.filter { it.building==name }.map { it.name.tr() }
        if(improvedResources.isNotEmpty()){
            // buildings that improve resources
            infoList += improvedResources.joinToString()+ " {provide} ".tr()+ resourceBonusStats.toString()
        }
        if(requiredNearbyImprovedResources!=null)
            infoList += ("Requires worked ["+requiredNearbyImprovedResources!!.joinToString("/"){it.tr()}+"] near city").tr()
        if(uniques.isNotEmpty()) infoList += uniques.joinToString { it.tr() }
        if(cityStrength!=0) infoList+="{City strength} +".tr()+cityStrength
        if(cityHealth!=0) infoList+="{City health} +".tr()+cityHealth
        if(xpForNewUnits!=0) infoList+= "+$xpForNewUnits {XP for new units}".tr()
        return infoList.joinToString()
    }

    fun getDescription(forBuildingPickerScreen: Boolean, civInfo: CivilizationInfo?, ruleset: Ruleset): String {
        val stats = getStats(civInfo)
        val stringBuilder = StringBuilder()
        if(uniqueTo!=null) stringBuilder.appendln("Unique to [$uniqueTo], replaces [$replaces]".tr())
        if (!forBuildingPickerScreen) stringBuilder.appendln("{Cost}: $cost".tr())
        if (isWonder) stringBuilder.appendln("Wonder".tr())
        if(isNationalWonder) stringBuilder.appendln("National Wonder".tr())
        if (!forBuildingPickerScreen && requiredTech != null)
            stringBuilder.appendln("Required tech: [$requiredTech]".tr())
        if (!forBuildingPickerScreen && requiredBuilding != null)
            stringBuilder.appendln("Requires [$requiredBuilding] to be built in the city".tr())
        if (!forBuildingPickerScreen && requiredBuildingInAllCities != null)
            stringBuilder.appendln("Requires [$requiredBuildingInAllCities] to be built in all cities".tr())
        if(requiredResource!=null)
            stringBuilder.appendln("Consumes 1 [$requiredResource]".tr())
        if (providesFreeBuilding != null)
            stringBuilder.appendln("Provides a free [$providesFreeBuilding] in the city".tr())
        if(uniques.isNotEmpty()) stringBuilder.appendln(uniques.asSequence().map { it.tr() }.joinToString("\n"))
        if (stats.toString() != "")
            stringBuilder.appendln(stats)

        val percentStats = getStatPercentageBonuses(civInfo)
        if (percentStats.production != 0f) stringBuilder.append("+" + percentStats.production.toInt() + "% {Production}\n".tr())
        if (percentStats.gold != 0f) stringBuilder.append("+" + percentStats.gold.toInt() + "% {Gold}\n".tr())
        if (percentStats.science != 0f) stringBuilder.append("+" + percentStats.science.toInt() + "% {Science}\r\n".tr())
        if (percentStats.food != 0f) stringBuilder.append("+" + percentStats.food.toInt() + "% {Food}\n".tr())
        if (percentStats.culture != 0f) stringBuilder.append("+" + percentStats.culture.toInt() + "% {Culture}\r\n".tr())

        if (this.greatPersonPoints != null) {
            val gpp = this.greatPersonPoints!!
            if (gpp.production != 0f) stringBuilder.appendln("+" + gpp.production.toInt()+" " + "[Great Engineer] points".tr())
            if (gpp.gold != 0f) stringBuilder.appendln("+" + gpp.gold.toInt() + " "+"[Great Merchant] points".tr())
            if (gpp.science != 0f) stringBuilder.appendln("+" + gpp.science.toInt() + " "+"[Great Scientist] points".tr())
            if (gpp.culture != 0f) stringBuilder.appendln("+" + gpp.culture.toInt() + " "+"[Great Artist] points".tr())
        }

        if (this.specialistSlots != null) {
            val ss = this.specialistSlots!!
            if (ss.production != 0f) stringBuilder.appendln("+" + ss.production.toInt() + " " + "[Engineer specialist] slots".tr())
            if (ss.gold       != 0f) stringBuilder.appendln("+" + ss.gold      .toInt() + " " + "[Merchant specialist] slots".tr())
            if (ss.science    != 0f) stringBuilder.appendln("+" + ss.science   .toInt() + " " + "[Scientist specialist] slots".tr())
            if (ss.culture    != 0f) stringBuilder.appendln("+" + ss.culture   .toInt() + " " + "[Artist specialist] slots".tr())
        }

        if (resourceBonusStats != null) {
            val resources = ruleset.tileResources.values.filter { name == it.building }.joinToString { it.name.tr() }
            stringBuilder.appendln("$resources {provide} $resourceBonusStats".tr())
        }

        if(requiredNearbyImprovedResources!=null)
            stringBuilder.appendln(("Requires worked ["+requiredNearbyImprovedResources!!.joinToString("/"){it.tr()}+"] near city").tr())

        if(cityStrength!=0) stringBuilder.appendln("{City strength} +".tr() + cityStrength)
        if(cityHealth!=0) stringBuilder.appendln("{City health} +".tr() + cityHealth)
        if(xpForNewUnits!=0) stringBuilder.appendln("+$xpForNewUnits {XP for new units}".tr())
        if (maintenance != 0)
            stringBuilder.appendln("{Maintenance cost}: $maintenance {Gold}".tr())
        return stringBuilder.toString().trim()
    }

    fun getStats(civInfo: CivilizationInfo?): Stats {
        val stats = this.clone()
        if(civInfo != null) {
            val adoptedPolicies = civInfo.policies.adoptedPolicies
            val baseBuildingName = getBaseBuilding(civInfo.gameInfo.ruleSet).name

            for(unique in civInfo.getMatchingUniques("[] from every []")) {
                if (unique.params[1] != baseBuildingName) continue
                stats.add(Stats.parse(unique.params[0]))
            }

            // todo policy
            if (adoptedPolicies.contains("Humanism") && hashSetOf("University", "Observatory", "Public School").contains(baseBuildingName ))
                stats.happiness += 1f

            if(!isWonder)
                for(unique in civInfo.getMatchingUniques("[] from all [] buildings")){
                    if(isStatRelated(Stat.valueOf(unique.params[1])))
                        stats.add(Stats.parse(unique.params[0]))
                }
            else
                for(unique in civInfo.getMatchingUniques("[] from every Wonder"))
                    stats.add(Stats.parse(unique.params[0]))

            if (adoptedPolicies.contains("Police State") && baseBuildingName == "Courthouse")
                stats.happiness += 3

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
        return !isWonder && !isNationalWonder && ("Cannot be purchased" !in uniques)
    }


    override fun getProductionCost(civInfo: CivilizationInfo): Int {
        var productionCost = cost.toFloat()

        for (unique in uniqueObjects.filter { it.placeholderText == "Cost increases by [] per owned city" })
            productionCost += civInfo.cities.count() * unique.params[0].toInt()

        if (civInfo.isPlayerCivilization()) {
            if (!isWonder)
                productionCost *= civInfo.getDifficulty().buildingCostModifier
        } else {
            productionCost *= if(isWonder)
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
        return rejectionReason==""
                || rejectionReason.startsWith("Requires")
                || rejectionReason.startsWith("Consumes")
                || rejectionReason == "Wonder is being built elsewhere"
    }

    fun getRejectionReason(construction: CityConstructions):String {
        if (construction.isBuilt(name)) return "Already built"
        // for buildings that are created as side effects of other things, and not directly built
        if (uniques.contains("Unbuildable")) return "Unbuildable"

        val cityCenter = construction.cityInfo.getCenterTile()
        val civInfo = construction.cityInfo.civInfo

        for(unique in uniqueObjects) when (unique.placeholderText) {
            "Must be on []" -> if (!cityCenter.fitsUniqueFilter(unique.params[0])) return unique.text
            "Must not be on []" -> if (cityCenter.fitsUniqueFilter(unique.params[0])) return unique.text
            "Must be next to []" -> if (!(unique.params[0] == "Fresh water" && cityCenter.isAdjacentToRiver()) // Fresh water is special, in that rivers are not tiles themselves but also fit the filter..
                    && cityCenter.getTilesInDistance(1).none { it.fitsUniqueFilter(unique.params[0])}) return unique.text
            "Must not be next to []" -> if (cityCenter.getTilesInDistance(1).any { it.fitsUniqueFilter(unique.params[0]) }) return unique.text
            "Must have an owned [] within [] tiles" -> if (cityCenter.getTilesInDistance(distance = unique.params[1].toInt()).none {
                        it.fitsUniqueFilter(unique.params[0]) && it.getOwner() == construction.cityInfo.civInfo }) return unique.text
            "Can only be built in annexed cities" -> if (construction.cityInfo.isPuppet || construction.cityInfo.foundingCiv == ""
                    || construction.cityInfo.civInfo.civName == construction.cityInfo.foundingCiv) return unique.text
            "Requires []" -> { val filter = unique.params[0]
                if (filter in civInfo.gameInfo.ruleSet.buildings) {
                    if (civInfo.cities.none { it.cityConstructions.containsBuildingOrEquivalent(filter) }) return unique.text // Wonder is not built
                } else if (!civInfo.policies.adoptedPolicies.contains(filter)) return "Policy is not adopted" // this reason should not be displayed
            }

            "Must have an owned mountain within 2 tiles" ->  // Deprecated as of 3.10.8 . Use "Must have an owned [Mountain] within [2] tiles" instead
                if (cityCenter.getTilesInDistance(2)
                                .none { it.baseTerrain == Constants.mountain && it.getOwner() == construction.cityInfo.civInfo })
                    return unique.text
            "Must be next to river" -> // Deprecated as of 3.10.8 . Use "Must be on [River]" instead
                if (!cityCenter.isAdjacentToRiver()) return unique.text
            "Must not be on plains" ->  // Deprecated as of 3.10.8 . Use "Must not be on [Plains]" instead
                if (cityCenter.baseTerrain == Constants.plains) return unique.text
            "Must not be on hill" ->  // Deprecated as of 3.10.8 . Use "Must not be on [Hill]" instead
                if (cityCenter.baseTerrain == Constants.hill) return unique.text
            "Can only be built in coastal cities" ->  // Deprecated as of 3.10.8 . Use "Must be next to [Coast]" instead
                if (!cityCenter.isCoastalTile()) return unique.text
            "Must border a source of fresh water" ->  // Deprecated as of 3.10.8 . Use "Must be next to [Fresh water]" instead
                if (!cityCenter.isAdjacentToFreshwater) return  unique.text
        }

        if (uniqueTo != null && uniqueTo != civInfo.civName) return "Unique to $uniqueTo"
        if (civInfo.gameInfo.ruleSet.buildings.values.any { it.uniqueTo == civInfo.civName && it.replaces == name })
            return "Our unique building replaces this"
        if (requiredTech != null && !civInfo.tech.isResearched(requiredTech!!)) return "$requiredTech not researched"

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
            if (requiredBuildingInAllCities != null
                    && civInfo.cities.any {
                        !it.isPuppet && !it.cityConstructions
                                .containsBuildingOrEquivalent(requiredBuildingInAllCities!!)
                    })
                return "Requires a [$requiredBuildingInAllCities] in all cities"
            if (civInfo.cities.any { it != construction.cityInfo && it.cityConstructions.isBeingConstructedOrEnqueued(name) })
                return "National Wonder is being built elsewhere"
            if (civInfo.isCityState())
                return "No national wonders for city-states"
        }

        if ("Spaceship part" in uniques) {
            if (!civInfo.hasUnique("Enables construction of Spaceship parts")) return "Apollo project not built!"
            if (civInfo.victoryManager.unconstructedSpaceshipParts()[name] == 0) return "Don't need to build any more of these!"
        }

        if (requiredBuilding != null && !construction.containsBuildingOrEquivalent(requiredBuilding!!))
            return "Requires a [$requiredBuilding] in this city"
        if (cannotBeBuiltWith != null && construction.isBuilt(cannotBeBuiltWith!!))
            return "Cannot be built with $cannotBeBuiltWith"

        if (requiredResource != null && !civInfo.hasResource(requiredResource!!) && !civInfo.gameInfo.gameParameters.godMode)
            return "Consumes 1 [$requiredResource]"

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

    override fun isBuildable(cityConstructions: CityConstructions): Boolean {
        return getRejectionReason(cityConstructions)==""
    }

    override fun postBuildEvent(cityConstructions: CityConstructions, wasBought: Boolean): Boolean {
        val civInfo = cityConstructions.cityInfo.civInfo

        if ("Spaceship part" in uniques) {
            civInfo.victoryManager.currentsSpaceshipParts.add(name, 1)
            return true
        }
        cityConstructions.addBuilding(name)

        if (providesFreeBuilding != null && !cityConstructions.containsBuildingOrEquivalent(providesFreeBuilding!!)) {
            var buildingToAdd = providesFreeBuilding!!

            for (building in civInfo.gameInfo.ruleSet.buildings.values)
                if (building.replaces == buildingToAdd && building.uniqueTo == civInfo.civName)
                    buildingToAdd = building.name

            cityConstructions.addBuilding(buildingToAdd)
        }

        for (unique in uniqueObjects)
            UniqueTriggerActivation.triggerCivwideUnique(unique, civInfo)

        // ALL these are deprecated as of 3.10.10 and are currently here to not break mods relying on them
        if ("2 free Great Artists appear" in uniques) {
            civInfo.addUnit("Great Artist", cityConstructions.cityInfo)
            civInfo.addUnit("Great Artist", cityConstructions.cityInfo)
        }
        if ("2 free great scientists appear" in uniques) {
            civInfo.addUnit("Great Scientist", cityConstructions.cityInfo)
            civInfo.addUnit("Great Scientist", cityConstructions.cityInfo)
        }
        if ("Provides 2 free workers" in uniques) {
            civInfo.addUnit(Constants.worker, cityConstructions.cityInfo)
            civInfo.addUnit(Constants.worker, cityConstructions.cityInfo)
        }


        if ("Enemy land units must spend 1 extra movement point when inside your territory (obsolete upon Dynamite)" in uniques)
            civInfo.updateHasActiveGreatWall()

        cityConstructions.cityInfo.cityStats.update() // new building, new stats
        civInfo.updateDetailedCivResources() // this building/unit could be a resource-requiring one
        civInfo.transients().updateCitiesConnectedToCapital(false) // could be a connecting building, like a harbor

        return true
    }

    override fun getResource(): String? = requiredResource

    fun isStatRelated(stat: Stat): Boolean {
        if (get(stat) > 0) return true
        if (getStatPercentageBonuses(null).get(stat)>0) return true
        if (specialistSlots!=null && specialistSlots!!.get(stat)>0) return true
        if(resourceBonusStats!=null && resourceBonusStats!!.get(stat)>0) return true
        return false
    }

    fun getBaseBuilding(ruleset: Ruleset): Building {
        if(replaces==null) return this
        else return ruleset.buildings[replaces!!]!!
    }
}
