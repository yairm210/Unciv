package com.unciv.logic

import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.BackwardCompatibility.removeMissingModReferences
import com.unciv.logic.BackwardCompatibility.replaceDiplomacyFlag
import com.unciv.logic.automation.NextTurnAutomation
import com.unciv.logic.civilization.*
import com.unciv.logic.civilization.diplomacy.DiplomacyFlags
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.Religion
import com.unciv.models.metadata.GameParameters
import com.unciv.models.metadata.GameSpeed
import com.unciv.models.ruleset.Difficulty
import com.unciv.models.ruleset.ModOptionsConstants
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.MusicTrackChooserFlags
import java.util.*


class UncivShowableException(missingMods: String) : Exception(missingMods)

class GameInfo {
    //region Fields - Serialized
    var civilizations = mutableListOf<CivilizationInfo>()
    var barbarians = BarbarianManager()
    var religions: HashMap<String, Religion> = hashMapOf()
    var difficulty = "Chieftain" // difficulty is game-wide, think what would happen if 2 human players could play on different difficulties?
    var tileMap: TileMap = TileMap()
    var gameParameters = GameParameters()
    var turns = 0
    var oneMoreTurnMode = false
    var currentPlayer = ""
    var currentTurnStartTime = 0L
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

    @Transient
    var spaceResources = HashSet<String>()

    //endregion
    //region Pure functions

    fun clone(): GameInfo {
        val toReturn = GameInfo()
        toReturn.tileMap = tileMap.clone()
        toReturn.civilizations.addAll(civilizations.map { it.clone() })
        toReturn.barbarians = barbarians.clone()
        toReturn.religions.putAll(religions.map { Pair(it.key, it.value.clone()) })
        toReturn.currentPlayer = currentPlayer
        toReturn.currentTurnStartTime = currentTurnStartTime
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
        else return getSpectator(userId)// you aren't anyone. How did you even get this game? Can you spectate?
    }

    /** Get a civ by name
     *  @throws NoSuchElementException if no civ of than name is in the game (alive or dead)! */
    fun getCivilization(civName: String) = civilizations.first { it.civName == civName }
    fun getCurrentPlayerCivilization() = currentPlayerCiv
    fun getCivilizationsAsPreviews() = civilizations.map { it.asPreview() }.toMutableList()
    /** Get barbarian civ
     *  @throws NoSuchElementException in no-barbarians games! */
    fun getBarbarianCivilization() = getCivilization(Constants.barbarians)
    fun getDifficulty() = difficultyObject
    fun getCities() = civilizations.asSequence().flatMap { it.cities }
    fun getAliveCityStates() = civilizations.filter { it.isAlive() && it.isCityState() }
    fun getAliveMajorCivs() = civilizations.filter { it.isAlive() && it.isMajorCiv() }

    /** Returns the first spectator for a [playerId] or creates one if none found */
    fun getSpectator(playerId: String) =
        civilizations.firstOrNull {
            it.isSpectator() && it.playerId == playerId
        } ?:
        CivilizationInfo(Constants.spectator).also { 
            it.playerType = PlayerType.Human
            it.playerId = playerId
            civilizations.add(it)
            it.gameInfo = this
            it.setNationTransient()
            it.setTransients()
        }

    fun isReligionEnabled(): Boolean {
        if (ruleSet.eras[gameParameters.startingEra]!!.hasUnique("Starting in this era disables religion")
            || ruleSet.modOptions.uniques.contains(ModOptionsConstants.disableReligion)
        ) return false
        return gameParameters.religionEnabled
    }

    private fun getEquivalentTurn(): Int {
        val totalTurns = 500f * gameParameters.gameSpeed.modifier
        val startPercent = ruleSet.eras[gameParameters.startingEra]!!.startPercent
        return turns + ((totalTurns * startPercent).toInt() / 100)
    }
    private class YearsToTurn(
        // enum class with lists for each value group potentially more efficient?
        val toTurn: Int,
        val yearInterval: Float
    ) {
        companion object {
            // Best to initialize these once only
            val marathon = listOf(YearsToTurn(100, 15f), YearsToTurn(400, 10f), YearsToTurn(570, 5f), YearsToTurn(771, 2f), YearsToTurn(900, 1f), YearsToTurn(1080, 0.5f), YearsToTurn(1344, 0.25f), YearsToTurn(1500, 0.083333f))
            val epic     = listOf(YearsToTurn(140, 25f), YearsToTurn(230, 15f), YearsToTurn(270, 10f), YearsToTurn(360, 5f), YearsToTurn(430, 2f), YearsToTurn(530, 1f), YearsToTurn(1500, 0.5f))
            val standard = listOf(YearsToTurn(75, 40f), YearsToTurn(135, 25f), YearsToTurn(160, 20f), YearsToTurn(210, 10f), YearsToTurn(270, 5f), YearsToTurn(320, 2f), YearsToTurn(440, 1f), YearsToTurn(500, 0.5f))
            val quick    = listOf(YearsToTurn(50, 60f), YearsToTurn(80, 40f), YearsToTurn(100, 30f), YearsToTurn(130, 20f), YearsToTurn(155, 10f), YearsToTurn(195, 5f), YearsToTurn(260, 2f), YearsToTurn(310, 1f))
            fun getList(gameSpeed: GameSpeed) = when (gameSpeed) {
                GameSpeed.Marathon -> marathon
                GameSpeed.Epic -> epic
                GameSpeed.Standard -> standard
                GameSpeed.Quick -> quick
            }
        }
    }
 
    fun getYear(turnOffset: Int = 0): Int {
        val turn = getEquivalentTurn() + turnOffset
        val yearToTurnList = YearsToTurn.getList(gameParameters.gameSpeed)
        var year: Float = -4000f
        var i = 0
        var yearsPerTurn: Float
 
        // if macros are ever added to kotlin, this is one hell of a place for em'
        while (i < turn) {
            yearsPerTurn = yearToTurnList.firstOrNull { i < it.toTurn }?.yearInterval ?: 0.5f
            year += yearsPerTurn
            ++i
        }

        return year.toInt()
    }

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
                if (thisPlayer.isBarbarian() && !gameParameters.noBarbarians)
                    barbarians.updateEncampments()

                // exit simulation mode when player wins
                if (thisPlayer.victoryManager.hasWon() && simulateUntilWin) {
                    // stop simulation
                    simulateUntilWin = false
                    break
                }
            }
            switchTurn()
        }

        currentTurnStartTime = System.currentTimeMillis()
        currentPlayer = thisPlayer.civName
        currentPlayerCiv = getCivilization(currentPlayer)
        if (currentPlayerCiv.isSpectator()) currentPlayerCiv.popupAlerts.clear() // no popups for spectators

        if (turns % 10 == 0) //todo measuring actual play time might be nicer
            UncivGame.Current.musicController.chooseTrack(currentPlayerCiv.civName,
                MusicMood.peaceOrWar(currentPlayerCiv.isAtWar()), MusicTrackChooserFlags.setNextTurn)

        // Start our turn immediately before the player can make decisions - affects
        // whether our units can commit automated actions and then be attacked immediately etc.
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

        addBombardNotification(thisPlayer,
                thisPlayer.cities.filter { city -> city.canBombard() &&
                enemyUnitsCloseToTerritory.any { tile -> tile.aerialDistanceTo(city.getCenterTile()) <= city.range }
                }
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

    private fun addBombardNotification(thisPlayer: CivilizationInfo, cities: List<CityInfo>) {
        if (cities.count() < 3) {
            for (city in cities)
                thisPlayer.addNotification("Your city [${city.name}] can bombard the enemy!", city.location, NotificationIcon.City, NotificationIcon.Crosshair)

        } else
            thisPlayer.addNotification("[${cities.count()}] of your cities can bombard the enemy!", LocationAction(cities.map { it.location }), NotificationIcon.City, NotificationIcon.Crosshair)
    }

    fun notifyExploredResources(civInfo: CivilizationInfo, resourceName: String, maxDistance: Int, showForeign: Boolean) {
        // Calling with `maxDistance = 0` removes distance limitation.
        data class CityTileAndDistance(val city: CityInfo, val tile: TileInfo, val distance: Int)

        var exploredRevealTiles:Sequence<TileInfo>

        if (ruleSet.tileResources[resourceName]!!.hasUnique(UniqueType.CityStateOnlyResource)) {
            // Look for matching mercantile CS centers 
            exploredRevealTiles = getAliveCityStates()
                .asSequence()
                .filter { it.cityStateResource == resourceName }
                .map { it.getCapital().getCenterTile() }
        } else {
            exploredRevealTiles = tileMap.values
                .asSequence()
                .filter { it.resource == resourceName }
        }

        val exploredRevealInfo = exploredRevealTiles
            .filter { it.position in civInfo.exploredTiles }
            .flatMap { tile -> civInfo.cities.asSequence()
                .map {
                    // build a full cross join all revealed tiles * civ's cities (should rarely surpass a few hundred)
                    // cache distance for each pair as sort will call it ~ 2n log n times
                    // should still be cheaper than looking up 'the' closest city per reveal tile before sorting
                    city -> CityTileAndDistance(city, tile, tile.aerialDistanceTo(city.getCenterTile()))
                }
            }
            .filter { (maxDistance == 0 || it.distance <= maxDistance) && (showForeign || it.tile.getOwner() == null || it.tile.getOwner() == civInfo) }
            .sortedWith ( compareBy { it.distance } )
            .distinctBy { it.tile }

        val chosenCity = exploredRevealInfo.firstOrNull()?.city ?: return
        val positions = exploredRevealInfo
            // re-sort to a more pleasant display order
            .sortedWith(compareBy{ it.tile.aerialDistanceTo(chosenCity.getCenterTile()) })
            .map { it.tile.position }
            .toList()       // explicit materialization of sequence to satisfy addNotification overload

        val text =  if(positions.size==1)
            "[$resourceName] revealed near [${chosenCity.name}]"
        else
            "[${positions.size}] sources of [$resourceName] revealed, e.g. near [${chosenCity.name}]"

        civInfo.addNotification(
            text,
            LocationAction(positions),
            "ResourceIcons/$resourceName"
        )
    }

    // All cross-game data which needs to be altered (e.g. when removing or changing a name of a building/tech)
    // will be done here, and not in CivInfo.setTransients or CityInfo
    fun setTransients() {
        tileMap.gameInfo = this

        // [TEMPORARY] Convert old saves to newer ones by moving base rulesets from the mod list to the base ruleset field
        val baseRulesetInMods = gameParameters.mods.firstOrNull { RulesetCache[it]?.modOptions?.isBaseRuleset==true }
        if (baseRulesetInMods != null) {
            gameParameters.baseRuleset = baseRulesetInMods
            gameParameters.mods = LinkedHashSet(gameParameters.mods.filter { it != baseRulesetInMods })
        }

        ruleSet = RulesetCache.getComplexRuleset(gameParameters.mods, gameParameters.baseRuleset)
        
        // any mod the saved game lists that is currently not installed causes null pointer
        // exceptions in this routine unless it contained no new objects or was very simple.
        // Player's fault, so better complain early:
        val missingMods = (gameParameters.mods + gameParameters.baseRuleset)
            .filterNot { it in ruleSet.mods }
            .joinToString(limit = 120) { it }
        if (missingMods.isNotEmpty()) {
            throw UncivShowableException("Missing mods: [$missingMods]")
        }

        removeMissingModReferences()


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
            for (cityInfo in civInfo.cities) cityInfo.cityStats.updateCityHappiness(
                cityInfo.cityConstructions.getStats()
            )

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

        spaceResources.addAll(ruleSet.buildings.values.filter { it.hasUnique("Spaceship part") }
            .flatMap { it.getResourceRequirements().keys } )
        
        barbarians.setTransients(this)
    }

    //endregion

    fun asPreview() = GameInfoPreview(this)
}

/**
 * Reduced variant of GameInfo used for load preview and multiplayer saves.
 * Contains additional data for multiplayer settings.
 */
class GameInfoPreview() {
    var civilizations = mutableListOf<CivilizationInfoPreview>()
    var difficulty = "Chieftain"
    var gameParameters = GameParameters()
    var turns = 0
    var gameId = ""
    var currentPlayer = ""
    var currentTurnStartTime = 0L
    var turnNotification = true //used as setting in the MultiplayerScreen

    /**
     * Converts a GameInfo object (can be uninitialized) into a GameInfoPreview object.
     * Sets all multiplayer settings to default.
     */
    constructor(gameInfo: GameInfo) : this() {
        civilizations = gameInfo.getCivilizationsAsPreviews()
        difficulty = gameInfo.difficulty
        gameParameters = gameInfo.gameParameters
        turns = gameInfo.turns
        gameId = gameInfo.gameId
        currentPlayer = gameInfo.currentPlayer
        currentTurnStartTime = gameInfo.currentTurnStartTime
    }

    fun getCivilization(civName: String) = civilizations.first { it.civName == civName }

    /**
     * Updates the current player and turn information in the GameInfoPreview object with the help of a
     * GameInfo object (can be uninitialized).
     */
    fun updateCurrentTurn(gameInfo: GameInfo) : GameInfoPreview {
        currentPlayer = gameInfo.currentPlayer
        turns = gameInfo.turns
        currentTurnStartTime = gameInfo.currentTurnStartTime
        //We update the civilizations in case someone is removed from the game (resign/kick)
        civilizations = gameInfo.getCivilizationsAsPreviews()

        return this
    }
}
