package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.multiplayer.apiv2.LobbyResponse
import com.unciv.ui.components.RefreshButton
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

/**
 * Table listing all available open lobbies and allow joining them by clicking on them
 */
class LobbyBrowserTable(private val screen: BaseScreen): Table() {

    private val updateButton = RefreshButton().onClick {
        triggerUpdate()
    }
    private val noLobbies = "Sorry, no open lobbies at the moment!".toLabel()
    private val enterLobbyPasswordText = "This lobby requires a password to join. Please enter it below:"

    init {
        add(noLobbies).row()
        add(updateButton).padTop(30f).row()
        triggerUpdate()
    }

    /**
     * Open a lobby by joining it (may ask for a passphrase for protected lobbies)
     */
    private fun openLobby(lobby: LobbyResponse) {
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
                    // TODO: screen.game.onlineMultiplayer.api.lobby.join
                    Concurrency.runOnGLThread {
                        screen.game.pushScreen(LobbyScreen(lobby))
                    }
                }
            }
            popup.open()
        } else {
            InfoPopup.load(stage) {
                // TODO: screen.game.onlineMultiplayer.api.lobby.join
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
            add(updateButton).padTop(30f).row()
            return
        }

        lobbies.sortedBy { it.createdAt }
        for (lobby in lobbies.reversed()) {
            // TODO: The button may be styled with icons and the texts may be translated
            val btn = "${lobby.name} (${lobby.currentPlayers}/${lobby.maxPlayers} players) ${if (lobby.hasPassword) " LOCKED" else ""}".toTextButton()
            btn.onClick {
                openLobby(lobby)
            }
            add(btn).row()
        }
        add(updateButton).padTop(30f).row()
    }

    /**
     * Detach updating the list of lobbies in another coroutine
     */
    fun triggerUpdate() {
        Concurrency.run("Update lobby list") {
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
