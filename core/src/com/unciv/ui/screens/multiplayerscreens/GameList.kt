package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.UncivGame
import com.unciv.logic.GameInfoPreview
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.HasMultiplayerGameName
import com.unciv.logic.multiplayer.MultiplayerGameAdded
import com.unciv.logic.multiplayer.MultiplayerGameDeleted
import com.unciv.logic.multiplayer.MultiplayerGameNameChanged
import com.unciv.logic.multiplayer.MultiplayerGameUpdateEnded
import com.unciv.logic.multiplayer.MultiplayerGameUpdateFailed
import com.unciv.logic.multiplayer.MultiplayerGameUpdateStarted
import com.unciv.logic.multiplayer.MultiplayerGameUpdateSucceeded
import com.unciv.logic.multiplayer.MultiplayerGameUpdated
import com.unciv.logic.multiplayer.isUsersTurn
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.toLabel

class GameList(
    private val onSelected: (String) -> Unit,
    private val useV2: Boolean = false  // set for APIv2 to change GameDisplay to GameDisplayV2
) : VerticalGroup() {

    private val gameDisplays = mutableMapOf<String, GameDisplayBase>()

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
            val gameDisplay = gameDisplays.remove(it.name)
            if (gameDisplay == null) return@receive
            gameDisplay.changeName(it.newName)
            gameDisplays[it.newName] = gameDisplay
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
        val gameDisplay = if (useV2) GameDisplayV2(name, preview, onSelected) else GameDisplay(name, preview, error, onSelected)
        gameDisplays[name] = gameDisplay
        addActor(gameDisplay)
        children.sort()
    }
}

/**
 * Common abstract base class for [GameDisplay] and [GameDisplayV2] allowing more code reuse
 */
private abstract class GameDisplayBase: Table(), Comparable<GameDisplayBase> {
    protected abstract var preview: GameInfoPreview?
    protected abstract var gameName: String

    abstract fun changeName(newName: String)

    protected fun createIndicator(imagePath: String): Actor {
        val image = ImageGetter.getImage(imagePath)
        image.setSize(50f)
        val container = Container(image)
        container.padRight(5f)
        return container
    }

    fun isPlayersTurn() = preview?.isUsersTurn() == true

    override fun compareTo(other: GameDisplayBase): Int =
            if (isPlayersTurn() != other.isPlayersTurn()) // games where it's the player's turn are displayed first, thus must get the lower number
                other.isPlayersTurn().compareTo(isPlayersTurn())
            else gameName.compareTo(other.gameName)
    override fun equals(other: Any?): Boolean = (other is GameDisplayBase) && (gameName == other.gameName)
    override fun hashCode(): Int = gameName.hashCode()
}

private class GameDisplay(
    override var gameName: String,
    override var preview: GameInfoPreview?,
    error: Exception?,
    private val onSelected: (String) -> Unit
) : GameDisplayBase() {

    val gameButton = TextButton(gameName, BaseScreen.skin)
    val turnIndicator = createIndicator("OtherIcons/ExclamationMark")
    val errorIndicator = createIndicator("StatIcons/Malcontent")
    val refreshIndicator = createIndicator("EmojiIcons/Turn")
    val statusIndicators = HorizontalGroup()

    val events = EventBus.EventReceiver()

    init {
        padBottom(5f)

        updateTurnIndicator()
        updateErrorIndicator(error != null)
        add(statusIndicators)
        add(gameButton)
        onClick { onSelected(gameName) }

        val isOurGame: (HasMultiplayerGameName) -> Boolean = { it.name == gameName }
        events.receive(MultiplayerGameUpdateStarted::class, isOurGame) {
            statusIndicators.addActor(refreshIndicator)
        }
        events.receive(MultiplayerGameUpdateEnded::class, isOurGame) {
            refreshIndicator.remove()
        }
        events.receive(MultiplayerGameUpdated::class, isOurGame) {
            preview = it.preview
            updateTurnIndicator()
        }
        events.receive(MultiplayerGameUpdateSucceeded::class, isOurGame) {
            updateErrorIndicator(false)
        }
        events.receive(MultiplayerGameUpdateFailed::class, isOurGame) {
            updateErrorIndicator(true)
        }
    }

    override fun changeName(newName: String) {
        gameName = newName
        gameButton.setText(newName)
    }

    private fun updateTurnIndicator() {
        if (isPlayersTurn()) statusIndicators.addActor(turnIndicator)
        else turnIndicator.remove()
    }

    private fun updateErrorIndicator(hasError: Boolean) {
        if (hasError) statusIndicators.addActor(errorIndicator)
        else errorIndicator.remove()
    }
}

private class GameDisplayV2(
    override var gameName: String,
    override var preview: GameInfoPreview?,
    private val onSelected: (String) -> Unit
): GameDisplayBase() {

    private val gameButton = TextButton(gameName, BaseScreen.skin)
    private val statusIndicators = HorizontalGroup()

    init {
        add(gameName.toLabel())
        add(statusIndicators)
        add(gameButton)
        onClick { onSelected(gameName) }
    }

    override fun changeName(newName: String) {
        // TODO
    }
}
