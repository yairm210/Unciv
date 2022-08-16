package com.unciv.logic.civilization

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.RoadStatus
import com.unciv.models.ruleset.Era
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.utils.MayaCalendar
import com.unciv.ui.utils.extensions.toPercent
import com.unciv.ui.utils.extensions.withItem
import java.util.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class TechManager : IsPartOfGameInfoSerialization {
    @Transient
    var era: Era = Era()

    @Transient
    lateinit var civInfo: CivilizationInfo
    /** This is the Transient list of Technologies */
    @Transient
    var researchedTechnologies = ArrayList<Technology>()
    @Transient
    internal var techUniques = UniqueMap()

    // MapUnit.canPassThrough is the most called function in the game, and having these extremely specific booleans is one way of improving the time cost
    @Transient
    var unitsCanEmbark = false
    @Transient
    var embarkedUnitsCanEnterOcean = false
    @Transient
    var allUnitsCanEnterOcean = false
    @Transient
    var specificUnitsCanEnterOcean = false

    // UnitMovementAlgorithms.getMovementCostBetweenAdjacentTiles is a close second =)
    @Transient
    var movementSpeedOnRoads = 1f
    @Transient
    var roadsConnectAcrossRivers = false

    var freeTechs = 0
    // For calculating score
    var repeatingTechsResearched = 0

    /** For calculating Great Scientist yields - see https://civilization.fandom.com/wiki/Great_Scientist_(Civ5)  */
    var scienceOfLast8Turns = IntArray(8) { 0 }
    var scienceFromResearchAgreements = 0
    /** This is the list of strings, which is serialized */
    var techsResearched = HashSet<String>()

    /** When moving towards a certain tech, the user doesn't have to manually pick every one. */
    var techsToResearch = ArrayList<String>()
    var overflowScience = 0
    var techsInProgress = HashMap<String, Int>()

    /** In civ IV, you can auto-convert a certain percentage of gold in cities to science */
    var goldPercentConvertedToScience = 0.6f

    //region state-changing functions
    fun clone(): TechManager {
        val toReturn = TechManager()
        toReturn.techsResearched.addAll(techsResearched)
        toReturn.freeTechs = freeTechs
        toReturn.repeatingTechsResearched = repeatingTechsResearched
        toReturn.techsInProgress.putAll(techsInProgress)
        toReturn.techsToResearch.addAll(techsToResearch)
        toReturn.scienceOfLast8Turns = scienceOfLast8Turns.clone()
        toReturn.scienceFromResearchAgreements = scienceFromResearchAgreements
        toReturn.overflowScience = overflowScience
        toReturn.goldPercentConvertedToScience = goldPercentConvertedToScience
        return toReturn
    }

    fun getNumberOfTechsResearched(): Int = techsResearched.size

    private fun getRuleset() = civInfo.gameInfo.ruleSet

    fun costOfTech(techName: String): Int {
        var techCost = getRuleset().technologies[techName]!!.cost.toFloat()
        if (civInfo.isPlayerCivilization())
            techCost *= civInfo.getDifficulty().researchCostModifier
        techCost *= civInfo.gameInfo.speed.scienceCostModifier
        val techsResearchedKnownCivs = civInfo.getKnownCivs()
                .count { it.isMajorCiv() && it.tech.isResearched(techName) }
        val undefeatedCivs = civInfo.gameInfo.civilizations
                .count { it.isMajorCiv() && !it.isDefeated() }
        // https://forums.civfanatics.com/threads/the-mechanics-of-overflow-inflation.517970/
        techCost /= 1 + techsResearchedKnownCivs / undefeatedCivs.toFloat() * 0.3f
        // https://civilization.fandom.com/wiki/Map_(Civ5)
        val worldSizeModifier = with (civInfo.gameInfo.tileMap.mapParameters.mapSize) {
            when {
                radius >= MapSize.Huge.radius -> floatArrayOf(1.3f, 0.025f)
                radius >= MapSize.Large.radius -> floatArrayOf(1.2f, 0.0375f)
                radius >= MapSize.Medium.radius -> floatArrayOf(1.1f, 0.05f)
                else -> floatArrayOf(1f, 0.05f)
            }
        }
        techCost *= worldSizeModifier[0]
        techCost *= 1 + (civInfo.cities.size - 1) * worldSizeModifier[1]
        return techCost.toInt()
    }

    fun currentTechnology(): Technology? {
        val currentTechnologyName = currentTechnologyName() ?: return null
        return getRuleset().technologies[currentTechnologyName]
    }

    fun currentTechnologyName(): String? {
        return if (techsToResearch.isEmpty()) null else techsToResearch[0]
    }

    fun researchOfTech(TechName: String?) = techsInProgress[TechName] ?: 0
    // Was once duplicated as fun scienceSpentOnTech(tech: String): Int

    fun remainingScienceToTech(techName: String) = costOfTech(techName) - researchOfTech(techName)

    fun turnsToTech(techName: String) = when {
        civInfo.statsForNextTurn.science <= 0f -> "∞"
        else -> max(1, ceil(remainingScienceToTech(techName).toDouble() / civInfo.statsForNextTurn.science).toInt()).toString()
    }

    fun isResearched(techName: String): Boolean = techsResearched.contains(techName)

    fun canBeResearched(techName: String): Boolean {
        val tech = getRuleset().technologies[techName]!!
        if (tech.uniqueObjects.any { it.type == UniqueType.OnlyAvailableWhen && !it.conditionalsApply(civInfo) })
            return false

        if (isResearched(tech.name) && !tech.isContinuallyResearchable())
            return false

        return tech.prerequisites.all { isResearched(it) }
    }

    //endregion

    fun getRequiredTechsToDestination(destinationTech: Technology): List<Technology> {
        val prerequisites = Stack<Technology>()

        val checkPrerequisites = ArrayDeque<Technology>()
        checkPrerequisites.add(destinationTech)

        while (!checkPrerequisites.isEmpty()) {
            val techToCheck = checkPrerequisites.pop()
            // future tech can have been researched even when we're researching it,
            // so...if we skip it we'll end up with 0 techs in the "required techs", which will mean that we don't have anything to research. Yeah.
            if (!techToCheck.isContinuallyResearchable() &&
                    (isResearched(techToCheck.name) || prerequisites.contains(techToCheck)))
                continue //no need to add or check prerequisites
            for (prerequisite in techToCheck.prerequisites)
                checkPrerequisites.add(getRuleset().technologies[prerequisite]!!)
            prerequisites.add(techToCheck)
        }

        return prerequisites.sortedBy { it.column!!.columnNumber }
    }

    fun getScienceFromGreatScientist(): Int {
        // https://civilization.fandom.com/wiki/Great_Scientist_(Civ5)
        return (scienceOfLast8Turns.sum() * civInfo.gameInfo.speed.scienceCostModifier).toInt()
    }

    private fun addCurrentScienceToScienceOfLast8Turns() {
        // The Science the Great Scientist generates does not include Science from Policies, Trade routes and City-States.
        var allCitiesScience = 0f
        civInfo.cities.forEach { it ->
            val totalBaseScience = it.cityStats.baseStatTree.totalStats.science
            val totalBonusPercents = it.cityStats.statPercentBonusTree.children.asSequence()
                .filter { it.key != "Policies" }.map { it.value.totalStats.science }.sum()
            allCitiesScience += totalBaseScience * totalBonusPercents.toPercent()
        }
        scienceOfLast8Turns[civInfo.gameInfo.turns % 8] = allCitiesScience.toInt()
    }

    private fun limitOverflowScience(overflowScience: Int): Int {
        // http://www.civclub.net/bbs/forum.php?mod=viewthread&tid=123976
        // Apparently yes, we care about the absolute tech cost, not the actual calculated-for-this-player tech cost,
        //  so don't change to costOfTech()
        return min(overflowScience, max(civInfo.statsForNextTurn.science.toInt() * 5,
                getRuleset().technologies[currentTechnologyName()]!!.cost))
    }

    private fun scienceFromResearchAgreements(): Int {
        // https://forums.civfanatics.com/resources/research-agreements-bnw.25568/
        var researchAgreementModifier = 0.5f
        for (unique in civInfo.getMatchingUniques(UniqueType.ScienceFromResearchAgreements)) {
            researchAgreementModifier += unique.params[0].toFloat() / 200f
        }
        return (scienceFromResearchAgreements / 3 * researchAgreementModifier).toInt()
    }

    fun endTurn(scienceForNewTurn: Int) {
        addCurrentScienceToScienceOfLast8Turns()
        if (currentTechnologyName() == null) return

        var finalScienceToAdd = scienceForNewTurn

        if (scienceFromResearchAgreements != 0) {
            finalScienceToAdd += scienceFromResearchAgreements()
            scienceFromResearchAgreements = 0
        }
        if (overflowScience != 0) { // https://forums.civfanatics.com/threads/the-mechanics-of-overflow-inflation.517970/
            val techsResearchedKnownCivs = civInfo.getKnownCivs()
                    .count { it.isMajorCiv() && it.tech.isResearched(currentTechnologyName()!!) }
            val undefeatedCivs = civInfo.gameInfo.civilizations.count { it.isMajorCiv() && !it.isDefeated() }
            val finalScienceFromOverflow = ((1 + techsResearchedKnownCivs / undefeatedCivs.toFloat() * 0.3f) * overflowScience).toInt()
            finalScienceToAdd += finalScienceFromOverflow
            overflowScience = 0
        }

        addScience(finalScienceToAdd)
    }

    fun addScience(scienceGet: Int) {
        val currentTechnology = currentTechnologyName() ?: return
        techsInProgress[currentTechnology] = researchOfTech(currentTechnology) + scienceGet
        if (techsInProgress[currentTechnology]!! < costOfTech(currentTechnology))
            return

        // We finished it!
        // http://www.civclub.net/bbs/forum.php?mod=viewthread&tid=123976
        val extraScienceLeftOver = techsInProgress[currentTechnology]!! - costOfTech(currentTechnology)
        overflowScience += limitOverflowScience(extraScienceLeftOver)
        addTechnology(currentTechnology)
    }

    fun getFreeTechnology(techName: String) {
        freeTechs--
        addTechnology(techName)
    }

    fun addTechnology(techName: String) {
        techsResearched.add(techName)

        // this is to avoid concurrent modification problems
        val newTech = getRuleset().technologies[techName]!!
        if (!newTech.isContinuallyResearchable())
            techsToResearch.remove(techName)
        else
            repeatingTechsResearched++
        researchedTechnologies = researchedTechnologies.withItem(newTech)
        addTechToTransients(newTech)
        for (unique in newTech.uniqueObjects) {
            UniqueTriggerActivation.triggerCivwideUnique(unique, civInfo)
        }
        updateTransientBooleans()
        for (city in civInfo.cities) {
            city.updateCitizens = true
        }

        civInfo.addNotification("Research of [$techName] has completed!", TechAction(techName), NotificationIcon.Science, techName)
        civInfo.popupAlerts.add(PopupAlert(AlertType.TechResearched, techName))

        if (civInfo.playerType == PlayerType.Human) {
            for (revealedResource in getRuleset().tileResources.values.filter { techName == it.revealedBy }) {
                civInfo.gameInfo.notifyExploredResources(civInfo, revealedResource.name, 5, false)
            }
        }

        val obsoleteUnits = getRuleset().units.values.filter { it.obsoleteTech == techName }.map { it.name }
        val unitUpgrades = HashMap<String, HashSet<CityInfo>>()
        for (city in civInfo.cities) {
            // Do not use replaceAll - that's a Java 8 feature and will fail on older phones!
            val oldQueue = city.cityConstructions.constructionQueue.toList()  // copy, since we're changing the queue
            city.cityConstructions.constructionQueue.clear()
            for (constructionName in oldQueue) {
                if (constructionName in obsoleteUnits) {
                    if (constructionName !in unitUpgrades.keys) {
                        unitUpgrades[constructionName] = hashSetOf()
                    }
                    unitUpgrades[constructionName]?.add(city)
                    val construction = city.cityConstructions.getConstruction(constructionName)
                    if (construction is BaseUnit && construction.upgradesTo != null) {
                        city.cityConstructions.constructionQueue.add(construction.upgradesTo!!)
                    }
                } else city.cityConstructions.constructionQueue.add(constructionName)
            }
        }

        // Add notifications for obsolete units/constructions
        for ((unit, cities) in unitUpgrades) {
            val construction = cities.first().cityConstructions.getConstruction(unit)
            if (cities.size == 1) {
                val city = cities.first()
                if (construction is BaseUnit && construction.upgradesTo != null) {
                    val text = "[${city.name}] changed production from [$unit] to [${construction.upgradesTo!!}]"
                    civInfo.addNotification(text, city.location, unit, NotificationIcon.Construction, construction.upgradesTo!!)
                } else {
                    val text = "[$unit] has become obsolete and was removed from the queue in [${city.name}]!"
                    civInfo.addNotification(text, city.location, NotificationIcon.Construction)
                }
            } else {
                val locationAction = LocationAction(cities.asSequence().map { it.location })
                if (construction is BaseUnit && construction.upgradesTo != null) {
                    val text = "[${cities.size}] cities changed production from [$unit] to [${construction.upgradesTo!!}]"
                    civInfo.addNotification(text, locationAction, unit, NotificationIcon.Construction, construction.upgradesTo!!)
                } else {
                    val text = "[$unit] has become obsolete and was removed from the queue in [${cities.size}] cities!"
                    civInfo.addNotification(text, locationAction, NotificationIcon.Construction)
                }
            }
        }

        for (unique in civInfo.getMatchingUniques(UniqueType.ReceiveFreeUnitWhenDiscoveringTech)) {
            if (unique.params[1] != techName) continue
            civInfo.addUnit(unique.params[0])
        }
        for (unique in civInfo.getMatchingUniques(UniqueType.MayanGainGreatPerson)) {
            if (unique.params[1] != techName) continue
            civInfo.addNotification("You have unlocked [The Long Count]!", MayaLongCountAction(), MayaCalendar.notificationIcon)
        }

        val previousEra = civInfo.getEra()
        updateEra()
        val currentEra = civInfo.getEra()
        if (previousEra != currentEra) {
            civInfo.addNotification("You have entered the [$currentEra]!", NotificationIcon.Science)
            if (civInfo.isMajorCiv()) {
                for (knownCiv in civInfo.getKnownCivs()) {
                    knownCiv.addNotification("[${civInfo.civName}] has entered the [$currentEra]!", civInfo.civName, NotificationIcon.Science)
                }
            }
            for (policyBranch in getRuleset().policyBranches.values.filter { it.era == currentEra.name && civInfo.policies.isAdoptable(it) }) {
                civInfo.addNotification("[" + policyBranch.name + "] policy branch unlocked!", NotificationIcon.Culture)
            }
            for (unique in currentEra.uniqueObjects) {
                UniqueTriggerActivation.triggerCivwideUnique(unique, civInfo)
            }
        }
    }

    fun updateEra() {
        val ruleset = civInfo.gameInfo.ruleSet
        if (ruleset.technologies.isEmpty() || researchedTechnologies.isEmpty())
            return

        val maxEraOfResearchedTechs = researchedTechnologies
            .asSequence()
            .map { it.column!! }
            .maxByOrNull { it.columnNumber }!!
            .era
        val maxEra = ruleset.eras[maxEraOfResearchedTechs]!!

        val minEraOfNonResearchedTechs = ruleset.technologies.values
            .asSequence()
            .filter { it !in researchedTechnologies }
            .map { it.column!! }
            .minByOrNull { it.columnNumber }
            ?.era
        if (minEraOfNonResearchedTechs == null) {
            era = maxEra
            return
        }

        val minEra = ruleset.eras[minEraOfNonResearchedTechs]!!

        era = if (minEra.eraNumber <= maxEra.eraNumber) maxEra
        else minEra
    }

    private fun addTechToTransients(tech: Technology) {
        techUniques.addUniques(tech.uniqueObjects)
    }

    fun setTransients() {
        researchedTechnologies.addAll(techsResearched.map { getRuleset().technologies[it]!! })
        researchedTechnologies.forEach { addTechToTransients(it) }
        updateEra()  // before updateTransientBooleans so era-based conditionals can work
        updateTransientBooleans()
    }

    private fun updateTransientBooleans() {
        unitsCanEmbark = civInfo.hasUnique(UniqueType.LandUnitEmbarkation)
        val enterOceanUniques = civInfo.getMatchingUniques(UniqueType.UnitsMayEnterOcean)
        allUnitsCanEnterOcean = enterOceanUniques.any { it.params[0] == "All" }
        embarkedUnitsCanEnterOcean = allUnitsCanEnterOcean ||
                enterOceanUniques.any { it.params[0] == "Embarked" }
        specificUnitsCanEnterOcean = enterOceanUniques.any { it.params[0] != "All" && it.params[0] != "Embarked" }

        movementSpeedOnRoads = if (civInfo.hasUnique(UniqueType.RoadMovementSpeed))
            RoadStatus.Road.movementImproved else RoadStatus.Road.movement
        roadsConnectAcrossRivers = civInfo.hasUnique(UniqueType.RoadsConnectAcrossRivers)
    }

    fun getBestRoadAvailable(): RoadStatus {
        val roadImprovement = RoadStatus.Road.improvement(getRuleset()) // May not exist in mods
        if (roadImprovement == null || !isResearched(roadImprovement.techRequired!!)) return RoadStatus.None

        val railroadImprovement = RoadStatus.Railroad.improvement(getRuleset())
        val canBuildRailroad = railroadImprovement != null && isResearched(railroadImprovement.techRequired!!)

        return if (canBuildRailroad) RoadStatus.Railroad else RoadStatus.Road
    }

    fun canResearchTech(): Boolean {
        return getRuleset().technologies.values.any { canBeResearched(it.name) }
    }
}
