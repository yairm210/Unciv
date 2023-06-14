package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.logic.multiplayer.apiv2.ApiException
import com.unciv.logic.multiplayer.apiv2.ApiStatusCode
import com.unciv.logic.multiplayer.apiv2.LobbyResponse
import com.unciv.ui.components.ArrowButton
import com.unciv.ui.components.LockButton
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.AskTextPopup
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import kotlinx.coroutines.delay

/**
 * Table listing all available open lobbies and allow joining them by clicking on them
 */
internal class LobbyBrowserTable(private val screen: BaseScreen, private val lobbyJoinCallback: (() -> Unit)): Table(BaseScreen.skin) {

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
                ImageGetter.getImage("OtherIcons/LockSmall")
                    .apply { this.color = Color.BLACK }
                    .surroundWithCircle(80f),
                maxLength = 120
            ) {
                InfoPopup.load(stage) {
                    try {
                        screen.game.onlineMultiplayer.api.lobby.join(lobby.uuid, it)
                        Concurrency.runOnGLThread {
                            lobbyJoinCallback()
                            screen.game.pushScreen(LobbyScreen(lobby))
                        }
                    } catch (e: ApiException) {
                        if (e.error.statusCode != ApiStatusCode.MissingPrivileges) {
                            throw e
                        }
                        Concurrency.runOnGLThread {
                            Popup(stage).apply {
                                addGoodSizedLabel("Invalid password")
                                addCloseButton()
                            }.open(force = true)
                        }
                    }
                }
            }
            popup.open()
        } else {
            InfoPopup.load(stage) {
                screen.game.onlineMultiplayer.api.lobby.join(lobby.uuid)
                Concurrency.runOnGLThread {
                    lobbyJoinCallback()
                    screen.game.pushScreen(LobbyScreen(lobby))
                }
            }
        }
    }

    /**
     * Recreate the table of this lobby browser using the supplied list of lobbies
     */
    internal fun recreate(lobbies: List<LobbyResponse>) {
        clearChildren()
        if (lobbies.isEmpty()) {
            add(noLobbies).row()
            return
        }

        for (lobby in lobbies.sortedBy { it.createdAt }.reversed()) {
            add(lobby.name).padRight(15f)
            add("${lobby.currentPlayers}/${lobby.maxPlayers}").padRight(10f)
            if (lobby.hasPassword) {
                add(LockButton().onClick { joinLobby(lobby) }).padBottom(5f).row()
            } else {
                add(ArrowButton().onClick { joinLobby(lobby) }).padBottom(5f).row()
            }
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
