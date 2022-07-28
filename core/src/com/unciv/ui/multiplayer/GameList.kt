package com.unciv.ui.multiplayer

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
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.setSize

class GameList(
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
        }
        events.receive(MultiplayerGameUpdateSucceeded::class, isOurGame) {
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
