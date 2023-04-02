package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.apiv2.LobbyResponse
import com.unciv.ui.components.RefreshButton
import com.unciv.ui.popups.Popup
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.popups.CreateLobbyPopup
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import com.unciv.ui.components.AutoScrollPane as ScrollPane

/**
 * Screen that should list all open lobbies in the left side and details about a selected lobby in the right side
 *
 * The right side is not fully implemented yet. The right side button should join a lobby.
 * A lobby might be password-protected (=private), in that case a pop-up should ask for the password.
 */
class LobbyBrowserScreen : PickerScreen() {
    private val lobbyBrowserTable = LobbyBrowserTable(this)
    private val rightSideTable = Table() // use for details about a lobby

    private val newLobbyButton = "Open lobby".toTextButton()
    private val noLobbySelected = "Select a lobby to show details"

    init {
        setDefaultCloseAction()

        // The functionality of joining a lobby will be added on-demand in [refreshLobbyList]
        rightSideButton.setText("Join lobby")
        rightSideButton.disable()

        val tab = Table()
        val helpButton = "Help".toTextButton()
        helpButton.onClick {
            val helpPopup = Popup(this)
            helpPopup.addGoodSizedLabel("This should become a lobby browser.").row()  // TODO
            helpPopup.addCloseButton()
            helpPopup.open()
        }
        tab.add(helpButton)
        tab.x = (stage.width - helpButton.width)
        tab.y = (stage.height - helpButton.height)
        stage.addActor(tab)

        val mainTable = Table()
        mainTable.add(ScrollPane(lobbyBrowserTable).apply { setScrollingDisabled(true, false) }).height(stage.height * 2 / 3)
        mainTable.add(rightSideTable)
        topTable.add(mainTable).row()
        scrollPane.setScrollingDisabled(false, true)

        rightSideTable.defaults().fillX()
        rightSideTable.defaults().pad(20.0f)
        rightSideTable.add(noLobbySelected.toLabel()).padBottom(10f).row()
    }

    /**
     * Update the right side table with details about a specific lobby
     */
    private fun updateRightSideTable(selectedLobby: LobbyResponse) {
        rightSideTable.clear()
        // TODO: This texts need translation
        rightSideTable.add("${selectedLobby.name} (${selectedLobby.currentPlayers}/${selectedLobby.maxPlayers} players)".toLabel()).padBottom(10f).row()
        if (selectedLobby.hasPassword) {
            rightSideTable.add("This lobby requires a password to join.".toLabel()).row()
        }
        rightSideTable.add("Created: ${selectedLobby.createdAt}.".toLabel()).row()
        rightSideTable.add("Owner: ${selectedLobby.owner.displayName}".toLabel()).row()
    }
}
