package com.unciv.logic.civilization


import com.badlogic.gdx.graphics.Color
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tech.Technology
import com.unciv.models.gamebasics.unit.BaseUnit
import com.unciv.ui.utils.tr
import java.util.*

class TechManager {
    @Transient lateinit var civInfo: CivilizationInfo
    @Transient val researchedTechnologies=ArrayList<Technology>()

    var freeTechs = 0
    var techsResearched = HashSet<String>()
    /* When moving towards a certain tech, the user doesn't have to manually pick every one. */
    var techsToResearch = ArrayList<String>()
    private var techsInProgress = HashMap<String, Int>()

    //region state-changing functions
    fun clone(): TechManager {
        val toReturn = TechManager()
        toReturn.techsResearched.addAll(techsResearched)
        toReturn.freeTechs=freeTechs
        toReturn.techsInProgress.putAll(techsInProgress)
        toReturn.techsToResearch.addAll(techsToResearch)
        return toReturn
    }

    private fun getCurrentTechnology(): Technology = GameBasics.Technologies[currentTechnology()]!!

    fun costOfTech(techName: String): Int {
        return (GameBasics.Technologies[techName]!!.cost * civInfo.getDifficulty().researchCostModifier).toInt()
    }

    fun currentTechnology(): String? {
        if (techsToResearch.isEmpty()) return null
        else return techsToResearch[0]
    }

    private fun researchOfTech(TechName: String?): Int {
        if (techsInProgress.containsKey(TechName)) return techsInProgress[TechName]!!
        else return 0
    }

    fun remainingScienceToTech(techName: String) = costOfTech(techName) - researchOfTech(techName)

    fun turnsToTech(techName: String): Int {
        return Math.ceil( remainingScienceToTech(techName).toDouble()
                / civInfo.getStatsForNextTurn().science).toInt()
    }

    fun isResearched(TechName: String): Boolean = techsResearched.contains(TechName)

    fun canBeResearched(TechName: String): Boolean {
        return GameBasics.Technologies[TechName]!!.prerequisites.all { isResearched(it) }
    }

    fun getUniques() = researchedTechnologies.flatMap { it.uniques }

    //endregion

    fun nextTurn(scienceForNewTurn: Int) {
        val currentTechnology = currentTechnology()
        if (currentTechnology == null) return
        techsInProgress[currentTechnology] = researchOfTech(currentTechnology) + scienceForNewTurn
        if (techsInProgress[currentTechnology]!! < costOfTech(currentTechnology))
            return

        val previousEra = civInfo.getEra()

        // We finished it!
        techsInProgress.remove(currentTechnology)
        if(currentTechnology!="Future Tech")
            techsToResearch.remove(currentTechnology)
        techsResearched.add(currentTechnology)
        researchedTechnologies.add(GameBasics.Technologies[currentTechnology]!!)
        civInfo.addNotification("Research of [$currentTechnology] has completed!", null, Color.BLUE)

        val currentEra = civInfo.getEra()
        if(previousEra < currentEra){
            civInfo.addNotification("You have entered the [$currentEra] era!".tr(),null,Color.GOLD)
            GameBasics.PolicyBranches.values.filter { it.era==currentEra }
                .forEach{civInfo.addNotification("["+it.name+"] policy branch unlocked!".tr(),null,Color.PURPLE)}
        }

        val revealedResource = GameBasics.TileResources.values.firstOrNull { currentTechnology == it.revealedBy }

        if (revealedResource != null) {
            for (tileInfo in civInfo.gameInfo.tileMap.values
                    .filter { it.resource == revealedResource.name && civInfo == it.getOwner() }) {

                val closestCityTile = tileInfo.getTilesInDistance(4)
                        .firstOrNull { it.isCityCenter() }
                if (closestCityTile != null) {
                    civInfo.addNotification("{"+revealedResource.name + "} {revealed near} "
                            + closestCityTile.getCity()!!.name, tileInfo.position, Color.BLUE)
                    break
                }
            }
        }

        val obsoleteUnits = GameBasics.Units.values.filter { it.obsoleteTech==currentTechnology }
        for(city in civInfo.cities)
            if(city.cityConstructions.getCurrentConstruction() in obsoleteUnits){
                val currentConstructionUnit = city.cityConstructions.getCurrentConstruction() as BaseUnit
                city.cityConstructions.currentConstruction = currentConstructionUnit.upgradesTo!!
            }
    }

    fun setTransients(){
        researchedTechnologies.addAll(techsResearched.map { GameBasics.Technologies[it]!! })
    }
}


