package com.unciv.ui.popups

import com.unciv.logic.multiplayer.apiv2.IncomingInvite
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.concurrency.Concurrency
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

/**
 * Popup that handles an [IncomingInvite] to a lobby
 */
class LobbyInvitationPopup(
    baseScreen: BaseScreen,
    private val lobbyInvite: IncomingInvite,
    action: (() -> Unit)? = null
) : Popup(baseScreen) {

    private val api = baseScreen.game.onlineMultiplayer.api
    private val setupJob: Job = Concurrency.run {
        val lobby = api.lobby.get(lobbyInvite.lobbyUUID, suppress = true)
        val name = lobby?.name ?: "?"
        Concurrency.runOnGLThread {
            addGoodSizedLabel("You have been invited to the lobby '[$name]' by ${lobbyInvite.from.displayName}. Do you want to accept this invitation? You will be headed to the lobby screen.").colspan(2).row()
            addCloseButton(action = action)
            addOKButton("Accept invitation") {
                // TODO: Implement accepting invitations
                ToastPopup("Accepting invitations is not yet implemented.", baseScreen.stage)
            }
            equalizeLastTwoButtonWidths()
            row()
        }
    }

    suspend fun await() {
        setupJob.join()
    }

    override fun open(force: Boolean) {
        runBlocking { setupJob.join() }
        super.open(force)
    }
}
