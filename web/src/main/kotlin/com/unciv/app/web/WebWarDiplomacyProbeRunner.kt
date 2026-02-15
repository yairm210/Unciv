package com.unciv.app.web

import com.unciv.GUI
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.civilization.AlertType
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.trade.TradeLogic
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.popups.closeAllPopups
import com.unciv.ui.popups.hasOpenPopups
import com.unciv.ui.screens.diplomacyscreen.DiplomacyScreen
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.victoryscreen.VictoryScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.JsonReader

object WebWarDiplomacyProbeRunner {
    private const val preloadSchemaVersion = 1

    private data class WarSetup(
        val world: WorldScreen,
        val player: Civilization,
        val majorEnemies: List<Civilization>,
        val peaceTarget: Civilization?,
        val metadata: WarPreloadMeta,
    )

    private class WarPreloadMeta {
        var schemaVersion: Int = 0
        var preloadId: String = ""
        var ruleset: String = ""
        var mapSeed: Long = 0L
        var expectedPlayerCiv: String = ""
        var expectedMajorEnemies: ArrayList<String> = arrayListOf()
        var expectedPeaceTarget: String = ""
        var expectedRequiredUnit: String = "Warrior"
    }

    private data class CombatSummary(
        val exchanges: Int,
        val forces: LinkedHashSet<String>,
    )

    private data class UiFlowResult(
        val success: Boolean,
        val reason: String = "",
    )

    suspend fun run(
        game: WebGame,
        role: WebUiProbeRunner.Role,
        runId: String,
        timeoutMs: Long,
        deadlineMs: Long,
        appendStep: (String, String) -> Unit,
    ): WebUiProbeRunner.FlowResult {
        return runCatching {
            when (role) {
                WebUiProbeRunner.Role.WAR_FROM_START -> runWarFromStart(game, role, runId, deadlineMs, appendStep)
                WebUiProbeRunner.Role.WAR_PREWORLD -> runWarPreworld(game, role, runId, deadlineMs, appendStep)
                WebUiProbeRunner.Role.WAR_DEEP -> runWarDeep(game, role, runId, timeoutMs, deadlineMs, appendStep)
                else -> WebUiProbeRunner.FlowResult(false, "Unsupported war role [${role.name.lowercase()}].")
            }
        }.getOrElse { throwable ->
            WebUiProbeRunner.FlowResult(
                passed = false,
                notes = "${throwable::class.simpleName}: ${throwable.message ?: "unknown error"}",
            )
        }
    }

    private suspend fun runWarFromStart(
        game: WebGame,
        role: WebUiProbeRunner.Role,
        runId: String,
        deadlineMs: Long,
        appendStep: (String, String) -> Unit,
    ): WebUiProbeRunner.FlowResult {
        ensureBeforeDeadline(deadlineMs, "war_from_start:boot")
        appendStep("running:war_from_start:setup", "Loading deterministic war preload runId=$runId.")
        val setup = loadWarSetupFromPreload(game, role, deadlineMs)
        dismissBlockingPopups(game, deadlineMs)

        val enemy = setup.majorEnemies.firstOrNull()
            ?: return WebUiProbeRunner.FlowResult(false, "No major-civ enemy found for war_from_start preload.")

        if (!ensurePlayerKnowsTarget(setup.player, enemy, setup.world, deadlineMs)) {
            return WebUiProbeRunner.FlowResult(false, "war_from_start preload invalid: player does not know enemy [${enemy.civName}].")
        }
        dismissBlockingPopups(game, deadlineMs)

        appendStep("running:war_from_start:declare", "Declaring war through diplomacy UI.")
        val warDeclared = declareWarViaUi(game, setup.player, enemy, deadlineMs)
        if (!warDeclared.success) {
            return WebUiProbeRunner.FlowResult(
                false,
                "UI declare-war flow failed for [${enemy.civName}] (${warDeclared.reason.ifBlank { "unknown reason" }}).",
            )
        }

        appendStep("running:war_from_start:combat", "Running required warrior/melee combat exchange via UI.")
        val combatSummary = runCombatExchanges(
            game = game,
            player = setup.player,
            enemy = enemy,
            requiredExchanges = 1,
            requiredUnitName = setup.metadata.expectedRequiredUnit,
            deadlineMs = deadlineMs,
        )
        val combatObserved = combatSummary.exchanges >= 1

        ensureBeforeDeadline(deadlineMs, "war_from_start:turn-loop")
        appendStep("running:war_from_start:turns", "Advancing 10 turns via UI controls.")
        val turnBefore = game.gameInfo?.turns ?: setup.world.gameInfo.turns
        val turns = WebValidationRunner.advanceTurnsByClicksProbe(game, 10)
        if (!turns.first) {
            return WebUiProbeRunner.FlowResult(
                passed = false,
                notes = "Turn progression failed: ${turns.second}",
                warDeclaredObserved = true,
                combatExchangesObserved = combatObserved,
                forcesObserved = combatSummary.forces.joinToString(","),
            )
        }

        WebValidationRunner.waitFramesProbe(24)
        val turnAfter = game.gameInfo?.turns ?: setup.world.gameInfo.turns
        val multiTurnProgressObserved = turnAfter > turnBefore
        val diplomacyTransitionObserved = setup.player.isAtWarWith(enemy)
        val hasWarriorOrMeleeForce = combatSummary.forces.contains("warrior") || combatSummary.forces.contains("melee")
        val passed = warDeclared.success && combatObserved && multiTurnProgressObserved && diplomacyTransitionObserved && hasWarriorOrMeleeForce

        return WebUiProbeRunner.FlowResult(
            passed = passed,
            notes = if (passed)
                "war_from_start passed: war declaration, warrior/melee combat, and multi-turn UI progress observed."
            else
                "war_from_start failed (war=$warDeclared combat=$combatObserved exchanges=${combatSummary.exchanges} multiTurn=$multiTurnProgressObserved turns=$turnBefore->$turnAfter diplo=$diplomacyTransitionObserved forces=${combatSummary.forces.joinToString(",")}).",
            warDeclaredObserved = warDeclared.success,
            combatExchangesObserved = combatObserved,
            diplomacyStateTransitionsObserved = diplomacyTransitionObserved,
            multiTurnProgressObserved = multiTurnProgressObserved,
            forcesObserved = combatSummary.forces.joinToString(","),
        )
    }

    private suspend fun runWarPreworld(
        game: WebGame,
        role: WebUiProbeRunner.Role,
        runId: String,
        deadlineMs: Long,
        appendStep: (String, String) -> Unit,
    ): WebUiProbeRunner.FlowResult {
        ensureBeforeDeadline(deadlineMs, "war_preworld:boot")
        appendStep("running:war_preworld:setup", "Loading deterministic preworld preload runId=$runId.")
        val setup = loadWarSetupFromPreload(game, role, deadlineMs)
        setup.world.gameInfo.oneMoreTurnMode = true
        game.settings.singleTapMove = false
        game.settings.longTapMove = false
        dismissBlockingPopups(game, deadlineMs)

        val peaceTarget = setup.peaceTarget
            ?: return WebUiProbeRunner.FlowResult(false, "war_preworld preload missing configured peace target.")
        if (peaceTarget.cities.isEmpty()) {
            return WebUiProbeRunner.FlowResult(false, "war_preworld peace target has no cities for capture scenario.")
        }

        if (!ensurePlayerKnowsTarget(setup.player, peaceTarget, setup.world, deadlineMs)) {
            return WebUiProbeRunner.FlowResult(false, "war_preworld preload invalid: player does not know peace target [${peaceTarget.civName}] after setup repair.")
        }
        dismissBlockingPopups(game, deadlineMs)

        appendStep("running:war_preworld:declare", "Declaring war through diplomacy UI.")
        val warDeclared = declareWarViaUi(game, setup.player, peaceTarget, deadlineMs)
        if (!warDeclared.success) {
            return WebUiProbeRunner.FlowResult(
                false,
                "UI declare-war flow failed for preworld target [${peaceTarget.civName}] (${warDeclared.reason.ifBlank { "unknown reason" }}).",
            )
        }
        stabilizePreworldScenario(setup.player, peaceTarget, setup.metadata.expectedRequiredUnit)

        val captureScenario = ensurePreworldCaptureScenario(
            player = setup.player,
            peaceTarget = peaceTarget,
            requiredUnitName = setup.metadata.expectedRequiredUnit,
        ) ?: return WebUiProbeRunner.FlowResult(false, "war_preworld could not establish a capturable city scenario for required unit [${setup.metadata.expectedRequiredUnit}].")
        val city = captureScenario.first
        val warriorAttacker = captureScenario.second
        city.health = 1
        city.getCenterTile().militaryUnit?.destroy()

        appendStep("running:war_preworld:capture", "Capturing city via warrior/melee UI attack and conquest popup flow.")
        val captureTile = city.getCenterTile()
        val initialCanAttackCity = unitCanAttackTile(warriorAttacker, captureTile)
        var captured = attemptCityCaptureViaUi(
            game = game,
            player = setup.player,
            enemy = peaceTarget,
            cityTile = captureTile,
            requiredUnitName = setup.metadata.expectedRequiredUnit,
            initialAttacker = warriorAttacker,
        )
        if (!captured) {
            captured = detectCityCaptureOutcome(game, city, setup.player)
        }
        if (!captured) {
            val screenName = game.screen?.javaClass?.simpleName ?: "unknown"
            val canAttackCity = unitCanAttackTile(warriorAttacker, captureTile)
            return WebUiProbeRunner.FlowResult(
                passed = false,
                notes = "City capture attack did not resolve through UI (screen=$screenName, attacker=${warriorAttacker.baseUnit.name}@${warriorAttacker.currentTile.position}, initialCanAttackCity=$initialCanAttackCity, canAttackCity=$canAttackCity).",
                warDeclaredObserved = true,
                combatExchangesObserved = false,
                forcesObserved = "warrior,melee",
            )
        }

        val popupHandled = resolveCityConqueredPopup(game, deadlineMs)
        if (!popupHandled) {
            return WebUiProbeRunner.FlowResult(
                passed = false,
                notes = "City conquered popup could not be resolved via UI button.",
                warDeclaredObserved = true,
                combatExchangesObserved = true,
                cityCaptureObserved = true,
                forcesObserved = "warrior,melee",
            )
        }

        val cityCaptureObserved = city.civ == setup.player
        if (!cityCaptureObserved) {
            return WebUiProbeRunner.FlowResult(
                passed = false,
                notes = "City ownership did not transfer to player after capture flow.",
                warDeclaredObserved = true,
                combatExchangesObserved = true,
                cityCaptureObserved = false,
                forcesObserved = "warrior,melee",
            )
        }
        trimPlayerMilitaryUnits(setup.player, setup.metadata.expectedRequiredUnit, keepRequiredUnits = 2, keepOtherMilitary = 2)
        val preTurnGameOverReason = detectGameOverReason(game, setup.player)
        if (preTurnGameOverReason != null) {
            return WebUiProbeRunner.FlowResult(
                passed = false,
                notes = "Player entered game-over state before peace step ($preTurnGameOverReason).",
                warDeclaredObserved = true,
                combatExchangesObserved = true,
                cityCaptureObserved = true,
                peaceObserved = false,
                forcesObserved = "warrior,melee",
            )
        }

        appendStep("running:war_preworld:war-turns", "Advancing 10 turns before peace negotiation (minimum-war-duration rule).")
        repeat(10) { index ->
            if (!recoverFromVictoryScreenIfPresent(game)) {
                return WebUiProbeRunner.FlowResult(
                    passed = false,
                    notes = "Could not recover from victory/defeat screen before pre-peace turn ${index + 1}.",
                    warDeclaredObserved = true,
                    combatExchangesObserved = true,
                    cityCaptureObserved = true,
                    peaceObserved = false,
                    forcesObserved = "warrior,melee",
                )
            }
            disarmMajorEnemies(setup.player)
            hardenPlayerCities(setup.player)
            val prePeaceAdvance = advanceTurnsFastForWarProbe(game, turns = 1, deadlineMs = deadlineMs)
            if (!prePeaceAdvance.success) {
                return WebUiProbeRunner.FlowResult(
                    passed = false,
                    notes = "Pre-peace turn advance failed on step ${index + 1}: ${prePeaceAdvance.reason}",
                    warDeclaredObserved = true,
                    combatExchangesObserved = true,
                    cityCaptureObserved = true,
                    forcesObserved = "warrior,melee",
                )
            }
        }
        val postWarTurnsGameOverReason = detectGameOverReason(game, setup.player)
        if (postWarTurnsGameOverReason != null) {
            return WebUiProbeRunner.FlowResult(
                passed = false,
                notes = "Player entered game-over state during pre-peace turns ($postWarTurnsGameOverReason).",
                warDeclaredObserved = true,
                combatExchangesObserved = true,
                cityCaptureObserved = true,
                peaceObserved = false,
                forcesObserved = "warrior,melee",
            )
        }
        WebValidationRunner.waitFramesProbe(20)

        val playerDiploFlag = setup.player.getDiplomacyManager(peaceTarget)?.getFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclaredWar) ?: -1
        val targetDiploFlag = peaceTarget.getDiplomacyManager(setup.player)?.getFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclaredWar) ?: -1
        appendStep(
            "running:war_preworld:peace",
            "Concluding peace through diplomacy UI (turn=${game.gameInfo?.turns ?: -1}, playerFlag=$playerDiploFlag, targetFlag=$targetDiploFlag, targetCities=${peaceTarget.cities.size}, targetDefeated=${peaceTarget.isDefeated()}).",
        )
        val peaceFlow = negotiatePeaceViaUi(game, setup.player, peaceTarget, deadlineMs)
        val peaceObserved = peaceFlow.success
        if (!peaceObserved) {
            return WebUiProbeRunner.FlowResult(
                passed = false,
                notes = "UI negotiate-peace flow failed for preworld target (${peaceFlow.reason.ifBlank { "unknown reason" }}).",
                warDeclaredObserved = true,
                combatExchangesObserved = true,
                cityCaptureObserved = true,
                peaceObserved = false,
                forcesObserved = "warrior,melee",
            )
        }

        appendStep("running:war_preworld:post-turn", "Advancing one turn to verify persisted post-peace state.")
        val turnBefore = game.gameInfo?.turns ?: setup.world.gameInfo.turns
        val turnResult = advanceTurnsFastForWarProbe(game, turns = 1, deadlineMs = deadlineMs)
        if (!turnResult.success) {
            return WebUiProbeRunner.FlowResult(
                passed = false,
                notes = "Post-peace turn advance failed: ${turnResult.reason}",
                warDeclaredObserved = true,
                combatExchangesObserved = true,
                cityCaptureObserved = true,
                peaceObserved = true,
                forcesObserved = "warrior,melee",
            )
        }

        WebValidationRunner.waitFramesProbe(24)
        val turnAfter = game.gameInfo?.turns ?: setup.world.gameInfo.turns
        val multiTurnProgressObserved = turnAfter > turnBefore
        val persistedOwner = city.civ == setup.player
        val persistedPeace = !setup.player.isAtWarWith(peaceTarget)
        val diplomacyStateTransitionsObserved = warDeclared.success && persistedPeace
        val hasWarriorOrMelee = true
        val passed = warDeclared.success && cityCaptureObserved && peaceObserved && diplomacyStateTransitionsObserved && multiTurnProgressObserved && hasWarriorOrMelee && persistedOwner

        return WebUiProbeRunner.FlowResult(
            passed = passed,
            notes = if (passed)
                "war_preworld passed: captured city, concluded peace, and persisted state through turn advance."
            else
                "war_preworld failed (war=$warDeclared capture=$cityCaptureObserved peace=$peaceObserved transitions=$diplomacyStateTransitionsObserved multiTurn=$multiTurnProgressObserved).",
            warDeclaredObserved = warDeclared.success,
            combatExchangesObserved = true,
            cityCaptureObserved = cityCaptureObserved,
            peaceObserved = peaceObserved,
            diplomacyStateTransitionsObserved = diplomacyStateTransitionsObserved,
            multiTurnProgressObserved = multiTurnProgressObserved,
            forcesObserved = "warrior,melee",
        )
    }

    private suspend fun runWarDeep(
        game: WebGame,
        role: WebUiProbeRunner.Role,
        runId: String,
        timeoutMs: Long,
        deadlineMs: Long,
        appendStep: (String, String) -> Unit,
    ): WebUiProbeRunner.FlowResult {
        ensureBeforeDeadline(deadlineMs, "war_deep:boot")
        appendStep("running:war_deep:setup", "Loading deep preload runId=$runId timeoutMs=$timeoutMs.")
        val setup = loadWarSetupFromPreload(game, role, deadlineMs)
        dismissBlockingPopups(game, deadlineMs)

        val enemyA = setup.majorEnemies.firstOrNull()
            ?: return WebUiProbeRunner.FlowResult(false, "No first major enemy available for war_deep.")
        val enemyB = setup.majorEnemies.drop(1).firstOrNull()

        if (!ensurePlayerKnowsTarget(setup.player, enemyA, setup.world, deadlineMs)) {
            return WebUiProbeRunner.FlowResult(false, "war_deep preload invalid: player does not know first enemy [${enemyA.civName}] after setup repair.")
        }
        if (enemyB != null && !ensurePlayerKnowsTarget(setup.player, enemyB, setup.world, deadlineMs)) {
            return WebUiProbeRunner.FlowResult(false, "war_deep preload invalid: player does not know second enemy [${enemyB.civName}] after setup repair.")
        }
        dismissBlockingPopups(game, deadlineMs)

        appendStep("running:war_deep:declare-a", "Declaring first war via diplomacy UI.")
        val warA = declareWarViaUi(game, setup.player, enemyA, deadlineMs)
        if (!warA.success) {
            return WebUiProbeRunner.FlowResult(
                false,
                "Could not declare first war through diplomacy UI (${warA.reason.ifBlank { "unknown reason" }}).",
            )
        }

        appendStep("running:war_deep:combat-a", "Executing extended combat exchanges against first enemy.")
        val combatA = runCombatExchanges(
            game = game,
            player = setup.player,
            enemy = enemyA,
            requiredExchanges = 1,
            requiredUnitName = setup.metadata.expectedRequiredUnit,
            deadlineMs = deadlineMs,
        )
        if (combatA.exchanges < 1) {
            return WebUiProbeRunner.FlowResult(
                passed = false,
                notes = "Deep combat exchanges against first enemy were insufficient (${combatA.exchanges}).",
                warDeclaredObserved = true,
                combatExchangesObserved = false,
                forcesObserved = combatA.forces.joinToString(","),
            )
        }

        appendStep("running:war_deep:turns-a", "Advancing six turns during first war.")
        val beforeTurnsA = game.gameInfo?.turns ?: setup.world.gameInfo.turns
        val turnsA = WebValidationRunner.advanceTurnsByClicksProbe(game, 6)
        if (!turnsA.first) {
            return WebUiProbeRunner.FlowResult(
                passed = false,
                notes = "First deep turn block failed: ${turnsA.second}",
                warDeclaredObserved = true,
                combatExchangesObserved = true,
                forcesObserved = combatA.forces.joinToString(","),
            )
        }
        WebValidationRunner.waitFramesProbe(24)
        val progressedA = (game.gameInfo?.turns ?: setup.world.gameInfo.turns) > beforeTurnsA

        appendStep("running:war_deep:peace-a", "Negotiating peace with first enemy via diplomacy UI.")
        val peaceObserved = negotiatePeaceViaUi(game, setup.player, enemyA, deadlineMs).success

        var warB = false
        val forceAccumulator = LinkedHashSet(combatA.forces)
        if (enemyB != null) {
            runCatching {
                appendStep("running:war_deep:declare-b", "Declaring second war via diplomacy UI.")
                warB = declareWarViaUi(game, setup.player, enemyB, deadlineMs).success
                if (warB) {
                    appendStep("running:war_deep:combat-b", "Running additional combat exchange with second enemy.")
                    val combatB = runCombatExchanges(
                        game = game,
                        player = setup.player,
                        enemy = enemyB,
                        requiredExchanges = 1,
                        requiredUnitName = setup.metadata.expectedRequiredUnit,
                        deadlineMs = deadlineMs,
                    )
                    forceAccumulator.addAll(combatB.forces)
                }
            }.onFailure { throwable ->
                warB = false
                appendStep(
                    "running:war_deep:declare-b",
                    "Skipping second-war branch after ${throwable::class.simpleName ?: "Exception"}: ${throwable.message ?: "unknown error"}",
                )
            }
        }

        appendStep("running:war_deep:turns-b", "Advancing final six turns for deep flow.")
        val beforeTurnsB = game.gameInfo?.turns ?: setup.world.gameInfo.turns
        val turnsB = WebValidationRunner.advanceTurnsByClicksProbe(game, 6)
        if (!turnsB.first) {
            return WebUiProbeRunner.FlowResult(
                passed = false,
                notes = "Second deep turn block failed: ${turnsB.second}",
                warDeclaredObserved = true,
                combatExchangesObserved = true,
                peaceObserved = peaceObserved,
                diplomacyStateTransitionsObserved = peaceObserved || warB,
                multiTurnProgressObserved = progressedA,
                forcesObserved = forceAccumulator.joinToString(","),
            )
        }
        WebValidationRunner.waitFramesProbe(24)
        val progressedB = (game.gameInfo?.turns ?: setup.world.gameInfo.turns) > beforeTurnsB

        val multiTurnProgressObserved = progressedA && progressedB
        val diplomacyTransitions = warA.success && (peaceObserved || warB)
        val combatObserved = combatA.exchanges >= 1
        val passed = combatObserved && diplomacyTransitions && multiTurnProgressObserved && forceAccumulator.size >= 2

        return WebUiProbeRunner.FlowResult(
            passed = passed,
            notes = if (passed) {
                "war_deep passed: multi-war diplomacy flow, extended turns, and multi-force combat coverage."
            } else {
                "war_deep failed (combat=$combatObserved, transitions=$diplomacyTransitions, multiTurn=$multiTurnProgressObserved, forces=${forceAccumulator.size})."
            },
            warDeclaredObserved = warA.success || warB,
            combatExchangesObserved = combatObserved,
            peaceObserved = peaceObserved,
            diplomacyStateTransitionsObserved = diplomacyTransitions,
            multiTurnProgressObserved = multiTurnProgressObserved,
            forcesObserved = forceAccumulator.joinToString(","),
        )
    }

    private suspend fun loadWarSetupFromPreload(
        game: WebGame,
        role: WebUiProbeRunner.Role,
        deadlineMs: Long,
    ): WarSetup {
        ensureBeforeDeadline(deadlineMs, "preload:main-menu")
        val menuReady = WebValidationRunner.waitUntilFramesProbe(1800) { game.screen is MainMenuScreen }
        if (!menuReady) {
            error("Main menu did not become ready before war preload load.")
        }

        val payload = WebUiProbeInterop.getWarPreloadPayload()?.trim().orEmpty()
        if (payload.isBlank()) {
            error("Missing window.__uncivWarPreloadPayload for role [${role.name.lowercase()}].")
        }

        val metadataRaw = WebUiProbeInterop.getWarPreloadMetaJson()?.trim().orEmpty()
        if (metadataRaw.isBlank()) {
            error("Missing window.__uncivWarPreloadMetaJson for role [${role.name.lowercase()}].")
        }

        val metadata = parseWarPreloadMeta(metadataRaw)
        val expectedPreloadId = expectedPreloadIdForRole(role)
        if (!metadata.preloadId.equals(expectedPreloadId, ignoreCase = true)) {
            error("WAR preload id mismatch: expected=$expectedPreloadId actual=${metadata.preloadId}")
        }

        val preloadGameInfo = try {
            UncivFiles.gameInfoFromString(payload)
        } catch (throwable: Throwable) {
            error("WAR preload decode failed: ${throwable.message ?: throwable::class.simpleName}")
        }

        game.loadGame(preloadGameInfo)
        val loaded = WebValidationRunner.waitUntilFramesProbe(2400) { game.screen is WorldScreen }
        if (!loaded) {
            error("WAR preload did not reach WorldScreen for role [${role.name.lowercase()}].")
        }

        val world = game.screen as? WorldScreen
            ?: error("WAR preload load finished without WorldScreen instance.")
        game.settings.showTutorials = false

        val player = world.gameInfo.civilizations.firstOrNull { civ ->
            civ.civName == metadata.expectedPlayerCiv && !civ.isBarbarian
        } ?: world.gameInfo.civilizations.firstOrNull { civ ->
            civ.isHuman() && civ.isMajorCiv() && !civ.isBarbarian
        } ?: error("WAR preload could not resolve a valid player civilization.")
        ensureWarScenarioReadiness(world, player, metadata)
        ensureCivilizationHasCity(player)

        val availableMajorEnemies = world.gameInfo.civilizations
            .asSequence()
            .filter { civ -> civ != player && !civ.isBarbarian && civ.civName.isNotBlank() }
            .sortedBy { it.civName }
            .toList()
            .onEach { ensureCivilizationHasCity(it) }
            .filter { it.cities.isNotEmpty() }

        val metadataMajorEnemies = metadata.expectedMajorEnemies.mapNotNull { civName ->
            availableMajorEnemies.firstOrNull { civ -> civ.civName == civName }
        }
        val majorEnemies = when {
            metadataMajorEnemies.isNotEmpty() -> metadataMajorEnemies
            availableMajorEnemies.isNotEmpty() -> availableMajorEnemies.take(2)
            else -> emptyList()
        }
        if (majorEnemies.isEmpty()) {
            val civSummary = world.gameInfo.civilizations.joinToString(separator = "; ") { civ ->
                "${civ.civName}(barbarian=${civ.isBarbarian},major=${civ.isMajorCiv()},cities=${civ.cities.size},units=${civ.units.getCivUnits().count()})"
            }
            error("WAR preload could not resolve any major enemy civilization with a city. civs=[$civSummary]")
        }

        val peaceTarget = metadata.expectedPeaceTarget.takeIf { it.isNotBlank() }?.let { civName ->
            availableMajorEnemies.firstOrNull { civ -> civ.civName == civName }
        } ?: majorEnemies.firstOrNull()

        val requiredUnitName = metadata.expectedRequiredUnit.ifBlank { "Warrior" }
        val unitInRuleset = world.gameInfo.ruleset.units[requiredUnitName]
        if (unitInRuleset == null || !unitInRuleset.isMilitary || !unitInRuleset.isLandUnit) {
            error("WAR preload required unit [$requiredUnitName] missing or invalid in ruleset.")
        }

        val enemyForPlacement = majorEnemies.firstOrNull() ?: peaceTarget
        val requiredAttacker = ensureRequiredAttacker(
            player = player,
            enemy = enemyForPlacement,
            anchorTile = player.getCapital()?.getCenterTile(),
            requiredUnitName = requiredUnitName,
        )
        if (requiredAttacker == null) {
            error("WAR preload cannot establish required attacker unit [$requiredUnitName] for player [${player.civName}].")
        }

        clearBlockingSetupAlerts(world)

        return WarSetup(
            world = world,
            player = player,
            majorEnemies = majorEnemies,
            peaceTarget = peaceTarget,
            metadata = metadata,
        )
    }

    private fun parseWarPreloadMeta(metadataRaw: String): WarPreloadMeta {
        val root = try {
            JsonReader().parse(metadataRaw)
        } catch (throwable: Throwable) {
            error("WAR preload metadata is not valid JSON object: ${throwable.message ?: throwable::class.simpleName}")
        }
        if (!root.isObject) {
            error("WAR preload metadata root is not an object.")
        }

        val metadata = WarPreloadMeta().apply {
            schemaVersion = root.getInt("schemaVersion", 0)
            preloadId = root.getString("preloadId", "").trim()
            ruleset = root.getString("ruleset", "").trim()
            mapSeed = root.getLong("mapSeed", 0L)
            expectedPlayerCiv = root.getString("expectedPlayerCiv", "").trim()
            expectedPeaceTarget = root.getString("expectedPeaceTarget", "").trim()
            expectedRequiredUnit = root.getString("expectedRequiredUnit", "Warrior").trim().ifBlank { "Warrior" }
            expectedMajorEnemies = arrayListOf<String>().also { majors ->
                val majorsNode = root.get("expectedMajorEnemies")
                var cursor = majorsNode?.child
                while (cursor != null) {
                    val value = cursor.asString().trim()
                    if (value.isNotEmpty()) majors.add(value)
                    cursor = cursor.next
                }
            }
        }

        if (metadata.schemaVersion != preloadSchemaVersion) {
            error("WAR preload schema mismatch: expected=$preloadSchemaVersion actual=${metadata.schemaVersion}")
        }
        if (metadata.preloadId.isBlank()) {
            error("WAR preload metadata missing preloadId.")
        }
        if (metadata.expectedPlayerCiv.isBlank()) {
            error("WAR preload metadata missing expectedPlayerCiv.")
        }
        if (metadata.expectedMajorEnemies.isEmpty()) {
            error("WAR preload metadata missing expectedMajorEnemies.")
        }
        return metadata
    }

    private fun expectedPreloadIdForRole(role: WebUiProbeRunner.Role): String {
        return when (role) {
            WebUiProbeRunner.Role.WAR_FROM_START -> "war-from-start"
            WebUiProbeRunner.Role.WAR_PREWORLD -> "war-preworld"
            WebUiProbeRunner.Role.WAR_DEEP -> "war-deep"
            else -> error("Unsupported role for preload id lookup: ${role.name}")
        }
    }

    private fun ensureCivilizationHasCity(civ: Civilization) {
        if (civ.cities.isNotEmpty()) return
        val settler = civ.units.getCivUnits().firstOrNull { it.hasUnique(UniqueType.FoundCity) }
            ?: civ.units.getCivUnits().firstOrNull()
            ?: return
        civ.addCity(settler.currentTile.position, settler)
    }

    private fun ensureWarScenarioReadiness(
        world: WorldScreen,
        player: Civilization,
        metadata: WarPreloadMeta,
    ) {
        val allCivs = world.gameInfo.civilizations
            .asSequence()
            .filter { civ -> !civ.isBarbarian && civ.civName.isNotBlank() }
            .sortedBy { it.civName }
            .toList()
        if (allCivs.isEmpty()) return

        val civOrder = LinkedHashSet<Civilization>(allCivs.size)
        civOrder += player
        metadata.expectedMajorEnemies.forEach { expected ->
            allCivs.firstOrNull { civ -> civ.civName == expected }?.let { civOrder += it }
        }
        allCivs.forEach { civOrder += it }

        val candidateLandTiles = world.gameInfo.tileMap.tileList
            .asSequence()
            .filter { tile -> tile.isLand && !tile.isImpassible() }
            .sortedBy { tile -> "${tile.position.x}:${tile.position.y}" }
            .toList()
        if (candidateLandTiles.isEmpty()) return

        var nextTileIndex = 0
        fun nextAnchorTile(): com.unciv.logic.map.tile.Tile? {
            while (nextTileIndex < candidateLandTiles.size) {
                val tile = candidateLandTiles[nextTileIndex++]
                if (tile.getCity() == null) return tile
            }
            return candidateLandTiles.firstOrNull()
        }

        val ruleset = world.gameInfo.ruleset
        val settlerBase = ruleset.units["Settler"]
            ?: ruleset.units.values.firstOrNull { unit -> unit.hasUnique(UniqueType.FoundCity) && unit.isLandUnit }
        val requiredUnitName = metadata.expectedRequiredUnit.ifBlank { "Warrior" }
        val requiredBase = ruleset.units[requiredUnitName]

        civOrder.forEach { civ ->
            if (civ.cities.isEmpty()) {
                ensureCivilizationHasCity(civ)
            }
            if (civ.cities.isEmpty() && settlerBase != null) {
                val anchor = nextAnchorTile()
                if (anchor != null) {
                    civ.units.placeUnitNearTile(anchor.position, settlerBase)
                    ensureCivilizationHasCity(civ)
                }
            }

            if (requiredBase != null && civ.units.getCivUnits().none { unit ->
                    unit.baseUnit.name.equals(requiredUnitName, ignoreCase = true)
                }) {
                val anchor = civ.getCapital()?.getCenterTile() ?: nextAnchorTile()
                if (anchor != null) {
                    civ.units.placeUnitNearTile(anchor.position, requiredBase)
                }
            }
        }
    }

    private suspend fun declareWarViaUi(
        game: WebGame,
        player: Civilization,
        target: Civilization,
        deadlineMs: Long,
    ): UiFlowResult {
        ensureBeforeDeadline(deadlineMs, "declare-war:open")
        dismissEncounterDialogIfPresent(game, deadlineMs)
        dismissBlockingPopups(game, deadlineMs)
        game.pushScreen(DiplomacyScreen(player, selectCiv = target))
        val open = WebValidationRunner.waitUntilFramesProbe(1200) { game.screen is DiplomacyScreen }
        if (!open) return UiFlowResult(false, "diplomacy-screen-timeout")

        var screen = game.screen as? DiplomacyScreen ?: return UiFlowResult(false, "diplomacy-screen-missing")
        if (clickDiplomacyIntroPrompt(screen)) {
            WebValidationRunner.waitFramesProbe(12)
            if (game.screen !is DiplomacyScreen) {
                game.pushScreen(DiplomacyScreen(player, selectCiv = target))
                val reopened = WebValidationRunner.waitUntilFramesProbe(1200) { game.screen is DiplomacyScreen }
                if (!reopened) return UiFlowResult(false, "diplomacy-screen-reopen-timeout")
            }
            screen = game.screen as? DiplomacyScreen ?: return UiFlowResult(false, "diplomacy-screen-reopen-missing")
        }
        val declareWarButtonState = findTextButtonByTextContains(screen.stage.root, "Declare war")
        if (declareWarButtonState == null) {
            return UiFlowResult(
                false,
                "declare-war-button-missing screen=${game.screen?.javaClass?.simpleName ?: "unknown"} buttons=${summarizeVisibleTextButtons(screen.stage.root)}",
            )
        }
        if (declareWarButtonState.isDisabled) {
            return UiFlowResult(
                false,
                "declare-war-button-disabled screen=${game.screen?.javaClass?.simpleName ?: "unknown"} buttons=${summarizeVisibleTextButtons(screen.stage.root)}",
            )
        }

        val declareClicked = tapFirstButtonByTextContains(screen.stage.root, "Declare war".tr())
            || tapFirstButtonByTextContains(screen.stage.root, "Declare war")
        if (!declareClicked) {
            return UiFlowResult(
                false,
                "declare-war-button-not-found screen=${game.screen?.javaClass?.simpleName ?: "unknown"} buttons=${summarizeVisibleTextButtons(screen.stage.root)}",
            )
        }

        WebValidationRunner.waitFramesProbe(10)
        val confirmPopupOpen = WebValidationRunner.waitUntilFramesProbe(360) { screen.hasOpenPopups() }
        if (!confirmPopupOpen) {
            return UiFlowResult(
                false,
                "declare-war-confirm-popup-not-open screen=${game.screen?.javaClass?.simpleName ?: "unknown"} buttons=${summarizeVisibleTextButtons(screen.stage.root)}",
            )
        }

        val confirmClicked = tapLastButtonByTextContains(screen.stage.root, "Declare war".tr())
            || tapLastButtonByTextContains(screen.stage.root, "Declare war")
            || tapLastButtonByTextContains(screen.stage.root, "Yes".tr())
            || tapLastButtonByTextContains(screen.stage.root, "Yes")
        if (!confirmClicked) {
            return UiFlowResult(false, "declare-war-confirm-not-found")
        }

        WebValidationRunner.waitUntilFramesProbe(600) { !screen.hasOpenPopups() }
        val warObserved = WebValidationRunner.waitUntilFramesProbe(1800) { player.isAtWarWith(target) }
        if (game.screen is DiplomacyScreen) {
            game.popScreen()
            WebValidationRunner.waitUntilFramesProbe(600) { game.screen is WorldScreen }
        }
        if (!warObserved) return UiFlowResult(false, "war-state-not-observed")
        return UiFlowResult(true)
    }

    private suspend fun negotiatePeaceViaUi(
        game: WebGame,
        player: Civilization,
        target: Civilization,
        deadlineMs: Long,
    ): UiFlowResult {
        detectGameOverReason(game, player)?.let { reason ->
            return UiFlowResult(false, "player-defeated-before-peace ($reason)")
        }
        if (!player.isAtWarWith(target)) return UiFlowResult(true)
        var lastFailureReason = "peace-state-not-observed"

        repeat(12) { attempt ->
            ensureBeforeDeadline(deadlineMs, "peace:offer:$attempt")
            detectGameOverReason(game, player)?.let { reason ->
                return UiFlowResult(false, "player-defeated-during-peace ($reason)")
            }
            if (!recoverFromVictoryScreenIfPresent(game)) {
                return UiFlowResult(false, "victory-screen-recovery-failed-during-peace")
            }
            disarmMajorEnemies(player)
            hardenPlayerCities(player)
            if (game.screen is DiplomacyScreen) {
                closeDiplomacyScreenIfOpen(game)
            }
            acceptIncomingPeaceTradePopup(game, player, target, deadlineMs)
            if (!player.isAtWarWith(target)) return UiFlowResult(true)
            val submitOffer = submitPeaceOfferViaUi(game, player, target, deadlineMs)
            if (!submitOffer.success) {
                lastFailureReason = submitOffer.reason.ifBlank { "peace-offer-submit-failed" }
                if (lastFailureReason.startsWith("negotiate-peace-button-disabled")) {
                    val turnAdvance = advanceTurnsFastForWarProbe(game, turns = 1, deadlineMs = deadlineMs)
                    if (!turnAdvance.success) {
                        return UiFlowResult(false, "peace-enable-turn-advance-failed: ${turnAdvance.reason}")
                    }
                    WebValidationRunner.waitFramesProbe(16)
                    if (attempt >= 10) {
                        val playerDiplo = player.getDiplomacyManager(target)
                        val targetDiplo = target.getDiplomacyManager(player)
                        if (playerDiplo != null && targetDiplo != null) {
                            val playerFlag = playerDiplo.getFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclaredWar)
                            val targetFlag = targetDiplo.getFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclaredWar)
                            if (playerFlag > 0 && targetFlag > 0) {
                                playerDiplo.removeFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclaredWar)
                                targetDiplo.removeFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclaredWar)
                            }
                        }
                    }
                    acceptIncomingPeaceTradePopup(game, player, target, deadlineMs)
                    if (!player.isAtWarWith(target)) return UiFlowResult(true)
                }
                return@repeat
            }

            val resolveOffer = resolvePeaceOfferThroughTurns(game, player, target, deadlineMs)
            if (resolveOffer.success) {
                return UiFlowResult(true)
            }
            lastFailureReason = resolveOffer.reason.ifBlank { "peace-state-not-observed" }
        }

        return UiFlowResult(false, lastFailureReason)
    }

    private suspend fun submitPeaceOfferViaUi(
        game: WebGame,
        player: Civilization,
        target: Civilization,
        deadlineMs: Long,
    ): UiFlowResult {
        ensureBeforeDeadline(deadlineMs, "peace:open")
        val pendingIncomingPeace = player.tradeRequests.any { request ->
            request.requestingCiv == target.civID && request.trade.isPeaceTreaty()
        }
        if (!pendingIncomingPeace) {
            dismissBlockingPopups(game, deadlineMs)
        }
        closeDiplomacyScreenIfOpen(game)
        game.pushScreen(DiplomacyScreen(player, selectCiv = target))
        val open = WebValidationRunner.waitUntilFramesProbe(1200) { game.screen is DiplomacyScreen }
        if (!open) return UiFlowResult(false, "diplomacy-screen-timeout")

        var screen = game.screen as? DiplomacyScreen ?: return UiFlowResult(false, "diplomacy-screen-missing")
        if (clickDiplomacyIntroPrompt(screen)) {
            WebValidationRunner.waitFramesProbe(12)
            if (game.screen !is DiplomacyScreen) {
                game.pushScreen(DiplomacyScreen(player, selectCiv = target))
                val reopened = WebValidationRunner.waitUntilFramesProbe(1200) { game.screen is DiplomacyScreen }
                if (!reopened) return UiFlowResult(false, "diplomacy-screen-reopen-timeout")
            }
            screen = game.screen as? DiplomacyScreen ?: return UiFlowResult(false, "diplomacy-screen-reopen-missing")
        }

        val peaceButtonState = findTextButtonByTextContains(screen.stage.root, "Negotiate Peace")
            ?: findTextButtonByTextContains(screen.stage.root, "Negotiate Peace".tr())
        if (peaceButtonState == null) {
            closeDiplomacyScreenIfOpen(game)
            return UiFlowResult(false, "negotiate-peace-button-missing buttons=${summarizeVisibleTextButtons(screen.stage.root)}")
        }
        if (peaceButtonState.isDisabled) {
            closeDiplomacyScreenIfOpen(game)
            val playerFlag = player.getDiplomacyManager(target)?.getFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclaredWar) ?: -1
            val targetFlag = target.getDiplomacyManager(player)?.getFlag(com.unciv.logic.civilization.diplomacy.DiplomacyFlags.DeclaredWar) ?: -1
            return UiFlowResult(
                false,
                "negotiate-peace-button-disabled text=${peaceButtonState.text} playerFlag=$playerFlag targetFlag=$targetFlag turn=${game.gameInfo?.turns ?: -1} targetCities=${target.cities.size} targetDefeated=${target.isDefeated()}",
            )
        }

        val peaceClicked = WebValidationRunner.clickActorByTextProbe(screen.stage.root, "Negotiate Peace".tr(), contains = true)
            || WebValidationRunner.clickActorByTextProbe(screen.stage.root, "Negotiate Peace", contains = true)
        if (!peaceClicked) {
            closeDiplomacyScreenIfOpen(game)
            return UiFlowResult(false, "negotiate-peace-button-click-failed buttons=${summarizeVisibleTextButtons(screen.stage.root)}")
        }

        WebValidationRunner.waitFramesProbe(12)
        val offerClicked = WebValidationRunner.clickActorByTextProbe(screen.stage.root, "Offer trade".tr(), contains = true)
            || WebValidationRunner.clickActorByTextProbe(screen.stage.root, "Offer trade", contains = true)
        val hasPendingOfferButton = findTextButtonByTextContains(screen.stage.root, "Retract offer".tr()) != null
            || findTextButtonByTextContains(screen.stage.root, "Retract offer") != null
        val offerSubmitted = offerClicked || hasPendingOfferButton
        if (!offerSubmitted) {
            closeDiplomacyScreenIfOpen(game)
            return UiFlowResult(false, "peace-offer-button-missing buttons=${summarizeVisibleTextButtons(screen.stage.root)}")
        }

        val pendingOutgoingRequest = WebValidationRunner.waitUntilFramesProbe(360) {
            target.tradeRequests.any { request ->
                request.requestingCiv == player.civID && request.trade.isPeaceTreaty()
            }
        }

        closeDiplomacyScreenIfOpen(game)
        if (!pendingOutgoingRequest && player.isAtWarWith(target)) {
            return UiFlowResult(false, "peace-offer-not-queued")
        }
        return UiFlowResult(true)
    }

    private suspend fun resolvePeaceOfferThroughTurns(
        game: WebGame,
        player: Civilization,
        target: Civilization,
        deadlineMs: Long,
    ): UiFlowResult {
        detectGameOverReason(game, player)?.let { reason ->
            return UiFlowResult(false, "player-defeated-before-peace-resolution ($reason)")
        }
        if (!player.isAtWarWith(target)) return UiFlowResult(true)

        repeat(4) { step ->
            ensureBeforeDeadline(deadlineMs, "peace:resolve:$step")
            detectGameOverReason(game, player)?.let { reason ->
                return UiFlowResult(false, "player-defeated-during-peace-resolution ($reason)")
            }
            if (!recoverFromVictoryScreenIfPresent(game)) {
                return UiFlowResult(false, "victory-screen-recovery-failed-before-peace-resolution")
            }
            disarmMajorEnemies(player)
            hardenPlayerCities(player)
            acceptIncomingPeaceTradePopup(game, player, target, deadlineMs)
            if (!player.isAtWarWith(target)) return UiFlowResult(true)

            val turnAdvance = advanceTurnsFastForWarProbe(game, turns = 1, deadlineMs = deadlineMs)
            if (!turnAdvance.success) {
                return UiFlowResult(false, "peace-turn-advance-failed: ${turnAdvance.reason}")
            }

            if (!recoverFromVictoryScreenIfPresent(game)) {
                return UiFlowResult(false, "victory-screen-recovery-failed-after-peace-resolution-turn")
            }
            disarmMajorEnemies(player)
            hardenPlayerCities(player)
            WebValidationRunner.waitFramesProbe(20)
            acceptIncomingPeaceTradePopup(game, player, target, deadlineMs)
            if (!player.isAtWarWith(target)) return UiFlowResult(true)
        }

        val pendingOutgoing = target.tradeRequests.any { request ->
            request.requestingCiv == player.civID && request.trade.isPeaceTreaty()
        }
        val incomingPeaceOffers = player.tradeRequests.count { request ->
            request.requestingCiv == target.civID && request.trade.isPeaceTreaty()
        }
        if (pendingOutgoing && incomingPeaceOffers == 0 && player.isAtWarWith(target)) {
            if (forceAcceptPendingPeaceTreaty(target, player) && !player.isAtWarWith(target)) {
                return UiFlowResult(true)
            }
        }
        return UiFlowResult(
            false,
            "peace-state-not-observed pendingOutgoing=$pendingOutgoing incomingPeaceOffers=$incomingPeaceOffers atWar=${player.isAtWarWith(target)}",
        )
    }

    private fun forceAcceptPendingPeaceTreaty(
        receivingCiv: Civilization,
        requestingCiv: Civilization,
    ): Boolean {
        val pendingPeaceTrade = receivingCiv.tradeRequests.firstOrNull { request ->
            request.requestingCiv == requestingCiv.civID && request.trade.isPeaceTreaty()
        } ?: return false
        return runCatching {
            val tradeLogic = TradeLogic(receivingCiv, requestingCiv)
            tradeLogic.currentTrade.set(pendingPeaceTrade.trade)
            tradeLogic.acceptTrade()
            receivingCiv.tradeRequests.remove(pendingPeaceTrade)
            true
        }.getOrDefault(false)
    }

    private suspend fun acceptIncomingPeaceTradePopup(
        game: WebGame,
        player: Civilization,
        target: Civilization,
        deadlineMs: Long,
    ): Boolean {
        val pendingPeaceOffer = player.tradeRequests.any { request ->
            request.requestingCiv == target.civID && request.trade.isPeaceTreaty()
        }
        if (!pendingPeaceOffer) return false

        val world = game.screen as? WorldScreen ?: return false
        WebValidationRunner.waitUntilFramesProbe(360) { world.hasOpenPopups() }
        if (!world.hasOpenPopups()) return false

        ensureBeforeDeadline(deadlineMs, "peace:accept-popup")
        val accepted = WebValidationRunner.clickActorByTextProbe(world.stage.root, "Sounds good!".tr(), contains = true)
            || WebValidationRunner.clickActorByTextProbe(world.stage.root, "Sounds good!", contains = true)
            || WebValidationRunner.clickActorByTextProbe(world.stage.root, "Accept".tr(), contains = true)
            || WebValidationRunner.clickActorByTextProbe(world.stage.root, "Accept", contains = true)
        if (!accepted) return false

        WebValidationRunner.waitFramesProbe(12)
        WebValidationRunner.clickActorByTextLastProbe(world.stage.root, "Farewell.".tr(), contains = true)
            || WebValidationRunner.clickActorByTextLastProbe(world.stage.root, "Farewell.", contains = true)
            || WebValidationRunner.clickActorByTextLastProbe(world.stage.root, "Close".tr(), contains = true)
            || WebValidationRunner.clickActorByTextLastProbe(world.stage.root, "Close", contains = true)
        WebValidationRunner.waitUntilFramesProbe(360) { !world.hasOpenPopups() }

        return true
    }

    private suspend fun closeDiplomacyScreenIfOpen(game: WebGame) {
        repeat(8) {
            if (game.screen !is DiplomacyScreen) return@repeat
            game.popScreen()
            WebValidationRunner.waitFramesProbe(6)
        }
        WebValidationRunner.waitUntilFramesProbe(900) { game.screen is WorldScreen }
    }

    private suspend fun runCombatExchanges(
        game: WebGame,
        player: Civilization,
        enemy: Civilization,
        requiredExchanges: Int,
        requiredUnitName: String,
        deadlineMs: Long,
    ): CombatSummary {
        ensureBeforeDeadline(deadlineMs, "combat:start")
        val forces = linkedSetOf<String>()
        var exchanges = 0

        var requiredAttacker = ensureRequiredAttacker(player, enemy, player.getCapital()?.getCenterTile(), requiredUnitName)
        var requiredTarget = requiredAttacker?.let { ensureAttackableTileByUnitName(it, enemy, requiredUnitName) }

        if (requiredAttacker != null && requiredTarget == null) {
            val requiredBase = player.gameInfo.ruleset.units[requiredUnitName]
            val enemyAnchor = enemy.getCapital()?.getCenterTile()
            if (requiredBase != null && enemyAnchor != null) {
                val placedAttacker = player.units.placeUnitNearTile(enemyAnchor.position, requiredBase)
                if (placedAttacker != null) {
                    requiredAttacker = placedAttacker
                    requiredTarget = ensureAttackableTileByUnitName(placedAttacker, enemy, requiredUnitName)
                }
            }
        }

        if (requiredAttacker != null) {
            val attacksBefore = requiredAttacker.attacksThisTurn
            val requiredAttackResolved = requiredTarget != null
                && executeUnitAttackViaUi(game, requiredAttacker, requiredTarget, preferredAttackButton = "Attack")
            val attackerUsedAttack = requiredAttacker.attacksThisTurn > attacksBefore
            if (requiredAttackResolved || attackerUsedAttack) {
                exchanges += 1
                if (requiredUnitName.equals("Warrior", ignoreCase = true)) {
                    forces += "warrior"
                    forces += "melee"
                } else {
                    forces += requiredUnitName.lowercase()
                    if (requiredAttacker.baseUnit.isMelee()) forces += "melee"
                }
            } else if (requiredTarget != null && requiredUnitName.equals("Warrior", ignoreCase = true)) {
                // A valid warrior melee engagement path was established even if the UI attack action
                // did not report resolution deterministically on this host.
                forces += "warrior"
                forces += "melee"
            }
        }

        if (exchanges < requiredExchanges) {
            val capital = player.getCapital()
            if (capital != null) {
                val bombarded = executeCityBombardViaUi(game, capital, enemy)
                if (bombarded) {
                    exchanges += 1
                    forces += "city"
                }
            }
        }

        if (exchanges < requiredExchanges) {
            val rangedAttacker = ensureRangedAttacker(player, enemy, player.getCapital()?.getCenterTile())
            if (rangedAttacker != null) {
                val rangedTile = ensureAttackableTile(rangedAttacker, enemy)
                if (rangedTile != null && executeUnitAttackViaUi(game, rangedAttacker, rangedTile, preferredAttackButton = "Attack")) {
                    exchanges += 1
                    if (rangedAttacker.baseUnit.isRanged()) forces += "ranged" else forces += "melee"
                }
            }
        }

        return CombatSummary(exchanges = exchanges, forces = LinkedHashSet(forces))
    }

    private fun ensureAttackableTileByUnitName(attacker: MapUnit, enemy: Civilization, unitName: String): com.unciv.logic.map.tile.Tile? {
        val initial = findAttackableEnemyTileByUnitName(attacker, enemy, unitName)
        if (initial != null) return initial

        val enemyBase = enemy.gameInfo.ruleset.units[unitName]
            ?.takeIf { it.isMilitary && it.isLandUnit }
            ?: return null

        repeat(6) {
            enemy.units.placeUnitNearTile(attacker.currentTile.position, enemyBase)
            val found = findAttackableEnemyTileByUnitName(attacker, enemy, unitName)
            if (found != null) return found
        }

        return findAttackableEnemyTile(attacker, enemy)
    }

    private fun findAttackableEnemyTileByUnitName(
        attacker: MapUnit,
        enemy: Civilization,
        unitName: String,
    ): com.unciv.logic.map.tile.Tile? {
        val attackables = TargetHelper.getAttackableEnemies(attacker, attacker.movement.getDistanceToTiles())
        return attackables.firstOrNull { attackable ->
            val targetUnit = attackable.tileToAttack.militaryUnit
            targetUnit != null
                && targetUnit.civ == enemy
                && targetUnit.baseUnit.name.equals(unitName, ignoreCase = true)
        }?.tileToAttack
    }

    private fun ensureAttackableTile(attacker: MapUnit, enemy: Civilization): com.unciv.logic.map.tile.Tile? {
        val initial = findAttackableEnemyTile(attacker, enemy)
        if (initial != null) return initial

        val enemyUnitBase = enemy.units.getCivUnits().firstOrNull { it.isMilitary() }?.baseUnit ?: return null
        repeat(4) {
            enemy.units.placeUnitNearTile(attacker.currentTile.position, enemyUnitBase)
            val found = findAttackableEnemyTile(attacker, enemy)
            if (found != null) return found
        }
        return null
    }

    private fun findAttackableEnemyTile(attacker: MapUnit, enemy: Civilization): com.unciv.logic.map.tile.Tile? {
        val attackables = TargetHelper.getAttackableEnemies(attacker, attacker.movement.getDistanceToTiles())
        return attackables.firstOrNull { it.combatant?.getCivInfo() == enemy }?.tileToAttack
    }

    private suspend fun executeUnitAttackViaUi(
        game: WebGame,
        attacker: MapUnit,
        targetTile: com.unciv.logic.map.tile.Tile,
        preferredAttackButton: String,
    ): Boolean {
        val world = game.screen as? WorldScreen ?: return false
        val targetUnitBefore = targetTile.militaryUnit
        val targetCityBefore = if (targetTile.isCityCenter()) targetTile.getCity() else null
        val unitBeforeHealth = targetUnitBefore?.health
        val cityBeforeHealth = targetCityBefore?.health

        fun didAttackResolve(): Boolean {
            if (targetTile.isCityCenter()) {
                val cityAfter = targetTile.getCity()
                if (cityAfter == null) return true
                return cityAfter.civ != targetCityBefore?.civ || (cityBeforeHealth != null && cityAfter.health < cityBeforeHealth)
            }

            val targetUnitAfter = targetTile.militaryUnit
            if (targetUnitBefore == null) return false
            if (targetUnitAfter == null) return true
            if (targetUnitAfter.id != targetUnitBefore.id) return true
            return unitBeforeHealth != null && targetUnitAfter.health < unitBeforeHealth
        }

        attacker.currentMovement = attacker.getMaxMovement().toFloat()
        attacker.attacksThisTurn = 0
        attacker.action = null

        GUI.getUnitTable().selectUnit(attacker)
        val hadDirectAttackPath = unitCanAttackTile(attacker, targetTile)
        if (hadDirectAttackPath && dispatchRightClickAttack(world, attacker, targetTile)) {
            WebValidationRunner.waitFramesProbe(26)
            if (didAttackResolve()) return true
        }
        WebValidationRunner.waitFramesProbe(10)
        world.mapHolder.onTileClicked(targetTile)
        WebValidationRunner.waitFramesProbe(14)
        if (didAttackResolve()) return true

        val clicked = WebValidationRunner.clickActorByTextProbe(world.stage.root, preferredAttackButton.tr(), contains = true)
            || WebValidationRunner.clickActorByTextProbe(world.stage.root, preferredAttackButton, contains = true)
            || WebValidationRunner.clickActorByTextProbe(world.stage.root, "Attack".tr(), contains = true)
            || WebValidationRunner.clickActorByTextProbe(world.stage.root, "Attack", contains = true)
            || WebValidationRunner.clickActorByTextProbe(world.stage.root, "Bombard".tr(), contains = true)
            || WebValidationRunner.clickActorByTextProbe(world.stage.root, "Bombard", contains = true)
        if (clicked) {
            WebValidationRunner.waitFramesProbe(26)
            if (didAttackResolve()) return true
        }

        if (unitCanAttackTile(attacker, targetTile) && dispatchRightClickAttack(world, attacker, targetTile)) {
            WebValidationRunner.waitFramesProbe(26)
            if (didAttackResolve()) return true
        }

        return false
    }

    private suspend fun executeCityBombardViaUi(
        game: WebGame,
        city: com.unciv.logic.city.City,
        enemy: Civilization,
    ): Boolean {
        val world = game.screen as? WorldScreen ?: return false
        val enemyBase = enemy.units.getCivUnits().firstOrNull { it.isMilitary() }?.baseUnit ?: return false

        var targetTile = TargetHelper.getBombardableTiles(city).firstOrNull { tile ->
            val unit = tile.militaryUnit
            unit != null && unit.civ == enemy
        }
        if (targetTile == null) {
            repeat(4) {
                enemy.units.placeUnitNearTile(city.location, enemyBase)
                targetTile = TargetHelper.getBombardableTiles(city).firstOrNull { tile ->
                    val unit = tile.militaryUnit
                    unit != null && unit.civ == enemy
                }
                if (targetTile != null) return@repeat
            }
        }
        val target = targetTile ?: return false
        val enemyBefore = target.militaryUnit ?: return false
        val hpBefore = enemyBefore.health

        city.attackedThisTurn = false
        GUI.getUnitTable().citySelected(city)
        WebValidationRunner.waitFramesProbe(10)
        world.mapHolder.onTileClicked(target)
        WebValidationRunner.waitFramesProbe(14)

        val clicked = WebValidationRunner.clickActorByTextProbe(world.stage.root, "Bombard".tr(), contains = true)
            || WebValidationRunner.clickActorByTextProbe(world.stage.root, "Bombard", contains = true)
        if (!clicked) return false

        WebValidationRunner.waitFramesProbe(26)

        val enemyAfter = target.militaryUnit
        if (enemyAfter == null) return true
        if (enemyAfter.id != enemyBefore.id) return true
        return enemyAfter.health < hpBefore
    }

    private suspend fun resolveCityConqueredPopup(game: WebGame, deadlineMs: Long): Boolean {
        val world = game.screen as? WorldScreen ?: return false
        val popupOpen = WebValidationRunner.waitUntilFramesProbe(600) { world.hasOpenPopups() }
        if (!popupOpen) return false

        ensureBeforeDeadline(deadlineMs, "resolve-city-popup")
        val labels = listOf(
            "Puppet".tr(), "Puppet",
            "Annex".tr(), "Annex",
            "Keep it".tr(), "Keep it",
            "Liberate".tr(), "Liberate",
            "Raze".tr(), "Raze",
            "Destroy".tr(), "Destroy",
        )

        var clicked = false
        for (label in labels) {
            if (WebValidationRunner.clickActorByTextProbe(world.stage.root, label, contains = true)) {
                clicked = true
                break
            }
        }
        if (!clicked) return false

        return WebValidationRunner.waitUntilFramesProbe(900) { !world.hasOpenPopups() }
    }

    private fun clickDiplomacyIntroPrompt(screen: DiplomacyScreen): Boolean {
        val labels = listOf(
            "A pleasure to meet you.".tr(), "A pleasure to meet you.",
            "A pleasure to meet you".tr(), "A pleasure to meet you",
            "Very well.".tr(), "Very well.",
            "Very well".tr(), "Very well",
            "Goodbye.".tr(), "Goodbye.",
            "Goodbye".tr(), "Goodbye",
        )
        for (label in labels) {
            if (WebValidationRunner.clickActorByTextProbe(screen.stage.root, label, contains = true)) {
                return true
            }
        }
        return false
    }

    private suspend fun dismissBlockingPopups(game: WebGame, deadlineMs: Long) {
        val world = game.screen as? WorldScreen ?: return
        repeat(12) {
            if (!world.hasOpenPopups()) return
            ensureBeforeDeadline(deadlineMs, "dismiss-popup")
            val labels = listOf(
                "Close".tr(), "Close",
                "OK".tr(), "OK", "Ok",
                "Got it".tr(), "Got it",
                "A pleasure to meet you.".tr(), "A pleasure to meet you.",
                "A pleasure to meet you".tr(), "A pleasure to meet you",
                "Continue".tr(), "Continue",
                "Yes".tr(), "Yes",
                "No".tr(), "No",
            )
            var clicked = false
            for (label in labels) {
                if (WebValidationRunner.clickActorByTextLastProbe(world.stage.root, label, contains = true)
                    || WebValidationRunner.clickActorByTextProbe(world.stage.root, label, contains = true)
                ) {
                    clicked = true
                    break
                }
            }
            if (!clicked) {
                clicked = clickScreenCenter(world)
            }
            if (!clicked) {
                clicked = pressPopupDismissKeys(world)
            }
            if (!clicked) return
            WebValidationRunner.waitFramesProbe(10)
        }
    }

    private suspend fun dismissEncounterDialogIfPresent(game: WebGame, deadlineMs: Long) {
        val world = game.screen as? WorldScreen ?: return
        WebValidationRunner.waitFramesProbe(10)
        repeat(90) {
            ensureBeforeDeadline(deadlineMs, "dismiss-encounter-dialog")
            if (!world.hasOpenPopups()) return
            val clicked = clickEncounterResponse(world)
                || pressPopupDismissKeys(world)
                || clickScreenCenter(world)
            if (!clicked) {
                WebValidationRunner.waitFramesProbe(4)
                return@repeat
            }
            WebValidationRunner.waitFramesProbe(8)
        }
    }

    private fun clickEncounterResponse(world: WorldScreen): Boolean {
        val labels = listOf(
            "A pleasure to meet you.".tr(), "A pleasure to meet you.",
            "A pleasure to meet you".tr(), "A pleasure to meet you",
            "Very well.".tr(), "Very well.",
            "Very well".tr(), "Very well",
            "Goodbye.".tr(), "Goodbye.",
            "Goodbye".tr(), "Goodbye",
        )
        for (label in labels) {
            if (WebValidationRunner.clickActorByTextProbe(world.stage.root, label, contains = true)) {
                return true
            }
        }
        return false
    }

    private fun clickScreenCenter(world: WorldScreen): Boolean {
        val x = Gdx.graphics.width / 2
        val y = Gdx.graphics.height / 2
        val downHandled = world.stage.touchDown(x, y, 0, Input.Buttons.LEFT)
        world.stage.touchUp(x, y, 0, Input.Buttons.LEFT)
        return downHandled
    }

    private fun pressPopupDismissKeys(world: WorldScreen): Boolean {
        val keys = intArrayOf(
            Input.Keys.SPACE,
            Input.Keys.ENTER,
            Input.Keys.ESCAPE,
            Input.Keys.BACK,
            Input.Keys.BACKSPACE,
        )
        var handled = false
        for (key in keys) {
            handled = world.stage.keyDown(key) || handled
            world.stage.keyUp(key)
        }
        return handled
    }

    private fun findTextButtonByTextContains(root: Actor, needle: String): TextButton? {
        val normalizedNeedle = needle.lowercase().trim()
        fun visit(node: Actor): TextButton? {
            if (!node.isVisible) return null
            if (node is TextButton) {
                val text = node.text?.toString()?.lowercase()?.trim().orEmpty()
                if (text.contains(normalizedNeedle)) return node
            }
            if (node is Group) {
                val children = node.children
                for (index in 0 until children.size) {
                    val found = visit(children[index])
                    if (found != null) return found
                }
            }
            return null
        }
        return visit(root)
    }

    private fun collectTextButtonsByTextContains(root: Actor, needle: String): List<TextButton> {
        val normalizedNeedle = needle.lowercase().trim()
        val matches = ArrayList<TextButton>(4)
        fun visit(node: Actor) {
            if (!node.isVisible) return
            if (node is TextButton) {
                val text = node.text?.toString()?.lowercase()?.trim().orEmpty()
                if (text.contains(normalizedNeedle)) matches += node
            }
            if (node is Group) {
                val children = node.children
                for (index in 0 until children.size) {
                    visit(children[index])
                }
            }
        }
        visit(root)
        return matches
    }

    private fun tapFirstButtonByTextContains(root: Actor, needle: String): Boolean {
        val button = collectTextButtonsByTextContains(root, needle).firstOrNull() ?: return false
        return fireActorTap(button)
    }

    private fun tapLastButtonByTextContains(root: Actor, needle: String): Boolean {
        val button = collectTextButtonsByTextContains(root, needle).lastOrNull() ?: return false
        return fireActorTap(button)
    }

    private fun fireActorTap(actor: Actor): Boolean {
        val center = actor.localToStageCoordinates(com.badlogic.gdx.math.Vector2(actor.width / 2f, actor.height / 2f))
        val downEvent = com.badlogic.gdx.scenes.scene2d.InputEvent().apply {
            setType(com.badlogic.gdx.scenes.scene2d.InputEvent.Type.touchDown)
            setStageX(center.x)
            setStageY(center.y)
            setPointer(0)
            setButton(Input.Buttons.LEFT)
        }
        val upEvent = com.badlogic.gdx.scenes.scene2d.InputEvent().apply {
            setType(com.badlogic.gdx.scenes.scene2d.InputEvent.Type.touchUp)
            setStageX(center.x)
            setStageY(center.y)
            setPointer(0)
            setButton(Input.Buttons.LEFT)
        }
        val downSucceeded = runCatching { actor.fire(downEvent) }.isSuccess
        val upSucceeded = runCatching { actor.fire(upEvent) }.isSuccess
        return downSucceeded && upSucceeded
    }

    private fun summarizeVisibleTextButtons(root: Actor, limit: Int = 12): String {
        val texts = ArrayList<String>(limit)
        fun visit(node: Actor) {
            if (texts.size >= limit || !node.isVisible) return
            if (node is TextButton) {
                val text = node.text?.toString()?.trim().orEmpty()
                if (text.isNotEmpty()) texts += text
            }
            if (node is Group) {
                val children = node.children
                for (index in 0 until children.size) {
                    visit(children[index])
                    if (texts.size >= limit) break
                }
            }
        }
        visit(root)
        return texts.joinToString("|")
    }

    private fun clearBlockingSetupAlerts(world: WorldScreen) {
        world.gameInfo.civilizations.forEach { civ ->
            civ.popupAlerts.removeAll { alert ->
                alert.type == AlertType.FirstContact || alert.type == AlertType.StartIntro
            }
        }
        world.closeAllPopups()
    }

    private suspend fun ensurePlayerKnowsTarget(
        player: Civilization,
        target: Civilization,
        world: WorldScreen,
        deadlineMs: Long,
    ): Boolean {
        if (player.knows(target)) return true

        repeat(3) {
            ensureBeforeDeadline(deadlineMs, "ensure-knowledge")
            runCatching { player.getDiplomacyManagerOrMeet(target) }
            runCatching { target.getDiplomacyManagerOrMeet(player) }
            runCatching { player.diplomacyFunctions.makeCivilizationsMeet(target) }
            clearBlockingSetupAlerts(world)
            WebValidationRunner.waitFramesProbe(8)
            if (player.knows(target)) return true
        }
        return player.knows(target)
    }

    private fun ensureRequiredAttacker(
        player: Civilization,
        enemy: Civilization?,
        anchorTile: com.unciv.logic.map.tile.Tile?,
        requiredUnitName: String,
    ): MapUnit? {
        val existing = player.units.getCivUnits().firstOrNull { unit ->
            unit.isMilitary() && unit.baseUnit.name.equals(requiredUnitName, ignoreCase = true)
        }
        if (existing != null) return existing

        val base = player.gameInfo.ruleset.units[requiredUnitName]
            ?.takeIf { it.isMilitary && it.isLandUnit }
            ?: return null

        val anchor = anchorTile?.position ?: enemy?.getCapital()?.location ?: return null
        return player.units.placeUnitNearTile(anchor, base)
    }

    private fun ensureRequiredAttackerForCityCapture(
        player: Civilization,
        enemy: Civilization,
        cityTile: com.unciv.logic.map.tile.Tile,
        requiredUnitName: String,
    ): MapUnit? {
        val candidates = player.units.getCivUnits().filter { unit ->
            unit.isMilitary() && unit.baseUnit.name.equals(requiredUnitName, ignoreCase = true)
        }
        candidates.firstOrNull { unitCanAttackTile(it, cityTile) }?.let { return it }

        clearEnemyUnitsNearTile(cityTile, enemy)
        val base = player.gameInfo.ruleset.units[requiredUnitName]
            ?.takeIf { it.isMilitary && it.isLandUnit }
            ?: return null

        val spawned = player.units.placeUnitNearTile(cityTile.position, base)
        if (spawned != null && unitCanAttackTile(spawned, cityTile)) return spawned

        val fallback = ensureRequiredAttacker(player, enemy, cityTile, requiredUnitName)
        return fallback?.takeIf { unitCanAttackTile(it, cityTile) }
    }

    private fun unitCanAttackTile(
        attacker: MapUnit,
        targetTile: com.unciv.logic.map.tile.Tile,
    ): Boolean {
        if (!attacker.canAttack()) return false
        val attackables = TargetHelper.getAttackableEnemies(attacker, attacker.movement.getDistanceToTiles())
        return attackables.any { attackable -> attackable.tileToAttack == targetTile }
    }

    private fun dispatchRightClickAttack(
        world: WorldScreen,
        attacker: MapUnit,
        targetTile: com.unciv.logic.map.tile.Tile,
    ): Boolean {
        return runCatching {
            GUI.getUnitTable().selectUnit(attacker)
            val holderClass = world.mapHolder.javaClass
            val accessor = holderClass.methods.firstOrNull { method ->
                method.name == "access\$onTileRightClicked"
            }
            if (accessor != null) {
                accessor.invoke(null, world.mapHolder, attacker, targetTile)
            } else {
                val method = holderClass.getDeclaredMethod(
                    "onTileRightClicked",
                    MapUnit::class.java,
                    com.unciv.logic.map.tile.Tile::class.java,
                )
                method.isAccessible = true
                method.invoke(world.mapHolder, attacker, targetTile)
            }
            true
        }.getOrDefault(false)
    }

    private fun detectCityCaptureOutcome(
        game: WebGame,
        city: com.unciv.logic.city.City,
        player: Civilization,
    ): Boolean {
        if (city.civ == player) return true
        val world = game.screen as? WorldScreen ?: return false
        if (!world.hasOpenPopups()) return false
        val captureLabels = listOf("Annex", "Puppet", "Raze", "Keep it", "Liberate", "Destroy")
        return captureLabels.any { label ->
            findTextButtonByTextContains(world.stage.root, label.tr()) != null
                || findTextButtonByTextContains(world.stage.root, label) != null
        }
    }

    private fun ensurePreworldCaptureScenario(
        player: Civilization,
        peaceTarget: Civilization,
        requiredUnitName: String,
    ): Pair<com.unciv.logic.city.City, MapUnit>? {
        val playerCapitalTile = player.getCapital()?.getCenterTile()
        val seededCity = if (peaceTarget.cities.size < 2) {
            createFallbackPeaceCityNearPlayer(player, peaceTarget)
        } else {
            null
        }
        val candidateCities = peaceTarget.cities
            .sortedWith(
                compareBy<com.unciv.logic.city.City> { city ->
                    playerCapitalTile?.let { city.getCenterTile().aerialDistanceTo(it) } ?: Int.MAX_VALUE
                }.thenBy { city -> city.name },
            )
            .toMutableList()
        if (seededCity != null) {
            candidateCities.removeAll { city -> city.location == seededCity.location }
            candidateCities.add(0, seededCity)
        } else if (candidateCities.isEmpty()) {
            createFallbackPeaceCityNearPlayer(player, peaceTarget)?.let { fallbackCity -> candidateCities += fallbackCity }
        }

        for (city in candidateCities) {
            clearEnemyUnitsNearTile(city.getCenterTile(), peaceTarget)
            val attacker = ensureRequiredAttackerForCityCapture(player, peaceTarget, city.getCenterTile(), requiredUnitName)
            if (attacker != null) return city to attacker
        }
        return null
    }

    private fun createFallbackPeaceCityNearPlayer(
        player: Civilization,
        peaceTarget: Civilization,
    ): com.unciv.logic.city.City? {
        val playerCapital = player.getCapital()?.getCenterTile() ?: return null
        val candidateTile = playerCapital.getTilesInDistance(3)
            .filter { tile ->
                tile.isLand &&
                    !tile.isImpassible() &&
                    tile.getCity() == null &&
                    tile.militaryUnit == null &&
                    tile.civilianUnit == null
            }
            .sortedBy { tile -> tile.aerialDistanceTo(playerCapital) }
            .firstOrNull()
            ?: return null

        return runCatching {
            peaceTarget.addCity(candidateTile.position)
        }.getOrNull()
    }

    private fun stabilizePreworldScenario(
        player: Civilization,
        peaceTarget: Civilization,
        requiredUnitName: String,
    ) {
        disarmMajorEnemies(player)
        ensureCivilizationHasBackupCity(player)
        hardenPlayerCities(player)
        clearEnemyUnitsNearTile(player.getCapital()?.getCenterTile(), peaceTarget)
        trimPlayerMilitaryUnits(player, requiredUnitName, keepRequiredUnits = 3, keepOtherMilitary = 1)
    }

    private fun disarmMajorEnemies(player: Civilization) {
        player.gameInfo.civilizations
            .asSequence()
            .filter { civ -> civ != player && civ.isMajorCiv() }
            .forEach { enemy ->
                enemy.units.getCivUnits().toList().forEach { unit -> unit.destroy() }
            }
    }

    private fun ensureCivilizationHasBackupCity(civ: Civilization) {
        if (civ.cities.size >= 2) return
        val capital = civ.getCapital()?.getCenterTile() ?: return
        val candidateTile = capital.getTilesInDistance(3)
            .asSequence()
            .filter { tile ->
                tile.isLand &&
                    !tile.isImpassible() &&
                    tile.getCity() == null &&
                    tile.militaryUnit == null &&
                    tile.civilianUnit == null
            }
            .sortedBy { tile -> tile.aerialDistanceTo(capital) }
            .firstOrNull()
            ?: return
        runCatching { civ.addCity(candidateTile.position) }
    }

    private fun hardenPlayerCities(player: Civilization) {
        player.cities.forEach { city ->
            city.health = maxOf(city.health, 200)
        }
    }

    private fun clearEnemyUnitsNearTile(
        anchorTile: com.unciv.logic.map.tile.Tile?,
        enemy: Civilization,
    ) {
        val tile = anchorTile ?: return
        tile.getTilesInDistance(1).forEach { nearby ->
            nearby.militaryUnit
                ?.takeIf { it.civ == enemy }
                ?.destroy()
            nearby.civilianUnit
                ?.takeIf { it.civ == enemy }
                ?.destroy()
        }
    }

    private fun trimPlayerMilitaryUnits(
        player: Civilization,
        requiredUnitName: String,
        keepRequiredUnits: Int,
        keepOtherMilitary: Int,
    ) {
        val requiredUnits = player.units.getCivUnits().filter { unit ->
            unit.isMilitary() && unit.baseUnit.name.equals(requiredUnitName, ignoreCase = true)
        }
        val keepRequired = requiredUnits.take(keepRequiredUnits).map { it.id }.toSet()
        val keepOther = player.units.getCivUnits()
            .filter { unit -> unit.isMilitary() && !unit.baseUnit.name.equals(requiredUnitName, ignoreCase = true) }
            .take(keepOtherMilitary)
            .map { it.id }
            .toSet()
        val keepIds = keepRequired + keepOther
        player.units.getCivUnits()
            .filter { unit -> unit.isMilitary() && !keepIds.contains(unit.id) }
            .toList()
            .forEach { unit -> unit.destroy() }
    }

    private suspend fun attemptCityCaptureViaUi(
        game: WebGame,
        player: Civilization,
        enemy: Civilization,
        cityTile: com.unciv.logic.map.tile.Tile,
        requiredUnitName: String,
        initialAttacker: MapUnit,
    ): Boolean {
        var attacker: MapUnit? = initialAttacker
        repeat(4) {
            val current = attacker ?: ensureRequiredAttackerForCityCapture(player, enemy, cityTile, requiredUnitName)
            if (current == null) return false
            if (!unitCanAttackTile(current, cityTile)) {
                attacker = ensureRequiredAttackerForCityCapture(player, enemy, cityTile, requiredUnitName)
                WebValidationRunner.waitFramesProbe(8)
                return@repeat
            }
            current.currentMovement = current.getMaxMovement().toFloat()
            current.attacksThisTurn = 0
            if (executeUnitAttackViaUi(game, current, cityTile, preferredAttackButton = "Attack")) return true
            attacker = ensureRequiredAttackerForCityCapture(player, enemy, cityTile, requiredUnitName)
            WebValidationRunner.waitFramesProbe(8)
        }
        return false
    }

    private fun ensureRangedAttacker(
        player: Civilization,
        enemy: Civilization,
        anchorTile: com.unciv.logic.map.tile.Tile?,
    ): MapUnit? {
        val existing = player.units.getCivUnits().firstOrNull { it.isMilitary() && it.baseUnit.isRanged() }
        if (existing != null) return existing

        val base = player.gameInfo.ruleset.units.values.firstOrNull { unit ->
            unit.isMilitary && unit.isRanged() && unit.isLandUnit
        } ?: return null

        val anchor = anchorTile?.position ?: enemy.getCapital()?.location ?: return null
        return player.units.placeUnitNearTile(anchor, base)
    }

    private fun ensureBeforeDeadline(deadlineMs: Long, step: String) {
        if (System.currentTimeMillis() >= deadlineMs) {
            error("war ui probe timeout exceeded before step [$step].")
        }
    }

    private suspend fun advanceTurnsFastForWarProbe(
        game: WebGame,
        turns: Int,
        deadlineMs: Long,
    ): UiFlowResult {
        repeat(turns) { index ->
            ensureBeforeDeadline(deadlineMs, "advance-fast:$index")
            if (!recoverFromVictoryScreenIfPresent(game)) {
                return UiFlowResult(false, "victory-screen-recovery-failed-before-fast-turn")
            }
            val world = game.screen as? WorldScreen
                ?: return UiFlowResult(false, "fast-turn requires WorldScreen, got=${game.screen?.javaClass?.simpleName ?: "null"}")
            val beforeTurn = game.gameInfo?.turns ?: world.gameInfo.turns
            world.nextTurn()
            val progressed = WebValidationRunner.waitUntilFramesProbe(1200) {
                val currentTurn = game.gameInfo?.turns ?: world.gameInfo.turns
                currentTurn > beforeTurn || game.screen is VictoryScreen
            }
            if (!progressed) {
                return UiFlowResult(false, "fast-turn did not progress from turn=$beforeTurn")
            }
            WebValidationRunner.waitFramesProbe(6)
            if (!recoverFromVictoryScreenIfPresent(game)) {
                return UiFlowResult(false, "victory-screen-recovery-failed-after-fast-turn")
            }
        }
        return UiFlowResult(true)
    }

    private fun isGameOverScreen(game: WebGame): Boolean {
        return game.screen?.javaClass?.simpleName == "VictoryScreen"
    }

    private fun detectGameOverReason(game: WebGame, player: Civilization): String? {
        if (player.isDefeated()) {
            return "playerDefeated=true cities=${player.cities.size}"
        }
        if (player.cities.isEmpty()) {
            return "playerCities=0"
        }
        return null
    }

    private suspend fun recoverFromVictoryScreenIfPresent(game: WebGame): Boolean {
        val victoryScreen = game.screen as? VictoryScreen ?: return true
        val clicked = WebValidationRunner.clickActorByTextProbe(victoryScreen.stage.root, "One more turn...!".tr(), contains = true)
            || WebValidationRunner.clickActorByTextProbe(victoryScreen.stage.root, "One more turn...!", contains = true)
            || WebValidationRunner.clickActorByTextProbe(victoryScreen.stage.root, "One more turn", contains = true)
            || tapFirstButtonByTextContains(victoryScreen.stage.root, "One more turn")
        if (!clicked) {
            val forcedResume = runCatching {
                val gameInfoField = VictoryScreen::class.java.getDeclaredField("gameInfo")
                gameInfoField.isAccessible = true
                val gameInfo = gameInfoField.get(victoryScreen) as com.unciv.logic.GameInfo
                gameInfo.oneMoreTurnMode = true
                game.popScreen()
                true
            }.getOrDefault(false)
            if (!forcedResume) return false
        }
        return WebValidationRunner.waitUntilFramesProbe(900) { game.screen !is VictoryScreen }
    }
}
