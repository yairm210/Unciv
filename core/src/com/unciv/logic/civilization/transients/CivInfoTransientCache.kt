package com.unciv.logic.civilization.transients

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.Proximity
import com.unciv.logic.civilization.transients.CapitalConnectionsFinder.CapitalConnectionMedium
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.tile.Tile
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.*
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stats
import com.unciv.utils.DebugUtils
import java.util.EnumSet

/** CivInfo class was getting too crowded */
class CivInfoTransientCache(val civInfo: Civilization) {

    @Transient
    var lastEraResourceUsedForBuilding = java.util.HashMap<String, Int>()

    @Transient
    val lastEraResourceUsedForUnit = java.util.HashMap<String, Int>()

    /** Easy way to look up a Civilization's unique units and buildings */
    @Transient
    val uniqueUnits = hashSetOf<BaseUnit>()

    @Transient
    val uniqueImprovements = hashSetOf<TileImprovement>()

    @Transient
    val uniqueBuildings = hashSetOf<Building>()

    /** Contains mapping of cities to travel mediums from ALL civilizations connected by trade routes to the capital */
    @Transient
    var citiesConnectedToCapitalToMediums = mapOf<City, EnumSet<CapitalConnectionMedium>>()

    fun updateState() {
        civInfo.state = GameContext(civInfo)
    }

    fun setTransients() {
        val ruleset = civInfo.gameInfo.ruleset

        val state = civInfo.state
        val buildingsToRequiredResources = ruleset.buildings.values
                .filter { civInfo.getEquivalentBuilding(it) == it }
                .associateWith { it.requiredResources(state) }

        val unitsToRequiredResources = ruleset.units.values
                .filter { civInfo.getEquivalentUnit(it) == it }
                .associateWith { it.requiredResources(state) }

        for (resource in ruleset.tileResources.values.asSequence().filter { it.resourceType == ResourceType.Strategic }.map { it.name }) {
            val applicableBuildings = buildingsToRequiredResources.filter { it.value.contains(resource) }.map { it.key }
            val applicableUnits = unitsToRequiredResources.filter { it.value.contains(resource) }.map { it.key }

            val lastEraForBuilding = applicableBuildings.maxOfOrNull { it.era(ruleset)?.eraNumber ?: 0 }
            val lastEraForUnit = applicableUnits.maxOfOrNull { it.era(ruleset)?.eraNumber ?: 0 }

            if (lastEraForBuilding != null)
                lastEraResourceUsedForBuilding[resource] = lastEraForBuilding
            if (lastEraForUnit != null)
                lastEraResourceUsedForUnit[resource] = lastEraForUnit
        }

        for (building in ruleset.buildings.values) {
            if (building.uniqueTo != null && civInfo.matchesFilter(building.uniqueTo!!)) {
                uniqueBuildings.add(building)
            }
        }

        for (improvement in ruleset.tileImprovements.values)
            if (improvement.uniqueTo != null && civInfo.matchesFilter(improvement.uniqueTo!!))
                uniqueImprovements.add(improvement)

        for (unit in ruleset.units.values) {
            if (unit.uniqueTo != null && civInfo.matchesFilter(unit.uniqueTo!!)) {
                uniqueUnits.add(unit)
            }
        }
    }

    fun updateSightAndResources() {
        updateViewableTiles()
        updateHasActiveEnemyMovementPenalty()
        updateCivResources()
    }

    // This is a big performance
    fun updateViewableTiles(explorerPosition: Vector2? = null) {
        setNewViewableTiles()

        updateViewableInvisibleTiles()

        updateLastSeenImprovements()

        // updating the viewable tiles also affects the explored tiles, obviously.
        // So why don't we play switcharoo with the explored tiles as well?
        // Well, because it gets REALLY LARGE so it's a lot of memory space,
        // and we never actually iterate on the explored tiles (only check contains()),
        // so there's no fear of concurrency problems.
        civInfo.viewableTiles.asSequence().forEach { tile ->
            tile.setExplored(civInfo, true, explorerPosition)
        }


        val viewedCivs = HashMap<Civilization, Tile>()
        for (tile in civInfo.viewableTiles) {
            val tileOwner = tile.getOwner()
            if (tileOwner != null) viewedCivs[tileOwner] = tile
            val unitOwner = tile.getFirstUnit()?.civ
            if (unitOwner != null) viewedCivs[unitOwner] = tile
        }

        if (!civInfo.isBarbarian) {
            for (entry in viewedCivs) {
                val metCiv = entry.key
                if (metCiv == civInfo || metCiv.isBarbarian || civInfo.diplomacy.containsKey(metCiv.civName)) continue
                civInfo.diplomacyFunctions.makeCivilizationsMeet(metCiv)
                if(!civInfo.isSpectator())
                    civInfo.addNotification("We have encountered [${metCiv.civName}]!",
                        entry.value.position,
                        NotificationCategory.Diplomacy, metCiv.civName,
                        NotificationIcon.Diplomacy
                    )
                metCiv.addNotification("We have encountered [${civInfo.civName}]!",
                    entry.value.position,
                    NotificationCategory.Diplomacy, civInfo.civName,
                    NotificationIcon.Diplomacy
                )
            }

            discoverNaturalWonders()
        }
    }

    private fun updateViewableInvisibleTiles() {
        val newViewableInvisibleTiles = HashSet<Tile>()
        for (unit in civInfo.units.getCivUnits()) {
            val invisibleUnitUniques = unit.getMatchingUniques(UniqueType.CanSeeInvisibleUnits)
            if (invisibleUnitUniques.none()) continue
            val visibleUnitTypes = invisibleUnitUniques.map { it.params[0] }
                .toList() // save this, it'll be seeing a lot of use
            for (tile in unit.viewableTiles) {
                if (tile.militaryUnit == null) continue
                if (tile in newViewableInvisibleTiles) continue
                if (visibleUnitTypes.any { tile.militaryUnit!!.matchesFilter(it) })
                    newViewableInvisibleTiles.add(tile)
            }
        }

        civInfo.viewableInvisibleUnitsTiles = newViewableInvisibleTiles
    }

    var ourTilesAndNeighboringTiles: Set<Tile> = HashSet()

    /** Our tiles update pretty infrequently - most 'viewable tile' changes are due to unit movements,
     * which means we can store this separately and use it 'as is' so we don't need to find the neighboring tiles every time
     * a unit moves */
    fun updateOurTiles() {
        ourTilesAndNeighboringTiles = civInfo.cities.asSequence()
            .flatMap { it.getTiles() } // our owned tiles, still distinct
            .flatMap { sequenceOf(it) + it.neighbors }
            // now we got a mix of owned, unowned and competitor-owned tiles, and **duplicates**
            // but Sequence.toSet is just as good at making them distinct as any other operation
            .toSet()

        updateViewableTiles()
        updateCivResources()
    }

    private fun setNewViewableTiles() {
        if (civInfo.isDefeated()) {
            // Avoid meeting dead city states when entering a tile owned by their former ally (#9245)
            // In that case ourTilesAndNeighboringTiles and getCivUnits will be empty, but the for
            // loop getKnownCivs/getAllyCiv would add tiles.
            civInfo.viewableTiles = emptySet()
            return
        }

        // while spectating all map is visible
        if (civInfo.isSpectator() || DebugUtils.VISIBLE_MAP) {
            val allTiles = civInfo.gameInfo.tileMap.values.toSet()
            civInfo.viewableTiles = allTiles
            civInfo.viewableInvisibleUnitsTiles = allTiles
            return
        }

        val newViewableTiles = HashSet<Tile>(ourTilesAndNeighboringTiles)
        newViewableTiles.addAll(civInfo.units.getCivUnits().flatMap { unit -> unit.viewableTiles.asSequence().filter { it.getOwner() != civInfo } })

        for (otherCiv in civInfo.getKnownCivs()) {
            if (otherCiv.allyCiv == civInfo || otherCiv == civInfo.allyCiv) {
                newViewableTiles.addAll(otherCiv.cities.asSequence().flatMap { it.getTiles() })
            }
        }

        newViewableTiles.addAll(civInfo.espionageManager.getTilesVisibleViaSpies())

        civInfo.viewableTiles = newViewableTiles // to avoid concurrent modification problems
    }

    private fun updateLastSeenImprovements() {
        if (civInfo.playerType == PlayerType.AI) return // don't bother for AI, they don't really use the info anyway

        for (tile in civInfo.viewableTiles)
            civInfo.setLastSeenImprovement(tile.position, tile.improvement)
    }

    /** Visible for DevConsole use only */
    fun discoverNaturalWonders() {
        val newlyViewedNaturalWonders = HashSet<Tile>()
        for (tile in civInfo.viewableTiles) {
            if (tile.naturalWonder != null && !civInfo.naturalWonders.contains(tile.naturalWonder!!))
                newlyViewedNaturalWonders += tile
        }

        for (tile in newlyViewedNaturalWonders) {
            // GBR could be discovered twice otherwise!
            if (civInfo.naturalWonders.contains(tile.naturalWonder))
                continue
            civInfo.naturalWonders.add(tile.naturalWonder!!)
            if (civInfo.isSpectator()) continue // don't trigger anything

            civInfo.addNotification("We have discovered [${tile.naturalWonder}]!",
                tile.position, NotificationCategory.General, "StatIcons/Happiness")

            val statsGained = Stats()

            val discoveredNaturalWonders = civInfo.gameInfo.civilizations.filter { it != civInfo && it.isMajorCiv() }
                    .flatMap { it.naturalWonders }
            if (tile.terrainHasUnique(UniqueType.GrantsStatsToFirstToDiscover)
                    && !discoveredNaturalWonders.contains(tile.naturalWonder!!)) {

                for (unique in tile.getTerrainMatchingUniques(UniqueType.GrantsStatsToFirstToDiscover)) {
                    statsGained.add(unique.stats)
                }
            }

            for (unique in civInfo.getMatchingUniques(UniqueType.StatBonusWhenDiscoveringNaturalWonder)) {

                val normalBonus = Stats.parse(unique.params[0])
                val firstDiscoveredBonus = Stats.parse(unique.params[1])

                if (discoveredNaturalWonders.contains(tile.naturalWonder!!))
                    statsGained.add(normalBonus)
                else
                    statsGained.add(firstDiscoveredBonus)
            }

            var naturalWonder: String? = null

            if (!statsGained.isEmpty()) {
                naturalWonder = tile.naturalWonder!!
            }

            if (!statsGained.isEmpty() && naturalWonder != null) {
                civInfo.addStats(statsGained)
                civInfo.addNotification("We have received [${statsGained}] for discovering [${naturalWonder}]",
                    Notification.NotificationCategory.General, statsGained.toString()
                    )
            }

            for (unique in civInfo.getTriggeredUniques(UniqueType.TriggerUponDiscoveringNaturalWonder,
                GameContext(civInfo, tile = tile)
            ))
                UniqueTriggerActivation.triggerUnique(unique, civInfo, tile=tile, triggerNotificationText = "due to discovering a Natural Wonder")
        }
    }

    fun updateHasActiveEnemyMovementPenalty() {
        civInfo.hasActiveEnemyMovementPenalty = civInfo.hasUnique(UniqueType.EnemyUnitsSpendExtraMovement)
        civInfo.enemyMovementPenaltyUniques =
                civInfo.getMatchingUniques(UniqueType.EnemyUnitsSpendExtraMovement)
    }

    fun updateCitiesConnectedToCapital(initialSetup: Boolean = false) {
        if (civInfo.cities.isEmpty()) return // No cities to connect

        val oldConnectedCities = if (initialSetup)
            civInfo.cities.filterTo(mutableSetOf()) { it.connectedToCapitalStatus }
            else citiesConnectedToCapitalToMediums.keys

        citiesConnectedToCapitalToMediums = CapitalConnectionsFinder(civInfo).find()

        val newConnectedCities = citiesConnectedToCapitalToMediums.keys

        for (city in newConnectedCities)
            if (city !in oldConnectedCities && city.civ == civInfo && city != civInfo.getCapital())
                civInfo.addNotification("[${city.name}] has been connected to your capital!",
                    city.location, NotificationCategory.Cities, NotificationIcon.Gold
                )

        // This may still contain cities that have just been destroyed by razing - thus the population test
        for (city in oldConnectedCities)
            if (city !in newConnectedCities && city.civ == civInfo && city.population.population > 0)
                civInfo.addNotification("[${city.name}] has been disconnected from your capital!",
                    city.location, NotificationCategory.Cities, NotificationIcon.Gold
                )

        for (city in civInfo.cities)
            city.connectedToCapitalStatus = city in newConnectedCities
    }

    fun updateCivResources() {
        val newDetailedCivResources = ResourceSupplyList()
        val resourceModifers = civInfo.getResourceModifiers()
        for (city in civInfo.cities) newDetailedCivResources.add(city.getResourcesGeneratedByCity(resourceModifers))

        if (!civInfo.isCityState) {
            // First we get all these resources of each city state separately
            val cityStateProvidedResources = ResourceSupplyList()
            var resourceBonusPercentage = 1f
            for (unique in civInfo.getMatchingUniques(UniqueType.CityStateResources))
                resourceBonusPercentage += unique.params[0].toFloat() / 100
            for (cityStateAlly in civInfo.getKnownCivs().filter { it.allyCiv == civInfo }) {
                for (resourceSupply in cityStateAlly.cityStateFunctions.getCityStateResourcesForAlly()) {
                    if (resourceSupply.resource.hasUnique(UniqueType.CannotBeTraded, cityStateAlly.state)) continue
                    val newAmount = (resourceSupply.amount * resourceBonusPercentage).toInt()
                    cityStateProvidedResources.add(resourceSupply.copy(amount = newAmount))
                }
            }
            // Then we combine these into one
            newDetailedCivResources.addByResource(cityStateProvidedResources, Constants.cityStates)
        }

        for (unique in civInfo.getMatchingUniques(UniqueType.ProvidesResources)) {
            if (unique.sourceObjectType == UniqueTarget.Building || unique.sourceObjectType == UniqueTarget.Wonder) continue // already calculated in city
            val resource = civInfo.gameInfo.ruleset.tileResources[unique.params[1]]!!
            newDetailedCivResources.add(
                resource,
                unique.getSourceNameForUser(),
                (unique.params[0].toFloat() * civInfo.getResourceModifier(resource)).toInt()
            )
        }

        for (diplomacyManager in civInfo.diplomacy.values)
            newDetailedCivResources.add(diplomacyManager.resourcesFromTrade())

        for (unit in civInfo.units.getCivUnits())
            newDetailedCivResources.subtractResourceRequirements(
                unit.getResourceRequirementsPerTurn(), civInfo.gameInfo.ruleset, "Units")

        newDetailedCivResources.removeAll { it.resource.isCityWide }

        // Check if anything has actually changed so we don't update stats for no reason - this uses List equality which means it checks the elements
        if (civInfo.detailedCivResources == newDetailedCivResources) return

        val summarizedResourceSupply = newDetailedCivResources.sumByResource("All")

        val newResourceUniqueMap = UniqueMap()
        for (resource in summarizedResourceSupply)
            if (resource.amount > 0)
                newResourceUniqueMap.addUniques(resource.resource.uniqueObjects)
        
        civInfo.detailedCivResources = newDetailedCivResources
        civInfo.summarizedCivResourceSupply = summarizedResourceSupply
        civInfo.civResourcesUniqueMap = newResourceUniqueMap

        civInfo.updateStatsForNextTurn() // More or less resources = more or less happiness, with potential domino effects
    }


    fun updateProximity(otherCiv: Civilization, preCalculated: Proximity? = null): Proximity {
        if (otherCiv == civInfo)   return Proximity.None
        if (preCalculated != null) {
            // We usually want to update this for a pair of civs at the same time
            // Since this function *should* be symmetrical for both civs, we can just do it once
            civInfo.proximity[otherCiv.civName] = preCalculated
            return preCalculated
        }
        if (civInfo.cities.isEmpty() || otherCiv.cities.isEmpty()) {
            civInfo.proximity[otherCiv.civName] = Proximity.None
            return Proximity.None
        }

        val mapParams = civInfo.gameInfo.tileMap.mapParameters
        var minDistance = 100000 // a long distance
        var totalDistance = 0
        var connections = 0

        var proximity = Proximity.None

        for (ourCity in civInfo.cities) {
            for (theirCity in otherCiv.cities) {
                val distance = ourCity.getCenterTile().aerialDistanceTo(theirCity.getCenterTile())
                totalDistance += distance
                connections++
                if (minDistance > distance) minDistance = distance
            }
        }

        if (minDistance <= 7) {
            proximity = Proximity.Neighbors
        } else if (connections > 0) {
            val averageDistance = totalDistance / connections
            val mapFactor = if (mapParams.shape == MapShape.rectangular)
                (mapParams.mapSize.height + mapParams.mapSize.width) / 2
            else  (mapParams.mapSize.radius * 3) / 2 // slightly less area than equal size rect

            val closeDistance = ((mapFactor * 25) / 100).coerceIn(10, 20)
            val farDistance = ((mapFactor * 45) / 100).coerceIn(20, 50)

            proximity = if (minDistance <= 11 && averageDistance <= closeDistance)
                Proximity.Close
            else if (averageDistance <= farDistance)
                Proximity.Far
            else
                Proximity.Distant
        }

        // Check if different continents (unless already max distance, or water map)
        if (connections > 0 && proximity != Proximity.Distant && !civInfo.gameInfo.tileMap.isWaterMap()
                && civInfo.getCapital()!!.getCenterTile().getContinent() != otherCiv.getCapital()!!.getCenterTile().getContinent()
        ) {
            // Different continents - increase separation by one step
            proximity = when (proximity) {
                Proximity.Far -> Proximity.Distant
                Proximity.Close -> Proximity.Far
                Proximity.Neighbors -> Proximity.Close
                else -> proximity
            }
        }

        // If there aren't many players (left) we can't be that far
        val numMajors = civInfo.gameInfo.getAliveMajorCivs().size
        if (numMajors <= 2 && proximity > Proximity.Close)
            proximity = Proximity.Close
        if (numMajors <= 4 && proximity > Proximity.Far)
            proximity = Proximity.Far

        civInfo.proximity[otherCiv.civName] = proximity

        return proximity
    }

}
