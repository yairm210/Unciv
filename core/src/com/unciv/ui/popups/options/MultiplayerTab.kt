package com.unciv.ui.popups.options

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.OnlineMultiplayer
import com.unciv.logic.multiplayer.storage.FileStorageRateLimitReached
import com.unciv.logic.multiplayer.storage.MultiplayerAuthException
import com.unciv.models.UncivSound
import com.unciv.models.metadata.GameSetting
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.ruleset.RulesetCache
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.extensions.addSeparator
import com.unciv.ui.components.extensions.brighten
import com.unciv.ui.components.extensions.format
import com.unciv.ui.components.extensions.isEnabled
import com.unciv.ui.components.extensions.onChange
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.toGdxArray
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.popups.AuthPopup
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
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

    addSelectAsSeparateTable(tab, SettingsSelect("Sound notification for when it's your turn in your currently open game:",
        createNotificationSoundOptions(),
        GameSetting.MULTIPLAYER_CURRENT_GAME_TURN_NOTIFICATION_SOUND,
        settings
    )
    )

    addSelectAsSeparateTable(tab, SettingsSelect("Sound notification for when it's your turn in any other game:",
        createNotificationSoundOptions(),
        GameSetting.MULTIPLAYER_OTHER_GAME_TURN_NOTIFICATION_SOUND,
        settings
    )
    )

    addSeparator(tab)

    addMultiplayerServerOptions(tab, optionsPopup, listOf(curRefreshSelect, allRefreshSelect, turnCheckerSelect).filterNotNull())

    return tab
}

private fun createNotificationSoundOptions(): List<SelectItem<UncivSound>> = listOf(
    SelectItem("None", UncivSound.Silent),
    SelectItem("Notification [1]", UncivSound.Notification1),
    SelectItem("Notification [2]", UncivSound.Notification2),
    SelectItem("Chimes", UncivSound.Chimes),
    SelectItem("Choir", UncivSound.Choir),
    SelectItem("Buy", UncivSound.Coin),
    SelectItem("Create", UncivSound.Construction),
    SelectItem("Fortify", UncivSound.Fortify),
    SelectItem("Pick a tech", UncivSound.Paper),
    SelectItem("Adopt policy", UncivSound.Policy),
    SelectItem("Promote", UncivSound.Promote),
    SelectItem("Set up", UncivSound.Setup),
    SelectItem("Swap units", UncivSound.Swap),
    SelectItem("Upgrade", UncivSound.Upgrade),
    SelectItem("Bombard", UncivSound.Bombard)
) + buildUnitAttackSoundOptions()

private fun buildUnitAttackSoundOptions(): List<SelectItem<UncivSound>> {
    return RulesetCache.getSortedBaseRulesets()
        .map(RulesetCache::get).filterNotNull()
        .map(Ruleset::units).map { it.values }
        .flatMap { it }
        .filter { it.attackSound != null }
        .filter { it.attackSound != "nuke" } // much too long for a notification
        .distinctBy { it.attackSound }
        .map { SelectItem("[${it.name}] Attack Sound", UncivSound(it.attackSound!!)) }
}

private fun addMultiplayerServerOptions(
    tab: Table,
    optionsPopup: OptionsPopup,
    toUpdate: Iterable<RefreshSelect>
) {
    val settings = optionsPopup.settings

    val connectionToServerButton = "Check connection to server".toTextButton()

    val textToShowForMultiplayerAddress = if (OnlineMultiplayer.usesCustomServer()) {
        settings.multiplayer.server
    } else {
        "https://"
    }
    val multiplayerServerTextField = UncivTextField.create("Server address", textToShowForMultiplayerAddress)
    multiplayerServerTextField.setTextFieldFilter { _, c -> c !in " \r\n\t\\" }
    multiplayerServerTextField.programmaticChangeEvents = true
    val serverIpTable = Table()

    val passwordTextField = UncivTextField.create("Password")
    val setPasswordButton = "Set password".toTextButton()

    serverIpTable.add("Server address".toLabel().onClick {
        multiplayerServerTextField.text = Gdx.app.clipboard.contents
        }).row()
    multiplayerServerTextField.onChange {
        val isCustomServer = OnlineMultiplayer.usesCustomServer()
        connectionToServerButton.isEnabled = isCustomServer

        for (refreshSelect in toUpdate) refreshSelect.update(isCustomServer)

        if (isCustomServer) {
            fixTextFieldUrlOnType(multiplayerServerTextField)
            // we can't trim on 'fixTextFieldUrlOnType' for reasons
            settings.multiplayer.server = multiplayerServerTextField.text.trimEnd('/')
        } else {
            settings.multiplayer.server = multiplayerServerTextField.text
        }
        settings.save()
    }

    serverIpTable.add(multiplayerServerTextField).minWidth(optionsPopup.stageToShowOn.width / 2).growX()
    tab.add(serverIpTable).colspan(2).fillX().row()

    tab.add("Reset to Dropbox".toTextButton().onClick {
        multiplayerServerTextField.text = Constants.dropboxMultiplayerServer
        for (refreshSelect in toUpdate) refreshSelect.update(false)
        settings.save()
    }).colspan(2).row()

    tab.add(connectionToServerButton.onClick {
        val popup = Popup(optionsPopup.stageToShowOn).apply {
            addGoodSizedLabel("Awaiting response...").row()
        }
        popup.open(true)

        successfullyConnectedToServer { connectionSuccess, authSuccess ->
            if (connectionSuccess && authSuccess) {
                popup.reuseWith("Success!", true)
            } else if (connectionSuccess) {
                popup.close()
                AuthPopup(optionsPopup.stageToShowOn).open(true)
            } else {
                popup.reuseWith("Failed!", true)
            }
        }
    }).colspan(2).row()

    tab.add("Set server password".toLabel()).padTop(16f).colspan(2).row()
    tab.add(passwordTextField).colspan(2).growX().row()
    tab.add(setPasswordButton.onClick {
        if (passwordTextField.text.isNullOrBlank())
            return@onClick

        if (UncivGame.Current.onlineMultiplayer.serverFeatureSet.authVersion == 0) {
            Popup(optionsPopup.stageToShowOn).apply {
                addGoodSizedLabel("This server does not support authentication").row()
                addCloseButton()
                open()
            }
            return@onClick
        }

        try {
            UncivGame.Current.onlineMultiplayer.setPassword(passwordTextField.text)
        } catch (ex: Exception) {
            if (ex is MultiplayerAuthException) {
                AuthPopup(optionsPopup.stageToShowOn).open(true)
                return@onClick
            }

            val message = when (ex) {
                is FileStorageRateLimitReached -> "Server limit reached! Please wait for [${ex.limitRemainingSeconds}] seconds"
                else -> "Failed to set password!"
            }

            Popup(optionsPopup.stageToShowOn).apply {
                addGoodSizedLabel(message).row()
                addCloseButton()
                open()
            }
        }
    }).colspan(2).row()
}

private fun addTurnCheckerOptions(
    tab: Table,
    optionsPopup: OptionsPopup
): RefreshSelect? {
    val settings = optionsPopup.settings

    optionsPopup.addCheckbox(tab, "Enable out-of-game turn notifications", settings.multiplayer::turnCheckerEnabled)

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
            val connectionSuccess = UncivGame.Current.onlineMultiplayer.checkServerStatus()
            var authSuccess = false
            if (connectionSuccess) {
                try {
                    authSuccess = UncivGame.Current.onlineMultiplayer.authenticate(null)
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
    return if (OnlineMultiplayer.usesCustomServer()) customServerItems else dropboxItems
}

private fun fixTextFieldUrlOnType(TextField: TextField) {
    var text: String = TextField.text
    var cursor: Int = minOf(TextField.cursorPosition, text.length)

    val textBeforeCursor: String = text.substring(0, cursor)

    // replace multiple slash with a single one, except when it's a ://
    val multipleSlashes = Regex("(?<!:)/{2,}")
    text = multipleSlashes.replace(text, "/")

    // calculate updated cursor
    cursor = multipleSlashes.replace(textBeforeCursor, "/").length

    // update TextField
    if (text != TextField.text) {
        TextField.text = text
        TextField.cursorPosition = cursor
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
