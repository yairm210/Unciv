package com.unciv.logic

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.utils.debug
import com.unciv.logic.civilization.*
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.ModOptionsConstants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

object GameStarter {
    // temporary instrumentation while tuning/debugging
    private const val consoleTimings = false
    private lateinit var gameSetupInfo: GameSetupInfo

    fun startNewGame(gameSetupInfo: GameSetupInfo): GameInfo {
        this.gameSetupInfo = gameSetupInfo
        if (consoleTimings)
            debug("\nGameStarter run with parameters %s, map %s", gameSetupInfo.gameParameters, gameSetupInfo.mapParameters)

        val gameInfo = GameInfo()
        lateinit var tileMap: TileMap

        // In the case where we used to have an extension mod, and now we don't, we cannot "unselect" it in the UI.
        // We need to remove the dead mods so there aren't problems later.
        gameSetupInfo.gameParameters.mods.removeAll { !RulesetCache.containsKey(it) }

        // [TEMPORARY] If we have a base ruleset in the mod list, we make that our base ruleset
        val baseRulesetInMods = gameSetupInfo.gameParameters.mods.firstOrNull { RulesetCache[it]!!.modOptions.isBaseRuleset }
        if (baseRulesetInMods != null)
            gameSetupInfo.gameParameters.baseRuleset = baseRulesetInMods

        if (!RulesetCache.containsKey(gameSetupInfo.gameParameters.baseRuleset))
            gameSetupInfo.gameParameters.baseRuleset = RulesetCache.getVanillaRuleset().name

        gameInfo.gameParameters = gameSetupInfo.gameParameters
        val ruleset = RulesetCache.getComplexRuleset(gameInfo.gameParameters)
        val mapGen = MapGenerator(ruleset)

        // Make sure that a valid game speed is loaded (catches a base ruleset not using the default game speed)
        if (!ruleset.speeds.containsKey(gameSetupInfo.gameParameters.speed)) {
            gameSetupInfo.gameParameters.speed = ruleset.speeds.keys.first()
        }

        if (gameSetupInfo.mapParameters.name != "") runAndMeasure("loadMap") {
            tileMap = MapSaver.loadMap(gameSetupInfo.mapFile!!)
            // Don't override the map parameters - this can include if we world wrap or not!
        } else runAndMeasure("generateMap") {
            // The mapgen needs to know what civs are in the game to generate regions, starts and resources
            addCivilizations(gameSetupInfo.gameParameters, gameInfo, ruleset, existingMap = false)
            tileMap = mapGen.generateMap(gameSetupInfo.mapParameters, gameSetupInfo.gameParameters, gameInfo.civilizations)
            tileMap.mapParameters = gameSetupInfo.mapParameters
            // Now forget them for a moment! MapGen can silently fail to place some city states, so then we'll use the old fallback method to place those.
            gameInfo.civilizations.clear()
        }

        runAndMeasure("addCivilizations") {
            gameInfo.tileMap = tileMap
            tileMap.gameInfo =
                gameInfo // need to set this transient before placing units in the map
            addCivilizations(
                gameSetupInfo.gameParameters,
                gameInfo,
                ruleset,
                existingMap = true
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

        runAndMeasure("Policies") {
            addCivPolicies(gameInfo, ruleset)
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
        debug("GameStarter.%s took %s.%sms", text, delta/1000000L, (delta/10000L).rem(100))
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
            for (unique in civInfo.getMatchingUniques(UniqueType.StartsWithTech)) {
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

    private fun addCivPolicies(gameInfo: GameInfo, ruleset: Ruleset) {
        for (civInfo in gameInfo.civilizations.filter { !it.isBarbarian() }) {

            // generic start with policy unique
            for (unique in civInfo.getMatchingUniques(UniqueType.StartsWithPolicy)) {
                // get the parameter from the unique
                val policyName = unique.params[0]

                // check if the policy is in the ruleset and not already adopted
                if (ruleset.policies.containsKey(policyName) && !civInfo.policies.isAdopted(policyName)) {
                    val policyToAdopt = ruleset.policies[policyName]!!
                    civInfo.policies.run {
                        freePolicies++
                        adopt(policyToAdopt)
                    }
                }
            }
        }
    }

    private fun addCivStats(gameInfo: GameInfo) {
        val ruleSet = gameInfo.ruleSet
        val startingEra = gameInfo.gameParameters.startingEra
        val era = ruleSet.eras[startingEra]!!
        for (civInfo in gameInfo.civilizations.filter { !it.isBarbarian() }) {
            civInfo.addGold((era.startingGold * gameInfo.speed.goldCostModifier).toInt())
            civInfo.policies.addCulture((era.startingCulture * gameInfo.speed.cultureCostModifier).toInt())
        }
    }

    private fun addCivilizations(newGameParameters: GameParameters, gameInfo: GameInfo, ruleset: Ruleset, existingMap: Boolean) {
        val availableCivNames = Stack<String>()
        // CityState or Spectator civs are not available for Random pick
        availableCivNames.addAll(ruleset.nations.filter { it.value.isMajorCiv() }.keys.shuffled())
        availableCivNames.removeAll(newGameParameters.players.map { it.chosenCiv }.toSet())
        availableCivNames.remove(Constants.barbarians)

        val startingTechs = ruleset.technologies.values.filter { it.hasUnique(UniqueType.StartingTech) }

        if (!newGameParameters.noBarbarians && ruleset.nations.containsKey(Constants.barbarians)) {
            val barbarianCivilization = CivilizationInfo(Constants.barbarians)
            gameInfo.civilizations.add(barbarianCivilization)
        }

        val civNamesWithStartingLocations = if(existingMap) gameInfo.tileMap.startingLocationsByNation.keys
            else emptySet()
        val presetMajors = Stack<String>()
        presetMajors.addAll(availableCivNames.filter { it in civNamesWithStartingLocations })

        for (player in newGameParameters.players.sortedBy { it.chosenCiv == Constants.random }) {
            val nationName = when {
                player.chosenCiv != Constants.random -> player.chosenCiv
                presetMajors.isNotEmpty() -> presetMajors.pop()
                else -> availableCivNames.pop()
            }
            availableCivNames.remove(nationName) // In case we got it from a map preset

            val playerCiv = CivilizationInfo(nationName)
            for (tech in startingTechs)
                playerCiv.tech.techsResearched.add(tech.name) // can't be .addTechnology because the civInfo isn't assigned yet
            playerCiv.playerType = player.playerType
            playerCiv.playerId = player.playerId
            gameInfo.civilizations.add(playerCiv)
        }

        val availableCityStatesNames = Stack<String>()
        // since we shuffle and then order by, we end up with all the City-States with starting tiles first in a random order,
        //   and then all the other City-States in a random order! Because the sortedBy function is stable!
        availableCityStatesNames.addAll( ruleset.nations
            .filter {
                it.value.isCityState() &&
                !it.value.hasUnique(UniqueType.CityStateDeprecated)
            }.keys
            .shuffled()
            .sortedBy { it in civNamesWithStartingLocations } ) // pop() gets the last item, so sort ascending

        var addedCityStates = 0
        // Keep trying to add city states until we reach the target number.
        while (addedCityStates < newGameParameters.numberOfCityStates) {
            if (availableCityStatesNames.isEmpty()) // We ran out of city-states somehow
                break

            val cityStateName = availableCityStatesNames.pop()
            val civ = CivilizationInfo(cityStateName)
            if (civ.cityStateFunctions.initCityState(ruleset, newGameParameters.startingEra, availableCivNames)) {
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
                if (unit == Constants.settler && Constants.settler !in ruleSet.units) {
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
                civ.nation.startBias.any { it in tileMap.naturalWonders } && !gameSetupInfo.gameParameters.noStartBias -> 2
                civ.nation.startBias.contains(Constants.tundra) && !gameSetupInfo.gameParameters.noStartBias -> 3    // Tundra starts are hard to find, so let's do them first
                civ.nation.startBias.isNotEmpty() && !gameSetupInfo.gameParameters.noStartBias -> 4 // less harsh
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
        if (gameSetupInfo.gameParameters.noStartBias) {
            return freeTiles.random()
        }
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
                startBias.equalsPlaceholderText("Avoid []") -> {
                    val tileToAvoid = startBias.getPlaceholderParameters()[0]
                    preferredTiles.filter { tile ->
                        !tile.getTilesInDistance(1).any {
                            it.matchesTerrainFilter(tileToAvoid)
                        }
                    }
                }
                startBias in tileMap.naturalWonders -> preferredTiles  // passthrough: already failed
                else -> preferredTiles.filter { tile ->
                    tile.getTilesInDistance(1).any {
                        it.matchesTerrainFilter(startBias)
                    }
                }
            }
        }
        return preferredTiles.randomOrNull() ?: freeTiles.random()
    }
}
