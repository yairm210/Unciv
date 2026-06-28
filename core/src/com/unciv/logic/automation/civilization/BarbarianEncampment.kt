package com.unciv.logic.automation.civilization

import com.unciv.logic.GameInfo
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.map.HexCoord

internal class BarbarianEncampment() : IsPartOfGameInfoSerialization {
    var position = HexCoord()
    var countdown = 0
    var spawnedUnits = -1
    var destroyed = false // destroyed encampments haunt the vicinity for 15 turns preventing new spawns

    @Transient
    lateinit var gameInfo: GameInfo

    constructor(position: HexCoord): this() {
        this.position = position
    }

    fun clone(): BarbarianEncampment {
        val toReturn = BarbarianEncampment(position)
        toReturn.countdown = countdown
        toReturn.spawnedUnits = spawnedUnits
        toReturn.destroyed = destroyed
        return toReturn
    }

    fun update() {
        if (countdown > 0) // Not yet
            countdown--
        // Countdown at 0, try to spawn a barbarian
        else if (!destroyed && gameInfo.barbarians.spawnBarbarian(gameInfo.tileMap[position]) != null) { 
            // Successful
            spawnedUnits++
            resetCountdown()
        }
    }

    fun wasAttacked() {
        if (!destroyed)
            countdown /= 2
    }

    fun wasDestroyed() {
        if (!destroyed) {
            countdown = 15
            destroyed = true
        }
    }

    /** When a barbarian is spawned, seed the counter for next spawn */
    private fun resetCountdown() {
        val rng = gameInfo.getBarbarianCivilization().state.stateBasedRandom("BarbarianManager.resetCooldown")
        // Base 8-12 turns
        countdown = 8 + rng.nextInt(5)
        // Quicker on Raging Barbarians
        if (gameInfo.gameParameters.ragingBarbarians)
            countdown /= 2
        // Higher on low difficulties
        countdown += gameInfo.ruleset.difficulties[gameInfo.gameParameters.difficulty]!!.barbarianSpawnDelay
        // Quicker if this camp has already spawned units
        countdown -= spawnedUnits.coerceAtMost(3)

        countdown = (countdown * gameInfo.speed.barbarianModifier).toInt()
    }
}
