package com.unciv.logic.city

import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.city.ConstructionAutomation
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.CivilopediaAction
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.MapUnitAction
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.multiplayer.isUsersTurn
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.IRulesetObject
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.models.ruleset.RejectionReasonType
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.screens.civilopediascreen.CivilopediaCategories
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.utils.withItem
import com.unciv.utils.withoutItem
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * City constructions manager.
 *
 * @property city the city it refers to
 * @property currentConstructionFromQueue name of the construction that is currently being produced
 * @property currentConstructionIsUserSet a flag indicating if the [currentConstructionFromQueue] has been set by the user or by the AI
 * @property constructionQueue a list of constructions names enqueued
 */
class CityConstructions : IsPartOfGameInfoSerialization {
    //region Non-Serialized Properties
    @Transient
    lateinit var city: City

    @Transient
    private var builtBuildingObjects = ArrayList<Building>()

    @Transient
    val builtBuildingUniqueMap = UniqueMap()

    // No backing field, not serialized
    var currentConstructionFromQueue: String
        get() {
            return if (constructionQueue.isEmpty()) ""
            else constructionQueue.first()
        }
        set(value) {
            if (constructionQueue.isEmpty()) constructionQueue.add(value) else constructionQueue[0] = value
        }

    //endregion
    //region Serialized Fields

    var builtBuildings = HashSet<String>()
    val inProgressConstructions = HashMap<String, Int>()
    var currentConstructionIsUserSet = false
    var constructionQueue = mutableListOf<String>()
    var productionOverflow = 0
    private val queueMaxSize = 10

    /** Maps cities by id to a set of the buildings they received (by nation equivalent name)
     *  Source: [UniqueType.GainFreeBuildings]
     */
    val freeBuildingsProvidedFromThisCity: HashMap<String, HashSet<String>> = hashMapOf()

    //endregion
    //region pure functions

    fun clone(): CityConstructions {
        val toReturn = CityConstructions()
        toReturn.builtBuildings.addAll(builtBuildings)
        toReturn.inProgressConstructions.putAll(inProgressConstructions)
        toReturn.currentConstructionIsUserSet = currentConstructionIsUserSet
        toReturn.constructionQueue.addAll(constructionQueue)
        toReturn.productionOverflow = productionOverflow
        toReturn.freeBuildingsProvidedFromThisCity.putAll(freeBuildingsProvidedFromThisCity)
        return toReturn
    }

    // Why is one of these called 'buildable' and the other 'constructable'?
    internal fun getBuildableBuildings(): Sequence<Building> = city.getRuleset().buildings.values
        .asSequence().filter { it.isBuildable(this) }

    fun getConstructableUnits() = city.getRuleset().units.values
        .asSequence().filter { it.isBuildable(this) }

    /**
     * @return [Stats] provided by all built buildings in city plus the bonus from Library
     */
    fun getStats(localUniqueCache: LocalUniqueCache): StatTreeNode {
        val stats = StatTreeNode()
        for (building in getBuiltBuildings())
            stats.addStats(building.getStats(city, localUniqueCache), building.name)
        return stats
    }

    /**
     * @return Maintenance cost of all built buildings
     */
    fun getMaintenanceCosts(): Int {
        var maintenanceCost = 0
        val freeBuildings = city.civ.civConstructions.getFreeBuildingNames(city)

        for (building in getBuiltBuildings())
            if (building.name !in freeBuildings)
                maintenanceCost += building.maintenance

        return maintenanceCost
    }

    fun getCityProductionTextForCityButton(): String {
        val currentConstructionSnapshot = currentConstructionFromQueue // See below
        var result = currentConstructionSnapshot.tr(true)
        if (currentConstructionSnapshot.isNotEmpty()) {
            val construction = PerpetualConstruction.perpetualConstructionsMap[currentConstructionSnapshot]
            result += construction?.getProductionTooltip(city)
                ?: getTurnsToConstructionString(currentConstructionSnapshot)
        }
        return result
    }

    /** @param constructionName needs to be a non-perpetual construction, else an empty string is returned */
    internal fun getTurnsToConstructionString(constructionName: String, useStoredProduction: Boolean = true) =
        getTurnsToConstructionString(getConstruction(constructionName), useStoredProduction)

    /** @param construction needs to be a non-perpetual construction, else an empty string is returned */
    internal fun getTurnsToConstructionString(construction: IConstruction, useStoredProduction: Boolean = true): String {
        if (construction !is INonPerpetualConstruction) return ""   // shouldn't happen
        val cost = construction.getProductionCost(city.civ, city)
        val turnsToConstruction = turnsToConstruction(construction.name, useStoredProduction)
        val currentProgress = if (useStoredProduction) getWorkDone(construction.name) else 0
        val lines = ArrayList<String>()
        val buildable = !construction.getMatchingUniques(UniqueType.Unbuildable)
            .any { it.conditionalsApply(city.state) }
        if (buildable)
            lines += (if (currentProgress == 0) "" else "$currentProgress/") +
                    "$cost${Fonts.production} $turnsToConstruction${Fonts.turn}"
        val otherStats = Stat.entries.filter {
            (it != Stat.Gold || !buildable) &&  // Don't show rush cost for consistency
            construction.canBePurchasedWithStat(city, it)
        }.joinToString(" / ") { "${construction.getStatBuyCost(city, it)}${it.character}" }
        if (otherStats.isNotEmpty()) lines += otherStats
        return lines.joinToString("\n", "\n")
    }

    fun getProductionMarkup(ruleset: Ruleset): FormattedLine {
        val currentConstructionSnapshot = currentConstructionFromQueue
        if (currentConstructionSnapshot.isEmpty()) return FormattedLine()
        val category = when {
            ruleset.buildings[currentConstructionSnapshot]?.isAnyWonder() == true ->
                CivilopediaCategories.Wonder.name
            currentConstructionSnapshot in ruleset.buildings ->
                CivilopediaCategories.Building.name
            currentConstructionSnapshot in ruleset.units ->
                CivilopediaCategories.Unit.name
            else -> ""
        }
        var label = "{$currentConstructionSnapshot}"
        if (!PerpetualConstruction.perpetualConstructionsMap.containsKey(currentConstructionSnapshot)) {
            val turnsLeft = turnsToConstruction(currentConstructionSnapshot)
            label += " - $turnsLeft${Fonts.turn}"
        }
        return if (category.isEmpty()) FormattedLine(label)
            else FormattedLine(label, link="$category/$currentConstructionSnapshot")
    }

    fun getCurrentConstruction(): IConstruction = getConstruction(currentConstructionFromQueue)

    fun isBuilt(buildingName: String): Boolean = builtBuildings.contains(buildingName)

    // Note: There was a isEnqueued here functionally identical to isBeingConstructedOrEnqueued,
    // which was calling both isEnqueued and isBeingConstructed - BUT: currentConstructionFromQueue is just a
    // a wrapper for constructionQueue[0], so that was redundant. Also, isEnqueued was used nowhere,
    // and isBeingConstructed _only_ redundantly as described above.
    // `isEnqueuedForLater` is not optimal code as it can iterate the whole list where checking size
    // and first() would suffice, but the one current use in CityScreenConstructionMenu isn't critical.

    @Suppress("unused", "MemberVisibilityCanBePrivate")  // kept for illustration
    /** @return `true` if [constructionName] is the top queue entry, the one receiving production points */
    fun isBeingConstructed(constructionName: String) = currentConstructionFromQueue == constructionName
    /** @return `true` if [constructionName] is queued but not the top queue entry */
    fun isEnqueuedForLater(constructionName: String) = constructionQueue.indexOf(constructionName) > 0
    /** @return `true` if [constructionName] is anywhere in the construction queue - [isBeingConstructed] **or** [isEnqueuedForLater] */
    fun isBeingConstructedOrEnqueued(constructionName: String) = constructionQueue.contains(constructionName)

    fun isQueueFull(): Boolean = constructionQueue.size >= queueMaxSize

    fun isBuildingWonder(): Boolean {
        val currentConstruction = getCurrentConstruction()
        return currentConstruction is Building && currentConstruction.isWonder
    }

    fun canBeHurried(): Boolean {
        val currentConstruction = getCurrentConstruction()
        return currentConstruction is INonPerpetualConstruction && !currentConstruction.hasUnique(UniqueType.CannotBeHurried)
    }

    /** If the city is constructing multiple units of the same type, subsequent units will require the full cost  */
    fun isFirstConstructionOfItsKind(constructionQueueIndex: Int, name: String): Boolean {
        // Simply compare index of first found [name] with given index
        return constructionQueueIndex == constructionQueue.indexOf(name)
    }


    internal fun getConstruction(constructionName: String): IConstruction {
        val gameBasics = city.getRuleset()
        when {
            constructionName == "" -> return PerpetualConstruction.idle
            gameBasics.buildings.containsKey(constructionName) -> return gameBasics.buildings[constructionName]!!
            gameBasics.units.containsKey(constructionName) -> return gameBasics.units[constructionName]!!
            else -> {
                val special = PerpetualConstruction.perpetualConstructionsMap[constructionName]
                if (special != null) return special
            }
        }

        class NotBuildingOrUnitException(message: String) : Exception(message)
        throw NotBuildingOrUnitException("$constructionName is not a building or a unit!")
    }

    fun getBuiltBuildings(): Sequence<Building> = builtBuildingObjects.asSequence()

    fun containsBuildingOrEquivalent(buildingNameOrUnique: String): Boolean =
            isBuilt(buildingNameOrUnique) || getBuiltBuildings().any { it.replaces == buildingNameOrUnique || it.hasUnique(buildingNameOrUnique, city.state) }

    fun getWorkDone(constructionName: String): Int {
        return if (inProgressConstructions.containsKey(constructionName)) inProgressConstructions[constructionName]!!
            else 0
    }

    fun getRemainingWork(constructionName: String, useStoredProduction: Boolean = true): Int {
        val constr = getConstruction(constructionName)
        return when {
            constr is PerpetualConstruction -> 0
            useStoredProduction -> (constr as INonPerpetualConstruction).getProductionCost(city.civ, city) - getWorkDone(constructionName)
            else -> (constr as INonPerpetualConstruction).getProductionCost(city.civ, city)
        }
    }

    fun turnsToConstruction(constructionName: String, useStoredProduction: Boolean = true): Int {
        val workLeft = getRemainingWork(constructionName, useStoredProduction)
        if (workLeft <= 0) // This most often happens when a production is more than finished in a multiplayer game while its not your turn
            return 0 // So we finish it at the start of the next turn. This could technically also happen when we lower production costs during our turn,
        // but distinguishing those two cases is difficult, and the second one is much rarer than the first
        if (workLeft <= productionOverflow) // if we already have stored up enough production to finish it directly
            return 1 // we'll finish this next turn

        return ceil((workLeft-productionOverflow) / productionForConstruction(constructionName).toDouble()).toInt()
    }

    fun productionForConstruction(constructionName: String): Int {
        val cityStatsForConstruction: Stats
        if (currentConstructionFromQueue == constructionName) cityStatsForConstruction = city.cityStats.currentCityStats
        else {
            /*
            The ol' Switcharoo - what would our stats be if that was our current construction?
            Since this is only ever used for UI purposes, I feel fine with having it be a bit inefficient
            and recalculating the entire city stats
            We don't want to change our current construction queue - what if we have an empty queue,
             this can affect the city if we run it on another thread like in ConstructionsTable -
            So we run the numbers for the other construction
            ALSO apparently if we run on the actual cityStats from another thread,
              we get all sorts of fun concurrency problems when accessing various parts of the cityStats.
            SO, we create an entirely new CityStats and iterate there - problem solve!
            */
            val cityStats = CityStats(city)
            cityStats.statsFromTiles = city.cityStats.statsFromTiles // take as-is
            val construction = city.cityConstructions.getConstruction(constructionName)
            cityStats.update(construction, false, false)
            cityStatsForConstruction = cityStats.currentCityStats
        }

        return cityStatsForConstruction.production.roundToInt()
    }

    fun cheapestStatBuilding(stat: Stat): Building? {
        return city.getRuleset().buildings.values.asSequence()
            .filter { !it.isAnyWonder() && it.isStatRelated(stat, city) &&
                (it.isBuildable(this) || isBeingConstructedOrEnqueued(it.name)) }
            .minByOrNull { it.cost }
    }

    //endregion
    //region state changing functions

    fun setTransients() {
        builtBuildingObjects = ArrayList(builtBuildings.map {
            city.getRuleset().buildings[it]
                    ?: throw java.lang.Exception("Building $it is not found!")
        })
        updateUniques(true)
    }

    fun addProductionPoints(productionToAdd: Int) {
        val construction = getConstruction(currentConstructionFromQueue)
        if (construction is PerpetualConstruction) {
            productionOverflow += productionToAdd
            return
        }
        if (!inProgressConstructions.containsKey(currentConstructionFromQueue))
            inProgressConstructions[currentConstructionFromQueue] = 0
        inProgressConstructions[currentConstructionFromQueue] = inProgressConstructions[currentConstructionFromQueue]!! + productionToAdd
    }

    fun constructIfEnough() {
        validateConstructionQueue()

        // Update InProgressConstructions for any available refunds
        validateInProgressConstructions()

        val construction = getConstruction(currentConstructionFromQueue)
        if (construction is PerpetualConstruction) chooseNextConstruction() // check every turn if we could be doing something better, because this doesn't end by itself
        else {
            val productionCost = (construction as INonPerpetualConstruction).getProductionCost(city.civ, city)
            if (inProgressConstructions.containsKey(currentConstructionFromQueue)
                    && inProgressConstructions[currentConstructionFromQueue]!! >= productionCost) {
                val potentialOverflow = inProgressConstructions[currentConstructionFromQueue]!! - productionCost
                if (completeConstruction(construction)) {
                    // See the URL below for explanation for this cap
                    // https://forums.civfanatics.com/threads/hammer-overflow.419352/
                    val maxOverflow = maxOf(productionCost, city.cityStats.currentCityStats.production.roundToInt())
                    productionOverflow = min(maxOverflow, potentialOverflow)
                }
                else {
                    city.civ.addNotification("No space available to place [${construction.name}] near [${city.name}]",
                        city.location, NotificationCategory.Production, construction.name)
                }
                city.civ.civConstructions.builtItemsWithIncreasingCost[construction.name] += 1
            }
        }
    }

    fun endTurn(cityStats: Stats) {
        validateConstructionQueue()
        validateInProgressConstructions()

        if (getConstruction(currentConstructionFromQueue) !is PerpetualConstruction) {
            if (getWorkDone(currentConstructionFromQueue) == 0) {
                constructionBegun(getConstruction(currentConstructionFromQueue))
            }
            addProductionPoints(cityStats.production.roundToInt() + productionOverflow)
            productionOverflow = 0
        }
    }


    private fun validateConstructionQueue() {
        val queueSnapshot = constructionQueue.toMutableList()
        constructionQueue.clear()

        for (constructionName in queueSnapshot) {
            val construction = getConstruction(constructionName)
            // First construction will be built next turn, we need to make sure it has the correct resources
            if (constructionQueue.isEmpty() && getWorkDone(constructionName) == 0) {
                val costUniques = construction.getMatchingUniquesNotConflicting(UniqueType.CostsResources, 
                    city.state
                )
                val civResources = city.civ.getCivResourcesByName()

                if (costUniques.any {
                            val resourceName = it.params[1]
                            civResources[resourceName] == null
                                    || it.params[0].toInt() > civResources[resourceName]!! })
                    continue // Removes this construction from the queue
            }
            if (construction.isBuildable(this))
                constructionQueue.add(constructionName)
        }
        chooseNextConstruction()
    }

    fun validateInProgressConstructions() {
        // remove obsolete stuff from in progress constructions - happens often and leaves clutter in memory and save files
        // should have little visible consequences - any accumulated points that may be reused later should stay (nukes when manhattan project city lost, nat wonder when conquered an empty city...), all other points should be refunded
        // Should at least be called before each turn - if another civ completes a wonder after our previous turn, we should get the refund this turn
        val inProgressSnapshot = inProgressConstructions.keys.filter { it != currentConstructionFromQueue }
        for (constructionName in inProgressSnapshot) {
            val construction = getConstruction(constructionName)
            // Perpetual constructions should always still be valid (I hope)
            if (construction is PerpetualConstruction) continue

            val rejectionReasons =
                (construction as INonPerpetualConstruction).getRejectionReasons(this)

            if (!rejectionReasons.any { it.hasAReasonToBeRemovedFromQueue() }) continue
            
            val workDone = getWorkDone(constructionName)
            if (construction is Building) {
                // Production put into wonders gets refunded
                if (construction.isWonder && workDone != 0) {
                    city.civ.addGold(workDone)
                    city.civ.addNotification(
                        "Excess production for [$constructionName] converted to [$workDone] gold",
                        city.location,
                        NotificationCategory.Production,
                        NotificationIcon.Gold, "BuildingIcons/${constructionName}")
                }
            } else if (construction is BaseUnit) {
                // Production put into upgradable units gets put into upgraded version
                val cheapestUpgradeUnit = construction.getRulesetUpgradeUnits(city.state)
                    .map { city.civ.getEquivalentUnit(it) }
                    .filter { it.isBuildable(this) }
                    .minByOrNull { it.cost }
                if (rejectionReasons.all { it.type == RejectionReasonType.Obsoleted } && cheapestUpgradeUnit != null) {
                    inProgressConstructions[cheapestUpgradeUnit.name] = (inProgressConstructions[cheapestUpgradeUnit.name] ?: 0) + workDone
                }
            }
            inProgressConstructions.remove(constructionName)
        }
    }

    private fun constructionBegun(construction: IConstruction) {
        val costUniques = construction.getMatchingUniquesNotConflicting(UniqueType.CostsResources, city.state)

        for (unique in costUniques) {
            val amount = unique.params[0].toInt()
            val resourceName = unique.params[1]
            val resource = city.civ.gameInfo.ruleset.tileResources[resourceName] ?: continue
            city.gainStockpiledResource(resource, -amount)
        }

        if (construction !is INonPerpetualConstruction) return
        if (!construction.hasUnique(UniqueType.TriggersAlertOnStart)) return
        val icon = if (construction is Building) "BuildingIcons/${construction.name}" else "UnitIcons/${construction.name}"
        for (otherCiv in city.civ.gameInfo.civilizations) {
            if (otherCiv == city.civ) continue
            when {
                otherCiv.hasExplored(city.getCenterTile()) ->
                    otherCiv.addNotification("The city of [${city.name}] has started constructing [${construction.name}]!",
                        city.location, NotificationCategory.General, NotificationIcon.Construction, icon)
                otherCiv.knows(city.civ) ->
                    otherCiv.addNotification("[${city.civ.civName}] has started constructing [${construction.name}]!",
                        NotificationCategory.General, NotificationIcon.Construction, icon)
                else -> otherCiv.addNotification("An unknown civilization has started constructing [${construction.name}]!",
                    NotificationCategory.General, NotificationIcon.Construction, icon)
            }
        }
    }

    /** Returns false if we tried to construct a unit but it has nowhere to go */
    fun completeConstruction(construction: INonPerpetualConstruction): Boolean {
        var unit: MapUnit? = null
        if (construction is Building) construction.construct(this)
        else if (construction is BaseUnit) {
            unit = construction.construct(this, null)
                ?: return false // unable to place unit
            
            // checking if it's true that we should load saved promotion for the unitType
            // Check if the player want to rebuild the unit the saved promotion
            // and null check.
            // and finally check if the current unit has enough XP.
            val savedPromotion = city.unitToPromotions[unit.baseUnit.name]
            if (city.unitShouldUseSavedPromotion[unit.baseUnit.name] == true &&
                savedPromotion != null && unit.promotions.XP >= savedPromotion.XP) {
                    // sorting it to avoid getting Accuracy III before Accuracy I
                    for (promotions in savedPromotion.promotions.sorted()) {
                        if (unit.promotions.XP >= savedPromotion.XP) {
                            unit.promotions.addPromotion(promotions)
                        } else {
                            break
                        }
                }
            }
        }

        if (construction.name in inProgressConstructions)
            inProgressConstructions.remove(construction.name)
        if (construction.name == currentConstructionFromQueue)
            removeCurrentConstruction()

        validateConstructionQueue() // if we've built e.g. the Great Lighthouse, then Lighthouse is no longer relevant in the queue

        construction as IRulesetObject // Always OK for INonPerpetualConstruction, but compiler doesn't know

        val buildingIcon = "BuildingIcons/${construction.name}"
        val pediaAction = CivilopediaAction(construction.makeLink())
        val locationAction = if (construction is BaseUnit) MapUnitAction(unit!!)
            else LocationAction(city.location)
        val locationAndPediaActions = listOf(locationAction, pediaAction)

        if (construction is Building && construction.isWonder) {
            city.civ.popupAlerts.add(PopupAlert(AlertType.WonderBuilt, construction.name))
            for (civ in city.civ.gameInfo.civilizations) {
                if (civ.hasExplored(city.getCenterTile()))
                    civ.addNotification("[${construction.name}] has been built in [${city.name}]",
                        locationAndPediaActions,
                        if (civ == city.civ) NotificationCategory.Production else NotificationCategory.General, buildingIcon)
                else
                    civ.addNotification("[${construction.name}] has been built in a faraway land",
                        pediaAction, NotificationCategory.General, buildingIcon)
            }
        } else {
            val icon = if (construction is Building) buildingIcon else construction.name // could be a unit, in which case take the unit name.
            city.civ.addNotification(
                "[${construction.name}] has been built in [${city.name}]",
                locationAndPediaActions, NotificationCategory.Production, NotificationIcon.Construction, icon)
        }

        if (construction.hasUnique(UniqueType.TriggersAlertOnCompletion, city.state)) {
            for (otherCiv in city.civ.gameInfo.civilizations) {
                // No need to notify ourself, since we already got the building notification anyway
                if (otherCiv == city.civ) continue
                val completingCivDescription =
                    if (otherCiv.knows(city.civ)) "[${city.civ.civName}]" else "An unknown civilization"
                otherCiv.addNotification("$completingCivDescription has completed [${construction.name}]!",
                    pediaAction, NotificationCategory.General, NotificationIcon.Construction, buildingIcon)
            }
        }
        return true
    }

    fun addBuilding(buildingName: String) {
        val building = city.getRuleset().buildings[buildingName]!!
        addBuilding(building)
    }

    fun addBuilding(building: Building,
                    /** False when creating initial buildings in city - so we don't "waste" a free building on a building we're going to get anyway, from settler buildings */
                    tryAddFreeBuildings: Boolean = true) {
        val buildingName = building.name
        val civ = city.civ

        if (building.cityHealth > 0) {
            // city built a building that increases health so add a portion of this added health that is
            // proportional to the city's current health
            city.health += (building.cityHealth.toFloat() * city.health.toFloat() / city.getMaxHealth().toFloat()).toInt()
        }
        builtBuildingObjects = builtBuildingObjects.withItem(building)
        builtBuildings.add(buildingName)

        updateUniques()

        /** Support for [UniqueType.CreatesOneImprovement] */
        applyCreateOneImprovement(building)

        triggerNewBuildingUniques(building)

        if (building.hasUnique(UniqueType.EnemyUnitsSpendExtraMovement))
            civ.cache.updateHasActiveEnemyMovementPenalty()

        // Korean unique - apparently gives the same as the research agreement
        if (building.isStatRelated(Stat.Science, city) && civ.hasUnique(UniqueType.TechBoostWhenScientificBuildingsBuiltInCapital)
            && city.isCapital())
            civ.tech.addScience(civ.tech.scienceOfLast8Turns.sum() / 8)

        val previousHappiness = civ.getHappiness()
        // can cause civ happiness update: reassignPopulationDeferred -> reassignPopulation -> cityStats.update -> civ.updateHappiness
        city.reassignPopulationDeferred()
        val newHappiness = civ.getHappiness()
        
        /** Same check as [com.unciv.logic.civilization.Civilization.updateStatsForNextTurn] - 
         *   but that triggers *stat calculation* whereas this is for *population assignment* */
        if (previousHappiness != newHappiness && city.civ.gameInfo.ruleset.allHappinessLevelsThatAffectUniques
                .any { newHappiness < it != previousHappiness < it})
            city.civ.cities.filter { it != city }.forEach { it.reassignPopulationDeferred() }

        if (tryAddFreeBuildings)
            city.civ.civConstructions.tryAddFreeBuildings()
    }

    fun triggerNewBuildingUniques(building: Building) {
        val stateForConditionals = city.state
        val triggerNotificationText ="due to constructing [${building.name}]"

        for (unique in building.uniqueObjects)
            if (!unique.hasTriggerConditional() && unique.conditionalsApply(stateForConditionals))
                UniqueTriggerActivation.triggerUnique(unique, city, triggerNotificationText = triggerNotificationText)

        for (unique in city.civ.getTriggeredUniques(UniqueType.TriggerUponConstructingBuilding, stateForConditionals)
                { building.matchesFilter(it.params[0], stateForConditionals) })
            UniqueTriggerActivation.triggerUnique(unique, city, triggerNotificationText = triggerNotificationText)

        for (unique in city.civ.getTriggeredUniques(UniqueType.TriggerUponConstructingBuildingCityFilter, stateForConditionals)
                { building.matchesFilter(it.params[0], stateForConditionals) && city.matchesFilter(it.params[1]) })
            UniqueTriggerActivation.triggerUnique(unique, city, triggerNotificationText = triggerNotificationText)
    }

    fun removeBuilding(buildingName: String) {
        val buildingObject = city.getRuleset().buildings[buildingName]
        if (buildingObject != null)
            builtBuildingObjects = builtBuildingObjects.withoutItem(buildingObject)
        else builtBuildingObjects.removeAll{ it.name == buildingName }
        builtBuildings.remove(buildingName)
        updateUniques()
    }

    fun removeBuilding(building: Building) {
        builtBuildingObjects = builtBuildingObjects.withoutItem(building)
        builtBuildings.remove(building.name)
        updateUniques()
    }

    fun removeBuildings(buildings: Set<Building>) {
        val buildingsToRemove = buildings.map { it.name }.toSet()
        builtBuildings.removeAll {
            it in buildingsToRemove
        }
        setTransients()
    }

    fun updateUniques(onLoadGame: Boolean = false) {
        builtBuildingUniqueMap.clear()
        for (building in getBuiltBuildings())
            builtBuildingUniqueMap.addUniques(building.uniqueObjects)
        if (!onLoadGame) {
            city.civ.cache.updateCitiesConnectedToCapital(false) // could be a connecting building, like a harbor
            city.cityStats.update()
            city.civ.cache.updateCivResources()
        }
    }

    /**
     *  Purchase a construction for gold (or another stat)
     *  called from NextTurnAutomation and the City UI
     *  Build / place the new item, deduct cost, and maintain queue.
     *
     *  @param constructionName What to buy (needed since buying something not queued is allowed)
     *  @param queuePosition    Position in the queue or -1 if not from queue
     *                          Note: -1 does not guarantee queue will remain unchanged (validation)
     *  @param automatic        Flag whether automation should try to choose what next to build (not coming from UI)
     *                          Note: settings.autoAssignCityProduction is handled later
     *  @param stat             Stat object of the stat with which was paid for the construction
     *  @param tile             Supports [UniqueType.CreatesOneImprovement] the tile to place the improvement from that unique on.
     *                          Ignored when the [constructionName] does not have that unique. If null and the building has the unique, a tile is chosen automatically.
     *  @return                 Success (false e.g. unit cannot be placed)
     */
    fun purchaseConstruction(
        constructionName: String,
        queuePosition: Int,
        automatic: Boolean,
        stat: Stat = Stat.Gold,
        tile: Tile? = null
    ): Boolean {
        val construction = getConstruction(constructionName) as? INonPerpetualConstruction ?: return false
        return purchaseConstruction(construction, queuePosition, automatic, stat, tile)
    }

    /**
     *  Purchase a construction for gold (or another stat)
     *  called from NextTurnAutomation and the City UI
     *  Build / place the new item, deduct cost, and maintain queue.
     *
     *  @param construction What to buy (needed since buying something not queued is allowed)
     *  @param queuePosition    Position in the queue or -1 if not from queue
     *                          Note: -1 does not guarantee queue will remain unchanged (validation)
     *  @param automatic        Flag whether automation should try to choose what next to build (not coming from UI)
     *                          Note: settings.autoAssignCityProduction is handled later
     *  @param stat             Stat object of the stat with which was paid for the construction
     *  @param tile             Supports [UniqueType.CreatesOneImprovement] the tile to place the improvement from that unique on.
     *                          Ignored when the [constructionName] does not have that unique. If null and the building has the unique, a tile is chosen automatically.
     *  @return                 Success (false e.g. unit cannot be placed)
     */
    fun purchaseConstruction(
        construction: INonPerpetualConstruction,
        queuePosition: Int,
        automatic: Boolean,
        stat: Stat = Stat.Gold,
        tile: Tile? = null
    ): Boolean {
        // Support UniqueType.CreatesOneImprovement: it is active when getImprovementToCreate returns an improvement
        val improvementToPlace = (construction as? Building)?.getImprovementToCreate(city.getRuleset(), city.civ)
        if (improvementToPlace != null) {
            // If active without a predetermined tile to place the improvement on, automate a tile
            val finalTile = tile
                ?: Automation.getTileForConstructionImprovement(city, improvementToPlace)
                ?: return false // This was never reached in testing
            finalTile.improvementFunctions.markForCreatesOneImprovement(improvementToPlace.name)
            // postBuildEvent does the rest by calling cityConstructions.applyCreateOneImprovement
        }

        if (construction is Building) construction.construct(this)
        else if (construction is BaseUnit) {
            construction.construct(this, stat) 
                ?: return false  // nothing built - no pay
        }

        if (!city.civ.gameInfo.gameParameters.godMode) {
            val constructionCost = construction.getStatBuyCost(city, stat)
                ?: return false // We should never end up here anyway, so things have already gone _way_ wrong
            city.addStat(stat, -1 * constructionCost)

            val conditionalState = city.state

            if ((
                    city.civ.getMatchingUniques(UniqueType.BuyUnitsIncreasingCost, conditionalState) +
                    city.civ.getMatchingUniques(UniqueType.BuyBuildingsIncreasingCost, conditionalState)
                ).any {
                    (
                        construction is BaseUnit && construction.matchesFilter(it.params[0], conditionalState) ||
                        construction is Building && construction.matchesFilter(it.params[0], conditionalState)
                    )
                    && city.matchesFilter(it.params[3])
                    && it.params[2] == stat.name
                }
            ) {
                city.civ.civConstructions.boughtItemsWithIncreasingPrice.add(construction.name, 1)
            }
            
            // Consume stockpiled resources - usually consumed when construction starts, but not when bought
            if (getWorkDone(construction.name) == 0){ // we didn't pay the resources when we started building
                val costUniques = construction.getMatchingUniques(UniqueType.CostsResources, conditionalState)

                for (unique in costUniques) {
                    val amount = unique.params[0].toInt()
                    val resourceName = unique.params[1]
                    val resource = city.civ.gameInfo.ruleset.tileResources[resourceName] ?: continue
                    city.gainStockpiledResource(resource, -amount)
                }
            }
        }

        if (queuePosition in 0 until constructionQueue.size)
            removeFromQueue(queuePosition, automatic)
        validateConstructionQueue()

        return true
    }


    /** This is the *one true test* of "can we buty this construction"
     * This tests whether the buy button should be _enabled_ */
    fun isConstructionPurchaseAllowed(construction: INonPerpetualConstruction, stat: Stat, constructionBuyCost: Int): Boolean {
        return when {
            city.isPuppet && !city.getMatchingUniques(UniqueType.MayBuyConstructionsInPuppets).any() -> false
            city.isInResistance() -> false
            !construction.isPurchasable(city.cityConstructions) -> false    // checks via 'rejection reason'
            construction is BaseUnit && !city.canPlaceNewUnit(construction) -> false
            !construction.canBePurchasedWithStat(city, stat) -> false
            city.civ.gameInfo.gameParameters.godMode -> true
            constructionBuyCost == 0 -> true
            else -> city.getStatReserve(stat) >= constructionBuyCost
        }
    }

    private fun removeCurrentConstruction() = removeFromQueue(0, true)

    fun chooseNextConstruction() {
        if (!isQueueEmptyOrIdle()) {
            // If the USER set a perpetual construction, then keep it!
            if (getConstruction(currentConstructionFromQueue) !is PerpetualConstruction || currentConstructionIsUserSet) return
        }

        val isCurrentPlayersTurn = city.civ.gameInfo.isUsersTurn()
                || !city.civ.gameInfo.gameParameters.isOnlineMultiplayer
        if ((isCurrentPlayersTurn && (UncivGame.Current.settings.autoAssignCityProduction
                || UncivGame.Current.worldScreen?.autoPlay?.isAutoPlayingAndFullAutoPlayAI() == true)) // only automate if the active human player has the setting to automate production
                || city.civ.isAI() || city.isPuppet) {
            ConstructionAutomation(this).chooseNextConstruction()
        }

        /** Support for [UniqueType.CreatesOneImprovement] - if an Improvement-creating Building was auto-queued, auto-choose a tile: */
        val building = getCurrentConstruction() as? Building ?: return
        val improvement = building.getImprovementToCreate(city.getRuleset(), city.civ) ?: return
        if (getTileForImprovement(improvement.name) != null) return
        val newTile = Automation.getTileForConstructionImprovement(city, improvement) ?: return
        newTile.improvementFunctions.markForCreatesOneImprovement(improvement.name)
    }

    fun canAddToQueue(construction: IConstruction) =
        !isQueueFull() &&
        construction.isBuildable(this) &&
        !(construction is Building && isBeingConstructedOrEnqueued(construction.name))

    private fun isLastConstructionPerpetual() = constructionQueue.isNotEmpty() &&
        PerpetualConstruction.isNamePerpetual(constructionQueue.last())
        // `getConstruction(constructionQueue.last()) is PerpetualConstruction` is clear but more expensive

    fun isQueueEmptyOrIdle() = currentConstructionFromQueue.isEmpty()
        || currentConstructionFromQueue == PerpetualConstruction.idle.name

    /** Add [construction] to the end or top (controlled by [addToTop]) of the queue with all checks (does nothing if not possible)
     *
     *  Note: Overload with string parameter `constructionName` exists as well.
     */
    fun addToQueue(construction: IConstruction, addToTop: Boolean = false) {
        if (!canAddToQueue(construction)) return
        val constructionName = construction.name
        when {
            isQueueEmptyOrIdle() ->
                currentConstructionFromQueue = constructionName
            addToTop && construction is PerpetualConstruction && PerpetualConstruction.isNamePerpetual(currentConstructionFromQueue) ->
                currentConstructionFromQueue = constructionName // perpetual constructions will replace each other
            addToTop ->
                constructionQueue.add(0, constructionName)
            isLastConstructionPerpetual() -> {
                // Note this also works if currentConstructionFromQueue is perpetual and the only entry - that var is delegated to the first queue position
                if (construction is PerpetualConstruction) {
                    // perpetual constructions will replace each other
                    constructionQueue.removeLast()
                    constructionQueue.add(constructionName)
                } else
                    constructionQueue.add(constructionQueue.size - 1, constructionName) // insert new construction before perpetual one
            }
            else ->
                constructionQueue.add(constructionName)
        }
        currentConstructionIsUserSet = true
    }

    /** Add a construction named [constructionName] to the end of the queue with all checks
     *
     *  Note: Delegates to overload with `construction` parameter.
     */
    fun addToQueue(constructionName: String) = addToQueue(getConstruction(constructionName))

    /** Remove one entry from the queue by index.
     *  @param automatic  If this was done automatically, we should automatically try to choose a new construction and treat it as such
     */
    fun removeFromQueue(constructionQueueIndex: Int, automatic: Boolean) {
        val constructionName = constructionQueue.removeAt(constructionQueueIndex)

        // UniqueType.CreatesOneImprovement support
        val construction = getConstruction(constructionName)
        if (construction is Building) {
            val improvement = construction.getImprovementToCreate(city.getRuleset(), city.civ)
            if (improvement != null) {
                getTileForImprovement(improvement.name)?.stopWorkingOnImprovement()
            }
        }

        currentConstructionIsUserSet = if (constructionQueue.isEmpty()) {
            if (automatic) chooseNextConstruction()
            else constructionQueue.add(PerpetualConstruction.idle.name) // To prevent Construction Automation
            false
        } else true // we're just continuing the regular queue
    }

    /** Remove all queue entries for [constructionName].
     *
     *  Does nothing if there's no entry of that name in the queue.
     *  If the queue is emptied, no automatic: getSettings().autoAssignCityProduction is ignored! (parameter to be added when needed)
     */
    fun removeAllByName(constructionName: String) {
        while (!isQueueEmptyOrIdle()) {
            val index = constructionQueue.indexOf(constructionName)
            if (index < 0) return
            removeFromQueue(index, false)
        }
    }

    /** Moves an entry to the queue top by index.
     *  No-op when index invalid. Must not be called for PerpetualConstruction entries - unchecked! */
    fun moveEntryToTop(constructionQueueIndex: Int) {
        if (constructionQueueIndex == 0 || constructionQueueIndex >= constructionQueue.size) return
        val constructionName = constructionQueue.removeAt(constructionQueueIndex)
        constructionQueue.add(0, constructionName)
    }

    /** Moves an entry by index to the end of the queue, or just before a PerpetualConstruction
     *  (or replacing a PerpetualConstruction if it itself is one and the queue is by happenstance invalid having more than one of those)
     */
    fun moveEntryToEnd(constructionQueueIndex: Int) {
        if (constructionQueueIndex >= constructionQueue.size) return
        val constructionName = constructionQueue.removeAt(constructionQueueIndex)
        // Some of the overhead of addToQueue is redundant here, but if the complex "needs to replace or go before a perpetual" logic is needed, then use it anyway
        if (isLastConstructionPerpetual()) return addToQueue(constructionName)
        constructionQueue.add(constructionName)
    }

    fun raisePriority(constructionQueueIndex: Int): Int {
        if (constructionQueueIndex == 0) return constructionQueueIndex // Already first
        constructionQueue.swap(constructionQueueIndex - 1, constructionQueueIndex)
        return constructionQueueIndex - 1
    }

    // Lowering == Highering next element in queue
    fun lowerPriority(constructionQueueIndex: Int): Int {
        if (constructionQueueIndex >= constructionQueue.size - 1) return constructionQueueIndex // Already last
        raisePriority(constructionQueueIndex + 1)
        return constructionQueueIndex + 1
    }

    private fun MutableList<String>.swap(idx1: Int, idx2: Int) {
        val tmp = this[idx1]
        this[idx1] = this[idx2]
        this[idx2] = tmp
    }

    /** Support for [UniqueType.CreatesOneImprovement]:
     *
     *  If [building] is an improvement-creating one, find a marked tile matching the improvement to be created
     *  (skip if none found), then un-mark the tile and place the improvement unless [removeOnly] is set.
     */
    private fun applyCreateOneImprovement(building: Building, removeOnly: Boolean = false) {
        val improvement = building.getImprovementToCreate(city.getRuleset(), city.civ)
            ?: return
        val tileForImprovement = getTileForImprovement(improvement.name) ?: return
        tileForImprovement.stopWorkingOnImprovement()  // clears mark
        if (removeOnly) return
        tileForImprovement.setImprovement(improvement.name, city.civ)
        // If bought the worldscreen will not have been marked to update, and the new improvement won't show until later...
        GUI.setUpdateWorldOnNextRender()
    }

    /** Support for [UniqueType.CreatesOneImprovement]:
     *
     *  To be called after circumstances forced clearing a marker from a tile (pillaging, nuking).
     *  Should remove one matching building from the queue without looking for a marked tile.
     */
    fun removeCreateOneImprovementConstruction(improvement: String) {
        val ruleset = city.getRuleset()
        val indexToRemove = constructionQueue.withIndex().firstNotNullOfOrNull {
            val construction = getConstruction(it.value)
            val buildingImprovement = (construction as? Building)?.getImprovementToCreate(ruleset, city.civ)?.name
            it.index.takeIf { buildingImprovement == improvement }
        } ?: return

        constructionQueue.removeAt(indexToRemove)

        currentConstructionIsUserSet = if (constructionQueue.isEmpty()) {
            constructionQueue.add(PerpetualConstruction.idle.name)
            false
        } else true
    }

    /** Support for [UniqueType.CreatesOneImprovement]:
     *
     *  Find the selected tile for a specific improvement being constructed via a building, if any.
     */
    fun getTileForImprovement(improvementName: String) = city.getTiles()
        .firstOrNull {
            it.isMarkedForCreatesOneImprovement(improvementName)
        }
    //endregion
}
