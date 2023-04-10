package com.unciv.ui.screens.multiplayerscreens

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.components.SearchButton
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.extensions.addSeparatorVertical
import com.unciv.ui.components.extensions.keyShortcuts
import com.unciv.ui.components.extensions.onActivation
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.popups.InfoPopup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import java.util.UUID

class LobbyInviteTable(private val lobbyUUID: UUID, private val base: BaseScreen): Table() {
    init {
        add("Invite player".toLabel(fontSize = Constants.headingFontSize)).colspan(2).pad(5f).padBottom(10f)
        row()

        val nameField = UncivTextField.create("Search player")
        val searchButton = SearchButton()
        searchButton.onActivation {
            Log.debug("Searching for player '%s'", nameField.text)
            Concurrency.run {
                val response = InfoPopup.wrap(base.stage) {
                    base.game.onlineMultiplayer.api.account.lookup(nameField.text)
                }
                if (response != null) {
                    Concurrency.runOnGLThread {
                        Log.debug("Looked up '%s' as '%s'", response.uuid, response.username)
                        invite(response.uuid)
                    }
                }
            }
        }

        searchButton.keyShortcuts.add(KeyCharAndCode.RETURN)
        add(nameField).padLeft(5f).padRight(5f)
        add(searchButton).padRight(5f)
        row()

        addSeparatorVertical(Color.DARK_GRAY).colspan(2).pad(5f).row()

    }

    private fun invite(friendUUID: UUID) {
        InfoPopup.load(base.stage) {
            base.game.onlineMultiplayer.api.invite.new(friendUUID, lobbyUUID)
        }
    }
}
