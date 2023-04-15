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
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.newgamescreen.IPreviousScreen
import com.unciv.ui.screens.newgamescreen.NationPickerPopup
import com.unciv.utils.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import kotlin.math.roundToInt

/**
 * List of players in an APIv2 lobby screen
 */
class LobbyPlayerList(
    private val lobbyUUID: UUID,
    private var editable: Boolean,
    private var api: ApiV2,
    startPlayers: List<AccountResponse> = listOf(),
    private val base: IPreviousScreen
) : Table() {
    private val mutex = Mutex()  // used to synchronize changes to the players list
    internal val players: MutableList<LobbyPlayer> = startPlayers.map { LobbyPlayer(it) }.toMutableList()

    private val addBotButton = "+".toLabel(Color.LIGHT_GRAY, 30)
        .apply { this.setAlignment(Align.center) }
        .surroundWithCircle(50f, color = Color.GRAY)
        .onClick {
            runBlocking {
                mutex.withLock {
                    players.add(LobbyPlayer(null, Constants.random))
                }
            }
            recreate()
        }

    init {
        defaults().expandX()
        recreate()
    }

    /**
     * Add the specified player to the player list and recreate the view
     */
    internal fun addPlayer(player: AccountResponse): Boolean {
        runBlocking {
            mutex.withLock {
                players.add(LobbyPlayer(player))
            }
        }
        recreate()
        return true
    }

    /**
     * Remove the specified player from the player list and recreate the view
     */
    internal fun removePlayer(player: UUID): Boolean {
        var modified: Boolean
        runBlocking {
            mutex.withLock {
                modified = players.removeAll { it.account?.uuid == player }
            }
        }
        recreate()
        return modified
    }

    /**
     * Recreate the table of players based on the list of internal player representations
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

        runBlocking {
            mutex.withLock {
                for (i in players.indices) {
                    row()
                    val movements = VerticalGroup()
                    movements.space(5f)
                    movements.addActor("↑".toLabel(fontSize = Constants.headingFontSize).onClick {
                        if (runBlocking {
                            var changed = false
                            mutex.withLock {
                                if (i > 0) {
                                    changed = true
                                    val above = players[i - 1]
                                    players[i - 1] = players[i]
                                    players[i] = above
                                }
                            }
                            changed
                        }) {
                            recreate()
                        }
                    })
                    movements.addActor("↓".toLabel(fontSize = Constants.headingFontSize).onClick {
                        if (runBlocking {
                            var changed = false
                            mutex.withLock {
                                if (i < players.size - 1) {
                                    changed = true
                                    val below = players[i + 1]
                                    players[i + 1] = players[i]
                                    players[i] = below

                                }
                            }
                            changed
                        }) {
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
                        if (!player.isAI) {
                            runBlocking {
                                success = true == InfoPopup.wrap(stage) {
                                    api.lobby.kick(lobbyUUID, player.account!!.uuid)
                                }
                            }
                        }
                        if (success) {
                            runBlocking {
                                mutex.withLock {
                                    success = players.remove(player)
                                }
                            }
                        }
                        Log.debug("Removing player %s [%s]: %s", player.account, i, if (success) "success" else "failure")
                        recreate()
                    }
                    if (editable) {
                        add(kickButton)
                    }

                    if (i < players.size - 1) {
                        row()
                        addSeparator(color = Color.DARK_GRAY).width(0.8f * width).pad(5f)
                    }
                }
            }
        }

        row()
        if (editable) {
            add(addBotButton).colspan(columns).fillX().center()
        } else {
            add("Non-human players are not shown in this list.".toLabel(fontSize = (Constants.defaultFontSize * 0.75).roundToInt())).colspan(columns).fillX().padTop(20f).center()
        }
        updateParameters()
    }

    /**
     * Update game parameters to reflect changes in the list of players
     */
    internal fun updateParameters() {
        base.gameSetupInfo.gameParameters.players = players.map { it.to() }.toMutableList()
    }

    private fun reassignRemovedModReferences() {
        runBlocking {
            mutex.withLock {
                for (player in players) {
                    if (!base.ruleset.nations.containsKey(player.chosenCiv) || base.ruleset.nations[player.chosenCiv]!!.isCityState)
                        player.chosenCiv = Constants.random
                }
            }
        }
    }

    /**
     * Create clickable icon and nation name for some [LobbyPlayer] based on its index in [players], where clicking creates [NationPickerPopup]
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
            NationPickerPopup(p, 0.45f * stage.width, base as BaseScreen, base, false, availableCivilisations) {
                players[index].chosenCiv = p.chosenCiv
                updateParameters()
                recreate()
            }.open()
        }
        return nationTable
    }

    /**
     * Refresh the view of the human players based on the [currentPlayers] response from the server
     */
    internal fun updateCurrentPlayers(currentPlayers: List<AccountResponse>) {
        runBlocking {
            mutex.withLock {
                val humanPlayers = players.filter { !it.isAI }.map { it.account!! }
                val toBeRemoved = mutableListOf<LobbyPlayer>()
                for (oldPlayer in players) {
                    if (!oldPlayer.isAI && oldPlayer.account!!.uuid !in currentPlayers.map { it.uuid }) {
                        toBeRemoved.add(oldPlayer)
                    }
                }
                for (r in toBeRemoved) {
                    players.remove(r)
                }
                for (newPlayer in currentPlayers) {
                    if (newPlayer !in humanPlayers) {
                        players.add(LobbyPlayer(newPlayer))
                    }
                }
            }
        }
        recreate()
    }

}
