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
    internal val players: MutableList<LobbyPlayer> = mutableListOf(),
    private val base: IPreviousScreen,
    private val update: () -> Unit
) : Table() {
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
            add(label).fillX().fillY().center()
            return
        }

        for (i in 0 until players.size) {
            row()
            val movements = VerticalGroup()
            movements.space(5f)
            movements.addActor("↑".toLabel(fontSize = Constants.headingFontSize).onClick { Log.error("Click up not implemented yet") })
            movements.addActor("↓".toLabel(fontSize = Constants.headingFontSize).onClick { Log.error("Click down not implemented yet") })
            add(movements)

            val player = players[i]
            add(getNationTable(player.to()))
            if (player.isAI) {
                add("AI".toLabel())
            } else {
                add(player.account!!.username.toLabel())
            }

            val kickButton = "❌".toLabel(Color.SCARLET, Constants.headingFontSize).apply { this.setAlignment(Align.center) }
            // kickButton.surroundWithCircle(Constants.headingFontSize.toFloat(), color = color)
            kickButton.onClick { ToastPopup("Kicking players has not been implemented yet", stage) }
            add(kickButton)

            if (i < players.size - 1) {
                row()
                addSeparator(color = Color.DARK_GRAY).width(0.8f * width).pad(5f)
            }
        }

        row()
        val addPlayerButton = "+".toLabel(Color.LIGHT_GRAY, 30)
            .apply { this.setAlignment(Align.center) }
            .surroundWithCircle(50f, color = Color.GRAY)
            .onClick {
                ToastPopup("Adding AI players has not been implemented yet", stage)
            }
        add(addPlayerButton).colspan(columns).fillX().center()
    }

    /**
     * Create clickable icon and nation name for some [Player], where clicking creates [NationPickerPopup]
     */
    private fun getNationTable(player: Player): Table {
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
            .filter { it.name == player.chosenCiv || base.gameSetupInfo.gameParameters.players.none { player -> player.chosenCiv == it.name } }
        nationTable.onClick {
            NationPickerPopup(player, 0.45f * stage.width, { update() }, base as BaseScreen, base, false, availableCivilisations).open()
            update()
        }
        return nationTable
    }

}
