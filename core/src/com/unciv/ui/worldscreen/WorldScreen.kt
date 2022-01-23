package com.unciv.ui.worldscreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.RepeatAction
import com.badlogic.gdx.scenes.scene2d.ui.Button
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.ReligionState
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.map.MapVisualization
import com.unciv.logic.trade.TradeEvaluation
import com.unciv.models.Tutorial
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.tile.ResourceType
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.translations.tr
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.civilopedia.CivilopediaScreen
import com.unciv.ui.overviewscreen.EmpireOverviewScreen
import com.unciv.ui.pickerscreens.*
import com.unciv.ui.saves.LoadGameScreen
import com.unciv.ui.saves.SaveGameScreen
import com.unciv.ui.trade.DiplomacyScreen
import com.unciv.ui.utils.*
import com.unciv.ui.utils.UncivDateFormat.formatDate
import com.unciv.ui.victoryscreen.VictoryScreen
import com.unciv.ui.worldscreen.bottombar.BattleTable
import com.unciv.ui.worldscreen.bottombar.TileInfoTable
import com.unciv.ui.worldscreen.mainmenu.OnlineMultiplayer
import com.unciv.ui.worldscreen.unit.UnitActionsTable
import com.unciv.ui.worldscreen.unit.UnitTable
import java.util.*
import kotlin.concurrent.thread
import kotlin.concurrent.timer

/**
 * Unciv's world screen
 * @param gameInfo The game state the screen should represent
 * @param viewingCiv The currently active [civilization][CivilizationInfo]
 * @property shouldUpdate When set, causes the screen to update in the next [render][BaseScreen.render] event
 * @property isPlayersTurn (readonly) Indicates it's the player's ([viewingCiv]) turn
 * @property selectedCiv Selected civilization, used in spectator and replay mode, equals viewingCiv in ordinary games
 * @property canChangeState (readonly) `true` when it's the player's turn unless he is a spectator
 * @property mapHolder A [MinimapHolder] instance
 * @property bottomUnitTable Bottom left widget holding information about a selected unit or city
 */
class WorldScreen(val gameInfo: GameInfo, val viewingCiv:CivilizationInfo) : BaseScreen() {

    var isPlayersTurn = viewingCiv == gameInfo.currentPlayerCiv
        private set     // only this class is allowed to make changes
    var selectedCiv = viewingCiv
    private var fogOfWar = true
    val canChangeState
        get() = isPlayersTurn && !viewingCiv.isSpectator()
    private var waitingForAutosave = false
    val mapVisualization = MapVisualization(gameInfo, viewingCiv)

    val mapHolder = WorldMapHolder(this, gameInfo.tileMap)
    private val minimapWrapper = MinimapHolder(mapHolder)

    private val topBar = WorldScreenTopBar(this)
    val bottomUnitTable = UnitTable(this)
    private val bottomTileInfoTable = TileInfoTable(viewingCiv)
    private val battleTable = BattleTable(this)
    private val unitActionsTable = UnitActionsTable(this)

    private val techPolicyAndVictoryHolder = Table()
    private val techButtonHolder = Table()
    private val diplomacyButtonHolder = Table()
    private val fogOfWarButton = createFogOfWarButton()
    private val nextTurnButton = createNextTurnButton()
    private var nextTurnAction: () -> Unit = {}
    private val tutorialTaskTable = Table().apply { background = ImageGetter.getBackground(ImageGetter.getBlue().lerp(Color.BLACK, 0.5f)) }

    private val notificationsScroll: NotificationsScroll
    var shouldUpdate = false

    companion object {
        /** Switch for console logging of next turn duration */
        private const val consoleLog = false

        // this object must not be created multiple times
        private var multiPlayerRefresher: Timer? = null
    }

    init {
        topBar.setPosition(0f, stage.height - topBar.height)
        topBar.width = stage.width

        notificationsScroll = NotificationsScroll(this)
        // notifications are right-aligned, they take up only as much space as necessary.
        notificationsScroll.width = stage.width / 2

        minimapWrapper.x = stage.width - minimapWrapper.width

        try { // Most memory errors occur here, so this is a sort of catch-all
            mapHolder.addTiles()
        } catch (outOfMemoryError: OutOfMemoryError) {
            mapHolder.clear() // hopefully enough memory will be freed to be able to display the toast popup
            ToastPopup("Not enough memory on phone to load game!", this)
        }

        // resume music (in case choices from the menu lead to instantiation of a new WorldScreen)
        UncivGame.Current.musicController.resume()

        techButtonHolder.touchable = Touchable.enabled
        techButtonHolder.onClick(UncivSound.Paper) {
            game.setScreen(TechPickerScreen(viewingCiv))
        }
        techPolicyAndVictoryHolder.add(techButtonHolder)

        fogOfWarButton.isVisible = viewingCiv.isSpectator()
        fogOfWarButton.setPosition(10f, topBar.y - fogOfWarButton.height - 10f)

        // Don't show policies until they become relevant
        if (viewingCiv.policies.adoptedPolicies.isNotEmpty() || viewingCiv.policies.canAdoptPolicy()) {
            val policyScreenButton = Button(skin)
            policyScreenButton.add(ImageGetter.getImage("PolicyIcons/Constitution")).size(30f).pad(15f)
            policyScreenButton.onClick { game.setScreen(PolicyPickerScreen(this)) }
            techPolicyAndVictoryHolder.add(policyScreenButton).pad(10f)
        }

        stage.addActor(mapHolder)
        stage.scrollFocus = mapHolder
        stage.addActor(minimapWrapper)
        stage.addActor(topBar)
        stage.addActor(nextTurnButton)
        stage.addActor(techPolicyAndVictoryHolder)
        stage.addActor(notificationsScroll)
        stage.addActor(tutorialTaskTable)


        diplomacyButtonHolder.defaults().pad(5f)
        stage.addActor(fogOfWarButton)
        stage.addActor(diplomacyButtonHolder)
        stage.addActor(bottomUnitTable)
        stage.addActor(bottomTileInfoTable)
        battleTable.width = stage.width / 3
        battleTable.x = stage.width / 3
        stage.addActor(battleTable)

        stage.addActor(unitActionsTable)

        val tileToCenterOn: Vector2 =
                when {
                    viewingCiv.cities.isNotEmpty() -> viewingCiv.getCapital().location
                    viewingCiv.getCivUnits().any() -> viewingCiv.getCivUnits().first().getTile().position
                    else -> Vector2.Zero
                }

        // Don't select unit and change selectedCiv when centering as spectator
        if (viewingCiv.isSpectator())
            mapHolder.setCenterPosition(tileToCenterOn, immediately = true, selectUnit = false)
        else
            mapHolder.setCenterPosition(tileToCenterOn, immediately = true, selectUnit = true)


        tutorialController.allTutorialsShowedCallback = { shouldUpdate = true }

        onBackButtonClicked { backButtonAndESCHandler() }

        addKeyboardListener() // for map panning by W,S,A,D
        addKeyboardPresses()  // shortcut keys like F1


        if (gameInfo.gameParameters.isOnlineMultiplayer && !gameInfo.isUpToDate)
            isPlayersTurn = false // until we're up to date, don't let the player do anything

        if (gameInfo.gameParameters.isOnlineMultiplayer && !isPlayersTurn) {
            // restart the timer
            stopMultiPlayerRefresher()

            // isDaemon = true, in order to not block the app closing
            // DO NOT use Timer() since this seems to (maybe?) translate to com.badlogic.gdx.utils.Timer? Not sure about this.
            multiPlayerRefresher = timer("multiPlayerRefresh", true, period = 10000) {
                loadLatestMultiplayerState()
            }
        }

        // don't run update() directly, because the UncivGame.worldScreen should be set so that the city buttons and tile groups
        //  know what the viewing civ is.
        shouldUpdate = true
    }

    private fun stopMultiPlayerRefresher() {
        if (multiPlayerRefresher != null) {
            multiPlayerRefresher?.cancel()
            multiPlayerRefresher?.purge()
        }
    }

    private fun addKeyboardPresses() {
        // Note these helpers might need unification with similar code e.g. in:
        // GameSaver.autoSave, SaveGameScreen.saveGame, LoadGameScreen.rightSideButton.onClick,...
        val quickSave = {
            val toast = ToastPopup("Quicksaving...", this)
            crashHandlingThread(name = "SaveGame") {
                GameSaver.saveGame(gameInfo, "QuickSave") {
                    postCrashHandlingRunnable {
                        toast.close()
                        if (it != null)
                            ToastPopup("Could not save game!", this)
                        else {
                            ToastPopup("Quicksave successful.", this)
                        }
                    }
                }
            }
            Unit    // change type of anonymous fun from ()->Thread to ()->Unit without unchecked cast
        }
        val quickLoad = {
            val toast = ToastPopup("Quickloading...", this)
            crashHandlingThread(name = "SaveGame") {
                try {
                    val loadedGame = GameSaver.loadGameByName("QuickSave")
                    postCrashHandlingRunnable {
                        toast.close()
                        UncivGame.Current.loadGame(loadedGame)
                        ToastPopup("Quickload successful.", this)
                    }
                } catch (ex: Exception) {
                    postCrashHandlingRunnable {
                        ToastPopup("Could not load game!", this)
                    }
                }
            }
            Unit    // change type to ()->Unit
        }

        // Space and N are assigned in createNextTurnButton
        keyPressDispatcher[Input.Keys.F1] = { game.setScreen(CivilopediaScreen(gameInfo.ruleSet, this)) }
        keyPressDispatcher['E'] = { game.setScreen(EmpireOverviewScreen(selectedCiv)) }     // Empire overview last used page
        /*
         * These try to be faithful to default Civ5 key bindings as found in several places online
         * Some are a little arbitrary, e.g. Economic info, Military info
         * Some are very much so as Unciv *is* Strategic View and the Notification log is always visible
         */
        keyPressDispatcher[Input.Keys.F2] = { game.setScreen(EmpireOverviewScreen(selectedCiv, "Trades")) }    // Economic info
        keyPressDispatcher[Input.Keys.F3] = { game.setScreen(EmpireOverviewScreen(selectedCiv, "Units")) }    // Military info
        keyPressDispatcher[Input.Keys.F4] = { game.setScreen(EmpireOverviewScreen(selectedCiv, "Diplomacy")) }    // Diplomacy info
        keyPressDispatcher[Input.Keys.F5] = { game.setScreen(PolicyPickerScreen(this, selectedCiv)) }    // Social Policies Screen
        keyPressDispatcher[Input.Keys.F6] = { game.setScreen(TechPickerScreen(viewingCiv)) }    // Tech Screen
        keyPressDispatcher[Input.Keys.F7] = { game.setScreen(EmpireOverviewScreen(selectedCiv, "Cities")) }    // originally Notification Log
        keyPressDispatcher[Input.Keys.F8] = { game.setScreen(VictoryScreen(this)) }    // Victory Progress
        keyPressDispatcher[Input.Keys.F9] = { game.setScreen(EmpireOverviewScreen(selectedCiv, "Stats")) }    // Demographics
        keyPressDispatcher[Input.Keys.F10] = { game.setScreen(EmpireOverviewScreen(selectedCiv, "Resources")) }    // originally Strategic View
        keyPressDispatcher[Input.Keys.F11] = quickSave    // Quick Save
        keyPressDispatcher[Input.Keys.F12] = quickLoad    // Quick Load
        keyPressDispatcher[Input.Keys.HOME] = {    // Capital City View
            val capital = gameInfo.currentPlayerCiv.getCapital()
            if (!mapHolder.setCenterPosition(capital.location))
                game.setScreen(CityScreen(capital))
        }
        keyPressDispatcher[KeyCharAndCode.ctrl('O')] = { this.openOptionsPopup() }    //   Game Options
        keyPressDispatcher[KeyCharAndCode.ctrl('S')] = { game.setScreen(SaveGameScreen(gameInfo)) }    //   Save
        keyPressDispatcher[KeyCharAndCode.ctrl('L')] = { game.setScreen(LoadGameScreen(this)) }    //   Load
        keyPressDispatcher[Input.Keys.NUMPAD_ADD] = { this.mapHolder.zoomIn() }    //   '+' Zoom
        keyPressDispatcher[Input.Keys.NUMPAD_SUBTRACT] = { this.mapHolder.zoomOut() }    //   '-' Zoom
        keyPressDispatcher.setCheckpoint()
    }

    private fun addKeyboardListener() {
        stage.addListener(
                object : InputListener() {
                    private val pressedKeys = mutableSetOf<Int>()
                    private var infiniteAction: RepeatAction? = null
                    private val amountToMove = 6 / mapHolder.scaleX
                    private val ALLOWED_KEYS = setOf(Input.Keys.W, Input.Keys.S, Input.Keys.A, Input.Keys.D,
                            Input.Keys.UP, Input.Keys.DOWN, Input.Keys.LEFT, Input.Keys.RIGHT)


                    override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
                        if (keycode !in ALLOWED_KEYS) return false
                        // Without the following keyPressDispatcher Ctrl-S would leave WASD map scrolling stuck
                        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT) || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) return false

                        pressedKeys.add(keycode)
                        if (infiniteAction == null) {
                            // create a copy of the action, because removeAction() will destroy this instance
                            infiniteAction = Actions.forever(Actions.delay(0.01f, Actions.run { whileKeyPressedLoop() }))
                            mapHolder.addAction(infiniteAction)
                        }
                        return true
                    }

                    fun whileKeyPressedLoop() {
                        for (keycode in pressedKeys) {
                            when (keycode) {
                                Input.Keys.W, Input.Keys.UP -> mapHolder.scrollY -= amountToMove
                                Input.Keys.S, Input.Keys.DOWN -> mapHolder.scrollY += amountToMove
                                Input.Keys.A, Input.Keys.LEFT -> mapHolder.scrollX -= amountToMove
                                Input.Keys.D, Input.Keys.RIGHT -> mapHolder.scrollX += amountToMove
                            }
                        }
                        mapHolder.updateVisualScroll()
                    }

                    override fun keyUp(event: InputEvent?, keycode: Int): Boolean {
                        if (keycode !in ALLOWED_KEYS) return false

                        pressedKeys.remove(keycode)
                        if (infiniteAction != null && pressedKeys.isEmpty()) {
                            // stop the loop otherwise it keeps going even after removal
                            infiniteAction?.finish()
                            // remove and nil the action
                            mapHolder.removeAction(infiniteAction)
                            infiniteAction = null
                        }
                        return true
                    }
                }
        )

    }

    private fun loadLatestMultiplayerState() {
        // Since we're on a background thread, all the UI calls in this func need to run from the
        // main thread which has a GL context
        val loadingGamePopup = Popup(this)
        postCrashHandlingRunnable {
            loadingGamePopup.add("Loading latest game state...".tr())
            loadingGamePopup.open()
        }

        try {
            val latestGame = OnlineMultiplayer().tryDownloadGame(gameInfo.gameId)

            // if we find the current player didn't change, don't update
            // Additionally, check if we are the current player, and in that case always stop
            // This fixes a bug where for some reason players were waiting for themselves.
            if (gameInfo.currentPlayer == latestGame.currentPlayer 
                && gameInfo.turns == latestGame.turns 
                && latestGame.currentPlayer != gameInfo.getPlayerToViewAs().civName
            ) {
                postCrashHandlingRunnable { loadingGamePopup.close() }
                shouldUpdate = true
                return
            } else { // if the game updated, even if it's not our turn, reload the world -
                // stuff has changed and the "waiting for X" will now show the correct civ
                stopMultiPlayerRefresher()
                latestGame.isUpToDate = true
                postCrashHandlingRunnable { createNewWorldScreen(latestGame) }
            }

        } catch (ex: Throwable) {
            postCrashHandlingRunnable {
                val couldntDownloadLatestGame = Popup(this)
                couldntDownloadLatestGame.addGoodSizedLabel("Couldn't download the latest game state!").row()
                couldntDownloadLatestGame.addCloseButton()
                couldntDownloadLatestGame.addAction(Actions.delay(5f, Actions.run { couldntDownloadLatestGame.close() }))
                loadingGamePopup.close()
                couldntDownloadLatestGame.open()
            }
        }
    }

    // This is private so that we will set the shouldUpdate to true instead.
    // That way, not only do we save a lot of unnecessary updates, we also ensure that all updates are called from the main GL thread
    // and we don't get any silly concurrency problems!
    private fun update() {

        displayTutorialsOnUpdate()

        bottomUnitTable.update()
        bottomTileInfoTable.updateTileTable(mapHolder.selectedTile)
        bottomTileInfoTable.x = stage.width - bottomTileInfoTable.width
        bottomTileInfoTable.y = if (game.settings.showMinimap) minimapWrapper.height else 0f
        battleTable.update()

        updateSelectedCiv()

        fogOfWarButton.isEnabled = !selectedCiv.isSpectator()

        tutorialTaskTable.clear()
        val tutorialTask = getCurrentTutorialTask()
        if (tutorialTask == "" || !game.settings.showTutorials || viewingCiv.isDefeated()) {
            tutorialTaskTable.isVisible = false
        } else {
            tutorialTaskTable.isVisible = true
            tutorialTaskTable.add(tutorialTask.toLabel()
                    .apply { setAlignment(Align.center) }).pad(10f)
            tutorialTaskTable.pack()
            tutorialTaskTable.centerX(stage)
            tutorialTaskTable.y = topBar.y - tutorialTaskTable.height
        }

        if (fogOfWar) minimapWrapper.update(selectedCiv)
        else minimapWrapper.update(viewingCiv)

        keyPressDispatcher.revertToCheckPoint()
        unitActionsTable.update(bottomUnitTable.selectedUnit)
        unitActionsTable.y = bottomUnitTable.height

        mapHolder.resetArrows()
        val allUnits = gameInfo.civilizations.asSequence().flatMap { it.getCivUnits() }
        val allAttacks = allUnits.map { unit -> unit.attacksSinceTurnStart.asSequence().map { attacked -> Triple(unit.civInfo, unit.getTile().position, attacked) } }.flatten() +
            gameInfo.civilizations.asSequence().flatMap { civInfo -> civInfo.attacksSinceTurnStart.asSequence().map { Triple(civInfo, it.source, it.target) } }
        mapHolder.updateMovementOverlay(
            allUnits.filter(mapVisualization::isUnitPastVisible),
            allUnits.filter(mapVisualization::isUnitFutureVisible),
            allAttacks.filter { (attacker, source, target) -> mapVisualization.isAttackVisible(attacker, source, target) }
                    .map { (attacker, source, target) -> source to target }
        )

        // if we use the clone, then when we update viewable tiles
        // it doesn't update the explored tiles of the civ... need to think about that harder
        // it causes a bug when we move a unit to an unexplored tile (for instance a cavalry unit which can move far)

        try { // Most memory errors occur here, so this is a sort of catch-all
            if (fogOfWar) mapHolder.updateTiles(selectedCiv)
            else mapHolder.updateTiles(viewingCiv)
        } catch (outOfMemoryError: OutOfMemoryError) {
            ToastPopup("Not enough memory on phone to load game!", this)
        }

        topBar.update(selectedCiv)

        updateTechButton()
        techPolicyAndVictoryHolder.pack()
        techPolicyAndVictoryHolder.setPosition(10f, topBar.y - techPolicyAndVictoryHolder.height - 5f)
        updateDiplomacyButton(viewingCiv)

        if (!hasOpenPopups() && isPlayersTurn) {
            when {
                !gameInfo.oneMoreTurnMode && (viewingCiv.isDefeated() || gameInfo.civilizations.any { it.victoryManager.hasWon() }) ->
                    game.setScreen(VictoryScreen(this))
                viewingCiv.shouldShowDiplomaticVotingResults() ->
                    UncivGame.Current.setScreen(DiplomaticVoteResultScreen(gameInfo.diplomaticVictoryVotesCast, viewingCiv))
                viewingCiv.greatPeople.freeGreatPeople > 0 -> game.setScreen(GreatPersonPickerScreen(viewingCiv))
                viewingCiv.popupAlerts.any() -> AlertPopup(this, viewingCiv.popupAlerts.first()).open()
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
        updateNextTurnButton(hasOpenPopups()) // This must be before the notifications update, since its position is based on it
        notificationsScroll.update(viewingCiv.notifications)
        notificationsScroll.setPosition(stage.width - notificationsScroll.width * 0.5f - 10f,
                nextTurnButton.y - notificationsScroll.height * 0.5f - 5f)
    }

    private fun getCurrentTutorialTask(): String {
        val completedTasks = game.settings.tutorialTasksCompleted
        if (!completedTasks.contains("Move unit"))
            return "Move a unit!\nClick on a unit > Click on a destination > Click the arrow popup"
        if (!completedTasks.contains("Found city"))
            return "Found a city!\nSelect the Settler (flag unit) > Click on 'Found city' (bottom-left corner)"
        if (!completedTasks.contains("Enter city screen"))
            return "Enter the city screen!\nClick the city button twice"
        if (!completedTasks.contains("Pick technology"))
            return "Pick a technology to research!\nClick on the tech button (greenish, top left) > " +
                    "\n select technology > click 'Research' (bottom right)"
        if (!completedTasks.contains("Pick construction"))
            return "Pick a construction!\nEnter city screen > Click on a unit or building (bottom left side) >" +
                    " \n click 'add to queue'"
        if (!completedTasks.contains("Pass a turn"))
            return "Pass a turn!\nCycle through units with 'Next unit' > Click 'Next turn'"
        if (!completedTasks.contains("Reassign worked tiles"))
            return "Reassign worked tiles!\nEnter city screen > click the assigned (green) tile to unassign > " +
                    "\n click an unassigned tile to assign population"
        if (!completedTasks.contains("Meet another civilization"))
            return "Meet another civilization!\nExplore the map until you encounter another civilization!"
        if (!completedTasks.contains("Open the options table"))
            return "Open the options table!\nClick the menu button (top left) > click 'Options'"
        if (!completedTasks.contains("Construct an improvement"))
            return "Construct an improvement!\nConstruct a Worker unit > Move to a Plains or Grassland tile > " +
                    "\n Click 'Create improvement' (above the unit table, bottom left)" +
                    "\n > Choose the farm > \n Leave the worker there until it's finished"
        if (!completedTasks.contains("Create a trade route")
                && viewingCiv.citiesConnectedToCapitalToMediums.any { it.key.civInfo == viewingCiv })
            game.settings.addCompletedTutorialTask("Create a trade route")
        if (viewingCiv.cities.size > 1 && !completedTasks.contains("Create a trade route"))
            return "Create a trade route!\nConstruct roads between your capital and another city" +
                    "\nOr, automate your worker and let him get to that eventually"
        if (viewingCiv.isAtWar() && !completedTasks.contains("Conquer a city"))
            return "Conquer a city!\nBring an enemy city down to low health > " +
                    "\nEnter the city with a melee unit"
        if (viewingCiv.getCivUnits().any { it.baseUnit.movesLikeAirUnits() } && !completedTasks.contains("Move an air unit"))
            return "Move an air unit!\nSelect an air unit > select another city within range > " +
                    "\nMove the unit to the other city"
        if (!completedTasks.contains("See your stats breakdown"))
            return "See your stats breakdown!\nEnter the Overview screen (top right corner) >" +
                    "\nClick on 'Stats'"

        return ""
    }

    private fun displayTutorialsOnUpdate() {

        displayTutorial(Tutorial.Introduction)

        displayTutorial(Tutorial.EnemyCityNeedsConqueringWithMeleeUnit) {
            viewingCiv.diplomacy.values.asSequence()
                    .filter { it.diplomaticStatus == DiplomaticStatus.War }
                    .map { it.otherCiv() } // we're now lazily enumerating over CivilizationInfo's we're at war with
                    .flatMap { it.cities.asSequence() } // ... all *their* cities
                    .filter { it.health == 1 } // ... those ripe for conquering
                    .flatMap { it.getCenterTile().getTilesInDistance(2).asSequence() }
                    // ... all tiles around those in range of an average melee unit
                    // -> and now we look for a unit that could do the conquering because it's ours
                    //    no matter whether civilian, air or ranged, tell user he needs melee
                    .any { it.getUnits().any { unit -> unit.civInfo == viewingCiv } }
        }
        displayTutorial(Tutorial.AfterConquering) { viewingCiv.cities.any { it.hasJustBeenConquered } }

        displayTutorial(Tutorial.InjuredUnits) { gameInfo.getCurrentPlayerCivilization().getCivUnits().any { it.health < 100 } }

        displayTutorial(Tutorial.Workers) {
            gameInfo.getCurrentPlayerCivilization().getCivUnits().any {
                it.hasUniqueToBuildImprovements && it.isCivilian()
            }
        }
    }

    private fun updateDiplomacyButton(civInfo: CivilizationInfo) {
        diplomacyButtonHolder.clear()
        if (!civInfo.isDefeated() && !civInfo.isSpectator() && civInfo.getKnownCivs()
                        .filterNot { it == viewingCiv || it.isBarbarian() }
                        .any()) {
            displayTutorial(Tutorial.OtherCivEncountered)
            val btn = "Diplomacy".toTextButton()
            btn.onClick { game.setScreen(DiplomacyScreen(viewingCiv)) }
            btn.label.setFontSize(30)
            btn.labelCell.pad(10f)
            diplomacyButtonHolder.add(btn)
        }
        diplomacyButtonHolder.pack()
        diplomacyButtonHolder.y = techPolicyAndVictoryHolder.y - 20 - diplomacyButtonHolder.height
    }

    private fun updateTechButton() {
        if (gameInfo.ruleSet.technologies.isEmpty()) return
        techButtonHolder.isVisible = viewingCiv.cities.isNotEmpty()
        techButtonHolder.clearChildren()

        if (viewingCiv.tech.currentTechnology() != null) {
            val currentTech = viewingCiv.tech.currentTechnologyName()!!
            val innerButton = TechButton(currentTech, viewingCiv.tech)
            innerButton.color = colorFromRGB(7, 46, 43)
            techButtonHolder.add(innerButton)
            val turnsToTech = viewingCiv.tech.turnsToTech(currentTech)
            innerButton.text.setText(currentTech.tr() + "\r\n" + turnsToTech + Fonts.turn)
        } else if (viewingCiv.tech.canResearchTech() || viewingCiv.tech.researchedTechnologies.any()) {
            val buttonPic = Table()
            buttonPic.background = ImageGetter.getRoundedEdgeRectangle(colorFromRGB(7, 46, 43))
            buttonPic.defaults().pad(20f)
            val text = if (viewingCiv.tech.canResearchTech()) "{Pick a tech}!" else "Technologies"
            buttonPic.add(text.toLabel(Color.WHITE, 30))
            techButtonHolder.add(buttonPic)
        }

        techButtonHolder.pack()
    }

    private fun updateSelectedCiv() {
        when {
            bottomUnitTable.selectedUnit != null -> selectedCiv = bottomUnitTable.selectedUnit!!.civInfo
            bottomUnitTable.selectedCity != null -> selectedCiv = bottomUnitTable.selectedCity!!.civInfo
            else -> viewingCiv
        }
    }

    private fun createFogOfWarButton(): TextButton {
        val fogOfWarButton = "Fog of War".toTextButton()
        fogOfWarButton.label.setFontSize(30)
        fogOfWarButton.labelCell.pad(10f)
        fogOfWarButton.pack()
        fogOfWarButton.onClick {
            fogOfWar = !fogOfWar
            shouldUpdate = true
        }
        return fogOfWarButton

    }

    private fun createNextTurnButton(): TextButton {

        val nextTurnButton = TextButton("", skin) // text is set in update()
        nextTurnButton.label.setFontSize(30)
        nextTurnButton.labelCell.pad(10f)
        val nextTurnActionWrapped = { nextTurnAction() }
        nextTurnButton.onClick(nextTurnActionWrapped)
        keyPressDispatcher[Input.Keys.SPACE] = nextTurnActionWrapped
        keyPressDispatcher['n'] = nextTurnActionWrapped

        return nextTurnButton
    }


    private fun createNewWorldScreen(gameInfo: GameInfo) {

        game.gameInfo = gameInfo
        val newWorldScreen = WorldScreen(gameInfo, gameInfo.getPlayerToViewAs())

        // This is not the case if you have a multiplayer game where you play as 2 civs
        if (newWorldScreen.viewingCiv.civName == viewingCiv.civName) {
            newWorldScreen.mapHolder.scrollX = mapHolder.scrollX
            newWorldScreen.mapHolder.scrollY = mapHolder.scrollY
            newWorldScreen.mapHolder.scaleX = mapHolder.scaleX
            newWorldScreen.mapHolder.scaleY = mapHolder.scaleY
            newWorldScreen.mapHolder.updateVisualScroll()
        }

        newWorldScreen.selectedCiv = gameInfo.getCivilization(selectedCiv.civName)
        newWorldScreen.fogOfWar = fogOfWar

        game.worldScreen = newWorldScreen
        game.setWorldScreen()
    }

    private fun nextTurn() {
        isPlayersTurn = false
        shouldUpdate = true


        crashHandlingThread(name = "NextTurn") { // on a separate thread so the user can explore their world while we're passing the turn
            if (consoleLog)
                println("\nNext turn starting " + Date().formatDate())
            val startTime = System.currentTimeMillis()
            val gameInfoClone = gameInfo.clone()
            gameInfoClone.setTransients()  // this can get expensive on large games, not the clone itself

            gameInfoClone.nextTurn()

            if (gameInfo.gameParameters.isOnlineMultiplayer) {
                try {
                    OnlineMultiplayer().tryUploadGame(gameInfoClone, withPreview = true)
                } catch (ex: Exception) {
                    postCrashHandlingRunnable { // Since we're changing the UI, that should be done on the main thread
                        val cantUploadNewGamePopup = Popup(this)
                        cantUploadNewGamePopup.addGoodSizedLabel("Could not upload game!").row()
                        cantUploadNewGamePopup.addCloseButton()
                        cantUploadNewGamePopup.open()
                    }
                    isPlayersTurn = true // Since we couldn't push the new game clone, then it's like we never clicked the "next turn" button
                    shouldUpdate = true
                    return@crashHandlingThread
                }
            }

            game.gameInfo = gameInfoClone
            if (consoleLog)
                println("Next turn took ${System.currentTimeMillis()-startTime}ms")

            val shouldAutoSave = gameInfoClone.turns % game.settings.turnsBetweenAutosaves == 0

            // create a new WorldScreen to show the new stuff we've changed, and switch out the current screen.
            // do this on main thread - it's the only one that has a GL context to create images from
            postCrashHandlingRunnable {


                if (gameInfoClone.currentPlayerCiv.civName != viewingCiv.civName
                        && !gameInfoClone.gameParameters.isOnlineMultiplayer)
                    game.setScreen(PlayerReadyScreen(gameInfoClone, gameInfoClone.getCurrentPlayerCivilization()))
                else {
                    createNewWorldScreen(gameInfoClone)
                }

                if (shouldAutoSave) {
                    val newWorldScreen = game.worldScreen
                    newWorldScreen.waitingForAutosave = true
                    newWorldScreen.shouldUpdate = true
                    GameSaver.autoSave(gameInfoClone) {
                        // only enable the user to next turn once we've saved the current one
                        newWorldScreen.waitingForAutosave = false
                        newWorldScreen.shouldUpdate = true
                    }
                }
            }
        }
    }

    private class NextTurnAction(val text: String, val color: Color, val action: () -> Unit)

    private fun updateNextTurnButton(isSomethingOpen: Boolean) {
        val action: NextTurnAction = getNextTurnAction()
        nextTurnAction = action.action

        nextTurnButton.setText(action.text.tr())
        nextTurnButton.label.color = action.color
        nextTurnButton.pack()
        nextTurnButton.isEnabled = !isSomethingOpen && isPlayersTurn && !waitingForAutosave
        nextTurnButton.setPosition(stage.width - nextTurnButton.width - 10f, topBar.y - nextTurnButton.height - 10f)
    }

    /**
     * Used by [OptionsPopup][com.unciv.ui.worldscreen.mainmenu.OptionsPopup]
     * to re-enable the next turn button within its Close button action
     */
    fun enableNextTurnButtonAfterOptions() {
        nextTurnButton.isEnabled = isPlayersTurn && !waitingForAutosave
    }

    private fun getNextTurnAction(): NextTurnAction {
        return when {
            !isPlayersTurn && gameInfo.gameParameters.isOnlineMultiplayer ->
                NextTurnAction("Waiting for [${gameInfo.currentPlayerCiv}]...", Color.GRAY) {}
            !isPlayersTurn && !gameInfo.gameParameters.isOnlineMultiplayer ->
                NextTurnAction("Waiting for other players...",Color.GRAY) {}

            viewingCiv.shouldGoToDueUnit() ->
                NextTurnAction("Next unit", Color.LIGHT_GRAY) {
                    val nextDueUnit = viewingCiv.getNextDueUnit()
                    if (nextDueUnit != null) {
                        mapHolder.setCenterPosition(
                            nextDueUnit.currentTile.position,
                            immediately = false,
                            selectUnit = false
                        )
                        bottomUnitTable.selectUnit(nextDueUnit)
                        shouldUpdate = true
                    }
                }

            viewingCiv.cities.any { it.cityConstructions.currentConstructionFromQueue == "" } ->
                NextTurnAction("Pick construction", Color.CORAL) {
                    val cityWithNoProductionSet = viewingCiv.cities
                        .firstOrNull { it.cityConstructions.currentConstructionFromQueue == "" }
                    if (cityWithNoProductionSet != null) game.setScreen(
                        CityScreen(cityWithNoProductionSet)
                    )
                }

            viewingCiv.shouldOpenTechPicker() ->
                NextTurnAction("Pick a tech", Color.SKY) {
                    game.setScreen(
                        TechPickerScreen(viewingCiv, null, viewingCiv.tech.freeTechs != 0)
                    )
                }

            viewingCiv.policies.shouldOpenPolicyPicker || (viewingCiv.policies.freePolicies > 0 && viewingCiv.policies.canAdoptPolicy()) ->
                NextTurnAction("Pick a policy", Color.VIOLET) {
                    game.setScreen(PolicyPickerScreen(this))
                    viewingCiv.policies.shouldOpenPolicyPicker = false
                }

            viewingCiv.religionManager.canFoundPantheon() ->
                NextTurnAction("Found Pantheon", Color.WHITE) {
                    game.setScreen(PantheonPickerScreen(viewingCiv, gameInfo))
                }

            viewingCiv.religionManager.religionState == ReligionState.FoundingReligion ->
                NextTurnAction("Found Religion", Color.WHITE) {
                    game.setScreen(
                        ReligiousBeliefsPickerScreen(
                            viewingCiv,
                            gameInfo,
                            viewingCiv.religionManager.getBeliefsToChooseAtFounding(),
                            pickIconAndName = true
                        )
                    )
                }

            viewingCiv.religionManager.religionState == ReligionState.EnhancingReligion ->
                NextTurnAction("Enhance Religion", Color.ORANGE) {
                    game.setScreen(
                        ReligiousBeliefsPickerScreen(
                            viewingCiv,
                            gameInfo,
                            viewingCiv.religionManager.getBeliefsToChooseAtEnhancing(),
                            pickIconAndName = false
                        )
                    )
                }

            viewingCiv.mayVoteForDiplomaticVictory() ->
                NextTurnAction("Vote for World Leader", Color.RED) {
                    game.setScreen(DiplomaticVotePickerScreen(viewingCiv))
                }

            !viewingCiv.hasMovedAutomatedUnits && viewingCiv.getCivUnits()
                .any { it.isMoving() || it.isAutomated() || it.isExploring() } ->
                NextTurnAction("Move automated units", Color.LIGHT_GRAY) {
                    viewingCiv.hasMovedAutomatedUnits = true
                    isPlayersTurn = false // Disable state changes
                    nextTurnButton.disable()
                    crashHandlingThread(name="Move automated units") {
                        for (unit in viewingCiv.getCivUnits())
                            unit.doAction()
                        postCrashHandlingRunnable {
                            shouldUpdate = true
                            isPlayersTurn = true //Re-enable state changes
                            nextTurnButton.enable()
                        }
                    }
                }

            else ->
                NextTurnAction("${Fonts.turn}{Next turn}", Color.WHITE) {
                    game.settings.addCompletedTutorialTask("Pass a turn")
                    nextTurn()
                }
        }
    }

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height)
            createNewWorldScreen(gameInfo) // start over
    }


    override fun render(delta: Float) {
        //  This is so that updates happen in the MAIN THREAD, where there is a GL Context,
        //    otherwise images will not load properly!
        if (shouldUpdate) {
            shouldUpdate = false

            update()
            showTutorialsOnNextTurn()
        }
//        topBar.selectedCivLabel.setText(Gdx.graphics.framesPerSecond) // for framerate testing

        minimapWrapper.minimap.updateScrollPosition()

        super.render(delta)
    }

    private fun showTutorialsOnNextTurn() {
        if (!game.settings.showTutorials) return
        displayTutorial(Tutorial.SlowStart)
        displayTutorial(Tutorial.CityExpansion) { viewingCiv.cities.any { it.expansion.tilesClaimed() > 0 } }
        displayTutorial(Tutorial.BarbarianEncountered) { viewingCiv.viewableTiles.any { it.getUnits().any { unit -> unit.civInfo.isBarbarian() } } }
        displayTutorial(Tutorial.RoadsAndRailroads) { viewingCiv.cities.size > 2 }
        displayTutorial(Tutorial.Happiness) { viewingCiv.getHappiness() < 5 }
        displayTutorial(Tutorial.Unhappiness) { viewingCiv.getHappiness() < 0 }
        displayTutorial(Tutorial.GoldenAge) { viewingCiv.goldenAges.isGoldenAge() }
        displayTutorial(Tutorial.IdleUnits) { gameInfo.turns >= 50 && game.settings.checkForDueUnits }
        displayTutorial(Tutorial.ContactMe) { gameInfo.turns >= 100 }
        val resources = viewingCiv.detailedCivResources.asSequence().filter { it.origin == "All" }  // Avoid full list copy
        displayTutorial(Tutorial.LuxuryResource) { resources.any { it.resource.resourceType == ResourceType.Luxury } }
        displayTutorial(Tutorial.StrategicResource) { resources.any { it.resource.resourceType == ResourceType.Strategic } }
        displayTutorial(Tutorial.EnemyCity) {
            viewingCiv.getKnownCivs().asSequence().filter { viewingCiv.isAtWarWith(it) }
                    .flatMap { it.cities.asSequence() }.any { viewingCiv.exploredTiles.contains(it.location) }
        }
        displayTutorial(Tutorial.ApolloProgram) { viewingCiv.hasUnique("Enables construction of Spaceship parts") }
        displayTutorial(Tutorial.SiegeUnits) { viewingCiv.getCivUnits().any { it.baseUnit.isProbablySiegeUnit() } }
        displayTutorial(Tutorial.Embarking) { viewingCiv.hasUnique(UniqueType.LandUnitEmbarkation) }
        displayTutorial(Tutorial.NaturalWonders) { viewingCiv.naturalWonders.size > 0 }
        displayTutorial(Tutorial.WeLoveTheKingDay) { viewingCiv.cities.any { it.demandedResource != "" } }
    }

    private fun backButtonAndESCHandler() {

        // Deselect Unit
        if (bottomUnitTable.selectedUnit != null) {
            bottomUnitTable.selectUnit()
            bottomUnitTable.isVisible = false
            shouldUpdate = true
            return
        }

        // Deselect city
        if (bottomUnitTable.selectedCity != null) {
            bottomUnitTable.selectedCity = null
            bottomUnitTable.isVisible = false
            shouldUpdate = true
            return
        }

        ExitGamePopup(this, true)

    }
}
