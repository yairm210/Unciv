package com.unciv.ui.popups

import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.multiplayer.ApiVersion
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.multiplayerscreens.LobbyScreen
import com.unciv.utils.Log

/**
 * Variant of [Popup] used to ask the questions related to opening a new [ApiVersion.APIv2] multiplayer lobby
 */
class CreateLobbyPopup(private val base: BaseScreen) : Popup(base.stage) {
    private var requirePassword: Boolean = false
    private val nameField = UncivTextField.create("Lobby name", "New lobby").apply { this.maxLength = 64 }
    private val passwordField = UncivTextField.create("Password", "").apply { this.maxLength = 64 }
    private val checkbox = "Require password".toCheckBox(false) {
        requirePassword = it
        recreate()
    }

    init {
        if (base.game.onlineMultiplayer.apiVersion != ApiVersion.APIv2) {
            Log.error("Popup to create a new lobby without a valid APIv2 server! This is not supported!")
        }
        recreate()
        open()
    }

    private fun recreate() {
        innerTable.clearChildren()
        addGoodSizedLabel("Create new lobby", Constants.headingFontSize).center().colspan(2).row()

        addGoodSizedLabel("Please give your new lobby a recognizable name:").colspan(2).row()
        add(nameField).growX().colspan(2).row()

        addGoodSizedLabel("You can choose to open a public lobby, where everyone may join, or protect it with a password.").colspan(2).row()
        checkbox.isDisabled = false
        checkbox.align(Align.left)
        add(checkbox).colspan(2).row()

        if (requirePassword) {
            add(passwordField).growX().colspan(2).row()
        }

        addCloseButton()
        addOKButton(action = ::onClose).row()
        equalizeLastTwoButtonWidths()
        open()
    }

    private fun onClose() {
        Log.debug("Creating a new lobby '%s'", nameField.text)
        val response = InfoPopup.load(base.stage) {
            base.game.onlineMultiplayer.api.lobby.open(nameField.text, if (requirePassword) passwordField.text else null)
        }
        if (response != null) {
            base.game.pushScreen(LobbyScreen(response))
        }
    }

}
