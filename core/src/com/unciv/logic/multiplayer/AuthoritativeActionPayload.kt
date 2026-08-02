package com.unciv.logic.multiplayer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * POST /files/{gameId}/action body — shared by client encode and UncivServer decode.
 *
 * Unit: move | attack | foundCity
 * Mid-turn: dismissAlert | acceptTrade | declineTrade | dismissTrade | setProduction | chooseBeliefs
 */
@Serializable
data class AuthoritativeActionPayload(
    val type: String,
    val unitId: Int = -1,
    val fromX: Int = 0,
    val fromY: Int = 0,
    val toX: Int = 0,
    val toY: Int = 0,
    /** dismissAlert */
    val alertType: String? = null,
    val alertValue: String? = null,
    /** acceptTrade / declineTrade / dismissTrade */
    val requestingCiv: String? = null,
    /** setProduction — replace the city's construction queue */
    val cityId: String? = null,
    val constructionQueue: List<String>? = null,
    val currentConstructionIsUserSet: Boolean = true,
    /** chooseBeliefs — pantheon / founding / enhancing */
    val beliefNames: List<String>? = null,
    val useFreeBeliefs: Boolean = false,
    val religionName: String? = null,
    val religionDisplayName: String? = null,
) {
    companion object {
        /** encodeDefaults so false flags (e.g. currentConstructionIsUserSet) are not dropped. */
        val json = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }
    }
}
