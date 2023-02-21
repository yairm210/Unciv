package com.unciv.ui.popups

import com.badlogic.gdx.scenes.scene2d.Stage
import com.unciv.UncivGame
import com.unciv.ui.components.BaseScreen
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toTextButton

class AuthPopup(stage: Stage, authSuccessful: ((Boolean) -> Unit)? = null)
    : Popup(stage) {

    constructor(screen: BaseScreen, authSuccessful: ((Boolean) -> Unit)? = null) : this(screen.stage, authSuccessful)

    init {
        val passwordField = UncivTextField.create("Password").apply { isPasswordMode = true }
        val button = "Authenticate".toTextButton()
        button.onClick {
            try {
                UncivGame.Current.onlineMultiplayer.authenticate(passwordField.text)
                authSuccessful?.invoke(true)
                close()
            } catch (ex: Exception) {
                reuseWith("Authentication failed: ${ex.message}")
                row()
                add(passwordField).row()
                add(button)
                return@onClick
            }
        }

        addGoodSizedLabel("The server must authenticate you before proceeding").row()
        add(passwordField).row()
        add(button)
    }
}
