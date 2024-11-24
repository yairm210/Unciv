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
import com.unciv.logic.multiplayer.MultiplayerGameNameChanged
import com.unciv.logic.multiplayer.MultiplayerGameUpdateEnded
import com.unciv.logic.multiplayer.MultiplayerGameUpdateFailed
import com.unciv.logic.multiplayer.MultiplayerGameUpdateStarted
import com.unciv.logic.multiplayer.MultiplayerGameUpdateSucceeded
import com.unciv.logic.multiplayer.MultiplayerGameUpdated
import com.unciv.logic.multiplayer.isUsersTurn
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.setSize

class GameList(
    val onSelected: (String) -> Unit
) : VerticalGroup() {

    private val gameDisplays = mutableMapOf<String, GameDisplay>()

    private val events = EventBus.EventReceiver()

    init {
        padTop(10f)
        padBottom(10f)

        events.receive(MultiplayerGameNameChanged::class) {
            update()
        }

        update()
    }

    fun update(){
        clearChildren()
        gameDisplays.clear()
        for (game in UncivGame.Current.onlineMultiplayer.games) {
            val gameDisplay = GameDisplay(game.name, game.preview, game.error, onSelected)
            gameDisplays[game.name] = gameDisplay
            addActor(gameDisplay)
        }
        children.sort()
    }
}

private class GameDisplay(
    multiplayerGameName: String,
    var preview: GameInfoPreview?,
    error: Throwable?,
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

    private fun updateTurnIndicator() {
        if (isPlayersTurn()) statusIndicators.addActor(turnIndicator)
        else turnIndicator.remove()
    }

    private fun updateErrorIndicator(hasError: Boolean) {
        if (hasError) statusIndicators.addActor(errorIndicator)
        else errorIndicator.remove()
    }

    private fun createIndicator(imagePath: String): Actor {
        val image = ImageGetter.getImage(imagePath)
        image.setSize(50f)
        val container = Container(image)
        container.padRight(5f)
        return container
    }

    fun isPlayersTurn() = preview?.isUsersTurn() == true

    override fun compareTo(other: GameDisplay): Int =
            if (isPlayersTurn() != other.isPlayersTurn()) // games where it's the player's turn are displayed first, thus must get the lower number
                other.isPlayersTurn().compareTo(isPlayersTurn())
            else gameName.compareTo(other.gameName)
    override fun equals(other: Any?): Boolean = (other is GameDisplay) && (gameName == other.gameName)
    override fun hashCode(): Int = gameName.hashCode()
}
