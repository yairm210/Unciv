package com.unciv.models.gamebasics

import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.IConstruction
import com.unciv.models.gamebasics.tech.Technology
import com.unciv.models.stats.NamedStats
import com.unciv.models.stats.Stats
import com.unciv.ui.utils.tr

class Building : NamedStats(), IConstruction{
    override val description: String
        get() = getDescription(false, hashSetOf())

    var requiredTech: String? = null

    var cost: Int = 0
    var maintenance = 0
    var percentStatBonus: Stats? = null
    var specialistSlots: Stats? = null
    var greatPersonPoints: Stats? = null
    /** Extra cost percentage when purchasing */
    var hurryCostModifier: Int = 0
    var isWonder = false
    var requiredBuilding: String? = null
    var requiredBuildingInAllCities: String? = null
    /** A strategic resource that will be consumed by this building */
    var requiredResource: String? = null
    /** City can only be built if one of these resources is nearby - it must be improved! */
    var requiredNearbyImprovedResources: List<String>? = null
    var cannotBeBuiltWith: String? = null
    var cityStrength=0
    var cityHealth=0
    var xpForNewUnits=0
    var replaces:String?=null
    var uniqueTo:String?=null

    // Uniques
    var providesFreeBuilding: String? = null
    var freeTechs: Int = 0
    var uniques = ArrayList<String>()


    /**
     * The bonus stats that a resource gets when this building is built
     */
    var resourceBonusStats: Stats? = null

    fun getRequiredTech(): Technology = GameBasics.Technologies[requiredTech]!!

    fun getShortDescription(): String { // should fit in one line
        val infoList= mutableListOf<String>()
        val str = getStats(hashSetOf()).toString()
        if(str.isNotEmpty()) infoList += str
        val improvedResources = GameBasics.TileResources.values.filter { it.building==name }.map { it.name.tr() }
        if(improvedResources.isNotEmpty()){
            // buildings that improve resources
            infoList += improvedResources.joinToString()+ " {provide} ".tr()+ resourceBonusStats.toString()
        }
        if(uniques.isNotEmpty()) infoList += uniques.map { it.tr() }.joinToString()
        if(cityStrength!=0) infoList+="{City strength} +".tr()+cityStrength
        if(cityHealth!=0) infoList+="{City health} +".tr()+cityHealth
        if(xpForNewUnits!=0) infoList+= "+$xpForNewUnits {XP for new units}".tr()
        return infoList.joinToString()
    }

    fun getStats(adoptedPolicies: HashSet<String>): Stats {
        val stats = this.clone()
        if (adoptedPolicies.contains("Organized Religion") && hashSetOf("Monument", "Temple", "Monastery").contains(name))
            stats.happiness += 1

        if (adoptedPolicies.contains("Free Religion") && hashSetOf("Monument", "Temple", "Monastery").contains(name))
            stats.culture += 1f

        if (adoptedPolicies.contains("Entrepreneurship") && hashSetOf("Mint", "Market", "Bank", "Stock Market").contains(name))
            stats.science += 1f

        if (adoptedPolicies.contains("Humanism") && hashSetOf("University", "Observatory", "Public School").contains(name))
            stats.science += 1f

        if (adoptedPolicies.contains("Theocracy") && name == "Temple")
            percentStatBonus = Stats().apply { gold=10f }

        if (adoptedPolicies.contains("Free Thought") && name == "University")
            percentStatBonus!!.science = 50f

        if (adoptedPolicies.contains("Rationalism Complete") && !isWonder && stats.science > 0)
            stats.gold += 1f

        if (adoptedPolicies.contains("Constitution") && isWonder)
            stats.culture += 2f

        if(adoptedPolicies.contains("Autocracy Complete") && cityStrength>0)
            stats.happiness+=1

        return stats
    }


    fun getDescription(forBuildingPickerScreen: Boolean, adoptedPolicies: HashSet<String>): String {
        val stats = getStats(adoptedPolicies)
        val stringBuilder = StringBuilder()
        if(uniqueTo!=null) stringBuilder.appendln("Unique to $uniqueTo, replaces $replaces")
        if (!forBuildingPickerScreen) stringBuilder.appendln("{Cost}: $cost".tr())
        if (isWonder) stringBuilder.appendln("Wonder".tr())
        if (!forBuildingPickerScreen && requiredTech != null)
            stringBuilder.appendln("Requires {$requiredTech} to be researched".tr())
        if (!forBuildingPickerScreen && requiredBuilding != null)
            stringBuilder.appendln("Requires a $requiredBuilding to be built in this city")
        if (!forBuildingPickerScreen && requiredBuildingInAllCities != null)
            stringBuilder.appendln("Requires a $requiredBuildingInAllCities to be built in all cities")
        if (providesFreeBuilding != null)
            stringBuilder.appendln("Provides a free $providesFreeBuilding in this city")
        if(uniques.isNotEmpty()) stringBuilder.appendln(uniques.asSequence().map { it.tr() }.joinToString("\n"))
        if (stats.toString() != "")
            stringBuilder.appendln(stats)
        if (this.percentStatBonus != null) {
            if (this.percentStatBonus!!.production != 0f) stringBuilder.append("+" + this.percentStatBonus!!.production.toInt() + "% {Production}\n".tr())
            if (this.percentStatBonus!!.gold != 0f) stringBuilder.append("+" + this.percentStatBonus!!.gold.toInt() + "% {Gold}\n".tr())
            if (this.percentStatBonus!!.science != 0f) stringBuilder.append("+" + this.percentStatBonus!!.science.toInt() + "% {Science}\r\n".tr())
            if (this.percentStatBonus!!.food != 0f) stringBuilder.append("+" + this.percentStatBonus!!.food.toInt() + "% {Food}\n".tr())
            if (this.percentStatBonus!!.culture != 0f) stringBuilder.append("+" + this.percentStatBonus!!.culture.toInt() + "% {Culture}\r\n".tr())
        }
        if (this.greatPersonPoints != null) {
            val gpp = this.greatPersonPoints!!
            if (gpp.production != 0f) stringBuilder.appendln("+" + gpp.production.toInt() + " Great Engineer points")
            if (gpp.gold != 0f) stringBuilder.appendln("+" + gpp.gold.toInt() + " Great Merchant points")
            if (gpp.science != 0f) stringBuilder.appendln("+" + gpp.science.toInt() + " Great Scientist points")
            if (gpp.culture != 0f) stringBuilder.appendln("+" + gpp.culture.toInt() + " Great Artist points")
        }
        if (resourceBonusStats != null) {
            val resources = GameBasics.TileResources.values.filter { name == it.building }.joinToString { it.name.tr() }
            stringBuilder.appendln("$resources {provide} $resourceBonusStats".tr())
        }

        if(cityStrength!=0) stringBuilder.appendln("{City strength} +".tr() + cityStrength)
        if(cityHealth!=0) stringBuilder.appendln("{City health} +".tr() + cityHealth)
        if (maintenance != 0)
            stringBuilder.appendln("{Maintenance cost}: $maintenance {Gold}".tr())
        return stringBuilder.toString().trim()
    }

    override fun getProductionCost(adoptedPolicies: HashSet<String>): Int {
        return if (!isWonder && culture != 0f && adoptedPolicies.contains("Piety")) (cost * 0.85).toInt()
        else cost
    }

    override fun getGoldCost(adoptedPolicies: HashSet<String>): Int {
        var cost = Math.pow((30 * getProductionCost(adoptedPolicies)).toDouble(), 0.75) * (1 + hurryCostModifier / 100)
        if (adoptedPolicies.contains("Mercantilism")) cost *= 0.75
        if (adoptedPolicies.contains("Patronage")) cost *= 0.5
        return (cost / 10).toInt() * 10
    }

    override fun isBuildable(construction: CityConstructions): Boolean {
        if (construction.isBuilt(name)) return false

        val civInfo = construction.cityInfo.civInfo
        if (uniqueTo!=null && uniqueTo!=civInfo.civName) return false
        if (GameBasics.Buildings.values.any { it.uniqueTo==civInfo.civName && it.replaces==name }) return false
        if (requiredTech != null && !civInfo.tech.isResearched(requiredTech!!)) return false
        if (isWonder && requiredBuildingInAllCities==null
                && civInfo.gameInfo.civilizations.flatMap { it.cities }.any {
                            it.cityConstructions.isBuilding(name) || it.cityConstructions.isBuilt(name)
                        })
            return false

        if (requiredBuilding != null && !construction.containsBuildingOrEquivalent(requiredBuilding!!)) return false
        if (requiredBuildingInAllCities != null && civInfo.cities.any { !it.cityConstructions.containsBuildingOrEquivalent(requiredBuildingInAllCities!!) })
            return false
        if(requiredBuildingInAllCities!=null && civInfo.cities.any {
                    it.cityConstructions.isBuilding(name) || it.cityConstructions.isBuilt(name)
                })
            return false

        if (cannotBeBuiltWith != null && construction.isBuilt(cannotBeBuiltWith!!)) return false
        if ("Must be next to desert" in uniques
                && !construction.cityInfo.getCenterTile().getTilesInDistance(1).any { it.baseTerrain == "Desert" })
            return false
        if("Can only be built in coastal cities" in uniques
                && construction.cityInfo.getCenterTile().neighbors.none { it.baseTerrain=="Coast" })
            return false
        if (requiredResource != null && !civInfo.getCivResources().containsKey(GameBasics.TileResources[requiredResource!!]))
            return false


        if (requiredNearbyImprovedResources != null) {
            val containsResourceWithImprovement = construction.cityInfo.getTilesInRange()
                    .any {
                        it.resource != null
                                && requiredNearbyImprovedResources!!.contains(it.resource!!)
                                && it.getTileResource().improvement == it.improvement
                                && it.getOwner() == civInfo
                    }
            if (!containsResourceWithImprovement) return false
        }

        if ("Spaceship part" in uniques) {
            if (!civInfo.getBuildingUniques().contains("Enables construction of Spaceship parts")) return false
            if (civInfo.scienceVictory.unconstructedParts()[name] == 0) return false // Don't need to build any more of these!
        }
        return true
    }

    override fun postBuildEvent(construction: CityConstructions) {
        val civInfo = construction.cityInfo.civInfo

        if ("Spaceship part" in uniques) {
            civInfo.scienceVictory.currentParts.add(name, 1)
            return
        }
        construction.builtBuildings.add(name)

        if (providesFreeBuilding != null && !construction.builtBuildings.contains(providesFreeBuilding!!))
            construction.builtBuildings.add(providesFreeBuilding!!)
        when {
            "Empire enters golden age" in uniques-> civInfo.goldenAges.enterGoldenAge()
            "Free Great Artist Appears" in uniques-> civInfo.addGreatPerson("Great Artist")
            "Free great scientist appears" in uniques -> civInfo.addGreatPerson("Great Scientist")
            "Provides 2 free workers" in uniques -> {
                civInfo.placeUnitNearTile(construction.cityInfo.location, "Worker")
                civInfo.placeUnitNearTile(construction.cityInfo.location, "Worker")
            }
            "Free Social Policy" in uniques -> {
                civInfo.policies.freePolicies++
            }
        }

        if (freeTechs != 0) civInfo.tech.freeTechs += freeTechs
    }
}