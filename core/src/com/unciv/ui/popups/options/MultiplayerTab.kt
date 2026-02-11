package com.unciv.ui.popups.options

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.Constants
import com.unciv.logic.files.IMediaFinder.LabeledSounds
import com.unciv.logic.multiplayer.Multiplayer
import com.unciv.logic.multiplayer.storage.AsyncAuthProvider
import com.unciv.logic.multiplayer.storage.AuthStatus
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.MultiplayerAuthException
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.popups.options.MultiplayerSelectBoxHelpers.RefreshSelectOptions
import com.unciv.ui.popups.AuthPopup
import com.unciv.ui.popups.Popup
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import java.net.URI
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map

internal class MultiplayerTab(
    optionsPopup: OptionsPopup
) : OptionsPopupTab(optionsPopup), MultiplayerSelectBoxHelpers {
    private val mpSettings by settings::multiplayer
    private val mpServer by game.onlineMultiplayer::multiplayerServer

    override fun lateInitialize() {
        addCheckbox(
            "Enable multiplayer status button in singleplayer games",
            mpSettings::statusButtonInSinglePlayer, updateWorld = true
        )

        addSeparator()

        val curRefreshSelect = RefreshSelectOptions(
            mpSettings::currentGameRefreshDelay,
            createRefreshOptions(ChronoUnit.SECONDS, 3, 5),
            createRefreshOptions(ChronoUnit.SECONDS, 10, 20, 30, 60)
        )
        curRefreshSelect.selectBox = addSelectBox("Update status of currently played game every:", curRefreshSelect::value, curRefreshSelect.getItems())

        val allRefreshSelect = RefreshSelectOptions(
            mpSettings::allGameRefreshDelay,
            createRefreshOptions(ChronoUnit.SECONDS, 15, 30),
            createRefreshOptions(ChronoUnit.MINUTES, 1, 2, 5, 15)
        )
        allRefreshSelect.selectBox = addSelectBox("In-game, update status of all games every:", allRefreshSelect::value, allRefreshSelect.getItems())

        addSeparator()

        val turnCheckerSelect = addTurnCheckerOptions()

        val labeledSounds = LabeledSounds() // This is a cache but only used until the SelectBoxes are filled
        fun soundsProvider() = synchronized(labeledSounds) {
            labeledSounds.getLabeledSounds().asFlow().map { MultiplayerSelectBoxHelpers.UncivSoundLabeled(it) }
        }
        val currentSoundProxy = MultiplayerSelectBoxHelpers.UncivSoundProxy(mpSettings::currentGameTurnNotificationSound)
        val otherSoundProxy = MultiplayerSelectBoxHelpers.UncivSoundProxy(mpSettings::otherGameTurnNotificationSound)
        addAsyncSelectBox("Sound notification for when it's your turn in your currently open game:", currentSoundProxy::value, ::soundsProvider) {
            SoundPlayer.play(it.value)
        }
        addAsyncSelectBox("Sound notification for when it's your turn in any other game:", otherSoundProxy::value, ::soundsProvider) {
            SoundPlayer.play(it.value)
        }

        addSeparator()

        addMultiplayerServerOptions(listOfNotNull(curRefreshSelect, allRefreshSelect, turnCheckerSelect))

        super.lateInitialize()
    }

    private fun addMultiplayerServerOptions(toUpdate: Iterable<RefreshSelectOptions>) {
        val connectionToServerButton = "Check connection".toTextButton()

        val textToShowForOnlineMultiplayerAddress = if (Multiplayer.usesCustomServer()) {
            mpSettings.getServer()
        } else {
            "https://"
        }
        val multiplayerServerTextField = UncivTextField("Server address", textToShowForOnlineMultiplayerAddress)
        multiplayerServerTextField.setTextFieldFilter { _, c -> c !in " \r\n\t\\" }
        multiplayerServerTextField.programmaticChangeEvents = true
        val serverIpTable = Table()

        serverIpTable.add("Server address".toLabel().onClick {
            multiplayerServerTextField.text = Gdx.app.clipboard.contents
        }).colspan(2).padBottom(Constants.defaultFontSize / 2.0f).row()

        val errorTextField = "".toLabel(Color.RED)
        errorTextField.isVisible = false

        serverIpTable.add(errorTextField).colspan(2).row()

        multiplayerServerTextField.onChange {
            fixTextFieldUrlOnType(multiplayerServerTextField)

        try {
            // we can't trim on 'fixTextFieldUrlOnType' for reasons
            val uri = URI(multiplayerServerTextField.text.trimEnd('/'))
            if (uri.scheme != "http" && uri.scheme != "https") {
                throw Error("URL must start with http:// or https://")
            }

                // URL has stricter validation than URI
                settings.multiplayer.setServer(uri.toURL().toString())
                errorTextField.isVisible = false
                multiplayerServerTextField.color = Color.GREEN
            } catch (ex: Throwable) {
                errorTextField.setText(ex.message)
                errorTextField.isVisible = true
                multiplayerServerTextField.color = Color.RED
            }

            val isCustomServer = Multiplayer.usesCustomServer()
            connectionToServerButton.isEnabled = isCustomServer

            for (refreshSelect in toUpdate) refreshSelect.update(isCustomServer)
        }

        serverIpTable.add(multiplayerServerTextField)
            .minWidth(optionsPopup.stageToShowOn.width / 3).padRight(Constants.defaultFontSize.toFloat()).growX()

        serverIpTable.add(connectionToServerButton.onClick {
            val popup = Popup(optionsPopup.stageToShowOn).apply {
                addGoodSizedLabel("Awaiting response...").row()
                open(true)
            }

            successfullyConnectedToServer { connectionSuccess, authSuccess ->
                if (authSuccess == false) {
                    popup.close()
                    AuthPopup(optionsPopup.stageToShowOn) { success ->
                        popup.apply {
                            reuseWith(if (success) "Success!" else "Failed!", true)
                            open(true)
                        }
                    }.open(true)
                } else if (connectionSuccess) {
                    if (authSuccess == true) popup.reuseWith("Success!", true)
                    else popup.reuseWith("Auth rejected for unknown reasons, please try again.", true)
                } else {
                    popup.reuseWith("Failed!", true)
                }
    
                if (connectionSuccess) {
                    // because multiplayer server url can get autopatched during isAilve test
                    multiplayerServerTextField.text = settings.multiplayer.getServer()
                }
            }
        }).row()

        if (mpServer.getFeatureSet().authVersion > 0) {
            val passwordTextField = UncivTextField(
                mpSettings.getCurrentServerPassword() ?: "Password"
            )
            val setPasswordButton = "Set password".toTextButton()

            serverIpTable.add("Set password".toLabel()).padTop(16f).colspan(2).row()
            serverIpTable.add(passwordTextField).colspan(2).growX().padBottom(8f).row()

            // initially assume no password
            val authStatusLabel = "Set a password to secure your userId".toLabel()

            val password = mpSettings.getCurrentServerPassword()
            if (password != null) {
                authStatusLabel.setText("Validating your authentication status...")
                val userId = mpSettings.getUserId()
                val server = mpServer
                if (server is AsyncAuthProvider) {
                    server.checkAuthStatusAsync(userId, password) { authStatus ->
                        val newAuthStatusText = when (authStatus) {
                            AuthStatus.UNAUTHORIZED -> "Your current password was rejected from the server"
                            AuthStatus.UNREGISTERED -> "You userId is unregistered! Set password to secure your userId"
                            AuthStatus.VERIFIED -> "Your current password has been succesfully verified"
                            AuthStatus.UNKNOWN -> "Your authentication status could not be determined"
                        }
                        Concurrency.runOnGLThread {
                            authStatusLabel.setText(newAuthStatusText)
                        }
                    }
                } else {
                    Concurrency.run {
                        val authStatus = server.fileStorage()
                            .checkAuthStatus(userId, password)

                        val newAuthStatusText = when (authStatus) {
                            AuthStatus.UNAUTHORIZED -> "Your current password was rejected from the server"
                            AuthStatus.UNREGISTERED -> "You userId is unregistered! Set password to secure your userId"
                            AuthStatus.VERIFIED -> "Your current password has been succesfully verified"
                            AuthStatus.UNKNOWN -> "Your authentication status could not be determined"
                        }

                        Concurrency.runOnGLThread {
                            authStatusLabel.setText(newAuthStatusText)
                        }
                    }
                }
            }

            val passwordStatusTable = Table().apply {
                add(authStatusLabel)
                add(setPasswordButton.onClick {
                    setPassword(passwordTextField.text, optionsPopup)
                }).padLeft(16f)
            }

            serverIpTable.add(passwordStatusTable).colspan(2).row()
        }

        add(serverIpTable).colspan(2).fillX().row()
    }

    private fun addTurnCheckerOptions(): RefreshSelectOptions? {
        // at the moment the notification service only exists on Android
        if (Gdx.app.type != Application.ApplicationType.Android) return null

        addCheckbox("Enable out-of-game turn notifications", mpSettings::turnCheckerEnabled) {
            reopenOptions(force = true)
        }

        if (!mpSettings.turnCheckerEnabled) return null

        val turnCheckerSelect = RefreshSelectOptions(
            mpSettings::turnCheckerDelay,
            createRefreshOptions(ChronoUnit.SECONDS, 30),
            createRefreshOptions(ChronoUnit.MINUTES, 1, 2, 5, 15)
        )
        turnCheckerSelect.selectBox = addSelectBox("Out-of-game, update status of all games every:", turnCheckerSelect::value, turnCheckerSelect.getItems())

        addCheckbox("Show persistent notification for turn notifier service", mpSettings::turnCheckerPersistentNotificationEnabled)

        addSeparator()
        return turnCheckerSelect
    }

    private fun successfullyConnectedToServer(action: (Boolean, Boolean?) -> Unit) {
        Concurrency.run("TestIsAlive") {
            try {
                val server = mpServer
                val connectionSuccess = server.checkServerStatus()
                var authSuccess: Boolean? = null
                if (connectionSuccess) {
                    if (server is AsyncAuthProvider) {
                        server.authenticateAsync(null) { result ->
                            Concurrency.runOnGLThread {
                                action(true, result.getOrNull())
                            }
                        }
                        return@run
                    }
                    try {
                        authSuccess = server.authenticate(null)
                    } catch (_: MultiplayerAuthException) {
                        authSuccess = false
                    } catch (_: Throwable) {
                        // We ignore the exception here, because we handle the failed auth onGLThread
                    }
                }
                launchOnGLThread {
                    action(connectionSuccess, authSuccess)
                }
            } catch (_: Exception) {
                launchOnGLThread {
                    action(false, false)
                }
            }
        }
    }

    private fun setPassword(password: String, optionsPopup: OptionsPopup) {
        if (password.isBlank())
            return

        val popup = Popup(optionsPopup.stageToShowOn).apply {
            addGoodSizedLabel("Awaiting response...").row()
            open(true)
        }

        if (password.length < 6) {
            popup.reuseWith("Password must be at least 6 characters long", true)
            return
        }

        if (mpServer.getFeatureSet().authVersion == 0) {
            popup.reuseWith("This server does not support authentication", true)
            return
        }

        successfullySetPassword(password) { success, ex ->
            if (success) {
                popup.reuseWith(
                    "Password set successfully for server [${mpSettings.getServer()}]",
                    true
                )
            } else {
                if (ex is MultiplayerAuthException) {
                    AuthPopup(optionsPopup.stageToShowOn) { authSuccess ->
                        // If auth was successful, try to set password again
                        if (authSuccess) {
                            popup.close()
                            setPassword(password, optionsPopup)
                        } else {
                            popup.reuseWith("Failed to set password!", true)
                        }
                    }.open(true)
                    return@successfullySetPassword
                }

                val message = when (ex) {
                    is FileStorageRateLimitReached -> "Server limit reached! Please wait for [${ex.limitRemainingSeconds}] seconds"
                    else -> "Failed to set password!"
                }

                popup.reuseWith(message, true)
            }
        }
    }

    private fun successfullySetPassword(password: String, action: (Boolean, Exception?) -> Unit) {
        Concurrency.run("SetPassword") {
            try {
                val server = mpServer
                if (server is AsyncAuthProvider) {
                    server.setPasswordAsync(password) { result ->
                        launchOnGLThread {
                            val throwable = result.exceptionOrNull()
                            val exception = when (throwable) {
                                null -> null
                                is Exception -> throwable
                                else -> Exception(throwable)
                            }
                            action(result.getOrNull() == true, exception)
                        }
                    }
                } else {
                    val setSuccess = server.setPassword(password)
                    launchOnGLThread {
                        action(setSuccess, null)
                    }
                }
            } catch (ex: Exception) {
                launchOnGLThread {
                    action(false, ex)
                }
            }
        }
    }

    private fun fixTextFieldUrlOnType(textField: TextField) {
        var text: String = textField.text
        var cursor: Int = minOf(textField.cursorPosition, text.length)

        val textBeforeCursor: String = text.substring(0, cursor)

        // replace multiple slash with a single one, except when it's a ://
        val multipleSlashes = Regex("(?<!:)/{2,}")
        text = multipleSlashes.replace(text, "/")

        // calculate updated cursor
        cursor = multipleSlashes.replace(textBeforeCursor, "/").length

        // update TextField
        if (text != textField.text) {
            textField.text = text
            textField.cursorPosition = cursor
        }
    }
}
