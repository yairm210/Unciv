package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.VerticalGroup
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.models.metadata.Player
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.newgamescreen.IPreviousScreen
import com.unciv.ui.screens.newgamescreen.NationPickerPopup
import com.unciv.utils.Log
import java.util.*

/**
 * List of players in an APIv2 lobby screen
 */
class LobbyPlayerList(
    private val lobbyUUID: UUID,
    private val mutablePlayers: MutableList<LobbyPlayer> = mutableListOf(),
    private val base: IPreviousScreen,
    private val update: () -> Unit
) : Table() {
    internal val players: List<LobbyPlayer> = mutablePlayers

    private val addBotButton = "+".toLabel(Color.LIGHT_GRAY, 30)
        .apply { this.setAlignment(Align.center) }
        .surroundWithCircle(50f, color = Color.GRAY)
        .onClick {
            mutablePlayers.add(LobbyPlayer(null, Constants.random))
            recreate()
        }

    init {
        defaults().expandX()
        recreate()
    }

    /**
     * Recreate the table of players based on the list of internal player representations
     */
    fun recreate() {
        clearChildren()
        if (players.isEmpty()) {
            val label = "No players here yet".toLabel()
            label.setAlignment(Align.center)
            add(label).fillX().fillY().center().padBottom(15f).row()
            add(addBotButton)
            return
        }

        for (i in players.indices) {
            row()
            val movements = VerticalGroup()
            movements.space(5f)
            movements.addActor("↑".toLabel(fontSize = Constants.headingFontSize).onClick { Log.error("Click up not implemented yet") })
            movements.addActor("↓".toLabel(fontSize = Constants.headingFontSize).onClick { Log.error("Click down not implemented yet") })
            add(movements)

            val player = players[i]
            add(getNationTable(player))
            if (player.isAI) {
                add("AI".toLabel())
            } else {
                add(player.account!!.username.toLabel())
            }

            val kickButton = "❌".toLabel(Color.SCARLET, Constants.headingFontSize).apply { this.setAlignment(Align.center) }
            // kickButton.surroundWithCircle(Constants.headingFontSize.toFloat(), color = color)
            kickButton.onClick {
                if (!player.isAI) {
                    ToastPopup("Kicking human players has not been implemented yet.", stage)  // TODO: Implement this
                }
                val success = mutablePlayers.remove(player)
                Log.debug("Removing player %s [%s]: %s", player.account, i, if (success) "success" else "failure")
                recreate()
            }
            add(kickButton)

            if (i < players.size - 1) {
                row()
                addSeparator(color = Color.DARK_GRAY).width(0.8f * width).pad(5f)
            }
        }

        row()
        add(addBotButton).colspan(columns).fillX().center()
    }

    /**
     * Create clickable icon and nation name for some [LobbyPlayer], where clicking creates [NationPickerPopup]
     */
    private fun getNationTable(player: LobbyPlayer): Table {
        val nationTable = Table()
        val nationImage =
            if (player.chosenCiv == Constants.random)
                ImageGetter.getRandomNationPortrait(40f)
            else ImageGetter.getNationPortrait(base.ruleset.nations[player.chosenCiv]!!, 40f)
        nationTable.add(nationImage).padRight(10f)
        nationTable.add(player.chosenCiv.toLabel()).padRight(5f)
        nationTable.touchable = Touchable.enabled
        val availableCivilisations = base.ruleset.nations.values.asSequence()
            .filter { it.isMajorCiv() }
            .filter { it.name == player.chosenCiv || players.none { player -> player.chosenCiv == it.name } }
        nationTable.onClick {
            val p = player.to()
            NationPickerPopup(p, 0.45f * stage.width, base as BaseScreen, base, false, availableCivilisations) {
                player.chosenCiv = p.chosenCiv
                recreate()
                update()
            }.open()
        }
        return nationTable
    }

}
