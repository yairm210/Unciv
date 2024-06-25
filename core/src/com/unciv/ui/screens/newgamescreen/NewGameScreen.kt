package com.unciv.ui.screens.newgamescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameStarter
import com.unciv.logic.IdChecker
import com.unciv.logic.UncivShowableException
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.files.MapSaver
import com.unciv.logic.map.MapGeneratedMainType
import com.unciv.logic.multiplayer.OnlineMultiplayer
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.models.metadata.BaseRuleset
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.addSeparatorVertical
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.pad
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.ExpanderTab
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import com.unciv.utils.launchOnGLThread
import kotlinx.coroutines.coroutineScope
import java.net.URL
import java.util.UUID
import kotlin.math.floor
import com.unciv.ui.components.widgets.AutoScrollPane as ScrollPane

class NewGameScreen(
    defaultGameSetupInfo: GameSetupInfo? = null
): IPreviousScreen, PickerScreen(), RecreateOnResize {

    override val gameSetupInfo = defaultGameSetupInfo ?: GameSetupInfo.fromSettings()
    override val ruleset = Ruleset()  // updateRuleset will clear and add
    private val newGameOptionsTable: GameOptionsTable
    internal val playerPickerTable: PlayerPickerTable
    private val mapOptionsTable: MapOptionsTable

    init {
        val isPortrait = isNarrowerThan4to3()

        tryUpdateRuleset(updateUI = false)  // must come before playerPickerTable so mod nations from fromSettings

        // remove the victory types which are not in the rule set (e.g. were in the recently disabled mod)
        gameSetupInfo.gameParameters.victoryTypes.removeAll { it !in ruleset.victories.keys }

        if (gameSetupInfo.gameParameters.victoryTypes.isEmpty())
            gameSetupInfo.gameParameters.victoryTypes.addAll(ruleset.victories.keys)

        rightSideButton.enable()  // now because PlayerPickerTable init might disable it again
        playerPickerTable = PlayerPickerTable(
            this, gameSetupInfo.gameParameters,
            if (isPortrait) stage.width - 20f else 0f
        )
        newGameOptionsTable = GameOptionsTable(
            this, isPortrait,
            updatePlayerPickerTable = { desiredCiv -> playerPickerTable.update(desiredCiv) },
            updatePlayerPickerRandomLabel = { playerPickerTable.updateRandomNumberLabel() }
        )
        mapOptionsTable = MapOptionsTable(this)
        closeButton.onActivation {
            mapOptionsTable.cancelBackgroundJobs()
            game.popScreen()
        }
        closeButton.keyShortcuts.add(KeyCharAndCode.BACK)

        if (isPortrait) initPortrait()
        else initLandscape()

        bottomTable.background = skinStrings.getUiBackground("NewGameScreen/BottomTable", tintColor = skinStrings.skinConfig.clearColor)
        topTable.background = skinStrings.getUiBackground("NewGameScreen/TopTable", tintColor = skinStrings.skinConfig.clearColor)

        if (UncivGame.Current.settings.lastGameSetup != null) {
            rightSideGroup.addActorAt(0, VerticalGroup().padBottom(5f))
            val resetToDefaultsButton = "Reset to defaults".toTextButton()
            rightSideGroup.addActorAt(0, resetToDefaultsButton)
            resetToDefaultsButton.onClick {
                ConfirmPopup(
                    this,
                    "Are you sure you want to reset all game options to defaults?",
                    "Reset to defaults",
                ) {
                    game.replaceCurrentScreen(NewGameScreen(GameSetupInfo()))
                }.open(true)
            }
        }

        rightSideButton.setText("Start game!".tr())
        rightSideButton.onClick(this::onStartGameClicked)
    }

    private fun onStartGameClicked() {
        mapOptionsTable.cancelBackgroundJobs()
        if (gameSetupInfo.gameParameters.isOnlineMultiplayer) {
            if (!checkConnectionToMultiplayerServer()) {
                val noInternetConnectionPopup = Popup(this)
                val label = if (OnlineMultiplayer.usesCustomServer()) "Couldn't connect to Multiplayer Server!" else "Couldn't connect to Dropbox!"
                noInternetConnectionPopup.addGoodSizedLabel(label.tr()).row()
                noInternetConnectionPopup.addCloseButton()
                noInternetConnectionPopup.open()
                return
            }

            for (player in gameSetupInfo.gameParameters.players.filter { it.playerType == PlayerType.Human }) {
                try {
                    UUID.fromString(IdChecker.checkAndReturnPlayerUuid(player.playerId))
                } catch (_: Exception) {
                    val invalidPlayerIdPopup = Popup(this)
                    invalidPlayerIdPopup.addGoodSizedLabel("Invalid player ID!".tr()).row()
                    invalidPlayerIdPopup.addCloseButton()
                    invalidPlayerIdPopup.open()
                    return
                }
            }

            if (!gameSetupInfo.gameParameters.anyoneCanSpectate) {
                if (gameSetupInfo.gameParameters.players.none { it.playerId == UncivGame.Current.settings.multiplayer.userId }) {
                    val notAllowedToSpectate = Popup(this)
                    notAllowedToSpectate.addGoodSizedLabel("You are not allowed to spectate!".tr()).row()
                    notAllowedToSpectate.addCloseButton()
                    notAllowedToSpectate.open()
                    return
                }
            }
        }

        if (gameSetupInfo.gameParameters.players.none {
                    it.playerType == PlayerType.Human &&
                            // do not allow multiplayer with only spectator(s) and AI(s) - non-MP that works
                            !(it.chosenCiv == Constants.spectator && gameSetupInfo.gameParameters.isOnlineMultiplayer)
                }) {
            val noHumanPlayersPopup = Popup(this)
            noHumanPlayersPopup.addGoodSizedLabel("No human players selected!".tr()).row()
            noHumanPlayersPopup.addCloseButton()
            noHumanPlayersPopup.open()
            return
        }

        if (gameSetupInfo.gameParameters.victoryTypes.isEmpty()) {
            val noVictoryTypesPopup = Popup(this)
            noVictoryTypesPopup.addGoodSizedLabel("No victory conditions were selected!".tr()).row()
            noVictoryTypesPopup.addCloseButton()
            noVictoryTypesPopup.open()
            return
        }

        val modCheckResult = newGameOptionsTable.modCheckboxes.savedModcheckResult
        newGameOptionsTable.modCheckboxes.savedModcheckResult = null
        if (modCheckResult != null) {
            AcceptModErrorsPopup(
                this, modCheckResult,
                restoreDefault = { newGameOptionsTable.resetRuleset() },
                action = {
                    gameSetupInfo.gameParameters.acceptedModCheckErrors = modCheckResult
                    onStartGameClicked()
                }
            )
            return
        }

        Gdx.input.inputProcessor = null // remove input processing - nothing will be clicked!

        if (mapOptionsTable.mapTypeSelectBox.selected.value == MapGeneratedMainType.custom) {
            val map = try {
                MapSaver.loadMap(gameSetupInfo.mapFile!!)
            } catch (ex: Throwable) {
                Log.error("Could not load map", ex)
                Gdx.input.inputProcessor = stage
                ToastPopup("Could not load map!", this)
                return
            }

            val rulesetIncompatibilities = map.getRulesetIncompatibility(ruleset)
            if (rulesetIncompatibilities.isNotEmpty()) {
                val incompatibleMap = Popup(this)
                incompatibleMap.addGoodSizedLabel("Map is incompatible with the chosen ruleset!".tr()).row()
                for(incompatibility in rulesetIncompatibilities)
                    incompatibleMap.addGoodSizedLabel(incompatibility).row()
                incompatibleMap.addCloseButton()
                incompatibleMap.open()
                Gdx.input.inputProcessor = stage
                return
            }
        } else {
            // Generated map - check for sensible dimensions and if exceeded correct them and notify user
            val mapSize = gameSetupInfo.mapParameters.mapSize
            val message = mapSize.fixUndesiredSizes(gameSetupInfo.mapParameters.worldWrap)
            if (message != null) {
                ToastPopup( message, UncivGame.Current.screen!!, 4000 )
                with (mapOptionsTable.generatedMapOptionsTable) {
                    customMapSizeRadius.text = mapSize.radius.toString()
                    customMapWidth.text = mapSize.width.toString()
                    customMapHeight.text = mapSize.height.toString()
                }
                Gdx.input.inputProcessor = stage
                return
            }
        }

        rightSideButton.disable()
        rightSideButton.setText(Constants.working.tr())

        setSkin()
        // Creating a new game can take a while and we don't want ANRs
        Concurrency.runOnNonDaemonThreadPool("NewGame") {
            startNewGame()
        }
    }

    /** Subtables may need an upper limit to their width - they can ask this function. */
    // In sync with isPortrait in init, here so UI details need not know about 3-column vs 1-column layout
    internal fun getColumnWidth() = floor(stage.width / (if (isNarrowerThan4to3()) 1 else 3))

    private fun initLandscape() {
        scrollPane.setScrollingDisabled(true,true)

        topTable.add("Game Options".toLabel(fontSize = Constants.headingFontSize)).pad(20f, 0f)
        topTable.addSeparatorVertical(Color.BLACK, 1f)
        topTable.add("Map Options".toLabel(fontSize = Constants.headingFontSize)).pad(20f,0f)
        topTable.addSeparatorVertical(Color.BLACK, 1f)
        topTable.add("Civilizations".toLabel(fontSize = Constants.headingFontSize)).pad(20f,0f)
        topTable.addSeparator(Color.CLEAR, height = 1f)

        topTable.add(ScrollPane(newGameOptionsTable)
                .apply { setOverscroll(false, false) })
                .width(stage.width / 3).top()
        topTable.addSeparatorVertical(Color.CLEAR, 1f)
        topTable.add(ScrollPane(mapOptionsTable)
                .apply { setOverscroll(false, false) })
                .width(stage.width / 3).top()
        topTable.addSeparatorVertical(Color.CLEAR, 1f)
        topTable.add(playerPickerTable)  // No ScrollPane, PlayerPickerTable has its own
                .width(stage.width / 3).top()
    }

    private fun initPortrait() {
        scrollPane.setScrollingDisabled(false,false)

        topTable.add(ExpanderTab("Game Options") {
            it.add(newGameOptionsTable).row()
        }).expandX().fillX().row()
        topTable.addSeparator(Color.DARK_GRAY, height = 1f)

        topTable.add(newGameOptionsTable.modCheckboxes).expandX().fillX().row()
        topTable.addSeparator(Color.DARK_GRAY, height = 1f)

        topTable.add(ExpanderTab("Map Options") {
            it.add(mapOptionsTable).row()
        }).expandX().fillX().row()
        topTable.addSeparator(Color.DARK_GRAY, height = 1f)

        (playerPickerTable.playerListTable.parent as ScrollPane).setScrollingDisabled(true,true)
        topTable.add(ExpanderTab("Civilizations") {
            it.add(playerPickerTable).row()
        }).expandX().fillX().row()
    }

    private fun checkConnectionToMultiplayerServer(): Boolean {
        return try {
            val multiplayerServer = UncivGame.Current.settings.multiplayer.server
            val u =  URL(if (OnlineMultiplayer.usesDropbox()) "https://content.dropboxapi.com" else multiplayerServer)
            val con = u.openConnection()
            con.connectTimeout = 3000
            con.connect()

            true
        } catch(_: Throwable) {
            false
        }
    }

    private suspend fun startNewGame() = coroutineScope {
        val popup = Popup(this@NewGameScreen)
        launchOnGLThread {
            popup.addGoodSizedLabel(Constants.working).row()
            popup.open()
        }

        val newGame:GameInfo
        try {
            newGame = GameStarter.startNewGame(gameSetupInfo)
        } catch (exception: Exception) {
            exception.printStackTrace()
            launchOnGLThread {
                popup.apply {
                    reuseWith("It looks like we can't make a map with the parameters you requested!")
                    row()
                    addGoodSizedLabel("Maybe you put too many players into too small a map?").row()
                    addButton("Copy to clipboard"){
                        Gdx.app.clipboard.contents = exception.stackTraceToString()
                    }
                    addCloseButton()
                }
                Gdx.input.inputProcessor = stage
                rightSideButton.enable()
                rightSideButton.setText("Start game!".tr())
            }
            return@coroutineScope
        }

        if (gameSetupInfo.gameParameters.isOnlineMultiplayer) {
            newGame.isUpToDate = true // So we don't try to download it from dropbox the second after we upload it - the file is not yet ready for loading!
            try {
                game.onlineMultiplayer.createGame(newGame)
                game.files.autosaves.requestAutoSave(newGame)
            } catch (ex: FileStorageRateLimitReached) {
                launchOnGLThread {
                    popup.reuseWith("Server limit reached! Please wait for [${ex.limitRemainingSeconds}] seconds", true)
                    rightSideButton.enable()
                    rightSideButton.setText("Start game!".tr())
                }
                Gdx.input.inputProcessor = stage
                return@coroutineScope
            } catch (ex: Exception) {
                Log.error("Error while creating game", ex)
                launchOnGLThread {
                    popup.reuseWith("Could not upload game!", true)
                    rightSideButton.enable()
                    rightSideButton.setText("Start game!".tr())
                }
                Gdx.input.inputProcessor = stage
                return@coroutineScope
            }
        }

        val worldScreen = game.loadGame(newGame)

        if (newGame.gameParameters.isOnlineMultiplayer) {
            launchOnGLThread {
                    // Save gameId to clipboard because you have to do it anyway.
                    Gdx.app.clipboard.contents = newGame.gameId
                    // Popup to notify the User that the gameID got copied to the clipboard
                    ToastPopup("Game ID copied to clipboard!".tr(), worldScreen, 2500)
            }
        }
    }

    /** Updates our local [ruleset] from [gameSetupInfo], guarding against exceptions.
     *
     *  Note: The options reset on failure is not propagated automatically to the Widgets -
     *  the caller must ensure that.
     *
     *  @return Success - failure means gameSetupInfo was reset to defaults and the Ruleset was reverted to G&K
     */
    fun tryUpdateRuleset(updateUI: Boolean): Boolean {
        var success = true
        fun handleFailure(message: String): Ruleset {
            success = false
            ToastPopup(message, this, 5000)
            gameSetupInfo.gameParameters.mods.clear()
            gameSetupInfo.gameParameters.baseRuleset = BaseRuleset.Civ_V_GnK.fullName
            return RulesetCache[BaseRuleset.Civ_V_GnK.fullName]!!
        }

        val newRuleset = try {
            // this can throw with non-default gameSetupInfo, e.g. when Mods change or we change the impact of Mod errors
            RulesetCache.getComplexRuleset(gameSetupInfo.gameParameters)
        } catch (ex: UncivShowableException) {
            handleFailure("«YELLOW»{Your previous options needed to be reset to defaults.}«»\n\n${ex.localizedMessage}")
        } catch (ex: Throwable) {
            Log.debug("updateRuleset failed", ex)
            handleFailure("«RED»{Your previous options needed to be reset to defaults.}«»")
        }

        ruleset.clear()
        ruleset.add(newRuleset)
        ImageGetter.setNewRuleset(ruleset)
        game.musicController.setModList(gameSetupInfo.gameParameters.getModsAndBaseRuleset())

        if (updateUI) newGameOptionsTable.updateRuleset(ruleset)
        return success
    }

    fun lockTables() {
        playerPickerTable.locked = true
        newGameOptionsTable.locked = true
    }

    fun unlockTables() {
        playerPickerTable.locked = false
        newGameOptionsTable.locked = false
    }

    fun updateTables() {
        playerPickerTable.gameParameters = gameSetupInfo.gameParameters
        playerPickerTable.update()
        newGameOptionsTable.changeGameParameters(gameSetupInfo.gameParameters)
        newGameOptionsTable.update()
    }

    override fun recreate(): BaseScreen = NewGameScreen(gameSetupInfo)
}
