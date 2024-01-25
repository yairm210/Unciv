package com.unciv.logic.civilization.managers

import com.unciv.Constants
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.city.City
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.MayaLongCountAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.PolicyAction
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.civilization.TechAction
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.tech.Era
import com.unciv.models.ruleset.tech.Technology
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.ui.components.MayaCalendar
import com.unciv.ui.components.extensions.withItem
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class TechManager : IsPartOfGameInfoSerialization {
    @Transient
    var era: Era = Era()

    @Transient
    lateinit var civInfo: Civilization
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
    private var overflowScience = 0
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

    fun getOverflowScience(techName: String): Int {
        return if (overflowScience == 0) 0
            else (getScienceModifier(techName) * overflowScience).toInt()
    }

    private fun getScienceModifier(techName: String): Float { // https://forums.civfanatics.com/threads/the-mechanics-of-overflow-inflation.517970/
        val techsResearchedKnownCivs = civInfo.getKnownCivs()
            .count { it.isMajorCiv() && it.tech.isResearched(techName) }
        val undefeatedCivs = civInfo.gameInfo.civilizations
            .count { it.isMajorCiv() && !it.isDefeated() }
        return 1 + techsResearchedKnownCivs / undefeatedCivs.toFloat() * 0.3f
    }

    private fun getRuleset() = civInfo.gameInfo.ruleset

    fun costOfTech(techName: String): Int {
        var techCost = getRuleset().technologies[techName]!!.cost.toFloat()
        if (civInfo.isHuman())
            techCost *= civInfo.getDifficulty().researchCostModifier
        techCost *= civInfo.gameInfo.speed.scienceCostModifier
        techCost /= getScienceModifier(techName)
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

    fun researchOfTech(techName: String?) = techsInProgress[techName] ?: 0
    // Was once duplicated as fun scienceSpentOnTech(tech: String): Int

    fun remainingScienceToTech(techName: String): Int {
        val spareScience = if (canBeResearched(techName)) getOverflowScience(techName) else 0
        return costOfTech(techName) - researchOfTech(techName) - spareScience
    }

    fun turnsToTech(techName: String): String {
        val remainingCost = remainingScienceToTech(techName).toDouble()
        return when {
            remainingCost <= 0f -> "0"
            civInfo.stats.statsForNextTurn.science <= 0f -> "∞"
            else -> max(1, ceil(remainingCost / civInfo.stats.statsForNextTurn.science).toInt()).toString()
        }
    }

    fun isResearched(techName: String): Boolean = techsResearched.contains(techName)

    fun isResearched(construction: INonPerpetualConstruction): Boolean = construction.requiredTechs().all{ requiredTech -> isResearched(requiredTech) }

    fun isObsolete(unit: BaseUnit): Boolean = unit.techsThatObsoleteThis().any{ obsoleteTech -> isResearched(obsoleteTech) }

    fun canBeResearched(techName: String): Boolean {
        val tech = getRuleset().technologies[techName]!!
        if (tech.uniqueObjects.any { it.type == UniqueType.OnlyAvailable && !it.conditionalsApply(civInfo) })
            return false

        if (isResearched(tech.name) && !tech.isContinuallyResearchable())
            return false

        return tech.prerequisites.all { isResearched(it) }
    }

    //endregion

    fun getRequiredTechsToDestination(destinationTech: Technology): List<Technology> {
        val prerequisites = mutableListOf<Technology>()

        val checkPrerequisites = ArrayDeque<Technology>()
        checkPrerequisites.add(destinationTech)

        while (!checkPrerequisites.isEmpty()) {
            val techToCheck = checkPrerequisites.removeFirst()
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

    private fun addCurrentScienceToScienceOfLast8Turns(science: Int) {
        scienceOfLast8Turns[civInfo.gameInfo.turns % 8] = science
    }

    private fun limitOverflowScience(overflowScience: Int): Int {
        // http://www.civclub.net/bbs/forum.php?mod=viewthread&tid=123976
        // Apparently yes, we care about the absolute tech cost, not the actual calculated-for-this-player tech cost,
        //  so don't change to costOfTech()
        return min(overflowScience, max(civInfo.stats.statsForNextTurn.science.toInt() * 5,
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
        addCurrentScienceToScienceOfLast8Turns(scienceForNewTurn)
        if (currentTechnologyName() == null) return

        var finalScienceToAdd = scienceForNewTurn

        if (scienceFromResearchAgreements != 0) {
            val scienceBoost = scienceFromResearchAgreements()
            finalScienceToAdd += scienceBoost
            scienceFromResearchAgreements = 0
            civInfo.addNotification("We gained [$scienceBoost] Science from Research Agreement",
                NotificationCategory.General,
                NotificationIcon.Science)
        }
        if (overflowScience != 0) {
            finalScienceToAdd += getOverflowScience(currentTechnologyName()!!)
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

    /**
     * Checks whether the research on the current technology can be completed
     * and, if so, completes the research.
     */
    fun updateResearchProgress() {
        val currentTechnology = currentTechnologyName() ?: return
        val realOverflow = getOverflowScience(currentTechnology)
        val scienceSpent = researchOfTech(currentTechnology) + realOverflow
        if (scienceSpent >= costOfTech(currentTechnology)) {
            overflowScience = 0
            addScience(realOverflow)
        }
    }

    fun getFreeTechnology(techName: String) {
        freeTechs--
        addTechnology(techName)
    }

    fun addTechnology(techName: String, showNotification: Boolean = true) {
        val isNewTech = techsResearched.add(techName)

        // this is to avoid concurrent modification problems
        val newTech = getRuleset().technologies[techName]!!
        if (!newTech.isContinuallyResearchable())
            techsToResearch.remove(techName)
        else
            repeatingTechsResearched++
        techsInProgress.remove(techName)
        researchedTechnologies = researchedTechnologies.withItem(newTech)
        addTechToTransients(newTech)

        moveToNewEra(showNotification)

        if (!civInfo.isSpectator() && showNotification)
            civInfo.addNotification("Research of [$techName] has completed!", TechAction(techName),
                NotificationCategory.General,
                NotificationIcon.Science)
        if (isNewTech)
            civInfo.popupAlerts.add(PopupAlert(AlertType.TechResearched, techName))

        val triggerNotificationText = "due to researching [$techName]"
        for (unique in newTech.uniqueObjects)
            if (!unique.hasTriggerConditional() && unique.conditionalsApply(StateForConditionals(civInfo)))
                UniqueTriggerActivation.triggerCivwideUnique(unique, civInfo, triggerNotificationText = triggerNotificationText)

        for (unique in civInfo.getTriggeredUniques(UniqueType.TriggerUponResearch))
            if (unique.conditionals.any {it.type == UniqueType.TriggerUponResearch && it.params[0] == techName})
                UniqueTriggerActivation.triggerCivwideUnique(unique, civInfo, triggerNotificationText = triggerNotificationText)


        val revealedResources = getRuleset().tileResources.values.filter { techName == it.revealedBy }
        if (civInfo.playerType == PlayerType.Human) {
            for (revealedResource in revealedResources) {
                civInfo.gameInfo.notifyExploredResources(civInfo, revealedResource.name, 5)
            }
        }

        updateTransientBooleans()

        // In the case of a player hurrying research, this civ's resource availability may now be out of date
        // - e.g. when an owned tile by luck already has an appropriate improvement or when a tech provides a resource.
        // That can be seen on WorldScreenTopBar, so better update.
        civInfo.cache.updateCivResources()

        for (city in civInfo.cities) {
            city.reassignPopulationDeferred()
        }

        obsoleteOldUnits(techName)

        for (unique in civInfo.getMatchingUniques(UniqueType.MayanGainGreatPerson)) {
            if (unique.params[1] != techName) continue
            civInfo.addNotification("You have unlocked [The Long Count]!",
                MayaLongCountAction(), NotificationCategory.General, MayaCalendar.notificationIcon)
        }

        updateResearchProgress()
    }

    /** A variant of kotlin's [associateBy] that omits null values */
    private inline fun <T, K, V> Iterable<T>.associateByNotNull(keySelector: (T) -> K, valueTransform: (T) -> V?): Map<K, V> {
        val destination = LinkedHashMap<K, V>()
        for (element in this) {
            val value = valueTransform(element) ?: continue
            destination[keySelector(element)] = value
        }
        return destination
    }

    private fun obsoleteOldUnits(techName: String) {
        // First build a map with obsoleted units to their (nation-specific) upgrade
        fun BaseUnit.getEquivalentUpgradeOrNull(techName: String): BaseUnit? {
            val unitUpgradesTo = automaticallyUpgradedInProductionToUnitByTech(techName)
                ?: return null
            return civInfo.getEquivalentUnit(unitUpgradesTo)
        }
        val obsoleteUnits = getRuleset().units.entries
            .associateByNotNull({ it.key }, { it.value.getEquivalentUpgradeOrNull(techName) })
        if (obsoleteUnits.isEmpty()) return

        // Apply each to all cities - and remember which cities had which obsoleted unit
        //  in their construction queues in this Map<String, MutableSet<City>>:
        val unitUpgrades = obsoleteUnits.keys.associateWith { mutableSetOf<City>() }
        fun transformConstruction(old: String, city: City): String? {
            val entry = unitUpgrades[old] ?: return old  // Entry OK, not obsolete
            entry.add(city)  // Remember city has updated its queue
            return obsoleteUnits[old]?.name  // Replacement, or pass through null to remove from queue
        }
        for (city in civInfo.cities) {
            // Replace queue - the sequence iteration and finalization happens before the result
            // is reassigned, therefore no concurrent modification worries
            city.cityConstructions.constructionQueue =
                city.cityConstructions.constructionQueue
                .asSequence()
                .mapNotNull { transformConstruction(it, city) }
                .toMutableList()
        }

        // Add notifications for obsolete units/constructions
        for ((unit, cities) in unitUpgrades) {
            if (cities.isEmpty()) continue

            //The validation check happens again while processing start and end of turn,
            //but for mid-turn free tech picks like Oxford University, it should happen immediately
            //so the hammers from the obsolete unit are guaranteed to go to the upgraded unit
            //and players don't think they lost all their production mid turn
            for(city in cities)
                city.cityConstructions.validateInProgressConstructions()

            val locationAction = LocationAction(cities.asSequence().map { it.location })
            val cityText = if (cities.size == 1) "[${cities.first().name}]"
                else "[${cities.size}] cities"
            val newUnit = obsoleteUnits[unit]?.name
            val text = if (newUnit == null)
                "[$unit] has become obsolete and was removed from the queue in $cityText!"
                else "$cityText changed production from [$unit] to [$newUnit]"
            val icons = if (newUnit == null)
                arrayOf(NotificationIcon.Construction)
                else arrayOf(unit, NotificationIcon.Construction, newUnit)
            civInfo.addNotification(text, locationAction, NotificationCategory.Production, *icons)
        }
    }

    private fun moveToNewEra(showNotification: Boolean = true) {
        val previousEra = civInfo.getEra()
        updateEra()
        val currentEra = civInfo.getEra()
        if (previousEra != currentEra) {
            if(showNotification) {
                if(!civInfo.isSpectator())
                    civInfo.addNotification(
                        "You have entered the [$currentEra]!",
                        NotificationCategory.General,
                        NotificationIcon.Science
                    )
                if (civInfo.isMajorCiv()) {
                    for (knownCiv in civInfo.getKnownCivsWithSpectators()) {
                        knownCiv.addNotification(
                            "[${civInfo.civName}] has entered the [$currentEra]!",
                            NotificationCategory.General, civInfo.civName, NotificationIcon.Science
                        )
                    }
                }
                for (policyBranch in getRuleset().policyBranches.values.filter {
                    it.era == currentEra.name && civInfo.policies.isAdoptable(it)
                }) {
                    if (!civInfo.isSpectator())
                        civInfo.addNotification(
                            "[${policyBranch.name}] policy branch unlocked!",
                            PolicyAction(policyBranch.name),
                            NotificationCategory.General,
                            NotificationIcon.Culture
                        )
                }
            }

            val erasPassed = getRuleset().eras.values
                .filter { it.eraNumber > previousEra.eraNumber && it.eraNumber <= currentEra.eraNumber }
                .sortedBy { it.eraNumber }


            for (era in erasPassed)
                for (unique in era.uniqueObjects)
                    if (!unique.hasTriggerConditional() && unique.conditionalsApply(StateForConditionals(civInfo)))
                        UniqueTriggerActivation.triggerCivwideUnique(
                            unique,
                            civInfo,
                            triggerNotificationText = "due to entering the [${era.name}]"
                        )

            val eraNames = erasPassed.map { it.name }.toHashSet()
            for (unique in civInfo.getTriggeredUniques(UniqueType.TriggerUponEnteringEra))
                for (eraName in eraNames)
                    if (unique.conditionals.any { it.type == UniqueType.TriggerUponEnteringEra && it.params[0] == eraName })
                        UniqueTriggerActivation.triggerCivwideUnique(
                            unique,
                            civInfo,
                            triggerNotificationText = "due to entering the [$eraName]"
                        )
        }
    }

    private fun updateEra() {
        val ruleset = civInfo.gameInfo.ruleset
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

    fun setTransients(civInfo: Civilization) {
        this.civInfo = civInfo
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
                enterOceanUniques.any { it.params[0] == Constants.embarked }
        specificUnitsCanEnterOcean = enterOceanUniques.any { it.params[0] != "All" && it.params[0] != Constants.embarked }

        movementSpeedOnRoads = if (civInfo.hasUnique(UniqueType.RoadMovementSpeed))
            RoadStatus.Road.movementImproved else RoadStatus.Road.movement
        roadsConnectAcrossRivers = civInfo.hasUnique(UniqueType.RoadsConnectAcrossRivers)
    }

    fun getBestRoadAvailable(): RoadStatus {
        val railroadImprovement = RoadStatus.Railroad.improvement(getRuleset())  // May not exist in mods
        if (railroadImprovement != null && (railroadImprovement.techRequired==null || isResearched(railroadImprovement.techRequired!!)))
            return RoadStatus.Railroad

        val roadImprovement = RoadStatus.Road.improvement(getRuleset())
        if (roadImprovement != null && (roadImprovement.techRequired==null || isResearched(roadImprovement.techRequired!!)))
            return RoadStatus.Road

        return RoadStatus.None
    }

    fun canResearchTech(): Boolean {
        return getRuleset().technologies.values.any { canBeResearched(it.name) }
    }
}
