package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.UncivShowableException
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.event.EventBus
import com.unciv.logic.map.MapVisualization
import com.unciv.logic.multiplayer.MultiplayerGameUpdated
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.MultiplayerAuthException
import com.unciv.logic.trade.TradeEvaluation
import com.unciv.models.TutorialTrigger
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.Event
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.components.extensions.centerX
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.input.KeyShortcutDispatcherVeto
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.KeyboardPanningListener
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.AuthPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.popups.hasOpenPopups
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.cityscreen.CityScreen
import com.unciv.ui.screens.devconsole.DevConsolePopup
import com.unciv.ui.screens.mainmenuscreen.MainMenuScreen
import com.unciv.ui.screens.newgamescreen.NewGameScreen
import com.unciv.ui.screens.overviewscreen.EmpireOverviewCategories
import com.unciv.ui.screens.overviewscreen.EmpireOverviewScreen
import com.unciv.ui.screens.pickerscreens.DiplomaticVoteResultScreen
import com.unciv.ui.screens.pickerscreens.GreatPersonPickerScreen
import com.unciv.ui.screens.savescreens.LoadGameScreen
import com.unciv.ui.screens.savescreens.QuickSave
import com.unciv.ui.screens.savescreens.SaveGameScreen
import com.unciv.ui.screens.victoryscreen.VictoryScreen
import com.unciv.ui.screens.worldscreen.bottombar.BattleTable
import com.unciv.ui.screens.worldscreen.bottombar.TileInfoTable
import com.unciv.ui.screens.worldscreen.chat.ChatButton
import com.unciv.ui.screens.worldscreen.mainmenu.WorldScreenMusicPopup
import com.unciv.ui.screens.worldscreen.minimap.MinimapHolder
import com.unciv.ui.screens.worldscreen.status.AutoPlayStatusButton
import com.unciv.ui.screens.worldscreen.status.MultiplayerStatusButton
import com.unciv.ui.screens.worldscreen.status.NextTurnButton
import com.unciv.ui.screens.worldscreen.status.NextTurnProgress
import com.unciv.ui.screens.worldscreen.status.SmallUnitButton
import com.unciv.ui.screens.worldscreen.status.StatusButtons
import com.unciv.ui.screens.worldscreen.topbar.WorldScreenTopBar
import com.unciv.ui.screens.worldscreen.unit.AutoPlay
import com.unciv.ui.screens.worldscreen.unit.UnitTable
import com.unciv.ui.screens.worldscreen.unit.actions.UnitActionsTable
import com.unciv.ui.screens.worldscreen.worldmap.WorldMapHolder
import com.unciv.ui.screens.worldscreen.worldmap.WorldMapTileUpdater.updateTiles
import com.unciv.utils.Concurrency
import com.unciv.utils.debug
import com.unciv.utils.launchOnGLThread
import com.unciv.utils.launchOnThreadPool
import com.unciv.utils.withGLContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import java.util.Timer
import kotlin.concurrent.timer

/**
 * Do not create this screen without seriously thinking about the implications: this is the single most memory-intensive class in the application.
 * There really should ever be only one in memory at the same time, likely managed by [UncivGame].
 *
 * @param gameInfo The game state the screen should represent
 * @param viewingCiv The currently active [civilization][Civilization]
 * @param restoreState
 */
class WorldScreen(
    val gameInfo: GameInfo,
    val autoPlay: AutoPlay,
    val viewingCiv: Civilization,
    restoreState: RestoreState? = null
) : BaseScreen() {
    /** When set, causes the screen to update in the next [render][render] event */
    var shouldUpdate = false

    /** Indicates it's the player's ([viewingCiv]) turn */
    var isPlayersTurn = viewingCiv.isCurrentPlayer()
        internal set     // only this class is allowed to make changes

    /** Selected civilization, used in spectator and replay mode, equals viewingCiv in ordinary games */
    var selectedCiv = viewingCiv

    var fogOfWar = true

    /** `true` when it's the player's turn unless he is a spectator */
    val canChangeState
        get() = isPlayersTurn && !viewingCiv.isSpectator()

    val mapHolder = WorldMapHolder(this, gameInfo.tileMap)

    internal var waitingForAutosave = false
    private val mapVisualization = MapVisualization(gameInfo, viewingCiv)

    // Floating Widgets going counter-clockwise
    internal val topBar = WorldScreenTopBar(this)
    internal val techPolicyAndDiplomacy = TechPolicyDiplomacyButtons(this)
    internal val chatButton = ChatButton(this)
    private val unitActionsTable = UnitActionsTable(this)
    /** Bottom left widget holding information about a selected unit or city */
    internal val bottomUnitTable = UnitTable(this)
    private val battleTable = BattleTable(this)
    private val zoomController = ZoomButtonPair(mapHolder)
    internal val minimapWrapper = MinimapHolder(mapHolder)
    private val bottomTileInfoTable = TileInfoTable(this)
    internal val notificationsScroll = NotificationsScroll(this)
    internal val nextTurnButton = NextTurnButton(this)
    private val statusButtons = StatusButtons(nextTurnButton)
    internal val smallUnitButton = SmallUnitButton(this, statusButtons)
    private val tutorialTaskTable = Table().apply {
        background = skinStrings.getUiBackground("WorldScreen/TutorialTaskTable", tintColor = skinStrings.skinConfig.baseColor.darken(0.5f))
    }
    private var tutorialTaskTableHash = 0

    private var nextTurnUpdateJob: Job? = null

    private val events = EventBus.EventReceiver()

    private var uiEnabled = true

    internal val undoHandler = UndoHandler(this)


    init {
        // notifications are right-aligned, they take up only as much space as necessary.
        notificationsScroll.width = stage.width / 2

        minimapWrapper.x = stage.width - minimapWrapper.width

        // This is the most memory-intensive operation we have currently, most OutOfMemory errors will occur here
        mapHolder.addTiles()
        mapHolder.reloadMaxZoom()

        // resume music (in case choices from the menu lead to instantiation of a new WorldScreen)
        UncivGame.Current.musicController.resume()

        stage.addActor(mapHolder)
        stage.scrollFocus = mapHolder
        stage.addActor(notificationsScroll)  // very low in z-order, so we're free to let it extend _below_ tile info and minimap if we want
        stage.addActor(tutorialTaskTable)    // behind topBar!
        stage.addActor(topBar)
        stage.addActor(statusButtons)
        stage.addActor(techPolicyAndDiplomacy)
        stage.addActor(chatButton)

        stage.addActor(zoomController)
        zoomController.isVisible = UncivGame.Current.settings.showZoomButtons

        stage.addActor(bottomUnitTable)
        stage.addActor(unitActionsTable)
        stage.addActor(bottomTileInfoTable)
        stage.addActor(minimapWrapper)
        battleTable.width = stage.width / 3
        battleTable.x = stage.width / 3
        stage.addActor(battleTable)

        val tileToCenterOn: Vector2 =
                when {
                    viewingCiv.getCapital() != null -> viewingCiv.getCapital()!!.location
                    viewingCiv.units.getCivUnits().any() -> viewingCiv.units.getCivUnits().first().getTile().position
                    else -> Vector2.Zero
                }

        mapHolder.isAutoScrollEnabled = Gdx.app.type == Application.ApplicationType.Desktop && game.settings.mapAutoScroll
        mapHolder.mapPanningSpeed = game.settings.mapPanningSpeed

        // Don't select unit and change selectedCiv when centering as spectator
        if (viewingCiv.isSpectator())
            mapHolder.setCenterPosition(tileToCenterOn, immediately = true, selectUnit = false)
        else
            mapHolder.setCenterPosition(tileToCenterOn, immediately = true, selectUnit = true)

        tutorialController.allTutorialsShowedCallback = { shouldUpdate = true }

        addKeyboardListener() // for map panning by W,S,A,D
        addKeyboardPresses()  // shortcut keys like F1


        if (gameInfo.gameParameters.isOnlineMultiplayer && !gameInfo.isUpToDate)
            isPlayersTurn = false // until we're up to date, don't let the player do anything

        if (gameInfo.gameParameters.isOnlineMultiplayer) {
            val gameId = gameInfo.gameId
            events.receive(MultiplayerGameUpdated::class, { it.preview.gameId == gameId }) {
                if (isNextTurnUpdateRunning() || game.onlineMultiplayer.hasLatestGameState(gameInfo, it.preview)) {
                    return@receive
                }
                Concurrency.run("Load latest multiplayer state") {
                    loadLatestMultiplayerState()
                }
            }
        }

        if (restoreState != null) restore(restoreState)

        // don't run update() directly, because the UncivGame.worldScreen should be set so that the city buttons and tile groups
        //  know what the viewing civ is.
        shouldUpdate = true
    }

    override fun dispose() {
        resizeDeferTimer?.cancel()
        events.stopReceiving()
        statusButtons.dispose()
        super.dispose()
    }

    override fun getCivilopediaRuleset() = gameInfo.ruleset

    // Handle disabling and re-enabling WASD listener while Options are open
    override fun openOptionsPopup(startingPage: Int, withDebug: Boolean, onClose: () -> Unit) {
        val oldListener = stage.root.listeners.filterIsInstance<KeyboardPanningListener>().firstOrNull()
        if (oldListener != null) {
            stage.removeListener(oldListener)
            oldListener.dispose()
        }
        super.openOptionsPopup(startingPage, withDebug) {
            addKeyboardListener()
            onClose()
        }
    }

    fun openEmpireOverview(category: EmpireOverviewCategories? = null) {
        game.pushScreen(EmpireOverviewScreen(selectedCiv, category))
    }

    fun openNewGameScreen() {
        val newGameSetupInfo = GameSetupInfo(gameInfo)
        newGameSetupInfo.mapParameters.reseed()
        val newGameScreen = NewGameScreen(newGameSetupInfo)
        game.pushScreen(newGameScreen)
    }

    fun openSaveGameScreen() {
        // See #10353 - we don't support locally saving an online multiplayer game
        if (gameInfo.gameParameters.isOnlineMultiplayer) return
        game.pushScreen(SaveGameScreen(gameInfo))
    }

    private fun addKeyboardPresses() {
        globalShortcuts.add(KeyboardBinding.DeselectOrQuit) { backButtonAndESCHandler() }

        // Space and N are assigned in NextTurnButton constructor
        // Functions that have a big button are assigned there (WorldScreenTopBar, TechPolicyDiplomacyButtons..)
        globalShortcuts.add(KeyboardBinding.Civilopedia) { openCivilopedia() }
        globalShortcuts.add(KeyboardBinding.EmpireOverviewTrades) { openEmpireOverview(EmpireOverviewCategories.Trades) }
        globalShortcuts.add(KeyboardBinding.EmpireOverviewUnits) { openEmpireOverview(EmpireOverviewCategories.Units) }
        globalShortcuts.add(KeyboardBinding.EmpireOverviewPolitics) { openEmpireOverview(EmpireOverviewCategories.Politics) }
        globalShortcuts.add(KeyboardBinding.EmpireOverviewNotifications) { openEmpireOverview(EmpireOverviewCategories.Notifications) }
        globalShortcuts.add(KeyboardBinding.VictoryScreen) { game.pushScreen(VictoryScreen(this)) }
        globalShortcuts.add(KeyboardBinding.EmpireOverviewStats) { openEmpireOverview(EmpireOverviewCategories.Stats) }
        globalShortcuts.add(KeyboardBinding.EmpireOverviewResources) { openEmpireOverview(EmpireOverviewCategories.Resources) }
        globalShortcuts.add(KeyboardBinding.QuickSave) { QuickSave.save(gameInfo, this) }
        globalShortcuts.add(KeyboardBinding.QuickLoad) { QuickSave.load(this) }
        globalShortcuts.add(KeyboardBinding.ViewCapitalCity) {
            val capital = gameInfo.getCurrentPlayerCivilization().getCapital()
            if (capital != null && !mapHolder.setCenterPosition(capital.location))
                game.pushScreen(CityScreen(capital))
        }
        globalShortcuts.add(KeyboardBinding.Options) { // Game Options
            openOptionsPopup { nextTurnButton.update() }
        }
        globalShortcuts.add(KeyboardBinding.SaveGame) { openSaveGameScreen() }    //   Save
        globalShortcuts.add(KeyboardBinding.LoadGame) { game.pushScreen(LoadGameScreen()) }    //   Load
        globalShortcuts.add(KeyboardBinding.QuitGame) { game.popScreen() }    //   WorldScreen is the last screen, so this quits
        globalShortcuts.add(KeyboardBinding.NewGame) { openNewGameScreen() }
        globalShortcuts.add(KeyboardBinding.MusicPlayer) {
            WorldScreenMusicPopup(this).open(force = true)
        }
        globalShortcuts.add(Input.Keys.NUMPAD_ADD) { this.mapHolder.zoomIn() }    //   '+' Zoom
        globalShortcuts.add(Input.Keys.NUMPAD_SUBTRACT) { this.mapHolder.zoomOut() }    //   '-' Zoom
        globalShortcuts.add(KeyboardBinding.ToggleUI) { toggleUI() }
        globalShortcuts.add(KeyboardBinding.ToggleYieldDisplay) { minimapWrapper.yieldImageButton.toggle() }
        globalShortcuts.add(KeyboardBinding.ToggleWorkedTilesDisplay) { minimapWrapper.populationImageButton.toggle() }
        globalShortcuts.add(KeyboardBinding.ToggleMovementDisplay) { minimapWrapper.movementsImageButton.toggle() }
        globalShortcuts.add(KeyboardBinding.ToggleResourceDisplay) { minimapWrapper.resourceImageButton.toggle() }
        globalShortcuts.add(KeyboardBinding.ToggleImprovementDisplay) { minimapWrapper.improvementsImageButton.toggle() }

        globalShortcuts.add(KeyboardBinding.DeveloperConsole, action = ::openDeveloperConsole)
    }

    fun openDeveloperConsole() {
        // No cheating unless you're by yourself
        if (gameInfo.civilizations.count { it.isHuman() } > 1) return
        DevConsolePopup(this)
    }

    private fun toggleUI() {
        uiEnabled = !uiEnabled
        topBar.isVisible = uiEnabled
        statusButtons.isVisible = uiEnabled
        techPolicyAndDiplomacy.isVisible = uiEnabled
        tutorialTaskTable.isVisible = uiEnabled
        bottomTileInfoTable.isVisible = uiEnabled
        unitActionsTable.isVisible = uiEnabled
        notificationsScroll.isVisible = uiEnabled
        minimapWrapper.isVisible = uiEnabled
        bottomUnitTable.isVisible = uiEnabled
        if (uiEnabled) battleTable.update() else battleTable.isVisible = false
    }

    private fun addKeyboardListener() {
        stage.addListener(KeyboardPanningListener(mapHolder, allowWASD = true))
    }

    // We contain a map...
    override fun getShortcutDispatcherVetoer() = KeyShortcutDispatcherVeto.createTileGroupMapDispatcherVetoer()

    private suspend fun loadLatestMultiplayerState(): Unit = coroutineScope {
        if (game.screen != this@WorldScreen) return@coroutineScope // User already went somewhere else

        val loadingGamePopup = Popup(this@WorldScreen)
        launchOnGLThread {
            loadingGamePopup.addGoodSizedLabel("Loading latest game state...")
            loadingGamePopup.open()
        }

        try {
            debug("loadLatestMultiplayerState current game: gameId: %s, turn: %s, curCiv: %s",
                gameInfo.gameId, gameInfo.turns, gameInfo.currentPlayer)
            val latestGame = game.onlineMultiplayer.multiplayerServer.downloadGame(gameInfo.gameId)
            debug("loadLatestMultiplayerState downloaded game: gameId: %s, turn: %s, curCiv: %s",
                latestGame.gameId, latestGame.turns, latestGame.currentPlayer)
            if (viewingCiv.civName == latestGame.currentPlayer || viewingCiv.civName == Constants.spectator) {
                game.notifyTurnStarted()
            }
            launchOnGLThread {
                loadingGamePopup.close()
            }
            startNewScreenJob(latestGame, autoPlay)
        } catch (ex: Throwable) {
            launchOnGLThread {
                val (message) = LoadGameScreen.getLoadExceptionMessage(ex, "Couldn't download the latest game state!")
                loadingGamePopup.clear()
                loadingGamePopup.addGoodSizedLabel(message).colspan(2).row()
                loadingGamePopup.addButton("Retry") {
                    launchOnThreadPool("Load latest multiplayer state after error") {
                        loadLatestMultiplayerState()
                    }
                }.right()
                loadingGamePopup.addButton("Main menu") {
                    game.pushScreen(MainMenuScreen())
                }.left()
            }
        }
    }

    // This is private so that we will set the shouldUpdate to true instead.
    // That way, not only do we save a lot of unnecessary updates, we also ensure that all updates are called from the main GL thread
    // and we don't get any silly concurrency problems!
    private fun update() {

        if (uiEnabled) {
            displayTutorialsOnUpdate()

            bottomUnitTable.update()

            updateSelectedCiv()

            if (fogOfWar) minimapWrapper.update(selectedCiv)
            else minimapWrapper.update(viewingCiv)

            if (fogOfWar) bottomTileInfoTable.selectedCiv = selectedCiv
            else bottomTileInfoTable.selectedCiv = viewingCiv
            bottomTileInfoTable.updateTileTable(mapHolder.selectedTile)
            bottomTileInfoTable.x = stage.width - bottomTileInfoTable.width
            bottomTileInfoTable.y = if (game.settings.showMinimap) minimapWrapper.height + 5f else 0f

            battleTable.update()

            displayTutorialTaskOnUpdate()
        }

        mapHolder.resetArrows()
        if (UncivGame.Current.settings.showUnitMovements) {
            val allUnits = gameInfo.civilizations.asSequence().flatMap { it.units.getCivUnits() }
            val allAttacks = allUnits.map { unit -> unit.attacksSinceTurnStart.asSequence().map { attacked -> Triple(unit.civ, unit.getTile().position, attacked) } }.flatten() +
                gameInfo.civilizations.asSequence().flatMap { civInfo -> civInfo.attacksSinceTurnStart.asSequence().map { Triple(civInfo, it.source, it.target) } }
            mapHolder.updateMovementOverlay(
                allUnits.filter(mapVisualization::isUnitPastVisible),
                allUnits.filter(mapVisualization::isUnitFutureVisible),
                allAttacks.filter { (attacker, source, target) -> mapVisualization.isAttackVisible(attacker, source, target) }
                        .map { (_, source, target) -> source to target }
            )
        }

        zoomController.isVisible = UncivGame.Current.settings.showZoomButtons

        // if we use the clone, then when we update viewable tiles
        // it doesn't update the explored tiles of the civ... need to think about that harder
        // it causes a bug when we move a unit to an unexplored tile (for instance a cavalry unit which can move far)

        if (fogOfWar) mapHolder.updateTiles(selectedCiv)
        else mapHolder.updateTiles(viewingCiv)

        topBar.update(selectedCiv)
        if (tutorialTaskTable.isVisible)
            tutorialTaskTable.y = topBar.getYForTutorialTask() - tutorialTaskTable.height

        if (techPolicyAndDiplomacy.update())
            displayTutorial(TutorialTrigger.OtherCivEncountered)

        if (uiEnabled) {
            // UnitActionsTable measures geometry (its own y, techPolicyAndDiplomacy and fogOfWarButton), so call update this late
            unitActionsTable.y = bottomUnitTable.height
            unitActionsTable.update(bottomUnitTable.selectedUnit)
        }

        // If the game has ended, lets stop AutoPlay
        if (autoPlay.isAutoPlaying() && !gameInfo.oneMoreTurnMode && (viewingCiv.isDefeated() || gameInfo.checkForVictory())) {
            autoPlay.stopAutoPlay()
        }

        if (!hasOpenPopups() && !autoPlay.isAutoPlaying() && isPlayersTurn) {
            when {
                viewingCiv.shouldShowDiplomaticVotingResults() ->
                    UncivGame.Current.pushScreen(DiplomaticVoteResultScreen(gameInfo.diplomaticVictoryVotesCast, viewingCiv))
                !gameInfo.oneMoreTurnMode && (viewingCiv.isDefeated() || gameInfo.checkForVictory()) ->
                    game.pushScreen(VictoryScreen(this))
                viewingCiv.greatPeople.freeGreatPeople > 0 ->
                    game.pushScreen(GreatPersonPickerScreen(this, viewingCiv))
                viewingCiv.popupAlerts.any() -> AlertPopup(this, viewingCiv.popupAlerts.first())
                viewingCiv.tradeRequests.isNotEmpty() -> {
                    // In the meantime this became invalid, perhaps because we accepted previous trades
                    for (tradeRequest in viewingCiv.tradeRequests.toList())
                        if (!TradeEvaluation().isTradeValid(tradeRequest.trade, viewingCiv,
                                gameInfo.getCivilization(tradeRequest.requestingCiv)))
                            viewingCiv.tradeRequests.remove(tradeRequest)

                    if (viewingCiv.tradeRequests.isNotEmpty()) // if a valid one still exists
                        TradePopup(this).open()
                }
            }
        }

        updateGameplayButtons()

        val coveredNotificationsTop = stage.height - statusButtons.y
        val coveredNotificationsBottom = (bottomTileInfoTable.height + bottomTileInfoTable.y)
//                (if (game.settings.showMinimap) minimapWrapper.height else 0f)
        notificationsScroll.update(viewingCiv.notifications, coveredNotificationsTop, coveredNotificationsBottom)

        val posZoomFromRight = if (game.settings.showMinimap) minimapWrapper.width
        else bottomTileInfoTable.width
        zoomController.setPosition(stage.width - posZoomFromRight - 10f, 10f, Align.bottomRight)
    }

    private fun getCurrentTutorialTask(): Event? {
        if (!game.settings.tutorialTasksCompleted.contains("Create a trade route")) {
            if (viewingCiv.cache.citiesConnectedToCapitalToMediums.any { it.key.civ == viewingCiv })
                game.settings.addCompletedTutorialTask("Create a trade route")
        }
        val stateForConditionals = viewingCiv.state
        return gameInfo.ruleset.events.values.firstOrNull {
            it.presentation == Event.Presentation.Floating &&
                it.isAvailable(stateForConditionals)
        }
    }

    private fun displayTutorialsOnUpdate() {

        displayTutorial(TutorialTrigger.Introduction)

        displayTutorial(TutorialTrigger.EnemyCityNeedsConqueringWithMeleeUnit) {
            viewingCiv.diplomacy.values.asSequence()
                    .filter { it.diplomaticStatus == DiplomaticStatus.War }
                    .map { it.otherCiv() } // we're now lazily enumerating over CivilizationInfo's we're at war with
                    .flatMap { it.cities.asSequence() } // ... all *their* cities
                    .filter { it.health == 1 } // ... those ripe for conquering
                    .flatMap { it.getCenterTile().getTilesInDistance(2) }
                    // ... all tiles around those in range of an average melee unit
                    // -> and now we look for a unit that could do the conquering because it's ours
                    //    no matter whether civilian, air or ranged, tell user he needs melee
                    .any { it.getUnits().any { unit -> unit.civ == viewingCiv } }
        }
        displayTutorial(TutorialTrigger.AfterConquering) { viewingCiv.cities.any { it.hasJustBeenConquered } }

        displayTutorial(TutorialTrigger.InjuredUnits) { gameInfo.getCurrentPlayerCivilization().units.getCivUnits().any { it.health < 100 } }

        displayTutorial(TutorialTrigger.Workers) {
            gameInfo.getCurrentPlayerCivilization().units.getCivUnits().any {
                it.cache.hasUniqueToBuildImprovements && it.isCivilian() && !it.isGreatPerson()
            }
        }
    }

    private fun displayTutorialTaskOnUpdate() {
        fun setInvisible() {
            tutorialTaskTable.isVisible = false
            tutorialTaskTable.clear()
            tutorialTaskTableHash = 0
        }
        if (!game.settings.showTutorials || viewingCiv.isDefeated()) return setInvisible()
        val tutorialTask = getCurrentTutorialTask() ?: return setInvisible()

        if (!UncivGame.Current.isTutorialTaskCollapsed) {
            val hash = tutorialTask.hashCode()  // Default implementation is OK - we see the same instance or not
            if (hash != tutorialTaskTableHash) {
                val renderEvent = RenderEvent(tutorialTask, this) {
                    shouldUpdate = true
                }
                if (!renderEvent.isValid) return setInvisible()
                tutorialTaskTable.clear()
                tutorialTaskTable.add(renderEvent).pad(10f)
                tutorialTaskTableHash = hash
            }
        } else {
            tutorialTaskTable.clear()
            tutorialTaskTable.add(ImageGetter.getImage("OtherIcons/HiddenTutorialTask").apply { setSize(30f,30f) }).pad(5f)
            tutorialTaskTableHash = 0
        }
        tutorialTaskTable.pack()
        tutorialTaskTable.centerX(stage)
        tutorialTaskTable.y = topBar.getYForTutorialTask() - tutorialTaskTable.height
        tutorialTaskTable.onClick {
            UncivGame.Current.isTutorialTaskCollapsed = !UncivGame.Current.isTutorialTaskCollapsed
            displayTutorialTaskOnUpdate()
        }
        tutorialTaskTable.isVisible = true
    }

    private fun updateSelectedCiv() {
        selectedCiv = when {
            bottomUnitTable.selectedUnit != null -> bottomUnitTable.selectedUnit!!.civ
            bottomUnitTable.selectedCity != null -> bottomUnitTable.selectedCity!!.civ
            else -> viewingCiv
        }
    }

    class RestoreState(
        mapHolder: WorldMapHolder,
        val selectedCivName: String,
        val viewingCivName: String,
        val fogOfWar: Boolean
    ) {
        val zoom = mapHolder.scaleX
        val scrollX = mapHolder.scrollX
        val scrollY = mapHolder.scrollY
    }
    fun getRestoreState(): RestoreState {
        return RestoreState(mapHolder, selectedCiv.civName, viewingCiv.civName, fogOfWar)
    }

    private fun restore(restoreState: RestoreState) {

        // This is not the case if you have a multiplayer game where you play as 2 civs
        if (viewingCiv.civName == restoreState.viewingCivName) {
            mapHolder.zoom(restoreState.zoom)
            mapHolder.scrollX = restoreState.scrollX
            mapHolder.scrollY = restoreState.scrollY
            mapHolder.updateVisualScroll()
        }

        selectedCiv = gameInfo.getCivilization(restoreState.selectedCivName)
        fogOfWar = restoreState.fogOfWar
    }

    fun nextTurn() {
        isPlayersTurn = false
        shouldUpdate = true
        val progressBar = NextTurnProgress(nextTurnButton)
        progressBar.start(this)

        // on a separate thread so the user can explore their world while we're passing the turn
        nextTurnUpdateJob = Concurrency.runOnNonDaemonThreadPool("NextTurn") {
            debug("Next turn starting")
            val startTime = System.currentTimeMillis()
            val originalGameInfo = gameInfo
            val gameInfoClone = originalGameInfo.clone()
            gameInfoClone.setTransients()  // this can get expensive on large games, not the clone itself

            progressBar.increment()

            gameInfoClone.nextTurn(progressBar)

            if (originalGameInfo.gameParameters.isOnlineMultiplayer) {
                try {
                    game.onlineMultiplayer.updateGame(gameInfoClone)
                }catch (ex: Exception) {
                    when (ex) {
                        is MultiplayerAuthException -> {
                            launchOnGLThread {
                                AuthPopup(this@WorldScreen) {
                                        success -> if (success) nextTurn()
                                }.open(true)
                            }
                        }
                        is FileStorageRateLimitReached -> {
                            val message = "Server limit reached! Please wait for [${ex.limitRemainingSeconds}] seconds"
                            launchOnGLThread {
                                val cantUploadNewGamePopup = Popup(this@WorldScreen)
                                cantUploadNewGamePopup.addGoodSizedLabel(message).row()
                                cantUploadNewGamePopup.addCloseButton()
                                cantUploadNewGamePopup.open()
                            }
                        }
                        else -> {
                            val message = "Could not upload game! Reason: [${ex.message ?: "Unknown"}]"
                            launchOnGLThread {
                                val cantUploadNewGamePopup = Popup(this@WorldScreen)
                                cantUploadNewGamePopup.addGoodSizedLabel(message).row()
                                cantUploadNewGamePopup.addButton("Copy to clipboard") {
                                    Gdx.app.clipboard.contents = ex.stackTraceToString()
                                }
                                cantUploadNewGamePopup.addCloseButton()
                                cantUploadNewGamePopup.open()
                            }
                        }
                    }

                    this@WorldScreen.isPlayersTurn = true // Since we couldn't push the new game clone, then it's like we never clicked the "next turn" button
                    this@WorldScreen.shouldUpdate = true
                    return@runOnNonDaemonThreadPool
                }
            }

            if (game.gameInfo != originalGameInfo) // while this was turning we loaded another game
                return@runOnNonDaemonThreadPool

            debug("Next turn took %sms", System.currentTimeMillis() - startTime)

            // Special case: when you are the only alive human player, the game will always be up to date
            if (gameInfo.gameParameters.isOnlineMultiplayer
                    && gameInfoClone.civilizations.count { it.isAlive() && it.playerType == PlayerType.Human } == 1) {
                gameInfoClone.isUpToDate = true
            }

            progressBar.increment()

            startNewScreenJob(gameInfoClone, autoPlay)
        }
    }

    fun switchToNextUnit(resetDue: Boolean = true) {
        // Try to select something new if we already have the next pending unit selected.
        if (bottomUnitTable.selectedUnit != null && resetDue)
            bottomUnitTable.selectedUnit!!.due = false
        val nextDueUnit = viewingCiv.units.cycleThroughDueUnits(bottomUnitTable.selectedUnit)
        if (nextDueUnit != null) {
            mapHolder.setCenterPosition(
                nextDueUnit.currentTile.position,
                immediately = false,
                selectUnit = false
            )
            bottomUnitTable.selectUnit(nextDueUnit)
        } else {
            mapHolder.removeAction(mapHolder.blinkAction)
            mapHolder.selectedTile = null
            bottomUnitTable.selectUnit()
        }
        shouldUpdate = true
    }

    internal fun isNextTurnUpdateRunning(): Boolean {
        val job = nextTurnUpdateJob
        return job != null && job.isActive
    }

    private fun updateGameplayButtons() {
        nextTurnButton.update()

        updateAutoPlayStatusButton()
        updateMultiplayerStatusButton()

        statusButtons.update(false)
        val maxWidth = stage.width - techPolicyAndDiplomacy.width - 25f
        if(statusButtons.width > maxWidth) {
            statusButtons.update(true)
        }
        statusButtons.setPosition(stage.width - statusButtons.width - 10f, topBar.y - statusButtons.height - 10f)

        // Update chat button position to always be below techPolicyAndDiplomacy
        chatButton.updatePosition()
    }

    private fun updateAutoPlayStatusButton() {
        if (statusButtons.autoPlayStatusButton == null) {
            if (game.settings.autoPlay.showAutoPlayButton)
                statusButtons.autoPlayStatusButton = AutoPlayStatusButton(this, nextTurnButton)
        } else {
            if (!game.settings.autoPlay.showAutoPlayButton) {
                statusButtons.autoPlayStatusButton = null
                autoPlay.stopAutoPlay()
            }
        }
    }

    private fun updateMultiplayerStatusButton() {
        if (gameInfo.gameParameters.isOnlineMultiplayer || game.settings.multiplayer.statusButtonInSinglePlayer) {
            if (statusButtons.multiplayerStatusButton != null) return
            statusButtons.multiplayerStatusButton = MultiplayerStatusButton(this,
                game.onlineMultiplayer.multiplayerFiles.getGameByGameId(gameInfo.gameId))
        } else {
            if (statusButtons.multiplayerStatusButton == null) return
            statusButtons.multiplayerStatusButton = null
        }
    }


    private var resizeDeferTimer: Timer? = null

    override fun resize(width: Int, height: Int) {
        resizeDeferTimer?.cancel()
        if (resizeDeferTimer == null && stage.viewport.screenWidth == width && stage.viewport.screenHeight == height) return
        resizeDeferTimer = timer("Resize", daemon = true, 500L, Long.MAX_VALUE) {
            resizeDeferTimer?.cancel()
            resizeDeferTimer = null
            startNewScreenJob(gameInfo, autoPlay, true) // start over
        }
    }

    override fun render(delta: Float) {
        //  This is so that updates happen in the MAIN THREAD, where there is a GL Context,
        //    otherwise images will not load properly!
        if (shouldUpdate && resizeDeferTimer == null) {
            shouldUpdate = false

            // Since updating the worldscreen can take a long time, *especially* the first time, we disable input processing to avoid ANRs
            Gdx.input.inputProcessor = null
            update()
            showTutorialsOnNextTurn()
            if (Gdx.input.inputProcessor == null) // Update may have replaced the worldscreen with a GreatPersonPickerScreen etc, so the input would already be set
                Gdx.input.inputProcessor = stage
        }

        super.render(delta)
    }


    private fun showTutorialsOnNextTurn() {
        if (!game.settings.showTutorials || autoPlay.isAutoPlaying()) return
        displayTutorial(TutorialTrigger.SlowStart)
        displayTutorial(TutorialTrigger.CityExpansion) { viewingCiv.cities.any { it.expansion.tilesClaimed() > 0 } }
        displayTutorial(TutorialTrigger.BarbarianEncountered) { viewingCiv.viewableTiles.any { it.getUnits().any { unit -> unit.civ.isBarbarian } } }
        displayTutorial(TutorialTrigger.RoadsAndRailroads) { viewingCiv.cities.size > 2 }
        displayTutorial(TutorialTrigger.Happiness) { viewingCiv.getHappiness() < 5 }
        displayTutorial(TutorialTrigger.Unhappiness) { viewingCiv.getHappiness() < 0 }
        displayTutorial(TutorialTrigger.GoldenAge) { viewingCiv.goldenAges.isGoldenAge() }
        displayTutorial(TutorialTrigger.IdleUnits) { gameInfo.turns >= 50 && game.settings.checkForDueUnits }
        displayTutorial(TutorialTrigger.ContactMe) { gameInfo.turns >= 100 }
        val resources = viewingCiv.detailedCivResources.asSequence().filter { it.origin == "All" }  // Avoid full list copy
        displayTutorial(TutorialTrigger.LuxuryResource) { resources.any { it.resource.resourceType == ResourceType.Luxury } }
        displayTutorial(TutorialTrigger.StrategicResource) { resources.any { it.resource.resourceType == ResourceType.Strategic } }
        displayTutorial(TutorialTrigger.EnemyCity) {
            viewingCiv.getKnownCivs().filter { viewingCiv.isAtWarWith(it) }
                    .flatMap { it.cities.asSequence() }.any { viewingCiv.hasExplored(it.getCenterTile()) }
        }
        displayTutorial(TutorialTrigger.ApolloProgram) { viewingCiv.hasUnique(UniqueType.EnablesConstructionOfSpaceshipParts) }
        displayTutorial(TutorialTrigger.SiegeUnits) { viewingCiv.units.getCivUnits().any { it.baseUnit.isProbablySiegeUnit() } }
        displayTutorial(TutorialTrigger.Embarking) { viewingCiv.hasUnique(UniqueType.LandUnitEmbarkation) }
        displayTutorial(TutorialTrigger.NaturalWonders) { viewingCiv.naturalWonders.size > 0 }
        displayTutorial(TutorialTrigger.WeLoveTheKingDay) { viewingCiv.cities.any { it.demandedResource != "" } }
    }

    private fun backButtonAndESCHandler() {

        // Deselect Unit
        if (bottomUnitTable.selectedUnit != null) {
            bottomUnitTable.selectUnit()
            shouldUpdate = true
            return
        }

        // Deselect city
        if (bottomUnitTable.selectedCity != null) {
            bottomUnitTable.selectUnit()
            shouldUpdate = true
            return
        }

        if (bottomUnitTable.selectedSpy != null) {
            bottomUnitTable.selectSpy(null)
            shouldUpdate = true
            return
        }

        game.popScreen()
    }

    fun autoSave() {
        waitingForAutosave = true
        shouldUpdate = true
        UncivGame.Current.files.autosaves.requestAutoSave(gameInfo, true).invokeOnCompletion {
            // only enable the user to next turn once we've saved the current one
            waitingForAutosave = false
            shouldUpdate = true
        }
    }
}

/** This exists so that no reference to the current world screen remains, so the old world screen can get garbage collected during [UncivGame.loadGame]. */
private fun startNewScreenJob(gameInfo: GameInfo, autoPlay: AutoPlay, autosaveDisabled: Boolean = false) {
    Concurrency.run {
        val newWorldScreen = try {
            UncivGame.Current.loadGame(gameInfo, autoPlay)
        } catch (notAPlayer: UncivShowableException) {
            withGLContext {
                val (message) = LoadGameScreen.getLoadExceptionMessage(notAPlayer)
                val mainMenu = UncivGame.Current.goToMainMenu()
                ToastPopup(message, mainMenu)
            }
            return@run
        } catch (_: OutOfMemoryError) {
            withGLContext {
                val mainMenu = UncivGame.Current.goToMainMenu()
                ToastPopup("Not enough memory on phone to load game!", mainMenu)
            }
            return@run
        }

        val shouldAutoSave = !autosaveDisabled
                && gameInfo.turns % UncivGame.Current.settings.turnsBetweenAutosaves == 0
        if (shouldAutoSave) {
            newWorldScreen.autoSave()
        }
    }
}
