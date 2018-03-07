package com.unciv.models.gamebasics

import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.IConstruction
import com.unciv.models.linq.Linq
import com.unciv.models.stats.NamedStats
import com.unciv.models.stats.Stats
import com.unciv.ui.ScienceVictoryScreen
import com.unciv.ui.UnCivGame
import com.unciv.ui.VictoryScreen
import com.unciv.ui.pickerscreens.PolicyPickerScreen

class Building : NamedStats(), IConstruction, ICivilopedia {
    private lateinit var baseDescription: String
    override val description: String
        get() = getDescription(false, Linq())

    @JvmField var requiredTech: String? = null

    @JvmField var cost: Int = 0
    @JvmField var maintenance = 0
    @JvmField var percentStatBonus: Stats? = null
    @JvmField var specialistSlots: Stats? = null
    @JvmField var greatPersonPoints: Stats? = null
    /** Extra cost percentage when purchasing */
    @JvmField var hurryCostModifier: Int = 0
    @JvmField var isWonder = false
    @JvmField var requiredBuilding: String? = null
    @JvmField var requiredBuildingInAllCities: String? = null
    /** A strategic resource that will be consumed by this building */
    @JvmField var requiredResource: String? = null
    /** City can only be built if one of these resources is nearby - it must be improved! */
    @JvmField var requiredNearbyImprovedResources: Linq<String>? = null
    @JvmField var cannotBeBuiltWith: String? = null

    // Uniques
    @JvmField var providesFreeBuilding: String? = null
    @JvmField var freeTechs: Int = 0
    @JvmField var unique: String? = null // for wonders which have individual functions that are totally unique


    /**
     * The bonus stats that a resource gets when this building is built
     */
    @JvmField var resourceBonusStats: Stats? = null

    fun getRequiredTech(): Technology = GameBasics.Technologies[requiredTech]!!

    fun getStats(adoptedPolicies: Linq<String>): Stats {
        val stats = this.clone()
        if (adoptedPolicies.contains("Organized Religion") && Linq("Monument", "Temple", "Monastery").contains(name))
            stats.happiness += 1

        if (adoptedPolicies.contains("Free Religion") && Linq("Monument", "Temple", "Monastery").contains(name))
            stats.culture += 1f

        if (adoptedPolicies.contains("Entrepreneurship") && Linq("Mint", "Market", "Bank", "Stock Market").contains(name))
            stats.science += 1f

        if (adoptedPolicies.contains("Humanism") && Linq("University", "Observatory", "Public School").contains(name))
            stats.science += 1f

        if (adoptedPolicies.contains("Theocracy") && name == "Temple")
            percentStatBonus = object : Stats() {
                init {
                    gold = 10f
                }
            }

        if (adoptedPolicies.contains("Free Thought") && name == "University")
            percentStatBonus!!.science = 50f

        if (adoptedPolicies.contains("Rationalism Complete") && !isWonder && stats.science > 0)
            stats.gold += 1f

        if (adoptedPolicies.contains("Constitution") && isWonder)
            stats.culture += 2f

        return stats
    }


    fun getDescription(forBuildingPickerScreen: Boolean, adoptedPolicies: Linq<String>): String {
        val stats = getStats(adoptedPolicies)
        val stringBuilder = StringBuilder()
        if (!forBuildingPickerScreen) stringBuilder.appendln("Cost: " + cost)
        if (isWonder) stringBuilder.appendln("Wonder")
        if (!forBuildingPickerScreen && requiredTech != null)
            stringBuilder.appendln("Requires $requiredTech to be researched")
        if (!forBuildingPickerScreen && requiredBuilding != null)
            stringBuilder.appendln("Requires a $requiredBuilding to be built in this city")
        if (!forBuildingPickerScreen && requiredBuildingInAllCities != null)
            stringBuilder.appendln("Requires a $requiredBuildingInAllCities to be built in all cities")
        if (providesFreeBuilding != null)
            stringBuilder.appendln("Provides a free $providesFreeBuilding in this city")
        stringBuilder.appendln(baseDescription)
        if (stats.toString() != "")
            stringBuilder.appendln(stats)
        if (this.percentStatBonus != null) {
            if (this.percentStatBonus!!.production != 0f) stringBuilder.append("+" + this.percentStatBonus!!.production.toInt() + "% production\r\n")
            if (this.percentStatBonus!!.gold != 0f) stringBuilder.append("+" + this.percentStatBonus!!.gold.toInt() + "% gold\r\n")
            if (this.percentStatBonus!!.science != 0f) stringBuilder.append("+" + this.percentStatBonus!!.science.toInt() + "% science\r\n")
            if (this.percentStatBonus!!.food != 0f) stringBuilder.append("+" + this.percentStatBonus!!.food.toInt() + "% food\r\n")
            if (this.percentStatBonus!!.culture != 0f) stringBuilder.append("+" + this.percentStatBonus!!.culture.toInt() + "% culture\r\n")
        }
        if (this.greatPersonPoints != null) {
            val gpp = this.greatPersonPoints!!
            if (gpp.production != 0f) stringBuilder.appendln("+" + gpp.production.toInt() + " Great Engineer points")
            if (gpp.gold != 0f) stringBuilder.appendln("+" + gpp.gold.toInt() + " Great Merchant points")
            if (gpp.science != 0f) stringBuilder.appendln("+" + gpp.science.toInt() + " Great Scientist points")
            if (gpp.culture != 0f) stringBuilder.appendln("+" + gpp.culture.toInt() + " Great Artist points")
        }
        if (resourceBonusStats != null) {
            val resources = GameBasics.TileResources.linqValues().where { name == it.building }.select { it.name }.joinToString()
            stringBuilder.appendln("$resources provide $resourceBonusStats")
        }
        if (maintenance != 0)
            stringBuilder.appendln("Maintenance cost: $maintenance gold")
        return stringBuilder.toString()
    }

    override fun getProductionCost(adoptedPolicies: Linq<String>): Int {
        return if (!isWonder && culture != 0f && adoptedPolicies.contains("Piety")) (cost * 0.85).toInt()
        else cost
    }

    override fun getGoldCost(adoptedPolicies: Linq<String>): Int {
        var cost = Math.pow((30 * getProductionCost(adoptedPolicies)).toDouble(), 0.75) * (1 + hurryCostModifier / 100)
        if (adoptedPolicies.contains("Mercantilism")) cost *= 0.75
        if (adoptedPolicies.contains("Patronage")) cost *= 0.5
        return (cost / 10).toInt() * 10
    }

    override fun isBuildable(construction: CityConstructions): Boolean {
        if (construction.isBuilt(name)) return false
        val civInfo = construction.cityInfo.civInfo
        if (requiredTech != null && !civInfo.tech.isResearched(requiredTech!!)) return false
        if (isWonder && civInfo.cities.any {
                            it.cityConstructions.isBuilding(name) || it.cityConstructions.isBuilt(name)
                        })
            return false
        if (requiredBuilding != null && !construction.isBuilt(requiredBuilding!!)) return false
        if (requiredBuildingInAllCities != null && civInfo.cities.any { !it.cityConstructions.isBuilt(requiredBuildingInAllCities!!) })
            return false
        if (cannotBeBuiltWith != null && construction.isBuilt(cannotBeBuiltWith!!)) return false
        if ("MustBeNextToDesert" == unique && !civInfo.gameInfo.tileMap.getTilesInDistance(construction.cityInfo.cityLocation, 1).any { it.baseTerrain == "Desert" })
            return false
        if (requiredResource != null && !civInfo.getCivResources().containsKey(GameBasics.TileResources[requiredResource]))
            return false


        if (requiredNearbyImprovedResources != null) {
            val containsResourceWithImprovement = construction.cityInfo.tilesInRange
                    .any { tile ->
                        (tile.resource != null
                                && requiredNearbyImprovedResources!!.contains(tile.resource)
                                && tile.tileResource.improvement == tile.improvement)
                    }
            if (!containsResourceWithImprovement) return false
        }

        if ("SpaceshipPart" == unique) {
            if (!civInfo.buildingUniques.contains("ApolloProgram")) return false
            if (civInfo.scienceVictory.unconstructedParts()[name] == 0) return false // Don't need to build any more of these!
        }
        return true
    }

    override fun postBuildEvent(construction: CityConstructions) {
        val civInfo = construction.cityInfo.civInfo

        if (unique == "SpaceshipPart") {
            civInfo.scienceVictory.currentParts.add(name, 1)
            UnCivGame.Current.screen = ScienceVictoryScreen(civInfo)
            if (civInfo.scienceVictory.unconstructedParts().isEmpty())
                UnCivGame.Current.screen = VictoryScreen()
            return
        }
        construction.builtBuildings.add(name)

        if (providesFreeBuilding != null && !construction.builtBuildings.contains(providesFreeBuilding))
            construction.builtBuildings.add(providesFreeBuilding)
        when (unique) {
            "ApolloProgram" -> UnCivGame.Current.screen = ScienceVictoryScreen(civInfo)
            "EmpireEntersGoldenAge" -> civInfo.goldenAges.enterGoldenAge()
            "FreeGreatArtistAppears" -> civInfo.addGreatPerson("Great Artist")
            "WorkerConstruction" -> {
                civInfo.placeUnitNearTile(construction.cityInfo.cityLocation, "Worker")
                civInfo.placeUnitNearTile(construction.cityInfo.cityLocation, "Worker")
            }
            "FreeSocialPolicy" -> {
                civInfo.policies.freePolicies++
                UnCivGame.Current.screen = PolicyPickerScreen(civInfo)
            }
        }

        if (freeTechs != 0) civInfo.tech.freeTechs += freeTechs
    }
}