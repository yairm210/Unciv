package com.unciv.logic.civilization

import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.tile.ResourceSupplyList

/** CivInfo class was getting too crowded */
class CivInfoTransientUpdater(val civInfo: CivilizationInfo) {

    // This is a big performance
    fun updateViewableTiles() {
        setNewViewableTiles()

        val newViewableInvisibleTiles = HashSet<TileInfo>()
        newViewableInvisibleTiles.addAll(civInfo.getCivUnits()
            // "Can attack submarines" unique deprecated since 3.16.9
            .filter { attacker -> attacker.hasUnique("Can see invisible [] units") || attacker.hasUnique("Can attack submarines") }
            .flatMap { attacker ->
                attacker.viewableTiles
                    .asSequence()
                    .filter { tile -> 
                        ( tile.militaryUnit != null 
                            && attacker.getMatchingUniques("Can see invisible [] units")
                                .any { unique -> tile.militaryUnit!!.matchesFilter(unique.params[0]) }
                        ) || (
                            tile.militaryUnit != null
                            // "Can attack submarines" unique deprecated since 3.16.9
                            && attacker.hasUnique("Can attack submarines") 
                            && tile.militaryUnit!!.matchesFilter("Submarine")
                        )
                    } 
            }
        )
        civInfo.viewableInvisibleUnitsTiles = newViewableInvisibleTiles


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

    private fun setNewViewableTiles() {
        val newViewableTiles = HashSet<TileInfo>()

        // while spectating all map is visible
        if (civInfo.isSpectator()) {
            val allTiles = civInfo.gameInfo.tileMap.values.toSet()
            civInfo.viewableTiles = allTiles
            civInfo.viewableInvisibleUnitsTiles = allTiles
            return
        }

        // There are a LOT of tiles usually.
        // And making large lists of them just as intermediaries before we shove them into the hashset is very space-inefficient.
        // And so, sequences to the rescue!
        val ownedTiles = civInfo.cities.asSequence().flatMap { it.getTiles() }
        newViewableTiles.addAll(ownedTiles)
        val neighboringUnownedTiles = ownedTiles.flatMap { it.neighbors.filter { it.getOwner() != civInfo } }
        newViewableTiles.addAll(neighboringUnownedTiles)
        newViewableTiles.addAll(civInfo.getCivUnits().flatMap { it.viewableTiles.asSequence().filter { it.getOwner() != civInfo } })

        if (!civInfo.isCityState()) {
            for (otherCiv in civInfo.getKnownCivs()) {
                if (otherCiv.getAllyCiv() == civInfo.civName) {
                    newViewableTiles.addAll(otherCiv.cities.asSequence().flatMap { it.getTiles() })
                }
            }
        }

        civInfo.viewableTiles = newViewableTiles // to avoid concurrent modification problems
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
            civInfo.addNotification("We have discovered [" + tile.naturalWonder + "]!", tile.position, "StatIcons/Happiness")

            var goldGained = 0
            val discoveredNaturalWonders = civInfo.gameInfo.civilizations.filter { it != civInfo && it.isMajorCiv() }
                    .flatMap { it.naturalWonders }
            if (tile.hasUnique("Grants 500 Gold to the first civilization to discover it")
                    && !discoveredNaturalWonders.contains(tile.naturalWonder!!)) {
                goldGained += 500
            }

            if (civInfo.hasUnique("100 Gold for discovering a Natural Wonder (bonus enhanced to 500 Gold if first to discover it)")) {
                if (!discoveredNaturalWonders.contains(tile.naturalWonder!!))
                    goldGained += 500
                else goldGained += 100
            }

            if (goldGained > 0) {
                civInfo.addGold(goldGained)
                civInfo.addNotification("We have received [" + goldGained + "] Gold for discovering [" + tile.naturalWonder + "]", NotificationIcon.Gold)
            }

        }
    }

    fun updateHasActiveGreatWall() {
        civInfo.hasActiveGreatWall = !civInfo.tech.isResearched("Dynamite") &&
                civInfo.hasUnique("Enemy land units must spend 1 extra movement point when inside your territory (obsolete upon Dynamite)")
    }


    fun updateCitiesConnectedToCapital(initialSetup: Boolean = false) {
        if (civInfo.cities.isEmpty()) return // eg barbarians

        val citiesReachedToMediums = CapitalConnectionsFinder(civInfo).find()

        if (!initialSetup) { // In the initial setup we're loading an old game state, so it doesn't really count
            for (city in citiesReachedToMediums.keys)
                if (city !in civInfo.citiesConnectedToCapitalToMediums && city.civInfo == civInfo && city != civInfo.getCapital())
                    civInfo.addNotification("[${city.name}] has been connected to your capital!", city.location, NotificationIcon.Gold)

            // This may still contain cities that have just been destroyed by razing - thus the population test
            for (city in civInfo.citiesConnectedToCapitalToMediums.keys)
                if (!citiesReachedToMediums.containsKey(city) && city.civInfo == civInfo && city.population.population > 0)
                    civInfo.addNotification("[${city.name}] has been disconnected from your capital!", city.location, NotificationIcon.Gold)
        }

        civInfo.citiesConnectedToCapitalToMediums = citiesReachedToMediums
    }

    fun updateDetailedCivResources() {
        val newDetailedCivResources = ResourceSupplyList()
        for (city in civInfo.cities) newDetailedCivResources.add(city.getCityResources())

        if (!civInfo.isCityState()) {
            var resourceBonusPercentage = 1f
            for (unique in civInfo.getMatchingUniques("Quantity of Resources gifted by City-States increased by []%"))
                resourceBonusPercentage += unique.params[0].toFloat() / 100
            for (city in civInfo.getKnownCivs().filter { it.getAllyCiv() == civInfo.civName }
                .flatMap { it.cities }) {
                for (resourceSupply in city.getCityResources())
                    if (resourceSupply.origin != "Buildings") // IGNORE the fact that they consume their own resources - #4769
                        newDetailedCivResources.add(
                            resourceSupply.resource,
                            (resourceSupply.amount * resourceBonusPercentage).toInt(), "City-States"
                        )
            }
        }

        for (dip in civInfo.diplomacy.values) newDetailedCivResources.add(dip.resourcesFromTrade())
        for (unit in civInfo.getCivUnits())
            for ((resource, amount) in unit.baseUnit.getResourceRequirements())
                newDetailedCivResources.add(civInfo.gameInfo.ruleSet.tileResources[resource]!!, -amount, "Units")
        civInfo.detailedCivResources = newDetailedCivResources
    }
}