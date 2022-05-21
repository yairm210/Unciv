package com.unciv.ui.newgamescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.badlogic.gdx.utils.Array
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.*
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapType
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.OnlineMultiplayerGameSaver
import com.unciv.models.metadata.GameSetupInfo
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.popup.YesNoPopup
import com.unciv.ui.utils.*
import java.net.URL
import java.util.*
import com.unciv.ui.utils.AutoScrollPane as ScrollPane


class NewGameScreen(
    private val previousScreen: BaseScreen,
    _gameSetupInfo: GameSetupInfo? = null
): IPreviousScreen, PickerScreen() {

    override val gameSetupInfo = _gameSetupInfo ?: GameSetupInfo.fromSettings()
    override var ruleset = RulesetCache.getComplexRuleset(gameSetupInfo.gameParameters) // needs to be set because the GameOptionsTable etc. depend on this
    private val newGameOptionsTable: GameOptionsTable
    private val playerPickerTable: PlayerPickerTable
    private val mapOptionsTable: MapOptionsTable

    init {
        updateRuleset()  // must come before playerPickerTable so mod nations from fromSettings
        // Has to be initialized before the mapOptionsTable, since the mapOptionsTable refers to it on init

        if (gameSetupInfo.gameParameters.victoryTypes.isEmpty())
            gameSetupInfo.gameParameters.victoryTypes.addAll(ruleset.victories.keys)

        playerPickerTable = PlayerPickerTable(
            this, gameSetupInfo.gameParameters,
            if (isNarrowerThan4to3()) stage.width - 20f else 0f
        )
        newGameOptionsTable = GameOptionsTable(this, isNarrowerThan4to3()) { desiredCiv: String -> playerPickerTable.update(desiredCiv) }
        mapOptionsTable = MapOptionsTable(this)
        setDefaultCloseAction(previousScreen)

        if (isNarrowerThan4to3()) initPortrait()
        else initLandscape()

        if (UncivGame.Current.settings.lastGameSetup != null) {
            rightSideGroup.addActorAt(0, VerticalGroup().padBottom(5f))
            val resetToDefaultsButton = "Reset to defaults".toTextButton()
            rightSideGroup.addActorAt(0, resetToDefaultsButton)
            resetToDefaultsButton.onClick {
                YesNoPopup("Are you sure you want to reset all game options to defaults?", {
                    game.setScreen(NewGameScreen(previousScreen, GameSetupInfo()))
                }, this).open(true)
            }
        }

        rightSideButton.enable()
        rightSideButton.setText("Start game!".tr())
        rightSideButton.onClick {
            if (gameSetupInfo.gameParameters.isOnlineMultiplayer) {
                val isDropbox = UncivGame.Current.settings.multiplayerServer == Constants.dropboxMultiplayerServer
                if (!checkConnectionToMultiplayerServer()) {
                    val noInternetConnectionPopup = Popup(this)
                    val label = if (isDropbox) "Couldn't connect to Dropbox!" else "Couldn't connect to Multiplayer Server!"
                    noInternetConnectionPopup.addGoodSizedLabel(label.tr()).row()
                    noInternetConnectionPopup.addCloseButton()
                    noInternetConnectionPopup.open()
                    return@onClick
                }

                for (player in gameSetupInfo.gameParameters.players.filter { it.playerType == PlayerType.Human }) {
                    try {
                        UUID.fromString(IdChecker.checkAndReturnPlayerUuid(player.playerId))
                    } catch (ex: Exception) {
                        val invalidPlayerIdPopup = Popup(this)
                        invalidPlayerIdPopup.addGoodSizedLabel("Invalid player ID!".tr()).row()
                        invalidPlayerIdPopup.addCloseButton()
                        invalidPlayerIdPopup.open()
                        return@onClick
                    }
                }
            }

            if (gameSetupInfo.gameParameters.players.none {
                it.playerType == PlayerType.Human &&
                    // do not allow multiplayer with only remote spectator(s) and AI(s) - non-MP that works
                    !(it.chosenCiv == Constants.spectator && gameSetupInfo.gameParameters.isOnlineMultiplayer &&
                            it.playerId != UncivGame.Current.settings.userId)
            }) {
                val noHumanPlayersPopup = Popup(this)
                noHumanPlayersPopup.addGoodSizedLabel("No human players selected!".tr()).row()
                noHumanPlayersPopup.addCloseButton()
                noHumanPlayersPopup.open()
                return@onClick
            }

            if (gameSetupInfo.gameParameters.victoryTypes.isEmpty()) {
                val noVictoryTypesPopup = Popup(this)
                noVictoryTypesPopup.addGoodSizedLabel("No victory conditions were selected!".tr()).row()
                noVictoryTypesPopup.addCloseButton()
                noVictoryTypesPopup.open()
                return@onClick
            }

            Gdx.input.inputProcessor = null // remove input processing - nothing will be clicked!

            if (mapOptionsTable.mapTypeSelectBox.selected.value == MapType.custom) {
                val map = try {
                    MapSaver.loadMap(gameSetupInfo.mapFile!!)
                } catch (ex: Throwable) {
                    game.setScreen(this)
                    ToastPopup("Could not load map!", this)
                    return@onClick
                }

                val rulesetIncompatibilities = map.getRulesetIncompatibility(ruleset)
                if (rulesetIncompatibilities.isNotEmpty()) {
                    val incompatibleMap = Popup(this)
                    incompatibleMap.addGoodSizedLabel("Map is incompatible with the chosen ruleset!".tr()).row()
                    for(incompatibility in rulesetIncompatibilities)
                        incompatibleMap.addGoodSizedLabel(incompatibility).row()
                    incompatibleMap.addCloseButton()
                    incompatibleMap.open()
                    game.setScreen(this) // to get the input back
                    return@onClick
                }
            } else {
                // Generated map - check for sensible dimensions and if exceeded correct them and notify user
                val mapSize = gameSetupInfo.mapParameters.mapSize
                val message = mapSize.fixUndesiredSizes(gameSetupInfo.mapParameters.worldWrap)
                if (message != null) {
                    postCrashHandlingRunnable {
                        ToastPopup( message, UncivGame.Current.screen as BaseScreen, 4000 )
                        with (mapOptionsTable.generatedMapOptionsTable) {
                            customMapSizeRadius.text = mapSize.radius.toString()
                            customMapWidth.text = mapSize.width.toString()
                            customMapHeight.text = mapSize.height.toString()
                        }
                    }
                    game.setScreen(this) // to get the input back
                    return@onClick
                }
            }

            rightSideButton.disable()
            rightSideButton.setText("Working...".tr())

            // Creating a new game can take a while and we don't want ANRs
            launchCrashHandling("NewGame", runAsDaemon = false) {
                startNewGame()
            }
        }
    }

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
        val isDropbox = UncivGame.Current.settings.multiplayerServer == Constants.dropboxMultiplayerServer
        return try {
            val multiplayerServer = UncivGame.Current.settings.multiplayerServer
            val u =  URL(if (isDropbox) "https://content.dropboxapi.com" else multiplayerServer)
            val con = u.openConnection()
            con.connectTimeout = 3000
            con.connect()

            true
        } catch(ex: Throwable) {
            false
        }
    }

    private suspend fun startNewGame() {
        val popup = Popup(this)
        postCrashHandlingRunnable {
            popup.addGoodSizedLabel("Working...").row()
            popup.open()
        }

        val newGame:GameInfo
        try {
            newGame = GameStarter.startNewGame(gameSetupInfo)
        } catch (exception: Exception) {
            exception.printStackTrace()
            postCrashHandlingRunnable {
                popup.apply {
                    reuseWith("It looks like we can't make a map with the parameters you requested!")
                    row()
                    addGoodSizedLabel("Maybe you put too many players into too small a map?").row()
                    addCloseButton()
                }
                Gdx.input.inputProcessor = stage
                rightSideButton.enable()
                rightSideButton.setText("Start game!".tr())
            }
            return
        }

        if (gameSetupInfo.gameParameters.isOnlineMultiplayer) {
            newGame.isUpToDate = true // So we don't try to download it from dropbox the second after we upload it - the file is not yet ready for loading!
            try {
                OnlineMultiplayerGameSaver().tryUploadGame(newGame, withPreview = true)

                GameSaver.autoSave(newGame)

                // Saved as Multiplayer game to show up in the session browser
                val newGamePreview = newGame.asPreview()
                GameSaver.saveGame(newGamePreview, newGamePreview.gameId)
            } catch (ex: FileStorageRateLimitReached) {
                postCrashHandlingRunnable {
                    popup.reuseWith("Server limit reached! Please wait for [${ex.limitRemainingSeconds}] seconds", true)
                }
                Gdx.input.inputProcessor = stage
                rightSideButton.enable()
                rightSideButton.setText("Start game!".tr())
                return
            } catch (ex: Exception) {
                postCrashHandlingRunnable {
                    popup.reuseWith("Could not upload game!", true)
                }
                Gdx.input.inputProcessor = stage
                rightSideButton.enable()
                rightSideButton.setText("Start game!".tr())
                return
            }
        }

        postCrashHandlingRunnable {
            game.loadGame(newGame)
            previousScreen.dispose()
            if (newGame.gameParameters.isOnlineMultiplayer) {
                // Save gameId to clipboard because you have to do it anyway.
                Gdx.app.clipboard.contents = newGame.gameId
                // Popup to notify the User that the gameID got copied to the clipboard
                ToastPopup("Game ID copied to clipboard!".tr(), game.worldScreen, 2500)
            }
        }
    }

    fun updateRuleset() {
        ruleset.clear()
        ruleset.add(RulesetCache.getComplexRuleset(gameSetupInfo.gameParameters))
        ImageGetter.setNewRuleset(ruleset)
        game.musicController.setModList(gameSetupInfo.gameParameters.getModsAndBaseRuleset())
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
        newGameOptionsTable.gameParameters = gameSetupInfo.gameParameters
        newGameOptionsTable.update()
    }

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            game.setScreen(NewGameScreen(previousScreen, gameSetupInfo))
        }
    }
}

class TranslatedSelectBox(values : Collection<String>, default:String, skin: Skin) : SelectBox<TranslatedSelectBox.TranslatedString>(skin) {
    class TranslatedString(val value: String) {
        val translation = value.tr()
        override fun toString() = translation
        // Equality contract needs to be implemented else TranslatedSelectBox.setSelected won't work properly
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            return value == (other as TranslatedString).value
        }
        override fun hashCode() = value.hashCode()
    }

    init {
        val array = Array<TranslatedString>()
        values.forEach { array.add(TranslatedString(it)) }
        items = array
        selected = array.firstOrNull { it.value == default } ?: array.first()
    }
    
    fun setSelected(newValue: String) {
        selected = items.firstOrNull { it == TranslatedString(newValue) } ?: return
    }
}
