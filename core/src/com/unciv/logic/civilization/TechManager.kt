package com.unciv.logic.civilization


import com.badlogic.gdx.graphics.Color
import com.unciv.logic.map.RoadStatus
import com.unciv.models.gamebasics.GameBasics
import com.unciv.models.gamebasics.tech.Technology
import com.unciv.models.gamebasics.tr
import com.unciv.models.gamebasics.unit.BaseUnit
import com.unciv.ui.utils.withItem
import java.util.*
import kotlin.collections.ArrayList

class TechManager {
    @Transient lateinit var civInfo: CivilizationInfo
    @Transient var researchedTechnologies=ArrayList<Technology>()
    @Transient private var researchedTechUniques=ArrayList<String>()

    // MapUnit.canPassThrough is the most called function in the game, and having these extremey specific booleans is or way of improving the time cost
    @Transient var unitsCanEmbark=false
    @Transient var embarkedUnitsCanEnterOcean=false

    // UnitMovementAlgorithms.getMovementCostBetweenAdjacentTiles is a close second =)
    @Transient var movementSpeedOnRoadsImproved=false

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

    fun costOfTech(techName: String): Int {
        var techCost = GameBasics.Technologies[techName]!!.cost.toFloat()
        techCost *= civInfo.getDifficulty().researchCostModifier
        techCost *= civInfo.gameInfo.gameParameters.gameSpeed.getModifier()
        techCost *= (1 + 0.02 * (civInfo.cities.size -1 )).toFloat()
        return techCost.toInt()
    }

    fun currentTechnology(): Technology? = currentTechnologyName()?.let {
        GameBasics.Technologies[it]
    }

    fun currentTechnologyName(): String? {
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
                / civInfo.statsForNextTurn.science).toInt()
    }

    fun isResearched(TechName: String): Boolean = techsResearched.contains(TechName)

    fun canBeResearched(TechName: String): Boolean {
        return GameBasics.Technologies[TechName]!!.prerequisites.all { isResearched(it) }
    }

    fun getTechUniques() = researchedTechUniques

    //endregion

    fun getRequiredTechsToDestination(destinationTech: Technology): List<String> {
        val prerequisites = Stack<String>()
        val checkPrerequisites = ArrayDeque<String>()
        checkPrerequisites.add(destinationTech.name)

        while (!checkPrerequisites.isEmpty()) {
            val techNameToCheck = checkPrerequisites.pop()
            // future tech can have been researched even when we're researching it,
            // so...if we skip it we'll end up with 0 techs in the "required techs", which will mean that we don't have annything to research. Yeah.
            if (techNameToCheck!="Future Tech" &&
                    (isResearched(techNameToCheck) || prerequisites.contains(techNameToCheck)) )
                continue //no need to add or check prerequisites
            val techToCheck = GameBasics.Technologies[techNameToCheck]
            for (str in techToCheck!!.prerequisites)
                if (!checkPrerequisites.contains(str)) checkPrerequisites.add(str)
            prerequisites.add(techNameToCheck)
        }

        return prerequisites.reversed()
    }

    fun nextTurn(scienceForNewTurn: Int) {
        val currentTechnology = currentTechnologyName()
        if (currentTechnology == null) return
        techsInProgress[currentTechnology] = researchOfTech(currentTechnology) + scienceForNewTurn
        if (techsInProgress[currentTechnology]!! < costOfTech(currentTechnology))
            return

        // We finished it!
        techsInProgress.remove(currentTechnology)
        addTechnology(currentTechnology)
    }

    fun getFreeTechnology(techName:String){
        freeTechs--
        addTechnology(techName)
    }

    fun addTechnology(techName:String) {
        if(techName!="Future Tech")
            techsToResearch.remove(techName)

        val previousEra = civInfo.getEra()
        techsResearched.add(techName)

        // this is to avoid concurrent modification problems
        val newTech = GameBasics.Technologies[techName]!!
        researchedTechnologies = researchedTechnologies.withItem(newTech)
        for(unique in newTech.uniques)
            researchedTechUniques = researchedTechUniques.withItem(unique)
        updateTransientBooleans()

        civInfo.addNotification("Research of [$techName] has completed!", Color.BLUE, TechAction(techName))

        val currentEra = civInfo.getEra()
        if (previousEra < currentEra) {
            civInfo.addNotification("You have entered the [$currentEra era]!".tr(), null, Color.GOLD)
            GameBasics.PolicyBranches.values.filter { it.era == currentEra }
                    .forEach { civInfo.addNotification("[" + it.name + "] policy branch unlocked!".tr(), null, Color.PURPLE) }
        }

        val revealedResource = GameBasics.TileResources.values.firstOrNull { techName == it.revealedBy }

        if (revealedResource != null) {
            for (tileInfo in civInfo.gameInfo.tileMap.values
                    .filter { it.resource == revealedResource.name && civInfo == it.getOwner() }) {

                val closestCityTile = tileInfo.getTilesInDistance(4)
                        .firstOrNull { it.isCityCenter() }
                if (closestCityTile != null) {
                    civInfo.addNotification("{" + revealedResource.name + "} {revealed near} "
                            + closestCityTile.getCity()!!.name, tileInfo.position, Color.BLUE) // todo change to [] notation
                    break
                }
            }
        }

        val obsoleteUnits = GameBasics.Units.values.filter { it.obsoleteTech == techName }
        for (city in civInfo.cities)
            if (city.cityConstructions.getCurrentConstruction() in obsoleteUnits) {
                val currentConstructionUnit = city.cityConstructions.getCurrentConstruction() as BaseUnit
                city.cityConstructions.currentConstruction = currentConstructionUnit.upgradesTo!!
            }

        if(techName=="Writing" && civInfo.nation.unique=="Receive free Great Scientist when you discover Writing, Earn Great Scientists 50% faster"
                && civInfo.cities.any())
            civInfo.addGreatPerson("Great Scientist")
    }

    fun setTransients(){
        // As of 2.10.16, removed mass media, since our tech tree is like G&K
        techsResearched.remove("Mass Media")
        techsToResearch.remove("Mass Media")
        techsInProgress.remove("Mass Media")

        // As of 2.13.15, "Replacable parts" is renamed to "Replaceable Parts"
        val badTechName = "Replacable Parts"
        val goodTechName = "Replaceable Parts"
        if(techsResearched.contains(badTechName)){
            techsResearched.remove(badTechName)
            techsResearched.add(goodTechName)
        }
        if(techsInProgress.containsKey(badTechName)){
            techsInProgress[goodTechName] = techsInProgress[badTechName]!!
            techsInProgress.remove(badTechName)
        }
        if(techsToResearch.contains(badTechName)){
            val newTechToReseach= ArrayList<String>()
            for(tech in techsToResearch)
                newTechToReseach.add(if(tech!=badTechName) tech else goodTechName)
            techsToResearch = newTechToReseach
        }

        researchedTechnologies.addAll(techsResearched.map { GameBasics.Technologies[it]!! })
        researchedTechUniques.addAll(researchedTechnologies.flatMap { it.uniques })
        updateTransientBooleans()
    }

    fun updateTransientBooleans(){
        if(researchedTechUniques.contains("Enables embarkation for land units")) unitsCanEmbark=true
        if(researchedTechUniques.contains("Enables embarked units to enter ocean tiles")) embarkedUnitsCanEnterOcean=true
        if(researchedTechUniques.contains("Improves movement speed on roads")) movementSpeedOnRoadsImproved = true
    }

    fun getBestRoadAvailable(): RoadStatus {
        if (!isResearched(RoadStatus.Road.improvement()!!.techRequired!!)) return RoadStatus.None

        val techEnablingRailroad = RoadStatus.Railroad.improvement()!!.techRequired!!
        val canBuildRailroad = isResearched(techEnablingRailroad)

        return if (canBuildRailroad) RoadStatus.Railroad else RoadStatus.Road
    }
}