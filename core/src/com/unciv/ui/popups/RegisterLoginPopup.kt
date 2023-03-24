package com.unciv.ui.popups

import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.ApiVersion
import com.unciv.logic.multiplayer.apiv2.ApiException
import com.unciv.models.translations.tr
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread

/**
 * Popup that asks for a username and password that should be used to login/register to APIv2 servers
 *
 * [UncivGame.Current.onlineMultiplayer] must be set to a [ApiVersion.APIv2] server,
 * otherwise this pop-up will not work.
 */
class RegisterLoginPopup(private val stage: Stage, authSuccessful: ((Boolean) -> Unit)? = null) : Popup(stage) {

    private val multiplayer = UncivGame.Current.onlineMultiplayer

    init {
        val negativeButtonStyle = BaseScreen.skin.get("negative", TextButton.TextButtonStyle::class.java)

        if (!multiplayer.isInitialized() || multiplayer.apiVersion != ApiVersion.APIv2) {
            Log.error("Uninitialized online multiplayer instance or ${multiplayer.baseUrl} not APIv2 compatible")
            addGoodSizedLabel("Uninitialized online multiplayer instance or ${multiplayer.baseUrl} not APIv2 compatible").colspan(2).row()
            addCloseButton(style=negativeButtonStyle) { authSuccessful?.invoke(false) }.growX().padRight(8f)
        } else {
            val usernameField = UncivTextField.create("Username")
            val passwordField = UncivTextField.create("Password")

            val loginButton = "Login existing".toTextButton()
            val registerButton = "Register new".toTextButton()

            loginButton.onClick {
                val popup = createPopup(force = true)
                Concurrency.run {
                    try {
                        val success = UncivGame.Current.onlineMultiplayer.api.auth.login(
                            usernameField.text, passwordField.text
                        )
                        launchOnGLThread {
                            popup.close()
                            close()
                            authSuccessful?.invoke(success)
                        }
                    } catch (e: ApiException) {
                        launchOnGLThread {
                            popup.close()
                            close()
                            InfoPopup(stage, "Failed to login with existing account".tr() + ":\n${e.localizedMessage}") {
                                authSuccessful?.invoke(false)
                            }
                        }
                    }
                }
            }

            registerButton.onClick {
                val popup = createPopup(force = true)
                Concurrency.run {
                    try {
                        UncivGame.Current.onlineMultiplayer.api.accounts.register(
                            usernameField.text, usernameField.text, passwordField.text
                        )
                        UncivGame.Current.onlineMultiplayer.api.auth.login(
                            usernameField.text, passwordField.text
                        )
                        launchOnGLThread {
                            popup.close()
                            close()
                            InfoPopup(stage, "Successfully registered new account".tr()) {
                                authSuccessful?.invoke(true)
                            }
                        }
                    } catch (e: ApiException) {
                        launchOnGLThread {
                            popup.close()
                            close()
                            InfoPopup(stage, "Failed to register new account".tr() + ":\n${e.localizedMessage}") {
                                authSuccessful?.invoke(false)
                            }
                        }
                    }
                }
            }

            addGoodSizedLabel("It looks like you are playing for the first time on ${multiplayer.baseUrl} with this device. Please login if you have played on this server, otherwise you can register a new account as well.").colspan(2).row()
            add(usernameField).colspan(2).growX().pad(16f, 0f, 16f, 0f).row()
            add(passwordField).colspan(2).growX().pad(16f, 0f, 16f, 0f).row()
            addCloseButton(style=negativeButtonStyle) { authSuccessful?.invoke(false) }.growX().padRight(8f)
            add(loginButton).growX().padLeft(8f)
            add(registerButton).growX().padLeft(8f)
        }
    }

    private fun createPopup(msg: String? = null, force: Boolean = false): Popup {
        val popup = Popup(stage)
        popup.addGoodSizedLabel(msg?: "Working...")
        popup.open(force)
        return popup
    }
}
