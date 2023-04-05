package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.event.EventBus
import com.unciv.logic.multiplayer.apiv2.GameOverviewResponse
import com.unciv.ui.components.ChatButton
import com.unciv.ui.components.PencilButton
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.popups.ToastPopup
import com.unciv.utils.concurrency.Concurrency
import kotlinx.coroutines.delay

/**
 * Table listing the recently played open games for APIv2 multiplayer games
 */
class GameListV2(private val screen: BaseScreen, private val onSelected: (String) -> Unit) : Table() {

    private val noGames = "No recently played games here".toLabel()
    private val games = mutableListOf<GameOverviewResponse>()
    private val events = EventBus.EventReceiver()

    init {
        // TODO: Add event handling
        add(noGames).row()
        triggerUpdate()
    }

    private fun addGame(game: GameOverviewResponse) {
        // TODO: Determine if it's the current turn, then add an indicator for that

        add(game.name)
        add(game.lastActivity.toString())
        add(game.lastPlayer.username)
        add(game.gameDataID.toString())
        add(game.gameUUID.toString())
        add(game.gameDataID.toString())

        add(PencilButton().apply { onClick {
            ToastPopup("Renaming game ${game.gameUUID} not implemented yet", screen.stage)
        } })
        add(ChatButton().apply { onClick {
            ToastPopup("Opening chat room ${game.chatRoomUUID} not implemented yet", screen.stage)
        } })
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
        games.sortedBy { it.lastActivity }
        for (game in games.reversed()) {
            addGame(game)
            row()
        }
    }

    /**
     * Detach updating the list of games in another coroutine
     */
    fun triggerUpdate() {
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
                    listOfOpenGames.forEach { games.add(it) }
                    recreate()
                }
            }
        }
    }

}
