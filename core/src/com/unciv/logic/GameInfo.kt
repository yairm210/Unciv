package com.unciv.logic

import com.unciv.Constants
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.UncivGame.Version
import com.unciv.json.json
import com.unciv.logic.BackwardCompatibility.convertFortify
import com.unciv.logic.BackwardCompatibility.guaranteeUnitPromotions
import com.unciv.logic.BackwardCompatibility.migrateGreatGeneralPools
import com.unciv.logic.BackwardCompatibility.migrateToTileHistory
import com.unciv.logic.BackwardCompatibility.removeMissingModReferences
import com.unciv.logic.GameInfo.Companion.CURRENT_COMPATIBILITY_NUMBER
import com.unciv.logic.GameInfo.Companion.FIRST_WITHOUT
import com.unciv.logic.automation.civilization.BarbarianManager
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.CivilizationInfoPreview
import com.unciv.logic.civilization.LocationAction
import com.unciv.logic.civilization.Notification
import com.unciv.logic.civilization.NotificationCategory
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.managers.TechManager
import com.unciv.logic.civilization.managers.TurnManager
import com.unciv.logic.civilization.managers.VictoryManager
import com.unciv.logic.github.Github.repoNameToFolderName
import com.unciv.logic.map.CityDistanceData
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Religion
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.ruleset.Speed
import com.unciv.models.ruleset.nation.Difficulty
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.audio.MusicMood
import com.unciv.ui.audio.MusicTrackChooserFlags
import com.unciv.ui.screens.savescreens.Gzip
import com.unciv.ui.screens.worldscreen.status.NextTurnProgress
import com.unciv.utils.DebugUtils
import com.unciv.utils.debug
import java.security.MessageDigest
import java.util.UUID


/**
 * A class that implements this interface is part of [GameInfo] serialization, i.e. save files.
 *
 * Take care with `lateinit` and `by lazy` fields - both are **never** serialized.
 *
 * When you change the structure of any class with this interface in a way which makes it impossible
 * to load the new saves from an older game version, increment [CURRENT_COMPATIBILITY_NUMBER]! And don't forget
 * to add backwards compatibility for the previous format.
 *
 * Reminder: In all subclasse, do use only actual Collection types, not abstractions like
 * `= mutableSetOf<Something>()`. That would make the reflection type of the field an interface, which
 * hides the actual implementation from Gdx Json, so it will not try to call a no-args constructor but
 * will instead deserialize a List in the jsonData.isArray() -> isAssignableFrom(Collection) branch of readValue:
 * https://github.com/libgdx/libgdx/blob/75612dae1eeddc9611ed62366858ff1d0ac7898b/gdx/src/com/badlogic/gdx/utils/Json.java#L1111
 * .. which will crash later (when readFields actually assigns it) unless empty.
 */
interface IsPartOfGameInfoSerialization

interface HasGameInfoSerializationVersion {
    val version: CompatibilityVersion
}

data class CompatibilityVersion(
    /** Contains the current serialization version of [GameInfo], i.e. when this number is not equal to [CURRENT_COMPATIBILITY_NUMBER], it means
     * this instance has been loaded from a save file json that was made with another version of the game. */
    val number: Int,
    val createdWith: Version
) : IsPartOfGameInfoSerialization {
    @Suppress("unused") // used by json serialization
    constructor() : this(-1, Version())

    operator fun compareTo(other: CompatibilityVersion) = number.compareTo(other.number)

}

data class VictoryData(val winningCiv: String, val victoryType: String, val victoryTurn: Int) {
    @Suppress("unused")  // used by json serialization
    constructor(): this("","",0)
}

class GameInfo : IsPartOfGameInfoSerialization, HasGameInfoSerializationVersion {
    companion object {
        /** The current compatibility version of [GameInfo]. This number is incremented whenever changes are made to the save file structure that guarantee that
         * previous versions of the game will not be able to load or play a game normally. */
        const val CURRENT_COMPATIBILITY_NUMBER = 3

        val CURRENT_COMPATIBILITY_VERSION = CompatibilityVersion(CURRENT_COMPATIBILITY_NUMBER, UncivGame.VERSION)

        /** This is the version just before this field was introduced, i.e. all saves without any version will be from this version */
        val FIRST_WITHOUT = CompatibilityVersion(1, Version("4.1.14", 731))
    }
    //region Fields - Serialized

    override var version = FIRST_WITHOUT

    var civilizations = mutableListOf<Civilization>()
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
    var checksum = ""

    var victoryData: VictoryData? = null

    /** Maps a civ to the civ they voted for - `null` on the value side means they abstained */
    var diplomaticVictoryVotesCast = HashMap<String, String?>()
    // Set to false whenever the results still need te be processed
    var diplomaticVictoryVotesProcessed = false

    /** The turn the replay history started recording.
     *
     *  *   `-1` means the game was serialized with an older version without replay
     *  *   `0`  would be the normal value in any newer game
     *      (remember gameParameters.startingEra is not implemented through turns starting > 0)
     *  *   `>0` would be set by compatibility migration, handled in [BackwardCompatibility.migrateToTileHistory]
     *
     *  @see [com.unciv.logic.map.tile.TileHistory]
     */
    var historyStartTurn = -1

    /**
     * Keep track of a custom location this game was saved to _or_ loaded from, using it as the default custom location for any further save/load attempts.
     */
    @Volatile
    var customSaveLocation: String? = null

    //endregion
    //region Fields - Transient

    @Transient
    lateinit var difficultyObject: Difficulty // Since this is static game-wide, and was taking a large part of nextTurn

    @Transient
    lateinit var speed: Speed

    @Transient
    lateinit var currentPlayerCiv: Civilization // this is called thousands of times, no reason to search for it with a find{} every time

    /** This is used in multiplayer games, where I may have a saved game state on my phone
     * that is inconsistent with the saved game on the cloud */
    @Transient
    var isUpToDate = false

    @Transient
    lateinit var ruleset: Ruleset

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

    @Transient
    var cityDistances: CityDistanceData = CityDistanceData()

    //endregion
    //region Pure functions

    fun clone(): GameInfo {
        val toReturn = GameInfo()
        toReturn.tileMap = tileMap.clone()
        toReturn.civilizations = civilizations.asSequence()
            .map { it.clone() }
            .toCollection(ArrayList(civilizations.size))
        toReturn.barbarians = barbarians.clone()
        toReturn.religions.putAll(religions.asSequence().map { it.key to it.value.clone() })
        toReturn.currentPlayer = currentPlayer
        toReturn.currentTurnStartTime = currentTurnStartTime
        toReturn.turns = turns
        toReturn.difficulty = difficulty
        toReturn.gameParameters = gameParameters
        toReturn.gameId = gameId
        toReturn.diplomaticVictoryVotesCast.putAll(diplomaticVictoryVotesCast)
        toReturn.oneMoreTurnMode = oneMoreTurnMode
        toReturn.customSaveLocation = customSaveLocation
        toReturn.victoryData = victoryData?.copy()
        toReturn.historyStartTurn = historyStartTurn

        return toReturn
    }

    fun getPlayerToViewAs(): Civilization {
        if (!gameParameters.isOnlineMultiplayer) return getCurrentPlayerCivilization() // non-online, play as human player
        val userId = UncivGame.Current.settings.multiplayer.userId

        // Iterating on all civs, starting from the the current player, gives us the one that will have the next turn
        // This allows multiple civs from the same UserID
        if (civilizations.any { it.playerId == userId }) {
            var civIndex = civilizations.map { it.civName }.indexOf(currentPlayer)
            while (true) {
                val civToCheck = civilizations[civIndex % civilizations.size]
                if (civToCheck.playerId == userId) return civToCheck
                civIndex++
            }
        } else {
            // you aren't anyone. How did you even get this game? Can you spectate?
            return getSpectator(userId)
        }
    }

    @delegate:Transient
    val civMap by lazy { civilizations.associateBy { it.civName } }
    /** Get a civ by name
     *  @throws NoSuchElementException if no civ of that name is in the game (alive or dead)! */
    fun getCivilization(civName: String) = civMap[civName]
        ?: civilizations.first { it.civName == civName } // This is for spectators who are added in later, artificially
    fun getCurrentPlayerCivilization() = currentPlayerCiv
    fun getCivilizationsAsPreviews() = civilizations.map { it.asPreview() }.toMutableList()
    /** Get barbarian civ
     *  @throws NoSuchElementException in no-barbarians games! */
    fun getBarbarianCivilization() = getCivilization(Constants.barbarians)
    fun getDifficulty() = difficultyObject
    fun getCities() = civilizations.asSequence().flatMap { it.cities }
    fun getAliveCityStates() = civilizations.filter { it.isAlive() && it.isCityState() }
    fun getAliveMajorCivs() = civilizations.filter { it.isAlive() && it.isMajorCiv() }

    /** Gets civilizations in their commonly used order - City-states last,
     *  otherwise alphabetically by culture and translation. [civToSortFirst] can be used to force
     *  a specific Civilization to be listed first.
     *
     *  Barbarians and Spectators always excluded, other filter criteria are [includeCityStates],
     *  [includeDefeated] and optionally an [additionalFilter].
     */
    fun getCivsSorted(
        includeCityStates: Boolean = true,
        includeDefeated: Boolean = false,
        civToSortFirst: Civilization? = null,
        additionalFilter: ((Civilization) -> Boolean)? = null
    ): Sequence<Civilization> {
        val collator = GUI.getSettings().getCollatorFromLocale()
        return civilizations.asSequence()
            .filterNot {
                it.isBarbarian() ||
                it.isSpectator() ||
                !includeDefeated && it.isDefeated() ||
                !includeCityStates && it.isCityState() ||
                additionalFilter?.invoke(it) == false
            }
            .sortedWith(
                compareBy<Civilization> { it != civToSortFirst }
                    .thenByDescending { it.isMajorCiv() }
                    .thenBy(collator) { it.civName.tr(hideIcons = true) }
            )
    }

    /** Returns the first spectator for a [playerId] or creates one if none found */
    fun getSpectator(playerId: String): Civilization {
        val gameSpectatorCiv = civilizations.firstOrNull {
            it.isSpectator() && it.playerId == playerId
        }
        return gameSpectatorCiv ?: createTemporarySpectatorCiv(playerId)

    }

    private fun createTemporarySpectatorCiv(playerId: String) = Civilization(Constants.spectator).also {
        it.playerType = PlayerType.Human
        it.playerId = playerId
        civilizations.add(it)
        it.gameInfo = this
        it.setNationTransient()
        it.cache.updateViewableTiles()
        it.setTransients()
    }

    fun isReligionEnabled(): Boolean {
        val religionDisabledByRuleset = (ruleset.eras[gameParameters.startingEra]!!.hasUnique(UniqueType.DisablesReligion)
                || ruleset.modOptions.hasUnique(UniqueType.DisableReligion))
        return !religionDisabledByRuleset
    }

    fun isEspionageEnabled(): Boolean {
        return gameParameters.espionageEnabled
    }

    private fun getEquivalentTurn(): Int {
        val totalTurns = speed.numTotalTurns()
        val startPercent = ruleset.eras[gameParameters.startingEra]!!.startPercent
        return turns + (totalTurns * startPercent / 100)
    }

    fun getYear(turnOffset: Int = 0): Int {
        val turn = getEquivalentTurn() + turnOffset
        val yearsToTurn = speed.yearsPerTurn
        var year = speed.startYear
        var i = 0
        var yearsPerTurn: Float

        while (i < turn) {
            yearsPerTurn = (yearsToTurn.firstOrNull { i < it.untilTurn }?.yearInterval ?: yearsToTurn.last().yearInterval)
            year += yearsPerTurn
            ++i
        }

        return year.toInt()
    }

    fun calculateChecksum(): String {
        val oldChecksum = checksum
        checksum = "" // Checksum calculation cannot include old checksum, obvs
        val bytes = MessageDigest
            .getInstance("SHA-1")
            .digest(json().toJson(this).toByteArray(Charsets.UTF_8))
        checksum = oldChecksum
        return Gzip.encode(bytes)
    }

    //endregion
    //region State changing functions

    // Do we automatically simulate until N turn?
    fun isSimulation(): Boolean = turns < DebugUtils.SIMULATE_UNTIL_TURN
            || turns < simulateMaxTurns && simulateUntilWin

    fun nextTurn(progressBar: NextTurnProgress? = null) {

        var player = currentPlayerCiv
        var playerIndex = civilizations.indexOf(player)

        // We rotate Players in cycle: 1,2...N,1,2...
        fun setNextPlayer() {
            playerIndex = (playerIndex + 1) % civilizations.size
            if (playerIndex == 0) {
                turns++
                if (DebugUtils.SIMULATE_UNTIL_TURN != 0)
                    debug("Starting simulation of turn %s", turns)
            }
            player = civilizations[playerIndex]
        }


        // Ending current player's turn
        //  (Check is important or else switchTurn
        //  would skip a turn if an AI civ calls nextTurn
        //  this happens when resigning a multiplayer game)
        if (player.isHuman()) {
            TurnManager(player).endTurn(progressBar)
            setNextPlayer()
        }


        val isOnline = gameParameters.isOnlineMultiplayer

        // Skip the player if we are playing hotseat
        // If all hotseat players are defeated then skip all but the first one
        fun shouldAutoProcessHotseatPlayer(): Boolean =
            !isOnline &&
            player.isDefeated() && (civilizations.any { it.isHuman() && it.isAlive() }
                || civilizations.first { it.isHuman() } != player)

        // Skip all spectators and defeated players
        // If all players are defeated then let the first player control next turn
        fun shouldAutoProcessOnlinePlayer(): Boolean =
            isOnline && (player.isSpectator() || player.isDefeated() &&
                (civilizations.any { it.isHuman() && it.isAlive() }
                    || civilizations.first { it.isHuman() } != player))

        // We process player automatically if:
        while (isSimulation() ||                    // simulation is active
                player.isAI() ||                    // or player is AI
            shouldAutoProcessHotseatPlayer() ||     // or a player is defeated in hotseat
            shouldAutoProcessOnlinePlayer())        // or player is online spectator
        {

            // Starting preparations
            TurnManager(player).startTurn(progressBar)

            // Automation done here
            TurnManager(player).automateTurn()

            val worldScreen = UncivGame.Current.worldScreen
            // Do we need to break if player won?
            if (simulateUntilWin && player.victoryManager.hasWon()) {
                simulateUntilWin = false
                worldScreen?.autoPlay?.stopAutoPlay()
                break
            }

            // Do we need to stop AutoPlay?
            if (worldScreen != null && worldScreen.autoPlay.isAutoPlaying() && player.victoryManager.hasWon() && !oneMoreTurnMode)
                worldScreen.autoPlay.stopAutoPlay()

            // Clean up
            TurnManager(player).endTurn(progressBar)

            // To the next player
            setNextPlayer()
        }

        if (turns == DebugUtils.SIMULATE_UNTIL_TURN)
            DebugUtils.SIMULATE_UNTIL_TURN = 0

        // We found a human player, so we are making them current
        currentTurnStartTime = System.currentTimeMillis()
        currentPlayer = player.civName
        currentPlayerCiv = getCivilization(currentPlayer)

        // Starting their turn
        TurnManager(player).startTurn(progressBar)

        // No popups for spectators
        if (currentPlayerCiv.isSpectator())
            currentPlayerCiv.popupAlerts.clear()

        // Play some nice music TODO: measuring actual play time might be nicer
        if (turns % 10 == 0)
            UncivGame.Current.musicController.chooseTrack(
                currentPlayerCiv.civName,
                MusicMood.peaceOrWar(currentPlayerCiv.isAtWar()), MusicTrackChooserFlags.setNextTurn
            )

        // Start our turn immediately before the player can make decisions - affects
        // whether our units can commit automated actions and then be attacked immediately etc.
        notifyOfCloseEnemyUnits(player)
    }

    private fun notifyOfCloseEnemyUnits(thisPlayer: Civilization) {
        val viewableInvisibleTiles = thisPlayer.viewableInvisibleUnitsTiles.map { it.position }
        val enemyUnitsCloseToTerritory = thisPlayer.viewableTiles
            .filter {
                it.militaryUnit != null && it.militaryUnit!!.civ != thisPlayer
                        && thisPlayer.isAtWarWith(it.militaryUnit!!.civ)
                        && (it.getOwner() == thisPlayer || it.neighbors.any { neighbor -> neighbor.getOwner() == thisPlayer }
                        && (!it.militaryUnit!!.isInvisible(thisPlayer) || viewableInvisibleTiles.contains(it.position)))
            }

        // enemy units IN our territory
        addEnemyUnitNotification(
            thisPlayer,
            enemyUnitsCloseToTerritory.filter { it.getOwner() == thisPlayer },
            "in"
        )
        // enemy units NEAR our territory
        addEnemyUnitNotification(
            thisPlayer,
            enemyUnitsCloseToTerritory.filter { it.getOwner() != thisPlayer },
            "near"
        )

        addBombardNotification(
            thisPlayer,
            thisPlayer.cities.filter { city ->
                city.canBombard() &&
                        enemyUnitsCloseToTerritory.any { tile -> tile.aerialDistanceTo(city.getCenterTile()) <= city.range }
            }
        )
    }

    fun getEnabledVictories() = ruleset.victories.filter { !it.value.hiddenInVictoryScreen && gameParameters.victoryTypes.contains(it.key) }

    fun processDiplomaticVictory() {
        if (diplomaticVictoryVotesProcessed) return
        for (civInfo in civilizations) {
            if (civInfo.victoryManager.hasEnoughVotesForDiplomaticVictory()) {
                civInfo.victoryManager.hasEverWonDiplomaticVote = true
            }
        }
        diplomaticVictoryVotesProcessed = true
    }

    /** @return `true` if someone has won - checks existing [victoryData] and each civ's [VictoryManager.getVictoryTypeAchieved] */
    fun checkForVictory(): Boolean {
        if (victoryData != null) return true
        for (civ in civilizations) {
            TurnManager(civ).updateWinningCiv()
            if (victoryData != null) return true
        }
        return false
    }

    private fun addEnemyUnitNotification(thisPlayer: Civilization, tiles: List<Tile>, inOrNear: String) {
        // don't flood the player with similar messages. instead cycle through units by clicking the message multiple times.
        if (tiles.size < 3) {
            for (tile in tiles) {
                val unitName = tile.militaryUnit!!.name
                thisPlayer.addNotification("An enemy [$unitName] was spotted $inOrNear our territory", tile.position, NotificationCategory.War, NotificationIcon.War, unitName)
            }
        } else {
            val positions = tiles.asSequence().map { it.position }
            thisPlayer.addNotification("[${tiles.size}] enemy units were spotted $inOrNear our territory", LocationAction(positions), NotificationCategory.War, NotificationIcon.War)
        }
    }

    private fun addBombardNotification(thisPlayer: Civilization, cities: List<City>) {
        if (cities.size < 3) {
            for (city in cities)
                thisPlayer.addNotification("Your city [${city.name}] can bombard the enemy!", city.location, NotificationCategory.War, NotificationIcon.City, NotificationIcon.Crosshair)
        } else {
            val positions = cities.asSequence().map { it.location }
            thisPlayer.addNotification("[${cities.size}] of your cities can bombard the enemy!", LocationAction(positions),  NotificationCategory.War, NotificationIcon.City, NotificationIcon.Crosshair)
        }
    }

    /** Generate and show a notification pointing out resources.
     *  Used by [addTechnology][TechManager.addTechnology] and [ResourcesOverviewTab][com.unciv.ui.screens.overviewscreen.ResourcesOverviewTab]
     * @param maxDistance from next City, 0 removes distance limitation.
     * @param filter optional tile filter predicate, e.g. to exclude foreign territory.
     * @return `false` if no resources were found and no notification was added.
     * @see getExploredResourcesNotification
     */
    fun notifyExploredResources(
        civInfo: Civilization,
        resourceName: String,
        maxDistance: Int = Int.MAX_VALUE,
        filter: (Tile) -> Boolean = { true }
    ): Boolean {
        val notification = getExploredResourcesNotification(civInfo, resourceName, maxDistance, filter)
            ?: return false
        civInfo.notifications.add(notification)
        return true
    }

    /** Generate a notification pointing out resources.
     * @param maxDistance from next City, default removes distance limitation.
     * @param filter optional tile filter predicate, e.g. to exclude foreign territory.
     * @return `null` if no resources were found, otherwise a Notification instance.
     * @see notifyExploredResources
     */
    fun getExploredResourcesNotification(
        civInfo: Civilization,
        resourceName: String,
        maxDistance: Int = Int.MAX_VALUE,
        filter: (Tile) -> Boolean = { true }
    ): Notification? {
        data class CityTileAndDistance(val city: City, val tile: Tile, val distance: Int)

        val exploredRevealTiles: Sequence<Tile> =
                if (ruleset.tileResources[resourceName]!!.hasUnique(UniqueType.CityStateOnlyResource)) {
                    // Look for matching mercantile CS centers
                    getAliveCityStates()
                        .asSequence()
                        .filter { it.cityStateResource == resourceName }
                        .map { it.getCapital()!!.getCenterTile() }
                } else {
                    tileMap.values
                        .asSequence()
                        .filter { it.resource == resourceName }
                }

        val exploredRevealInfo = exploredRevealTiles
            .filter { civInfo.hasExplored(it) }
            .flatMap { tile ->
                civInfo.cities.asSequence()
                    .map {
                        // build a full cross join all revealed tiles * civ's cities (should rarely surpass a few hundred)
                        // cache distance for each pair as sort will call it ~ 2n log n times
                        // should still be cheaper than looking up 'the' closest city per reveal tile before sorting
                            city ->
                        CityTileAndDistance(city, tile, tile.aerialDistanceTo(city.getCenterTile()))
                    }
            }
            .filter { it.distance <= maxDistance && filter(it.tile) }
            .sortedWith(compareBy { it.distance })
            .distinctBy { it.tile }

        val chosenCity = exploredRevealInfo.firstOrNull()?.city
            ?: return null
        val positions = exploredRevealInfo
            // re-sort to a more pleasant display order
            .sortedWith(compareBy { it.tile.aerialDistanceTo(chosenCity.getCenterTile()) })
            .map { it.tile.position }

        val positionsCount = positions.count()
        val text = if (positionsCount == 1)
            "[$resourceName] revealed near [${chosenCity.name}]"
        else
            "[$positionsCount] sources of [$resourceName] revealed, e.g. near [${chosenCity.name}]"

        return Notification(text, arrayOf("ResourceIcons/$resourceName"),
            LocationAction(positions).asIterable(), NotificationCategory.General)
    }

    // All cross-game data which needs to be altered (e.g. when removing or changing a name of a building/tech)
    // will be done here, and not in Civilization.setTransients or City
    fun setTransients() {
        tileMap.gameInfo = this

        // [TEMPORARY] Convert old saves to newer ones by moving base rulesets from the mod list to the base ruleset field
        convertOldSavesToNewSaves()

        // Cater for the mad modder using trailing '-' in their repo name - convert the mods list so
        // it requires our new, Windows-safe local name (no trailing blanks)
        for ((oldName, newName) in gameParameters.mods.map { it to it.repoNameToFolderName(onlyOuterBlanks = true) }) {
            if (newName == oldName) continue
            gameParameters.mods.remove(oldName)
            gameParameters.mods.add(newName)
        }

        ruleset = RulesetCache.getComplexRuleset(gameParameters)

        // any mod the saved game lists that is currently not installed causes null pointer
        // exceptions in this routine unless it contained no new objects or was very simple.
        // Player's fault, so better complain early:
        val missingMods = (listOf(gameParameters.baseRuleset) + gameParameters.mods)
            .filterNot { it in ruleset.mods }
        if (missingMods.isNotEmpty())
            throw MissingModsException(missingMods)

        removeMissingModReferences()

        for (baseUnit in ruleset.units.values)
            baseUnit.ruleset = ruleset

        for (building in ruleset.buildings.values)
            building.ruleset = ruleset

        // This needs to go before tileMap.setTransients, as units need to access
        // the nation of their civilization when setting transients
        for (civInfo in civilizations) civInfo.gameInfo = this
        for (civInfo in civilizations) civInfo.setNationTransient()
        // must be done before updating tileMap, since unit uniques depend on civ uniques depend on allied city-state uniques depend on diplomacy
        for (civInfo in civilizations) {
            for (diplomacyManager in civInfo.diplomacy.values) {
                diplomacyManager.civInfo = civInfo
                diplomacyManager.updateHasOpenBorders()
            }
        }

        tileMap.setTransients(ruleset)

        if (currentPlayer == "") currentPlayer =
            if (gameParameters.isOnlineMultiplayer) civilizations.first { it.isHuman() && !it.isSpectator() }.civName // For MP, spectator doesn't get a 'turn'
            else civilizations.first { it.isHuman()  }.civName // for non-MP games, you can be a spectator of an AI-only match, and you *do* get a turn, sort of
        currentPlayerCiv = getCivilization(currentPlayer)

        difficultyObject = ruleset.difficulties[difficulty]!!

        speed = ruleset.speeds[gameParameters.speed]!!

        for (religion in religions.values) religion.setTransients(this)


        for (civInfo in civilizations) civInfo.setTransients()
        tileMap.setNeutralTransients() // has to happen after civInfo.setTransients() sets owningCity

        for (civInfo in civilizations) // Due to religion victory, has to happen after civInfo.religionManager is set for all civs
            civInfo.thingsToFocusOnForVictory = civInfo.getPreferredVictoryTypeObjects().flatMap { it.getThingsToFocus(civInfo) }.toSet()

        convertFortify()

        updateCivilizationState()

        spaceResources.clear()
        spaceResources.addAll(ruleset.buildings.values.filter { it.hasUnique(UniqueType.SpaceshipPart) }
            .flatMap { it.getResourceRequirementsPerTurn().keys })
        spaceResources.addAll(ruleset.victories.values.flatMap { it.requiredSpaceshipParts })

        barbarians.setTransients(this)

        cityDistances.game = this

        guaranteeUnitPromotions()

        migrateToTileHistory()

        migrateGreatGeneralPools()
    }

    private fun updateCivilizationState() {
        for (civInfo in civilizations.asSequence()
            // update city-state resource first since the happiness of major civ depends on it.
            // See issue: https://github.com/yairm210/Unciv/issues/7781
            .sortedByDescending { it.isCityState() }
        ) {
            for (unit in civInfo.units.getCivUnits())
                unit.updateVisibleTiles(false) // this needs to be done after all the units are assigned to their civs and all other transients are set
            if (civInfo.playerType == PlayerType.Human)
                civInfo.exploredRegion.setMapParameters(tileMap.mapParameters) // Required for the correct calculation of the explored region on world wrap maps

            civInfo.cache.updateOurTiles()
            civInfo.cache.updateSightAndResources() // only run ONCE and not for each unit - this is a huge performance saver!

            // Since this depends on the cities of ALL civilizations,
            // we need to wait until we've set the transients of all the cities before we can run this.
            // Hence why it's not in CivInfo.setTransients().
            civInfo.cache.updateCitiesConnectedToCapital(true)

            // We need to determine the GLOBAL happiness state in order to determine the city stats
            val localUniqueCache = LocalUniqueCache()
            for (city in civInfo.cities) {
                city.cityStats.updateTileStats(localUniqueCache) // Some nat wonders can give happiness!
                city.cityStats.updateCityHappiness(
                    city.cityConstructions.getStats(localUniqueCache)
                )
            }

            for (city in civInfo.cities) {
                /** We remove constructions from the queue that aren't defined in the ruleset.
                 * This can lead to situations where the city is puppeted and had its construction removed, and there's no way to user-set it
                 * So for cities like those, we'll auto-set the construction
                 * Also set construction for human players who have automate production turned on
                 */
                if (city.cityConstructions.constructionQueue.isEmpty())
                    city.cityConstructions.chooseNextConstruction()

                // We also remove resources that the city may be demanding but are no longer in the ruleset
                if (!ruleset.tileResources.containsKey(city.demandedResource))
                    city.demandedResource = ""

                // No uniques have changed since the cache was created, so we can still use it
                city.cityStats.update(localUniqueCache=localUniqueCache)
            }
        }
    }

    private fun convertOldSavesToNewSaves() {
        val baseRulesetInMods = gameParameters.mods.firstOrNull { RulesetCache[it]?.modOptions?.isBaseRuleset == true }
        if (baseRulesetInMods != null) {
            gameParameters.baseRuleset = baseRulesetInMods
            gameParameters.mods = LinkedHashSet(gameParameters.mods.filter { it != baseRulesetInMods })
        }
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
}

/** Class to use when parsing jsons if you only want the serialization [version]. */
class GameInfoSerializationVersion : HasGameInfoSerializationVersion {
    override var version = FIRST_WITHOUT
}
