package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Disposable
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.apiv2.GameOverviewResponse
import com.unciv.models.translations.tr
import com.unciv.ui.components.ChatButton
import com.unciv.ui.components.PencilButton
import com.unciv.ui.components.extensions.formatShort
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.ToastPopup
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

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
    }

    private fun addGame(game: GameOverviewResponse) {
        add(game.name.toTextButton().onClick { onSelected(game) }).padRight(10f).padBottom(5f)
        val time = "[${Duration.between(game.lastActivity, Instant.now()).formatShort()}] ago".tr()
        add(time).padRight(10f).padBottom(5f)
        add(game.lastPlayer.username).padRight(10f).padBottom(5f)
        add(game.gameDataID.toString()).padRight(10f).padBottom(5f)

        add(PencilButton().apply { onClick {
            ToastPopup("Renaming game ${game.gameUUID} not implemented yet", screen.stage)
        } }).padRight(5f).padBottom(5f)
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
            popup.innerTable.add(ChatTable(chatMessageList)).padBottom(10f).row()
            popup.addCloseButton()
            popup.open(force = true)
        } }).padBottom(5f)
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

    /**
     * Dispose children who need to be cleaned up properly
     */
    override fun dispose() {
        disposables.forEach { it.dispose() }
    }

}
