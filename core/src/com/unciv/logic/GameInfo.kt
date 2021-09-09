package com.unciv.logic

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.BackwardCompatibility.removeMissingModReferences
import com.unciv.logic.BackwardCompatibility.replaceDiplomacyFlag
import com.unciv.logic.automation.NextTurnAutomation
import com.unciv.logic.civilization.*
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.Religion
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.Difficulty
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import java.util.*

class UncivShowableException(missingMods: String) : Exception(missingMods)

class GameInfo {
    //region Fields - Serialized
    var civilizations = mutableListOf<CivilizationInfo>()
    var religions: HashMap<String, Religion> = hashMapOf()
    var difficulty = "Chieftain" // difficulty is game-wide, think what would happen if 2 human players could play on different difficulties?
    var tileMap: TileMap = TileMap()
    var gameParameters = GameParameters()
    var turns = 0
    var oneMoreTurnMode = false
    var currentPlayer = ""
    var gameId = UUID.randomUUID().toString() // random string

    // Maps a civ to the civ they voted for
    var diplomaticVictoryVotesCast = HashMap<String, String>()

    /**Keep track of a custom location this game was saved to _or_ loaded from
     *
     * Note this was used as silent autosave destination, but it was decided (#3898) to
     * make the custom location feature a one-shot import/export kind of operation.
     * The tracking is left in place, however [GameSaver.autoSaveSingleThreaded] no longer uses it
     */
    @Volatile
    var customSaveLocation: String? = null

    //endregion
    //region Fields - Transient

    @Transient
    lateinit var difficultyObject: Difficulty // Since this is static game-wide, and was taking a large part of nextTurn

    @Transient
    lateinit var currentPlayerCiv: CivilizationInfo // this is called thousands of times, no reason to search for it with a find{} every time

    /** This is used in multiplayer games, where I may have a saved game state on my phone
     * that is inconsistent with the saved game on the cloud */
    @Transient
    var isUpToDate = false

    @Transient
    lateinit var ruleSet: Ruleset

    /** Simulate until any player wins,
     *  or turns exceeds indicated number
     *  Does not update World View until finished.
     *  Should be set manually on each new game start.
     */
    @Transient
    var simulateMaxTurns: Int = 1000
    @Transient
    var simulateUntilWin = false

    //endregion
    //region Pure functions

    fun clone(): GameInfo {
        val toReturn = GameInfo()
        toReturn.tileMap = tileMap.clone()
        toReturn.civilizations.addAll(civilizations.map { it.clone() })
        toReturn.religions.putAll(religions.map { Pair(it.key, it.value.clone()) })
        toReturn.currentPlayer = currentPlayer
        toReturn.turns = turns
        toReturn.difficulty = difficulty
        toReturn.gameParameters = gameParameters
        toReturn.gameId = gameId
        toReturn.diplomaticVictoryVotesCast.putAll(diplomaticVictoryVotesCast)
        toReturn.oneMoreTurnMode = oneMoreTurnMode
        toReturn.customSaveLocation = customSaveLocation
        return toReturn
    }

    fun getPlayerToViewAs(): CivilizationInfo {
        if (!gameParameters.isOnlineMultiplayer) return currentPlayerCiv // non-online, play as human player
        val userId = UncivGame.Current.settings.userId

        // Iterating on all civs, starting from the the current player, gives us the one that will have the next turn
        // This allows multiple civs from the same UserID
        if (civilizations.any { it.playerId == userId }) {
            var civIndex = civilizations.map { it.civName }.indexOf(currentPlayer)
            while (true) {
                val civToCheck = civilizations[civIndex % civilizations.size]
                if (civToCheck.playerId == userId) return civToCheck
                civIndex++
            }
        }
        else return getBarbarianCivilization()// you aren't anyone. How did you even get this game? Can you spectate?
    }

    fun getCivilization(civName: String) = civilizations.first { it.civName == civName }
    fun getCurrentPlayerCivilization() = currentPlayerCiv
    fun getBarbarianCivilization() = getCivilization(Constants.barbarians)
    fun getDifficulty() = difficultyObject
    fun getCities() = civilizations.asSequence().flatMap { it.cities }
    fun getAliveCityStates() = civilizations.filter { it.isAlive() && it.isCityState() }
    fun getAliveMajorCivs() = civilizations.filter { it.isAlive() && it.isMajorCiv() }

    fun hasReligionEnabled() =
        // Temporary function to check whether religion should be used for this game
        (gameParameters.religionEnabled || ruleSet.hasReligion())
                && (ruleSet.eras.isEmpty() || !ruleSet.eras[gameParameters.startingEra]!!.hasUnique("Starting in this era disables religion"))

    //endregion
    //region State changing functions

    fun nextTurn() {
        val previousHumanPlayer = getCurrentPlayerCivilization()
        var thisPlayer = previousHumanPlayer // not calling it currentPlayer because that's already taken and I can't think of a better name
        var currentPlayerIndex = civilizations.indexOf(thisPlayer)


        fun switchTurn() {
            thisPlayer.endTurn()
            currentPlayerIndex = (currentPlayerIndex + 1) % civilizations.size
            if (currentPlayerIndex == 0) {
                turns++
            }
            thisPlayer = civilizations[currentPlayerIndex]
            thisPlayer.startTurn()
        }

        //check is important or else switchTurn
        //would skip a turn if an AI civ calls nextTurn
        //this happens when resigning a multiplayer game
        if (thisPlayer.isPlayerCivilization()){
            switchTurn()
        }

        while (thisPlayer.playerType == PlayerType.AI
                || turns < UncivGame.Current.simulateUntilTurnForDebug
                || turns < simulateMaxTurns && simulateUntilWin
                // For multiplayer, if there are 3+ players and one is defeated or spectator,
                // we'll want to skip over their turn
                || gameParameters.isOnlineMultiplayer && (thisPlayer.isDefeated() || thisPlayer.isSpectator())
        ) {
            if (!thisPlayer.isDefeated() || thisPlayer.isBarbarian()) {
                NextTurnAutomation.automateCivMoves(thisPlayer)

                // Placing barbarians after their turn
                if (thisPlayer.isBarbarian()
                        && !gameParameters.noBarbarians
                        && turns % 10 == 0) placeBarbarians()

                // exit simulation mode when player wins
                if (thisPlayer.victoryManager.hasWon() && simulateUntilWin) {
                    // stop simulation
                    simulateUntilWin = false
                    break
                }
            }
            switchTurn()
        }

        currentPlayer = thisPlayer.civName
        currentPlayerCiv = getCivilization(currentPlayer)
        if (currentPlayerCiv.isSpectator()) currentPlayerCiv.popupAlerts.clear() // no popups for spectators


        // Start our turn immediately before the player can made decisions - affects whether our units can commit automated actions and then be attacked immediately etc.
        notifyOfCloseEnemyUnits(thisPlayer)
    }

    private fun notifyOfCloseEnemyUnits(thisPlayer: CivilizationInfo) {
        val viewableInvisibleTiles = thisPlayer.viewableInvisibleUnitsTiles.map { it.position }
        val enemyUnitsCloseToTerritory = thisPlayer.viewableTiles
                .filter {
                    it.militaryUnit != null && it.militaryUnit!!.civInfo != thisPlayer
                            && thisPlayer.isAtWarWith(it.militaryUnit!!.civInfo)
                            && (it.getOwner() == thisPlayer || it.neighbors.any { neighbor -> neighbor.getOwner() == thisPlayer }
                            && (!it.militaryUnit!!.isInvisible(thisPlayer) || viewableInvisibleTiles.contains(it.position)))
                }

        // enemy units ON our territory
        addEnemyUnitNotification(thisPlayer,
                enemyUnitsCloseToTerritory.filter { it.getOwner() == thisPlayer },
                "in"
        )
        // enemy units NEAR our territory
        addEnemyUnitNotification(thisPlayer,
                enemyUnitsCloseToTerritory.filter { it.getOwner() != thisPlayer },
                "near"
        )
    }

    private fun addEnemyUnitNotification(thisPlayer: CivilizationInfo, tiles: List<TileInfo>, inOrNear: String) {
        // don't flood the player with similar messages. instead cycle through units by clicking the message multiple times.
        if (tiles.size < 3) {
            for (tile in tiles) {
                val unitName = tile.militaryUnit!!.name
                thisPlayer.addNotification("An enemy [$unitName] was spotted $inOrNear our territory", tile.position, NotificationIcon.War, unitName)
            }
        } else {
            val positions = tiles.map { it.position }
            thisPlayer.addNotification("[${positions.size}] enemy units were spotted $inOrNear our territory", LocationAction(positions), NotificationIcon.War)
        }
    }


    fun placeBarbarians() {
        val encampments = tileMap.values.filter { it.improvement == Constants.barbarianEncampment }

        if (encampments.size < civilizations.filter { it.isMajorCiv() }.size) {
            val newEncampmentTile = placeBarbarianEncampment(encampments)
            if (newEncampmentTile != null)
                placeBarbarianUnit(newEncampmentTile)
        }

        val totalBarbariansAllowedOnMap = encampments.size * 3
        var extraBarbarians = totalBarbariansAllowedOnMap - getBarbarianCivilization().getCivUnits().count()

        for (tile in tileMap.values.filter { it.improvement == Constants.barbarianEncampment }) {
            if (extraBarbarians <= 0) break
            extraBarbarians--
            placeBarbarianUnit(tile)
        }
    }

    fun placeBarbarianEncampment(existingEncampments: List<TileInfo>): TileInfo? {
        // Barbarians will only spawn in places that no one can see
        val allViewableTiles = civilizations.filterNot { it.isBarbarian() || it.isSpectator() }
                .flatMap { it.viewableTiles }.toHashSet()
        val tilesWithin3ofExistingEncampment = existingEncampments.asSequence()
                .flatMap { it.getTilesInDistance(3) }.toSet()
        val viableTiles = tileMap.values.filter {
            it.isLand && it.terrainFeatures.isEmpty()
                    && !it.isImpassible()
                    && it !in tilesWithin3ofExistingEncampment
                    && it !in allViewableTiles
        }
        if (viableTiles.isEmpty()) return null // no place for more barbs =(
        val tile = viableTiles.random()
        tile.improvement = Constants.barbarianEncampment
        notifyCivsOfBarbarianEncampment(tile)
        return tile
    }

    private fun placeBarbarianUnit(tileToPlace: TileInfo) {
        // if we don't make this into a separate list then the retain() will happen on the Tech keys,
        // which effectively removes those techs from the game and causes all sorts of problems
        val allResearchedTechs = ruleSet.technologies.keys.toMutableList()
        for (civ in civilizations.filter { !it.isBarbarian() && !it.isDefeated() }) {
            allResearchedTechs.retainAll(civ.tech.techsResearched)
        }
        val barbarianCiv = getBarbarianCivilization()
        barbarianCiv.tech.techsResearched = allResearchedTechs.toHashSet()
        val unitList = ruleSet.units.values
                .filter { it.isMilitary() }
                .filter { it.isBuildable(barbarianCiv) }

        val landUnits = unitList.filter { it.isLandUnit() }
        val waterUnits = unitList.filter { it.isWaterUnit() }

        val unit: String = if (waterUnits.isNotEmpty() && tileToPlace.isCoastalTile() && Random().nextBoolean())
            waterUnits.random().name
        else landUnits.random().name

        tileMap.placeUnitNearTile(tileToPlace.position, unit, getBarbarianCivilization())
    }

    /**
     * [CivilizationInfo.addNotification][Add a notification] to every civilization that have
     * adopted Honor policy and have explored the [tile] where the Barbarian Encampment has spawned.
     */
    private fun notifyCivsOfBarbarianEncampment(tile: TileInfo) {
        for (civ in civilizations
            .filter {
                it.hasApplyingUnique("Notified of new Barbarian encampments")
                && it.exploredTiles.contains(tile.position)
            }
        ) {
            civ.addNotification("A new barbarian encampment has spawned!", tile.position, NotificationIcon.War)
        }
    }

    // All cross-game data which needs to be altered (e.g. when removing or changing a name of a building/tech)
    // will be done here, and not in CivInfo.setTransients or CityInfo
    fun setTransients() {
        tileMap.gameInfo = this
        ruleSet = RulesetCache.getComplexRuleset(gameParameters.mods)
        // any mod the saved game lists that is currently not installed causes null pointer
        // exceptions in this routine unless it contained no new objects or was very simple.
        // Player's fault, so better complain early:
        val missingMods = gameParameters.mods
                .filterNot { it in ruleSet.mods }
                .joinToString(limit = 120) { it }
        if (missingMods.isNotEmpty()) {
            throw UncivShowableException("Missing mods: [$missingMods]")
        }

        removeMissingModReferences()

        replaceDiplomacyFlag(DiplomacyFlags.Denunceation, DiplomacyFlags.Denunciation)

        for (baseUnit in ruleSet.units.values)
            baseUnit.ruleset = ruleSet

        // This needs to go before tileMap.setTransients, as units need to access 
        // the nation of their civilization when setting transients
        for (civInfo in civilizations) civInfo.gameInfo = this
        for (civInfo in civilizations) civInfo.setNationTransient()

        tileMap.setTransients(ruleSet)

        if (currentPlayer == "") currentPlayer = civilizations.first { it.isPlayerCivilization() }.civName
        currentPlayerCiv = getCivilization(currentPlayer)

        difficultyObject = ruleSet.difficulties[difficulty]!!

        for (religion in religions.values) religion.setTransients(this)

        for (civInfo in civilizations) civInfo.setTransients()
        for (civInfo in civilizations) civInfo.updateSightAndResources()

        for (civInfo in civilizations) {
            for (unit in civInfo.getCivUnits())
                unit.updateVisibleTiles() // this needs to be done after all the units are assigned to their civs and all other transients are set

            // Since this depends on the cities of ALL civilizations,
            // we need to wait until we've set the transients of all the cities before we can run this.
            // Hence why it's not in CivInfo.setTransients().
            civInfo.initialSetCitiesConnectedToCapitalTransients()

            // We need to determine the GLOBAL happiness state in order to determine the city stats
            for (cityInfo in civInfo.cities) cityInfo.cityStats.updateCityHappiness()

            for (cityInfo in civInfo.cities) {
                /** We remove constructions from the queue that aren't defined in the ruleset.
                 * This can lead to situations where the city is puppeted and had its construction removed, and there's no way to user-set it
                 * So for cities like those, we'll auto-set the construction
                 */
                if (cityInfo.isPuppet && cityInfo.cityConstructions.constructionQueue.isEmpty())
                    cityInfo.cityConstructions.chooseNextConstruction()

                cityInfo.cityStats.update()
            }

            if (civInfo.hasEverOwnedOriginalCapital == null) {
                civInfo.hasEverOwnedOriginalCapital = civInfo.cities.any { it.isOriginalCapital }
            }
        }
    }

    //endregion
}

// reduced variant only for load preview
class GameInfoPreview {
    var civilizations = mutableListOf<CivilizationInfoPreview>()
    var difficulty = "Chieftain"
    var gameParameters = GameParameters()
    var turns = 0
    var gameId = ""
    var currentPlayer = ""
    fun getCivilization(civName: String) = civilizations.first { it.civName == civName }
}
