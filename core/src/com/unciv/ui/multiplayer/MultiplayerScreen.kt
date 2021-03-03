package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.logic.GameInfo
import com.unciv.logic.GameSaver
import com.unciv.logic.IdChecker
import com.unciv.logic.UncivShowableException
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.mainmenu.OnlineMultiplayer
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class MultiplayerScreen(previousScreen: CameraStageBaseScreen) : PickerScreen() {

    private lateinit var selectedGameFile: FileHandle

    // Concurrent because we can get concurrent modification errors if we change things around while running redownloadAllGames() in another thread
    private var multiplayerGames = ConcurrentHashMap<FileHandle, GameInfo>()
    private val rightSideTable = Table()
    private val leftSideTable = Table()

    private val editButtonText = "Game settings".tr()
    private val addGameText = "Add multiplayer game".tr()
    private val copyGameIdText = "Copy game ID".tr()
    private val copyUserIdText = "Copy user ID".tr()
    private val refreshText = "Refresh list".tr()

    private val editButton = TextButton(editButtonText, skin).apply { disable() }
    private val addGameButton = TextButton(addGameText, skin)
    private val copyGameIdButton = TextButton(copyGameIdText, skin).apply { disable() }
    private val copyUserIdButton = TextButton(copyUserIdText, skin)
    private val refreshButton = TextButton(refreshText, skin)

    init {
        setDefaultCloseAction(previousScreen)

        //Help Button Setup
        val tab = Table()
        val helpButton = TextButton("?", skin)
        helpButton.onClick {
            val helpPopup = Popup(this)
            helpPopup.addGoodSizedLabel("To create a multiplayer game, check the 'multiplayer' toggle in the New Game screen, and for each human player insert that player's user ID.").row()
            helpPopup.addGoodSizedLabel("You can assign your own user ID there easily, and other players can copy their user IDs here and send them to you for you to include them in the game.").row()
            helpPopup.addGoodSizedLabel("").row()

            helpPopup.addGoodSizedLabel("Once you've created your game, the Game ID gets automatically copied to your clipboard so you can send it to the other players.").row()
            helpPopup.addGoodSizedLabel("Players can enter your game by copying the game ID to the clipboard, and clicking on the 'Add multiplayer game' button").row()
            helpPopup.addGoodSizedLabel("").row()

            helpPopup.addGoodSizedLabel("The symbol of your nation will appear next to the game when it's your turn").row()

            helpPopup.addCloseButton()
            helpPopup.open()
        }
        tab.add(helpButton)
        tab.x = (stage.width - helpButton.width)
        tab.y = (stage.height - helpButton.height)
        stage.addActor(tab)

        //TopTable Setup
        //Have to put it into a separate Table to be able to add another copyGameID button
        val mainTable = Table()
        mainTable.add(ScrollPane(leftSideTable).apply { setScrollingDisabled(true, false) }).height(stage.height * 2 / 3)
        mainTable.add(rightSideTable)
        topTable.add(mainTable).row()
        scrollPane.setScrollingDisabled(false, true)

        rightSideTable.defaults().uniformX()
        rightSideTable.defaults().fillX()
        rightSideTable.defaults().pad(10.0f)

        // leftTable Setup
        reloadGameListUI()

        // A Button to add the currently running game as multiplayer game
        //addCurrentGameButton()

        //rightTable Setup
        copyUserIdButton.onClick {
            Gdx.app.clipboard.contents = game.settings.userId
            ToastPopup("UserID copied to clipboard", this)
        }
        rightSideTable.add(copyUserIdButton).padBottom(30f).row()

        copyGameIdButton.onClick {
            val gameInfo = multiplayerGames[selectedGameFile]
            if (gameInfo != null) {
                Gdx.app.clipboard.contents = gameInfo.gameId
                ToastPopup("GameID copied to clipboard", this)
            }
        }
        rightSideTable.add(copyGameIdButton).row()

        editButton.onClick {
            game.setScreen(EditMultiplayerGameInfoScreen(multiplayerGames[selectedGameFile], selectedGameFile.name(), this))
            //game must be unselected in case the game gets deleted inside the EditScreen
            unselectGame()
        }
        rightSideTable.add(editButton).row()

        addGameButton.onClick {
            game.setScreen(AddMultiplayerGameScreen(this))
        }
        rightSideTable.add(addGameButton).padBottom(30f).row()

        refreshButton.onClick {
            redownloadAllGames()
        }
        rightSideTable.add(refreshButton).row()

        //RightSideButton Setup
        rightSideButton.setText("Join game".tr())
        rightSideButton.onClick {
            joinMultiplaerGame()
        }
    }

    //Adds a new Multiplayer game to the List
    //gameId must be nullable because clipboard content could be null
    fun addMultiplayerGame(gameId: String?, gameName: String = "") {
        try {
            //since the gameId is a String it can contain anything and has to be checked
            UUID.fromString(IdChecker.checkAndReturnGameUuid(gameId!!))
        } catch (ex: Exception) {
            val errorPopup = Popup(this)
            errorPopup.addGoodSizedLabel("Invalid game ID!")
            errorPopup.row()
            errorPopup.addCloseButton()
            errorPopup.open()
            return
        }

        if (gameIsAlreadySavedAsMultiplayer(gameId)) {
            ToastPopup("Game is already added", this)
            return
        }

        addGameButton.setText("Working...".tr())
        addGameButton.disable()
        thread(name = "MultiplayerDownload") {
            try {
                // The tryDownload can take more than 500ms. Therefore, to avoid ANRs,
                // we need to run it in a different thread.
                val game = OnlineMultiplayer().tryDownloadGame(gameId.trim())
                if (gameName == "")
                    GameSaver.saveGame(game, game.gameId, true)
                else
                    GameSaver.saveGame(game, gameName, true)

                Gdx.app.postRunnable { reloadGameListUI() }
            } catch (ex: Exception) {
                Gdx.app.postRunnable {
                    val errorPopup = Popup(this)
                    errorPopup.addGoodSizedLabel("Could not download game!")
                    errorPopup.row()
                    errorPopup.addCloseButton()
                    errorPopup.open()
                }
            }
            Gdx.app.postRunnable {
                addGameButton.setText(addGameText)
                addGameButton.enable()
            }
        }
    }

    //just loads the game from savefile
    //the game will be downloaded opon joining it anyway
    private fun joinMultiplaerGame() {
        try {
            game.loadGame(multiplayerGames[selectedGameFile]!!)
        } catch (ex: Exception) {
            val errorPopup = Popup(this)
            errorPopup.addGoodSizedLabel("Could not download game!")
            errorPopup.row()
            errorPopup.addCloseButton()
            errorPopup.open()
        }
    }

    private fun gameIsAlreadySavedAsMultiplayer(gameId: String): Boolean {
        val games = multiplayerGames.filterValues { it.gameId == gameId }
        return games.isNotEmpty()
    }

    //reloads all gameFiles to refresh UI
    fun reloadGameListUI() {
        val leftSubTable = Table()
        val gameSaver = GameSaver
        val savedGames: Sequence<FileHandle>

        try {
            savedGames = gameSaver.getSaves(true)
        } catch (ex: Exception) {
            val errorPopup = Popup(this)
            errorPopup.addGoodSizedLabel("Could not refresh!")
            errorPopup.row()
            errorPopup.addCloseButton()
            errorPopup.open()
            return
        }

        for (gameSaveFile in savedGames) {
            val gameTable = Table()
            val turnIndicator = Table()
            var currentTurnUser = ""

            try {
                turnIndicator.add(ImageGetter.getImage("EmojiIcons/Turn"))
                gameTable.add(turnIndicator)

                val lastModifiedMillis = gameSaveFile.lastModified()
                val gameButton = gameSaveFile.name().toTextButton()

                gameButton.onClick {
                    selectedGameFile = gameSaveFile
                    if (multiplayerGames[gameSaveFile] != null) {
                        copyGameIdButton.enable()
                    } else {
                        copyGameIdButton.disable()
                    }

                    editButton.enable()
                    rightSideButton.enable()

                    //get Minutes since last modified
                    val lastSavedMinutesAgo = (System.currentTimeMillis() - lastModifiedMillis) / 60000
                    var descriptionText = "Last refresh: [$lastSavedMinutesAgo] minutes ago".tr() + "\r\n"
                    descriptionText += "Current Turn:".tr() + " ${currentTurnUser}\r\n"
                    descriptionLabel.setText(descriptionText)
                }

                gameTable.add(gameButton).pad(5f).row()
                leftSubTable.add(gameTable).row()
            } catch (ex: Exception) {
                //skipping one save is not fatal
                ToastPopup("Could not refresh!", this)
                continue
            }

            thread(name = "loadGameFile") {
                try {
                    val game = gameSaver.loadGameFromFile(gameSaveFile)

                    //Add games to list so saves don't have to be loaded as Files so often
                    if (!gameIsAlreadySavedAsMultiplayer(game.gameId)) {
                        multiplayerGames[gameSaveFile] = game
                    }

                    Gdx.app.postRunnable {
                        turnIndicator.clear()
                        if (isUsersTurn(game)) {
                            turnIndicator.add(ImageGetter.getNationIndicator(game.currentPlayerCiv.nation, 50f))
                        }
                        //set variable so it can be displayed when gameButton.onClick gets called
                        currentTurnUser = game.currentPlayer
                    }
                } catch (usx: UncivShowableException) {
                    //Gets thrown when mods are not installed
                    Gdx.app.postRunnable {
                        val popup = Popup(this)
                        popup.addGoodSizedLabel(usx.message!! + " in ${gameSaveFile.name()}").row()
                        popup.addCloseButton()
                        popup.open(true)

                        turnIndicator.clear()
                        turnIndicator.add(ImageGetter.getImage("StatIcons/Malcontent")).size(50f)
                    }
                } catch (ex: Exception) {
                    Gdx.app.postRunnable {
                        ToastPopup("Could not refresh!", this)
                        turnIndicator.clear()
                        turnIndicator.add(ImageGetter.getImage("StatIcons/Malcontent")).size(50f)
                    }
                }
            }
        }

        leftSideTable.clear()
        leftSideTable.add(leftSubTable)
    }

    //redownload all games to update the list
    //can maybe replaced when notification support gets introduced
    private fun redownloadAllGames() {
        addGameButton.disable()
        refreshButton.setText("Working...".tr())
        refreshButton.disable()

        //One thread for all downloads
        thread(name = "multiplayerGameDownload") {
            for (entry in multiplayerGames) {
                try {
                    val game = OnlineMultiplayer().tryDownloadGame(entry.value.gameId)
                    GameSaver.saveGame(game, entry.key.name(), true)
                } catch (ex: Exception) {
                    //skipping one is not fatal
                    //Trying to use as many prev. used strings as possible
                    Gdx.app.postRunnable {
                        ToastPopup("Could not download game!" + " ${entry.key.name()}", this)
                    }
                    continue
                }
            }

            //Reset UI
            Gdx.app.postRunnable {
                addGameButton.enable()
                refreshButton.setText(refreshText)
                refreshButton.enable()
                unselectGame()
                reloadGameListUI()
            }
        }
    }

    //Adds a Button to add the currently running game to multiplayerGameList
    private fun addCurrentGameButton() {
        val currentlyRunningGame = game.gameInfo
        if (!currentlyRunningGame.gameParameters.isOnlineMultiplayer || gameIsAlreadySavedAsMultiplayer(currentlyRunningGame.gameId))
            return

        val currentGameButton = "Add Currently Running Game".toTextButton()
        currentGameButton.onClick {
            if (gameIsAlreadySavedAsMultiplayer(currentlyRunningGame.gameId))
                return@onClick
            try {
                GameSaver.saveGame(currentlyRunningGame, currentlyRunningGame.gameId, true)
                reloadGameListUI()
            } catch (ex: Exception) {
                val errorPopup = Popup(this)
                errorPopup.addGoodSizedLabel("Could not save game!")
                errorPopup.row()
                errorPopup.addCloseButton()
                errorPopup.open()
            }
        }

        topTable.add(currentGameButton)
    }

    //It doesn't really unselect the game because selectedGame cant be null
    //It just disables everything a selected game has set
    private fun unselectGame() {
        editButton.disable()
        copyGameIdButton.disable()
        rightSideButton.disable()
        descriptionLabel.setText("")
    }

    //check if its the users turn
    private fun isUsersTurn(gameInfo: GameInfo) = gameInfo.currentPlayerCiv.playerId == game.settings.userId

    fun removeMultiplayerGame(gameInfo: GameInfo?, gameName: String) {
        val games = multiplayerGames.filterValues { it == gameInfo }.keys
        try {
            GameSaver.deleteSave(gameName, true)
            if (games.isNotEmpty()) multiplayerGames.remove(games.first())
        } catch (ex: Exception) {
            ToastPopup("Could not delete game!", this)
        }

    }
}