package com.unciv.logic.multiplayer

import com.badlogic.gdx.Net
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.civilization.PopupAlert
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.multiplayer.storage.SimpleHttp
import com.unciv.logic.multiplayer.storage.UncivServerFileStorage
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Evgeny's model: client sends a small action JSON; UncivServer applies it to the canonical save
 * and returns the new save. The client must not commit unit moves/attacks until HTTP 200.
 *
 * Mid-turn decisions (alerts, trades, production) also go through POST /action so the server
 * stays canonical. [applyLocalPlayerMidTurnState] remains a safety net if a race loses a sync.
 *
 * [scheduleMidTurnSync] is a fallback PUT for mid-turn state not yet covered by /action.
 * Prefer dedicated action types when available.
 */
object AuthoritativeUnitActions {

    private val dismissedAlertKeys = HashSet<String>()
    private val dismissedTradeRequestKeys = HashSet<String>()
    private var midTurnSyncJob: Job? = null

    fun isEnabled(gameInfo: GameInfo): Boolean =
        gameInfo.gameParameters.isOnlineMultiplayer
            && gameInfo.gameParameters.serverAuthoritativeUnitActions

    /**
     * Debounced full-save PUT for mid-turn state without a dedicated /action yet.
     * Allowed when unit tiles are unchanged (server [isForbiddenMidTurnPut]).
     */
    fun scheduleMidTurnSync() {
        val game = UncivGame.Current.gameInfo ?: return
        if (!isEnabled(game)) return
        midTurnSyncJob?.cancel()
        midTurnSyncJob = Concurrency.run("AuthMidTurnSync") {
            delay(250)
            val latest = UncivGame.Current.gameInfo ?: return@run
            if (!isEnabled(latest)) return@run
            try {
                UncivGame.Current.onlineMultiplayer.updateGame(latest)
            } catch (ex: Exception) {
                Log.debug("Failed to sync mid-turn: %s", ex.message)
            }
        }
    }
    fun rememberDismissedAlert(alert: PopupAlert) {
        dismissedAlertKeys.add(alertKey(alert))
    }

    fun rememberDismissedTradeRequest(requestingCiv: String, tradeSummary: String) {
        dismissedTradeRequestKeys.add("$requestingCiv|$tradeSummary")
    }

    fun clearSessionUiState() {
        dismissedAlertKeys.clear()
        dismissedTradeRequestKeys.clear()
    }

    fun applyLocalPlayerMidTurnState(local: GameInfo, incoming: GameInfo) {
        if (local.gameId != incoming.gameId) return
        applyDismissedAlerts(incoming)
        applyDismissedTradeRequests(incoming)
        applyLocalCityProduction(local, incoming)
        applyLocalTradeRequests(local, incoming)
    }

    fun applyDismissedAlerts(incoming: GameInfo) {
        if (dismissedAlertKeys.isEmpty()) return
        for (civ in incoming.civilizations) {
            civ.popupAlerts.removeAll { alertKey(it) in dismissedAlertKeys }
        }
    }

    private fun applyDismissedTradeRequests(incoming: GameInfo) {
        if (dismissedTradeRequestKeys.isEmpty()) return
        for (civ in incoming.civilizations) {
            civ.tradeRequests.removeAll {
                "${it.requestingCiv}|${tradeSummary(it.trade)}" in dismissedTradeRequestKeys
            }
        }
    }

    fun applyLocalTradeRequests(local: GameInfo, incoming: GameInfo) {
        val localCiv = local.civilizations.firstOrNull { it.civName == local.currentPlayer } ?: return
        val incomingCiv = incoming.civilizations.firstOrNull { it.civName == local.currentPlayer } ?: return
        val localKeys = localCiv.tradeRequests
            .map { "${it.requestingCiv}|${tradeSummary(it.trade)}" }
            .toSet()
        incomingCiv.tradeRequests.removeAll {
            val key = "${it.requestingCiv}|${tradeSummary(it.trade)}"
            key !in localKeys || key in dismissedTradeRequestKeys
        }
    }

    fun applyLocalCityProduction(local: GameInfo, incoming: GameInfo) {
        val localCiv = local.civilizations.firstOrNull { it.civName == local.currentPlayer } ?: return
        val incomingCiv = incoming.civilizations.firstOrNull { it.civName == local.currentPlayer } ?: return
        val localById = localCiv.cities.associateBy { it.id }
        for (city in incomingCiv.cities) {
            val localCity = localById[city.id] ?: continue
            val from = localCity.cityConstructions
            val to = city.cityConstructions
            to.constructionQueue.clear()
            to.constructionQueue.addAll(from.constructionQueue)
            to.currentConstructionIsUserSet = from.currentConstructionIsUserSet
            to.inProgressConstructions.clear()
            to.inProgressConstructions.putAll(from.inProgressConstructions)
            to.productionOverflow = from.productionOverflow
        }
    }

    private fun alertKey(alert: PopupAlert) = "${alert.type.name}|${alert.value}"

    fun tradeSummary(trade: com.unciv.logic.trade.Trade): String {
        fun side(offers: List<com.unciv.logic.trade.TradeOffer>) =
            offers.joinToString(",") { "${it.type}:${it.name}:${it.amount}" }
        return "ours=${side(trade.ourOffers)};theirs=${side(trade.theirOffers)}"
    }

    /** POST /files/{id}/action with [AuthoritativeActionPayload] encoded via kotlinx.serialization. */
    suspend fun postAction(
        gameId: String,
        payload: AuthoritativeActionPayload,
        errorOut: (String) -> Unit = {},
    ): String? = withContext(Dispatchers.IO) {
        val server = UncivGame.Current.onlineMultiplayer.multiplayerServer
        val url = "${server.getServerUrl().trimEnd('/')}/files/$gameId/action"
        val jsonBody = AuthoritativeActionPayload.json.encodeToString(
            AuthoritativeActionPayload.serializer(),
            payload
        )
        var newSave: String? = null
        var error: String? = "No response"
        SimpleHttp.sendRequest(
            Net.HttpMethods.POST,
            url,
            jsonBody,
            timeout = 60000,
            header = UncivServerFileStorage.authHeader
        ) { success, result, code ->
            if (success && code == 200 && result.isNotBlank()) {
                newSave = result
                error = null
            } else {
                error = "Server rejected action (${code}): $result"
            }
        }
        if (error != null) {
            Log.debug("Authoritative action failed: %s", error)
            errorOut(error!!)
        }
        newSave
    }

    suspend fun requestAction(
        gameId: String,
        type: String,
        unit: MapUnit,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int,
        errorOut: (String) -> Unit = {},
    ): String? = postAction(
        gameId,
        AuthoritativeActionPayload(
            type = type,
            unitId = unit.id,
            fromX = fromX,
            fromY = fromY,
            toX = toX,
            toY = toY,
        ),
        errorOut
    )

    suspend fun requestDismissAlert(
        gameId: String,
        alertType: String,
        alertValue: String,
        errorOut: (String) -> Unit = {},
    ): String? = postAction(
        gameId,
        AuthoritativeActionPayload(type = "dismissAlert", alertType = alertType, alertValue = alertValue),
        errorOut
    )

    suspend fun requestTradeAction(
        gameId: String,
        type: String, // acceptTrade | declineTrade | dismissTrade
        requestingCiv: String,
        errorOut: (String) -> Unit = {},
    ): String? = postAction(
        gameId,
        AuthoritativeActionPayload(type = type, requestingCiv = requestingCiv),
        errorOut
    )

    suspend fun requestSetProduction(
        gameId: String,
        cityId: String,
        constructionQueue: List<String>,
        currentConstructionIsUserSet: Boolean = true,
        errorOut: (String) -> Unit = {},
    ): String? = postAction(
        gameId,
        AuthoritativeActionPayload(
            type = "setProduction",
            cityId = cityId,
            constructionQueue = constructionQueue,
            currentConstructionIsUserSet = currentConstructionIsUserSet,
        ),
        errorOut
    )

    suspend fun requestChooseBeliefs(
        gameId: String,
        beliefNames: List<String>,
        useFreeBeliefs: Boolean = false,
        religionName: String? = null,
        religionDisplayName: String? = null,
        errorOut: (String) -> Unit = {},
    ): String? = postAction(
        gameId,
        AuthoritativeActionPayload(
            type = "chooseBeliefs",
            beliefNames = beliefNames,
            useFreeBeliefs = useFreeBeliefs,
            religionName = religionName,
            religionDisplayName = religionDisplayName,
        ),
        errorOut
    )

    suspend fun requestAdoptPolicy(
        gameId: String,
        policyName: String,
        branchCompletion: Boolean = false,
        errorOut: (String) -> Unit = {},
    ): String? = postAction(
        gameId,
        AuthoritativeActionPayload(
            type = "adoptPolicy",
            policyName = policyName,
            branchCompletion = branchCompletion,
        ),
        errorOut
    )

    suspend fun requestChooseGreatPerson(
        gameId: String,
        unitName: String,
        mayaLimited: Boolean = false,
        errorOut: (String) -> Unit = {},
    ): String? = postAction(
        gameId,
        AuthoritativeActionPayload(
            type = "chooseGreatPerson",
            greatPersonUnitName = unitName,
            mayaLimited = mayaLimited,
        ),
        errorOut
    )

    suspend fun resyncSaveIfDesynced(gameId: String, error: String): String? {
        val desynced = error.contains("is at") ||
            error.contains("unit positions changed") ||
            error.contains("Failed to sync mid-turn")
        if (!desynced) return null
        return downloadCanonicalSave(gameId)
    }

    suspend fun downloadCanonicalSave(gameId: String): String? = withContext(Dispatchers.IO) {
        try {
            UncivServerFileStorage.loadFileData(gameId)
        } catch (ex: Exception) {
            Log.debug("Failed to download canonical save: %s", ex.message)
            null
        }
    }

    fun loadReturnedSave(zippedSave: String, local: GameInfo? = UncivGame.Current.gameInfo): GameInfo {
        val game = UncivFiles.gameInfoFromString(zippedSave)
        if (local != null) applyLocalPlayerMidTurnState(local, game)
        else applyDismissedAlerts(game)
        game.isUpToDate = true
        return game
    }
}
