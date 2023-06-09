package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Disposable
import com.unciv.Constants
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.MultiplayerGameCanBeLoaded
import com.unciv.logic.multiplayer.apiv2.GameOverviewResponse
import com.unciv.models.translations.tr
import com.unciv.ui.components.ChatButton
import com.unciv.ui.components.PencilButton
import com.unciv.ui.components.extensions.formatShort
import com.unciv.ui.components.extensions.onActivation
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Table listing the recently played open games for APIv2 multiplayer games
 */
class GameListV2(private val screen: BaseScreen, private val onSelected: (GameOverviewResponse) -> Unit) : Table(BaseScreen.skin), Disposable {
    private val disposables = mutableListOf<Disposable>()
    private val noGames = "No recently played games here".toLabel()
    private val games = mutableListOf<GameOverviewResponse>()
    private val events = EventBus.EventReceiver()

    init {
        add(noGames).row()
        triggerUpdate()

        events.receive(MultiplayerGameCanBeLoaded::class, null) {
            Concurrency.run {
                val updatedGame = screen.game.onlineMultiplayer.api.game.head(UUID.fromString(it.gameInfo.gameId), suppress = true)
                if (updatedGame != null) {
                    Concurrency.runOnGLThread {
                        games.removeAll { game -> game.gameUUID.toString() == it.gameInfo.gameId }
                        games.add(updatedGame)
                        recreate()
                    }
                }
            }
        }
    }

    private fun addGame(game: GameOverviewResponse) {
        add(game.name.toTextButton().onClick { onSelected(game) }).padRight(10f).padBottom(5f)
        add(convertTime(game.lastActivity)).padRight(10f).padBottom(5f)

        add(ChatButton().apply { onClick {
            Log.debug("Opening chat room ${game.chatRoomUUID} from game list")
            val popup = Popup(screen.stage)
            val chatMessageList = ChatMessageList(
                true,
                Pair(ChatRoomType.Game, game.name),
                game.chatRoomUUID,
                screen.game.onlineMultiplayer
            )
            disposables.add(chatMessageList)
            popup.add(ChatTable(chatMessageList)).padBottom(10f).row()
            popup.addCloseButton()
            popup.open(force = true)
        } }).padBottom(5f)
        add(PencilButton().apply { onClick {
            GameEditPopup(screen, game).open()
        } }).padRight(5f).padBottom(5f)
    }

    companion object {
        private fun convertTime(time: Instant): String {
            return "[${Duration.between(time, Instant.now()).formatShort()}] ago".tr()
        }
    }

    /**
     * Recreate the table of this game list using the supplied list of open games
     */
    fun recreate() {
        clearChildren()
        if (games.isEmpty()) {
            add(noGames).row()
            return
        }
        for (game in games.sortedBy { it.lastActivity }.reversed()) {
            addGame(game)
            row()
        }
    }

    /**
     * Detach updating the list of games in another coroutine
     */
    private fun triggerUpdate() {
        Concurrency.run("Update game list") {
            while (stage == null) {
                delay(20)  // fixes race condition and null pointer exception in access to `stage`
            }
            val listOfOpenGames = InfoPopup.wrap(stage) {
                screen.game.onlineMultiplayer.api.game.list()
            }
            if (listOfOpenGames != null) {
                Concurrency.runOnGLThread {
                    games.clear()
                    listOfOpenGames.sortedBy { it.lastActivity }.reversed().forEach { games.add(it) }
                    recreate()
                }
            }
        }
    }

    private class GameEditPopup(screen: BaseScreen, game: GameOverviewResponse) : Popup(screen) {
        init {
            add(game.name.toLabel(fontSize = Constants.headingFontSize)).colspan(2).padBottom(5f).row()
            add("Last played")
            add(convertTime(game.lastActivity)).padBottom(5f).row()
            add("Last player")
            add(game.lastPlayer.displayName).padBottom(5f).row()
            add("Max players")
            add(game.maxPlayers.toString()).padBottom(5f).row()
            add("Remove / Resign".toTextButton().apply { onActivation {
                ToastPopup("This functionality is not implemented yet.", screen).open(force = true)
            } }).colspan(2).row()
            addCloseButton().colspan(2).row()
        }
    }

    /**
     * Dispose children who need to be cleaned up properly
     */
    override fun dispose() {
        disposables.forEach { it.dispose() }
    }
}
