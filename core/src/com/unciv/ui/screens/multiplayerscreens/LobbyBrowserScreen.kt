package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.scenes.scene2d.ui.*
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.apiv2.LobbyResponse
import com.unciv.ui.screens.pickerscreens.PickerScreen
import com.unciv.ui.popups.Popup
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.enable
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
import com.unciv.ui.components.AutoScrollPane as ScrollPane

/**
 * Screen that should list all open lobbies in the left side and details about a selected lobby in the right side
 *
 * The right side is not fully implemented yet. The right side button should join a lobby.
 * A lobby might be password-protected (=private), in that case a pop-up should ask for the password.
 */
class LobbyBrowserScreen : PickerScreen() {
    private val leftSideTable = Table() // use to list all lobbies in a scrollable way
    private val rightSideTable = Table() // use for details about a lobby

    private val updateListButton = "Update".toTextButton()

    private val noLobbies = "Sorry, no open lobbies at the moment!"

    init {
        setDefaultCloseAction()

        // This will be updated concurrently, but it shows some text to fix the layout
        leftSideTable.add(noLobbies.toLabel()).row()
        leftSideTable.add(updateListButton).padTop(30f).row()

        Concurrency.run("Update lobby list") {
            val listOfOpenLobbies = UncivGame.Current.onlineMultiplayer.api.lobby.list()
            launchOnGLThread {
                refreshLobbyList(listOfOpenLobbies)
            }
        }
        updateListButton.onClick {
            Concurrency.run("Update lobby list") {
                val listOfOpenLobbies = UncivGame.Current.onlineMultiplayer.api.lobby.list()
                launchOnGLThread {
                    refreshLobbyList(listOfOpenLobbies)
                }
            }
        }

        rightSideButton.setText("Join lobby")
        rightSideButton.disable()
        rightSideButton.onClick {
            println("TODO")  // TODO: Join lobby
        }

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
        mainTable.add(ScrollPane(leftSideTable).apply { setScrollingDisabled(true, false) }).height(stage.height * 2 / 3)
        mainTable.add(rightSideTable)
        topTable.add(mainTable).row()
        scrollPane.setScrollingDisabled(false, true)

        rightSideTable.defaults().fillX()
        rightSideTable.defaults().pad(20.0f)
        rightSideTable.add("Lobby details".toLabel()).padBottom(10f).row()
    }

    /**
     * Refresh the list of lobbies (called after finishing the coroutine of the update button)
     */
    private fun refreshLobbyList(lobbies: List<LobbyResponse>) {
        leftSideTable.clear()
        if (lobbies.isEmpty()) {
            leftSideTable.add(noLobbies.toLabel()).row()
            leftSideTable.add(updateListButton).padTop(30f).row()
            return
        }

        lobbies.sortedBy { it.createdAt }
        for (lobby in lobbies) {
            val btn = "${lobby.name} (${lobby.currentPlayers}/${lobby.maxPlayers} players) ${if (lobby.hasPassword) " LOCKED" else ""}".toTextButton()
            btn.onClick {
                println("Button on ${lobby.name}")  // TODO: Select this lobby and handle extra details
                rightSideButton.enable()
                rightSideButton.onClick {
                    println("Join lobby ${lobby.name}")
                }
            }
            leftSideTable.add(btn).row()
        }
        leftSideTable.add(updateListButton).padTop(30f).row()
    }
}
