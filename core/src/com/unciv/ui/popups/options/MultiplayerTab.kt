package com.unciv.ui.popups.options

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.UncivGame
import com.unciv.logic.files.IMediaFinder
import com.unciv.logic.multiplayer.Multiplayer
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.MultiplayerAuthException
import com.unciv.models.metadata.GameSettings
import com.unciv.models.metadata.GameSettings.GameSetting
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.format
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.toGdxArray
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.onChange
import com.unciv.ui.components.input.onClick
import com.unciv.ui.popups.AuthPopup
import com.unciv.ui.popups.Popup
import com.unciv.ui.popups.options.SettingsSelect.SelectItem
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.utils.Concurrency
import com.unciv.utils.launchOnGLThread
import java.time.Duration
import java.time.temporal.ChronoUnit

fun multiplayerTab(
    optionsPopup: OptionsPopup
): Table {
    val tab = Table(BaseScreen.skin)
    tab.pad(10f)
    tab.defaults().pad(5f)

    val settings = optionsPopup.settings

    optionsPopup.addCheckbox(
        tab, "Enable multiplayer status button in singleplayer games",
        settings.multiplayer::statusButtonInSinglePlayer, updateWorld = true
    )

    addSeparator(tab)

    val curRefreshSelect = RefreshSelect(
        "Update status of currently played game every:",
        createRefreshOptions(ChronoUnit.SECONDS, 3, 5),
        createRefreshOptions(ChronoUnit.SECONDS, 10, 20, 30, 60),
        GameSetting.MULTIPLAYER_CURRENT_GAME_REFRESH_DELAY,
        settings
    )
    addSelectAsSeparateTable(tab, curRefreshSelect)

    val allRefreshSelect = RefreshSelect(
        "In-game, update status of all games every:",
        createRefreshOptions(ChronoUnit.SECONDS, 15, 30),
        createRefreshOptions(ChronoUnit.MINUTES, 1, 2, 5, 15),
        GameSetting.MULTIPLAYER_ALL_GAME_REFRESH_DELAY,
        settings
    )
    addSelectAsSeparateTable(tab, allRefreshSelect)

    addSeparator(tab)

    // at the moment the notification service only exists on Android
    val turnCheckerSelect: RefreshSelect?
    if (Gdx.app.type == Application.ApplicationType.Android) {
        turnCheckerSelect = addTurnCheckerOptions(tab, optionsPopup)
        addSeparator(tab)
    } else {
        turnCheckerSelect = null
    }

    val sounds = IMediaFinder.LabeledSounds().getLabeledSounds()
    addSelectAsSeparateTable(tab, SettingsSelect("Sound notification for when it's your turn in your currently open game:",
        sounds,
        GameSetting.MULTIPLAYER_CURRENT_GAME_TURN_NOTIFICATION_SOUND,
        settings
    ))

    addSelectAsSeparateTable(tab, SettingsSelect("Sound notification for when it's your turn in any other game:",
        sounds,
        GameSetting.MULTIPLAYER_OTHER_GAME_TURN_NOTIFICATION_SOUND,
        settings
    ))

    addSeparator(tab)

    addMultiplayerServerOptions(tab, optionsPopup,
        listOfNotNull(curRefreshSelect, allRefreshSelect, turnCheckerSelect)
    )

    return tab
}

private fun addMultiplayerServerOptions(
    tab: Table,
    optionsPopup: OptionsPopup,
    toUpdate: Iterable<RefreshSelect>
) {
    val settings = optionsPopup.settings

    val connectionToServerButton = "Check connection to server".toTextButton()

    val textToShowForOnlineMultiplayerAddress = if (Multiplayer.usesCustomServer()) {
        settings.multiplayer.server
    } else {
        "https://"
    }
    val multiplayerServerTextField = UncivTextField("Server address", textToShowForOnlineMultiplayerAddress)
    multiplayerServerTextField.setTextFieldFilter { _, c -> c !in " \r\n\t\\" }
    multiplayerServerTextField.programmaticChangeEvents = true
    val serverIpTable = Table()

    serverIpTable.add("Server address".toLabel().onClick {
        multiplayerServerTextField.text = Gdx.app.clipboard.contents
        }).colspan(2).row()
    multiplayerServerTextField.onChange {
        fixTextFieldUrlOnType(multiplayerServerTextField)
        // we can't trim on 'fixTextFieldUrlOnType' for reasons
        settings.multiplayer.server = multiplayerServerTextField.text.trimEnd('/')

        val isCustomServer = Multiplayer.usesCustomServer()
        connectionToServerButton.isEnabled = isCustomServer

        for (refreshSelect in toUpdate) refreshSelect.update(isCustomServer)
    }

    serverIpTable.add(multiplayerServerTextField)
        .minWidth(optionsPopup.stageToShowOn.width / 2)
        .colspan(2).growX().padBottom(8f).row()

    serverIpTable.add(connectionToServerButton.onClick {
        val popup = Popup(optionsPopup.stageToShowOn).apply {
            addGoodSizedLabel("Awaiting response...").row()
            open(true)
        }

        successfullyConnectedToServer { connectionSuccess, authSuccess ->
            if (connectionSuccess && authSuccess) {
                popup.reuseWith("Success!", true)
            } else if (connectionSuccess) {
                popup.close()
                AuthPopup(optionsPopup.stageToShowOn) {
                    success -> popup.apply{
                        reuseWith(if (success) "Success!" else "Failed!", true)
                        open(true)
                    }
                }.open(true)
            } else {
                popup.reuseWith("Failed!", true)
            }
        }
    }).row()

    if (UncivGame.Current.onlineMultiplayer.multiplayerServer.featureSet.authVersion > 0) {
        val passwordTextField = UncivTextField(
            settings.multiplayer.passwords[settings.multiplayer.server] ?: "Password"
        )
        val setPasswordButton = "Set password".toTextButton()

        serverIpTable.add("Set password".toLabel()).padTop(16f).colspan(2).row()
        serverIpTable.add(passwordTextField).colspan(2).growX().padBottom(8f).row()

        val passwordStatusTable = Table().apply {
            add(
                if (settings.multiplayer.passwords.containsKey(settings.multiplayer.server)) {
                    "Your userId is password secured"
                } else {
                    "Set a password to secure your userId"
                }.toLabel()
            )
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

private fun successfullyConnectedToServer(action: (Boolean, Boolean) -> Unit) {
    Concurrency.run("TestIsAlive") {
        try {
            val connectionSuccess = UncivGame.Current.onlineMultiplayer.multiplayerServer.checkServerStatus()
            var authSuccess = false
            if (connectionSuccess) {
                try {
                    authSuccess = UncivGame.Current.onlineMultiplayer.multiplayerServer.authenticate(null)
                } catch (_: Exception) {
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

    if (UncivGame.Current.onlineMultiplayer.multiplayerServer.featureSet.authVersion == 0) {
        popup.reuseWith("This server does not support authentication", true)
        return
    }

    successfullySetPassword(password) { success, ex ->
        if (success) {
            popup.reuseWith(
                "Password set successfully for server [${optionsPopup.settings.multiplayer.server}]",
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

private class RefreshSelect(
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

private fun getInitialOptions(extraCustomServerOptions: List<SelectItem<Duration>>, dropboxOptions: List<SelectItem<Duration>>): Iterable<SelectItem<Duration>> {
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

private fun addSeparator(tab: Table) {
    tab.addSeparator(BaseScreen.skinStrings.skinConfig.baseColor.brighten(0.1f))
}
