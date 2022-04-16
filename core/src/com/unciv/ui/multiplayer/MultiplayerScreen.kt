package com.unciv.ui

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.logic.*
import com.unciv.logic.multiplayer.FileStorageRateLimitReached
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import com.unciv.logic.multiplayer.OnlineMultiplayer
import com.unciv.ui.crashhandling.crashHandlingThread
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import java.io.FileNotFoundException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class MultiplayerScreen(previousScreen: BaseScreen) : PickerScreen() {

    private lateinit var selectedGameFile: FileHandle

    // Concurrent because we can get concurrent modification errors if we change things around while running redownloadAllGames() in another thread
    private var multiplayerGames = ConcurrentHashMap<FileHandle, GameInfoPreview>()
    private val rightSideTable = Table()
    private val leftSideTable = Table()

    private val editButtonText = "Game settings"
    private val addGameText = "Add multiplayer game"
    private val copyGameIdText = "Copy game ID"
    private val copyUserIdText = "Copy user ID"
    private val refreshText = "Refresh list"

    private val editButton = editButtonText.toTextButton().apply { disable() }
    private val addGameButton = addGameText.toTextButton()
    private val copyGameIdButton = copyGameIdText.toTextButton().apply { disable() }
    private val copyUserIdButton = copyUserIdText.toTextButton()
    private val refreshButton = refreshText.toTextButton()

    init {
        setDefaultCloseAction(previousScreen)

        //Help Button Setup
        val tab = Table()
        val helpButton = "Help".toTextButton()
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
                ToastPopup("Game ID copied to clipboard!", this)
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
            joinMultiplayerGame()
        }
    }

    //Adds a new Multiplayer game to the List
    //gameId must be nullable because clipboard content could be null
    fun addMultiplayerGame(gameId: String?, gameName: String = "") {
        val popup = Popup(this)
        popup.addGoodSizedLabel("Working...")
        popup.open()

        try {
            //since the gameId is a String it can contain anything and has to be checked
            UUID.fromString(IdChecker.checkAndReturnGameUuid(gameId!!))
        } catch (ex: Exception) {
            popup.reuseWith("Invalid game ID!", true)
            return
        }

        if (gameIsAlreadySavedAsMultiplayer(gameId)) {
            popup.reuseWith("Game is already added", true)
            return
        }

        addGameButton.setText("Working...".tr())
        addGameButton.disable()
        crashHandlingThread(name = "MultiplayerDownload") {
            try {
                // The tryDownload can take more than 500ms. Therefore, to avoid ANRs,
                // we need to run it in a different thread.
                val gamePreview = OnlineMultiplayer().tryDownloadGamePreview(gameId.trim())
                if (gameName == "")
                    GameSaver.saveGame(gamePreview, gamePreview.gameId)
                else
                    GameSaver.saveGame(gamePreview, gameName)

                postCrashHandlingRunnable { reloadGameListUI() }
            } catch (ex: FileNotFoundException) {
                // Game is so old that a preview could not be found on dropbox lets try the real gameInfo instead
                try {
                    val gamePreview = OnlineMultiplayer().tryDownloadGame(gameId.trim()).asPreview()
                    if (gameName == "")
                        GameSaver.saveGame(gamePreview, gamePreview.gameId)
                    else
                        GameSaver.saveGame(gamePreview, gameName)

                    postCrashHandlingRunnable { reloadGameListUI() }
                } catch (ex: Exception) {
                    postCrashHandlingRunnable {
                        popup.reuseWith("Could not download game!", true)
                    }
                }
            } catch (ex: FileStorageRateLimitReached) {
                postCrashHandlingRunnable {
                    popup.reuseWith("Server limit reached! Please wait for [${ex.message}] seconds", true)
                }
            } catch (ex: Exception) {
                postCrashHandlingRunnable {
                    popup.reuseWith("Could not download game!", true)
                }
            }
            postCrashHandlingRunnable {
                addGameButton.setText(addGameText.tr())
                addGameButton.enable()
            }
        }
    }

    //Download game and use the popup to cover ANRs
    private fun joinMultiplayerGame() {
        val loadingGamePopup = Popup(this)
        loadingGamePopup.add("Loading latest game state...".tr())
        loadingGamePopup.open()

        crashHandlingThread(name = "JoinMultiplayerGame") {
            try {
                val gameId = multiplayerGames[selectedGameFile]!!.gameId
                val gameInfo = OnlineMultiplayer().tryDownloadGame(gameId)
                postCrashHandlingRunnable { game.loadGame(gameInfo) }
            } catch (ex: FileStorageRateLimitReached) {
                postCrashHandlingRunnable {
                    loadingGamePopup.reuseWith("Server limit reached! Please wait for [${ex.message}] seconds", true)
                }
            } catch (ex: Exception) {
                postCrashHandlingRunnable {
                    loadingGamePopup.reuseWith("Could not download game!", true)
                }
            }
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
            var lastTurnMillis = 0L

            try {
                turnIndicator.add(ImageGetter.getImage("EmojiIcons/Turn"))
                gameTable.add(turnIndicator)

                val lastModifiedMillis = gameSaveFile.lastModified()
                val gameButton = gameSaveFile.name().toTextButton()


                //TODO: replace this with nice formatting using kotlin.time.DurationUnit (once it is no longer experimental)
                fun formattedElapsedTime(lastMillis: Long): String {
                    val elapsedMinutes = (System.currentTimeMillis() - lastMillis) / 60000
                    return when {
                        elapsedMinutes < 120 -> "[$elapsedMinutes] [Minutes]"
                        elapsedMinutes < 2880 -> "[${elapsedMinutes / 60}] [Hours]"
                        else -> "[${elapsedMinutes / 1440}] [Days]"
                    }
                }

                gameButton.onClick {
                    selectedGameFile = gameSaveFile
                    if (multiplayerGames[gameSaveFile] != null) {
                        copyGameIdButton.enable()
                    } else {
                        copyGameIdButton.disable()
                    }

                    editButton.enable()
                    rightSideButton.enable()
                    var descriptionText = "Last refresh: ${formattedElapsedTime(lastModifiedMillis)} ago".tr() + "\n"
                    descriptionText += "Current Turn: [$currentTurnUser] since ${formattedElapsedTime(lastTurnMillis)} ago".tr() + "\n"
                    descriptionLabel.setText(descriptionText)
                }

                gameTable.add(gameButton).pad(5f).row()
                leftSubTable.add(gameTable).row()
            } catch (ex: Exception) {
                //skipping one save is not fatal
                ToastPopup("Could not refresh!", this)
                continue
            }

            crashHandlingThread(name = "loadGameFile") {
                try {
                    val game = gameSaver.loadGamePreviewFromFile(gameSaveFile)

                    //Add games to list so saves don't have to be loaded as Files so often
                    if (!gameIsAlreadySavedAsMultiplayer(game.gameId)) {
                        multiplayerGames[gameSaveFile] = game
                    }

                    postCrashHandlingRunnable {
                        turnIndicator.clear()
                        if (isUsersTurn(game)) {
                            turnIndicator.add(ImageGetter.getImage("OtherIcons/ExclamationMark")).size(50f)
                        }
                        //set variable so it can be displayed when gameButton.onClick gets called
                        currentTurnUser = game.currentPlayer
                        lastTurnMillis = game.currentTurnStartTime
                    }
                } catch (usx: UncivShowableException) {
                    //Gets thrown when mods are not installed
                    postCrashHandlingRunnable {
                        val popup = Popup(this)
                        popup.addGoodSizedLabel(usx.message!! + " in ${gameSaveFile.name()}").row()
                        popup.addCloseButton()
                        popup.open(true)

                        turnIndicator.clear()
                        turnIndicator.add(ImageGetter.getImage("StatIcons/Malcontent")).size(50f)
                    }
                } catch (ex: Exception) {
                    postCrashHandlingRunnable {
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
        crashHandlingThread(name = "multiplayerGameDownload") {
            for ((fileHandle, gameInfo) in multiplayerGames) {
                try {
                    // Update game without overriding multiplayer settings
                    val game = gameInfo.updateCurrentTurn(OnlineMultiplayer().tryDownloadGamePreview(gameInfo.gameId))
                    GameSaver.saveGame(game, fileHandle.name())
                    multiplayerGames[fileHandle] = game

                } catch (ex: FileNotFoundException) {
                    // Game is so old that a preview could not be found on dropbox lets try the real gameInfo instead
                    try {
                        // Update game without overriding multiplayer settings
                        val game = gameInfo.updateCurrentTurn(OnlineMultiplayer().tryDownloadGame(gameInfo.gameId))
                        GameSaver.saveGame(game, fileHandle.name())
                        multiplayerGames[fileHandle] = game

                    } catch (ex: Exception) {
                        postCrashHandlingRunnable {
                            ToastPopup("Could not download game!" + " ${fileHandle.name()}", this)
                        }
                    }
                } catch (ex: FileStorageRateLimitReached) {
                    postCrashHandlingRunnable {
                        ToastPopup("Server limit reached! Please wait for [${ex.message}] seconds", this)
                    }
                    break // No need to keep trying if rate limit is reached
                } catch (ex: Exception) {
                    //skipping one is not fatal
                    //Trying to use as many prev. used strings as possible
                    postCrashHandlingRunnable {
                        ToastPopup("Could not download game!" + " ${fileHandle.name()}", this)
                    }
                }
            }

            //Reset UI
            postCrashHandlingRunnable {
                addGameButton.enable()
                refreshButton.setText(refreshText.tr())
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
                GameSaver.saveGame(currentlyRunningGame, currentlyRunningGame.gameId)
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
    private fun isUsersTurn(gameInfo: GameInfoPreview) = gameInfo.getCivilization(gameInfo.currentPlayer).playerId == game.settings.userId

    fun removeMultiplayerGame(gameInfo: GameInfoPreview?, gameName: String) {
        val games = multiplayerGames.filterValues { it == gameInfo }.keys
        try {
            GameSaver.deleteSave(gameName, true)
            if (games.isNotEmpty()) multiplayerGames.remove(games.first())
        } catch (ex: Exception) {
            ToastPopup("Could not delete game!", this)
        }

    }
}
