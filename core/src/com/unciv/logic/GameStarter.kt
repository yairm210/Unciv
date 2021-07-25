package com.unciv.logic

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.civilization.*
import com.unciv.logic.map.BFS
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.Era
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.ui.newgamescreen.GameSetupInfo
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.max

object GameStarter {

    fun startNewGame(gameSetupInfo: GameSetupInfo): GameInfo {
        val gameInfo = GameInfo()

        gameInfo.gameParameters = gameSetupInfo.gameParameters
        val ruleset = RulesetCache.getComplexRuleset(gameInfo.gameParameters.mods)

        if (gameSetupInfo.mapParameters.name != "") {
            gameInfo.tileMap = MapSaver.loadMap(gameSetupInfo.mapFile!!)
            // Don't override the map parameters - this can include if we world wrap or not!
        } else {
            gameInfo.tileMap = MapGenerator(ruleset).generateMap(gameSetupInfo.mapParameters)
            gameInfo.tileMap.mapParameters = gameSetupInfo.mapParameters
        }


        gameInfo.tileMap.gameInfo = gameInfo // need to set this transient before placing units in the map
        addCivilizations(gameSetupInfo.gameParameters, gameInfo, ruleset) // this is before gameInfo.setTransients, so gameInfo doesn't yet have the gameBasics

        // Remove units for civs that aren't in this game
        for (tile in gameInfo.tileMap.values)
            for (unit in tile.getUnits())
                if (gameInfo.civilizations.none { it.civName == unit.owner }) {
                    unit.currentTile = tile
                    unit.setTransients(ruleset)
                    unit.removeFromTile()
                }

        gameInfo.tileMap.setTransients(ruleset) // if we're starting from a map with preplaced units, they need the civs to exist first

        gameInfo.difficulty = gameSetupInfo.gameParameters.difficulty


        gameInfo.setTransients() // needs to be before placeBarbarianUnit because it depends on the tilemap having its gameinfo set

        addCivTechs(gameInfo, ruleset, gameSetupInfo)
        
        addCivStats(gameInfo)

        // and only now do we add units for everyone, because otherwise both the gameInfo.setTransients() and the placeUnit will both add the unit to the civ's unit list!
        addCivStartingUnits(gameInfo)

        // remove starting locations once we're done
        for (tile in gameInfo.tileMap.values) {
            if (tile.improvement != null && tile.improvement!!.startsWith("StartingLocation "))
                tile.improvement = null
            // set max starting movement for units loaded from map
            for (unit in tile.getUnits()) unit.currentMovement = unit.getMaxMovement().toFloat()
        }
        
        // This triggers the one-time greeting from Nation.startIntroPart1/2
        addPlayerIntros(gameInfo)

        return gameInfo
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
                    .filter { ruleset.getEraNumber(it.era()) < ruleset.getEraNumber(gameSetupInfo.gameParameters.startingEra) })
                if (!civInfo.tech.isResearched(tech.name))
                    civInfo.tech.addTechnology(tech.name)

            civInfo.popupAlerts.clear() // Since adding technologies generates popups...
        }
    }

    private fun addCivStats(gameInfo: GameInfo) {
        val ruleSet = gameInfo.ruleSet
        val startingEra = gameInfo.gameParameters.startingEra
        val era =
        if (startingEra in ruleSet.eras.keys) {
            ruleSet.eras[startingEra]!!
        } else {
            Era()
        }
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

        if (!newGameParameters.noBarbarians && ruleset.nations.containsKey(Constants.barbarians)) {
            val barbarianCivilization = CivilizationInfo(Constants.barbarians)
            gameInfo.civilizations.add(barbarianCivilization)
        }

        for (player in newGameParameters.players.sortedBy { it.chosenCiv == "Random" }) {
            val nationName = if (player.chosenCiv != "Random") player.chosenCiv
            else availableCivNames.pop()

            val playerCiv = CivilizationInfo(nationName)
            for (tech in ruleset.technologies.values.filter { it.uniques.contains("Starting tech") })
                playerCiv.tech.techsResearched.add(tech.name) // can't be .addTechnology because the civInfo isn't assigned yet
            playerCiv.playerType = player.playerType
            playerCiv.playerId = player.playerId
            gameInfo.civilizations.add(playerCiv)
        }

        val cityStatesWithStartingLocations =
                gameInfo.tileMap.values
                        .filter { it.improvement != null && it.improvement!!.startsWith("StartingLocation ") }
                        .map { it.improvement!!.replace("StartingLocation ", "") }

        val availableCityStatesNames = Stack<String>()
        // since we shuffle and then order by, we end up with all the City-States with starting tiles first in a random order,
        //   and then all the other City-States in a random order! Because the sortedBy function is stable!
        availableCityStatesNames.addAll(ruleset.nations.filter { it.value.isCityState() }.keys
                .shuffled().sortedByDescending { it in cityStatesWithStartingLocations })

        for (cityStateName in availableCityStatesNames.take(newGameParameters.numberOfCityStates)) {
            val civ = CivilizationInfo(cityStateName)
            civ.cityStatePersonality = CityStatePersonality.values().random()
            gameInfo.civilizations.add(civ)
            for (tech in ruleset.technologies.values.filter { it.uniques.contains("Starting tech") })
                civ.tech.techsResearched.add(tech.name) // can't be .addTechnology because the civInfo isn't assigned yet
        }
    }

    private fun addCivStartingUnits(gameInfo: GameInfo) {

        val ruleSet = gameInfo.ruleSet
        val startingEra = gameInfo.gameParameters.startingEra
        var startingUnits: MutableList<String>
        var eraUnitReplacement: String

        val startScores = HashMap<TileInfo, Float>()
        for (tile in gameInfo.tileMap.values) {
            startScores[tile] = tile.getTileStartScore()
        }

        // First we get start locations for the major civs, on the second pass the city states (without predetermined starts) can squeeze in wherever
        // I hear copying code is good
        val cityStatesWithStartingLocations =
            gameInfo.tileMap.values
                .filter { it.improvement != null && it.improvement!!.startsWith("StartingLocation ") }
                .map { it.improvement!!.replace("StartingLocation ", "") }
        val bestCivs = gameInfo.civilizations.filter { !it.isBarbarian() && (!it.isCityState() || it.civName in cityStatesWithStartingLocations) }
        val bestLocations = getStartingLocations(bestCivs, gameInfo.tileMap, startScores)
        for (civ in bestCivs)
        {
            if (civ.isCityState())  // Already have explicit starting locations
                continue

            // Mark the best start locations so we remember them for the second pass
            bestLocations[civ]!!.improvement = "StartingLocation " + civ.civName
        }

        val startingLocations = getStartingLocations(
                gameInfo.civilizations.filter { !it.isBarbarian() },
                gameInfo.tileMap, startScores)

        val settlerLikeUnits = ruleSet.units.filter {
            it.value.uniqueObjects.any { it.placeholderText == Constants.settlerUnique }
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

            for (tile in startingLocation.getTilesInDistance(3))
                if (tile.improvement == Constants.ancientRuins)
                    tile.improvement = null // Remove ancient ruins in immediate vicinity

            fun placeNearStartingPosition(unitName: String) {
                civ.placeUnitNearTile(startingLocation.position, unitName)
            }
            
            // We are using an older mod, so we only look at the difficulty file
            if (ruleSet.eras.isEmpty()) { 
                startingUnits = (when {
                    civ.isPlayerCivilization() -> gameInfo.getDifficulty().startingUnits
                    civ.isMajorCiv() -> gameInfo.getDifficulty().aiMajorCivStartingUnits
                    else -> gameInfo.getDifficulty().aiCityStateStartingUnits
                }).toMutableList()

                val warriorEquivalent = ruleSet.units.values
                    .filter { it.unitType.isLandUnit() && it.isMilitary() && it.isBuildable(civ) }
                    .maxByOrNull {max(it.strength, it.rangedStrength)}
                    ?.name
                
                for (unit in startingUnits) {
                    val unitToAdd = if (unit == "Warrior") warriorEquivalent else unit 
                    if (unitToAdd != null) placeNearStartingPosition(unitToAdd)
                }
                
                continue
            }

            // Determine starting units based on starting era   
            if (startingEra in ruleSet.eras.keys) {
                startingUnits = ruleSet.eras[startingEra]!!.getStartingUnits().toMutableList()
                eraUnitReplacement = ruleSet.eras[startingEra]!!.startingMilitaryUnit
            } else {
                startingUnits = Era().getStartingUnits().toMutableList()
                eraUnitReplacement = Era().startingMilitaryUnit
            }
            
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
                        it.value.uniqueObjects.any { it.placeholderText == Constants.canBuildImprovements }
                                && it.value.isBuildable(civ)
                                && it.value.isCivilian()
                    }
                    if (buildableWorkerLikeUnits.isEmpty()) return null // No workers in this mod
                    return civ.getEquivalentUnit(buildableWorkerLikeUnits.keys.random()).name
                }
                return civ.getEquivalentUnit(unit).name
            }

            
            if (civ.isCityState()) {
                // City states should spawn with one settler only irregardless of era and difficulty
                val startingSettlers = startingUnits.filter { settlerLikeUnits.contains(it) }

                startingUnits.clear()
                startingUnits.add( startingSettlers.random() )
            }

            for (unit in startingUnits) {
                val unitToAdd = getEquivalentUnit(civ, unit)
                if (unitToAdd != null) placeNearStartingPosition(unitToAdd)
            }
        }
    }

    private fun getStartingLocations(civs: List<CivilizationInfo>, tileMap: TileMap, startScores: HashMap<TileInfo, Float>): HashMap<CivilizationInfo, TileInfo> {
        var landTiles = tileMap.values
                // Games starting on snow might as well start over...
                .filter { it.isLand && !it.isImpassible() && it.baseTerrain != Constants.snow }

        val landTilesInBigEnoughGroup = ArrayList<TileInfo>()
        while (landTiles.any()) {
            val bfs = BFS(landTiles.random()) { it.isLand && !it.isImpassible() }
            bfs.stepToEnd()
            val tilesInGroup = bfs.tilesReached.keys
            landTiles = landTiles.filter { it !in tilesInGroup }
            if (tilesInGroup.size > 20) // is this a good number? I dunno, but it's easy enough to change later on
                landTilesInBigEnoughGroup.addAll(tilesInGroup)
        }

        val tilesWithStartingLocations = tileMap.values
                .filter { it.improvement != null && it.improvement!!.startsWith("StartingLocation ") }


        val civsOrderedByAvailableLocations = civs.shuffled()   // Order should be random since it determines who gets best start
            .sortedBy { civ ->
            when {
                tilesWithStartingLocations.any { it.improvement == "StartingLocation " + civ.civName } -> 1 // harshest requirements
                civ.nation.startBias.contains("Tundra") -> 2    // Tundra starts are hard to find, so let's do them first
                civ.nation.startBias.isNotEmpty() -> 3 // less harsh
                else -> 4
            }  // no requirements
        }

        for (minimumDistanceBetweenStartingLocations in tileMap.tileMatrix.size / 4 downTo 0) {
            val freeTiles = landTilesInBigEnoughGroup
                    .filter { vectorIsAtLeastNTilesAwayFromEdge(it.position, (minimumDistanceBetweenStartingLocations * 2) /3, tileMap) }
                    .toMutableList()

            val startingLocations = HashMap<CivilizationInfo, TileInfo>()
            for (civ in civsOrderedByAvailableLocations) {
                var startingLocation: TileInfo
                val presetStartingLocation = tilesWithStartingLocations.firstOrNull { it.improvement == "StartingLocation " + civ.civName }
                var distanceToNext = minimumDistanceBetweenStartingLocations

                if (presetStartingLocation != null) startingLocation = presetStartingLocation
                else {
                    if (freeTiles.isEmpty()) break // we failed to get all the starting tiles with this minimum distance
                    if (civ.isCityState())
                        distanceToNext = minimumDistanceBetweenStartingLocations / 2 // We allow random city states to squeeze in tighter

                    freeTiles.sortBy { startScores[it] }

                    var preferredTiles = freeTiles.toList()

                    for (startBias in civ.nation.startBias) {
                        if (startBias.startsWith("Avoid ")) {
                            val tileToAvoid = startBias.removePrefix("Avoid [").removeSuffix("]")
                            preferredTiles = preferredTiles.filter { !it.matchesTerrainFilter(tileToAvoid) }
                        } else if (startBias == Constants.coast) preferredTiles = preferredTiles.filter { it.isCoastalTile() }
                        else preferredTiles = preferredTiles.filter { it.matchesTerrainFilter(startBias) }
                    }

                    startingLocation = if (preferredTiles.isNotEmpty()) preferredTiles.last() else freeTiles.last()
                }
                startingLocations[civ] = startingLocation
                freeTiles.removeAll(tileMap.getTilesInDistance(startingLocation.position, distanceToNext))
            }
            if (startingLocations.size < civs.size) continue // let's try again with less minimum distance!

            return startingLocations
        }
        throw Exception("Didn't manage to get starting tiles even with distance of 1?")
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
            if(tile.resource != null && tile.getTileResource().resourceType == ResourceType.Luxury)
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

    private fun vectorIsAtLeastNTilesAwayFromEdge(vector: Vector2, n: Int, tileMap: TileMap): Boolean {
        // Since all maps are HEXAGONAL, the easiest way of checking if a tile is n steps away from the
        // edge is checking the distance to the CENTER POINT
        // Can't believe we used a dumb way of calculating this before!
        val hexagonalRadius = -tileMap.leftX
        val distanceFromCenter = HexMath.getDistance(vector, Vector2.Zero)
        return hexagonalRadius - distanceFromCenter >= n
    }
}