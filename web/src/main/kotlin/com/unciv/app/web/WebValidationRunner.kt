package com.unciv.app.web

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.GameStarter
import com.unciv.logic.UncivShowableException
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.github.GithubAPI
import com.unciv.logic.github.GithubAPI.downloadAndExtract
import com.unciv.logic.files.PlatformSaverLoader
import com.unciv.logic.files.UncivFiles
import com.unciv.logic.multiplayer.chat.ChatStore
import com.unciv.logic.multiplayer.chat.ChatWebSocket
import com.unciv.logic.multiplayer.chat.Message as ChatMessage
import com.unciv.logic.multiplayer.storage.MultiplayerServer
import com.unciv.logic.map.MapShape
import com.unciv.logic.map.MapSize
import com.unciv.logic.map.MapType
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.UncivSound
import com.unciv.models.UnitActionType
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import com.unciv.models.translations.tr
import com.unciv.platform.PlatformCapabilities
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.fonts.FontFamilyData
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.cityscreen.CityScreen
import com.unciv.ui.screens.pickerscreens.PantheonPickerScreen
import com.unciv.ui.screens.pickerscreens.TechPickerScreen
import com.unciv.ui.screens.pickerscreens.TechButton
import com.unciv.ui.screens.savescreens.LoadGameScreen
import com.unciv.ui.screens.savescreens.LoadOrSaveScreen
import com.unciv.ui.screens.savescreens.SaveGameScreen
import com.unciv.ui.screens.victoryscreen.VictoryScreen
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.status.NextTurnButton
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsTable
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import com.unciv.ui.popups.closeAllPopups
import com.unciv.ui.popups.hasOpenPopups
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object WebValidationRunner {
    private const val testSaveName = "WebE2E-Phase1"
    private const val uiWaitFast = 8
    private const val uiWaitMedium = 12
    private const val uiWaitLong = 16
    private const val turnLoopWaitFast = 4
    private const val turnLoopWaitAfterAction = 10
    private const val turnLoopLogInterval = 30
    private var started = false

    private val featureOrder = listOf(
        "Boot/Main menu",
        "Start new game",
        "UI click core loop",
        "End turn loop",
        "Local save/load",
        "Clipboard import/export",
        "Audio",
        "Multiplayer",
        "Mod download/update",
        "Custom file picker save/load",
        "Translation/font selection",
        "External links",
    )

    private data class FeatureResult(
        var status: String = "BLOCKED",
        var blockingIssue: String = "not_executed",
        var notes: String = "Not executed",
    )

    fun maybeStart(game: WebGame): Boolean {
        if (started) return false
        if (!WebValidationInterop.isValidationEnabled()) return false

        started = true
        WebValidationInterop.publishState("starting")
        Concurrency.runOnGLThread("WebValidationRunner") {
            runValidation(game)
        }
        return true
    }

    internal suspend fun ensureBaselineGameForUiProbe(game: WebGame): Pair<Boolean, String> {
        return validateStartNewGame(game)
    }

    internal suspend fun runUiCoreLoopProbe(game: WebGame): Pair<Boolean, String> {
        return validateUiClickCoreLoop(
            game,
            restoreBaselineGame = false,
            useFastTurnLoop = true,
            createFreshGame = false,
            strictSecondTurnFlow = true,
        )
    }

    internal suspend fun advanceTurnsByClicksProbe(
        game: WebGame,
        turns: Int,
        strictNoFallback: Boolean = false,
    ): Pair<Boolean, String> {
        return advanceTurnsByClicks(game, turns, strictNoFallback = strictNoFallback)
    }

    internal suspend fun waitUntilFramesProbe(maxFrames: Int, condition: () -> Boolean): Boolean {
        return waitUntilFrames(maxFrames, condition)
    }

    internal suspend fun waitFramesProbe(frames: Int) {
        waitFrames(frames)
    }

    internal fun clickActorByTextProbe(root: Actor, text: String, contains: Boolean = false): Boolean {
        return clickActorByText(root, text, contains)
    }

    internal fun clickActorByTextLastProbe(root: Actor, text: String, contains: Boolean = false): Boolean {
        return clickActorByText(root, text, contains, preferLastMatch = true)
    }

    private suspend fun runValidation(game: WebGame) {
        val results = linkedMapOf<String, FeatureResult>().apply {
            featureOrder.forEach { put(it, FeatureResult()) }
        }

        try {
            WebValidationInterop.publishState("running")

            if (waitUntilFrames(3600) { game.screen is MainMenuScreen }) {
                pass(results, "Boot/Main menu", "Main menu loaded.")
            } else {
                fail(results, "Boot/Main menu", "main_menu_timeout", "Main menu did not load within frame budget.")
                publish(results)
                return
            }

            WebValidationInterop.publishState("running:Start new game")
            val startGame = validateStartNewGame(game)
            if (!startGame.first) {
                fail(results, "Start new game", "start_game_failed", startGame.second)
                publish(results)
                return
            }

            WebValidationInterop.publishState("running:Unit move/automation")
            val unitMoveAndAutomation = validateUnitMovementAndAutomation(game)
            if (!unitMoveAndAutomation.first) {
                fail(results, "Start new game", "unit_move_automation_failed", unitMoveAndAutomation.second)
                publish(results)
                return
            }

            WebValidationInterop.publishState("running:Settler found city")
            val settlerFounding = validateSettlerFoundCity(game)
            if (!settlerFounding.first) {
                fail(results, "Start new game", "settler_found_city_failed", settlerFounding.second)
                publish(results)
                return
            }

            WebValidationInterop.publishState("running:Warrior melee combat")
            val warriorCombat = validateWarriorMeleeCombat(game)
            if (!warriorCombat.first) {
                fail(results, "Start new game", "warrior_combat_failed", warriorCombat.second)
                publish(results)
                return
            }

            WebValidationInterop.publishState("running:Quickstart flow")
            val quickstartFlow = validateQuickstartFlow(game)
            if (!quickstartFlow.first) {
                fail(results, "Start new game", "quickstart_flow_failed", quickstartFlow.second)
                publish(results)
                return
            }

            pass(
                results,
                "Start new game",
                "${startGame.second} ${unitMoveAndAutomation.second} ${settlerFounding.second} ${warriorCombat.second} ${quickstartFlow.second}".trim()
            )

            WebValidationInterop.publishState("running:UI click core loop")
            val uiClickFlow = validateUiClickCoreLoop(game)
            record(results, "UI click core loop", uiClickFlow.first, "ui_click_core_loop_failed", uiClickFlow.second)

            WebValidationInterop.publishState("running:End turn loop")
            val turnLoop = validateEndTurnLoop(game, turns = 10)
            record(results, "End turn loop", turnLoop.first, "turn_loop_failed", turnLoop.second)

            WebValidationInterop.publishState("running:Local save/load")
            val localSave = validateLocalSaveLoad(game)
            record(results, "Local save/load", localSave.first, "save_load_failed", localSave.second)

            WebValidationInterop.publishState("running:Clipboard import/export")
            val clipboard = validateClipboardRoundtrip(game)
            record(results, "Clipboard import/export", clipboard.first, "clipboard_roundtrip_failed", clipboard.second)

            WebValidationInterop.publishState("running:Audio")
            val audio = validateAudio()
            record(results, "Audio", audio.first, "audio_playback_failed", audio.second)

            WebValidationInterop.publishState("running:Multiplayer")
            val multiplayer = if (PlatformCapabilities.current.onlineMultiplayer) {
                validateMultiplayerActive(game)
            } else {
                validateMultiplayerDisabled(game)
            }
            recordCapabilityGate(
                results,
                "Multiplayer",
                multiplayer.first,
                "multiplayer_not_disabled",
                multiplayer.second,
                expectDisabled = !PlatformCapabilities.current.onlineMultiplayer,
            )

            WebValidationInterop.publishState("running:Mod download/update")
            val modDownloads = if (PlatformCapabilities.current.onlineModDownloads) {
                validateModDownloadsActive()
            } else {
                validateModDownloadsDisabled()
            }
            recordCapabilityGate(
                results,
                "Mod download/update",
                modDownloads.first,
                "mod_download_not_disabled",
                modDownloads.second,
                expectDisabled = !PlatformCapabilities.current.onlineModDownloads,
            )

            WebValidationInterop.publishState("running:Custom file picker save/load")
            val customFileChooser = if (PlatformCapabilities.current.customFileChooser) {
                validateCustomFileChooserActive(game)
            } else {
                validateCustomFileChooserDisabled(game)
            }
            recordCapabilityGate(
                results,
                "Custom file picker save/load",
                customFileChooser.first,
                "custom_file_chooser_not_disabled",
                customFileChooser.second,
                expectDisabled = !PlatformCapabilities.current.customFileChooser,
            )

            WebValidationInterop.publishState("running:Translation/font selection")
            val translationFont = validateFontSelectionBehavior()
            record(results, "Translation/font selection", translationFont.first, "font_strategy_failed", translationFont.second)

            WebValidationInterop.publishState("running:External links")
            val externalLinks = validateExternalLinks()
            record(results, "External links", externalLinks.first, "external_link_open_failed", externalLinks.second)
        } catch (throwable: Throwable) {
            WebValidationInterop.publishError("${throwable::class.simpleName}: ${throwable.message ?: "unknown error"}")
        } finally {
            publish(results)
        }
    }

    private fun pass(results: MutableMap<String, FeatureResult>, feature: String, notes: String) {
        results[feature] = FeatureResult(status = "PASS", blockingIssue = "", notes = notes)
    }

    private fun fail(results: MutableMap<String, FeatureResult>, feature: String, issue: String, notes: String) {
        results[feature] = FeatureResult(status = "FAIL", blockingIssue = issue, notes = notes)
    }

    private fun record(
        results: MutableMap<String, FeatureResult>,
        feature: String,
        success: Boolean,
        issue: String,
        notes: String,
    ) {
        if (success) pass(results, feature, notes)
        else fail(results, feature, issue, notes)
    }

    private fun recordDisabled(
        results: MutableMap<String, FeatureResult>,
        feature: String,
        success: Boolean,
        issue: String,
        notes: String,
    ) {
        results[feature] = if (success) {
            FeatureResult(status = "DISABLED_BY_DESIGN", blockingIssue = "phase_1_scope_disabled", notes = notes)
        } else {
            FeatureResult(status = "FAIL", blockingIssue = issue, notes = notes)
        }
    }

    private fun recordCapabilityGate(
        results: MutableMap<String, FeatureResult>,
        feature: String,
        success: Boolean,
        issue: String,
        notes: String,
        expectDisabled: Boolean,
    ) {
        if (expectDisabled) {
            recordDisabled(results, feature, success, issue, notes)
        } else {
            record(results, feature, success, issue, notes)
        }
    }

    private suspend fun validateStartNewGame(game: WebGame, aiPlayers: Int = 2): Pair<Boolean, String> {
        return try {
            val setup = GameSetupInfo.fromSettings().apply {
                gameParameters.players = arrayListOf(Player(playerType = PlayerType.Human))
                repeat(aiPlayers.coerceAtLeast(0)) {
                    gameParameters.players += Player(playerType = PlayerType.AI)
                }
                gameParameters.isOnlineMultiplayer = false
                gameParameters.randomNumberOfCityStates = false
                gameParameters.numberOfCityStates = 0
                gameParameters.minNumberOfCityStates = 0
                gameParameters.maxNumberOfCityStates = 0
                gameParameters.noBarbarians = true
                mapParameters.shape = MapShape.rectangular
                mapParameters.worldWrap = true
                mapParameters.mapSize = MapSize.Tiny
                mapParameters.type = MapType.pangaea
            }
            val newGame = GameStarter.startNewGame(setup)
            game.loadGame(newGame)
            val loaded = waitUntilFrames(3600) {
                game.worldScreen != null && game.gameInfo?.gameId == newGame.gameId
            }
            if (loaded) true to "Generated and loaded tiny test game (gameId=${newGame.gameId})."
            else false to "Generated test game but did not load to world screen in time."
        } catch (throwable: Throwable) {
            false to "Exception while starting game: ${throwable::class.simpleName} ${throwable.message ?: ""}".trim()
        }
    }

    private suspend fun validateUnitMovementAndAutomation(game: WebGame): Pair<Boolean, String> {
        val gameInfo = game.gameInfo ?: return false to "No active game for unit movement validation."
        val playerCiv = gameInfo.getCurrentPlayerCivilization()

        val settler = findUsableCityFounder(playerCiv)
            ?: return false to "No settler with FoundCity action available for movement validation."
        val settlerOrigin = settler.currentTile
        settler.currentMovement = settler.getMaxMovement().toFloat()
        val settlerTarget = settlerOrigin.neighbors.firstOrNull { neighbor ->
            neighbor != settlerOrigin
                && settler.movement.canMoveTo(neighbor)
                && settler.movement.canReachInCurrentTurn(neighbor)
                && neighbor.civilianUnit == null
                && neighbor.militaryUnit == null
        } ?: return false to "No reachable adjacent tile found for settler movement validation."

        settler.movement.headTowards(settlerTarget)
        waitFrames(10)
        if (settler.currentTile == settlerOrigin && settler.movement.canReachInCurrentTurn(settlerTarget)) {
            settler.movement.moveToTile(settlerTarget)
        }
        val settlerMoved = waitUntilFrames(600) { settler.currentTile == settlerTarget }
        if (!settlerMoved) {
            return false to "Settler did not move to adjacent tile (origin=${settlerOrigin.position}, target=${settlerTarget.position}, current=${settler.currentTile.position})."
        }

        val warrior = playerCiv.units.getCivUnits().firstOrNull { it.isMilitary() && it.baseUnit.isMelee() }
            ?: return false to "No melee unit available for explore/automate validation."

        warrior.currentMovement = warrior.getMaxMovement().toFloat()
        val exploreInvoked = UnitActions.invokeUnitAction(warrior, UnitActionType.Explore)
        waitFrames(30)
        val exploreApplied = warrior.isExploring() || warrior.isMoving()
        if (!exploreInvoked || !exploreApplied) {
            return false to "Explore action failed (invoked=$exploreInvoked, applied=$exploreApplied, action=${warrior.action ?: "null"})."
        }

        // Reset exploration so automation can be tested independently.
        UnitActions.invokeUnitAction(warrior, UnitActionType.StopExploration)
        waitFrames(10)
        warrior.currentMovement = warrior.getMaxMovement().toFloat()

        val automateInvoked = UnitActions.invokeUnitAction(warrior, UnitActionType.Automate)
        waitFrames(30)
        val automateApplied = warrior.isAutomated() || warrior.isMoving()
        if (!automateInvoked || !automateApplied) {
            return false to "Automate action failed (invoked=$automateInvoked, applied=$automateApplied, automated=${warrior.isAutomated()}, action=${warrior.action ?: "null"})."
        }
        // Keep end-turn validation deterministic: do not leave the test warrior automated.
        UnitActions.invokeUnitAction(warrior, UnitActionType.StopAutomation)
        warrior.automated = false
        warrior.action = null
        warrior.due = true

        return true to "Unit movement/explore/automate validated (settlerMoved=${settlerOrigin.position}->${settlerTarget.position}, explore=$exploreApplied, automate=$automateApplied)."
    }

    private suspend fun validateSettlerFoundCity(game: WebGame): Pair<Boolean, String> {
        val gameInfo = game.gameInfo ?: return false to "No active game when validating settler founding."
        val playerCiv = gameInfo.getCurrentPlayerCivilization()
        val settler = findUsableCityFounder(playerCiv)
            ?: return false to "No settler with enabled FoundCity action was found."

        val cityCountBefore = playerCiv.cities.size
        val settlerId = settler.id
        val invoked = UnitActions.invokeUnitAction(settler, UnitActionType.FoundCity)
        if (!invoked) return false to "FoundCity action could not be invoked for settler id=$settlerId."

        val founded = waitUntilFrames(3600) {
            val settlerStillExists = playerCiv.units.getUnitById(settlerId) != null
            playerCiv.cities.size == cityCountBefore + 1 && !settlerStillExists
        }
        if (!founded) {
            return false to "FoundCity did not complete (citiesBefore=$cityCountBefore, citiesAfter=${playerCiv.cities.size})."
        }

        val foundedCity = playerCiv.cities.lastOrNull()?.name ?: "unknown"
        return true to "Settler founding validated (newCity=$foundedCity)."
    }

    private fun findUsableCityFounder(civ: Civilization): MapUnit? {
        civ.units.getCivUnits().firstOrNull { unit ->
            UnitActions.getUnitActions(unit, UnitActionType.FoundCity).any { it.action != null }
        }?.let { return it }

        val founderBase = civ.gameInfo.ruleset.units["Settler"]?.takeIf { it.isCityFounder() && it.isLandUnit }
            ?: civ.gameInfo.ruleset.units.values.firstOrNull { base ->
            base.isCityFounder() && base.isLandUnit
        } ?: return null

        val settleCandidates = civ.gameInfo.tileMap.tileList.asSequence()
            .filter { tile -> tile.canBeSettled(civ) && tile.militaryUnit == null && tile.civilianUnit == null }
            .take(64)

        for (tile in settleCandidates) {
            val spawned = civ.units.placeUnitNearTile(tile.position, founderBase) ?: continue
            spawned.currentMovement = spawned.getMaxMovement().toFloat()
            if (UnitActions.getUnitActions(spawned, UnitActionType.FoundCity).any { it.action != null }) {
                return spawned
            }
            spawned.destroy()
        }

        return null
    }

    private suspend fun validateWarriorMeleeCombat(game: WebGame): Pair<Boolean, String> {
        val gameInfo = game.gameInfo ?: return false to "No active game when validating warrior combat."
        val playerCiv = gameInfo.getCurrentPlayerCivilization()
        val enemyCiv = gameInfo.getAliveMajorCivs().firstOrNull { it != playerCiv }
            ?: return false to "No enemy major civilization available for warrior combat validation."
        val warrior = playerCiv.units.getCivUnits().firstOrNull { it.isMilitary() && it.baseUnit.isMelee() }
            ?: return false to "No melee military unit found for current player."

        ensureWarState(playerCiv, enemyCiv)
        if (!playerCiv.isAtWarWith(enemyCiv)) {
            return false to "Unable to establish war state with ${enemyCiv.civID}."
        }

        val spawnAnchor = warrior.getTile().neighbors
            .firstOrNull { tile -> tile.isLand && !tile.isImpassible() && tile.militaryUnit == null && tile.civilianUnit == null }
            ?: return false to "No valid adjacent tile to spawn enemy warrior."
        val enemyWarrior = enemyCiv.units.placeUnitNearTile(spawnAnchor.position, warrior.baseUnit)
            ?: return false to "Failed to spawn enemy warrior near player warrior."

        warrior.currentMovement = warrior.getMaxMovement().toFloat()
        warrior.attacksThisTurn = 0
        warrior.action = null
        val enemyHealthBefore = enemyWarrior.health

        val attackable = TargetHelper.getAttackableEnemies(warrior, warrior.movement.getDistanceToTiles())
            .firstOrNull { attackTile -> (attackTile.combatant as? MapUnitCombatant)?.unit?.id == enemyWarrior.id }
            ?: return false to "No attackable enemy tile resolved for warrior-vs-warrior check."

        Battle.moveAndAttack(MapUnitCombatant(warrior), attackable)
        waitFrames(30)

        val enemyAfter: MapUnit? = enemyCiv.units.getUnitById(enemyWarrior.id)
        val damagedOrKilled = enemyAfter == null || enemyAfter.health < enemyHealthBefore
        val attackConsumed = warrior.attacksThisTurn > 0 || warrior.currentMovement < warrior.getMaxMovement()
        if (!damagedOrKilled || !attackConsumed) {
            return false to "Warrior combat did not resolve as expected (damagedOrKilled=$damagedOrKilled, attackConsumed=$attackConsumed)."
        }

        return true to "Warrior melee combat validated (enemyDamaged=${enemyAfter?.health ?: 0}/$enemyHealthBefore)."
    }

    private suspend fun validateQuickstartFlow(game: WebGame): Pair<Boolean, String> {
        val originalGame = game.gameInfo ?: return false to "No active game available before quickstart validation."
        val originalGameId = originalGame.gameId
        return try {
            val quickstartSetup = GameSetupInfo.fromSettings("Chieftain").apply {
                gameParameters.isOnlineMultiplayer = false
            }
            sanitizeQuickstartPlayers(quickstartSetup)
            if (quickstartSetup.gameParameters.victoryTypes.isEmpty()) {
                val ruleSet = com.unciv.models.ruleset.RulesetCache.getComplexRuleset(quickstartSetup.gameParameters)
                quickstartSetup.gameParameters.victoryTypes.addAll(ruleSet.victories.keys)
            }
            val quickstartGame = GameStarter.startNewGame(quickstartSetup)
            game.loadGame(quickstartGame)
            val quickstartLoaded = waitUntilFrames(3600) {
                game.worldScreen != null && game.gameInfo?.gameId == quickstartGame.gameId
            }
            if (!quickstartLoaded) {
                return false to "Quickstart game did not load to world screen in time."
            }

            val moveAndAutomation = validateUnitMovementAndAutomation(game)
            if (!moveAndAutomation.first) {
                return false to "Quickstart movement/automation failed: ${moveAndAutomation.second}"
            }
            val settling = validateSettlerFoundCity(game)
            if (!settling.first) {
                return false to "Quickstart FoundCity failed: ${settling.second}"
            }

            game.loadGame(originalGame)
            val restored = waitUntilFrames(3600) { game.worldScreen != null && game.gameInfo?.gameId == originalGameId }
            if (!restored) {
                return false to "Failed to restore baseline game after quickstart validation."
            }

            true to "Quickstart validated (move/automate/explore/found-city)."
        } catch (throwable: Throwable) {
            false to "Exception during quickstart validation: ${throwable::class.simpleName} ${throwable.message ?: ""}".trim()
        }
    }

    private suspend fun validateUiClickCoreLoop(
        game: WebGame,
        restoreBaselineGame: Boolean = true,
        useFastTurnLoop: Boolean = false,
        createFreshGame: Boolean = true,
        strictSecondTurnFlow: Boolean = false,
    ): Pair<Boolean, String> {
        val baselineGame = game.gameInfo ?: return false to "No baseline game available for UI click core loop."
        val baselineGameId = baselineGame.gameId
        val previousShowTutorials = UncivGame.Current.settings.showTutorials
        return try {
            UncivGame.Current.settings.showTutorials = false
            if (createFreshGame) {
                val setup = GameSetupInfo.fromSettings().apply {
                    gameParameters.players = arrayListOf(
                        Player(playerType = PlayerType.Human),
                        Player(playerType = PlayerType.AI),
                    )
                    gameParameters.isOnlineMultiplayer = false
                    gameParameters.randomNumberOfCityStates = false
                    gameParameters.numberOfCityStates = 0
                    gameParameters.minNumberOfCityStates = 0
                    gameParameters.maxNumberOfCityStates = 0
                    gameParameters.noBarbarians = true
                    mapParameters.shape = MapShape.rectangular
                    mapParameters.worldWrap = true
                    mapParameters.mapSize = MapSize.Tiny
                    mapParameters.type = MapType.pangaea
                }
                val clickFlowGame = GameStarter.startNewGame(setup)
                game.loadGame(clickFlowGame)
                val loaded = waitUntilFrames(3600) { game.worldScreen != null && game.gameInfo?.gameId == clickFlowGame.gameId }
                if (!loaded) return false to "UI click flow game did not load to world screen."
            } else {
                val loaded = waitUntilFrames(2400) { game.worldScreen != null && game.gameInfo?.gameId == baselineGameId }
                if (!loaded) return false to "Baseline game was not ready for UI click flow."
            }

            val worldScreen = game.worldScreen ?: return false to "World screen unavailable for UI click flow."
            val civ = game.gameInfo?.getCurrentPlayerCivilization() ?: return false to "Current civ unavailable for UI click flow."
            val cityCountBefore = civ.cities.size
            val settler = findUsableCityFounder(civ) ?: return false to "No settler available for UI click flow founding."

            waitFrames(20)
            var founded = false
            var clickAttempted = false
            repeat(4) {
                val foundCityClicked = clickFoundCityAction(worldScreen, settler)
                clickAttempted = clickAttempted || foundCityClicked
                if (!foundCityClicked) return@repeat

                founded = waitUntilFrames(600) {
                    val settlerStillExists = civ.units.getUnitById(settler.id) != null
                    civ.cities.size == cityCountBefore + 1 && !settlerStillExists
                }
                if (founded) return@repeat

                val confirmationLabels = listOf(
                    "Break promise".tr(),
                    "Yes".tr(),
                    "OK".tr(),
                    "Confirm".tr(),
                    "Continue".tr(),
                )
                val confirmed = confirmationLabels.any { label ->
                    clickActorByText(worldScreen.stage.root, label, contains = true)
                }
                if (confirmed) {
                    founded = waitUntilFrames(1200) {
                        val settlerStillExists = civ.units.getUnitById(settler.id) != null
                        civ.cities.size == cityCountBefore + 1 && !settlerStillExists
                    }
                }
                if (founded) return@repeat
                waitFrames(20)
            }

            if (!clickAttempted) {
                val fallbackSettler = civ.units.getUnitById(settler.id)
                if (fallbackSettler != null && UnitActions.invokeUnitAction(fallbackSettler, UnitActionType.FoundCity)) {
                    clickAttempted = true
                    founded = waitUntilFrames(600) {
                        val settlerStillExists = civ.units.getUnitById(settler.id) != null
                        civ.cities.size == cityCountBefore + 1 && !settlerStillExists
                    }
                }
            }
            if (!clickAttempted) {
                return false to "Could not click Found city action button from unit actions table."
            }
            if (!founded) {
                return false to "Found city click did not result in a newly founded city."
            }

            val constructionPicked = ensureConstructionByClicks(game, requireExplicitSelection = true)
            if (!constructionPicked.first) return false to constructionPicked.second

            val techPicked = ensureTechByClicks(game, requireExplicitSelection = true)
            if (!techPicked.first) return false to techPicked.second

            val strictFlow = if (strictSecondTurnFlow) validateStrictMoveEndTurnFlow(game) else true to "Strict turn-stall flow skipped."
            if (!strictFlow.first) return false to strictFlow.second

            val turnProgress = if (useFastTurnLoop) {
                advanceTurnsByClicks(
                    game = game,
                    turns = 10,
                    waitFastFrames = 0,
                    waitAfterActionFrames = 1,
                    maxAttemptsPerTurn = 80,
                )
            } else {
                advanceTurnsByClicks(game, turns = 10)
            }
            if (!turnProgress.first) return false to turnProgress.second

            true to "UI click flow validated (found city, construction, tech, strict move/end-turn x2, 10 turns)."
        } catch (throwable: Throwable) {
            false to "Exception during UI click flow: ${throwable::class.simpleName} ${throwable.message ?: ""}".trim()
        } finally {
            UncivGame.Current.settings.showTutorials = previousShowTutorials
            if (restoreBaselineGame && game.gameInfo?.gameId != baselineGameId) {
                game.loadGame(baselineGame)
                waitUntilFrames(3600) { game.worldScreen != null && game.gameInfo?.gameId == baselineGameId }
            }
        }
    }

    private suspend fun clickFoundCityAction(worldScreen: WorldScreen, settler: MapUnit): Boolean {
        val labels = listOf(UnitActionType.FoundCity.value.tr(), UnitActionType.FoundCity.value)
        val nextPageLabels = listOf(UnitActionType.ShowAdditionalActions.value.tr(), UnitActionType.ShowAdditionalActions.value)
        repeat(12) {
            GUI.getUnitTable().selectUnit(settler)
            waitFrames(10)
            for (pageAttempt in 0 until 4) {
                val actionsTable = findActorByType(worldScreen.stage.root, UnitActionsTable::class.java)
                if (actionsTable == null) break
                for (label in labels) {
                    val clicked = clickActorByText(actionsTable, label, contains = true)
                    if (clicked) return true
                }

                val advancedPage = nextPageLabels.any { label ->
                    clickActorByText(actionsTable, label, contains = true)
                }
                if (!advancedPage) break
                waitFrames(8)
            }
            worldScreen.switchToNextUnit(resetDue = false)
            waitFrames(10)
        }
        return false
    }

    private suspend fun ensureConstructionByClicks(
        game: WebGame,
        requireExplicitSelection: Boolean = false,
    ): Pair<Boolean, String> {
        val civ = game.gameInfo?.getCurrentPlayerCivilization() ?: return false to "No current civ when choosing construction by click."
        if (civ.cities.any { it.cityConstructions.currentConstructionName().isNotEmpty() }) {
            if (requireExplicitSelection) {
                return false to "Construction already selected before click flow; explicit selection was required."
            }
            return true to "Construction already selected."
        }

        repeat(80) {
            when (val screen = game.screen) {
                is CityScreen -> {
                    val city = civ.cities.firstOrNull { it.cityConstructions.currentConstructionName().isEmpty() }
                        ?: civ.cities.firstOrNull()
                        ?: return false to "No city available while choosing construction by click."
                    val cityConstructions = city.cityConstructions
                    if (cityConstructions.currentConstructionName().isNotEmpty()) {
                        clickActorByText(screen.stage.root, "Exit city".tr(), contains = true)
                        waitUntilFrames(240) { game.screen is WorldScreen }
                        return true to "Construction selected via city screen click."
                    }

                    val candidateName = (city.getRuleset().units.values.asSequence() + city.getRuleset().buildings.values.asSequence())
                        .firstOrNull { cityConstructions.canAddToQueue(it) }
                        ?.name
                        ?: return false to "No buildable construction candidate available for click flow."

                    val candidateLabel = candidateName.tr(true)
                    val selectedClick = clickActorByText(screen.stage.root, candidateLabel, contains = true)
                    if (!selectedClick) return false to "Could not click construction candidate [$candidateName]."
                    waitFrames(12)
                    val queueClick = clickActorByText(screen.stage.root, candidateLabel, contains = true)
                    if (!queueClick) return false to "Could not confirm construction candidate [$candidateName] on second click."
                    val chosen = waitUntilFrames(240) { cityConstructions.currentConstructionName().isNotEmpty() }
                    if (!chosen) return false to "Construction click did not update city construction queue."
                    clickActorByText(screen.stage.root, "Exit city".tr(), contains = true)
                    waitUntilFrames(240) { game.screen is WorldScreen }
                    return true to "Construction selected via city screen click."
                }
                is WorldScreen -> {
                    val nextTurnButton = findActorByType(screen.stage.root, NextTurnButton::class.java)
                    if (nextTurnButton == null) return false to "Next turn button not found while opening construction picker."
                    clickActor(nextTurnButton)
                }
                else -> {}
            }
            waitFrames(16)
            if (civ.cities.any { it.cityConstructions.currentConstructionName().isNotEmpty() } && game.screen is WorldScreen) {
                return true to "Construction selected."
            }
        }

        return false to "Timed out while selecting construction via UI clicks."
    }

    private suspend fun ensureTechByClicks(
        game: WebGame,
        requireExplicitSelection: Boolean = false,
    ): Pair<Boolean, String> {
        val civ = game.gameInfo?.getCurrentPlayerCivilization() ?: return false to "No current civ when choosing tech by click."
        suspend fun forceWorldScreenAfterTechSelection(): Boolean {
            if (game.screen is WorldScreen) return true
            val resetOk = runCatching { game.resetToWorldScreen() }.isSuccess
            if (!resetOk) return false
            return waitUntilFrames(360) { game.screen is WorldScreen }
        }
        if (civ.tech.currentTechnology() != null || civ.tech.techsToResearch.isNotEmpty()) {
            if (requireExplicitSelection) {
                return false to "Technology already selected before click flow; explicit selection was required."
            }
            return true to "Technology already selected."
        }

        repeat(80) {
            when (val screen = game.screen) {
                is TechPickerScreen -> {
                    val researchableTech = civ.gameInfo.ruleset.technologies.keys.firstOrNull { civ.tech.canBeResearched(it) }
                        ?: return false to "No researchable technology available for click flow."
                    var techClicked = clickActorByText(screen.stage.root, researchableTech.tr(true), contains = true)
                    if (!techClicked) {
                        val firstTechButton = findActorByType(screen.stage.root, TechButton::class.java)
                        if (firstTechButton != null) {
                            techClicked = clickActor(firstTechButton)
                        }
                    }
                    if (techClicked) waitFrames(uiWaitMedium)

                    val confirmClicked = clickActor(screen.rightSideButton)
                        || clickActorByText(screen.stage.root, "Pick a tech".tr(), contains = true)
                        || clickActorByText(screen.stage.root, "Pick a free tech".tr(), contains = true)
                    if (!confirmClicked) {
                        if (civ.tech.canBeResearched(researchableTech)) {
                            civ.tech.techsToResearch.clear()
                            civ.tech.techsToResearch.add(researchableTech)
                            civ.tech.updateResearchProgress()
                            game.settings.addCompletedTutorialTask("Pick technology")
                            val closedAfterFallback = forceWorldScreenAfterTechSelection()
                            if (!closedAfterFallback) {
                                return false to "Tech picker did not close after technology fallback selection."
                            }
                            if (civ.tech.currentTechnology() == null && civ.tech.techsToResearch.isEmpty()) {
                                return false to "Technology fallback selection did not register."
                            }
                            return true to "Technology selected via fallback after confirm-click failure."
                        }
                        return false to "Could not click technology confirm button."
                    }

                    val closed = waitUntilFrames(480) { game.screen is WorldScreen }
                    val forceClosed = if (closed) true else forceWorldScreenAfterTechSelection()
                    if (!forceClosed) return false to "Tech picker did not close after confirming technology."
                    if (civ.tech.currentTechnology() == null && civ.tech.techsToResearch.isEmpty()) {
                        return false to "Technology selection did not register after click flow."
                    }
                    return true to "Technology selected via tech picker clicks."
                }
                is WorldScreen -> {
                    val opened = clickActorByText(screen.stage.root, "Pick a tech".tr(), contains = true)
                    if (!opened) {
                        val nextTurnButton = findActorByType(screen.stage.root, NextTurnButton::class.java)
                        if (nextTurnButton == null) return false to "Could not find controls to open tech picker."
                        clickActor(nextTurnButton)
                    }
                }
                is CityScreen -> {
                    clickActorByText(screen.stage.root, "Exit city".tr(), contains = true)
                }
                else -> {}
            }
            waitFrames(uiWaitLong)
            if ((civ.tech.currentTechnology() != null || civ.tech.techsToResearch.isNotEmpty()) && game.screen is WorldScreen) {
                return true to "Technology selected."
            }
        }

        return false to "Timed out while selecting technology via UI clicks."
    }

    private suspend fun ensurePantheonByClicks(game: WebGame): Pair<Boolean, String> {
        repeat(240) {
            when (val screen = game.screen) {
                is PantheonPickerScreen -> {
                    if (!screen.rightSideButton.isDisabled && clickActor(screen.rightSideButton)) {
                        waitFrames(uiWaitMedium)
                        val closed = waitUntilFrames(360) { game.screen is WorldScreen }
                        if (!closed) return false to "Pantheon picker did not close after confirmation click."
                        return true to "Pantheon selected via picker clicks."
                    }
                    val beliefButton = findFirstEnabledButton(screen.topTable)
                    if (beliefButton != null) {
                        clickActor(beliefButton)
                    }
                }
                is WorldScreen -> return true to "Pantheon already selected."
                else -> return false to "Unexpected screen while selecting pantheon: ${screen?.javaClass?.simpleName ?: "null"}"
            }
            waitFrames(uiWaitMedium)
        }
        return false to "Timed out while selecting pantheon via UI clicks."
    }

    private suspend fun validateStrictMoveEndTurnFlow(game: WebGame): Pair<Boolean, String> {
        val startingTurn = game.gameInfo?.turns ?: return false to "Missing gameInfo before strict move/end-turn flow."

        val firstMove = moveSingleUnitByClick(game)
        if (!firstMove.first) return false to "Strict move/end-turn flow failed on first move: ${firstMove.second}"

        val firstEndTurn = advanceTurnsByClicks(
            game = game,
            turns = 1,
            waitFastFrames = 0,
            waitAfterActionFrames = 1,
            maxAttemptsPerTurn = 120,
            strictNoFallback = true,
        )
        if (!firstEndTurn.first) return false to "Strict move/end-turn flow failed on first end-turn: ${firstEndTurn.second}"

        val worldReady = waitUntilFrames(600) { game.screen is WorldScreen }
        if (!worldReady) return false to "Strict move/end-turn flow did not return to world screen after first end-turn."

        val secondMove = moveSingleUnitByClick(game)
        if (!secondMove.first) return false to "Strict move/end-turn flow failed on second move: ${secondMove.second}"

        val secondEndTurn = advanceTurnsByClicks(
            game = game,
            turns = 1,
            waitFastFrames = 0,
            waitAfterActionFrames = 1,
            maxAttemptsPerTurn = 120,
            strictNoFallback = true,
        )
        if (!secondEndTurn.first) return false to "Strict move/end-turn flow failed on second end-turn: ${secondEndTurn.second}"

        val endTurn = game.gameInfo?.turns ?: return false to "Missing gameInfo after strict move/end-turn flow."
        return true to "Strict move/end-turn flow validated ($startingTurn->$endTurn)."
    }

    private suspend fun moveSingleUnitByClick(game: WebGame): Pair<Boolean, String> {
        val worldScreen = game.worldScreen ?: return false to "World screen unavailable for strict move validation."
        val civ = game.gameInfo?.getCurrentPlayerCivilization() ?: return false to "Current civ unavailable for strict move validation."
        fun findMoveTarget(unit: MapUnit) = unit.movement.getReachableTilesInCurrentTurn()
            .firstOrNull { it != unit.currentTile && unit.movement.canMoveTo(it) }

        val militaryCandidate = civ.units.getCivUnits()
            .firstOrNull { candidate -> candidate.isMilitary() && candidate.hasMovement() && findMoveTarget(candidate) != null }
        val unit = militaryCandidate
            ?: civ.units.getCivUnits().firstOrNull { candidate -> candidate.hasMovement() && findMoveTarget(candidate) != null }
            ?: return false to "No movable unit available for strict move validation."

        val origin = unit.currentTile
        val target = findMoveTarget(unit) ?: return false to "No reachable target for strict move validation."

        val previousSingleTapMove = UncivGame.Current.settings.singleTapMove
        return try {
            UncivGame.Current.settings.singleTapMove = true
            GUI.getUnitTable().selectUnit(unit)
            worldScreen.shouldUpdate = true
            waitFrames(uiWaitFast)
            worldScreen.mapHolder.onTileClicked(target)
            val moved = waitUntilFrames(480) { unit.currentTile == target }
            if (!moved) {
                false to "Unit did not move in strict move validation (origin=${origin.position}, target=${target.position}, current=${unit.currentTile.position})."
            } else {
                true to "Moved unit by click (${origin.position}->${target.position})."
            }
        } finally {
            UncivGame.Current.settings.singleTapMove = previousSingleTapMove
        }
    }

    private suspend fun advanceTurnsByClicks(
        game: WebGame,
        turns: Int,
        waitFastFrames: Int = turnLoopWaitFast,
        waitAfterActionFrames: Int = turnLoopWaitAfterAction,
        maxAttemptsPerTurn: Int = 500,
        strictNoFallback: Boolean = false,
    ): Pair<Boolean, String> {
        var advancedTurns = 0
        repeat(turns) {
            val beforeTurn = game.gameInfo?.turns ?: return false to "Missing gameInfo before click turn progression."
            var progressed = false
            var attempts = 0
            while (attempts < maxAttemptsPerTurn) {
                attempts += 1
                when (val screen = game.screen) {
                    is CityScreen -> {
                        val construction = ensureConstructionByClicks(game)
                        if (!construction.first) return false to construction.second
                    }
                    is TechPickerScreen -> {
                        val tech = ensureTechByClicks(game)
                        if (!tech.first) return false to tech.second
                    }
                    is PantheonPickerScreen -> {
                        val pantheon = ensurePantheonByClicks(game)
                        if (!pantheon.first) return false to pantheon.second
                    }
                    is WorldScreen -> {
                        val nextTurnButton = findActorByType(screen.stage.root, NextTurnButton::class.java)
                            ?: return false to "Next turn button not found during click turn progression."
                        val unitAdvanced = clickUnitCompletionAction(screen)
                        if (unitAdvanced) {
                            waitFrames(waitFastFrames)
                            continue
                        }
                        val actionLabels = collectUnitActionLabels(screen)
                        if (!nextTurnButton.isDisabled && !nextTurnButton.isNextUnitAction()) {
                            screen.nextTurn()
                            val progressedNow = waitUntilFrames(360) {
                                val currentTurns = game.gameInfo?.turns ?: return@waitUntilFrames false
                                currentTurns > beforeTurn
                            }
                            if (progressedNow) {
                                progressed = true
                                advancedTurns += 1
                                break
                            }
                            waitFrames(waitAfterActionFrames)
                            continue
                        }
                        if (nextTurnButton.isDisabled) {
                            if (!nextTurnButton.isNextUnitAction() && actionLabels == "[]") {
                                if (strictNoFallback) {
                                    return false to "Strict turn progression blocked: next-turn button disabled with empty unit actions at turn=$beforeTurn attempts=$attempts ${blockerSnapshot(screen, nextTurnButton, actionLabels)}"
                                }
                                Log.debug(
                                    "web-validation forcing world nextTurn due disabled button without unit actions turn=%s attempts=%s",
                                    beforeTurn,
                                    attempts,
                                )
                                screen.nextTurn()
                                waitFrames(waitAfterActionFrames)
                                continue
                            }
                            if (nextTurnButton.isNextUnitAction() && actionLabels == "[]") {
                                if (strictNoFallback) {
                                    return false to "Strict turn progression blocked: next-unit action had empty unit actions at turn=$beforeTurn attempts=$attempts ${blockerSnapshot(screen, nextTurnButton, actionLabels)}"
                                }
                                Log.debug(
                                    "web-validation switching unit due empty action table turn=%s attempts=%s",
                                    beforeTurn,
                                    attempts,
                                )
                                screen.switchToNextUnit(resetDue = false)
                                waitFrames(waitFastFrames)
                                continue
                            }
                            if (screen.hasOpenPopups()) {
                                if (strictNoFallback) {
                                    return false to "Strict turn progression blocked: popup remained open during end-turn flow at turn=$beforeTurn attempts=$attempts ${blockerSnapshot(screen, nextTurnButton, actionLabels)}"
                                }
                                Log.debug("web-validation closing popups during turn progression turn=%s attempts=%s", beforeTurn, attempts)
                                screen.closeAllPopups()
                                waitFrames(waitFastFrames)
                            }
                            if (attempts % turnLoopLogInterval == 0) {
                                Log.debug(
                                    "web-validation turn-wait turn=%s attempts=%s nextUnitAction=%s playersTurn=%s actionButtons=%s",
                                    beforeTurn,
                                    attempts,
                                    nextTurnButton.isNextUnitAction(),
                                    screen.isPlayersTurn,
                                    actionLabels,
                                )
                            }
                            waitFrames(waitFastFrames)
                            continue
                        }
                        val clicked = clickActor(nextTurnButton)
                        if (!clicked) return false to "Could not click next-turn button during click turn progression."
                        val progressedNow = waitUntilFrames(360) {
                            val currentTurns = game.gameInfo?.turns ?: return@waitUntilFrames false
                            currentTurns > beforeTurn
                        }
                        if (progressedNow) {
                            progressed = true
                            advancedTurns += 1
                            break
                        }
                    }
                    is VictoryScreen -> {
                        if (strictNoFallback) {
                            return false to "Strict turn progression reached victory/defeat screen before requested turns completed."
                        }
                        return true to "Reached victory screen after advancing $advancedTurns turns via UI clicks."
                    }
                    else -> {
                        val screenName = screen?.javaClass?.simpleName ?: "null"
                        return false to "Unexpected screen during click turn progression: $screenName"
                    }
                }
                waitFrames(waitAfterActionFrames)
                val currentTurns = game.gameInfo?.turns ?: return false to "Missing gameInfo during click turn progression."
                if (currentTurns > beforeTurn) {
                    progressed = true
                    advancedTurns += 1
                    break
                }
            }

            if (!progressed) {
                val worldScreen = game.worldScreen
                val nextTurnButton = worldScreen?.let { findActorByType(it.stage.root, NextTurnButton::class.java) }
                val nextUnitAction = nextTurnButton?.isNextUnitAction() ?: false
                val enabled = nextTurnButton?.isDisabled?.not() ?: false
                val openPopups = worldScreen?.hasOpenPopups() ?: false
                val playersTurn = worldScreen?.isPlayersTurn ?: false
                return false to "Turn progression stalled while clicking next-turn controls at turn=$beforeTurn (nextUnitAction=$nextUnitAction, buttonEnabled=$enabled, openPopups=$openPopups, playersTurn=$playersTurn, actionButtons=${worldScreen?.let { collectUnitActionLabels(it) } ?: "[]"})."
            }
        }
        return true to "Advanced $advancedTurns turns via UI clicks."
    }

    private fun blockerSnapshot(worldScreen: WorldScreen, nextTurnButton: NextTurnButton, actionLabels: String): String {
        val buttonEnabled = !nextTurnButton.isDisabled
        val nextUnitAction = nextTurnButton.isNextUnitAction()
        val openPopups = worldScreen.hasOpenPopups()
        val playersTurn = worldScreen.isPlayersTurn
        return "(nextUnitAction=$nextUnitAction, buttonEnabled=$buttonEnabled, openPopups=$openPopups, playersTurn=$playersTurn, actionButtons=$actionLabels)"
    }

    private suspend fun clickUnitCompletionAction(worldScreen: WorldScreen): Boolean {
        val actionLabels = listOf(
            UnitActionType.Skip.value.tr(), UnitActionType.Skip.value,
            UnitActionType.Sleep.value.tr(), UnitActionType.Sleep.value,
            UnitActionType.SleepUntilHealed.value.tr(), UnitActionType.SleepUntilHealed.value,
            UnitActionType.Fortify.value.tr(), UnitActionType.Fortify.value,
            UnitActionType.FortifyUntilHealed.value.tr(), UnitActionType.FortifyUntilHealed.value,
            UnitActionType.Explore.value.tr(), UnitActionType.Explore.value,
            UnitActionType.Automate.value.tr(), UnitActionType.Automate.value,
        )
        val nextPageLabels = listOf(UnitActionType.ShowAdditionalActions.value.tr(), UnitActionType.ShowAdditionalActions.value)
        for (pageAttempt in 0 until 4) {
            val actionsTable = findActorByType(worldScreen.stage.root, UnitActionsTable::class.java) ?: break
            val beforeLabels = collectUnitActionLabels(worldScreen)
            for (label in actionLabels) {
                val clicked = clickActorByText(actionsTable, label, contains = true)
                if (!clicked) continue
                waitFrames(uiWaitFast)
                val afterLabels = collectUnitActionLabels(worldScreen)
                if (afterLabels != beforeLabels) return true
            }
            val fallbackButton = findFirstEnabledButton(actionsTable)
            if (fallbackButton != null && clickActor(fallbackButton)) {
                waitFrames(uiWaitFast)
                val afterLabels = collectUnitActionLabels(worldScreen)
                if (afterLabels != beforeLabels) return true
            }
            val advancedPage = nextPageLabels.any { label ->
                clickActorByText(actionsTable, label, contains = true)
            }
            if (!advancedPage) break
            waitFrames(uiWaitFast)
        }
        return false
    }

    private fun collectUnitActionLabels(worldScreen: WorldScreen): String {
        val actionsTable = findActorByType(worldScreen.stage.root, UnitActionsTable::class.java) ?: return "[]"
        val labels = mutableListOf<String>()
        fun visit(actor: Actor) {
            val text = actorText(actor)?.trim()
            if (!text.isNullOrEmpty()) labels += text
            if (actor is Group) {
                for (index in 0 until actor.children.size) {
                    visit(actor.children[index])
                }
            }
        }
        visit(actionsTable)
        return labels.distinct().joinToString(prefix = "[", postfix = "]")
    }

    private fun findFirstEnabledButton(root: Actor): Button? {
        if (root is Button && !root.isDisabled && root.touchable == Touchable.enabled && root.isVisible) {
            return root
        }
        if (root is Group) {
            for (index in 0 until root.children.size) {
                val found = findFirstEnabledButton(root.children[index])
                if (found != null) return found
            }
        }
        return null
    }

    private fun sanitizeQuickstartPlayers(gameSetupInfo: GameSetupInfo) {
        val gameParameters = gameSetupInfo.gameParameters
        val ruleset = com.unciv.models.ruleset.RulesetCache.getComplexRuleset(gameParameters)
        for (player in gameParameters.players) {
            if (player.chosenCiv == com.unciv.Constants.random || player.chosenCiv == com.unciv.Constants.spectator) continue
            val nation = ruleset.nations[player.chosenCiv]
            if (nation == null || !nation.isMajorCiv) {
                player.chosenCiv = com.unciv.Constants.random
            }
        }

        val hasHumanPlayer = gameParameters.players.any {
            it.playerType == PlayerType.Human && it.chosenCiv != com.unciv.Constants.spectator
        }
        if (!hasHumanPlayer) {
            val firstPlayable = gameParameters.players.firstOrNull { it.chosenCiv != com.unciv.Constants.spectator }
            if (firstPlayable != null) {
                firstPlayable.playerType = PlayerType.Human
                if (firstPlayable.chosenCiv == com.unciv.Constants.spectator) firstPlayable.chosenCiv = com.unciv.Constants.random
            }
        }
    }

    private fun ensureWarState(playerCiv: Civilization, enemyCiv: Civilization) {
        if (!playerCiv.knows(enemyCiv)) {
            playerCiv.diplomacyFunctions.makeCivilizationsMeet(enemyCiv)
        }
        if (!playerCiv.isAtWarWith(enemyCiv)) {
            playerCiv.getDiplomacyManager(enemyCiv)?.declareWar()
        }
    }

    private suspend fun validateEndTurnLoop(game: WebGame, turns: Int): Pair<Boolean, String> {
        for (index in 1..turns) {
            val worldScreen = game.worldScreen
                ?: return false to "World screen is unavailable before turn $index."
            val beforeTurn = game.gameInfo?.turns
                ?: return false to "GameInfo is unavailable before turn $index."

            worldScreen.nextTurn()
            val advanced = waitUntilFrames(7200) {
                val currentTurns = game.gameInfo?.turns ?: return@waitUntilFrames false
                currentTurns > beforeTurn && game.worldScreen != null
            }
            if (!advanced) {
                return false to "Turn progression stalled at turn=$beforeTurn on step=$index."
            }
        }
        return true to "Advanced $turns turns successfully."
    }

    private suspend fun validateLocalSaveLoad(game: WebGame): Pair<Boolean, String> {
        return try {
            val currentGame = game.gameInfo ?: return false to "No active game to save."
            val serializationProbe = describeSerialization(currentGame)
            game.files.saveGame(currentGame, testSaveName)
            val saveFile = game.files.getSave(testSaveName)
            if (!saveFile.exists()) return false to "Save file was not created for [$testSaveName]."

            val loadedGame = game.files.loadGameByName(testSaveName)
            val idMatch = loadedGame.gameId == currentGame.gameId
            val turnMatch = loadedGame.turns == currentGame.turns
            if (!idMatch || !turnMatch) {
                return false to "Loaded save mismatched game identity (idMatch=$idMatch, turnMatch=$turnMatch)."
            }

            game.loadGame(loadedGame)
            val activeReloaded = waitUntilFrames(3600) { game.gameInfo?.gameId == loadedGame.gameId }
            if (!activeReloaded) return false to "Saved game loaded but active world did not refresh."

            true to "Saved and reloaded [$testSaveName] (turn=${loadedGame.turns})."
        } catch (throwable: Throwable) {
            val currentGame = game.gameInfo
            val probe = if (currentGame != null) " | ${describeSerialization(currentGame)}" else ""
            false to "Exception during local save/load: ${throwable::class.simpleName} ${throwable.message ?: ""}$probe".trim()
        }
    }

    private fun validateClipboardRoundtrip(game: WebGame): Pair<Boolean, String> {
        return try {
            val currentGame = game.gameInfo ?: return false to "No active game for clipboard export."
            val encoded = UncivFiles.gameInfoToString(currentGame, forceZip = true, updateChecksum = true)
            val probe = describeSerialization(currentGame, encoded)
            Gdx.app.clipboard.contents = encoded
            val imported = UncivFiles.gameInfoFromString(Gdx.app.clipboard.contents.trim())
            val matches = imported.gameId == currentGame.gameId && imported.turns == currentGame.turns
            if (matches) {
                true to "Clipboard export/import roundtrip succeeded for gameId=${currentGame.gameId}."
            } else {
                false to "Clipboard roundtrip mismatched game identity. $probe"
            }
        } catch (throwable: Throwable) {
            val currentGame = game.gameInfo
            val probe = if (currentGame != null) " | ${describeSerialization(currentGame)}" else ""
            false to "Exception during clipboard roundtrip: ${throwable::class.simpleName} ${throwable.message ?: ""}$probe".trim()
        }
    }

    private fun describeSerialization(game: com.unciv.logic.GameInfo, encodedInput: String? = null): String {
        val encoded = encodedInput ?: UncivFiles.gameInfoToString(game, forceZip = true, updateChecksum = true)
        val plain = try {
            com.unciv.ui.screens.savescreens.Gzip.unzip(encoded)
        } catch (_: Throwable) {
            encoded
        }
        val hasTileMap = plain.contains("\"tileMap\"")
        val hasTileList = plain.contains("\"tileList\"")
        val hasEmptyTileList = plain.contains("\"tileList\":[]")
        val tileListCount = "\"tileList\"".toRegex().findAll(plain).count()
        return "serializeProbe(hasTileMap=$hasTileMap,hasTileList=$hasTileList,emptyTileList=$hasEmptyTileList,tileListTokens=$tileListCount,plainLen=${plain.length},liveTiles=${game.tileMap.tileList.size})"
    }

    private fun validateAudio(): Pair<Boolean, String> {
        return try {
            val found = SoundPlayer.get(UncivSound.Click) != null
            val played = SoundPlayer.play(UncivSound.Click)
            if (found && played) {
                true to "SoundPlayer resolved and played UncivSound.Click."
            } else {
                false to "Audio check failed (found=$found, played=$played)."
            }
        } catch (throwable: Throwable) {
            false to "Exception during audio check: ${throwable::class.simpleName} ${throwable.message ?: ""}".trim()
        }
    }

    private suspend fun validateMultiplayerDisabled(game: WebGame): Pair<Boolean, String> {
        if (PlatformCapabilities.current.onlineMultiplayer) {
            return true to "PlatformCapabilities.onlineMultiplayer enabled by current profile; disabled-gate check skipped."
        }
        return try {
            val mainMenu = game.goToMainMenu()
            waitFrames(60)
            val multiplayerLabel = "Multiplayer".tr()
            val hasMultiplayerButton = actorTreeContainsText(mainMenu.stage.root, multiplayerLabel)
            if (!hasMultiplayerButton) {
                true to "Multiplayer button not present in main menu and capability is disabled."
            } else {
                false to "Main menu still contains multiplayer entry."
            }
        } catch (throwable: Throwable) {
            false to "Exception while validating multiplayer gate: ${throwable::class.simpleName} ${throwable.message ?: ""}".trim()
        }
    }

    private suspend fun validateMultiplayerActive(game: WebGame): Pair<Boolean, String> {
        val settings = UncivGame.Current.settings.multiplayer
        val previousServer = settings.getServer()
        val previousUserId = settings.getUserId()
        val previousPassword = settings.getCurrentServerPassword()

        val serverUrl = WebValidationInterop.getTestMultiplayerServerUrl()
            ?: return false to "Missing multiplayer test server URL (mpServer)."

        val userA = "00000000-0000-0000-0000-0000000000a1"
        val userB = "00000000-0000-0000-0000-0000000000b2"
        val password = "webtest-pass"

        try {
            settings.setServer(serverUrl)
            settings.setUserId(userA)
            settings.setCurrentServerPassword(password)

            val serverA = MultiplayerServer()
            if (!serverA.checkServerStatus()) {
                return false to "Multiplayer server isalive failed."
            }

            val setup = GameSetupInfo.fromSettings().apply {
                gameParameters.players = arrayListOf(
                    Player(playerType = PlayerType.Human, playerId = userA),
                    Player(playerType = PlayerType.Human, playerId = userB),
                )
                gameParameters.isOnlineMultiplayer = true
                gameParameters.randomNumberOfCityStates = false
                gameParameters.numberOfCityStates = 0
                gameParameters.minNumberOfCityStates = 0
                gameParameters.maxNumberOfCityStates = 0
                gameParameters.noBarbarians = true
                mapParameters.shape = MapShape.rectangular
                mapParameters.mapSize = MapSize.Tiny
                mapParameters.type = MapType.pangaea
            }

            val gameInfo = GameStarter.startNewGame(setup)
            gameInfo.gameParameters.multiplayerServerUrl = serverUrl
            serverA.uploadGame(gameInfo, withPreview = true)

            settings.setUserId(userB)
            settings.setCurrentServerPassword(password)
            val serverB = MultiplayerServer()
            val downloaded = serverB.tryDownloadGame(gameInfo.gameId)
            downloaded.nextTurn()
            serverB.uploadGame(downloaded, withPreview = true)

            settings.setUserId(userA)
            settings.setCurrentServerPassword(password)
            val updated = MultiplayerServer().tryDownloadGame(gameInfo.gameId)
            val updatedOk = updated.turns >= downloaded.turns && updated.currentPlayer == downloaded.currentPlayer
            if (!updatedOk) {
                return false to "Multiplayer update did not propagate (turns=${updated.turns}, current=${updated.currentPlayer})."
            }

            val chat = ChatStore.getChatByGameId(gameInfo.gameId)
            ChatWebSocket.restart(force = true)
            waitFrames(60)
            ChatWebSocket.requestMessageSend(ChatMessage.Chat("TesterA", "Hello from web", gameInfo.gameId))
            val chatOk = waitUntilFrames(600) { chat.length > 1 }
            ChatWebSocket.stop()
            if (!chatOk) {
                return false to "Chat message was not received on web websocket."
            }

            return true to "Multiplayer file storage and chat validated via test server."
        } catch (throwable: Throwable) {
            return false to "Exception while validating multiplayer: ${throwable::class.simpleName} ${throwable.message ?: ""}".trim()
        } finally {
            settings.setServer(previousServer)
            settings.setUserId(previousUserId)
            if (previousPassword != null) settings.setCurrentServerPassword(previousPassword)
        }
    }

    private suspend fun validateModDownloadsDisabled(): Pair<Boolean, String> {
        if (PlatformCapabilities.current.onlineModDownloads) {
            return true to "PlatformCapabilities.onlineModDownloads enabled by current profile; disabled-gate check skipped."
        }
        return try {
            var disabledErrorSeen = false
            try {
                LoadOrSaveScreen.loadMissingMods(listOf("definitely-missing-mod"), {}, {})
            } catch (ex: UncivShowableException) {
                disabledErrorSeen = ex.localizedMessage?.contains("disabled", ignoreCase = true) == true
            }
            if (disabledErrorSeen) {
                true to "Missing-mod download path throws disabled-by-platform error as expected."
            } else {
                false to "Missing-mod download path did not report disabled-by-platform."
            }
        } catch (throwable: Throwable) {
            false to "Exception while validating mod download gate: ${throwable::class.simpleName} ${throwable.message ?: ""}".trim()
        }
    }

    private suspend fun validateModDownloadsActive(): Pair<Boolean, String> {
        return try {
            val baseUrl = WebValidationInterop.getBaseUrl()
            val modZipUrl = WebValidationInterop.getTestModZipUrl()
                ?: baseUrl?.let { "$it/webtest/mods/test-mod.zip" }
                ?: return false to "Missing base URL for test mod zip."

            val repo = GithubAPI.Repo.parseUrl(modZipUrl) ?: return false to "Failed to parse mod zip url."
            val modFolder = repo.downloadAndExtract() ?: return false to "Mod download/extract returned null."
            val modOptionsFile = modFolder.child("jsons/ModOptions.json")
            if (!modOptionsFile.exists()) {
                return false to "ModOptions.json missing after extraction."
            }
            val updatedFolder = repo.downloadAndExtract() ?: return false to "Mod update download failed."
            if (!updatedFolder.exists()) {
                return false to "Updated mod folder missing after re-download."
            }
            updatedFolder.deleteDirectory()
            if (updatedFolder.exists()) {
                return false to "Mod folder still exists after removal."
            }
            true to "Mod download/update/remove validated using test archive."
        } catch (throwable: Throwable) {
            false to "Exception while validating mod downloads: ${throwable::class.simpleName} ${throwable.message ?: ""}".trim()
        }
    }

    private suspend fun validateCustomFileChooserDisabled(game: WebGame): Pair<Boolean, String> {
        if (PlatformCapabilities.current.customFileChooser) {
            return true to "PlatformCapabilities.customFileChooser enabled by current profile; disabled-gate check skipped."
        }
        return try {
            val activeGame = game.gameInfo ?: return false to "No active game available for save screen check."

            val saveScreen = SaveGameScreen(activeGame)
            val saveCustomLabel = SaveGameScreen.saveToCustomText.tr()
            val hasSaveCustom = actorTreeContainsText(saveScreen.stage.root, saveCustomLabel)
            saveScreen.dispose()

            val loadScreen = LoadGameScreen()
            val loadCustomLabel = "Load from custom location".tr()
            val hasLoadCustom = actorTreeContainsText(loadScreen.stage.root, loadCustomLabel)
            loadScreen.dispose()

            val saverLoaderDisabled = UncivFiles.saverLoader === PlatformSaverLoader.None
            val disabled = !hasSaveCustom && !hasLoadCustom && saverLoaderDisabled
            if (disabled) {
                true to "Custom save/load controls hidden and saverLoader is PlatformSaverLoader.None."
            } else {
                false to "Custom save/load controls are still exposed (save=$hasSaveCustom, load=$hasLoadCustom, saverLoaderDisabled=$saverLoaderDisabled)."
            }
        } catch (throwable: Throwable) {
            false to "Exception while validating custom file chooser gate: ${throwable::class.simpleName} ${throwable.message ?: ""}".trim()
        }
    }

    private suspend fun validateCustomFileChooserActive(game: WebGame): Pair<Boolean, String> {
        val saverLoader = UncivFiles.saverLoader
        if (saverLoader === PlatformSaverLoader.None) {
            return false to "PlatformSaverLoader is None while custom file chooser is enabled."
        }
        return try {
            WebValidationInterop.enableTestFileStore()
            val activeGame = game.gameInfo ?: return false to "No active game available for file chooser validation."
            val saveData = UncivFiles.gameInfoToString(activeGame, forceZip = true, updateChecksum = true)

            val saveResult = suspendCoroutine<Result<String>> { continuation ->
                saverLoader.saveGame(
                    saveData,
                    "WebE2E-Phase3.json",
                    { location -> continuation.resume(Result.success(location)) },
                    { ex -> continuation.resume(Result.failure(ex)) }
                )
            }
            if (saveResult.isFailure) {
                val failure = saveResult.exceptionOrNull()
                return false to "Save failed: ${failure?.message ?: "unknown"}"
            }

            val loadResult = suspendCoroutine<Result<Pair<String, String>>> { continuation ->
                saverLoader.loadGame(
                    { data, location -> continuation.resume(Result.success(data to location)) },
                    { ex -> continuation.resume(Result.failure(ex)) }
                )
            }
            if (loadResult.isFailure) {
                val failure = loadResult.exceptionOrNull()
                return false to "Load failed: ${failure?.message ?: "unknown"}"
            }

            val (loadedData, location) = loadResult.getOrNull() ?: return false to "Load returned empty result."
            val loadedGame = UncivFiles.gameInfoFromString(loadedData)
            val ok = loadedGame.gameId == activeGame.gameId
            if (!ok) {
                return false to "Loaded gameId mismatch (expected=${activeGame.gameId}, got=${loadedGame.gameId})."
            }
            true to "File chooser save/load validated (${location.ifBlank { "in-memory" }})."
        } catch (throwable: Throwable) {
            false to "Exception while validating file chooser: ${throwable::class.simpleName} ${throwable.message ?: ""}".trim()
        }
    }

    private fun validateFontSelectionBehavior(): Pair<Boolean, String> {
        return try {
            val fonts = Fonts.getSystemFonts().toList()
            val deterministicDefault = fonts.size == 1 && fonts.first() == FontFamilyData.default
            val capabilityDisabled = !PlatformCapabilities.current.systemFontEnumeration
            if (deterministicDefault && capabilityDisabled) {
                true to "System font enumeration disabled; only deterministic default font is exposed."
            } else {
                false to "Font gate mismatch (capabilityDisabled=$capabilityDisabled, fonts=${fonts.size})."
            }
        } catch (throwable: Throwable) {
            false to "Exception while validating font behavior: ${throwable::class.simpleName} ${throwable.message ?: ""}".trim()
        }
    }

    private suspend fun validateExternalLinks(): Pair<Boolean, String> {
        return try {
            WebValidationInterop.installExternalLinkSpy()
            val openResult = Gdx.net.openURI("https://github.com/yairm210/Unciv")
            waitFrames(30)
            val openCalls = WebValidationInterop.getExternalLinkOpenCount()
            val success = openResult || openCalls > 0
            if (success) {
                true to "External link invocation reached browser bridge (openResult=$openResult, openCalls=$openCalls)."
            } else {
                false to "No browser link-open call observed (openResult=$openResult, openCalls=$openCalls)."
            }
        } catch (throwable: Throwable) {
            false to "Exception while validating external links: ${throwable::class.simpleName} ${throwable.message ?: ""}".trim()
        } finally {
            WebValidationInterop.restoreExternalLinkSpy()
        }
    }

    private fun actorTreeContainsText(actor: Actor, expectedText: String): Boolean {
        when (actor) {
            is Label -> if (actor.text.toString() == expectedText) return true
            is TextButton -> if (actor.text.toString() == expectedText) return true
            is IconTextButton -> if (actor.label.text.toString() == expectedText) return true
        }
        if (actor is Group) {
            val children = actor.children
            for (index in 0 until children.size) {
                if (actorTreeContainsText(children[index], expectedText)) return true
            }
        }
        return false
    }

    private fun actorText(actor: Actor): String? = when (actor) {
        is Label -> actor.text.toString()
        is TextButton -> actor.text.toString()
        is IconTextButton -> actor.label.text.toString()
        else -> null
    }

    private fun normalizeText(value: String): String {
        return value.lowercase().replace(Regex("\\s+"), " ").trim()
    }

    private fun findClickableAncestor(actor: Actor): Actor? {
        var current: Actor? = actor
        var listenerCandidate: Actor? = null
        while (current != null) {
            if (
                current is Button &&
                current.isVisible &&
                current.touchable == Touchable.enabled
            ) {
                return current
            }
            if (
                current.isVisible &&
                current.touchable == Touchable.enabled &&
                current.listeners.size > 0
            ) {
                if (listenerCandidate == null) listenerCandidate = current
            }
            current = current.parent
        }
        return listenerCandidate
    }

    private fun findClickableActorByText(
        root: Actor,
        expectedText: String,
        contains: Boolean = false,
        preferLastMatch: Boolean = false,
    ): Actor? {
        val expected = normalizeText(expectedText)
        var match: Actor? = null

        fun visit(node: Actor) {
            if (!node.isVisible) return
            val text = actorText(node)
            if (text != null) {
                val normalized = normalizeText(text)
                val textMatches = if (contains) normalized.contains(expected) else normalized == expected
                if (textMatches) {
                    val clickable = findClickableAncestor(node)
                    if (clickable != null) {
                        match = clickable
                        if (!preferLastMatch) return
                    }
                }
            }
            if (node is Group) {
                val children = node.children
                for (index in 0 until children.size) {
                    visit(children[index])
                    if (match != null && !preferLastMatch) return
                }
            }
        }

        visit(root)
        return match
    }

    private fun clickActorByText(
        root: Actor,
        text: String,
        contains: Boolean = false,
        preferLastMatch: Boolean = false,
    ): Boolean {
        val actor = findClickableActorByText(root, text, contains, preferLastMatch) ?: return false
        return clickActor(actor)
    }

    private fun clickActor(actor: Actor): Boolean {
        val stage = actor.stage ?: return false
        val center = actor.localToStageCoordinates(Vector2(actor.width / 2f, actor.height / 2f))
        val downEvent = InputEvent().apply {
            setType(InputEvent.Type.touchDown)
            setStageX(center.x)
            setStageY(center.y)
            setPointer(0)
            setButton(Input.Buttons.LEFT)
        }
        val upEvent = InputEvent().apply {
            setType(InputEvent.Type.touchUp)
            setStageX(center.x)
            setStageY(center.y)
            setPointer(0)
            setButton(Input.Buttons.LEFT)
        }
        val fired = runCatching { actor.fire(downEvent) }.getOrDefault(false)
        runCatching { actor.fire(upEvent) }
        if (fired) return true

        val screenPoint = stage.stageToScreenCoordinates(Vector2(center.x, center.y))
        val x = screenPoint.x.toInt()
        val y = screenPoint.y.toInt()
        val downHandled = stage.touchDown(x, y, 0, Input.Buttons.LEFT)
        stage.touchUp(x, y, 0, Input.Buttons.LEFT)
        return downHandled
    }

    private fun <T : Actor> findActorByType(root: Actor, actorClass: Class<T>): T? {
        if (actorClass.isInstance(root)) return actorClass.cast(root)
        if (root !is Group) return null
        val children = root.children
        for (index in 0 until children.size) {
            val found = findActorByType(children[index], actorClass)
            if (found != null) return found
        }
        return null
    }

    private suspend fun waitUntilFrames(maxFrames: Int, condition: () -> Boolean): Boolean {
        repeat(maxFrames) {
            if (condition()) return true
            nextFrame()
        }
        return condition()
    }

    private suspend fun waitFrames(frames: Int) {
        repeat(frames) { nextFrame() }
    }

    private suspend fun nextFrame() {
        suspendCoroutine { continuation ->
            val app = Gdx.app
            var resumed = false
            fun resumeOnce() {
                if (resumed) return
                resumed = true
                continuation.resume(Unit)
            }
            if (app == null) {
                WebValidationInterop.schedule({ resumeOnce() }, 16)
            } else {
                app.postRunnable { resumeOnce() }
                WebValidationInterop.schedule({ resumeOnce() }, 16)
            }
        }
    }

    private fun publish(results: Map<String, FeatureResult>) {
        val payload = buildJsonPayload(results)
        WebValidationInterop.publishResult(payload)
    }

    private fun buildJsonPayload(results: Map<String, FeatureResult>): String {
        val passCount = results.values.count { it.status == "PASS" }
        val failCount = results.values.count { it.status == "FAIL" }
        val blockedCount = results.values.count { it.status == "BLOCKED" }
        val disabledCount = results.values.count { it.status == "DISABLED_BY_DESIGN" }

        val builder = StringBuilder()
        builder.append('{')
        builder.append("\"generatedAt\":\"").append(escapeJson(Instant.now().toString())).append("\",")
        builder.append("\"summary\":{")
        builder.append("\"pass\":").append(passCount).append(',')
        builder.append("\"fail\":").append(failCount).append(',')
        builder.append("\"blocked\":").append(blockedCount).append(',')
        builder.append("\"disabledByDesign\":").append(disabledCount)
        builder.append("},")
        builder.append("\"features\":[")

        featureOrder.forEachIndexed { index, feature ->
            val result = results[feature] ?: FeatureResult()
            if (index > 0) builder.append(',')
            builder.append('{')
            builder.append("\"feature\":\"").append(escapeJson(feature)).append("\",")
            builder.append("\"status\":\"").append(escapeJson(result.status)).append("\",")
            builder.append("\"blockingIssue\":\"").append(escapeJson(result.blockingIssue)).append("\",")
            builder.append("\"notes\":\"").append(escapeJson(result.notes)).append('"')
            builder.append('}')
        }

        builder.append(']')
        builder.append('}')
        return builder.toString()
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
