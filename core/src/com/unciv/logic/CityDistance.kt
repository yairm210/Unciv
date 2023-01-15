package com.unciv.logic

import com.badlogic.gdx.math.Vector2
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo

class CityDistance(
    val city: CityInfo,
    val distance: Int) {

    companion object {
        fun compare(a: CityDistance?, b: CityDistance?) : CityDistance? {

            if (a == null && b != null)
                return b
            else if (a != null && b == null)
                return a
            else if (a == null && b == null)
                return null

            if (a!!.distance < b!!.distance)
                return a
            else if (a.distance > b.distance)
                return b

            if (a.city.civInfo.isMajorCiv() && b.city.civInfo.isMinorCiv())
                return a
            else if (b.city.civInfo.isMajorCiv() && a.city.civInfo.isMinorCiv())
                return b

            return a
        }
    }
}

/** This class holds information about distance from every tile to the nearest city */
class CityDistanceData {

    @Transient
    lateinit var game: GameInfo

    companion object {
        const val IDENTIFIER_ALL_CIVS = "ALL_CIVS"
        const val IDENTIFIER_MAJOR_CIVS = "MAJOR_CIVS"
    }

    private var shouldUpdate: Boolean = true

    /** Identifier -> Map (Tile position -> Distance)
     *  Identifier is either: Civ name, ALL_CIVS or MAJOR_CIVS */
    private var data: HashMap<String, HashMap<Vector2, CityDistance?>> = HashMap()

    private fun reset() {
        data = HashMap()
        data[IDENTIFIER_ALL_CIVS]= HashMap()
        data[IDENTIFIER_MAJOR_CIVS] = HashMap()
    }

    private fun resetPlayer(identifier: String) {
        data[identifier] = HashMap()
    }

    private fun updateDistanceIfLower(identifier: String, position: Vector2, city: CityInfo, distance: Int) {
        val currentDistance = data[identifier]!![position]
        val newDistance = CityDistance(city, distance)
        data[identifier]!![position] = CityDistance.compare(currentDistance, newDistance)
    }

    private fun updateDistances(thisTile: TileInfo, city: CityInfo, owner: CivilizationInfo, isMajor: Boolean) {

        val cityTile = city.getCenterTile()
        val distance = thisTile.aerialDistanceTo(cityTile)

        updateDistanceIfLower(IDENTIFIER_ALL_CIVS, thisTile.position, city, distance)
        if (isMajor) {
            updateDistanceIfLower(IDENTIFIER_MAJOR_CIVS, thisTile.position, city, distance)
            updateDistanceIfLower(owner.civName, thisTile.position, city, distance)
        }
    }

    private fun update() {

        // Clear previous info
        reset()

        for (player in game.civilizations) {

            // Not interested in defeated players
            if (player.isDefeated())
                continue

            val isMajor = player.isMajorCiv()
            if (isMajor)
                resetPlayer(player.civName)

            // Update distances for each tile inside radius 4 around each city
            for (city in player.cities.asSequence())
                for (otherTile in city.getCenterTile().getTilesInDistance(4))
                    updateDistances(otherTile, city, player, isMajor)
        }

        shouldUpdate = false

    }

    fun getClosestCityDistance(tile: TileInfo, player: CivilizationInfo? = null, majorsOnly: Boolean = false) : CityDistance? {

        if (shouldUpdate)
            update()

        val identifier = when {
            player != null && player.isMajorCiv() -> player.civName
            majorsOnly -> IDENTIFIER_MAJOR_CIVS
            else -> IDENTIFIER_ALL_CIVS
        }
        return data[identifier]!![tile.position]
    }

    fun setDirty() {
        shouldUpdate = true
    }

}
