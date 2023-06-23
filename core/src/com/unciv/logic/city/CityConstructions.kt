package com.unciv.logic.city

import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.automation.Automation
import com.unciv.logic.automation.city.ConstructionAutomation
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.multiplayer.isUsersTurn
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.models.ruleset.RejectionReasonType
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.StateForConditionals
import com.unciv.models.ruleset.unique.UniqueMap
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.components.Fonts
import com.unciv.ui.components.extensions.withItem
import com.unciv.ui.components.extensions.withoutItem
import com.unciv.ui.screens.civilopediascreen.CivilopediaCategories
import com.unciv.ui.screens.civilopediascreen.FormattedLine
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
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

    // Maps cities to the buildings they received
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

    private fun getBasicStatBuildings(stat: Stat) = city.getRuleset().buildings.values
        .asSequence()
        .filter { !it.isAnyWonder() && it.replaces == null && it[stat] > 0f }

    /**
     * @return [Stats] provided by all built buildings in city plus the bonus from Library
     */
    fun getStats(): StatTreeNode {
        val stats = StatTreeNode()
        val localUniqueCache = LocalUniqueCache()
        for (building in getBuiltBuildings())
            stats.addStats(building.getStats(city, localUniqueCache), building.name)
        return stats
    }

    /**
     * @return Maintenance cost of all built buildings
     */
    fun getMaintenanceCosts(): Int {
        var maintenanceCost = 0
        val freeBuildings = city.civ.civConstructions.getFreeBuildings(city.id)

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

    /** @constructionName needs to be a non-perpetual construction, else an empty string is returned */
    internal fun getTurnsToConstructionString(constructionName: String, useStoredProduction:Boolean = true): String {
        val construction = getConstruction(constructionName)
        if (construction !is INonPerpetualConstruction) return ""   // shouldn't happen
        val cost = construction.getProductionCost(city.civ)
        val turnsToConstruction = turnsToConstruction(constructionName, useStoredProduction)
        val currentProgress = if (useStoredProduction) getWorkDone(constructionName) else 0
        val lines = ArrayList<String>()
        val buildable = !construction.getMatchingUniques(UniqueType.Unbuildable)
            .any { it.conditionalsApply(StateForConditionals(city.civ, city)) }
        if (buildable)
            lines += (if (currentProgress == 0) "" else "$currentProgress/") +
                    "$cost${Fonts.production} $turnsToConstruction${Fonts.turn}"
        val otherStats = Stat.values().filter {
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
    @Suppress("MemberVisibilityCanBePrivate")
    fun isBeingConstructed(constructionName: String): Boolean = currentConstructionFromQueue == constructionName
    fun isEnqueued(constructionName: String): Boolean = constructionQueue.contains(constructionName)
    fun isBeingConstructedOrEnqueued(constructionName: String): Boolean = isBeingConstructed(constructionName) || isEnqueued(constructionName)

    fun isQueueFull(): Boolean = constructionQueue.size == queueMaxSize

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
        // if the construction name is the same as the current construction, it isn't the first
        return constructionQueueIndex == constructionQueue.indexOfFirst { it == name }
    }


    internal fun getConstruction(constructionName: String): IConstruction {
        val gameBasics = city.getRuleset()
        when {
            constructionName == "" -> return getConstruction("Nothing")
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

    internal fun getBuiltBuildings(): Sequence<Building> = builtBuildingObjects.asSequence()

    fun containsBuildingOrEquivalent(buildingNameOrUnique: String): Boolean =
            isBuilt(buildingNameOrUnique) || getBuiltBuildings().any { it.replaces == buildingNameOrUnique || it.hasUnique(buildingNameOrUnique) }

    fun getWorkDone(constructionName: String): Int {
        return if (inProgressConstructions.containsKey(constructionName)) inProgressConstructions[constructionName]!!
            else 0
    }

    fun getRemainingWork(constructionName: String, useStoredProduction: Boolean = true): Int {
        val constr = getConstruction(constructionName)
        return when {
            constr is PerpetualConstruction -> 0
            useStoredProduction -> (constr as INonPerpetualConstruction).getProductionCost(city.civ) - getWorkDone(constructionName)
            else -> (constr as INonPerpetualConstruction).getProductionCost(city.civ)
        }
    }

    fun turnsToConstruction(constructionName: String, useStoredProduction: Boolean = true): Int {
        val workLeft = getRemainingWork(constructionName, useStoredProduction)
        if (workLeft < 0) // This most often happens when a production is more than finished in a multiplayer game while its not your turn
            return 0 // So we finish it at the start of the next turn. This could technically also happen when we lower production costs during our turn,
        // but distinguishing those two cases is difficult, and the second one is much rarer than the first
        if (workLeft <= productionOverflow) // if we already have stored up enough production to finish it directly
            return 1 // we'll finish this next turn

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
            cityStats.update(construction, false)
            cityStatsForConstruction = cityStats.currentCityStats
        }

        val production = cityStatsForConstruction.production.roundToInt()

        return ceil((workLeft-productionOverflow) / production.toDouble()).toInt()
    }

    fun hasBuildableStatBuildings(stat: Stat): Boolean {
        return getBasicStatBuildings(stat)
            .map { city.civ.getEquivalentBuilding(it.name) }
            .filter { it.isBuildable(this) || isBeingConstructedOrEnqueued(it.name) }
            .any()
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
            val productionCost = (construction as INonPerpetualConstruction).getProductionCost(city.civ)
            if (inProgressConstructions.containsKey(currentConstructionFromQueue)
                    && inProgressConstructions[currentConstructionFromQueue]!! >= productionCost) {
                val potentialOverflow = inProgressConstructions[currentConstructionFromQueue]!! - productionCost
                if (constructionComplete(construction)){
                    // See the URL below for explanation for this cap
                    // https://forums.civfanatics.com/threads/hammer-overflow.419352/
                    val maxOverflow = maxOf(productionCost, city.cityStats.currentCityStats.production.roundToInt())
                    productionOverflow = min(maxOverflow, potentialOverflow)
                }
                else {
                    city.civ.addNotification("No space available to place [${construction.name}] near [${city.name}]",
                        city.location, NotificationCategory.Production, construction.name)
                }
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
                val costUniques = construction.getMatchingUniquesNotConflicting(UniqueType.CostsResources)
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
    }

    private fun validateInProgressConstructions() {
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

            if (rejectionReasons.any { it.hasAReasonToBeRemovedFromQueue() }) {
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
                    if (rejectionReasons.all { it.type == RejectionReasonType.Obsoleted } && construction.upgradesTo != null) {
                        val upgradedUnitName = city.civ.getEquivalentUnit(construction.upgradesTo!!).name
                        inProgressConstructions[upgradedUnitName] = (inProgressConstructions[upgradedUnitName] ?: 0) + workDone
                    }
                }
                inProgressConstructions.remove(constructionName)
            }
        }
    }

    private fun constructionBegun(construction: IConstruction) {
        val costUniques = construction.getMatchingUniquesNotConflicting(UniqueType.CostsResources)

        for (unique in costUniques){
            val amount = unique.params[0].toInt()
            val resourceName = unique.params[1]
            city.civ.resourceStockpiles.add(resourceName, -amount)
        }

        if (construction !is Building) return
        if (!construction.hasUnique(UniqueType.TriggersAlertOnStart)) return
        val buildingIcon = "BuildingIcons/${construction.name}"
        for (otherCiv in city.civ.gameInfo.civilizations) {
            if (otherCiv == city.civ) continue
            when {
                otherCiv.hasExplored(city.getCenterTile()) ->
                    otherCiv.addNotification("The city of [${city.name}] has started constructing [${construction.name}]!",
                        city.location, NotificationCategory.General, NotificationIcon.Construction, buildingIcon)
                otherCiv.knows(city.civ) ->
                    otherCiv.addNotification("[${city.civ.civName}] has started constructing [${construction.name}]!",
                        NotificationCategory.General, NotificationIcon.Construction, buildingIcon)
                else -> otherCiv.addNotification("An unknown civilization has started constructing [${construction.name}]!",
                    NotificationCategory.General, NotificationIcon.Construction, buildingIcon)
            }
        }
    }

    /** Returns false if we tried to construct a unit but it has nowhere to go */
    private fun constructionComplete(construction: INonPerpetualConstruction): Boolean {
        val managedToConstruct = construction.postBuildEvent(this)
        if (!managedToConstruct) return false

        if (construction.name in inProgressConstructions)
            inProgressConstructions.remove(construction.name)
        if (construction.name == currentConstructionFromQueue)
            removeCurrentConstruction()

        validateConstructionQueue() // if we've build e.g. the Great Lighthouse, then Lighthouse is no longer relevant in the queue

        val buildingIcon = "BuildingIcons/${construction.name}"
        if (construction is Building && construction.isWonder) {
            city.civ.popupAlerts.add(PopupAlert(AlertType.WonderBuilt, construction.name))
            for (civ in city.civ.gameInfo.civilizations) {
                if (civ.hasExplored(city.getCenterTile()))
                    civ.addNotification("[${construction.name}] has been built in [${city.name}]", city.location,
                        if (civ == city.civ) NotificationCategory.Production else NotificationCategory.General, buildingIcon)
                else
                    civ.addNotification("[${construction.name}] has been built in a faraway land", NotificationCategory.General, buildingIcon)
            }
        } else {
            val icon = if (construction is Building) buildingIcon else construction.name // could be a unit, in which case take the unit name.
            city.civ.addNotification(
                "[${construction.name}] has been built in [${city.name}]",
                    city.location, NotificationCategory.Production, NotificationIcon.Construction, icon)
        }

        if (construction is Building && construction.hasUnique(UniqueType.TriggersAlertOnCompletion,
                StateForConditionals(city.civ, city)
            )) {
            for (otherCiv in city.civ.gameInfo.civilizations) {
                // No need to notify ourself, since we already got the building notification anyway
                if (otherCiv == city.civ) continue
                val completingCivDescription =
                    if (otherCiv.knows(city.civ)) "[${city.civ.civName}]" else "An unknown civilization"
                otherCiv.addNotification("$completingCivDescription has completed [${construction.name}]!",
                    NotificationCategory.General, NotificationIcon.Construction, buildingIcon)
            }
        }
        return true
    }

    fun addBuilding(buildingName: String) {
        val buildingObject = city.getRuleset().buildings[buildingName]!!
        builtBuildingObjects = builtBuildingObjects.withItem(buildingObject)
        builtBuildings.add(buildingName)
        updateUniques()
    }

    fun removeBuilding(buildingName: String) {
        val buildingObject = city.getRuleset().buildings[buildingName]!!
        builtBuildingObjects = builtBuildingObjects.withoutItem(buildingObject)
        builtBuildings.remove(buildingName)
        updateUniques()
    }

    fun updateUniques(onLoadGame:Boolean = false) {
        builtBuildingUniqueMap.clear()
        for (building in getBuiltBuildings())
            builtBuildingUniqueMap.addUniques(building.uniqueObjects)
        if (!onLoadGame) city.cityStats.update()
    }

    fun addFreeBuildings() {
        // "Gain a free [buildingName] [cityFilter]"
        val freeBuildingUniques = city.getLocalMatchingUniques(UniqueType.GainFreeBuildings, StateForConditionals(city.civ, city))

        for (unique in freeBuildingUniques) {
            val freeBuildingName = city.civ.getEquivalentBuilding(unique.params[0]).name
            val citiesThatApply = when (unique.params[1]) {
                "in this city" -> listOf(city)
                "in other cities" -> city.civ.cities.filter { it !== city }
                else -> city.civ.cities.filter { it.matchesFilter(unique.params[1]) }
            }

            for (city in citiesThatApply) {
                if (city.cityConstructions.containsBuildingOrEquivalent(freeBuildingName)) continue
                city.cityConstructions.addBuilding(freeBuildingName)
                if (city.id !in freeBuildingsProvidedFromThisCity)
                    freeBuildingsProvidedFromThisCity[city.id] = hashSetOf()

                freeBuildingsProvidedFromThisCity[city.id]!!.add(freeBuildingName)
            }
        }

        // Civ-level uniques - for these only add free buildings from each city to itself to avoid weirdness on city conquest
        for (unique in city.civ.getMatchingUniques(UniqueType.GainFreeBuildings, stateForConditionals = StateForConditionals(city.civ, city))) {
            val freeBuildingName = city.civ.getEquivalentBuilding(unique.params[0]).name
            if (city.matchesFilter(unique.params[1])) {
                if (city.id !in freeBuildingsProvidedFromThisCity)
                    freeBuildingsProvidedFromThisCity[city.id] = hashSetOf()
                freeBuildingsProvidedFromThisCity[city.id]!!.add(freeBuildingName)
                if (!isBuilt(freeBuildingName))
                    addBuilding(freeBuildingName)
            }
        }


        val autoGrantedBuildings = city.getRuleset().buildings.values
            .filter { it.hasUnique(UniqueType.GainBuildingWhereBuildable) }

        for (building in autoGrantedBuildings)
            if (building.isBuildable(city.cityConstructions))
                addBuilding(building.name)
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

        // Support UniqueType.CreatesOneImprovement: it is active when getImprovementToCreate returns an improvement
        val improvementToPlace = (construction as? Building)?.getImprovementToCreate(city.getRuleset())
        if (improvementToPlace != null) {
            // If active without a predetermined tile to place the improvement on, automate a tile
            val finalTile = tile
                ?: Automation.getTileForConstructionImprovement(city, improvementToPlace)
                ?: return false // This was never reached in testing
            finalTile.improvementFunctions.markForCreatesOneImprovement(improvementToPlace.name)
            // postBuildEvent does the rest by calling cityConstructions.applyCreateOneImprovement
        }

        if (!construction.postBuildEvent(this, stat))
            return false // nothing built - no pay

        if (!city.civ.gameInfo.gameParameters.godMode) {
            val constructionCost = construction.getStatBuyCost(city, stat)
                ?: return false // We should never end up here anyway, so things have already gone _way_ wrong
            city.addStat(stat, -1 * constructionCost)

            val conditionalState = StateForConditionals(civInfo = city.civ, city = city)

            if ((
                    city.civ.getMatchingUniques(UniqueType.BuyUnitsIncreasingCost, conditionalState) +
                    city.civ.getMatchingUniques(UniqueType.BuyBuildingsIncreasingCost, conditionalState)
                ).any {
                    (
                        construction is BaseUnit && construction.matchesFilter(it.params[0]) ||
                        construction is Building && construction.matchesFilter(it.params[0])
                    )
                    && city.matchesFilter(it.params[3])
                    && it.params[2] == stat.name
                }
            ) {
                city.civ.civConstructions.boughtItemsWithIncreasingPrice.add(constructionName, 1)
            }
        }

        if (queuePosition in 0 until constructionQueue.size)
            removeFromQueue(queuePosition, automatic)
        validateConstructionQueue()

        return true
    }

    fun addCheapestBuildableStatBuilding(stat: Stat): String? {
        val cheapestBuildableStatBuilding = getBasicStatBuildings(stat)
            .map { city.civ.getEquivalentBuilding(it.name) }
            .filter { it.isBuildable(this) || isBeingConstructedOrEnqueued(it.name) }
            .minByOrNull { it.cost }?.name
            ?: return null

        constructionComplete(getConstruction(cheapestBuildableStatBuilding) as INonPerpetualConstruction)

        return cheapestBuildableStatBuilding
    }

    private fun removeCurrentConstruction() = removeFromQueue(0, true)

    fun chooseNextConstruction() {
        validateConstructionQueue()
        if (constructionQueue.isNotEmpty()) {
            if (currentConstructionFromQueue != ""
                    // If the USER set a perpetual construction, then keep it!
                    && (getConstruction(currentConstructionFromQueue) !is PerpetualConstruction || currentConstructionIsUserSet)) return
        }

        val isCurrentPlayersTurn = city.civ.gameInfo.isUsersTurn()
                || !city.civ.gameInfo.gameParameters.isOnlineMultiplayer
        if ((UncivGame.Current.settings.autoAssignCityProduction && isCurrentPlayersTurn) // only automate if the active human player has the setting to automate production
                || !city.civ.isHuman() || city.isPuppet) {
            ConstructionAutomation(this).chooseNextConstruction()
        }

        /** Support for [UniqueType.CreatesOneImprovement] - if an Improvement-creating Building was auto-queued, auto-choose a tile: */
        val building = getCurrentConstruction() as? Building ?: return
        val improvement = building.getImprovementToCreate(city.getRuleset()) ?: return
        if (getTileForImprovement(improvement.name) != null) return
        val newTile = Automation.getTileForConstructionImprovement(city, improvement) ?: return
        newTile.improvementFunctions.markForCreatesOneImprovement(improvement.name)
    }

    fun addToQueue(constructionName: String) {
        if (isQueueFull()) return
        val construction = getConstruction(constructionName)
        if (!construction.isBuildable(this)) return
        if (construction is Building && isBeingConstructedOrEnqueued(constructionName)) return
        if (currentConstructionFromQueue == "" || currentConstructionFromQueue == "Nothing") {
            currentConstructionFromQueue = constructionName
        } else if (getConstruction(constructionQueue.last()) is PerpetualConstruction) {
            if (construction is PerpetualConstruction) {  // perpetual constructions will replace each other
                constructionQueue.removeAt(constructionQueue.size - 1)
                constructionQueue.add(constructionName)
            } else
                constructionQueue.add(constructionQueue.size - 1, constructionName) // insert new construction before perpetual one
        } else
            constructionQueue.add(constructionName)
        currentConstructionIsUserSet = true
    }

    /** If this was done automatically, we should automatically try to choose a new construction and treat it as such */
    fun removeFromQueue(constructionQueueIndex: Int, automatic: Boolean) {
        val constructionName = constructionQueue.removeAt(constructionQueueIndex)

        // UniqueType.CreatesOneImprovement support
        val construction = getConstruction(constructionName)
        if (construction is Building) {
            val improvement = construction.getImprovementToCreate(city.getRuleset())
            if (improvement != null) {
                getTileForImprovement(improvement.name)?.stopWorkingOnImprovement()
            }
        }

        currentConstructionIsUserSet = if (constructionQueue.isEmpty()) {
            if (automatic) chooseNextConstruction()
            else constructionQueue.add("Nothing") // To prevent Construction Automation
            false
        } else true // we're just continuing the regular queue
    }

    fun raisePriority(constructionQueueIndex: Int) {
        constructionQueue.swap(constructionQueueIndex - 1, constructionQueueIndex)
    }

    // Lowering == Highering next element in queue
    fun lowerPriority(constructionQueueIndex: Int) {
        raisePriority(constructionQueueIndex + 1)
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
    fun applyCreateOneImprovement(building: Building, removeOnly: Boolean = false) {
        val improvement = building.getImprovementToCreate(city.getRuleset())
            ?: return
        val tileForImprovement = getTileForImprovement(improvement.name) ?: return
        tileForImprovement.stopWorkingOnImprovement()  // clears mark
        if (removeOnly) return
        /**todo unify with [UnitActions.getImprovementConstructionActions] and [MapUnit.workOnImprovement] - this won't allow e.g. a building to place a road */
        tileForImprovement.changeImprovement(improvement.name)
        city.civ.lastSeenImprovement[tileForImprovement.position] = improvement.name
        city.cityStats.update()
        city.civ.cache.updateCivResources()
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
        val indexToRemove = constructionQueue.withIndex().mapNotNull {
            val construction = getConstruction(it.value)
            val buildingImprovement = (construction as? Building)?.getImprovementToCreate(ruleset)?.name
            it.index.takeIf { buildingImprovement == improvement }
        }.firstOrNull() ?: return

        constructionQueue.removeAt(indexToRemove)

        currentConstructionIsUserSet = if (constructionQueue.isEmpty()) {
            constructionQueue.add("Nothing")
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
