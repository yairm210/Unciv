package com.unciv.ui.newgamescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.utils.Array
import com.unciv.UncivGame
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.GameStarter
import com.unciv.logic.civilization.PlayerType
import com.unciv.models.ruleset.RulesetCache
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.disable
import com.unciv.ui.utils.enable
import com.unciv.ui.utils.onClick
import com.unciv.ui.worldscreen.mainmenu.OnlineMultiplayer
import com.unciv.ui.utils.Popup
import java.util.*
import kotlin.concurrent.thread

class NewGameScreen: PickerScreen(){

    val newGameParameters= UncivGame.Current.gameInfo.gameParameters
    val mapParameters = UncivGame.Current.gameInfo.tileMap.mapParameters
    val ruleset = RulesetCache.getComplexRuleset(newGameParameters.mods)

    init {
        setDefaultCloseAction()

        val playerPickerTable = PlayerPickerTable(this, newGameParameters)
        val newGameScreenOptionsTable = NewGameScreenOptionsTable(this) { playerPickerTable.update() }
        topTable.add(ScrollPane(newGameScreenOptionsTable).apply{setOverscroll(false,false)}).height(topTable.parent.height)
        topTable.add(playerPickerTable).pad(10f)
        topTable.pack()
        topTable.setFillParent(true)

        rightSideButton.enable()
        rightSideButton.setText("Start game!".tr())
        rightSideButton.onClick {
            if (newGameParameters.players.none { it.playerType == PlayerType.Human }) {
                val noHumanPlayersPopup = Popup(this)
                noHumanPlayersPopup.addGoodSizedLabel("No human players selected!".tr()).row()
                noHumanPlayersPopup.addCloseButton()
                noHumanPlayersPopup.open()
                return@onClick
            }

            if (newGameParameters.isOnlineMultiplayer) {
                for (player in newGameParameters.players.filter { it.playerType == PlayerType.Human }) {
                    try {
                        UUID.fromString(player.playerId)
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
            rightSideButton.disable()
            rightSideButton.setText("Working...".tr())

            thread(name="NewGame") {
                // Creating a new game can take a while and we don't want ANRs
                try {
                    newGame = GameStarter().startNewGame(newGameParameters,mapParameters)
                    if (newGameParameters.isOnlineMultiplayer) {
                        newGame!!.isUpToDate=true // So we don't try to download it from dropbox the second after we upload it - the file is not yet ready for loading!
                        try {
                            OnlineMultiplayer().tryUploadGame(newGame!!)
                            GameSaver().autoSave(newGame!!){}
                        } catch (ex: Exception) {
                            val cantUploadNewGamePopup = Popup(this)
                            cantUploadNewGamePopup.addGoodSizedLabel("Could not upload game!")
                            cantUploadNewGamePopup.addCloseButton()
                            cantUploadNewGamePopup.open()
                            newGame = null
                        }
                    }
                } catch (exception: Exception) {
                    val cantMakeThatMapPopup = Popup(this)
                    cantMakeThatMapPopup.addGoodSizedLabel("It looks like we can't make a map with the parameters you requested!".tr()).row()
                    cantMakeThatMapPopup.addGoodSizedLabel("Maybe you put too many players into too small a map?".tr()).row()
                    cantMakeThatMapPopup.addCloseButton()
                    cantMakeThatMapPopup.open()
                    Gdx.input.inputProcessor = stage
                    rightSideButton.enable()
                    rightSideButton.setText("Start game!".tr())
                }
                Gdx.graphics.requestRendering()
            }
        }
    }

    fun setNewGameButtonEnabled(bool:Boolean){
        if(bool) rightSideButton.enable()
        else rightSideButton.disable()
    }


    var newGame:GameInfo?=null

    override fun render(delta: Float) {
        if (newGame != null){
            game.loadGame(newGame!!)
        }
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
        val defaultItem = array.firstOrNull { it.value == default }
        selected = if (defaultItem != null) defaultItem else array.first()
    }
}

