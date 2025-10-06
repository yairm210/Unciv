package com.unciv.ui.popups

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.screens.basescreen.BaseScreen

class AuthPopup(stage: Stage, private val authSuccessful: ((Boolean) -> Unit)? = null)
    : Popup(stage) {

    constructor(screen: BaseScreen, authSuccessful: ((Boolean) -> Unit)? = null) : this(screen.stage, authSuccessful)

    private val passwordField: UncivTextField = UncivTextField("Password")
    private val button: TextButton = "Authenticate".toTextButton()
    private val negativeButtonStyle: TextButton.TextButtonStyle =
        BaseScreen.skin.get("negative", TextButton.TextButtonStyle::class.java)

    init {
        button.onClick {
            try {
                UncivGame.Current.onlineMultiplayer.multiplayerServer.authenticate(passwordField.text)
                authSuccessful?.invoke(true)
                close()
            } catch (_: Exception) {
                clear()
                addComponents("Authentication failed")
            }
        }
        addComponents("Please enter your server password")
    }
    
    private fun addComponents(headerLabelText: String) {
        addGoodSizedLabel(headerLabelText).colspan(2).row()
        add(passwordField).colspan(2).growX().pad(16f, 0f, 16f, 0f).row()
        addCloseButton(style = negativeButtonStyle) {
            authSuccessful?.invoke(false)
        }.growX().padRight(8f)
        add(button).growX().padLeft(8f)
    }
}
