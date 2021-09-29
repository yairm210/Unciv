package com.unciv.logic

import com.badlogic.gdx.math.Vector2
import com.unciv.Constants
import com.unciv.logic.civilization.NotificationIcon
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.metadata.GameSpeed
import java.util.*
import kotlin.math.min
import kotlin.math.pow

class BarbarianManager {
    val camps = ArrayList<Encampment>()

    @Transient
    lateinit var gameInfo: GameInfo

    @Transient
    lateinit var tileMap: TileMap

    fun clone(): BarbarianManager {
        val toReturn = BarbarianManager()
        toReturn.camps.addAll(camps.map { it.clone() } )
        return toReturn
    }

    fun setTransients(gameInfo: GameInfo) {
        this.gameInfo = gameInfo
        this.tileMap = gameInfo.tileMap

        for (camp in camps)
            camp.gameInfo = gameInfo
    }

    fun updateEncampments() {
        // Check if camps were destroyed
        for (camp in camps.toList()) {
            if (tileMap[camp.position].improvement != Constants.barbarianEncampment) {
                camps.remove(camp)
            }
        }

        // Possibly place a new encampment
        placeBarbarianEncampment()

        // Update all existing camps
        for (camp in camps) {
            camp.update()
        }
    }

    /** Called when an encampment was attacked, will speed up time to next spawn */
    fun campAttacked(position: Vector2) {
        camps.firstOrNull { it.position == position }?.wasAttacked()
    }

    fun placeBarbarianEncampment() {
        // Before we do the expensive stuff, do a roll to see if we will place a camp at all
        if (gameInfo.turns > 1 && Random().nextBoolean())
            return

        // Barbarians will only spawn in places that no one can see
        val allViewableTiles = gameInfo.civilizations.filterNot { it.isBarbarian() || it.isSpectator() }
            .flatMap { it.viewableTiles }.toHashSet()
        val fogTiles = tileMap.values.filter { it.isLand && it !in allViewableTiles }

        val fogTilesPerCamp = (tileMap.values.size.toFloat().pow(0.4f)).toInt() // Approximately

        // Check if we have more room
        var campsToAdd = (fogTiles.size / fogTilesPerCamp) - camps.size

        // First turn of the game add 1/3 of all possible camps
        if (gameInfo.turns == 1) {
            campsToAdd /= 3
        } else if (campsToAdd > 0)
            campsToAdd = 1

        if (campsToAdd <= 0) return

        // Camps can't spawn within 7 tiles of each other or within 4 tiles of major civ capitals
        val tooCloseToCapitals = gameInfo.civilizations.filterNot { it.isBarbarian() || it.isSpectator() || it.cities.isEmpty() || it.isCityState() }
            .flatMap { it.getCapital().getCenterTile().getTilesInDistance(4) }.toSet()
        val tooCloseToCamps = camps
            .flatMap { tileMap[it.position].getTilesInDistance(7) }.toSet()

        val viableTiles = fogTiles.filter {
            !it.isImpassible()
                    && it.resource == null
                    && it.terrainFeatures.none { feature -> gameInfo.ruleSet.terrains[feature]!!.hasUnique("No Improvements can be built here") }
                    && it.neighbors.any { neighbor -> neighbor.isLand }
                    && it !in tooCloseToCapitals
                    && it !in tooCloseToCamps
        }.toMutableList()

        var tile: TileInfo?
        var addedCamps = 0
        var biasCoast = Random().nextInt(6) == 0

        // Add the camps
        while (addedCamps < campsToAdd) {
            if (viableTiles.isEmpty())
                break

            // If we're biasing for coast, get a coast tile if possible
            if (biasCoast) {
                tile = viableTiles.filter { it.isCoastalTile() }.randomOrNull()
                if (tile == null)
                    tile = viableTiles.random()
            } else
                tile = viableTiles.random()
            
            tile.improvement = Constants.barbarianEncampment
            val newCamp = Encampment()
            newCamp.position = tile.position
            newCamp.gameInfo = gameInfo
            camps.add(newCamp)
            notifyCivsOfBarbarianEncampment(tile)
            addedCamps++

            // Still more camps to add?
            if (addedCamps < campsToAdd) {
                // Remove some newly non-viable tiles
                viableTiles.removeAll( tile.getTilesInDistance(7) )
                // Reroll bias
                biasCoast = Random().nextInt(6) == 0
            }
        }
    }

    /**
     * [CivilizationInfo.addNotification][Add a notification] to every civilization that have
     * adopted Honor policy and have explored the [tile] where the Barbarian Encampment has spawned.
     */
    private fun notifyCivsOfBarbarianEncampment(tile: TileInfo) {
        gameInfo.civilizations.filter {
            it.hasUnique("Notified of new Barbarian encampments")
                    && it.exploredTiles.contains(tile.position)
        }
            .forEach { it.addNotification("A new barbarian encampment has spawned!", tile.position, NotificationIcon.War) }
    }
}

class Encampment {
    var countdown = 0
    var spawnedUnits = -1
    lateinit var position: Vector2

    @Transient
    lateinit var gameInfo: GameInfo

    fun clone(): Encampment {
        val toReturn = Encampment()
        toReturn.position = position
        toReturn.countdown = countdown
        toReturn.spawnedUnits = spawnedUnits
        return toReturn
    }

    fun update() {
        if (countdown <= 0) {
            // Try spawning a unit
            if (spawnBarbarian()) {
                // Successful
                spawnedUnits++
                resetCountdown()
            }
        } else
            countdown--
    }

    fun wasAttacked() {
        countdown /= 2
    }

    /** Attempts to spawn a Barbarian from this encampment. Returns true if a unit was spawned. */
    private fun spawnBarbarian(): Boolean {
        val barbarianCiv = gameInfo.getBarbarianCivilization()
        val tile = gameInfo.tileMap[position]

        // Empty camp - spawn a defender
        if (tile.militaryUnit == null) {
            val spawnedUnit = tile.tileMap.placeUnitNearTile(tile.position, chooseBarbarianUnit(naval = false), barbarianCiv)
            return (spawnedUnit != null)
        }

        // Don't spawn wandering barbs too early
        if (gameInfo.turns < 10)
            return false

        // Too many barbarians around already?
        if (tile.getTilesInDistance(4).count { it.militaryUnit?.civInfo == barbarianCiv } > 2)
            return false

        val canSpawnBoats = gameInfo.turns > 30
        val validTiles = tile.neighbors.toList().filterNot {
            it.isImpassible()
                    || it.isCityCenter()
                    || it.getFirstUnit() != null
                    || (it.isWater && !canSpawnBoats)
                    || (it.hasUnique("Fresh water") && it.isWater) // No Lakes
        }
        if (validTiles.isEmpty()) return false
        val spawnTile = validTiles.random()

        val spawnedUnit = gameInfo.tileMap.placeUnitNearTile(spawnTile.position, chooseBarbarianUnit(spawnTile.isWater), barbarianCiv)
        return (spawnedUnit != null)
    }

    private fun chooseBarbarianUnit(naval: Boolean): String {
        // if we don't make this into a separate list then the retain() will happen on the Tech keys,
        // which effectively removes those techs from the game and causes all sorts of problems
        val allResearchedTechs = gameInfo.ruleSet.technologies.keys.toMutableList()
        for (civ in gameInfo.civilizations.filter { !it.isBarbarian() && !it.isDefeated() }) {
            allResearchedTechs.retainAll(civ.tech.techsResearched)
        }
        val barbarianCiv = gameInfo.getBarbarianCivilization()
        barbarianCiv.tech.techsResearched = allResearchedTechs.toHashSet()
        val unitList = gameInfo.ruleSet.units.values
            .filter { it.isMilitary() }
            .filter { it.isBuildable(barbarianCiv) }

        var unit = if (naval)
            unitList.filter { it.isWaterUnit() }.randomOrNull()
        else
            unitList.filter { it.isLandUnit() }.randomOrNull()

        if (unit == null) // Didn't find a unit for preferred domain
            unit = unitList.random() // Pick another, or crash the game vOv

        return unit.name
    }

    /** When a barbarian is spawned, seed the counter for next spawn */
    private fun resetCountdown() {
        // Base 8-12 turns
        countdown = 8 + Random().nextInt(5)
        // Quicker on Raging Barbarians
        if (gameInfo.gameParameters.ragingBarbarians)
            countdown /= 2
        // Higher on low difficulties
        countdown += gameInfo.ruleSet.difficulties[gameInfo.gameParameters.difficulty]!!.barbarianSpawnDelay
        // Quicker if this camp has already spawned units
        countdown -= min(3, spawnedUnits)

        countdown *= 100
        countdown /= when (gameInfo.gameParameters.gameSpeed) {
            GameSpeed.Quick -> 67
            GameSpeed.Standard -> 100
            GameSpeed.Epic -> 150
            GameSpeed.Marathon -> 400 // sic!
        }
    }
}