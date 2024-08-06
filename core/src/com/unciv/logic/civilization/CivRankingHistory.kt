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
                RankingType.entries.associateWith { civilization.getStatForRanking(it) }
    }

    /** Implement Json.Serializable
     *  - Output looked like this: `statsHistory:{0:{S:50,G:120,...},1:{S:55,G:80,...}}`
     *    (but now we have turned off simplifed json, so it's properly quoted)
     *  - New format looks like this: `statsHistory:{0:"50,120,...",1:"55,80,..."}`
     */
    override fun write(json: Json) {
        for ((turn, rankings) in this) {

            // Old format - deprecated 4.12.18
            json.writeObjectStart(turn.toString())
            for ((rankingType, score) in rankings) {
                json.writeValue(rankingType.idForSerialization, score)
            }
            json.writeObjectEnd()

            // New format (disabled)
//            val rankingsString = rankings.entries.sortedBy { it.key }
//                .map { it.value }.joinToString(",")
//            json.writeValue(turn.toString(), rankingsString)
        }
    }

    override fun read(json: Json, jsonData: JsonValue) {
        for (entry in jsonData) {
            val turn = entry.name.toInt()
            val rankings = mutableMapOf<RankingType, Int>()

            if (entry.isString){
                val numbers = entry.asString().split(",").map { it.toInt() }
                // The numbers are ordered by RankingType.
                // By iterating on the RankingType instead of the numbers, we can ensure "future safety" -
                //   new RankingTypes can be added and older versions will just throw the extra numbers away
                for ((index, rankingType) in RankingType.entries.withIndex()) {
                    // getOrNull is a safety mechanism, so we can add new RankingTypes and still parse existing games
                    rankings[rankingType] = numbers.getOrNull(index) ?: continue
                }
                // New format
            } else {
                // Old format
                for (rankingEntry in entry) {
                    val rankingType = RankingType.fromIdForSerialization(rankingEntry.name)
                        ?: continue  // Silently drop unknown ranking types.
                    rankings[rankingType] = rankingEntry.asInt()
                }
            }

            this[turn] = rankings
        }
    }
}
