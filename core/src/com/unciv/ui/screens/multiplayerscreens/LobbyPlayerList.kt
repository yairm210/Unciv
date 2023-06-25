package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.multiplayer.apiv2.AccountResponse
import com.unciv.logic.multiplayer.apiv2.ApiV2
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.onClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.newgamescreen.MapOptionsInterface
import com.unciv.ui.screens.newgamescreen.NationPickerPopup
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import java.util.UUID

/**
 * List of players in an APIv2 lobby screen
 *
 * The instantiation of this class **must** be on the GL thread or lead to undefined behavior and crashes.
 *
 * Implementation detail: the access to various internal attributes, e.g. [playersImpl],
 * is not protected by locking mechanisms, since all accesses to them **must** go through
 * the GL render thread, which is single-threaded (at least on Desktop; if you encounter
 * any errors/crashes on other platforms, this means this assumption was probably wrong).
 *
 * See https://github.com/libgdx/libgdx/blob/master/backends/gdx-backend-android/src/com/badlogic/gdx/backends/android/AndroidGraphics.java#L496
 * and https://github.com/libgdx/libgdx/blob/master/backends/gdx-backend-lwjgl3/src/com/badlogic/gdx/backends/lwjgl3/Lwjgl3Application.java#L207
 * for details why it's certain that the coroutines are executed in-order (even though the order is not strictly defined).
 */
class LobbyPlayerList(
    private val lobbyUUID: UUID,
    private var editable: Boolean,
    private val me: UUID,  // the currently logged-in player UUID
    private var api: ApiV2,
    private val update: (() -> Unit)? = null,  // use for signaling player changes via buttons to the caller
    startPlayers: List<AccountResponse> = listOf(),
    private val base: MapOptionsInterface
) : Table() {
    // Access to this attribute **must** go through the GL render thread for synchronization after init
    private val playersImpl: MutableList<LobbyPlayer> = startPlayers.map { LobbyPlayer(it) }.toMutableList()
    /** Don't cache the [players] property, but get it freshly from this class every time */
    internal val players: List<LobbyPlayer>
        get() = playersImpl.toList()

    private val addBotButton = "+".toLabel(Color.LIGHT_GRAY, 30)
        .apply { this.setAlignment(Align.center) }
        .surroundWithCircle(50f, color = Color.GRAY)
        .onClick {
            playersImpl.add(LobbyPlayer(null, Constants.random))
            recreate()
            update?.invoke()
        }

    init {
        defaults().expandX()
        recreate()
    }

    /**
     * Add the specified player to the player list and recreate the view
     *
     * This method **must** be called on the GL thread or lead to undefined behavior and crashes.
     */
    internal fun addPlayer(player: AccountResponse): Boolean {
        playersImpl.add(LobbyPlayer(player))
        recreate()
        return true
    }

    /**
     * Remove the specified player from the player list and recreate the view
     *
     * This method **must** be called on the GL thread or lead to undefined behavior and crashes.
     */
    internal fun removePlayer(player: UUID): Boolean {
        val modified = playersImpl.removeAll { it.account?.uuid == player }
        recreate()
        return modified
    }

    /**
     * Recreate the table of players based on the list of internal player representations
     *
     * This method **must** be called on the GL thread or lead to undefined behavior and crashes.
     */
    fun recreate() {
        clearChildren()
        reassignRemovedModReferences()
        if (players.isEmpty()) {
            val label = "No players here yet".toLabel()
            label.setAlignment(Align.center)
            add(label).fillX().fillY().center().padBottom(15f).row()
            if (editable) {
                add(addBotButton)
            }
            return
        }

        for (i in players.indices) {
            row()
            val movements = VerticalGroup()
            movements.space(5f)
            movements.addActor("↑".toLabel(fontSize = Constants.headingFontSize).onClick {
                if (i > 0) {
                    val above = players[i - 1]
                    playersImpl[i - 1] = players[i]
                    playersImpl[i] = above
                    recreate()
                }
            })
            movements.addActor("↓".toLabel(fontSize = Constants.headingFontSize).onClick {
                if (i < players.size - 1) {
                    val below = players[i + 1]
                    playersImpl[i + 1] = players[i]
                    playersImpl[i] = below
                    recreate()
                }
            })
            if (editable) {
                add(movements)
            }

            val player = players[i]
            add(getNationTable(i))
            if (player.isAI) {
                add("AI".toLabel())
            } else {
                add(player.account!!.username.toLabel())
            }

            val kickButton = "❌".toLabel(Color.SCARLET, Constants.headingFontSize).apply { this.setAlignment(Align.center) }
            // kickButton.surroundWithCircle(Constants.headingFontSize.toFloat(), color = color)
            kickButton.onClick {
                var success = true
                Concurrency.run {
                    if (!player.isAI) {
                        success = true == InfoPopup.wrap(stage) {
                            api.lobby.kick(lobbyUUID, player.account!!.uuid)
                        }
                    }
                    Concurrency.runOnGLThread {
                        if (success) {
                            success = playersImpl.remove(player)
                        } else {
                            base.updateTables()
                        }
                        Log.debug("Removing player %s [%s]: %s", player.account, i, if (success) "success" else "failure")
                        recreate()
                        update?.invoke()
                    }
                }
            }
            if (editable && me != player.account?.uuid) {
                add(kickButton)
            }

            if (i < players.size - 1) {
                row()
                addSeparator(color = Color.DARK_GRAY).width(0.8f * width).pad(5f)
            }
        }

        row()
        if (editable) {
            add(addBotButton).colspan(columns).fillX().center()
        } else {
            add(
                "Non-human players are not shown in this list.".toLabel(
                    alignment = Align.center,
                    fontSize = Constants.smallFontSize
                )
            ).colspan(columns).growX().padTop(20f).center()
        }
        updateParameters()
    }

    /**
     * Update game parameters to reflect changes in the list of players
     *
     * This method **must** be called on the GL thread or lead to undefined behavior and crashes.
     */
    private fun updateParameters() {
        base.gameSetupInfo.gameParameters.players = playersImpl.map { it.to() }.toMutableList()
    }

    /**
     * This method **must** be called on the GL thread or lead to undefined behavior and crashes.
     */
    private fun reassignRemovedModReferences() {
        for (player in players) {
            if (!base.ruleset.nations.containsKey(player.chosenCiv) || base.ruleset.nations[player.chosenCiv]!!.isCityState) {
                player.chosenCiv = Constants.random
            }
        }
    }

    /**
     * Create clickable icon and nation name for some [LobbyPlayer] based on its index in [players], where clicking creates [NationPickerPopup]
     *
     * This method **must** be called on the GL thread or lead to undefined behavior and crashes.
     */
    private fun getNationTable(index: Int): Table {
        val player = players[index]
        val nationTable = Table()
        val nationImage =
            if (player.chosenCiv == Constants.random)
                ImageGetter.getRandomNationPortrait(40f)
            else ImageGetter.getNationPortrait(base.ruleset.nations[player.chosenCiv]!!, 40f)
        nationTable.add(nationImage).padRight(10f)
        nationTable.add(player.chosenCiv.toLabel()).padRight(5f)
        nationTable.touchable = Touchable.enabled
        val availableCivilisations = base.ruleset.nations.values.asSequence()
            .filter { it.isMajorCiv }
            .filter { it.name == player.chosenCiv || players.none { player -> player.chosenCiv == it.name } }
        nationTable.onClick {
            val p = player.to()
            NationPickerPopup(p, 0.45f * stage.width, base as BaseScreen, base, false, { availableCivilisations }) {
                players[index].chosenCiv = p.chosenCiv
                updateParameters()
                recreate()
            }.open()
        }
        return nationTable
    }

    /**
     * Refresh the view of the human players based on the [currentPlayers] response from the server
     *
     * This method **must** be called on the GL thread or lead to undefined behavior and crashes.
     */
    internal fun updateCurrentPlayers(currentPlayers: List<AccountResponse>) {
        val humanPlayers = players.filter { !it.isAI }.map { it.account!! }
        val toBeRemoved = mutableListOf<LobbyPlayer>()
        for (oldPlayer in players) {
            if (!oldPlayer.isAI && oldPlayer.account!!.uuid !in currentPlayers.map { it.uuid }) {
                toBeRemoved.add(oldPlayer)
            }
        }
        for (r in toBeRemoved) {
            playersImpl.remove(r)
        }
        for (newPlayer in currentPlayers) {
            if (newPlayer !in humanPlayers) {
                playersImpl.add(LobbyPlayer(newPlayer))
            }
        }
        recreate()
    }

}
