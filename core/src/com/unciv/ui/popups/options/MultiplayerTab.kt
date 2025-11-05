package com.unciv.ui.popups.options

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.files.IMediaFinder
import com.unciv.logic.multiplayer.Multiplayer
import com.unciv.logic.multiplayer.storage.AuthStatus
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.MultiplayerAuthException
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSettings.GameSetting
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.format
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.popups.AuthPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.options.SettingsSelect.SelectItem
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import com.unciv.utils.toGdxArray
import java.time.Duration
import java.time.temporal.ChronoUnit

internal class MultiplayerTab(
    optionsPopup: OptionsPopup
) : Table(BaseScreen.skin) {
    init {
        pad(10f)
        defaults().pad(5f)

        val settings = optionsPopup.settings

        optionsPopup.addCheckbox(
            this, "Enable multiplayer status button in singleplayer games",
            settings.multiplayer::statusButtonInSinglePlayer, updateWorld = true
        )

        addSeparator()

        val curRefreshSelect = RefreshSelect(
            "Update status of currently played game every:",
            createRefreshOptions(ChronoUnit.SECONDS, 3, 5),
            createRefreshOptions(ChronoUnit.SECONDS, 10, 20, 30, 60),
            GameSetting.MULTIPLAYER_CURRENT_GAME_REFRESH_DELAY,
            settings
        )
        addSelectAsSeparateTable(this, curRefreshSelect)

        val allRefreshSelect = RefreshSelect(
            "In-game, update status of all games every:",
            createRefreshOptions(ChronoUnit.SECONDS, 15, 30),
            createRefreshOptions(ChronoUnit.MINUTES, 1, 2, 5, 15),
            GameSetting.MULTIPLAYER_ALL_GAME_REFRESH_DELAY,
            settings
        )
        addSelectAsSeparateTable(this, allRefreshSelect)

        addSeparator()

        // at the moment the notification service only exists on Android
        val turnCheckerSelect: RefreshSelect?
        if (Gdx.app.type == Application.ApplicationType.Android) {
            turnCheckerSelect = addTurnCheckerOptions(this, optionsPopup)
            addSeparator()
        } else {
            turnCheckerSelect = null
        }

        val sounds = IMediaFinder.LabeledSounds().getLabeledSounds()
        addSelectAsSeparateTable(
            this, SettingsSelect(
                "Sound notification for when it's your turn in your currently open game:",
                sounds,
                GameSetting.MULTIPLAYER_CURRENT_GAME_TURN_NOTIFICATION_SOUND,
                settings
            )
        )

        addSelectAsSeparateTable(
            this, SettingsSelect(
                "Sound notification for when it's your turn in any other game:",
                sounds,
                GameSetting.MULTIPLAYER_OTHER_GAME_TURN_NOTIFICATION_SOUND,
                settings
            )
        )

        addSeparator()

        addMultiplayerServerOptions(
            this, optionsPopup,
            listOfNotNull(curRefreshSelect, allRefreshSelect, turnCheckerSelect)
        )
    }

    private fun addMultiplayerServerOptions(
        tab: Table,
        optionsPopup: OptionsPopup,
        toUpdate: Iterable<RefreshSelect>
    ) {
        val settings = optionsPopup.settings

        val connectionToServerButton = "Check connection".toTextButton()

        val textToShowForOnlineMultiplayerAddress = if (Multiplayer.usesCustomServer()) {
            settings.multiplayer.getServer()
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
        multiplayerServerTextField.onChange {
            fixTextFieldUrlOnType(multiplayerServerTextField)
            // we can't trim on 'fixTextFieldUrlOnType' for reasons
            settings.multiplayer.setServer(multiplayerServerTextField.text.trimEnd('/'))

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
            }
        }).row()

        if (UncivGame.Current.onlineMultiplayer.multiplayerServer.getFeatureSet().authVersion > 0) {
            val passwordTextField = UncivTextField(
                settings.multiplayer.getCurrentServerPassword() ?: "Password"
            )
            val setPasswordButton = "Set password".toTextButton()

            serverIpTable.add("Set password".toLabel()).padTop(16f).colspan(2).row()
            serverIpTable.add(passwordTextField).colspan(2).growX().padBottom(8f).row()

            // initially assume no password
            val authStatusLabel = "Set a password to secure your userId".toLabel()

            val password = settings.multiplayer.getCurrentServerPassword()
            if (password != null) {
                authStatusLabel.setText("Validating your authentication status...")
                Concurrency.run {
                    val userId = settings.multiplayer.getUserId()
                    val authStatus = UncivGame.Current.onlineMultiplayer.multiplayerServer.fileStorage()
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

            val passwordStatusTable = Table().apply {
                add(authStatusLabel)
                add(setPasswordButton.onClick {
                    setPassword(passwordTextField.text, optionsPopup)
                }).padLeft(16f)
            }

            serverIpTable.add(passwordStatusTable).colspan(2).row()
        }

        tab.add(serverIpTable).colspan(2).fillX().row()
    }

    private fun addTurnCheckerOptions(
        tab: Table,
        optionsPopup: OptionsPopup
    ): RefreshSelect? {
        val settings = optionsPopup.settings

        optionsPopup.addCheckbox(
            tab,
            "Enable out-of-game turn notifications",
            settings.multiplayer::turnCheckerEnabled
        )

        if (!settings.multiplayer.turnCheckerEnabled) return null

        val turnCheckerSelect = RefreshSelect(
            "Out-of-game, update status of all games every:",
            createRefreshOptions(ChronoUnit.SECONDS, 30),
            createRefreshOptions(ChronoUnit.MINUTES, 1, 2, 5, 15),
            GameSetting.MULTIPLAYER_TURN_CHECKER_DELAY,
            settings
        )
        addSelectAsSeparateTable(tab, turnCheckerSelect)


        optionsPopup.addCheckbox(
            tab, "Show persistent notification for turn notifier service",
            settings.multiplayer::turnCheckerPersistentNotificationEnabled
        )

        return turnCheckerSelect
    }

    private fun successfullyConnectedToServer(action: (Boolean, Boolean?) -> Unit) {
        Concurrency.run("TestIsAlive") {
            try {
                val connectionSuccess = UncivGame.Current.onlineMultiplayer.multiplayerServer.checkServerStatus()
                var authSuccess: Boolean? = null
                if (connectionSuccess) {
                    try {
                        authSuccess = UncivGame.Current.onlineMultiplayer.multiplayerServer.authenticate(null)
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

        if (UncivGame.Current.onlineMultiplayer.multiplayerServer.getFeatureSet().authVersion == 0) {
            popup.reuseWith("This server does not support authentication", true)
            return
        }

        successfullySetPassword(password) { success, ex ->
            if (success) {
                popup.reuseWith(
                    "Password set successfully for server [${optionsPopup.settings.multiplayer.getServer()}]",
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
                val setSuccess = UncivGame.Current.onlineMultiplayer.multiplayerServer.setPassword(password)
                launchOnGLThread {
                    action(setSuccess, null)
                }
            } catch (ex: Exception) {
                launchOnGLThread {
                    action(false, ex)
                }
            }
        }
    }

    private inner class RefreshSelect(
        labelText: String,
        extraCustomServerOptions: List<SelectItem<Duration>>,
        dropboxOptions: List<SelectItem<Duration>>,
        setting: GameSetting,
        settings: GameSettings
    ) : SettingsSelect<Duration>(labelText, getInitialOptions(extraCustomServerOptions, dropboxOptions), setting, settings) {
        private val customServerItems = (extraCustomServerOptions + dropboxOptions).toGdxArray()
        private val dropboxItems = dropboxOptions.toGdxArray()

        fun update(isCustomServer: Boolean) {
            if (isCustomServer && items.size != customServerItems.size) {
                replaceItems(customServerItems)
            } else if (!isCustomServer && items.size != dropboxItems.size) {
                replaceItems(dropboxItems)
            }
        }
    }

    private fun getInitialOptions(
        extraCustomServerOptions: List<SelectItem<Duration>>,
        dropboxOptions: List<SelectItem<Duration>>
    ): Iterable<SelectItem<Duration>> {
        val customServerItems = (extraCustomServerOptions + dropboxOptions).toGdxArray()
        val dropboxItems = dropboxOptions.toGdxArray()
        return if (Multiplayer.usesCustomServer()) customServerItems else dropboxItems
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

    private fun createRefreshOptions(unit: ChronoUnit, vararg options: Long): List<SelectItem<Duration>> {
        return options.map {
            val duration = Duration.of(it, unit)
            SelectItem(duration.format(), duration)
        }
    }

    private fun addSelectAsSeparateTable(tab: Table, settingsSelect: SettingsSelect<*>) {
        val table = Table()
        settingsSelect.addTo(table)
        tab.add(table).growX().fillX().row()
    }
}
