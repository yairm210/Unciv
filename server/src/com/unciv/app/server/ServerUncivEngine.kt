package com.unciv.app.server

import com.unciv.UncivGame
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.managers.ReligionState
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.multiplayer.AuthoritativeActionPayload
import com.unciv.logic.trade.TradeLogic
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.RulesetCache
import java.io.File

/**
 * Applies authoritative multiplayer actions to saves using Unciv core logic.
 *
 * No Gdx [com.badlogic.gdx.backends.headless.HeadlessApplication]: saves and rulesets load via
 * [FileHandle] / console-mode paths (see [UncivFiles.getSettingsForPlatformLaunchers]), and action
 * apply does not need graphics, audio, net, or the headless render thread.
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

        val assetsDir = File(assetsPath).absoluteFile
        if (assetsDir.isDirectory) {
            System.setProperty("user.dir", assetsDir.path)
        }

        // Console mode: RulesetCache uses FileHandle("jsons/...") relative to user.dir, not Gdx.files.
        UncivGame.Current = UncivGame(isConsoleMode = true)
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

    private val unitActionTypes = setOf("move", "attack", "foundCity")

    /**
     * Apply [payload] to [rawSave], return gzipped new save or error message.
     */
    fun applyAction(rawSave: String, payload: AuthoritativeActionPayload): Pair<String?, String?> {
        ensureInit()
        return try {
            val game = UncivFiles.gameInfoFromString(rawSave)
            if (!game.gameParameters.serverAuthoritativeUnitActions) {
                return null to "Game does not have serverAuthoritativeUnitActions enabled"
            }

            val error = if (payload.type in unitActionTypes)
                applyUnitAction(game, payload)
            else
                applyMidTurnAction(game, payload)
            if (error != null) return null to error

            val zipped = UncivFiles.gameInfoToString(game, forceZip = true, updateChecksum = true)
            zipped to null
        } catch (ex: Exception) {
            null to "Engine error: ${ex.message}"
        }
    }

    private fun applyUnitAction(game: com.unciv.logic.GameInfo, payload: AuthoritativeActionPayload): String? {
        val unit = findUnit(game, payload.unitId)
            ?: return "Unit ${payload.unitId} not found"
        val from = unit.currentTile.position
        if (from.x != payload.fromX || from.y != payload.fromY) {
            return "Unit ${payload.unitId} is at (${from.x},${from.y}), not (${payload.fromX},${payload.fromY})"
        }
        val toTile = game.tileMap[payload.toX, payload.toY]

        when (payload.type) {
            "move" -> {
                try {
                    if (!unit.movement.canReach(toTile)) {
                        return "Cannot reach (${payload.toX},${payload.toY})"
                    }
                    val previousTile = unit.currentTile
                    unit.movement.headTowards(toTile)
                    if (unit.currentTile == previousTile) {
                        return "Move had no effect"
                    }
                    if (unit.isExploring() || unit.isMoving())
                        unit.action = null
                    if (unit.currentTile != toTile) {
                        unit.action = "moveTo ${toTile.position.x},${toTile.position.y}"
                    }
                } catch (ex: Exception) {
                    return "Move failed: ${ex.message}"
                }
            }
            "attack" -> {
                val attackable = TargetHelper
                    .getAttackableEnemies(unit, unit.movement.getDistanceToTiles())
                    .firstOrNull { it.tileToAttack == toTile }
                    ?: return "No valid attack on (${payload.toX},${payload.toY})"
                val attacker = MapUnitCombatant(unit)
                if (!Battle.movePreparingAttack(attacker, attackable)) {
                    return "Cannot prepare attack"
                }
                Battle.attackOrNuke(attacker, attackable)
            }
            "foundCity" -> {
                if (!unit.baseUnit.isCityFounder()) {
                    return "Unit ${payload.unitId} cannot found a city"
                }
                if (from.x != payload.toX || from.y != payload.toY) {
                    return "foundCity to-tile must be the settler's tile"
                }
                if (!unit.hasMovement() || !unit.currentTile.canBeSettled(unit.civ)) {
                    return "Cannot found city here"
                }
                try {
                    unit.civ.addCity(unit.currentTile.position, unit)
                    unit.destroy()
                } catch (ex: Exception) {
                    return "Found city failed: ${ex.message}"
                }
            }
            else -> return "Unknown unit action '${payload.type}'"
        }
        return null
    }

    private fun applyMidTurnAction(game: com.unciv.logic.GameInfo, payload: AuthoritativeActionPayload): String? {
        val civ = game.getCivilization(game.currentPlayer)
        when (payload.type) {
            "dismissAlert" -> {
                val typeName = payload.alertType ?: return "dismissAlert requires alertType"
                val alertType = try {
                    AlertType.valueOf(typeName)
                } catch (_: Exception) {
                    return "Unknown alertType '$typeName'"
                }
                val value = payload.alertValue.orEmpty()
                civ.popupAlerts.removeAll { it.type == alertType && it.value == value }
            }
            "acceptTrade" -> {
                val requesting = payload.requestingCiv ?: return "acceptTrade requires requestingCiv"
                val request = civ.tradeRequests.firstOrNull { it.requestingCiv == requesting }
                    ?: return "Trade request from $requesting not found"
                val other = game.getCivilization(request.requestingCiv)
                val tradeLogic = TradeLogic(civ, other)
                tradeLogic.currentTrade.set(request.trade)
                tradeLogic.acceptTrade()
                civ.tradeRequests.remove(request)
            }
            "declineTrade" -> {
                val requesting = payload.requestingCiv ?: return "declineTrade requires requestingCiv"
                val request = civ.tradeRequests.firstOrNull { it.requestingCiv == requesting }
                    ?: return "Trade request from $requesting not found"
                request.decline(civ)
                civ.tradeRequests.remove(request)
            }
            "dismissTrade" -> {
                val requesting = payload.requestingCiv ?: return "dismissTrade requires requestingCiv"
                civ.tradeRequests.removeAll { it.requestingCiv == requesting }
            }
            "setProduction" -> {
                val cityId = payload.cityId ?: return "setProduction requires cityId"
                val queue = payload.constructionQueue ?: return "setProduction requires constructionQueue"
                val city = civ.cities.firstOrNull { it.id == cityId }
                    ?: return "City $cityId not found"
                city.cityConstructions.constructionQueue.clear()
                city.cityConstructions.constructionQueue.addAll(queue)
                city.cityConstructions.currentConstructionIsUserSet = payload.currentConstructionIsUserSet
            }
            "chooseBeliefs" -> {
                val beliefNames = payload.beliefNames ?: return "chooseBeliefs requires beliefNames"
                if (beliefNames.isEmpty()) return "chooseBeliefs requires at least one belief"
                val religionName = payload.religionName
                val religionDisplayName = payload.religionDisplayName
                if (religionName != null && religionDisplayName != null
                    && civ.religionManager.religionState == ReligionState.FoundingReligion
                ) {
                    try {
                        civ.religionManager.foundReligion(religionDisplayName, religionName)
                    } catch (ex: Exception) {
                        return "Found religion failed: ${ex.message}"
                    }
                }
                val beliefs = ArrayList<com.unciv.models.ruleset.Belief>(beliefNames.size)
                for (name in beliefNames) {
                    val belief = game.ruleset.beliefs[name]
                        ?: return "Unknown belief '$name'"
                    beliefs.add(belief)
                }
                try {
                    civ.religionManager.chooseBeliefs(beliefs, useFreeBeliefs = payload.useFreeBeliefs)
                } catch (ex: Exception) {
                    return "chooseBeliefs failed: ${ex.message}"
                }
            }
            "adoptPolicy" -> {
                val policyName = payload.policyName ?: return "adoptPolicy requires policyName"
                val policy = game.ruleset.policies[policyName]
                    ?: return "Unknown policy '$policyName'"
                try {
                    civ.policies.adopt(policy, branchCompletion = payload.branchCompletion)
                } catch (ex: Exception) {
                    return "adoptPolicy failed: ${ex.message}"
                }
            }
            "chooseGreatPerson" -> {
                val unitName = payload.greatPersonUnitName
                    ?: return "chooseGreatPerson requires greatPersonUnitName"
                if (civ.greatPeople.freeGreatPeople <= 0)
                    return "No free great person to choose"
                val unit = game.ruleset.units[unitName]
                    ?: return "Unknown unit '$unitName'"
                if (payload.mayaLimited && unitName !in civ.greatPeople.longCountGPPool)
                    return "Unit '$unitName' not in Maya long-count pool"
                val capital = civ.getCapital()
                    ?: return "No capital to place great person"
                try {
                    civ.units.addUnit(unit, capital)
                    civ.greatPeople.freeGreatPeople--
                    if (payload.mayaLimited) {
                        civ.greatPeople.mayaLimitedFreeGP--
                        civ.greatPeople.longCountGPPool.remove(unitName)
                    }
                } catch (ex: Exception) {
                    return "chooseGreatPerson failed: ${ex.message}"
                }
            }
            else -> return "Unknown action type '${payload.type}'"
        }
        return null
    }

    private fun findUnit(game: com.unciv.logic.GameInfo, unitId: Int): MapUnit? =
        game.civilizations.asSequence()
            .flatMap { it.units.getCivUnits() }
            .firstOrNull { it.id == unitId }

    /**
     * Mid-turn PUT is allowed for non-movement state. Rejected only when an existing unit's tile
     * changed without going through POST /action.
     */
    fun isForbiddenMidTurnPut(oldRaw: String, newRaw: String): Boolean {
        return try {
            ensureInit()
            val oldGame = UncivFiles.gameInfoFromString(oldRaw)
            if (!oldGame.gameParameters.serverAuthoritativeUnitActions) return false
            val newGame = UncivFiles.gameInfoFromString(newRaw)
            if (oldGame.turns != newGame.turns || oldGame.currentPlayer != newGame.currentPlayer)
                return false
            hasIllicitUnitRelocations(oldGame, newGame)
        } catch (_: Exception) {
            false
        }
    }

    private fun hasIllicitUnitRelocations(
        oldGame: com.unciv.logic.GameInfo,
        newGame: com.unciv.logic.GameInfo,
    ): Boolean {
        val oldPositions = HashMap<Int, Pair<Int, Int>>()
        for (civ in oldGame.civilizations) {
            for (unit in civ.units.getCivUnits()) {
                val pos = unit.currentTile.position
                oldPositions[unit.id] = pos.x to pos.y
            }
        }
        for (civ in newGame.civilizations) {
            for (unit in civ.units.getCivUnits()) {
                val oldPos = oldPositions[unit.id] ?: continue
                val pos = unit.currentTile.position
                if (oldPos.first != pos.x || oldPos.second != pos.y) return true
            }
        }
        return false
    }
}
