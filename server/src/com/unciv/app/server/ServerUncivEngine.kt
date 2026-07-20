package com.unciv.app.server

import com.badlogic.gdx.ApplicationListener
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.backends.headless.HeadlessApplication
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration
import com.badlogic.gdx.graphics.GL20
import com.unciv.UncivGame
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.RulesetCache
import kotlinx.serialization.Serializable
import org.mockito.Mockito
import java.io.File

/**
 * Runs Unciv core headlessly so the multiplayer server can apply unit actions to saves
 * (Evgeny's model: client sends a small action JSON; server mutates the canonical save).
 *
 * Working directory (or [assetsPath]) must contain `jsons/` and optionally `mods/`
 * — same layout as `android/assets`.
 */
internal object ServerUncivEngine {

    @Volatile private var initialized = false
    private var assetsPath: String = "."

    fun init(assetsPath: String) {
        this.assetsPath = assetsPath
        ensureInit()
    }

    @Synchronized
    fun ensureInit() {
        if (initialized) return

        if (Gdx.app == null) {
            val conf = HeadlessApplicationConfiguration()
            HeadlessApplication(object : ApplicationListener {
                override fun create() {}
                override fun resize(width: Int, height: Int) {}
                override fun render() {}
                override fun pause() {}
                override fun resume() {}
                override fun dispose() {}
            }, conf)
            Gdx.gl = Mockito.mock(GL20::class.java)
            Gdx.gl20 = Gdx.gl
        }

        // Relative FileHandle("jsons/...") / FileHandle("mods") resolve against user.dir
        val assetsDir = File(assetsPath).absoluteFile
        if (assetsDir.isDirectory) {
            System.setProperty("user.dir", assetsDir.path)
        }

        UncivGame.Current = UncivGame()
        UncivGame.Current.settings = GameSettings().apply {
            musicVolume = 0f
            soundEffectsVolume = 0f
            citySoundsVolume = 0f
            voicesVolume = 0f
        }

        val errors = RulesetCache.loadRulesets(consoleMode = true, noMods = false)
        if (errors.isNotEmpty()) {
            System.err.println("Ruleset load warnings: ${errors.take(5)}")
        }
        initialized = true
    }

    @Serializable
    data class UnitActionPayload(
        /** "move" or "attack" */
        val type: String,
        val unitId: Int,
        val fromX: Int,
        val fromY: Int,
        val toX: Int,
        val toY: Int,
    )

    /**
     * Apply [payload] to [rawSave], return gzipped new save or error message.
     */
    fun applyAction(rawSave: String, payload: UnitActionPayload): Pair<String?, String?> {
        ensureInit()
        return try {
            val game = UncivFiles.gameInfoFromString(rawSave)
            if (!game.gameParameters.serverAuthoritativeUnitActions) {
                return null to "Game does not have serverAuthoritativeUnitActions enabled"
            }

            val unit = findUnit(game, payload.unitId)
                ?: return null to "Unit ${payload.unitId} not found"
            val from = unit.currentTile.position
            if (from.x.toInt() != payload.fromX || from.y.toInt() != payload.fromY) {
                return null to "Unit ${payload.unitId} is at (${from.x.toInt()},${from.y.toInt()}), not (${payload.fromX},${payload.fromY})"
            }

            val toTile = game.tileMap[payload.toX, payload.toY]

            when (payload.type) {
                "move" -> {
                    try {
                        unit.movement.moveToTile(toTile)
                    } catch (ex: Exception) {
                        return null to "Move failed: ${ex.message}"
                    }
                    val newPos = unit.currentTile.position
                    if (newPos.x == from.x && newPos.y == from.y) {
                        return null to "Move had no effect"
                    }
                }
                "attack" -> {
                    val attackable = TargetHelper
                        .getAttackableEnemies(unit, unit.movement.getDistanceToTiles())
                        .firstOrNull { it.tileToAttack == toTile }
                        ?: return null to "No valid attack on (${payload.toX},${payload.toY})"
                    val attacker = MapUnitCombatant(unit)
                    if (!Battle.movePreparingAttack(attacker, attackable)) {
                        return null to "Cannot prepare attack"
                    }
                    Battle.attackOrNuke(attacker, attackable)
                }
                else -> return null to "Unknown action type '${payload.type}'"
            }

            val zipped = UncivFiles.gameInfoToString(game, forceZip = true, updateChecksum = true)
            zipped to null
        } catch (ex: Exception) {
            null to "Engine error: ${ex.message}"
        }
    }

    private fun findUnit(game: com.unciv.logic.GameInfo, unitId: Int): MapUnit? =
        game.civilizations.asSequence()
            .flatMap { it.units.getCivUnits() }
            .firstOrNull { it.id == unitId }

    /** Quick check without full engine: authoritative flag + same turn/player PUT ban. */
    fun isForbiddenMidTurnPut(oldRaw: String, newRaw: String): Boolean {
        return try {
            ensureInit()
            val oldGame = UncivFiles.gameInfoFromString(oldRaw)
            if (!oldGame.gameParameters.serverAuthoritativeUnitActions) return false
            val newGame = UncivFiles.gameInfoFromString(newRaw)
            oldGame.turns == newGame.turns && oldGame.currentPlayer == newGame.currentPlayer
        } catch (_: Exception) {
            false
        }
    }
}
