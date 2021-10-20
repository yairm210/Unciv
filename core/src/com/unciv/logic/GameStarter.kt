package com.unciv.logic

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.*
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.ModOptionsConstants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.UniqueType
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

object GameStarter {
    // temporary instrumentation while tuning/debugging
    private const val consoleOutput = false
    private const val consoleTimings = false

    fun startNewGame(gameSetupInfo: GameSetupInfo): GameInfo {
        if (consoleOutput || consoleTimings)
            println("\nGameStarter run with parameters ${gameSetupInfo.gameParameters}, map ${gameSetupInfo.mapParameters}")

        val gameInfo = GameInfo()
        lateinit var tileMap: TileMap

        // In the case where we used to have a mod, and now we don't, we cannot "unselect" it in the UI.
        // We need to remove the dead mods so there aren't problems later.
        gameSetupInfo.gameParameters.mods.removeAll { !RulesetCache.containsKey(it) }

        gameInfo.gameParameters = gameSetupInfo.gameParameters
        val ruleset = RulesetCache.getComplexRuleset(gameInfo.gameParameters.mods)
        val mapGen = MapGenerator(ruleset)

        if (gameSetupInfo.mapParameters.name != "") runAndMeasure("loadMap") {
            tileMap = MapSaver.loadMap(gameSetupInfo.mapFile!!)
            // Don't override the map parameters - this can include if we world wrap or not!
        } else runAndMeasure("generateMap") {
            tileMap = mapGen.generateMap(gameSetupInfo.mapParameters)
            tileMap.mapParameters = gameSetupInfo.mapParameters
        }

        runAndMeasure("addCivilizations") {
            gameInfo.tileMap = tileMap
            tileMap.gameInfo =
                gameInfo // need to set this transient before placing units in the map
            addCivilizations(
                gameSetupInfo.gameParameters,
                gameInfo,
                ruleset
            ) // this is before gameInfo.setTransients, so gameInfo doesn't yet have the gameBasics
        }

        runAndMeasure("Remove units") {
            // Remove units for civs that aren't in this game
            for (tile in tileMap.values)
                for (unit in tile.getUnits())
                    if (gameInfo.civilizations.none { it.civName == unit.owner }) {
                        unit.currentTile = tile
                        unit.setTransients(ruleset)
                        unit.removeFromTile()
                    }
        }

        runAndMeasure("setTransients") {
            tileMap.setTransients(ruleset) // if we're starting from a map with pre-placed units, they need the civs to exist first
            tileMap.setStartingLocationsTransients()

            gameInfo.difficulty = gameSetupInfo.gameParameters.difficulty

            gameInfo.setTransients() // needs to be before placeBarbarianUnit because it depends on the tilemap having its gameInfo set
        }

        runAndMeasure("Techs and Stats") {
            addCivTechs(gameInfo, ruleset, gameSetupInfo)

            addCivStats(gameInfo)
        }

        if (tileMap.continentSizes.isEmpty())   // Probably saved map without continent data
            runAndMeasure("assignContinents") {
                tileMap.assignContinents(TileMap.AssignContinentsMode.Ensure)
            }

        runAndMeasure("addCivStartingUnits") {
            // and only now do we add units for everyone, because otherwise both the gameInfo.setTransients() and the placeUnit will both add the unit to the civ's unit list!
            addCivStartingUnits(gameInfo)
        }

        // remove starting locations once we're done
        tileMap.clearStartingLocations()

        // set max starting movement for units loaded from map
        for (tile in tileMap.values) {
            for (unit in tile.getUnits()) unit.currentMovement = unit.getMaxMovement().toFloat()
        }

        // This triggers the one-time greeting from Nation.startIntroPart1/2
        addPlayerIntros(gameInfo)

        UncivGame.Current.settings.apply {
            lastGameSetup = gameSetupInfo
            save()
        }
        return gameInfo
    }

    private fun runAndMeasure(text: String, action: ()->Unit) {
        if (!consoleTimings) return action()
        val startNanos = System.nanoTime()
        action()
        val delta = System.nanoTime() - startNanos
        println("GameStarter.$text took ${delta/1000000L}.${(delta/10000L).rem(100)}ms")
    }

    private fun addPlayerIntros(gameInfo: GameInfo) {
        gameInfo.civilizations.filter {
            // isNotEmpty should also exclude a spectator
            it.playerType == PlayerType.Human && it.nation.startIntroPart1.isNotEmpty()
        }.forEach {
            it.popupAlerts.add(PopupAlert(AlertType.StartIntro, ""))
        }
    }

    private fun addCivTechs(gameInfo: GameInfo, ruleset: Ruleset, gameSetupInfo: GameSetupInfo) {
        for (civInfo in gameInfo.civilizations.filter { !it.isBarbarian() }) {

            if (!civInfo.isPlayerCivilization())
                for (tech in gameInfo.getDifficulty().aiFreeTechs)
                    civInfo.tech.addTechnology(tech)

            // generic start with technology unique
            for (unique in civInfo.getMatchingUniques("Starts with []")) {
                // get the parameter from the unique
                val techName = unique.params[0]

                // check if the technology is in the ruleset and not already researched
                if (ruleset.technologies.containsKey(techName) && !civInfo.tech.isResearched(techName))
                    civInfo.tech.addTechnology(techName)
            }

            // add all techs to spectators
            if (civInfo.isSpectator())
                for (tech in ruleset.technologies.values)
                    if (!civInfo.tech.isResearched(tech.name))
                        civInfo.tech.addTechnology(tech.name)

            for (tech in ruleset.technologies.values
                    .filter { ruleset.eras[it.era()]!!.eraNumber < ruleset.eras[gameSetupInfo.gameParameters.startingEra]!!.eraNumber })
                if (!civInfo.tech.isResearched(tech.name))
                    civInfo.tech.addTechnology(tech.name)

            civInfo.popupAlerts.clear() // Since adding technologies generates popups...
        }
    }

    private fun addCivStats(gameInfo: GameInfo) {
        val ruleSet = gameInfo.ruleSet
        val startingEra = gameInfo.gameParameters.startingEra
        val era = ruleSet.eras[startingEra]!!
        for (civInfo in gameInfo.civilizations.filter { !it.isBarbarian() }) {
            civInfo.addGold((era.startingGold * gameInfo.gameParameters.gameSpeed.modifier).toInt())
            civInfo.policies.addCulture((era.startingCulture * gameInfo.gameParameters.gameSpeed.modifier).toInt())
        }
    }

    private fun addCivilizations(newGameParameters: GameParameters, gameInfo: GameInfo, ruleset: Ruleset) {
        val availableCivNames = Stack<String>()
        // CityState or Spectator civs are not available for Random pick
        availableCivNames.addAll(ruleset.nations.filter { it.value.isMajorCiv() }.keys.shuffled())
        availableCivNames.removeAll(newGameParameters.players.map { it.chosenCiv })
        availableCivNames.remove(Constants.barbarians)

        val startingTechs = ruleset.technologies.values.filter { it.uniques.contains("Starting tech") }

        if (!newGameParameters.noBarbarians && ruleset.nations.containsKey(Constants.barbarians)) {
            val barbarianCivilization = CivilizationInfo(Constants.barbarians)
            gameInfo.civilizations.add(barbarianCivilization)
        }

        for (player in newGameParameters.players.sortedBy { it.chosenCiv == "Random" }) {
            val nationName = if (player.chosenCiv != "Random") player.chosenCiv
            else availableCivNames.pop()

            val playerCiv = CivilizationInfo(nationName)
            for (tech in startingTechs)
                playerCiv.tech.techsResearched.add(tech.name) // can't be .addTechnology because the civInfo isn't assigned yet
            playerCiv.playerType = player.playerType
            playerCiv.playerId = player.playerId
            gameInfo.civilizations.add(playerCiv)
        }

        val civNamesWithStartingLocations = gameInfo.tileMap.startingLocationsByNation.keys

        val availableCityStatesNames = Stack<String>()
        // since we shuffle and then order by, we end up with all the City-States with starting tiles first in a random order,
        //   and then all the other City-States in a random order! Because the sortedBy function is stable!
        availableCityStatesNames.addAll( ruleset.nations
            .filter {
                it.value.isCityState() &&
                (it.value.cityStateType != CityStateType.Religious || newGameParameters.religionEnabled) &&
                !it.value.hasUnique(UniqueType.CityStateDeprecated)
            }.keys
            .shuffled()
            .sortedByDescending { it in civNamesWithStartingLocations } )


        val allMercantileResources = ruleset.tileResources.values.filter {
            it.unique == "Can only be created by Mercantile City-States" // Deprecated as of 3.16.16
                || it.hasUnique(UniqueType.CityStateOnlyResource) }.map { it.name }


        val unusedMercantileResources = Stack<String>()
        unusedMercantileResources.addAll(allMercantileResources.shuffled())

        var addedCityStates = 0
        // Keep trying to add city states until we reach the target number.
        while (addedCityStates < newGameParameters.numberOfCityStates) {
            if (availableCityStatesNames.isEmpty()) // We ran out of city-states somehow
                break

            val cityStateName = availableCityStatesNames.pop()
            val civ = CivilizationInfo(cityStateName)
            if (civ.initCityState(ruleset, newGameParameters.startingEra, availableCivNames)) {
                gameInfo.civilizations.add(civ)
                addedCityStates++
            }
        }
    }

    private fun addCivStartingUnits(gameInfo: GameInfo) {

        val ruleSet = gameInfo.ruleSet
        val tileMap = gameInfo.tileMap
        val startingEra = gameInfo.gameParameters.startingEra
        var startingUnits: MutableList<String>
        var eraUnitReplacement: String

        val startScores = HashMap<TileInfo, Float>(tileMap.values.size)
        for (tile in tileMap.values) {
            startScores[tile] = tile.getTileStartScore()
        }
        val allCivs = gameInfo.civilizations.filter { !it.isBarbarian() }
        val landTilesInBigEnoughGroup = getCandidateLand(allCivs.size, tileMap, startScores)

        // First we get start locations for the major civs, on the second pass the city states (without predetermined starts) can squeeze in wherever
        val civNamesWithStartingLocations = tileMap.startingLocationsByNation.keys
        val bestCivs = allCivs.filter { !it.isCityState() || it.civName in civNamesWithStartingLocations }
        val bestLocations = getStartingLocations(bestCivs, tileMap, landTilesInBigEnoughGroup, startScores)
        for ((civ, tile) in bestLocations) {
            // A nation can have multiple marked starting locations, of which the first pass may have chosen one
            tileMap.removeStartingLocations(civ.civName)
            // Mark the best start locations so we remember them for the second pass
            tileMap.addStartingLocation(civ.civName, tile)
        }

        val startingLocations = getStartingLocations(allCivs, tileMap, landTilesInBigEnoughGroup, startScores)

        val settlerLikeUnits = ruleSet.units.filter {
            it.value.hasUnique(UniqueType.FoundCity)
        }

        // no starting units for Barbarians and Spectators
        for (civ in gameInfo.civilizations.filter { !it.isBarbarian() && !it.isSpectator() }) {
            val startingLocation = startingLocations[civ]!!

            if(civ.isMajorCiv() && startScores[startingLocation]!! < 45) {
                // An unusually bad spawning location
                addConsolationPrize(gameInfo, startingLocation, 45 - startingLocation.getTileStartScore().toInt())
            }

            if(civ.isCityState())
                addCityStateLuxury(gameInfo, startingLocation)

            for (tile in startingLocation.getTilesInDistance(3)) {
                if (tile.improvement != null
                    && tile.getTileImprovement()!!.isAncientRuinsEquivalent()
                ) {
                    tile.improvement = null // Remove ancient ruins in immediate vicinity
                }
            }

            fun placeNearStartingPosition(unitName: String) {
                civ.placeUnitNearTile(startingLocation.position, unitName)
            }

            // Determine starting units based on starting era   
            startingUnits = ruleSet.eras[startingEra]!!.getStartingUnits().toMutableList()
            eraUnitReplacement = ruleSet.eras[startingEra]!!.startingMilitaryUnit

            // Add extra units granted by difficulty
            startingUnits.addAll(when {
                civ.isPlayerCivilization() -> gameInfo.getDifficulty().playerBonusStartingUnits
                civ.isMajorCiv() -> gameInfo.getDifficulty().aiMajorCivBonusStartingUnits
                else -> gameInfo.getDifficulty().aiCityStateBonusStartingUnits
            })


            fun getEquivalentUnit(civ: CivilizationInfo, unitParam: String): String? {
                var unit = unitParam // We want to change it and this is the easiest way to do so
                if (unit == Constants.eraSpecificUnit) unit = eraUnitReplacement
                if (unit == "Settler" && "Settler" !in ruleSet.units) {
                    val buildableSettlerLikeUnits = 
                        settlerLikeUnits.filter {
                            it.value.isBuildable(civ)
                            && it.value.isCivilian()
                        }
                    if (buildableSettlerLikeUnits.isEmpty()) return null // No settlers in this mod
                    return civ.getEquivalentUnit(buildableSettlerLikeUnits.keys.random()).name
                }
                if (unit == "Worker" && "Worker" !in ruleSet.units) {
                    val buildableWorkerLikeUnits = ruleSet.units.filter {
                        it.value.hasUnique(UniqueType.BuildImprovements) &&
                            it.value.isBuildable(civ) && it.value.isCivilian()
                    }
                    if (buildableWorkerLikeUnits.isEmpty()) return null // No workers in this mod
                    return civ.getEquivalentUnit(buildableWorkerLikeUnits.keys.random()).name
                }
                return civ.getEquivalentUnit(unit).name
            }

            // City states should only spawn with one settler regardless of difficulty, but this may be disabled in mods 
            if (civ.isCityState() && !ruleSet.modOptions.uniques.contains(ModOptionsConstants.allowCityStatesSpawnUnits)) {
                val startingSettlers = startingUnits.filter { settlerLikeUnits.contains(it) }

                startingUnits.clear()
                startingUnits.add(startingSettlers.random())
            }

            // One city challengers should spawn with one settler only regardless of era and difficulty
            if (civ.playerType == PlayerType.Human && gameInfo.gameParameters.oneCityChallenge) {
                val startingSettlers = startingUnits.filter { settlerLikeUnits.contains(it) }

                startingUnits.removeAll(startingSettlers)
                startingUnits.add(startingSettlers.random())
            }

            for (unit in startingUnits) {
                val unitToAdd = getEquivalentUnit(civ, unit)
                if (unitToAdd != null) placeNearStartingPosition(unitToAdd)
            }
        }
    }

    private fun getCandidateLand(
        civCount: Int,
        tileMap: TileMap,
        startScores: HashMap<TileInfo, Float>
    ): Map<TileInfo, Float> {
        tileMap.assignContinents(TileMap.AssignContinentsMode.Ensure)

        // We want to  distribute starting locations fairly, and thus not place anybody on a small island
        // - unless necessary. Old code would only consider landmasses >= 20 tiles.
        // Instead, take continents until >=75% total area or everybody can get their own island
        val orderedContinents = tileMap.continentSizes.asSequence().sortedByDescending { it.value }.toList()
        val totalArea = tileMap.continentSizes.values.sum()
        var candidateArea = 0
        val candidateContinents = HashSet<Int>()
        for ((index, continentSize) in orderedContinents.withIndex()) {
            candidateArea += continentSize.value
            candidateContinents.add(continentSize.key)
            if (candidateArea * 4 >= totalArea * 3) break
            if (index >= civCount) break
        }

        return startScores.filter { it.key.getContinent() in candidateContinents }
    }

    private fun getStartingLocations(
        civs: List<CivilizationInfo>,
        tileMap: TileMap,
        landTilesInBigEnoughGroup: Map<TileInfo, Float>,
        startScores: HashMap<TileInfo, Float>
    ): HashMap<CivilizationInfo, TileInfo> {

        val civsOrderedByAvailableLocations = civs.shuffled()   // Order should be random since it determines who gets best start
            .sortedBy { civ ->
            when {
                civ.civName in tileMap.startingLocationsByNation -> 1 // harshest requirements
                civ.nation.startBias.any { it in tileMap.naturalWonders } -> 2
                civ.nation.startBias.contains("Tundra") -> 3    // Tundra starts are hard to find, so let's do them first
                civ.nation.startBias.isNotEmpty() -> 4 // less harsh
                else -> 5  // no requirements
            }
        }

        for (minimumDistanceBetweenStartingLocations in tileMap.tileMatrix.size / 6 downTo 0) {
            val freeTiles = landTilesInBigEnoughGroup.asSequence()
                .filter {
                    HexMath.getDistanceFromEdge(it.key.position, tileMap.mapParameters) >=
                            (minimumDistanceBetweenStartingLocations * 2) / 3
                }.sortedBy { it.value }
                .map { it.key }
                .toMutableList()

            val startingLocations = HashMap<CivilizationInfo, TileInfo>()
            for (civ in civsOrderedByAvailableLocations) {
                val distanceToNext = minimumDistanceBetweenStartingLocations /
                        (if (civ.isCityState()) 2 else 1) // We allow city states to squeeze in tighter
                val presetStartingLocation = tileMap.startingLocationsByNation[civ.civName]?.randomOrNull()
                val startingLocation = if (presetStartingLocation != null) presetStartingLocation
                else {
                    if (freeTiles.isEmpty()) break // we failed to get all the starting tiles with this minimum distance
                    getOneStartingLocation(civ, tileMap, freeTiles, startScores)
                }
                startingLocations[civ] = startingLocation
                freeTiles.removeAll(tileMap.getTilesInDistance(startingLocation.position, distanceToNext))
            }
            if (startingLocations.size < civs.size) continue // let's try again with less minimum distance!

            return startingLocations
        }
        throw Exception("Didn't manage to get starting tiles even with distance of 1?")
    }

    private fun getOneStartingLocation(
        civ: CivilizationInfo,
        tileMap: TileMap,
        freeTiles: MutableList<TileInfo>,
        startScores: HashMap<TileInfo, Float>
    ): TileInfo {
        if (civ.nation.startBias.any { it in tileMap.naturalWonders }) {
            // startPref wants Natural wonder neighbor: Rare and very likely to be outside getDistanceFromEdge
            val wonderNeighbor = tileMap.values.asSequence()
                .filter { it.isNaturalWonder() && it.naturalWonder!! in civ.nation.startBias }
                .sortedByDescending { startScores[it] }
                .firstOrNull()
            if (wonderNeighbor != null) return wonderNeighbor
        }

        var preferredTiles = freeTiles.toList()
        for (startBias in civ.nation.startBias) {
            preferredTiles = when {
                startBias.startsWith("Avoid [") -> {
                    val tileToAvoid = startBias.removePrefix("Avoid [").removeSuffix("]")
                    preferredTiles.filter { !it.matchesTerrainFilter(tileToAvoid) }
                }
                startBias == Constants.coast -> preferredTiles.filter { it.isCoastalTile() }
                startBias in tileMap.naturalWonders -> preferredTiles  // passthrough: already failed
                else -> preferredTiles.filter { it.matchesTerrainFilter(startBias) }
            }
        }
        return preferredTiles.lastOrNull() ?: freeTiles.last()
    }

    private fun addConsolationPrize(gameInfo: GameInfo, spawn: TileInfo, points: Int) {
        val relevantTiles = spawn.getTilesInDistanceRange(1..2).shuffled()
        var addedPoints = 0
        var addedBonuses = 0

        for (tile in relevantTiles) {
            if (addedPoints >= points || addedBonuses >= 4) // At some point enough is enough
                break
            if (tile.resource != null || tile.baseTerrain == Constants.snow)    // Snow is quite irredeemable
                continue

            val bonusToAdd = gameInfo.ruleSet.tileResources.values
                .filter { it.terrainsCanBeFoundOn.contains(tile.getLastTerrain().name) && it.resourceType == ResourceType.Bonus }
                .randomOrNull()

            if (bonusToAdd != null) {
                tile.resource = bonusToAdd.name
                addedPoints += (bonusToAdd.food + bonusToAdd.production + bonusToAdd.gold + 1).toInt()  // +1 because resources can be improved
                addedBonuses++
            }
        }
    }

    private fun addCityStateLuxury(gameInfo: GameInfo, spawn: TileInfo) {
        // Every city state should have at least one luxury to trade
        val relevantTiles = spawn.getTilesInDistance(2).shuffled()

        for (tile in relevantTiles) {
            if(tile.resource != null && tile.tileResource.resourceType == ResourceType.Luxury)
                return  // At least one luxury; all set
        }

        for (tile in relevantTiles) {
            // Add a luxury to the first eligible tile
            if (tile.resource != null)
                continue

            val luxuryToAdd = gameInfo.ruleSet.tileResources.values
                .filter { it.terrainsCanBeFoundOn.contains(tile.getLastTerrain().name) && it.resourceType == ResourceType.Luxury }
                .randomOrNull()
            if (luxuryToAdd != null) {
                tile.resource = luxuryToAdd.name
                return
            }
        }
    }
}
