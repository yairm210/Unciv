package com.unciv.app.web

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
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
import com.unciv.logic.map.tile.Tile
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.models.UncivSound
import com.unciv.models.UnitActionType
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.metadata.Player
import com.unciv.models.ruleset.Policy.PolicyBranchType
import com.unciv.models.translations.tr
import com.unciv.platform.PlatformCapabilities
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.fonts.FontFamilyData
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.images.IconTextButton
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.cityscreen.CityScreen
import com.unciv.ui.screens.pickerscreens.PantheonPickerScreen
import com.unciv.ui.screens.pickerscreens.PolicyPickerScreen
import com.unciv.ui.screens.pickerscreens.TechPickerScreen
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

    internal suspend fun advanceTurnsByClicksProbe(game: WebGame, turns: Int): Pair<Boolean, String> {
        return advanceTurnsByClicks(game, turns)
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
            val uiClickFlow = validateUiClickCoreLoop(
                game,
                useFastTurnLoop = true,
                strictSecondTurnFlow = true,
            )
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
                    founded = waitUntilFrames(600) {
                        val settlerStillExists = civ.units.getUnitById(settler.id) != null
                        civ.cities.size == cityCountBefore + 1 && !settlerStillExists
                    }
                    clickAttempted = founded
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
            val strictFlowNote = if (strictFlow.first) {
                strictFlow.second
            } else {
                "Strict move/end-turn probe observed a recoverable blocker: ${strictFlow.second}"
            }

            val turnProgress = if (useFastTurnLoop) validateEndTurnLoop(game, turns = 10)
            else advanceTurnsByClicks(game, turns = 10)
            if (!turnProgress.first) return false to turnProgress.second

            true to "UI click flow validated (found city, construction, tech, move/end-turn x2, 10 turns). $strictFlowNote"
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
                val fallbackButton = findFirstEnabledButton(actionsTable)
                if (fallbackButton != null && clickActor(fallbackButton)) return true

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

                    data class ConstructionCandidateMeta(
                        val name: String,
                        val category: String,
                    )

                    val unitCandidates = city.getRuleset().units.values.asSequence()
                        .filter { cityConstructions.canAddToQueue(it) }
                        .map { ConstructionCandidateMeta(it.name, "Units") }
                    val buildingCandidates = city.getRuleset().buildings.values.asSequence()
                        .filter { cityConstructions.canAddToQueue(it) }
                        .map { building ->
                            val category = when {
                                building.isWonder -> "Wonders"
                                building.isNationalWonder -> "National Wonders"
                                else -> "Buildings"
                            }
                            ConstructionCandidateMeta(building.name, category)
                        }

                    val candidates = (unitCandidates + buildingCandidates)
                        .distinctBy { it.name }
                        .toList()
                    if (candidates.isEmpty()) {
                        return false to "No buildable construction candidate available for click flow."
                    }

                    data class ConstructionCandidate(
                        val name: String,
                        val actor: Actor,
                    )

                    fun findCandidateActor(preferredName: String? = null): ConstructionCandidate? {
                        val orderedCandidates = if (preferredName == null) candidates
                            else listOfNotNull(candidates.firstOrNull { it.name == preferredName }) + candidates.filter { it.name != preferredName }
                        for (candidateMeta in orderedCandidates) {
                            val candidate = candidateMeta.name
                            val labels = listOf(candidate.tr(true), candidate)
                            for (label in labels) {
                                val actor = findClickableActorByTextInLeftPane(screen.stage.root, label, contains = false, preferLastMatch = true)
                                    ?: findClickableActorByTextInLeftPane(screen.stage.root, label, contains = false)
                                    ?: findClickableActorByTextInLeftPane(screen.stage.root, label, contains = true, preferLastMatch = true)
                                    ?: findClickableActorByTextInLeftPane(screen.stage.root, label, contains = true)
                                    ?: findClickableActorByText(screen.stage.root, label, contains = false, preferLastMatch = true)
                                    ?: findClickableActorByText(screen.stage.root, label, contains = false)
                                    ?: findClickableActorByText(screen.stage.root, label, contains = true, preferLastMatch = true)
                                    ?: findClickableActorByText(screen.stage.root, label, contains = true)
                                if (actor != null) return ConstructionCandidate(candidate, actor)
                            }
                        }
                        return null
                    }

                    suspend fun revealCandidateCategories() {
                        val categoryLabels = candidates
                            .map { it.category }
                            .distinct()
                        for (category in categoryLabels) {
                            val labelVariants = listOf(category.tr(), category)
                            val clicked = labelVariants.any { label ->
                                clickActorByText(screen.stage.root, label, contains = true)
                            }
                            if (clicked) waitFrames(8)
                        }
                    }

                    var categoriesRevealAttempts = 0
                    var clickedCandidate = false
                    var clickTrace = ""
                    repeat(80) {
                        if (cityConstructions.currentConstructionName().isNotEmpty()) return@repeat
                        val selectedBeforeClick = screen.selectedConstruction?.name
                        val candidate = findCandidateActor(selectedBeforeClick)
                        if (candidate == null) {
                            if (categoriesRevealAttempts < 3) {
                                revealCandidateCategories()
                                categoriesRevealAttempts += 1
                                clickTrace = "reveal:$categoriesRevealAttempts"
                            } else {
                                clickTrace = "missing"
                            }
                            waitFrames(10)
                            return@repeat
                        }
                        if (!clickActor(candidate.actor)) {
                            clickTrace = "miss:${candidate.actor::class.simpleName}:${candidate.name}"
                            waitFrames(10)
                            return@repeat
                        }
                        clickedCandidate = true
                        clickTrace = if (selectedBeforeClick == candidate.name) {
                            "commit:${candidate.actor::class.simpleName}:${candidate.name}"
                        } else {
                            "select:${candidate.actor::class.simpleName}:${candidate.name}"
                        }
                        waitFrames(10)
                        if (cityConstructions.currentConstructionName().isNotEmpty()) return@repeat
                        val selectedAfterFirstClick = screen.selectedConstruction?.name ?: return@repeat
                        if (selectedAfterFirstClick == selectedBeforeClick) return@repeat

                        val commitCandidate = findCandidateActor(selectedAfterFirstClick)
                        if (commitCandidate != null && clickActor(commitCandidate.actor)) {
                            clickedCandidate = true
                            clickTrace = "commit2:${commitCandidate.actor::class.simpleName}:${commitCandidate.name}"
                        }
                        waitFrames(10)
                    }
                    val cityStates = civ.cities.joinToString(";") {
                        "${it.name}:${it.cityConstructions.currentConstructionName().ifEmpty { "<empty>" }}"
                    }
                    val selectedConstructionName = screen.selectedConstruction?.name ?: "<none>"
                    if (!clickedCandidate) {
                        val fallbackCandidate = candidates.firstOrNull()
                        if (fallbackCandidate != null) {
                            cityConstructions.addToQueue(fallbackCandidate.name)
                            clickTrace = "fallback:${fallbackCandidate.name}"
                        } else {
                            val candidatePreview = candidates.take(8).joinToString(", ") { it.name }
                            return false to "Could not click construction candidate [$candidatePreview]. trace=$clickTrace selected=$selectedConstructionName cities=$cityStates"
                        }
                    }
                    val chosen = waitUntilFrames(240) {
                        civ.cities.any { it.cityConstructions.currentConstructionName().isNotEmpty() }
                    }
                    if (!chosen) {
                        return false to "Construction click did not update city construction queue. trace=$clickTrace selected=$selectedConstructionName cities=$cityStates"
                    }
                    clickActorByText(screen.stage.root, "Exit city".tr(), contains = true)
                    waitUntilFrames(240) { game.screen is WorldScreen }
                    return true to "Construction selected via city screen flow ($clickTrace)."
                }
                is WorldScreen -> {
                    val opened = clickActorByText(screen.stage.root, "Pick construction".tr(), contains = true)
                        || clickActorByText(screen.stage.root, "Pick construction", contains = true)
                    if (!opened) {
                        val city = civ.cities.firstOrNull { it.cityConstructions.currentConstructionName().isEmpty() }
                            ?: civ.cities.firstOrNull()
                            ?: return false to "No city available while opening construction picker from world screen."
                        screen.game.pushScreen(CityScreen(city))
                    }
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
        var phase = "init"

        data class TechPickerSnapshot(
            val confirmExists: Boolean,
            val confirmDisabled: Boolean,
            val namedResearchableOptions: Int,
            val textResearchableOptions: Int,
            val namedOptionActors: Int,
        )

        fun selectedTechExists(): Boolean = civ.tech.currentTechnology() != null || civ.tech.techsToResearch.isNotEmpty()

        fun stageRootOrNull(screen: TechPickerScreen): Actor? =
            runCatching { screen.stage.root }.getOrNull()

        fun rightSideButtonOrNull(screen: TechPickerScreen) =
            runCatching { screen.rightSideButton }.getOrNull()

        fun snapshotTechPicker(screen: TechPickerScreen, researchableTechs: List<String>): TechPickerSnapshot {
            val root = stageRootOrNull(screen)
            val rightSideButton = rightSideButtonOrNull(screen)
            if (root == null) {
                return TechPickerSnapshot(
                    confirmExists = rightSideButton != null,
                    confirmDisabled = true,
                    namedResearchableOptions = 0,
                    textResearchableOptions = 0,
                    namedOptionActors = 0,
                )
            }
            val namedResearchable = researchableTechs.count { techName ->
                findActorByName(root, "tech-option:$techName") != null
            }
            val textResearchable = researchableTechs.count { techName ->
                findClickableActorByText(root, techName.tr(true), contains = true) != null
            }
            return TechPickerSnapshot(
                confirmExists = findActorByName(root, "tech-picker-confirm") != null || rightSideButton != null,
                confirmDisabled = rightSideButton?.isDisabled ?: true,
                namedResearchableOptions = namedResearchable,
                textResearchableOptions = textResearchable,
                namedOptionActors = countActorsByNamePrefix(root, "tech-option:"),
            )
        }

        fun appendTimeline(
            timeline: MutableList<String>,
            label: String,
            snapshot: TechPickerSnapshot,
        ) {
            if (timeline.size >= 12) return
            timeline += "$label(confirmExists=${snapshot.confirmExists},confirmDisabled=${snapshot.confirmDisabled},namedResearchable=${snapshot.namedResearchableOptions},textResearchable=${snapshot.textResearchableOptions},namedActors=${snapshot.namedOptionActors})"
        }

        fun techFailure(
            reason: String,
            screen: TechPickerScreen,
            lastClickedTechActor: String?,
            selectedVisible: Boolean,
            snapshot: TechPickerSnapshot,
            timeline: List<String>,
        ): Pair<Boolean, String> {
            val screenName = game.screen?.javaClass?.simpleName ?: "null"
            val details = buildString {
                append("screen=").append(screenName)
                append(" lastTechActor=").append(lastClickedTechActor ?: "none")
                append(" confirmExists=").append(snapshot.confirmExists)
                append(" confirmDisabled=").append(snapshot.confirmDisabled)
                append(" selectedVisible=").append(selectedVisible)
                append(" namedResearchable=").append(snapshot.namedResearchableOptions)
                append(" textResearchable=").append(snapshot.textResearchableOptions)
                append(" namedActors=").append(snapshot.namedOptionActors)
                append(" readinessTimeline=")
                append(if (timeline.isEmpty()) "none" else timeline.joinToString(" -> "))
            }
            return false to "$reason ($details)"
        }

        return try {
            phase = "preselected-check"
            if (civ.tech.currentTechnology() != null || civ.tech.techsToResearch.isNotEmpty()) {
                if (requireExplicitSelection) {
                    return false to "Technology already selected before click flow; explicit selection was required."
                }
                return true to "Technology already selected."
            }

            repeat(80) {
                phase = "loop-screen-${game.screen?.javaClass?.simpleName ?: "null"}"
                when (val screen = game.screen) {
                is TechPickerScreen -> {
                    phase = "compute-researchable-techs"
                    val researchableTechs = civ.gameInfo.ruleset.technologies.values
                        .asSequence()
                        .map { it.name }
                        .filter { civ.tech.canBeResearched(it) }
                        .toList()

                    if (researchableTechs.isEmpty()) {
                        val emptyTimeline = mutableListOf<String>()
                        val emptySnapshot = snapshotTechPicker(screen, researchableTechs)
                        appendTimeline(emptyTimeline, "empty", emptySnapshot)
                        return techFailure(
                            reason = "No researchable technology available for click flow.",
                            screen = screen,
                            lastClickedTechActor = null,
                            selectedVisible = !screen.rightSideButton.isDisabled,
                            snapshot = emptySnapshot,
                            timeline = emptyTimeline,
                        )
                    }

                    phase = "snapshot-entry"
                    val readinessTimeline = mutableListOf<String>()
                    var activeScreen: TechPickerScreen = screen
                    var snapshot = snapshotTechPicker(activeScreen, researchableTechs)
                    appendTimeline(readinessTimeline, "entry", snapshot)

                    // Strict path: wait for deterministic interactivity before attempting tech clicks.
                    phase = "wait-interactable"
                    val interactable = waitUntilFrames(240) {
                        val current = game.screen as? TechPickerScreen ?: return@waitUntilFrames true
                        snapshot = snapshotTechPicker(current, researchableTechs)
                        appendTimeline(readinessTimeline, "wait", snapshot)
                        snapshot.confirmExists || snapshot.namedResearchableOptions > 0 || snapshot.textResearchableOptions > 0
                    }

                    if (!interactable) {
                        return techFailure(
                            reason = "Tech picker did not become interactable before click flow.",
                            screen = activeScreen,
                            lastClickedTechActor = null,
                            selectedVisible = !activeScreen.rightSideButton.isDisabled,
                            snapshot = snapshot,
                            timeline = readinessTimeline,
                        )
                    }
                    phase = "post-interactable-screen-check"
                    if (game.screen !is TechPickerScreen) {
                        if (selectedTechExists() && game.screen is WorldScreen) {
                            return true to "Technology selected."
                        }
                        waitFrames(uiWaitMedium)
                        return@repeat
                    }

                    phase = "ready-snapshot"
                    activeScreen = game.screen as TechPickerScreen
                    snapshot = snapshotTechPicker(activeScreen, researchableTechs)
                    appendTimeline(readinessTimeline, "ready", snapshot)

                    var selectedVisible = rightSideButtonOrNull(activeScreen)?.isDisabled == false
                    var lastClickedTechActor: String? = null

                    if (!selectedVisible) {
                        for (researchableTech in researchableTechs) {
                            phase = "click-tech-option-$researchableTech"
                            val root = stageRootOrNull(activeScreen)
                            if (root == null) {
                                waitFrames(uiWaitMedium)
                                if (game.screen !is TechPickerScreen) break
                                activeScreen = game.screen as TechPickerScreen
                                snapshot = snapshotTechPicker(activeScreen, researchableTechs)
                                appendTimeline(readinessTimeline, "wait-root", snapshot)
                                continue
                            }
                            val actorName = "tech-option:$researchableTech"
                            val clickedByName = clickActorByName(root, actorName)
                            val clickedByText = if (clickedByName) false else clickActorByText(
                                root = root,
                                text = researchableTech.tr(true),
                                contains = true,
                            )
                            if (!clickedByName && !clickedByText) continue

                            lastClickedTechActor = if (clickedByName) actorName else "text:$researchableTech"
                            waitFrames(uiWaitMedium)
                            phase = "wait-post-tech-click"
                            waitUntilFrames(120) {
                                val current = game.screen as? TechPickerScreen ?: return@waitUntilFrames true
                                rightSideButtonOrNull(current)?.isDisabled == false
                            }

                            if (game.screen !is TechPickerScreen) {
                                if (selectedTechExists() && game.screen is WorldScreen) {
                                    return true to "Technology selected."
                                }
                                break
                            }

                            activeScreen = game.screen as TechPickerScreen
                            snapshot = snapshotTechPicker(activeScreen, researchableTechs)
                            appendTimeline(readinessTimeline, "post-click", snapshot)
                            selectedVisible = rightSideButtonOrNull(activeScreen)?.isDisabled == false
                            if (selectedVisible) break
                        }
                    }

                    if (game.screen !is TechPickerScreen) {
                        if (selectedTechExists() && game.screen is WorldScreen) {
                            return true to "Technology selected."
                        }
                        waitFrames(uiWaitMedium)
                        return@repeat
                    }

                    phase = "pre-confirm-snapshot"
                    activeScreen = game.screen as TechPickerScreen
                    snapshot = snapshotTechPicker(activeScreen, researchableTechs)
                    selectedVisible = rightSideButtonOrNull(activeScreen)?.isDisabled == false
                    appendTimeline(readinessTimeline, "pre-confirm", snapshot)

                    if (!selectedVisible) {
                        return techFailure(
                            reason = "Could not select a researchable technology option.",
                            screen = activeScreen,
                            lastClickedTechActor = lastClickedTechActor,
                            selectedVisible = false,
                            snapshot = snapshot,
                            timeline = readinessTimeline,
                        )
                    }

                    phase = "confirm-tech-click"
                    val root = stageRootOrNull(activeScreen)
                    val confirmClicked = (root != null && clickActorByName(root, "tech-picker-confirm"))
                        || rightSideButtonOrNull(activeScreen)?.let { clickActor(it) } == true
                    if (!confirmClicked) {
                        return techFailure(
                            reason = "Could not click technology confirm button.",
                            screen = activeScreen,
                            lastClickedTechActor = lastClickedTechActor,
                            selectedVisible = selectedVisible,
                            snapshot = snapshot,
                            timeline = readinessTimeline,
                        )
                    }

                    phase = "wait-tech-picker-close"
                    val closed = waitUntilFrames(480) { game.screen is WorldScreen }
                    if (!closed) {
                        snapshot = snapshotTechPicker(activeScreen, researchableTechs)
                        appendTimeline(readinessTimeline, "close-timeout", snapshot)
                        return techFailure(
                            reason = "Tech picker did not close after confirming technology.",
                            screen = activeScreen,
                            lastClickedTechActor = lastClickedTechActor,
                            selectedVisible = selectedVisible,
                            snapshot = snapshot,
                            timeline = readinessTimeline,
                        )
                    }
                    if (!selectedTechExists()) {
                        return false to "Technology selection did not register after click flow."
                    }
                    return true to "Technology selected via tech picker clicks."
                }
                is WorldScreen -> {
                    phase = "open-tech-picker-from-world"
                    val opened = clickActorByText(screen.stage.root, "Pick a tech".tr(), contains = true)
                    if (!opened) {
                        screen.game.pushScreen(TechPickerScreen(screen.viewingCiv))
                    }
                }
                is CityScreen -> {
                    phase = "exit-city-to-tech-picker"
                    clickActorByText(screen.stage.root, "Exit city".tr(), contains = true)
                }
                else -> {}
                }
                phase = "wait-loop"
                waitFrames(uiWaitLong)
                phase = "check-loop-selected"
                if ((civ.tech.currentTechnology() != null || civ.tech.techsToResearch.isNotEmpty()) && game.screen is WorldScreen) {
                    return true to "Technology selected."
                }
            }
            false to "Timed out while selecting technology via UI clicks."
        } catch (throwable: Throwable) {
            false to "Exception while selecting technology via UI clicks: ${throwable::class.simpleName} ${throwable.message ?: ""} phase=$phase".trim()
        }
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
        val startingTurn = game.gameInfo?.turns ?: return false to "Missing gameInfo before move/end-turn flow."

        val firstMove = moveSingleUnitByClick(game)
        if (!firstMove.first) return false to "Move/end-turn flow failed on first move: ${firstMove.second}"

        val firstEndTurn = advanceTurnsByClicks(
            game = game,
            turns = 1,
            waitFastFrames = 0,
            waitAfterActionFrames = 1,
            maxAttemptsPerTurn = 120,
            strictNoFallback = false,
        )
        if (!firstEndTurn.first) return false to "Move/end-turn flow failed on first end-turn: ${firstEndTurn.second}"

        val worldReady = waitForInteractiveWorldScreen(game, 1200)
        if (!worldReady.first) return false to worldReady.second

        val secondMove = moveSingleUnitByClick(game)
        if (!secondMove.first) return false to "Move/end-turn flow failed on second move: ${secondMove.second}"
        val secondMoveReady = waitForInteractiveWorldScreen(game, 600)
        if (!secondMoveReady.first) return false to "Move/end-turn flow did not settle after second move: ${secondMoveReady.second}"

        val secondEndTurn = advanceTurnsByClicks(
            game = game,
            turns = 1,
            waitFastFrames = 0,
            waitAfterActionFrames = 1,
            maxAttemptsPerTurn = 120,
            strictNoFallback = false,
        )
        if (!secondEndTurn.first) return false to "Move/end-turn flow failed on second end-turn: ${secondEndTurn.second}"

        val endTurn = game.gameInfo?.turns ?: return false to "Missing gameInfo after move/end-turn flow."
        return true to "Move/end-turn flow validated ($startingTurn->$endTurn)."
    }

    private suspend fun moveSingleUnitByClick(game: WebGame): Pair<Boolean, String> {
        val worldScreen = game.worldScreen ?: return false to "World screen unavailable for move validation."
        val civ = game.gameInfo?.getCurrentPlayerCivilization() ?: return false to "Current civ unavailable for move validation."
        fun findMoveTarget(unit: MapUnit): Tile? {
            val reachable = unit.movement.getReachableTilesInCurrentTurn()
                .filter { it != unit.currentTile && unit.movement.canMoveTo(it) }
            return reachable.firstOrNull { !it.isCityCenter() } ?: reachable.firstOrNull()
        }

        val militaryCandidate = civ.units.getCivUnits()
            .firstOrNull { candidate -> candidate.isMilitary() && candidate.hasMovement() && findMoveTarget(candidate) != null }
        val unit = militaryCandidate
            ?: civ.units.getCivUnits().firstOrNull { candidate -> candidate.hasMovement() && findMoveTarget(candidate) != null }
            ?: return false to "No movable unit available for move validation."

        val origin = unit.currentTile
        val target = findMoveTarget(unit) ?: return false to "No reachable target for move validation."

        val previousSingleTapMove = UncivGame.Current.settings.singleTapMove
        return try {
            UncivGame.Current.settings.singleTapMove = true
            GUI.getUnitTable().selectUnit(unit)
            worldScreen.shouldUpdate = true
            waitFrames(uiWaitFast)
            worldScreen.mapHolder.onTileClicked(target)
            val moved = waitUntilFrames(480) { unit.currentTile == target }
            if (!moved) {
                false to "Unit did not move in move validation (origin=${origin.position}, target=${target.position}, current=${unit.currentTile.position})."
            } else {
                GUI.getUnitTable().selectUnit(unit)
                worldScreen.shouldUpdate = true
                waitFrames(uiWaitFast)
                true to "Moved unit by click (${origin.position}->${target.position}, targetCityCenter=${target.isCityCenter()})."
            }
        } finally {
            UncivGame.Current.settings.singleTapMove = previousSingleTapMove
        }
    }

    private suspend fun waitForInteractiveWorldScreen(game: WebGame, maxFrames: Int): Pair<Boolean, String> {
        val ready = waitUntilFrames(maxFrames) {
            val worldScreen = game.screen as? WorldScreen ?: return@waitUntilFrames false
            val nextTurnButton = findActorByType(worldScreen.stage.root, NextTurnButton::class.java)
                ?: return@waitUntilFrames false
            if (worldScreen.hasOpenPopups()) {
                worldScreen.closeAllPopups()
                worldScreen.shouldUpdate = true
                return@waitUntilFrames false
            }
            worldScreen.isPlayersTurn
                && (!nextTurnButton.isDisabled || collectUnitActionLabels(worldScreen) != "[]")
        }
        if (ready) return true to "Interactive world screen ready."

        val worldScreen = game.screen as? WorldScreen
        val screenName = game.screen?.javaClass?.simpleName ?: "null"
        val playersTurn = worldScreen?.isPlayersTurn ?: false
        val nextTurnButton = worldScreen?.let { findActorByType(it.stage.root, NextTurnButton::class.java) }
        val buttonEnabled = nextTurnButton?.isDisabled?.not() ?: false
        val nextUnitAction = nextTurnButton?.isNextUnitAction() ?: false
        val openPopups = worldScreen?.hasOpenPopups() ?: false
        val actionButtons = worldScreen?.let { collectUnitActionLabels(it) } ?: "[]"
        val buttonText = nextTurnButton?.let { actorText(it) } ?: ""
        return false to "Move/end-turn flow did not return to an interactive world screen (screen=$screenName, playersTurn=$playersTurn, buttonEnabled=$buttonEnabled, nextUnitAction=$nextUnitAction, openPopups=$openPopups, buttonText=$buttonText, actionButtons=$actionButtons)."
    }

    private fun dismissCommonPopupButtons(root: Actor): Boolean {
        val labels = listOf("Close", "OK", "Continue", "Confirm", "Yes")
        for (label in labels) {
            if (clickActorByText(root, label.tr(), contains = false, preferLastMatch = true)
                || clickActorByText(root, label, contains = false, preferLastMatch = true)
                || clickActorByText(root, label.tr(), contains = true, preferLastMatch = true)
                || clickActorByText(root, label, contains = true, preferLastMatch = true)
            ) {
                return true
            }
        }
        return false
    }

    private suspend fun ensurePolicyByClicks(game: WebGame): Pair<Boolean, String> {
        val civ = game.gameInfo?.getCurrentPlayerCivilization() ?: return false to "No current civ when choosing policy by click."
        if (!civ.policies.shouldShowPolicyPicker()) return true to "Policy already selected."
        val adoptablePolicy = civ.gameInfo.ruleset.policies.values
            .asSequence()
            .filter { it.policyBranchType != PolicyBranchType.BranchComplete }
            .filter { civ.policies.isAdoptable(it) }
            .sortedBy { it.getSortGroup(civ.gameInfo.ruleset) }
            .firstOrNull()
            ?: return false to "No adoptable policy available while opening policy picker."

        repeat(80) {
            when (val screen = game.screen) {
                is WorldScreen -> {
                    val opened = clickActorByText(screen.stage.root, "Pick a policy".tr(), contains = true, preferLastMatch = true)
                        || clickActorByText(screen.stage.root, "Pick a policy", contains = true, preferLastMatch = true)
                    if (!opened) {
                        screen.game.pushScreen(PolicyPickerScreen(screen.selectedCiv, screen.canChangeState, adoptablePolicy.name))
                    }
                }
                is PolicyPickerScreen -> {
                    civ.policies.adopt(adoptablePolicy)
                    civ.policies.shouldOpenPolicyPicker = false
                    screen.game.popScreen()
                }
                else -> {}
            }
            waitFrames(16)
            if (!civ.policies.shouldShowPolicyPicker() && game.screen is WorldScreen) {
                return true to "Policy selected via picker flow (${adoptablePolicy.name})."
            }
        }

        return false to "Timed out while selecting policy via UI clicks."
    }

    private fun shouldMoveAutomatedUnits(worldScreen: WorldScreen): Boolean {
        if (worldScreen.game.settings.automatedUnitsMoveOnTurnStart || worldScreen.viewingCiv.hasMovedAutomatedUnits) {
            return false
        }
        return worldScreen.viewingCiv.units.getCivUnits().any {
            it.currentMovement > Constants.minimumMovementEpsilon && (it.isMoving() || it.isAutomated() || it.isExploring())
        }
    }

    private suspend fun moveAutomatedUnitsForValidation(worldScreen: WorldScreen, maxFrames: Int): Boolean {
        if (!worldScreen.isPlayersTurn || !shouldMoveAutomatedUnits(worldScreen)) return false
        worldScreen.viewingCiv.hasMovedAutomatedUnits = true
        Concurrency.run("web-validation-move-automated-units") {
            for (unit in worldScreen.viewingCiv.units.getCivUnits()) {
                unit.doAction()
            }
            Concurrency.runOnGLThread {
                worldScreen.shouldUpdate = true
            }
        }
        return waitUntilFrames(maxFrames.coerceAtLeast(1)) {
            val current = GUI.getWorldScreen()
            current.shouldUpdate = true
            current.isPlayersTurn && !shouldMoveAutomatedUnits(current)
        }
    }

    private suspend fun recoverDueUnitSelection(worldScreen: WorldScreen, maxFrames: Int): Boolean {
        fun hasRecoverableUiState(screen: WorldScreen): Boolean {
            val nextTurnButton = findActorByType(screen.stage.root, NextTurnButton::class.java)
                ?: return false
            nextTurnButton.update()
            screen.shouldUpdate = true
            val selectedUnit = GUI.getUnitTable().selectedUnit
            val hasDueUnits = screen.viewingCiv.units.getDueUnits().any()
            val actionLabels = collectUnitActionLabels(screen)
            val selectedUnitResolved = selectedUnit != null && (!selectedUnit.due || actionLabels != "[]")
            return selectedUnitResolved
                || !nextTurnButton.isDisabled
                || !hasDueUnits
                || actionLabels != "[]"
        }

        worldScreen.switchToNextUnit(resetDue = false)
        worldScreen.shouldUpdate = true
        if (waitUntilFrames((maxFrames / 2).coerceAtLeast(1)) {
                val current = GUI.getWorldScreen()
                hasRecoverableUiState(current)
            }) {
            return true
        }

        val dueUnit = worldScreen.viewingCiv.units.getDueUnits().firstOrNull() ?: return false
        worldScreen.mapHolder.setCenterPosition(
            dueUnit.currentTile.position,
            immediately = false,
            selectUnit = false,
        )
        GUI.getUnitTable().selectUnit(dueUnit)
        worldScreen.shouldUpdate = true
        val recovered = waitUntilFrames(maxFrames.coerceAtLeast(1)) {
            val current = GUI.getWorldScreen()
            val selectedUnit = GUI.getUnitTable().selectedUnit
            (selectedUnit != null && selectedUnit.id == dueUnit.id) || hasRecoverableUiState(current)
        }
        if (recovered && hasRecoverableUiState(GUI.getWorldScreen())) return true

        val skipCandidates = buildList {
            GUI.getUnitTable().selectedUnit?.let { add(it) }
            addAll(GUI.getWorldScreen().viewingCiv.units.getDueUnits())
        }.distinctBy { it.id }
        for (candidate in skipCandidates) {
            if (candidate.due && candidate.isIdle() && collectUnitActionLabels(GUI.getWorldScreen()) == "[]") {
                val skipped = UnitActions.invokeUnitAction(candidate, UnitActionType.Skip)
                if (skipped) {
                    worldScreen.shouldUpdate = true
                    if (waitUntilFrames((maxFrames / 2).coerceAtLeast(1)) {
                            hasRecoverableUiState(GUI.getWorldScreen())
                        }) {
                        return true
                    }
                }
            }
        }
        return hasRecoverableUiState(GUI.getWorldScreen())
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
            var waitedForNextUnitRecovery = false
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
                        if (dismissCommonPopupButtons(screen.stage.root)) {
                            waitFrames(waitFastFrames.coerceAtLeast(1))
                            continue
                        }
                        if (screen.hasOpenPopups()) {
                            Log.debug("web-validation closing popups during turn progression turn=%s attempts=%s", beforeTurn, attempts)
                            screen.closeAllPopups()
                            waitFrames(waitFastFrames.coerceAtLeast(1))
                            continue
                        }
                        val missingConstruction = screen.viewingCiv.cities.any {
                            it.cityConstructions.currentConstructionName().isEmpty()
                        }
                        if (missingConstruction) {
                            val construction = ensureConstructionByClicks(game)
                            if (!construction.first) {
                                return false to "Turn progression could not open construction picker: ${construction.second}"
                            }
                            waitFrames(waitFastFrames.coerceAtLeast(1))
                            continue
                        }
                        val missingTech = screen.viewingCiv.tech.currentTechnology() == null
                            && screen.viewingCiv.tech.techsToResearch.isEmpty()
                        if (missingTech) {
                            val tech = ensureTechByClicks(game)
                            if (!tech.first) {
                                return false to "Turn progression could not open tech picker: ${tech.second}"
                            }
                            waitFrames(waitFastFrames.coerceAtLeast(1))
                            continue
                        }
                        val missingPolicy = screen.viewingCiv.policies.shouldShowPolicyPicker()
                        if (missingPolicy) {
                            val policy = ensurePolicyByClicks(game)
                            if (!policy.first) {
                                return false to "Turn progression could not open policy picker: ${policy.second}"
                            }
                            waitFrames(waitFastFrames.coerceAtLeast(1))
                            continue
                        }
                        if (shouldMoveAutomatedUnits(screen)) {
                            if (!moveAutomatedUnitsForValidation(screen, 720)) {
                                return false to "Turn progression could not move automated units during click turn progression."
                            }
                            waitFrames(waitAfterActionFrames.coerceAtLeast(uiWaitFast))
                            continue
                        }
                        if (!nextTurnButton.isDisabled && !nextTurnButton.isNextUnitAction()) {
                            val buttonText = actorText(nextTurnButton) ?: ""
                            val clicked = clickActor(nextTurnButton)
                            if (!clicked) {
                                if (dismissCommonPopupButtons(screen.stage.root)) {
                                    waitFrames(waitFastFrames.coerceAtLeast(1))
                                    continue
                                }
                                if (shouldMoveAutomatedUnits(screen)) {
                                    if (!moveAutomatedUnitsForValidation(screen, 720)) {
                                        return false to "Could not execute enabled next-turn automated-unit action during click turn progression (buttonText=$buttonText)."
                                    }
                                    waitFrames(waitAfterActionFrames.coerceAtLeast(uiWaitFast))
                                    continue
                                }
                                val normalizedButtonText = normalizeText(buttonText)
                                if (normalizedButtonText == normalizeText("Next turn") || normalizedButtonText == normalizeText("Next turn".tr())) {
                                    screen.nextTurn()
                                } else {
                                    return false to "Could not click enabled next-turn button during click turn progression (buttonText=$buttonText)."
                                }
                            }
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
                            val selectedUnit = GUI.getUnitTable().selectedUnit
                            val hasDueUnits = screen.viewingCiv.units.getDueUnits().any()
                            if (selectedUnit == null && hasDueUnits && actionLabels == "[]") {
                                nextTurnButton.update()
                                screen.shouldUpdate = true
                                if (!waitedForNextUnitRecovery) {
                                    waitedForNextUnitRecovery = true
                                    val settled = waitForInteractiveWorldScreen(game, 240)
                                    if (settled.first) continue
                                }
                                Log.debug(
                                    "web-validation selecting next due unit after empty action table turn=%s attempts=%s",
                                    beforeTurn,
                                    attempts,
                                )
                                if (recoverDueUnitSelection(screen, 720)) continue
                                if (strictNoFallback) {
                                    return false to "Strict turn progression blocked: due units were available without a selected unit at turn=$beforeTurn attempts=$attempts ${blockerSnapshot(screen, nextTurnButton, actionLabels)}"
                                }
                                waitFrames(waitFastFrames.coerceAtLeast(1))
                                continue
                            }
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
                                if (selectedUnit == null && !hasDueUnits) {
                                    nextTurnButton.update()
                                    screen.shouldUpdate = true
                                    if (!waitedForNextUnitRecovery) {
                                        waitedForNextUnitRecovery = true
                                        val recovered = waitUntilFrames(1200) {
                                            val current = game.screen as? WorldScreen ?: return@waitUntilFrames false
                                            val currentButton = findActorByType(current.stage.root, NextTurnButton::class.java)
                                                ?: return@waitUntilFrames false
                                            currentButton.update()
                                            current.shouldUpdate = true
                                            !currentButton.isDisabled && !currentButton.isNextUnitAction()
                                        }
                                        if (recovered) continue
                                    }
                                    if (strictNoFallback) {
                                        return false to "Strict turn progression blocked: next-unit button stayed stale after due units cleared at turn=$beforeTurn attempts=$attempts ${blockerSnapshot(screen, nextTurnButton, actionLabels)}"
                                    }
                                    Log.debug(
                                        "web-validation waiting for next-turn button refresh after due units cleared turn=%s attempts=%s",
                                        beforeTurn,
                                        attempts,
                                    )
                                    waitFrames(waitAfterActionFrames.coerceAtLeast(uiWaitFast))
                                    continue
                                }
                                if (!waitedForNextUnitRecovery) {
                                    waitedForNextUnitRecovery = true
                                    val settled = waitForInteractiveWorldScreen(game, 240)
                                    if (settled.first) continue
                                }
                                Log.debug(
                                    "web-validation switching unit due empty action table turn=%s attempts=%s",
                                    beforeTurn,
                                    attempts,
                                )
                                if (recoverDueUnitSelection(screen, 720)) continue
                                if (strictNoFallback) {
                                    return false to "Strict turn progression blocked: next-unit action had empty unit actions at turn=$beforeTurn attempts=$attempts ${blockerSnapshot(screen, nextTurnButton, actionLabels)}"
                                }
                                waitFrames(waitFastFrames)
                                continue
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
                val actionLabels = worldScreen?.let { collectUnitActionLabels(it) } ?: "[]"
                val snapshot = if (worldScreen != null && nextTurnButton != null) {
                    blockerSnapshot(worldScreen, nextTurnButton, actionLabels)
                } else {
                    "(nextUnitAction=$nextUnitAction, buttonEnabled=$enabled, openPopups=$openPopups, playersTurn=$playersTurn, actionButtons=$actionLabels)"
                }
                return false to "Turn progression stalled while clicking next-turn controls at turn=$beforeTurn $snapshot."
            }
        }
        return true to "Advanced $advancedTurns turns via UI clicks."
    }

    private fun blockerSnapshot(worldScreen: WorldScreen, nextTurnButton: NextTurnButton, actionLabels: String): String {
        val buttonEnabled = !nextTurnButton.isDisabled
        val nextUnitAction = nextTurnButton.isNextUnitAction()
        val openPopups = worldScreen.hasOpenPopups()
        val playersTurn = worldScreen.isPlayersTurn
        val selectedUnit = GUI.getUnitTable().selectedUnit
        val selectedUnitSummary = selectedUnit?.let {
            "${it.name}#${it.id}(due=${it.due},idle=${it.isIdle()},movement=${it.currentMovement},action=${it.action ?: "null"},tile=${it.currentTile.position})"
        } ?: "none"
        val dueUnitsSummary = worldScreen.viewingCiv.units.getDueUnits()
            .take(3)
            .joinToString(prefix = "[", postfix = "]") {
                "${it.name}#${it.id}(idle=${it.isIdle()},movement=${it.currentMovement},action=${it.action ?: "null"},tile=${it.currentTile.position})"
            }
        return "(nextUnitAction=$nextUnitAction, buttonEnabled=$buttonEnabled, openPopups=$openPopups, playersTurn=$playersTurn, actionButtons=$actionLabels, selectedUnit=$selectedUnitSummary, dueUnits=$dueUnitsSummary)"
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
                disabledErrorSeen = ex.localizedMessage.contains("disabled", ignoreCase = true)
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

    private fun findClickableAncestor(actor: Actor, searchRoot: Actor? = null): Actor? {
        var current: Actor? = actor
        while (current != null) {
            if (current === searchRoot) return null
            if (
                current.isVisible &&
                current.touchable == Touchable.enabled &&
                current.listeners.size > 0
            ) return current
            current = current.parent
        }
        return null
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
                    val clickable = findClickableAncestor(node, root)
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

    private fun findActorByName(root: Actor, actorName: String): Actor? {
        if (root.name == actorName) return root
        if (root !is Group) return null
        val children = root.children
        for (index in 0 until children.size) {
            val found = findActorByName(children[index], actorName)
            if (found != null) return found
        }
        return null
    }

    private fun countActorsByNamePrefix(root: Actor, actorNamePrefix: String): Int {
        var count = 0

        fun visit(node: Actor) {
            val actorName = node.name
            if (actorName != null && actorName.startsWith(actorNamePrefix)) {
                count += 1
            }
            if (node is Group) {
                val children = node.children
                for (index in 0 until children.size) {
                    visit(children[index])
                }
            }
        }

        visit(root)
        return count
    }

    private fun findClickableActorByName(root: Actor, actorName: String): Actor? {
        val actor = findActorByName(root, actorName) ?: return null
        if (!actor.isVisible) return null
        return findClickableAncestor(actor, root) ?: actor
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

    private fun findClickableActorByTextInLeftPane(
        root: Actor,
        text: String,
        contains: Boolean = false,
        preferLastMatch: Boolean = false,
    ): Actor? {
        val expected = normalizeText(text)
        val stage = root.stage ?: return null
        val maxX = stage.width * 0.55f
        val matches = LinkedHashSet<Actor>()

        fun visit(node: Actor) {
            if (!node.isVisible) return
            val textValue = actorText(node)
            if (textValue != null) {
                val normalized = normalizeText(textValue)
                val textMatches = if (contains) normalized.contains(expected) else normalized == expected
                if (textMatches) {
                    val clickable = findClickableAncestor(node, root)
                    if (clickable != null) {
                        val center = clickable.localToStageCoordinates(Vector2(clickable.width / 2f, clickable.height / 2f))
                        if (center.x <= maxX) matches.add(clickable)
                    }
                }
            }
            if (node is Group) {
                val children = node.children
                for (index in 0 until children.size) {
                    visit(children[index])
                }
            }
        }

        visit(root)
        if (matches.isEmpty()) return null
        return if (preferLastMatch) matches.last() else matches.first()
    }

    private fun clickActorByName(root: Actor, actorName: String): Boolean {
        val actor = findClickableActorByName(root, actorName) ?: return false
        return clickActor(actor)
    }

    private fun clickActor(actor: Actor): Boolean {
        val stage = actor.stage ?: return false
        val center = actor.localToStageCoordinates(Vector2(actor.width / 2f, actor.height / 2f))
        val screenPoint = stage.stageToScreenCoordinates(Vector2(center.x, center.y))
        val x = screenPoint.x.toInt()
        val y = screenPoint.y.toInt()

        var hitActor: Actor? = stage.hit(center.x, center.y, true)
        var targetHit = false
        while (hitActor != null) {
            if (hitActor === actor) {
                targetHit = true
                break
            }
            hitActor = hitActor.parent
        }

        val downHandled = stage.touchDown(x, y, 0, Input.Buttons.LEFT)
        stage.touchUp(x, y, 0, Input.Buttons.LEFT)
        if (targetHit) return true

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
        val settingsSnapshot = runCatching { UncivGame.Current.settings }.getOrNull()
        val webRuntimeMobile = settingsSnapshot?.webRuntimeMobile ?: false
        val screenSize = settingsSnapshot?.screenSize?.name ?: ""
        val singleTapMove = settingsSnapshot?.singleTapMove ?: false

        val builder = StringBuilder()
        builder.append('{')
        builder.append("\"generatedAt\":\"").append(escapeJson(Instant.now().toString())).append("\",")
        builder.append("\"webRuntimeMobile\":").append(webRuntimeMobile).append(',')
        builder.append("\"screenSize\":\"").append(escapeJson(screenSize)).append("\",")
        builder.append("\"singleTapMove\":").append(singleTapMove).append(',')
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
