package com.unciv.logic.civilization.transients

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.Proximity
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.unique.UniqueTarget
import com.unciv.models.ruleset.unique.UniqueType

/** CivInfo class was getting too crowded */
class CivInfoTransientCache(val civInfo: CivilizationInfo) {

    fun updateSightAndResources() {
        updateViewableTiles()
        updateHasActiveEnemyMovementPenalty()
        updateCivResources()
    }

    // This is a big performance
    fun updateViewableTiles() {
        setNewViewableTiles()

        updateViewableInvisibleTiles()

        updateLastSeenImprovements()

        // updating the viewable tiles also affects the explored tiles, obviously.
        // So why don't we play switcharoo with the explored tiles as well?
        // Well, because it gets REALLY LARGE so it's a lot of memory space,
        // and we never actually iterate on the explored tiles (only check contains()),
        // so there's no fear of concurrency problems.
        val newlyExploredTiles = civInfo.viewableTiles.asSequence().map { it.position }
        civInfo.addExploredTiles(newlyExploredTiles)


        val viewedCivs = HashMap<CivilizationInfo, TileInfo>()
        for (tile in civInfo.viewableTiles) {
            val tileOwner = tile.getOwner()
            if (tileOwner != null) viewedCivs[tileOwner] = tile
            for (unit in tile.getUnits()) viewedCivs[unit.civInfo] = tile
        }

        if (!civInfo.isBarbarian()) {
            for (entry in viewedCivs) {
                val metCiv = entry.key
                if (metCiv == civInfo || metCiv.isBarbarian() || civInfo.diplomacy.containsKey(metCiv.civName)) continue
                civInfo.diplomacyFunctions.makeCivilizationsMeet(metCiv)
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
        val newViewableInvisibleTiles = HashSet<TileInfo>()
        for (unit in civInfo.units.getCivUnits()) {
            val invisibleUnitUniques = unit.getMatchingUniques(UniqueType.CanSeeInvisibleUnits)
            if (invisibleUnitUniques.none()) continue
            val visibleUnitTypes = invisibleUnitUniques.map { it.params[0] }
                .toList() // save this, it'll be seeing a lot of use
            for (tile in unit.viewableTiles) {
                if (tile.militaryUnit == null) continue
                if (visibleUnitTypes.any { tile.militaryUnit!!.matchesFilter(it) })
                    newViewableInvisibleTiles.add(tile)
            }
        }

        civInfo.viewableInvisibleUnitsTiles = newViewableInvisibleTiles
    }

    private fun setNewViewableTiles() {
        val newViewableTiles = HashSet<TileInfo>()

        // while spectating all map is visible
        if (civInfo.isSpectator() || UncivGame.Current.viewEntireMapForDebug) {
            val allTiles = civInfo.gameInfo.tileMap.values.toSet()
            civInfo.viewableTiles = allTiles
            civInfo.addExploredTiles(allTiles.asSequence().map { it.position })
            civInfo.viewableInvisibleUnitsTiles = allTiles
            return
        }

        // There are a LOT of tiles usually.
        // And making large lists of them just as intermediaries before we shove them into the hashset is very space-inefficient.
        // And so, sequences to the rescue!
        val ownedTiles = civInfo.cities.asSequence().flatMap { it.getTiles() }
        newViewableTiles.addAll(ownedTiles)
        val neighboringUnownedTiles = ownedTiles.flatMap { tile -> tile.neighbors.filter { it.getOwner() != civInfo } }
        newViewableTiles.addAll(neighboringUnownedTiles)
        newViewableTiles.addAll(civInfo.units.getCivUnits().flatMap { unit -> unit.viewableTiles.asSequence().filter { it.getOwner() != civInfo } })

        for (otherCiv in civInfo.getKnownCivs()) {
            if (otherCiv.getAllyCiv() == civInfo.civName || otherCiv.civName == civInfo.getAllyCiv()) {
                newViewableTiles.addAll(otherCiv.cities.asSequence().flatMap { it.getTiles() })
            }
        }

        for (spy in civInfo.espionageManager.spyList) {
            val spyCity = spy.getLocation() ?: continue
            if (!spy.isSetUp()) continue // Can't see cities when you haven't set up yet
            newViewableTiles.addAll(spyCity.getCenterTile().getTilesInDistance(1))
        }

        civInfo.viewableTiles = newViewableTiles // to avoid concurrent modification problems
    }

    private fun updateLastSeenImprovements() {
        if (civInfo.playerType == PlayerType.AI) return // don't bother for AI, they don't really use the info anyway

        for (tile in civInfo.viewableTiles) {
            if (tile.improvement == null)
                civInfo.lastSeenImprovement.remove(tile.position)
            else
                civInfo.lastSeenImprovement[tile.position] = tile.improvement!!
        }
    }

    private fun discoverNaturalWonders() {
        val newlyViewedNaturalWonders = HashSet<TileInfo>()
        for (tile in civInfo.viewableTiles) {
            if (tile.naturalWonder != null && !civInfo.naturalWonders.contains(tile.naturalWonder!!))
                newlyViewedNaturalWonders += tile
        }

        for (tile in newlyViewedNaturalWonders) {
            // GBR could be discovered twice otherwise!
            if (civInfo.naturalWonders.contains(tile.naturalWonder))
                continue
            civInfo.naturalWonders.add(tile.naturalWonder!!)
            civInfo.addNotification("We have discovered [${tile.naturalWonder}]!",
                tile.position, NotificationCategory.General, "StatIcons/Happiness")

            var goldGained = 0
            val discoveredNaturalWonders = civInfo.gameInfo.civilizations.filter { it != civInfo && it.isMajorCiv() }
                    .flatMap { it.naturalWonders }
            if (tile.terrainHasUnique(UniqueType.GrantsGoldToFirstToDiscover)
                    && !discoveredNaturalWonders.contains(tile.naturalWonder!!)) {
                goldGained += 500
            }

            if (civInfo.hasUnique(UniqueType.GoldWhenDiscoveringNaturalWonder)) {
                goldGained += if (discoveredNaturalWonders.contains(tile.naturalWonder!!)) 100 else 500
            }

            if (goldGained > 0) {
                civInfo.addGold(goldGained)
                civInfo.addNotification("We have received [$goldGained] Gold for discovering [${tile.naturalWonder}]",
                    NotificationCategory.General, NotificationIcon.Gold
                )
            }

        }
    }

    fun updateHasActiveEnemyMovementPenalty() {
        civInfo.hasActiveEnemyMovementPenalty = civInfo.hasUnique(UniqueType.EnemyLandUnitsSpendExtraMovement)
        civInfo.enemyMovementPenaltyUniques =
                civInfo.getMatchingUniques(UniqueType.EnemyLandUnitsSpendExtraMovement)
    }

    fun updateCitiesConnectedToCapital(initialSetup: Boolean = false) {
        if (civInfo.cities.isEmpty() || civInfo.getCapital() == null) return // eg barbarians

        val citiesReachedToMediums = CapitalConnectionsFinder(civInfo).find()

        if (!initialSetup) { // In the initial setup we're loading an old game state, so it doesn't really count
            for (city in citiesReachedToMediums.keys)
                if (city !in civInfo.citiesConnectedToCapitalToMediums && city.civInfo == civInfo && city != civInfo.getCapital()!!)
                    civInfo.addNotification("[${city.name}] has been connected to your capital!",
                        city.location, NotificationCategory.Cities, NotificationIcon.Gold
                    )

            // This may still contain cities that have just been destroyed by razing - thus the population test
            for (city in civInfo.citiesConnectedToCapitalToMediums.keys)
                if (!citiesReachedToMediums.containsKey(city) && city.civInfo == civInfo && city.population.population > 0)
                    civInfo.addNotification("[${city.name}] has been disconnected from your capital!",
                        city.location, NotificationCategory.Cities, NotificationIcon.Gold
                    )
        }

        civInfo.citiesConnectedToCapitalToMediums = citiesReachedToMediums
    }

    fun updateCivResources() {
        val newDetailedCivResources = ResourceSupplyList()
        for (city in civInfo.cities) newDetailedCivResources.add(city.getCityResources())

        if (!civInfo.isCityState()) {
            // First we get all these resources of each city state separately
            val cityStateProvidedResources = ResourceSupplyList()
            var resourceBonusPercentage = 1f
            for (unique in civInfo.getMatchingUniques(UniqueType.CityStateResources))
                resourceBonusPercentage += unique.params[0].toFloat() / 100
            for (cityStateAlly in civInfo.getKnownCivs().filter { it.getAllyCiv() == civInfo.civName }) {
                for (resourceSupply in cityStateAlly.cityStateFunctions.getCityStateResourcesForAlly()) {
                    val newAmount = (resourceSupply.amount * resourceBonusPercentage).toInt()
                    cityStateProvidedResources.add(resourceSupply.copy(amount = newAmount))
                }
            }
            // Then we combine these into one
            newDetailedCivResources.addByResource(cityStateProvidedResources, Constants.cityStates)
        }

        for (unique in civInfo.getMatchingUniques(UniqueType.ProvidesResources)) {
            if (unique.sourceObjectType == UniqueTarget.Building || unique.sourceObjectType == UniqueTarget.Wonder) continue // already calculated in city
            newDetailedCivResources.add(
                civInfo.gameInfo.ruleSet.tileResources[unique.params[1]]!!,
                unique.sourceObjectType?.name ?: "",
                unique.params[0].toInt()
            )
        }

        for (diplomacyManager in civInfo.diplomacy.values)
            newDetailedCivResources.add(diplomacyManager.resourcesFromTrade())

        for (unit in civInfo.units.getCivUnits())
            newDetailedCivResources.subtractResourceRequirements(
                unit.baseUnit.getResourceRequirements(), civInfo.gameInfo.ruleSet, "Units")

        // Check if anything has actually changed so we don't update stats for no reason - this uses List equality which means it checks the elements
        if (civInfo.detailedCivResources == newDetailedCivResources) return

        civInfo.detailedCivResources = newDetailedCivResources
        civInfo.summarizedCivResources = newDetailedCivResources.sumByResource("All")

        civInfo.updateStatsForNextTurn() // More or less resources = more or less happiness, with potential domino effects
    }


    fun updateProximity(otherCiv: CivilizationInfo, preCalculated: Proximity? = null): Proximity {
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
