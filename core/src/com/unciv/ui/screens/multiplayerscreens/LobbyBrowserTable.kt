package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.multiplayer.apiv2.LobbyResponse
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.AskTextPopup
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import kotlinx.coroutines.delay

/**
 * Table listing all available open lobbies and allow joining them by clicking on them
 */
class LobbyBrowserTable(private val screen: BaseScreen): Table() {

    private val noLobbies = "Sorry, no open lobbies at the moment!".toLabel()
    private val enterLobbyPasswordText = "This lobby requires a password to join. Please enter it below:"

    init {
        add(noLobbies).row()
        triggerUpdate()
    }

    /**
     * Open a lobby by joining it (may ask for a passphrase for protected lobbies)
     */
    private fun joinLobby(lobby: LobbyResponse) {
        Log.debug("Trying to join lobby '${lobby.name}' (UUID ${lobby.uuid}) ...")
        if (lobby.hasPassword) {
            val popup = AskTextPopup(
                screen,
                enterLobbyPasswordText,
                ImageGetter.getImage("OtherIcons/LockSmall").apply { this.color = Color.BLACK }
                    .surroundWithCircle(80f),
                maxLength = 120
            ) {
                InfoPopup.load(stage) {
                    screen.game.onlineMultiplayer.api.lobby.join(lobby.uuid)
                    Concurrency.runOnGLThread {
                        screen.game.pushScreen(LobbyScreen(lobby))
                    }
                }
            }
            popup.open()
        } else {
            InfoPopup.load(stage) {
                screen.game.onlineMultiplayer.api.lobby.join(lobby.uuid)
                Concurrency.runOnGLThread {
                    screen.game.pushScreen(LobbyScreen(lobby))
                }
            }
        }
    }

    /**
     * Recreate the table of this lobby browser using the supplied list of lobbies
     */
    fun recreate(lobbies: List<LobbyResponse>) {
        clearChildren()
        if (lobbies.isEmpty()) {
            add(noLobbies).row()
            return
        }

        lobbies.sortedBy { it.createdAt }
        for (lobby in lobbies.reversed()) {
            // TODO: The button may be styled with icons and the texts may be translated
            val btn = "${lobby.name} (${lobby.currentPlayers}/${lobby.maxPlayers} players) ${if (lobby.hasPassword) " LOCKED" else ""}".toTextButton()
            btn.onClick {
                joinLobby(lobby)
            }
            add(btn).row()
        }
    }

    /**
     * Detach updating the list of lobbies in another coroutine
     */
    fun triggerUpdate() {
        Concurrency.run("Update lobby list") {
            while (stage == null) {
                delay(20)  // fixes race condition and null pointer exception in access to `stage`
            }
            val listOfOpenLobbies = InfoPopup.wrap(stage) {
                screen.game.onlineMultiplayer.api.lobby.list()
            }
            if (listOfOpenLobbies != null) {
                Concurrency.runOnGLThread {
                    recreate(listOfOpenLobbies)
                }
            }
        }
    }

}
