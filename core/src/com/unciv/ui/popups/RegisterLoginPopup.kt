package com.unciv.ui.popups

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.EventListener
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.UncivShowableException
import com.unciv.logic.multiplayer.ApiVersion
import com.unciv.models.translations.tr
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.extensions.activate
import com.unciv.ui.components.extensions.keyShortcuts
import com.unciv.ui.components.extensions.onActivation
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import com.unciv.utils.launchOnGLThread

/**
 * Popup that asks for a username and password that should be used to login/register to APIv2 servers
 *
 * [UncivGame.Current.onlineMultiplayer] must be set to a [ApiVersion.APIv2] server,
 * otherwise this pop-up will not work.
 */
class RegisterLoginPopup(private val base: BaseScreen, confirmUsage: Boolean = false, private val authSuccessful: ((Boolean) -> Unit)? = null) : Popup(base.stage) {

    private val multiplayer = UncivGame.Current.onlineMultiplayer
    private val usernameField = UncivTextField.create("Username")
    private val passwordField = UncivTextField.create("Password")
    private val loginButton = "Login".toTextButton()
    private val registerButton = "Register".toTextButton()
    private val listener: EventListener

    private var confirmationPopup: Popup? = null

    init {
        /** Simple listener class for key presses on ENTER keys to trigger the login button */
        class SimpleEnterListener : InputListener() {
            override fun keyUp(event: InputEvent?, keycode: Int): Boolean {
                if (keycode in listOf(KeyCharAndCode.RETURN.code, KeyCharAndCode.NUMPAD_ENTER.code)) {
                    loginButton.activate()
                }
                return super.keyUp(event, keycode)
            }
        }

        listener = SimpleEnterListener()

        passwordField.isPasswordMode = true
        passwordField.setPasswordCharacter('*')

        loginButton.onActivation {
            if (usernameField.text == "") {
                stage.keyboardFocus = usernameField
            } else if (passwordField.text == "") {
                stage.keyboardFocus = passwordField
            } else {
                stage.removeListener(listener)
                login()
            }
        }
        registerButton.onClick {
            if (usernameField.text == "") {
                stage.keyboardFocus = usernameField
            } else if (passwordField.text == "") {
                stage.keyboardFocus = passwordField
            } else {
                stage.removeListener(listener)
                register()
            }
        }

        if (confirmUsage) {
            confirmationPopup = askConfirmUsage {
                build()
            }
            confirmationPopup?.open()
        } else {
            build()
        }
    }

    override fun close() {
        stage?.removeListener(listener)
        super.close()
    }

    /**
     * Build the popup stage
     */
    private fun build() {
        val negativeButtonStyle = BaseScreen.skin.get("negative", TextButton.TextButtonStyle::class.java)

        if (!multiplayer.isInitialized() || multiplayer.apiVersion != ApiVersion.APIv2) {
            Log.error("Uninitialized online multiplayer instance or ${multiplayer.baseUrl} not APIv2 compatible")
            addGoodSizedLabel("Uninitialized online multiplayer instance or ${multiplayer.baseUrl} not APIv2 compatible").colspan(2).row()
            addCloseButton(style=negativeButtonStyle) {
                stage?.removeListener(listener)
                authSuccessful?.invoke(false)
            }.growX().padRight(8f)
        } else {
            stage.addListener(listener)

            addGoodSizedLabel("It looks like you are playing for the first time on ${multiplayer.baseUrl} with this device. Please login if you have played on this server, otherwise you can register a new account as well.").colspan(3).row()
            add(usernameField).colspan(3).growX().pad(16f, 0f, 16f, 0f).row()
            add(passwordField).colspan(3).growX().pad(16f, 0f, 16f, 0f).row()
            addCloseButton {
                stage?.removeListener(listener)
                authSuccessful?.invoke(false)
            }.growX().padRight(8f)
            add(registerButton).growX().padLeft(8f)
            add(loginButton).growX().padLeft(8f).apply { keyShortcuts.add(KeyCharAndCode.RETURN) }
        }
    }

    private fun askConfirmUsage(block: () -> Unit): Popup {
        val playerId = UncivGame.Current.settings.multiplayer.userId
        val popup = Popup(base)
        popup.addGoodSizedLabel("By using the new multiplayer servers, you overwrite your existing player ID. Games on other servers will not be accessible anymore, unless the player ID is properly restored. Keep your player ID safe before proceeding:").colspan(2)
        popup.row()
        popup.addGoodSizedLabel(playerId)
        popup.addButton("Copy user ID") {
            Gdx.app.clipboard.contents = base.game.settings.multiplayer.userId
            ToastPopup("UserID copied to clipboard", base).open(force = true)
        }
        popup.row()
        val cell = popup.addCloseButton(Constants.OK, action = block)
        cell.colspan(2)
        cell.actor.keyShortcuts.add(KeyCharAndCode.ESC)
        cell.actor.keyShortcuts.add(KeyCharAndCode.BACK)
        cell.actor.keyShortcuts.add(KeyCharAndCode.RETURN)
        return popup
    }

    private fun createPopup(msg: String? = null, force: Boolean = false): Popup {
        val popup = Popup(base.stage)
        popup.addGoodSizedLabel(msg?: "Working...")
        popup.open(force)
        return popup
    }

    private fun login() {
        val popup = createPopup(force = true)
        Concurrency.run {
            try {
                val success = UncivGame.Current.onlineMultiplayer.api.auth.login(
                    usernameField.text, passwordField.text
                )
                launchOnGLThread {
                    Log.debug("Updating username and password after successfully authenticating")
                    UncivGame.Current.settings.multiplayer.userName = usernameField.text
                    UncivGame.Current.settings.multiplayer.passwords[UncivGame.Current.onlineMultiplayer.baseUrl] = passwordField.text
                    UncivGame.Current.settings.save()
                    popup.close()
                    stage?.removeListener(listener)
                    close()
                    authSuccessful?.invoke(success)
                }
            } catch (e: UncivShowableException) {
                launchOnGLThread {
                    popup.close()
                    InfoPopup(base.stage, e.localizedMessage) {
                        stage?.addListener(listener)
                        authSuccessful?.invoke(false)
                    }
                }
            }
        }
    }

    private fun register() {
        val popup = createPopup(force = true)
        Concurrency.run {
            try {
                UncivGame.Current.onlineMultiplayer.api.account.register(
                    usernameField.text, usernameField.text, passwordField.text
                )
                UncivGame.Current.onlineMultiplayer.api.auth.login(
                    usernameField.text, passwordField.text
                )
                launchOnGLThread {
                    Log.debug("Updating username and password after successfully authenticating")
                    UncivGame.Current.settings.multiplayer.userName = usernameField.text
                    UncivGame.Current.settings.multiplayer.passwords[UncivGame.Current.onlineMultiplayer.baseUrl] = passwordField.text
                    UncivGame.Current.settings.save()
                    popup.close()
                    close()
                    InfoPopup(base.stage, "Successfully registered new account".tr()) {
                        stage?.removeListener(listener)
                        authSuccessful?.invoke(true)
                    }
                }
            } catch (e: UncivShowableException) {
                launchOnGLThread {
                    popup.close()
                    InfoPopup(base.stage, e.localizedMessage) {
                        stage?.addListener(listener)
                        authSuccessful?.invoke(false)
                    }
                }
            }
        }
    }
}
