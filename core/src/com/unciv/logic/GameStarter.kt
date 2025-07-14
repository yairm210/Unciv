package com.unciv.logic

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.files.MapSaver
import com.unciv.logic.map.HexMath
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapgenerator.MapGenerator
import com.unciv.logic.map.tile.Tile
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.GameContext
import com.unciv.models.ruleset.unique.UniqueTriggerActivation
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.ruleset.unit.BaseUnit
import com.unciv.models.stats.Stats
import com.unciv.models.translations.equalsPlaceholderText
import com.unciv.models.translations.getPlaceholderParameters
import com.unciv.utils.debug

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

        var phaseOneChosenCivs: List<Player> = emptyList() // Never used, but the compiler needs it due to runAndMeasure capturing the var
        if (gameSetupInfo.mapParameters.name != "") runAndMeasure("loadMap") {
            tileMap = MapSaver.loadMap(gameSetupInfo.mapFile!!)
            // Don't override the map parameters - this can include if we world wrap or not!
            phaseOneChosenCivs = chooseCivilizations(gameSetupInfo.gameParameters, gameInfo, ruleset, existingMap = true)
        } else runAndMeasure("generateMap") {
            // The MapGen needs to know what civs are in the game to generate regions, starts and resources
            phaseOneChosenCivs = chooseCivilizations(gameSetupInfo.gameParameters, gameInfo, ruleset, existingMap = false)
            addCivilizations(gameSetupInfo.gameParameters, gameInfo, ruleset, phaseOneChosenCivs)
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
                phaseOneChosenCivs
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

        if (tileMap.continentSizes.isEmpty())   // Probably saved map without continent data
            runAndMeasure("assignContinents") {
                tileMap.assignContinents(TileMap.AssignContinentsMode.Ensure)
            }

        runAndMeasure("setTransients") {
            // mark as no migrateToTileHistory necessary
            gameInfo.historyStartTurn = 0
            tileMap.setTransients(ruleset) // if we're starting from a map with pre-placed units, they need the civs to exist first
            tileMap.setStartingLocationsTransients()

            gameInfo.difficulty = gameSetupInfo.gameParameters.difficulty

            gameInfo.setTransients() // needs to be before placeBarbarianUnit because it depends on the tilemap having its gameInfo set
        }

        runAndMeasure("addCivStartingUnits") {
            addCivStartingUnits(gameInfo)
        }

        runAndMeasure("Policies") {
            addCivPolicies(gameInfo, ruleset)
        }

        runAndMeasure("Techs and Stats") {
            addCivTechs(gameInfo, ruleset, gameSetupInfo)
        }

        runAndMeasure("Starting stats") {
            addCivStats(gameInfo)
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
        fun Civilization.addTechSilently(name: String) {
            // check if the technology is in the ruleset and not already researched
            if (!ruleset.technologies.containsKey(name)) return
            if (tech.isResearched(name)) return
            tech.addTechnology(name, false)
        }

        for (civInfo in gameInfo.civilizations) {
            if (civInfo.isBarbarian) continue

            for (tech in ruleset.technologies.values.filter { it.hasUnique(UniqueType.StartingTech) })
                civInfo.addTechSilently(tech.name)

            if (!civInfo.isHuman())
                for (tech in gameInfo.getDifficulty().aiFreeTechs)
                    civInfo.addTechSilently(tech)

            // generic start with technology unique
            for (unique in civInfo.getMatchingUniques(UniqueType.StartsWithTech))
                civInfo.addTechSilently(unique.params[0])

            // add all techs to spectators
            if (civInfo.isSpectator())
                for (tech in ruleset.technologies.values)
                    civInfo.addTechSilently(tech.name)

            // Add techs for advanced starting era
            val startingEraNumber = ruleset.eras[gameSetupInfo.gameParameters.startingEra]!!.eraNumber
            for (tech in ruleset.technologies.values) {
                if (ruleset.eras[tech.era()]!!.eraNumber >= startingEraNumber) continue
                if (civInfo.tech.isUnresearchable(tech)) continue
                civInfo.addTechSilently(tech.name)
            }

            // Since adding technologies generates popups (addTechnology parameter showNotification only suppresses Notifications)
            civInfo.popupAlerts.clear()
        }
    }

    private fun addCivPolicies(gameInfo: GameInfo, ruleset: Ruleset) {
        for (civInfo in gameInfo.civilizations.filter { !it.isBarbarian }) {

            // generic start with policy unique
            for (unique in civInfo.getMatchingUniques(UniqueType.StartsWithPolicy)) {
                // get the parameter from the unique
                val policyName = unique.params[0]

                // check if the policy is in the ruleset and not already adopted
                if (!ruleset.policies.containsKey(policyName) || civInfo.policies.isAdopted(policyName))
                    continue

                val policyToAdopt = ruleset.policies[policyName]!!
                civInfo.policies.run {
                    freePolicies++
                    adopt(policyToAdopt)
                }
            }
        }
    }

    private fun addCivStats(gameInfo: GameInfo) {
        val ruleSet = gameInfo.ruleset
        val startingEra = gameInfo.gameParameters.startingEra
        val era = ruleSet.eras[startingEra]!!
        for (civInfo in gameInfo.civilizations.filter { !it.isBarbarian && !it.isSpectator() }) {
            civInfo.addGold((era.startingGold * gameInfo.speed.goldCostModifier).toInt())
            civInfo.policies.addCulture((era.startingCulture * gameInfo.speed.cultureCostModifier).toInt())
        }
    }

    private fun chooseCivilizations(
        newGameParameters: GameParameters,
        gameInfo: GameInfo,
        ruleset: Ruleset,
        existingMap: Boolean
    ): List<Player> {
        val chosenPlayers = mutableListOf<Player>()  // Yes this preserves order
        val dequeCapacity = ruleset.nations.size

        val selectedPlayerNames = newGameParameters.players
            .map { it.chosenCiv }.toSet()
        val randomNationsPool = (
                if (gameSetupInfo.gameParameters.enableRandomNationsPool)
                    gameSetupInfo.gameParameters.randomNationsPool.asSequence()
                else
                    ruleset.nations.filter { it.value.isMajorCiv && !it.value.hasUnique(UniqueType.WillNotBeChosenForNewGames) }
                        .keys.asSequence()
                ).filter { it !in selectedPlayerNames }
            .shuffled().toCollection(ArrayDeque(dequeCapacity))

        val civNamesWithStartingLocations =
            if (existingMap) gameInfo.tileMap.startingLocationsByNation.keys
            else emptySet()
        val presetRandomNationsPool = randomNationsPool
            .filter { it in civNamesWithStartingLocations }
            .shuffled().toCollection(ArrayDeque(dequeCapacity))
        randomNationsPool.removeAll(presetRandomNationsPool)

        // At this point the civ names in newGameParameters.players, randomNationsPool and presetRandomNationsPool
        // are mutually exclusive. Random should **not** exist in the two random pools, but we have not explicitly guarded
        // here against the UI leaving one in gameParameters.randomNationsPool or map editor in tileMap.startingLocationsByNation.

        var extraRandomAIPlayers = 0
        var selectedAIToSkip = emptyList<Player>()
        if (newGameParameters.randomNumberOfPlayers) {
            // This swaps min and max if the user accidentally swapped min and max
            val min = newGameParameters.minNumberOfPlayers.coerceAtMost(newGameParameters.maxNumberOfPlayers)
            val max = newGameParameters.maxNumberOfPlayers.coerceAtLeast(newGameParameters.minNumberOfPlayers)
            val nonAICount = newGameParameters.players.count {
                it.playerType === PlayerType.Human || it.chosenCiv === Constants.spectator
            }
            val desiredNumberOfPlayers = (min.coerceAtLeast(nonAICount)..max.coerceAtLeast(nonAICount)).random()

            if (desiredNumberOfPlayers > newGameParameters.players.size) {
                extraRandomAIPlayers = desiredNumberOfPlayers - newGameParameters.players.size
            } else if (desiredNumberOfPlayers < newGameParameters.players.size) {
                val extraPlayers = newGameParameters.players.size - desiredNumberOfPlayers
                selectedAIToSkip = newGameParameters.players
                    .filter { it.playerType === PlayerType.AI }
                    .shuffled()
                    .sortedByDescending { it.chosenCiv == Constants.random }
                    .subList(0, extraPlayers)
            }
        }

        // Add player entries to the result
        (
            // Join two Sequences, one the explicitly chosen players...
            newGameParameters.players.asSequence()
            .filterNot { it in selectedAIToSkip }
            .sortedWith(compareBy<Player> { it.chosenCiv == Constants.random } // Nonrandom before random
                .thenBy { it.playerType == PlayerType.AI }) // Human before AI
            // ...another for the extra random ones
            + (0 until extraRandomAIPlayers).asSequence().map { Player() }
        ).mapNotNull {
            // Resolve random players
            when {
                it.chosenCiv != Constants.random -> it
                presetRandomNationsPool.isNotEmpty() -> Player(presetRandomNationsPool.removeLast(), it.playerType, it.playerId)
                randomNationsPool.isNotEmpty() -> Player(randomNationsPool.removeLast(), it.playerType, it.playerId)
                else -> null
            }
        }.toCollection(chosenPlayers)

        // ensure Spectators always first players
        val spectators = chosenPlayers.filter { it.chosenCiv == Constants.spectator }
        val otherPlayers = chosenPlayers.filterNot { it.chosenCiv == Constants.spectator }.toMutableList()
        
        // Shuffle Major Civs
        if (newGameParameters.shufflePlayerOrder) {
            otherPlayers.shuffle()
        }

        chosenPlayers.clear()
        chosenPlayers.addAll(spectators)
        chosenPlayers.addAll(otherPlayers)

        // Add CityStates to result - disguised as normal AI, but addCivilizations will detect them
        val numberOfCityStates = if (newGameParameters.randomNumberOfCityStates) {
            // This swaps min and max if the user accidentally swapped min and max
            val min = newGameParameters.minNumberOfCityStates.coerceAtMost(newGameParameters.maxNumberOfCityStates)
            val max = newGameParameters.maxNumberOfCityStates.coerceAtLeast(newGameParameters.minNumberOfCityStates)
            (min..max).random()
        } else {
            newGameParameters.numberOfCityStates
        }

        ruleset.nations.asSequence()
            .filter {
                it.value.isCityState &&
                        !it.value.hasUnique(UniqueType.WillNotBeChosenForNewGames)
            }.map { it.key }
            .shuffled()
            .sortedByDescending { it in civNamesWithStartingLocations }  // please those with location first
            .take(numberOfCityStates)
            .map { Player(it) }
            .toCollection(chosenPlayers)

        return chosenPlayers
    }

    private fun addCivilizations(
        newGameParameters: GameParameters,
        gameInfo: GameInfo,
        ruleset: Ruleset,
        chosenPlayers: List<Player>
    ) {
        if (!newGameParameters.noBarbarians && ruleset.nations.containsKey(Constants.barbarians)) {
            val barbarianCivilization = Civilization(Constants.barbarians)
            gameInfo.civilizations.add(barbarianCivilization)
        }

        val usedCivNames = chosenPlayers.map { it.chosenCiv }.toSet()
        val usedMajorCivs = ruleset.nations.asSequence()
            .filter { it.value.isMajorCiv }
            .map { it.key }
            .filter { it in usedCivNames }

        for (player in chosenPlayers) {
            val civ = Civilization(player.chosenCiv)
            when (player.chosenCiv) {
                in usedMajorCivs, Constants.spectator -> {
                    civ.playerType = player.playerType
                    civ.playerId = player.playerId
                }
                else ->
                    if (!civ.cityStateFunctions.initCityState(ruleset, newGameParameters.startingEra, usedMajorCivs))
                        continue
            }
            gameInfo.civilizations.add(civ)
        }
    }

    private fun addCivStartingUnits(gameInfo: GameInfo) {

        val ruleSet = gameInfo.ruleset
        val tileMap = gameInfo.tileMap

        val cityCenterMinStats = sequenceOf(ruleSet.tileImprovements[Constants.cityCenter])
            .filterNotNull()
            .flatMap { it.getMatchingUniques(UniqueType.EnsureMinimumStats, GameContext.IgnoreConditionals) }
            .firstOrNull()
            ?.stats ?: Stats.DefaultCityCenterMinimum

        val startScores = HashMap<Tile, Float>(tileMap.values.size)
        for (tile in tileMap.values) {
            startScores[tile] = tile.stats.getTileStartScore(cityCenterMinStats)
        }
        val allCivs = gameInfo.civilizations.filter { !it.isBarbarian }
        val landTilesInBigEnoughGroup = getCandidateLand(allCivs.size, tileMap, startScores)

        // First we get start locations for the major civs, on the second pass the city states (without predetermined starts) can squeeze in wherever
        val civNamesWithStartingLocations = tileMap.startingLocationsByNation.keys
        val bestCivs = allCivs.filter { (!it.isCityState || it.civName in civNamesWithStartingLocations)
            && !it.isSpectator()}
        val bestLocations = getStartingLocations(bestCivs, tileMap, landTilesInBigEnoughGroup, startScores)
        for ((civ, tile) in bestLocations) {
            // A nation can have multiple marked starting locations, of which the first pass may have chosen one
            tileMap.removeStartingLocations(civ.civName)
            // Mark the best start locations so we remember them for the second pass
            tileMap.addStartingLocation(civ.civName, tile)
        }

        val startingLocations = getStartingLocations(allCivs, tileMap, landTilesInBigEnoughGroup, startScores)

        // no starting units for Barbarians and Spectators
        determineStartingUnitsAndLocations(gameInfo, startingLocations, ruleSet)
    }

    private fun removeAncientRuinsNearStartingLocation(startingLocation: Tile) {
        for (tile in startingLocation.getTilesInDistance(3)) {
            if (tile.improvement != null
                && tile.getTileImprovement()!!.isAncientRuinsEquivalent()
            ) {
                tile.removeImprovement() // Remove ancient ruins in immediate vicinity
            }
        }
    }

    private fun determineStartingUnitsAndLocations(
        gameInfo: GameInfo,
        startingLocations: HashMap<Civilization, Tile>,
        ruleset: Ruleset
    ) {
        val startingEra = gameInfo.gameParameters.startingEra
        val settlerLikeUnits = ruleset.units.filter { it.value.isCityFounder() }

        for (civ in gameInfo.civilizations.filter { !it.isBarbarian && !it.isSpectator() }) {
            val startingLocation = startingLocations[civ]!!

            removeAncientRuinsNearStartingLocation(startingLocation)
            val startingUnits = getStartingUnitsForEraAndDifficulty(civ, gameInfo, ruleset, startingEra)
            adjustStartingUnitsForCityStatesAndOneCityChallenge(civ, gameInfo, startingUnits, settlerLikeUnits)
            placeStartingUnits(civ, startingLocation, startingUnits, ruleset, ruleset.eras[startingEra]!!.startingMilitaryUnit, settlerLikeUnits)

            // Trigger any global or nation uniques that should triggered.
            // We may need the starting location for some uniques, which is why we're doing it now
            // This relies on gameInfo.ruleset already being initialized
            val startingTriggers = (gameInfo.getGlobalUniques().uniqueObjects + civ.nation.uniqueObjects)
            for (unique in startingTriggers.filter { !it.hasTriggerConditional() && it.conditionalsApply(civ.state) })
                UniqueTriggerActivation.triggerUnique(unique, civ, tile = startingLocation)
        }
    }

    private fun getStartingUnitsForEraAndDifficulty(civ: Civilization, gameInfo: GameInfo, ruleset: Ruleset, startingEra: String): MutableList<String> {
        val startingUnits = ruleset.eras[startingEra]?.getStartingUnits(ruleset)
            ?: throw Exception("Era $startingEra does not exist in the ruleset!")

        // Add extra units granted by difficulty
        startingUnits.addAll(when {
            civ.isHuman() -> gameInfo.getDifficulty().playerBonusStartingUnits
            civ.isMajorCiv() -> gameInfo.getDifficulty().aiMajorCivBonusStartingUnits
            else -> gameInfo.getDifficulty().aiCityStateBonusStartingUnits
        })

        return startingUnits
    }

    private fun getEquivalentUnit(
        civ: Civilization,
        unitParam: String,
        ruleset: Ruleset,
        eraUnitReplacement: String,
        settlerLikeUnits: Map<String, BaseUnit>
    ): BaseUnit? {
        var unit = unitParam // We want to change it and this is the easiest way to do so
        if (unit == Constants.eraSpecificUnit) unit = eraUnitReplacement
        if (unit == Constants.settler && Constants.settler !in ruleset.units) {
            val buildableSettlerLikeUnits =
                settlerLikeUnits.filter {
                    it.value.isBuildable(civ)
                        && it.value.isCivilian()
                }
            if (buildableSettlerLikeUnits.isEmpty()) return null // No settlers in this mod
            return civ.getEquivalentUnit(buildableSettlerLikeUnits.keys.random())
        }
        if (unit == "Worker" && "Worker" !in ruleset.units) {
            val buildableWorkerLikeUnits = ruleset.units.filter {
                it.value.hasUnique(UniqueType.BuildImprovements) &&
                    it.value.isBuildable(civ) && it.value.isCivilian()
            }
            if (buildableWorkerLikeUnits.isEmpty()) return null // No workers in this mod
            return civ.getEquivalentUnit(buildableWorkerLikeUnits.keys.random())
        }
        return civ.getEquivalentUnit(unit)
    }

    private fun adjustStartingUnitsForCityStatesAndOneCityChallenge(
        civ: Civilization,
        gameInfo: GameInfo,
        startingUnits: MutableList<String>,
        settlerLikeUnits: Map<String, BaseUnit>
    ) {
        // Adjust starting units for city states
        if (civ.isCityState && !gameInfo.ruleset.modOptions.hasUnique(UniqueType.AllowCityStatesSpawnUnits)) {
            val startingSettlers = startingUnits.filter { settlerLikeUnits.contains(it) }

            startingUnits.clear()
            startingUnits.add(startingSettlers.random())
        }

        // Adjust starting units for one city challenge
        if (civ.playerType == PlayerType.Human && gameInfo.gameParameters.oneCityChallenge) {
            val startingSettlers = startingUnits.filter { settlerLikeUnits.contains(it) }

            startingUnits.removeAll(startingSettlers)
            startingUnits.add(startingSettlers.random())
        }
    }

    private fun placeStartingUnits(civ: Civilization, startingLocation: Tile, startingUnits: MutableList<String>, ruleset: Ruleset, eraUnitReplacement: String, settlerLikeUnits: Map<String, BaseUnit>) {
        for (unit in startingUnits) {
            val unitToAdd = getEquivalentUnit(civ, unit, ruleset, eraUnitReplacement, settlerLikeUnits)
            if (unitToAdd != null) civ.units.placeUnitNearTile(startingLocation.position, unitToAdd)
        }
    }

    private fun getCandidateLand(
        civCount: Int,
        tileMap: TileMap,
        startScores: HashMap<Tile, Float>
    ): Map<Tile, Float> {
        tileMap.assignContinents(TileMap.AssignContinentsMode.Ensure)

        // We want to  distribute starting locations fairly, and thus not place anybody on a small island
        // - unless necessary. Old code would only consider landmasses >= 20 tiles.
        // Instead, take continents until >=90% total area or everybody can get their own island
        val orderedContinents = tileMap.continentSizes.asSequence().sortedByDescending { it.value }.toList()
        val totalArea = tileMap.continentSizes.values.sum()
        var candidateArea = 0
        val candidateContinents = HashSet<Int>()
        for ((index, continentSize) in orderedContinents.withIndex()) {
            candidateArea += continentSize.value
            candidateContinents.add(continentSize.key)
            if (candidateArea >= totalArea * 0.9f) break
            if (index >= civCount) break
        }

        return startScores.filter { it.key.getContinent() in candidateContinents }
    }

    private fun getStartingLocations(
        civs: List<Civilization>,
        tileMap: TileMap,
        landTilesInBigEnoughGroup: Map<Tile, Float>,
        startScores: HashMap<Tile, Float>
    ): HashMap<Civilization, Tile> {

        val civsOrderedByAvailableLocations = getCivsOrderedByAvailableLocations(civs, tileMap)

        for (minimumDistanceBetweenStartingLocations in tileMap.tileMatrix.size / 6 downTo 0) {
            val freeTiles = getFreeTiles(tileMap, landTilesInBigEnoughGroup, minimumDistanceBetweenStartingLocations)

            val startingLocations = getStartingLocationsForCivs(civsOrderedByAvailableLocations, tileMap, freeTiles, startScores, minimumDistanceBetweenStartingLocations)
            if (startingLocations != null) return startingLocations
        }
        throw Exception("Didn't manage to get starting tiles even with distance of 1?")
    }

    private fun getCivsOrderedByAvailableLocations(civs: List<Civilization>, tileMap: TileMap): List<Civilization> {
        return civs.shuffled()   // Order should be random since it determines who gets best start
            .sortedBy { civ ->
                when {
                    civ.civName in tileMap.startingLocationsByNation -> 1 // harshest requirements
                    civ.nation.startBias.any { it in tileMap.naturalWonders } && !gameSetupInfo.gameParameters.noStartBias -> 2
                    civ.nation.startBias.contains(Constants.tundra) && !gameSetupInfo.gameParameters.noStartBias -> 3    // Tundra starts are hard to find, so let's do them first
                    civ.nation.startBias.isNotEmpty() && !gameSetupInfo.gameParameters.noStartBias -> 4 // less harsh
                    else -> 5  // no requirements
                }
            }.sortedByDescending { it.isHuman() } // More important for humans to get their start biases!
    }

    private fun getFreeTiles(tileMap: TileMap, landTilesInBigEnoughGroup: Map<Tile, Float>, minimumDistanceBetweenStartingLocations: Int): MutableList<Tile> {
        return landTilesInBigEnoughGroup.asSequence()
            .filter {
                HexMath.getDistanceFromEdge(it.key.position, tileMap.mapParameters) >=
                    (minimumDistanceBetweenStartingLocations * 2) / 3
            }.sortedBy { it.value }
            .map { it.key }
            .toMutableList()
    }

    private fun getStartingLocationsForCivs(
        civsOrderedByAvailableLocations: List<Civilization>,
        tileMap: TileMap,
        freeTiles: MutableList<Tile>,
        startScores: HashMap<Tile, Float>,
        minimumDistanceBetweenStartingLocations: Int
    ): HashMap<Civilization, Tile>? {
        val startingLocations = HashMap<Civilization, Tile>()
        for (civ in civsOrderedByAvailableLocations) {

            val startingLocation = getCivStartingLocation(civ, tileMap, freeTiles, startScores)
            startingLocation ?: break

            startingLocations[civ] = startingLocation

            val distanceToNext = minimumDistanceBetweenStartingLocations /
                (if (civ.isCityState) 2 else 1) // We allow city states to squeeze in tighter
            freeTiles.removeAll(tileMap.getTilesInDistance(startingLocation.position, distanceToNext)
                .toSet())
        }
        return if (startingLocations.size < civsOrderedByAvailableLocations.size) null else startingLocations
    }

    private fun getCivStartingLocation(
        civ: Civilization,
        tileMap: TileMap,
        freeTiles: MutableList<Tile>,
        startScores: HashMap<Tile, Float>,
    ): Tile? {
        var startingLocation = tileMap.startingLocationsByNation[civ.civName]?.randomOrNull()
        if (startingLocation == null) {
            startingLocation = tileMap.startingLocationsByNation[Constants.spectator]?.randomOrNull()
            if (startingLocation != null) {
                tileMap.startingLocationsByNation[Constants.spectator]?.remove(startingLocation)
            }
        }
        if (startingLocation == null && freeTiles.isNotEmpty())
            startingLocation = getOneStartingLocation(civ, tileMap, freeTiles, startScores)
        // If startingLocation is null we failed to get all the starting tiles with this minimum distance
        return startingLocation
    }

    private fun getOneStartingLocation(
        civ: Civilization,
        tileMap: TileMap,
        freeTiles: MutableList<Tile>,
        startScores: HashMap<Tile, Float>
    ): Tile {
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
                            it.matchesTerrainFilter(tileToAvoid, null)
                        }
                    }
                }
                startBias in tileMap.naturalWonders -> preferredTiles  // passthrough: already failed
                else -> preferredTiles.filter { tile ->
                    tile.getTilesInDistance(1).any {
                        it.matchesTerrainFilter(startBias, null)
                    }
                }
            }
        }
        return preferredTiles.randomOrNull() ?: freeTiles.random()
    }
}
