package com.unciv.logic.civilization

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.tile.ResourceSupplyList
import com.unciv.models.ruleset.unique.UniqueType

/** CivInfo class was getting too crowded */
class CivInfoTransientUpdater(val civInfo: CivilizationInfo) {

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
                .filterNot { civInfo.exploredTiles.contains(it) }
        civInfo.exploredTiles.addAll(newlyExploredTiles)


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
                civInfo.makeCivilizationsMeet(metCiv)
                civInfo.addNotification("We have encountered [" + metCiv.civName + "]!", entry.value.position, metCiv.civName, NotificationIcon.Diplomacy)
                metCiv.addNotification("We have encountered [" + civInfo.civName + "]!", entry.value.position, civInfo.civName, NotificationIcon.Diplomacy)
            }

            discoverNaturalWonders()
        }
    }

    private fun updateViewableInvisibleTiles() {
        val newViewableInvisibleTiles = HashSet<TileInfo>()
        for (unit in civInfo.getCivUnits()) {
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
            civInfo.exploredTiles = allTiles.map { it.position }.toHashSet()
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
        newViewableTiles.addAll(civInfo.getCivUnits().flatMap { unit -> unit.viewableTiles.asSequence().filter { it.getOwner() != civInfo } })

        for (otherCiv in civInfo.getKnownCivs()) {
            if (otherCiv.getAllyCiv() == civInfo.civName || otherCiv.civName ==civInfo.getAllyCiv()) {
                newViewableTiles.addAll(otherCiv.cities.asSequence().flatMap { it.getTiles() })
            }
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
            civInfo.discoverNaturalWonder(tile.naturalWonder!!)
            civInfo.addNotification("We have discovered [${tile.naturalWonder}]!", tile.position, "StatIcons/Happiness")

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
                civInfo.addNotification("We have received [$goldGained] Gold for discovering [${tile.naturalWonder}]", NotificationIcon.Gold)
            }

        }
    }

    fun updateHasActiveGreatWall() {
        civInfo.hasActiveGreatWall = !civInfo.tech.isResearched("Dynamite") &&
                civInfo.hasUnique(UniqueType.EnemyLandUnitsSpendExtraMovement)
    }

    fun updateCitiesConnectedToCapital(initialSetup: Boolean = false) {
        if (civInfo.cities.isEmpty() || civInfo.getCapital() == null) return // eg barbarians

        val citiesReachedToMediums = CapitalConnectionsFinder(civInfo).find()

        if (!initialSetup) { // In the initial setup we're loading an old game state, so it doesn't really count
            for (city in citiesReachedToMediums.keys)
                if (city !in civInfo.citiesConnectedToCapitalToMediums && city.civInfo == civInfo && city != civInfo.getCapital()!!)
                    civInfo.addNotification("[${city.name}] has been connected to your capital!", city.location, NotificationIcon.Gold)

            // This may still contain cities that have just been destroyed by razing - thus the population test
            for (city in civInfo.citiesConnectedToCapitalToMediums.keys)
                if (!citiesReachedToMediums.containsKey(city) && city.civInfo == civInfo && city.population.population > 0)
                    civInfo.addNotification("[${city.name}] has been disconnected from your capital!", city.location, NotificationIcon.Gold)
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

        for (diplomacyManager in civInfo.diplomacy.values)
            newDetailedCivResources.add(diplomacyManager.resourcesFromTrade())

        for (unit in civInfo.getCivUnits())
            newDetailedCivResources.subtractResourceRequirements(
                unit.baseUnit.getResourceRequirements(), civInfo.gameInfo.ruleSet, "Units")

        // Check if anything has actually changed so we don't update stats for no reason - this uses List equality which means it checks the elements
        if (civInfo.detailedCivResources == newDetailedCivResources) return

        civInfo.detailedCivResources = newDetailedCivResources
        civInfo.summarizedCivResources = newDetailedCivResources.sumByResource("All")

        civInfo.updateStatsForNextTurn() // More or less resources = more or less happiness, with potential domino effects
    }
}
