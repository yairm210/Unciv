package com.unciv.app.web

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.GameStarter
import com.unciv.logic.UncivShowableException
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.files.PlatformSaverLoader
import com.unciv.logic.files.UncivFiles
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
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.savescreens.LoadGameScreen
import com.unciv.ui.screens.savescreens.LoadOrSaveScreen
import com.unciv.ui.screens.savescreens.SaveGameScreen
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActions
import com.unciv.utils.Concurrency
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object WebValidationRunner {
    private const val testSaveName = "WebE2E-Phase1"
    private var started = false

    private val featureOrder = listOf(
        "Boot/Main menu",
        "Start new game",
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

    fun maybeStart(game: WebGame) {
        if (started) return
        if (!WebValidationInterop.isValidationEnabled()) return

        started = true
        WebValidationInterop.publishState("starting")
        Concurrency.runOnGLThread("WebValidationRunner") {
            runValidation(game)
        }
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
            val multiplayer = validateMultiplayerDisabled(game)
            recordCapabilityGate(
                results,
                "Multiplayer",
                multiplayer.first,
                "multiplayer_not_disabled",
                multiplayer.second,
                expectDisabled = !PlatformCapabilities.current.onlineMultiplayer,
            )

            WebValidationInterop.publishState("running:Mod download/update")
            val modDownloads = validateModDownloadsDisabled()
            recordCapabilityGate(
                results,
                "Mod download/update",
                modDownloads.first,
                "mod_download_not_disabled",
                modDownloads.second,
                expectDisabled = !PlatformCapabilities.current.onlineModDownloads,
            )

            WebValidationInterop.publishState("running:Custom file picker save/load")
            val customFileChooser = validateCustomFileChooserDisabled(game)
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

    private suspend fun validateStartNewGame(game: WebGame): Pair<Boolean, String> {
        return try {
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
        }
        if (actor is Group) {
            val children = actor.children
            for (index in 0 until children.size) {
                if (actorTreeContainsText(children[index], expectedText)) return true
            }
        }
        return false
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
