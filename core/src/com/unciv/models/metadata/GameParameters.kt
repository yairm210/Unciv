package com.unciv.models.metadata

import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.ruleset.Speed

class GameParameters : IsPartOfGameInfoSerialization { // Default values are the default new game
    var difficulty = "Prince"
    var speed: String = Speed.DEFAULT // Not an instance of class Speed

    var randomNumberOfPlayers = false
    var minNumberOfPlayers = 3
    var maxNumberOfPlayers = 3
    var players = ArrayList<Player>().apply {
        add(Player(playerType = PlayerType.Human))
        repeat(3) { add(Player()) }
    }
    var randomNumberOfCityStates = false
    var minNumberOfCityStates = 6
    var maxNumberOfCityStates = 6
    var numberOfCityStates = 6

    var enableRandomNationsPool = false
    var randomNationsPool = arrayListOf<String>()

    var noCityRazing = false
    var noBarbarians = false
    var ragingBarbarians = false
    var oneCityChallenge = false
    var godMode = false
    var nuclearWeaponsEnabled = true
    var espionageEnabled = false
    var noStartBias = false
    var shufflePlayerOrder = false

    // --- AI restrictions (Lekmod-style multiplayer options) ---
    /** AI always accepts a white peace (peace treaty without cities). */
    var aiAlwaysAcceptsWhitePeace = false
    /** Cities cannot be liberated back to AI civilizations. */
    var noAiLiberation = false
    /** AI civilizations do not vote in the World Congress / diplomatic victory. */
    var noAiCongressVotes = false
    /** AI cannot trade with human players (peace treaties still allowed). */
    var noAiTradesWithHumans = false
    /** AI cannot found a religion. */
    var noAiFoundReligion = false
    /** AI missionaries will not spread religion to City-States. */
    var noAiSpreadReligionToCityStates = false
    /** AI missionaries will not spread religion to human-owned cities. */
    var noAiSpreadReligionToHumans = false
    /** AI cannot stage coups in City-States. */
    var noAiCityStateCoups = false
    /** AI cannot build World Wonders. */
    var noAiWorldWonders = false
    /** AI civilizations cannot train city-founder units (Settlers). */
    var noAiSettlers = false
    /** Human units gain no XP from fighting AI (non-barbarian) combatants. */
    var noXpFromAi = false

    var victoryTypes: ArrayList<String> = arrayListOf()
    var startingEra = "Ancient era"

    @Deprecated("Since 4.21.0, use showCivilizationStats")
    var showVictoryStats = true
    // TODO: remove nullable after migration
    var showCivilizationStats: Boolean? = null
        get() = field ?: showVictoryStats
        set(value) {
            field = value
            if (value != null)
                showVictoryStats = value
        }
    var showDemographics = false
    var showRankings = true
    var showCharts = true
    var hideOtherCivilizationStats = false

    // Multiplayer parameters
    var isOnlineMultiplayer = false
    var multiplayerServerUrl: String? = null
    var anyoneCanSpectate = true
    /** After this amount of minutes, anyone can choose to 'skip turn' of the current player to keep the game going */
    var minutesUntilSkipTurn = 60 * 24
    /** Initial players' timer to play before they can be forced to resign permanently*/
    var minutesUntilForceResign = 3 * 24 * 60
    /** Time a player recover on their timer before they can be forced to resign. Time isn't added if the player get their turn skipped*/
    var minutesRecoveredPerTurn = 60 * 24

    var baseRuleset: String = BaseRuleset.Civ_V_GnK.fullName
    var mods = LinkedHashSet<String>()

    var maxTurns = 500

    var acceptedModCheckErrors = ""

    fun clone(): GameParameters {
        val parameters = GameParameters()
        parameters.difficulty = difficulty
        parameters.speed = speed
        parameters.randomNumberOfPlayers = randomNumberOfPlayers
        parameters.minNumberOfPlayers = minNumberOfPlayers
        parameters.maxNumberOfPlayers = maxNumberOfPlayers
        parameters.players = ArrayList(players)
        parameters.randomNumberOfCityStates = randomNumberOfCityStates
        parameters.minNumberOfCityStates = minNumberOfCityStates
        parameters.maxNumberOfCityStates = maxNumberOfCityStates
        parameters.numberOfCityStates = numberOfCityStates
        parameters.enableRandomNationsPool = enableRandomNationsPool
        parameters.randomNationsPool = ArrayList(randomNationsPool)
        parameters.noCityRazing = noCityRazing
        parameters.noBarbarians = noBarbarians
        parameters.ragingBarbarians = ragingBarbarians
        parameters.oneCityChallenge = oneCityChallenge
        // godMode intentionally reset on clone
        parameters.nuclearWeaponsEnabled = nuclearWeaponsEnabled
        parameters.espionageEnabled = espionageEnabled
        parameters.noStartBias = noStartBias
        parameters.shufflePlayerOrder = shufflePlayerOrder
        parameters.aiAlwaysAcceptsWhitePeace = aiAlwaysAcceptsWhitePeace
        parameters.noAiLiberation = noAiLiberation
        parameters.noAiCongressVotes = noAiCongressVotes
        parameters.noAiTradesWithHumans = noAiTradesWithHumans
        parameters.noAiFoundReligion = noAiFoundReligion
        parameters.noAiSpreadReligionToCityStates = noAiSpreadReligionToCityStates
        parameters.noAiSpreadReligionToHumans = noAiSpreadReligionToHumans
        parameters.noAiCityStateCoups = noAiCityStateCoups
        parameters.noAiWorldWonders = noAiWorldWonders
        parameters.noAiSettlers = noAiSettlers
        parameters.noXpFromAi = noXpFromAi
        parameters.victoryTypes = ArrayList(victoryTypes)
        parameters.startingEra = startingEra
        parameters.showCivilizationStats = showCivilizationStats
        parameters.showDemographics = showDemographics
        parameters.showRankings = showRankings
        parameters.showCharts = showCharts
        parameters.hideOtherCivilizationStats = hideOtherCivilizationStats
        parameters.isOnlineMultiplayer = isOnlineMultiplayer
        parameters.multiplayerServerUrl = multiplayerServerUrl
        parameters.anyoneCanSpectate = anyoneCanSpectate
        parameters.baseRuleset = baseRuleset
        parameters.mods = LinkedHashSet(mods)
        parameters.maxTurns = maxTurns
        parameters.acceptedModCheckErrors = acceptedModCheckErrors
        return parameters
    }

    // For debugging and GameStarter console output
    override fun toString() = sequence {
            yield("$difficulty $speed $startingEra")
            yield("${players.count { it.playerType == PlayerType.Human }} ${PlayerType.Human}")
            yield("${players.count { it.playerType == PlayerType.AI }} ${PlayerType.AI}")
            if (randomNumberOfPlayers) yield("Random number of Players: $minNumberOfPlayers..$maxNumberOfPlayers")
            if (randomNumberOfCityStates) yield("Random number of City-States: $minNumberOfCityStates..$maxNumberOfCityStates")
            else yield("$numberOfCityStates CS")
            if (isOnlineMultiplayer) yield("Online Multiplayer")
            if (noBarbarians) yield("No barbs")
            if (ragingBarbarians) yield("Raging barbs")
            if (oneCityChallenge) yield("OCC")
            if (!nuclearWeaponsEnabled) yield("No nukes")
            if (espionageEnabled) yield("Espionage")
            if (aiAlwaysAcceptsWhitePeace) yield("AI white peace")
            if (noAiLiberation) yield("No AI liberation")
            if (noAiCongressVotes) yield("No AI congress votes")
            if (noAiTradesWithHumans) yield("No AI-human trades")
            if (noAiFoundReligion) yield("No AI religion")
            if (noAiSpreadReligionToCityStates) yield("No AI religion→CS")
            if (noAiSpreadReligionToHumans) yield("No AI religion→humans")
            if (noAiCityStateCoups) yield("No AI coups")
            if (noAiWorldWonders) yield("No AI wonders")
            if (noAiSettlers) yield("No AI settlers")
            if (noXpFromAi) yield("No XP from AI")
            if (godMode) yield("God mode")
            yield("Enabled Victories: " + victoryTypes.joinToString())
            yield(baseRuleset)
            yield(if (mods.isEmpty()) "no mods" else mods.joinToString(",", "mods=(", ")", 6) )
        }.joinToString(prefix = "(", postfix = ")")

    /** Get all mods including base
     *
     *  The returned Set is ordered base first, then in the order they are stored in a save.
     *  This creates a fresh instance, and the caller is allowed to mutate it.
     */
    fun getModsAndBaseRuleset() =
        LinkedHashSet<String>(mods.size + 1).apply {
            add(baseRuleset)
            addAll(mods)
        }
}
