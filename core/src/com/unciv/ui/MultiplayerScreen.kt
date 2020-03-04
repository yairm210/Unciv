package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
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
        addHelpButton()

        //TopTable Setup
        //Have to put it into a separate Table to be able to add another copyGameID button
        val mainTable = Table()
        mainTable.add(ScrollPane(leftSideTable).apply { setScrollingDisabled(true, false) }).height(stage.height*2/3)
        mainTable.add(rightSideTable)
        topTable.add(mainTable).row()
        scrollPane.setScrollingDisabled(false, true)

        //leftTable Setup
        reloadGameListUI()

        //A Button to add the currently running game to the multiplayerFileManager if not yet done
        addCurrentGameButton()

        //rightTable Setup
        copyUserIdButton.onClick {
            Gdx.app.clipboard.contents = UncivGame.Current.settings.userId
            ResponsePopup("UserID copied to clipboard".tr(), this)
        }
        rightSideTable.add(copyUserIdButton).pad(10f).padBottom(30f).row()

        copyGameIdButton.onClick {
            Gdx.app.clipboard.contents = selectedGame.gameId
            ResponsePopup("GameID copied to clipboard".tr(), this)
        }
        rightSideTable.add(copyGameIdButton).pad(10f).row()

        editButton.onClick {
            UncivGame.Current.setScreen(EditMultiplayerGameInfoScreen(selectedGame, selectedGameName))
            //game must be unselected in case the game gets deleted inside the EditScreen
            unselectGame()
        }
        rightSideTable.add(editButton).pad(10f).row()

        addGameButton.onClick {
            addMultiplayerGame(Gdx.app.clipboard.contents)
        }
        rightSideTable.add(addGameButton).pad(10f).padBottom(30f).row()

        refreshButton.onClick {
            redownloadAllGames()
        }
        rightSideTable.add(refreshButton).pad(10f).row()

        //RightSideButton Setup
        rightSideButton.setText("Join Game".tr())
        rightSideButton.onClick {
            joinMultiplaerGame()
        }
    }

    //Adds a new Multiplayer game to the List
    //gameId must be nullable because clipboard content could be null
    private fun addMultiplayerGame(gameId: String?, gameName: String = ""){
        try {
            //since the gameId is a String it can contain anything and has to be checked
            UUID.fromString(gameId!!.trim())
        } catch (ex: Exception) {
            val errorPopup = Popup(this)
            errorPopup.addGoodSizedLabel("Invalid game ID!".tr())
            errorPopup.row()
            errorPopup.addCloseButton()
            errorPopup.open()
            return
        }

        if (GameSaver.gameIsAlreadySavedAsMultiplayer(gameId)) {
            ResponsePopup("Game is already added".tr(), this)
            return
        }

        thread(name="MultiplayerDownload") {
            addGameButton.setText("Working...".tr())
            addGameButton.disable()
            try {
                // The tryDownload can take more than 500ms. Therefore, to avoid ANRs,
                // we need to run it in a different thread.
                val game = OnlineMultiplayer().tryDownloadGame(gameId.trim())
                if (gameName.trim() == "")
                    GameSaver().saveGame(game, game.gameId, true)
                else
                    GameSaver().saveGame(game, gameName, true)

                reloadGameListUI()
            } catch (ex: Exception) {
                val errorPopup = Popup(this)
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
            val errorPopup = Popup(this)
            errorPopup.addGoodSizedLabel("Could not download game!".tr())
            errorPopup.row()
            errorPopup.addCloseButton()
            errorPopup.open()
        }
    }

    //reloads all gameFiles to refresh UI
    private fun reloadGameListUI(){
        val leftSubTable = Table()

        //Threading results in a much faster loading time for the screen because loadGameByName() can take some time
        thread (name = "reloadGameList") {
            for (entry in GameSaver.getMutliplayerGameList()) {
                val gameTable = Table()
                var game: GameInfo?
                var gameFileHandle: FileHandle?
                var lastModifiedMillis = 0L

                try {
                    game = GameSaver().loadGameByName(entry.value, true)
                } catch (ex: Exception) {
                    //skipping one save is not fatal
                    ResponsePopup("Could not refresh!".tr(), this)
                    continue
                }

                if (isUsersTurn(game)) {
                    gameTable.add(ImageGetter.getNationIndicator(game.currentPlayerCiv.nation, 45f))
                } else {
                    gameTable.add()
                }

                try {
                    gameFileHandle = GameSaver().getSave(entry.value, true)
                    lastModifiedMillis = gameFileHandle.lastModified()
                } catch (ex: Exception) {
                    //Just Catch. its not important if lastModifiedMillis is updated correctly
                }

                val gameButton = TextButton(entry.value, skin)
                gameButton.onClick {
                    selectedGame = game
                    selectedGameName = entry.value
                    copyGameIdButton.enable()
                    editButton.enable()
                    rightSideButton.enable()

                    //get Minutes since last modified
                    val lastSavedMinutesAgo = (System.currentTimeMillis() - lastModifiedMillis) / 60000
                    var descriptionText = "Last refresh: [$lastSavedMinutesAgo] minutes ago".tr() + "\r\n"
                    descriptionText += "Current Turn:".tr() + " ${selectedGame.currentPlayer}\r\n"
                    descriptionLabel.setText(descriptionText)
                }

                gameTable.add(gameButton).pad(5f).row()
                leftSubTable.add(gameTable).row()
            }
        }
        leftSideTable.clear()
        leftSideTable.add(leftSubTable)
    }

    /* Is this worth the translation afford? Seems definitely more polished if used

    private fun getLastRefreshText(lastRefreshMinutesAgo: Long): String{
        var lastRefresh = lastRefreshMinutesAgo
        if (lastRefresh < 60) {
            if (lastRefresh == 1L)
                return "Last refresh: [$lastRefresh] minute ago".tr()
            return "Last refresh: [$lastRefresh] minutes ago".tr()
        }
        lastRefresh /= 60
        if (lastRefresh < 24) {
            if (lastRefresh == 1L)
                return "Last refresh: [$lastRefresh] hour ago".tr()
            return "Last refresh: [$lastRefresh] hours ago".tr()
        }
        lastRefresh /= 24
        if (lastRefresh == 1L)
            return "Last refresh: [$lastRefresh] day ago".tr()
        return "Last refresh: [$lastRefresh] days ago".tr()
    }
    */

    //redownload all games to update the list
    private fun redownloadAllGames(){
        addGameButton.disable()
        refreshButton.setText("Working...".tr())
        refreshButton.disable()

        //One thread for all downloads
        thread (name = "multiplayerGameDownload") {
            for (entry in GameSaver.getMutliplayerGameList()) {
                try {
                    val game = OnlineMultiplayer().tryDownloadGame(entry.key)
                    GameSaver().saveGame(game, entry.value, true)
                } catch (ex: Exception) {
                    //skipping one is not fatal
                    //Trying to use as many prev. used strings as possible
                    ResponsePopup("Could not download game!".tr() + " ${entry.value}", this)
                    continue
                }
            }

            //Reset UI
            unselectGame()
            reloadGameListUI()
            addGameButton.enable()
            refreshButton.setText(refreshText)
            refreshButton.enable()
        }
    }

    //Adds a button to add the currently running game to the multiplayerFileManager
    private fun addCurrentGameButton(){
        val currentlyRunningGame = UncivGame.Current.gameInfo
        if (!currentlyRunningGame.gameParameters.isOnlineMultiplayer || GameSaver.gameIsAlreadySavedAsMultiplayer(currentlyRunningGame.gameId))
            return

        val currentGameButton = TextButton("Add Currently Running Game".tr(), skin)
        //save screen so it can be used for Popup call
        val menuScreen = this
        //onClick has to be inside apply to be able to remove the button from topTable
        currentGameButton.apply {
            onClick {
                //run on different thread because saveGame can take some time on phones
                thread {
                    setText("Working...".tr())
                    disable()
                    try {
                        GameSaver().saveGame(currentlyRunningGame, currentlyRunningGame.gameId, true)
                        reloadGameListUI()
                        topTable.removeActor(this)
                    } catch (ex: Exception) {
                        val errorPopup = Popup(menuScreen)
                        errorPopup.addGoodSizedLabel("Could not save game!".tr())
                        errorPopup.row()
                        errorPopup.addCloseButton()
                        errorPopup.open()
                        setText("Add Currently Running Game".tr())
                        enable()
                    }
                }
            }
        }

        topTable.add(currentGameButton)
    }

    //Adds a button
    private fun addHelpButton(){
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
        return (game.currentPlayerCiv.playerId == UncivGame.Current.settings.userId)
    }
}

//Subscreen of MultiplayerScreen to edit and delete saves
class EditMultiplayerGameInfoScreen(game: GameInfo, gameFileName: String): PickerScreen(){
    init {
        val textField = TextField(gameFileName, skin)

        topTable.add(Label("Rename".tr(), skin)).row()
        topTable.add(textField).pad(10f).padBottom(30f).width(stage.width/2).row()

        //TODO add "give up" button
            //->turn a player into an AI so everyone can still play without the user
                //->should only be possible on the users turn because it has to be uploaded afterwards
        val deleteButton = TextButton("Delete save".tr(), skin)
        deleteButton.onClick {
            val askPopup = Popup(this)
            askPopup.addGoodSizedLabel("Are you sure you want to delete this map?".tr()).row()
            askPopup.addButton("Yes"){
                try {
                    GameSaver().deleteMultplayerGameById(game.gameId)
                    UncivGame.Current.setScreen(MultiplayerScreen())
                }catch (ex: Exception) {
                    askPopup.close()
                    ResponsePopup("Could not delete game!".tr(), this)
                }
            }
            askPopup.addButton("No"){
                askPopup.close()
            }
            askPopup.open()
        }.apply { color = Color.RED }

        topTable.add(deleteButton)

        //CloseButton Setup
        closeButton.setText("Back".tr())
        closeButton.onClick {
            UncivGame.Current.setScreen(MultiplayerScreen())
        }

        //RightSideButton Setup
        rightSideButton.setText("Save game".tr())
        rightSideButton.enable()
        rightSideButton.onClick {
            rightSideButton.setText("Saving...".tr())
            rightSideButton.disable()
            try {
                GameSaver().saveGame(game, textField.text, true)
                UncivGame.Current.setScreen(MultiplayerScreen())
            }catch (ex: Exception) {
                val errorPopup = Popup(this)
                errorPopup.addGoodSizedLabel("Could not save game!".tr())
                errorPopup.row()
                errorPopup.addCloseButton()
                errorPopup.open()
                //If saveGame() fails rightSideButton would else displays "Saving..."
                rightSideButton.setText("Save game".tr())
                rightSideButton.enable()
            }
        }
    }
}
