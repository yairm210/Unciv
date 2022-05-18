package com.unciv.ui.multiplayer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.UncivGame
import com.unciv.logic.*
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.*
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.models.translations.tr
import com.unciv.ui.pickerscreens.PickerScreen
import com.unciv.ui.utils.*
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.Popup
import com.unciv.ui.popup.ToastPopup
import java.io.FileNotFoundException
import java.time.Duration
import java.time.Instant
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class MultiplayerScreen(previousScreen: BaseScreen) : PickerScreen() {
    private var selectedGame: OnlineMultiplayerGame? = null

    private val editButtonText = "Game settings"
    private val editButton = createEditButton()

    private val addGameText = "Add multiplayer game"
    private val addGameButton = createAddGameButton()

    private val copyGameIdText = "Copy game ID"
    private val copyGameIdButton = createCopyGameIdButton()

    private val copyUserIdText = "Copy user ID"
    private val copyUserIdButton = createCopyUserIdButton()

    private val refreshText = "Refresh list"
    private val refreshButton = createRefreshButton()

    private val rightSideTable = createRightSideTable()
    private val leftSideTable = GameList(::selectGame)

    private val events = EventBus.EventReceiver()

    init {
        setDefaultCloseAction(previousScreen)

        scrollPane.setScrollingDisabled(false, true)

        topTable.add(createMainContent()).row()

        setupHelpButton()

        setupRightSideButton()

        events.receive(MultiplayerGameDeleted::class, {it.name == selectedGame?.name}) {
            unselectGame()
        }

        game.onlineMultiplayer.requestUpdate()
    }

    private fun setupRightSideButton() {
        rightSideButton.setText("Join game".tr())
        rightSideButton.onClick { joinMultiplayerGame(selectedGame!!) }
    }

    private fun createRightSideTable(): Table {
        val table = Table()
        table.defaults().uniformX()
        table.defaults().fillX()
        table.defaults().pad(10.0f)
        table.add(copyUserIdButton).padBottom(30f).row()
        table.add(copyGameIdButton).row()
        table.add(editButton).row()
        table.add(addGameButton).padBottom(30f).row()
        table.add(refreshButton).row()
        return table
    }

    fun createRefreshButton(): TextButton {
        val btn = refreshText.toTextButton()
        btn.onClick { game.onlineMultiplayer.requestUpdate() }
        return btn
    }

    fun createAddGameButton(): TextButton {
        val btn = addGameText.toTextButton()
        btn.onClick {
            game.setScreen(AddMultiplayerGameScreen(this))
        }
        return btn
    }

    fun createEditButton(): TextButton {
        val btn = editButtonText.toTextButton().apply { disable() }
        btn.onClick {
            game.setScreen(EditMultiplayerGameInfoScreen(selectedGame!!, this))
        }
        return btn
    }

    fun createCopyGameIdButton(): TextButton {
        val btn = copyGameIdText.toTextButton().apply { disable() }
        btn.onClick {
            val gameInfo = selectedGame?.preview
            if (gameInfo != null) {
                Gdx.app.clipboard.contents = gameInfo.gameId
                ToastPopup("Game ID copied to clipboard!", this)
            }
        }
        return btn
    }

    private fun createCopyUserIdButton(): TextButton {
        val btn = copyUserIdText.toTextButton()
        btn.onClick {
            Gdx.app.clipboard.contents = game.settings.userId
            ToastPopup("UserID copied to clipboard", this)
        }
        return btn
    }

    private fun createMainContent(): Table {
        val mainTable = Table()
        mainTable.add(ScrollPane(leftSideTable).apply { setScrollingDisabled(true, false) }).center()
        mainTable.add(rightSideTable)
        return mainTable
    }

    private fun setupHelpButton() {
        val tab = Table()
        val helpButton = "Help".toTextButton()
        helpButton.onClick {
            val helpPopup = Popup(this)
            helpPopup.addGoodSizedLabel("To create a multiplayer game, check the 'multiplayer' toggle in the New Game screen, and for each human player insert that player's user ID.")
                .row()
            helpPopup.addGoodSizedLabel("You can assign your own user ID there easily, and other players can copy their user IDs here and send them to you for you to include them in the game.")
                .row()
            helpPopup.addGoodSizedLabel("").row()

            helpPopup.addGoodSizedLabel("Once you've created your game, the Game ID gets automatically copied to your clipboard so you can send it to the other players.")
                .row()
            helpPopup.addGoodSizedLabel("Players can enter your game by copying the game ID to the clipboard, and clicking on the 'Add multiplayer game' button")
                .row()
            helpPopup.addGoodSizedLabel("").row()

            helpPopup.addGoodSizedLabel("The symbol of your nation will appear next to the game when it's your turn").row()

            helpPopup.addCloseButton()
            helpPopup.open()
        }
        tab.add(helpButton)
        tab.x = (stage.width - helpButton.width)
        tab.y = (stage.height - helpButton.height)

        stage.addActor(tab)
    }

    fun joinMultiplayerGame(selectedGame: OnlineMultiplayerGame) {
        val loadingGamePopup = Popup(this)
        loadingGamePopup.addGoodSizedLabel("Loading latest game state...")
        loadingGamePopup.open()

        launchCrashHandling("JoinMultiplayerGame") {
            try {
                game.onlineMultiplayer.loadGame(selectedGame)
            } catch (ex: Exception) {
                val message = getLoadExceptionMessage(ex)
                postCrashHandlingRunnable {
                    loadingGamePopup.reuseWith(message, true)
                }
            }
        }
    }

    private fun unselectGame() {
        selectedGame = null

        editButton.disable()
        copyGameIdButton.disable()
        rightSideButton.disable()
        descriptionLabel.setText("")
    }

    fun selectGame(name: String) {
        val multiplayerGame = game.onlineMultiplayer.getGameByName(name)
        if (multiplayerGame == null) {
            // Should never happen
            unselectGame()
            return
        }

        selectedGame = multiplayerGame

        if (multiplayerGame.preview != null) {
            copyGameIdButton.enable()
        } else {
            copyGameIdButton.disable()
        }
        editButton.enable()
        rightSideButton.enable()

        descriptionLabel.setText(buildDescriptionText(multiplayerGame))
    }

    private fun buildDescriptionText(multiplayerGame: OnlineMultiplayerGame): StringBuilder {
        val descriptionText = StringBuilder()
        val ex = multiplayerGame.error
        if (ex != null) {
            descriptionText.append("Error while refreshing:".tr()).append(' ')
            val message = getLoadExceptionMessage(ex)
            descriptionText.appendLine(message.tr())
        }
        val lastUpdate = multiplayerGame.lastUpdate
        descriptionText.appendLine("Last refresh: ${formattedElapsedTime(lastUpdate)} ago".tr())
        val preview = multiplayerGame.preview
        if (preview?.currentPlayer != null) {
            val currentTurnStartTime = Instant.ofEpochMilli(preview.currentTurnStartTime)
            descriptionText.appendLine("Current Turn: [${preview.currentPlayer}] since ${formattedElapsedTime(currentTurnStartTime)} ago".tr())
        }
        return descriptionText
    }

    private fun formattedElapsedTime(lastUpdate: Instant): String {
        val durationToNow = Duration.between(lastUpdate, Instant.now())
        val elapsedMinutes = durationToNow.toMinutes()
        if (elapsedMinutes < 120) return "[$elapsedMinutes] [Minutes]"
        val elapsedHours = durationToNow.toHours()
        if (elapsedHours < 48) {
            return "[${elapsedHours}] [Hours]"
        } else {
            return "[${durationToNow.toDays()}] [Days]"
        }
    }

    fun getLoadExceptionMessage(ex: Exception) = when (ex) {
        is FileStorageRateLimitReached -> "Server limit reached! Please wait for [${ex.limitRemainingSeconds}] seconds"
        is FileNotFoundException -> "File could not be found on the multiplayer server"
        is UncivShowableException -> ex.message!! // some of these seem to be translated already, but not all
        else -> "Unhandled problem, [${ex::class.simpleName}] ${ex.message}"
    }
}

private class GameList(
    onSelected: (String) -> Unit
) : VerticalGroup() {

    private val gameDisplays = mutableMapOf<String, GameDisplay>()

    private val events = EventBus.EventReceiver()

    init {
        padTop(10f)
        padBottom(10f)

        events.receive(MultiplayerGameAdded::class) {
            val multiplayerGame = UncivGame.Current.onlineMultiplayer.getGameByName(it.name)
            if (multiplayerGame == null) return@receive
            addGame(it.name, multiplayerGame.preview, multiplayerGame.error, onSelected)
        }
        events.receive(MultiplayerGameNameChanged::class) {
            val gameDisplay = gameDisplays.remove(it.oldName)
            if (gameDisplay == null) return@receive
            gameDisplay.changeName(it.name)
            gameDisplays[it.name] = gameDisplay
            children.sort()
        }
        events.receive(MultiplayerGameDeleted::class) {
            val gameDisplay = gameDisplays.remove(it.name)
            if (gameDisplay == null) return@receive
            gameDisplay.remove()
        }
        for (game in UncivGame.Current.onlineMultiplayer.games) {
            addGame(game.name, game.preview, game.error, onSelected)
        }
    }

    private fun addGame(name: String, preview: GameInfoPreview?, error: Exception?, onSelected: (String) -> Unit) {
        val gameDisplay = GameDisplay(name, preview, error, onSelected)
        gameDisplays[name] = gameDisplay
        addActor(gameDisplay)
        children.sort()
    }
}

private class GameDisplay(
    multiplayerGameName: String,
    preview: GameInfoPreview?,
    error: Exception?,
    private val onSelected: (String) -> Unit
) : Table(), Comparable<GameDisplay> {
    var gameName: String = multiplayerGameName
        private set
    val gameButton = TextButton(gameName, BaseScreen.skin)
    val turnIndicator = createIndicator("OtherIcons/ExclamationMark")
    val errorIndicator = createIndicator("StatIcons/Malcontent")
    val refreshIndicator = createIndicator("EmojiIcons/Turn")
    val statusIndicators = HorizontalGroup()

    val events = EventBus.EventReceiver()

    init {
        padBottom(5f)

        updateTurnIndicator(preview)
        updateErrorIndicator(error != null)
        add(statusIndicators)
        add(gameButton)
        onClick { onSelected(gameName) }

        val isOurGame: (HasMultiplayerGameName) -> Boolean = { it.name == gameName }
        events.receive(MultiplayerGameUpdateStarted::class, isOurGame, {
            statusIndicators.addActor(refreshIndicator)
        })
        events.receive(MultiplayerGameUpdateEnded::class, isOurGame) {
            refreshIndicator.remove()
        }
        events.receive(MultiplayerGameUpdated::class, isOurGame) {
            updateTurnIndicator(it.preview)
            updateErrorIndicator(false)
        }
        events.receive(MultiplayerGameUpdateFailed::class, isOurGame) {
            updateErrorIndicator(true)
        }
    }

    fun changeName(newName: String) {
        gameName = newName
        gameButton.setText(newName)
    }

    private fun updateTurnIndicator(preview: GameInfoPreview?) {
        if (preview?.isUsersTurn() == true) {
            statusIndicators.addActor(turnIndicator)
        } else {
            turnIndicator.remove()
        }
    }

    private fun updateErrorIndicator(hasError: Boolean) {
        if (hasError) {
            statusIndicators.addActor(errorIndicator)
        } else {
            errorIndicator.remove()
        }
    }

    private fun createIndicator(imagePath: String): Actor {
        val image = ImageGetter.getImage(imagePath)
        image.setSize(50f)
        val container = Container(image)
        container.padRight(5f)
        return container
    }

    override fun compareTo(other: GameDisplay): Int = gameName.compareTo(other.gameName)
    override fun equals(other: Any?): Boolean = (other is GameDisplay) && (gameName == other.gameName)
    override fun hashCode(): Int = gameName.hashCode()
}
