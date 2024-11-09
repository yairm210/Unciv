package com.unciv.logic.automation.ai

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.GameInfo
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.map.TileMap
import com.unciv.utils.Log
import java.util.UUID
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

enum class TacticalTerritoryType {
    NONE,
    FRIENDLY,
    ENEMY,
    NEUTRAL
}

class TacticalDominanceZone {
    var id = "UNKNOWN"
    var territoryType = TacticalTerritoryType.NONE
    var owner: Civilization? = null
    var city: City? = null
    var area: Int = -1
    var tileCount: Int = 0

    var neighboringZones: HashSet<String> = HashSet()

    var friendlyMeleeStrength = 0
    var friendlyRangeStrength = 0
    var friendlyNavalMeleeStrength = 0
    var friendlyNavalRangeStrength = 0
    var friendlyUnitCount = 0
    var friendlyNavalUnitCount = 0

    var enemyMeleeStrength = 0
    var enemyRangeStrength = 0
    var enemyNavalMeleeStrength = 0
    var enemyNavalRangeStrength = 0
    var enemyUnitCount = 0
    var enemyNavalUnitCount = 0

    val neutralUnitStrength = 0
    val neutralUnitCount = 0

    var zoneValue = 0

    fun getOverallFriendlyStrength(): Int {
        return friendlyMeleeStrength*4/3 + friendlyNavalMeleeStrength + friendlyRangeStrength + friendlyNavalRangeStrength
    }

    fun getOverallEnemyStrength(): Int {
        return enemyMeleeStrength*4/3 + enemyNavalMeleeStrength + enemyRangeStrength + enemyNavalRangeStrength
    }

    fun isWater(): Boolean {
        return id.startsWith('-')
    }

    fun extend() {
        tileCount += 1
    }
}

class TacticalAnalysisMap {

    lateinit var game: GameInfo           // Current game
    lateinit var player: Civilization // Current player

    var lastUpdate: Int = -1

    val zones: ArrayList<TacticalDominanceZone> = ArrayList()
    val zoneIdToZoneIndex: HashMap<String, Int> = HashMap()
    val plotPositionToZoneId: HashMap<Vector2, String> = HashMap()

    companion object {
        const val maxRange = 4
        const val maxZoneSize = 30
    }

    fun reset(player: Civilization) {
        this.player = player
        this.game = player.gameInfo
        this.lastUpdate = -1

        this.zones.clear()
        this.zoneIdToZoneIndex.clear()
        this.plotPositionToZoneId.clear()
    }

    fun isUpToDate() : Boolean {
        if (lastUpdate == -1)
            return false
        if (player != game.currentPlayerCiv)
            return true
        return lastUpdate == game.turns
    }

    fun invalidate() {
        lastUpdate = -1
    }

    private fun refreshIfOutdated() {

        if (isUpToDate())
            return

        Log.debug("Refreshing Tactical Analysis Map...")

        // This is where creation and separation of zones occur
        createDominanceZones()
        establishZoneNeighborhood()

        // This is workaround for absent "Area" mechanics.
        // We glue small leftovers to bigger zones
        glueSmallZonesToBig()

        // TODO: calculateMilitaryStrength
        // TODO: prioritizeZones
        // TODO: updatePostures
    }

    fun getZoneByTile(tile: Tile): TacticalDominanceZone? {
        refreshIfOutdated()
        val zoneId = plotPositionToZoneId[tile.position]?: return null
        return getZoneById(zoneId)
    }

    fun getZoneById(id: String): TacticalDominanceZone? {
        refreshIfOutdated()
        val index = zoneIdToZoneIndex[id]
        if (index != null)
            return getZoneByIndex(index)
        return null
    }

    fun getZoneByIndex(index: Int): TacticalDominanceZone? {
        refreshIfOutdated()
        if (index < 0 || index >= zones.size)
            return null
        return zones[index]
    }

    fun createDominanceZones() {

        lastUpdate = game.turns
        zones.clear()
        zoneIdToZoneIndex.clear()
        plotPositionToZoneId.clear()

        val unknownZone = TacticalDominanceZone()

        zones.add(unknownZone)
        zoneIdToZoneIndex[unknownZone.id] = 0

        val nonCityTiles = ArrayList<Tile>()

        var zone: TacticalDominanceZone? = null

        for (tile in game.tileMap.values.asSequence()) {

            // Unexplored plot go into their own zone
            if (!player.hasExplored(tile)) {
                plotPositionToZoneId[tile.position] = unknownZone.id
                continue
            }

            // Is this plot close to a city?
            val cityDistance = game.cityDistances.getClosestCityDistance(tile, null, false)
            if (cityDistance == null) {
                // Non-city tiles processed separately
                nonCityTiles.add(tile)
                continue
            }

            val city: City? = when {
                cityDistance.distance < 3 -> cityDistance.city
                else -> tile.getCity()
            }

            if (city == null) {
                nonCityTiles.add(tile)
                continue
            }

            val zoneId = if (tile.isWater) "-${city.id}" else city.id

            // Chances are it's the same zone as before
            if (zone == null || zone.id != zoneId) {
                zone = getZoneById(zoneId)
                // Still not found? Create new
                if (zone == null) {

                    val newZone = TacticalDominanceZone()
                    newZone.id = zoneId
                    newZone.city = city
                    newZone.owner = city.civ
                    newZone.area = tile.getContinent()

                    if (newZone.owner == player)
                        newZone.territoryType = TacticalTerritoryType.FRIENDLY
                    else if (newZone.owner?.isAtWarWith(player) == true)
                        newZone.territoryType = TacticalTerritoryType.ENEMY
                    else
                        newZone.territoryType = TacticalTerritoryType.NEUTRAL

                    zoneIdToZoneIndex[zoneId] = zones.size
                    zones.add(newZone)

                    zone = zones.last()
                }
            }
            plotPositionToZoneId[tile.position] = zoneId
            zone.extend()
        }

        // Ensure that continents sizes are calculated
        game.tileMap.assignContinents(TileMap.AssignContinentsMode.Ensure)

        groupRemainingNonCityTiles(nonCityTiles)
    }

    private fun groupRemainingNonCityTiles(nonCityTiles: ArrayList<Tile>) {
        while (nonCityTiles.isNotEmpty()) {

            var count = maxZoneSize
            val stack: ArrayList<Tile> = ArrayList()
            stack.add(nonCityTiles.removeFirst())

            val randomId = UUID.randomUUID().toString()
            val newId = if (stack.last().isWater) "-$randomId" else randomId

            val newZone = TacticalDominanceZone()
            newZone.id = newId
            newZone.city = null
            newZone.area = stack.last().getContinent()
            newZone.territoryType = TacticalTerritoryType.NEUTRAL

            while (stack.isNotEmpty() || count > 0) {
                val tile = stack.removeLastOrNull() ?: break
                val tileContinentSize = tile.tileMap.continentSizes[tile.getContinent()] ?: Int.MAX_VALUE
                plotPositionToZoneId[tile.position] = newId
                newZone.extend()

                for (neighbor in tile.neighbors) {

                    // We don't want lakes and mountains to be separate zones - should attach them too
                    val isLake = neighbor.matchesTerrainFilter(Constants.lakes, multiFilter = false)
                    val isMountain = neighbor.matchesTerrainFilter(Constants.mountain, multiFilter = false)
                    val neighborContinentSize = neighbor.tileMap.continentSizes[neighbor.getContinent()] ?: Int.MAX_VALUE

                    val isSameZone = neighbor.getContinent() == tile.getContinent()
                        || isLake || (isMountain && neighbor.isLand)
                        || neighborContinentSize < 4 || tileContinentSize < 4

                    if (isSameZone && nonCityTiles.contains(neighbor) && count > 0) {
                        nonCityTiles.remove(neighbor)
                        stack.add(neighbor)
                        count -= 1
                    }
                }
            }

            zoneIdToZoneIndex[newZone.id] = zones.size
            zones.add(newZone)
        }
    }

    private fun glueSmallZonesToBig() {

        val toRemove = HashSet<TacticalDominanceZone>()

        for (zone in zones) {
            if (zone.tileCount < 5 && zone.city == null) {
                val biggerZone = zones.asSequence()
                    .filter { it.isWater() == zone.isWater() && it.neighboringZones.contains(zone.id) }
                    .firstOrNull()
                if (biggerZone != null) {
                    plotPositionToZoneId.asSequence()
                        .filter { it.value == zone.id }
                        .forEach { plotPositionToZoneId[it.key] = biggerZone.id }
                    toRemove.add(zone)
                }
            }
        }
        zones.removeAll(toRemove)
        zoneIdToZoneIndex.clear()

        for (i in 0 until zones.size) {
            val zoneId = zones[i].id
            zoneIdToZoneIndex[zoneId] = i
        }
    }

    private fun establishZoneNeighborhood() {

        for (zone in zones)
            zone.neighboringZones.clear()

        val tileMap = game.tileMap
        val directionsToCheck = setOf(12, 4, 8) // each tile only needs to check 3 directions for every possible neighboringZone to be identified

        for (tile in tileMap.values) {
            val zoneA = getZoneByTile(tile)
            val zoneAId = zoneA!!.id
            for (neighbor in directionsToCheck.mapNotNull { tileMap.getClockPositionNeighborTile(tile, it) }) {
                val zoneB = getZoneByTile(neighbor)!!
                val zoneBId = zoneB.id
                if (zoneAId != zoneBId) {
                    zoneA.neighboringZones.add(zoneB.id)
                    zoneB.neighboringZones.add(zoneA.id)
                }
            }
        }
    }

    fun debugOutput() {
        Log.debug("MYTAG: Total tactical zones: ${zones.size}")
        for (zone in zones) {
            Log.debug("MYTAG: Zone: ${zone.id} City: ${zone.city} Territory: ${zone.territoryType} Neighbors: ${zone.neighboringZones}")
        }
    }

}
