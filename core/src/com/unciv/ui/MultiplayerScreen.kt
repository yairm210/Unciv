package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.logic.GameSaver
import com.unciv.logic.GameInfo
import com.unciv.UncivGame
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.mainmenu.OnlineMultiplayer
import java.util.*
import kotlin.concurrent.thread

class MultiplayerScreen() : PickerScreen() {

    private lateinit var selectedGame: GameInfo
    private lateinit var selectedGameName: String
    private val rightSideTable = Table()
    private val leftSideTable = Table()

    private val editButtonText = "Edit Game Info".tr()
    private val addGameText = "Add Multiplayer Game".tr()

    private val editButton = TextButton(editButtonText, skin).apply { disable() }
    private val addGameButton = TextButton(addGameText, skin)
    private val copyGameIdButton = TextButton("Copy Game ID".tr(), skin).apply { disable() }
    private val copyUserIdButton = TextButton("Copy User ID".tr(), skin)

    init {
        setDefaultCloseAction()

        //Help Button Setup
        val tab = Table()
        val helpButton = TextButton("?", skin)
        tab.add(helpButton)
        tab.x = (stage.width - helpButton.width)
        tab.y = (stage.height - helpButton.height)
        stage.addActor(tab)

        //TopTable Setup
        topTable.add(ScrollPane(leftSideTable).apply { setScrollingDisabled(true, false) }).height(stage.height*2/3)
        topTable.add(rightSideTable)
        scrollPane.setScrollingDisabled(false, true)

        //rightTable Setup
        rightSideTable.add(
                copyUserIdButton.onClick {
                    Gdx.app.clipboard.contents = UncivGame.Current.settings.userId
                    ResponsePopup("userID copied to clipboard".tr(), this)
                }
        ).pad(10f).row()

        rightSideTable.add(
                copyGameIdButton.onClick {
                    Gdx.app.clipboard.contents = selectedGame.gameId
                    ResponsePopup("gameID copied to clipboard".tr(), this)
                }
        ).pad(10f).row()

        rightSideTable.add(
                editButton.onClick {
                    UncivGame.Current.setScreen(EditMultiplayerGameInfoScreen(selectedGame, selectedGameName, this))
                    //game must be unselected in case the game gets deleted inside the EditScreen
                    unselectGame()
                }
        ).pad(10f).row()

        rightSideTable.add(
                addGameButton.onClick {
                    addMultiplayerGame(Gdx.app.clipboard.contents)
                }
        ).pad(10f).row()

        //leftTable Setup
        reloadGameListUI()

        //RightSideButton Setup
        rightSideButton.setText("Join Game".tr())
        rightSideButton.onClick {
            joinMultiplaerGame()
        }
    }

    fun addMultiplayerGame(gameId: String, gameName: String = ""){
        val errorPopup = Popup(this)
        try {
            //since the gameId is a String it can contain anything and has to be checked
            UUID.fromString(gameId.trim())
        } catch (ex: Exception) {
            errorPopup.addGoodSizedLabel("Invalid game ID!".tr())
            errorPopup.row()
            errorPopup.addCloseButton()
            errorPopup.open()
            return
        }
        thread(name="MultiplayerDownload") {
            addGameButton.setText("Working...".tr())
            addGameButton.disable()
            try {
                // The tryDownload can take more than 500ms. Therefore, to avoid ANRs,
                // we need to run it in a different thread.
                val game = OnlineMultiplayer().tryDownloadGame(gameId.trim())
                if (gameName == "")
                    GameSaver().saveGame(game, "Multiplayer-Game-${game.gameId}", true)
                else
                    GameSaver().saveGame(game, gameName, true)

                reloadGameListUI()

            } catch (ex: Exception) {
                errorPopup.addGoodSizedLabel("Could not download game!".tr())
                errorPopup.row()
                errorPopup.addCloseButton()
                errorPopup.open()
            }
            addGameButton.setText(addGameText)
            addGameButton.enable()
        }
    }

    //just loads the game from savefile
    //the game will be downloaded opon joining it anyway
    private fun joinMultiplaerGame(){
        val errorPopup = Popup(this)
        try {
            UncivGame.Current.loadGame(selectedGame)
        } catch (ex: Exception) {
            errorPopup.addGoodSizedLabel("Could not download game!".tr())
            errorPopup.row()
            errorPopup.addCloseButton()
            errorPopup.open()
            return
        }

    }

    fun reloadGameListUI(){
        leftSideTable.clear()
        for (gameSaveName in GameSaver().getSaves(true)) {
            leftSideTable.add(TextButton(gameSaveName, skin).apply {
                onClick {
                    selectedGame = GameSaver().loadGameByName(gameSaveName, true)
                    selectedGameName = gameSaveName
                    copyGameIdButton.enable()
                    editButton.enable()
                    rightSideButton.enable()

                    //get Minutes since last modified
                    var lastSavedMinutesAgo = (System.currentTimeMillis() - GameSaver().getSave(gameSaveName, true).lastModified()) / 60000
                    var descriptionText = "Last Game State Updated: $lastSavedMinutesAgo minutes ago \r\n"
                    descriptionText += "Current Turn: " + selectedGame.currentPlayer + "\r\n"
                    descriptionLabel.setText(descriptionText)
                }
            }).pad(5f).row()
        }
    }

    //It doesn't really unselect the game because selectedGame cant be null
    //It just disables everything a selected game has set
    private fun unselectGame(){
        editButton.disable()
        copyGameIdButton.disable()
        rightSideButton.disable()
        descriptionLabel.setText("")
    }
}

//Subscreen of MultiplayerScreen to edit and delete saves
//backScreen is used for getting back to the MultiplayerScreen so it doesn't have to be created over and over again
class EditMultiplayerGameInfoScreen(game: GameInfo, gameName: String, backScreen: MultiplayerScreen): PickerScreen(){
    init {
        var textField = TextField(gameName, skin)

        topTable.add(Label("Rename".tr(), skin)).row()
        topTable.add(textField).pad(10f).padBottom(30f).width(backScreen.stage.width/2).row()

        //TODO Change delete to "give up"
            //->turn a player into an AI so everyone can still play without the user
                //->should only be possible on the users turn because it has to be uploaded afterwards
        topTable.add(
                TextButton("Delete save".tr(), skin).onClick {
                    var askPopup = Popup(this)
                    askPopup.addGoodSizedLabel("Are you sure you want to delete this Game?").row()
                    askPopup.addButton("Yes".tr()){
                        GameSaver().deleteSave(gameName, true)
                        UncivGame.Current.setScreen(backScreen)
                        backScreen.reloadGameListUI()
                    }
                    askPopup.addButton("No".tr()){
                        askPopup.close()
                    }
                    askPopup.open()
                }.apply { color = Color.RED }
        )

        //CloseButton Setup
        closeButton.setText("Back".tr())
        closeButton.onClick {
            UncivGame.Current.setScreen(backScreen)
        }

        //RightSideButton Setup
        rightSideButton.setText("Save game".tr())
        rightSideButton.enable()
        rightSideButton.onClick {
            rightSideButton.setText("Saving...".tr())
            //using addMultiplayerGame will download the game from Dropbox so the descriptionLabel displays the right things
            backScreen.addMultiplayerGame(game.gameId, textField.text)
            GameSaver().deleteSave(gameName, true)
            UncivGame.Current.setScreen(backScreen)
            backScreen.reloadGameListUI()
        }
    }
}
