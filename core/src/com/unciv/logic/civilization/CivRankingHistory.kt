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
     *  - New format looks like this: `statsHistory:{0:"S50G120,...",1:"S55G80,..."}`
     */
    override fun write(json: Json) {
        for ((turn, rankings) in this) {

            val rankingsString = rankings.entries
                .joinToString("") { it.key.idForSerialization.toString() + it.value }
            json.writeValue(turn.toString(), rankingsString)
        }
    }

    private val nonNumber = Regex("[^\\d-]") // Rankings can be negative, so we can't just \D :(
    override fun read(json: Json, jsonData: JsonValue) {
        for (entry in jsonData) {
            val turn = entry.name.toInt()
            val rankings = mutableMapOf<RankingType, Int>()

            if (entry.isString){
                // split into key-value pairs by adding a space before every non-digit, and splitting by spaces
                val pairs = entry.asString().replace(nonNumber, " $0").split(" ")
                    .filter { it.isNotEmpty() } // remove empty entries

                for (pair in pairs) {
                    val rankingType = RankingType.fromIdForSerialization(pair[0]) ?: continue
                    val value = pair.substring(1).toIntOrNull() ?: continue
                    rankings[rankingType] = value
                }
                // New format
            } else {
                // Old format
                for (rankingEntry in entry) {
                    if (rankingEntry.name.length != 1) continue
                    val rankingType = RankingType.fromIdForSerialization(rankingEntry.name[0])
                        ?: continue  // Silently drop unknown ranking types.
                    rankings[rankingType] = rankingEntry.asInt()
                }
            }

            this[turn] = rankings
        }
    }
}
