package com.unciv.logic.civilization


import com.badlogic.gdx.graphics.Color
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tech.Technology
import com.unciv.models.gamebasics.unit.Unit
import com.unciv.ui.utils.tr
import java.util.*

class TechManager {
    @Transient
    lateinit var civInfo: CivilizationInfo

    var freeTechs = 0
    var techsResearched = HashSet<String>()
    /* When moving towards a certain tech, the user doesn't have to manually pick every one. */
    var techsToResearch = ArrayList<String>()
    private var techsInProgress = HashMap<String, Int>()

    private fun getCurrentTechnology(): Technology = GameBasics.Technologies[currentTechnology()]!!

    fun getAmountResearchedText(): String =
            if (currentTechnology() == null) ""
            else "(" + researchOfTech(currentTechnology()!!) + "/" + getCurrentTechnology().cost + ")"


    fun currentTechnology(): String? {
        if (techsToResearch.isEmpty()) return null
        else return techsToResearch[0]
    }

    private fun researchOfTech(TechName: String?): Int {
        if (techsInProgress.containsKey(TechName)) return techsInProgress[TechName]!!
        else return 0
    }

    fun turnsToTech(TechName: String): Int {
        return Math.ceil(((GameBasics.Technologies[TechName]!!.cost - researchOfTech(TechName))
                / civInfo.getStatsForNextTurn().science).toDouble()).toInt()
    }

    fun isResearched(TechName: String): Boolean = techsResearched.contains(TechName)

    fun canBeResearched(TechName: String): Boolean {
        return GameBasics.Technologies[TechName]!!.prerequisites.all { isResearched(it) }
    }

    fun nextTurn(scienceForNewTurn: Int) {
        val currentTechnology = currentTechnology()
        if (currentTechnology == null) return
        techsInProgress[currentTechnology] = researchOfTech(currentTechnology) + scienceForNewTurn
        if (techsInProgress[currentTechnology]!! < getCurrentTechnology().cost)
            return

        val previousEra = civInfo.getEra()

        // We finished it!
        techsInProgress.remove(currentTechnology)
        techsToResearch.remove(currentTechnology)
        techsResearched.add(currentTechnology)
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
                val currentConstructionUnit = city.cityConstructions.getCurrentConstruction() as Unit
                city.cityConstructions.currentConstruction = currentConstructionUnit.upgradesTo!!
            }
    }
}


