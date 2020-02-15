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
    private val copyGameIdText = "Copy Game ID".tr()
    private val copyUserIdText = "Copy User ID".tr()
    private val refreshText = "Refresh List".tr()

    private val editButton = TextButton(editButtonText, skin).apply { disable() }
    private val addGameButton = TextButton(addGameText, skin)
    private val copyGameIdButton = TextButton(copyGameIdText, skin).apply { disable() }
    private val copyUserIdButton = TextButton(copyUserIdText, skin)
    private val refreshButton = TextButton(refreshText, skin)

    init {
        setDefaultCloseAction()

        //Help Button Setup
        val tab = Table()
        val helpButton = TextButton("?", skin)
        helpButton.onClick {
            val helpPopup = Popup(this)
            helpPopup.addGoodSizedLabel("To create a multiplayer game, check the 'multiplayer' toggle in the New Game screen, and for each human player insert that player's user ID.".tr()).row()
            helpPopup.addGoodSizedLabel("You can assign your own user ID there easily, and other players can copy their user IDs here and send them to you for you to include them in the game.".tr()).row()
            helpPopup.addGoodSizedLabel("").row()

            helpPopup.addGoodSizedLabel("Once you've created your game, the Game ID gets automatically copied to your clipboard so you can send it to the other players.".tr()).row()
            helpPopup.addGoodSizedLabel("Players can enter your game by copying the game ID to the clipboard, and clicking on the 'Add Multiplayer Game' button".tr()).row()
            helpPopup.addGoodSizedLabel("").row()

            helpPopup.addGoodSizedLabel("The symbol of your nation will appear next to the game when it's your turn".tr()).row()

            helpPopup.addCloseButton()
            helpPopup.open()
        }
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
                    ResponsePopup("UserID copied to clipboard".tr(), this)
                }
        ).pad(10f).padBottom(30f).row()

        rightSideTable.add(
                copyGameIdButton.onClick {
                    Gdx.app.clipboard.contents = selectedGame.gameId
                    ResponsePopup("GameID copied to clipboard".tr(), this)
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
        ).pad(10f).padBottom(30f).row()

        rightSideTable.add(
                refreshButton.onClick {
                    redownloadAllGames()
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

    //Adds a new Multiplayer game to the List
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
                    GameSaver().saveGame(game, game.gameId, true)
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
        try {
            UncivGame.Current.loadGame(selectedGame)
        } catch (ex: Exception) {
            Popup(this).apply {
                addGoodSizedLabel("Could not download game!".tr())
                row()
                addCloseButton()
                open()
            }
            return
        }
    }

    //reloads all gameFiles to refresh UI
    fun reloadGameListUI(){
        try {
            val leftSubTable = Table()
            for (gameSaveName in GameSaver().getSaves(true)) {
                try {
                    val gameTable = Table()
                    val game = GameSaver().loadGameByName(gameSaveName, true)
                    if (isUsersTurn(game)) {
                        gameTable.add(ImageGetter.getNationIndicator(game.currentPlayerCiv.nation, 50f))
                    }else{
                        gameTable.add()
                    }

                    //GameSaver().getSave() has to be outside of apply to be catched
                    val lastModifiedMillis = GameSaver().getSave(gameSaveName, true).lastModified()
                    gameTable.add(TextButton(gameSaveName, skin).apply {
                        onClick {
                            selectedGame = game
                            selectedGameName = gameSaveName
                            copyGameIdButton.enable()
                            editButton.enable()
                            rightSideButton.enable()

                            //get Minutes since last modified
                            val lastSavedMinutesAgo = (System.currentTimeMillis() - lastModifiedMillis) / 60000
                            var descriptionText = "Last refresh: [$lastSavedMinutesAgo] minutes ago".tr() + "\r\n"
                            descriptionText += "Current Turn:".tr() + " ${selectedGame.currentPlayer}\r\n"
                            descriptionLabel.setText(descriptionText)
                        }
                    }).pad(5f).row()

                    leftSubTable.add(gameTable).row()
                }catch (ex: Exception) {
                    //skipping one save is not fatal
                    ResponsePopup("Could not refresh!".tr(), this)
                    continue
                }
            }
            leftSideTable.clear()
            leftSideTable.add(leftSubTable)
        }catch (ex: Exception) {
            Popup(this).apply {
                addGoodSizedLabel("Could not refresh!".tr())
                row()
                addCloseButton()
                open()
            }
            return
        }
    }

    //redownload all games to update the list
    //can maybe replaced when notification support gets introduced
    fun redownloadAllGames(){
        addGameButton.disable()
        refreshButton.setText("Working...".tr())
        refreshButton.disable()

        //One thread for all downloads
        thread (name = "multiplayerGameDownload") {
            try {
                for (gameSaveName in GameSaver().getSaves(true)) {
                    try {
                        var game = GameSaver().loadGameByName(gameSaveName, true)
                        game = OnlineMultiplayer().tryDownloadGame(game.gameId.trim())
                        GameSaver().saveGame(game, gameSaveName, true)
                    } catch (ex: Exception) {
                        //skipping one is not fatal
                        //Trying to use as many prev. used strings as possible
                        ResponsePopup("Could not download game!".tr() + " $gameSaveName", this)
                        continue
                    }
                }
            } catch (ex: Exception) {
                Popup(this).apply {
                    addGoodSizedLabel("Could not download game!".tr())
                    row()
                    addCloseButton()
                    open()
                }
            }

            //Reset UI
            addGameButton.enable()
            refreshButton.setText(refreshText)
            refreshButton.enable()
            unselectGame()
            reloadGameListUI()
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

    //check if its the users turn
    private fun isUsersTurn(game: GameInfo) : Boolean{
        if (game.currentPlayerCiv.playerId == UncivGame.Current.settings.userId)
            return true
        return false
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
                    askPopup.addGoodSizedLabel("Are you sure you want to delete this map?".tr()).row()
                    askPopup.addButton("Yes".tr()){
                        try {
                            GameSaver().deleteSave(gameName, true)
                            UncivGame.Current.setScreen(backScreen)
                            backScreen.reloadGameListUI()
                        }catch (ex: Exception) {
                            askPopup.close()
                            ResponsePopup("Could not delete game!".tr(), this)
                        }
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
            try {
                backScreen.addMultiplayerGame(game.gameId, textField.text)
                GameSaver().deleteSave(gameName, true)
                UncivGame.Current.setScreen(backScreen)
                backScreen.reloadGameListUI()
            }catch (ex: Exception) {
                Popup(this).apply {
                    addGoodSizedLabel("Could not save game!".tr())
                    row()
                    addCloseButton()
                    open()
                }
            }
        }
    }
}
