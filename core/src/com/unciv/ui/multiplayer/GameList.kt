package com.unciv.ui.multiplayer

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.unciv.UncivGame
import com.unciv.logic.HasGameTurnData
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.HasMultiplayerGame
import com.unciv.logic.multiplayer.MultiplayerGame
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
    onSelected: (MultiplayerGame) -> Unit
) : VerticalGroup() {

    private val gameDisplays = mutableMapOf<MultiplayerGame, GameDisplay>()

    private val events = EventBus.EventReceiver()

    init {
        padTop(10f)
        padBottom(10f)

        events.receive(MultiplayerGameAdded::class) {
            addGame(it.game, onSelected)
        }
        events.receive(MultiplayerGameNameChanged::class) {
            val gameDisplay = gameDisplays.get(it.game)
            if (gameDisplay == null) return@receive
            children.sort()
        }
        events.receive(MultiplayerGameDeleted::class) {
            val gameDisplay = gameDisplays.remove(it.game)
            if (gameDisplay == null) return@receive
            gameDisplay.remove()
        }

        for (game in UncivGame.Current.multiplayer.games) {
            addGame(game, onSelected)
        }
    }

    private fun addGame(game: MultiplayerGame, onSelected: (MultiplayerGame) -> Unit) {
        val gameDisplay = GameDisplay(game, onSelected)
        gameDisplays[game] = gameDisplay
        addActor(gameDisplay)
        children.sort()
    }
}

private class GameDisplay(
    val game: MultiplayerGame,
    private val onSelected: (MultiplayerGame) -> Unit
) : Table(), Comparable<GameDisplay> {
    val gameButton = TextButton(game.name, BaseScreen.skin)
    val turnIndicator = createIndicator("OtherIcons/ExclamationMark")
    val errorIndicator = createIndicator("StatIcons/Malcontent")
    val refreshIndicator = createIndicator("EmojiIcons/Turn")
    val statusIndicators = HorizontalGroup()

    val events = EventBus.EventReceiver()

    init {
        padBottom(5f)

        updateTurnIndicator(game.status)
        updateErrorIndicator(game.error != null)
        add(statusIndicators)
        add(gameButton)
        onClick { onSelected(game) }

        val isOurGame: (HasMultiplayerGame) -> Boolean = { it.game == game }
        events.receive(MultiplayerGameUpdateStarted::class, isOurGame, {
            statusIndicators.addActor(refreshIndicator)
        })
        events.receive(MultiplayerGameUpdateEnded::class, isOurGame) {
            refreshIndicator.remove()
        }
        events.receive(MultiplayerGameNameChanged::class, isOurGame) {
            gameButton.setText(it.game.name)
        }
        events.receive(MultiplayerGameUpdated::class, isOurGame) {
            updateTurnIndicator(it.status)
        }
        events.receive(MultiplayerGameUpdateSucceeded::class, isOurGame) {
            updateErrorIndicator(false)
        }
        events.receive(MultiplayerGameUpdateFailed::class, isOurGame) {
            updateErrorIndicator(true)
        }
    }


    private fun updateTurnIndicator(status: HasGameTurnData?) {
        if (status?.isUsersTurn() == true) {
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

    override fun compareTo(other: GameDisplay): Int = game.name.compareTo(other.game.name)
    override fun equals(other: Any?): Boolean = (other is GameDisplay) && (game == other.game)
    override fun hashCode(): Int = game.hashCode()
}
