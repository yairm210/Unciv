package com.unciv.logic.civilization

import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.ui.screens.victoryscreen.RankingType

/** Records for each turn (key of outer map) what the score (value of inner map) was for each RankingType. */
class CivRankingHistory : HashMap<Int, Map<RankingType, Int>>(), IsPartOfGameInfoSerialization, Json.Serializable {

    /**
     * Returns a shallow copy of this [CivRankingHistory] instance.
     * The inner [Map] instances are not cloned, only their references are copied.
     */
    override fun clone(): CivRankingHistory {
        val toReturn = CivRankingHistory()
        toReturn.putAll(this)
        return toReturn
    }

    fun recordRankingStats(civilization: Civilization) {
        this[civilization.gameInfo.turns] =
                RankingType.values().associateWith { civilization.getStatForRanking(it) }
    }

    /** Implement Json.Serializable
     *  - Output looked like this: `statsHistory:{0:{S:50,G:120,...},1:{S:55,G:80,...}}`
     *    (but now we have turned off simplifed json, so it's properly quoted)
     */
    override fun write(json: Json) {
        for ((turn, rankings) in this) {
            json.writeObjectStart(turn.toString())
            for ((rankingType, score) in rankings) {
                json.writeValue(rankingType.idForSerialization, score)
            }
            json.writeObjectEnd()
        }
    }

    override fun read(json: Json, jsonData: JsonValue) {
        for (entry in jsonData) {
            val turn = entry.name.toInt()
            val rankings = mutableMapOf<RankingType, Int>()
            for (rankingEntry in entry) {
                val rankingType = RankingType.fromIdForSerialization(rankingEntry.name)
                    ?: continue  // Silently drop unknown ranking types.
                rankings[rankingType] = rankingEntry.asInt()
            }
            this[turn] = rankings
        }
    }
}
