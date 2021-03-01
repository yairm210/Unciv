package com.unciv.ui.newgamescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.Array
import com.unciv.UncivGame
import com.unciv.logic.*
import com.unciv.logic.civilization.PlayerType
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapType
import com.unciv.models.metadata.GameParameters
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.mainmenu.OnlineMultiplayer
import java.util.*
import kotlin.collections.HashSet
import kotlin.concurrent.thread
import com.unciv.ui.utils.AutoScrollPane as ScrollPane


class GameSetupInfo(var gameId:String, var gameParameters: GameParameters, var mapParameters: MapParameters) {
    var mapFile: FileHandle? = null
    constructor() : this("", GameParameters(), MapParameters())
    constructor(gameInfo: GameInfo) : this("", gameInfo.gameParameters.clone(), gameInfo.tileMap.mapParameters)
    constructor(gameParameters: GameParameters, mapParameters: MapParameters) : this("", gameParameters, mapParameters)

    fun clone(): GameSetupInfo {
        val toReturn = GameSetupInfo()
        toReturn.gameId = this.gameId
        toReturn.gameParameters = this.gameParameters
        toReturn.mapParameters = this.mapParameters
        return toReturn
    }
}

class NewGameScreen(previousScreen:CameraStageBaseScreen, _gameSetupInfo: GameSetupInfo?=null): IPreviousScreen, PickerScreen() {
    override val gameSetupInfo = _gameSetupInfo ?: GameSetupInfo()
    override var ruleset = RulesetCache.getComplexRuleset(gameSetupInfo.gameParameters.mods) // needs to be set because the gameoptionstable etc. depend on this
    var newGameOptionsTable = GameOptionsTable(this) { desiredCiv: String -> playerPickerTable.update(desiredCiv) }

    // Has to be defined before the mapOptionsTable, since the mapOptionsTable refers to it on init
    var playerPickerTable = PlayerPickerTable(this, gameSetupInfo.gameParameters)
    var mapOptionsTable = MapOptionsTable(this)


    init {
        setDefaultCloseAction(previousScreen)
        scrollPane.setScrollingDisabled(true, true)

        topTable.add(ScrollPane(newGameOptionsTable, skin).apply { setOverscroll(false, false) })
                .maxHeight(topTable.parent.height).width(stage.width / 3).padTop(20f).top()
        topTable.addSeparatorVertical()
        topTable.add(ScrollPane(mapOptionsTable, skin).apply { setOverscroll(false, false) })
                .maxHeight(topTable.parent.height).width(stage.width / 3).padTop(20f).top()
        topTable.addSeparatorVertical()
        topTable.add(ScrollPane(playerPickerTable, skin)
                .apply { setOverscroll(false, false) }
                .apply { setScrollingDisabled(true, false) })
                .maxHeight(topTable.parent.height).width(stage.width / 3).padTop(20f).top()

        topTable.pack()
        topTable.setFillParent(true)

        updateRuleset()

        rightSideButton.enable()
        rightSideButton.setText("Start game!".tr())
        rightSideButton.onClick {
            if (gameSetupInfo.gameParameters.players.none { it.playerType == PlayerType.Human }) {
                val noHumanPlayersPopup = Popup(this)
                noHumanPlayersPopup.addGoodSizedLabel("No human players selected!".tr()).row()
                noHumanPlayersPopup.addCloseButton()
                noHumanPlayersPopup.open()
                return@onClick
            }

            if (gameSetupInfo.gameParameters.isOnlineMultiplayer) {
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

            Gdx.input.inputProcessor = null // remove input processing - nothing will be clicked!


            if (mapOptionsTable.mapTypeSelectBox.selected.value == MapType.custom){
                val map = MapSaver.loadMap(gameSetupInfo.mapFile!!)
                val rulesetIncompatabilities = HashSet<String>()
                for(tile in map.values) {
                    val rulesetIncompat = tile.getRulesetIncompatability(ruleset)
                    if (rulesetIncompat != "") rulesetIncompatabilities.add(rulesetIncompat)
                }

                if (rulesetIncompatabilities.isNotEmpty()) {
                    val incompatibleMap = Popup(this)
                    incompatibleMap.addGoodSizedLabel("Map is incompatible with the chosen ruleset!".tr()).row()
                    for(incompat in rulesetIncompatabilities)
                        incompatibleMap.addGoodSizedLabel(incompat).row()
                    incompatibleMap.addCloseButton()
                    incompatibleMap.open()
                    game.setScreen(this) // to get the input back
                    return@onClick
                }
            }

            rightSideButton.disable()
            rightSideButton.setText("Working...".tr())

            thread(name = "NewGame") {
                // Creating a new game can take a while and we don't want ANRs
                newGameThread()
            }
        }
    }

    private fun newGameThread() {
        try {
            newGame = GameStarter.startNewGame(gameSetupInfo)
        } catch (exception: Exception) {
            Gdx.app.postRunnable {
                val cantMakeThatMapPopup = Popup(this)
                cantMakeThatMapPopup.addGoodSizedLabel("It looks like we can't make a map with the parameters you requested!".tr()).row()
                cantMakeThatMapPopup.addGoodSizedLabel("Maybe you put too many players into too small a map?".tr()).row()
                cantMakeThatMapPopup.addCloseButton()
                cantMakeThatMapPopup.open()
                Gdx.input.inputProcessor = stage
                rightSideButton.enable()
                rightSideButton.setText("Start game!".tr())
            }
        }

        if (newGame != null && gameSetupInfo.gameParameters.isOnlineMultiplayer) {
            newGame!!.isUpToDate = true // So we don't try to download it from dropbox the second after we upload it - the file is not yet ready for loading!
            try {
                OnlineMultiplayer().tryUploadGame(newGame!!)

                // Save gameId to clipboard because you have to do it anyway.
                Gdx.app.clipboard.contents = newGame!!.gameId
                // Popup to notify the User that the gameID got copied to the clipboard
                Gdx.app.postRunnable { ToastPopup("gameID copied to clipboard".tr(), UncivGame.Current.worldScreen, 2500) }

                GameSaver.autoSave(newGame!!) {}

                // Saved as Multiplayer game to show up in the session browser
                GameSaver.saveGame(newGame!!, newGame!!.gameId, true)
            } catch (ex: Exception) {
                Gdx.app.postRunnable {
                    val cantUploadNewGamePopup = Popup(this)
                    cantUploadNewGamePopup.addGoodSizedLabel("Could not upload game!")
                    cantUploadNewGamePopup.addCloseButton()
                    cantUploadNewGamePopup.open()
                }
                newGame = null
            }
        }

        Gdx.graphics.requestRendering()
    }

    fun updateRuleset() {
        ruleset.clear()
        ruleset.add(RulesetCache.getComplexRuleset(gameSetupInfo.gameParameters.mods))
        ImageGetter.setNewRuleset(ruleset)
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

    var newGame: GameInfo? = null

    override fun render(delta: Float) {
        if (newGame != null)
            game.loadGame(newGame!!)
        super.render(delta)
    }
}

class TranslatedSelectBox(values : Collection<String>, default:String, skin: Skin) : SelectBox<TranslatedSelectBox.TranslatedString>(skin) {
    class TranslatedString(val value: String) {
        val translation = value.tr()
        override fun toString() = translation
    }

    init {
        val array = Array<TranslatedString>()
        values.forEach { array.add(TranslatedString(it)) }
        items = array
        selected = array.firstOrNull { it.value == default } ?: array.first()
    }
}