package com.unciv.ui.options

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Array
import com.unciv.Constants
import com.unciv.logic.multiplayer.OnlineMultiplayer
import com.unciv.logic.multiplayer.storage.SimpleHttp
import com.unciv.models.metadata.GameSettings
import com.unciv.models.translations.tr
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.*
import java.time.Duration
import kotlin.reflect.KMutableProperty0

fun multiplayerTab(
    optionsPopup: OptionsPopup
): Table = Table(BaseScreen.skin).apply {
    pad(10f)
    defaults().pad(5f)

    val settings = optionsPopup.settings

    optionsPopup.addCheckbox(this, "Enable multiplayer status button in singleplayer games",
        settings.multiplayer.statusButtonInSinglePlayer, updateWorld = true
    ) {
        settings.multiplayer.statusButtonInSinglePlayer = it
        settings.save()
    }

    val curRefreshSelect = addRefreshSelect(this, settings, settings.multiplayer::currentGameRefreshDelay,
        "Update status of currently played game every:".toLabel(), curRefreshDropboxOptions, curRefreshCustomServerOptions)
    val allRefreshSelect = addRefreshSelect(this, settings, settings.multiplayer::allGameRefreshDelay,
        "In-game, update status of all games every:".toLabel(), allRefreshDropboxOptions, allRefreshCustomServerOptions)

    var turnCheckerSelect: SelectBox<RefreshOptions>? = null
    // at the moment the notification service only exists on Android
    if (Gdx.app.type == Application.ApplicationType.Android) {
        optionsPopup.addCheckbox(
            this, "Enable out-of-game turn notifications",
            settings.multiplayer.turnCheckerEnabled
        ) {
            settings.multiplayer.turnCheckerEnabled = it
            settings.save()
        }

        if (settings.multiplayer.turnCheckerEnabled) {
            turnCheckerSelect = addRefreshSelect(this, settings, settings.multiplayer::turnCheckerDelay,
                "Out-of-game, update status of all games every:".toLabel(), turnCheckerDropboxOptions, turnCheckerCustomServerOptions)

            optionsPopup.addCheckbox(
                this, "Show persistent notification for turn notifier service",
                settings.multiplayer.turnCheckerPersistentNotificationEnabled
            )
            { settings.multiplayer.turnCheckerPersistentNotificationEnabled = it }
        }
    }

    val connectionToServerButton = "Check connection to server".toTextButton()

    val textToShowForMultiplayerAddress =
        if (OnlineMultiplayer.usesCustomServer()) settings.multiplayer.server
        else "https://..."
    val multiplayerServerTextField = TextField(textToShowForMultiplayerAddress, BaseScreen.skin)
    multiplayerServerTextField.setTextFieldFilter { _, c -> c !in " \r\n\t\\" }
    multiplayerServerTextField.programmaticChangeEvents = true
    val serverIpTable = Table()

    serverIpTable.add("Server address".toLabel().onClick {
        multiplayerServerTextField.text = Gdx.app.clipboard.contents
    }).row()
    multiplayerServerTextField.onChange {
        val isCustomServer = multiplayerServerTextField.text != Constants.dropboxMultiplayerServer
        connectionToServerButton.isEnabled = isCustomServer

        updateRefreshSelectOptions(curRefreshSelect, isCustomServer, curRefreshDropboxOptions, curRefreshCustomServerOptions)
        updateRefreshSelectOptions(allRefreshSelect, isCustomServer, allRefreshDropboxOptions, allRefreshCustomServerOptions)
        if (turnCheckerSelect != null) {
            updateRefreshSelectOptions(turnCheckerSelect, isCustomServer, allRefreshDropboxOptions, allRefreshCustomServerOptions)
        }

        if (isCustomServer) {
            fixTextFieldUrlOnType(multiplayerServerTextField)
            // we can't trim on 'fixTextFieldUrlOnType' for reasons
            settings.multiplayer.server = multiplayerServerTextField.text.trimEnd('/')
        } else {
            settings.multiplayer.server = multiplayerServerTextField.text
        }
        settings.save()
    }

    val screen = optionsPopup.screen
    serverIpTable.add(multiplayerServerTextField).minWidth(screen.stage.width / 2).growX()
    add(serverIpTable).fillX().row()

    add("Reset to Dropbox".toTextButton().onClick {
        multiplayerServerTextField.text = Constants.dropboxMultiplayerServer
        if (allRefreshDropboxOptions.size != allRefreshSelect.items.size) {
            allRefreshSelect.items = allRefreshDropboxOptions
        }
        if (curRefreshDropboxOptions.size != curRefreshSelect.items.size) {
            curRefreshSelect.items = curRefreshDropboxOptions
        }
        if (turnCheckerSelect != null && turnCheckerDropboxOptions.size != turnCheckerSelect.items.size) {
            turnCheckerSelect.items = turnCheckerDropboxOptions
        }
        settings.save()
    }).row()

    add(connectionToServerButton.onClick {
        val popup = Popup(screen).apply {
            addGoodSizedLabel("Awaiting response...").row()
        }
        popup.open(true)

        successfullyConnectedToServer(settings) { success, _, _ ->
            popup.addGoodSizedLabel(if (success) "Success!" else "Failed!").row()
            popup.addCloseButton()
        }
    }).row()
}

private fun successfullyConnectedToServer(settings: GameSettings, action: (Boolean, String, Int?) -> Unit) {
    launchCrashHandling("TestIsAlive") {
        SimpleHttp.sendGetRequest("${settings.multiplayer.server}/isalive") {
                success, result, code ->
            postCrashHandlingRunnable {
                action(success, result, code)
            }
        }
    }
}

private fun fixTextFieldUrlOnType(TextField: TextField) {
    var text: String = TextField.text
    var cursor: Int = minOf(TextField.cursorPosition, text.length)

    // if text is 'http:' or 'https:' auto append '//'
    if (Regex("^https?:$").containsMatchIn(text)) {
        TextField.appendText("//")
        return
    }

    val textBeforeCursor: String = text.substring(0, cursor)

    // replace multiple slash with a single one
    val multipleSlashes = Regex("/{2,}")
    text = multipleSlashes.replace(text, "/")

    // calculate updated cursor
    cursor = multipleSlashes.replace(textBeforeCursor, "/").length

    // operations above makes 'https://' -> 'https:/'
    // fix that if available and update cursor
    val i: Int = text.indexOf(":/")
    if (i > -1) {
        text = text.replaceRange(i..i + 1, "://")
        if (cursor > i + 1) ++cursor
    }

    // update TextField
    if (text != TextField.text) {
        TextField.text = text
        TextField.cursorPosition = cursor
    }
}

private class RefreshOptions(val delay: Duration, val label: String) {
    override fun toString(): String = label
    override fun equals(other: Any?): Boolean = other is RefreshOptions && delay == other.delay
    override fun hashCode(): Int = delay.hashCode()
}


private val curRefreshDropboxOptions =
    (listOf<Long>(10, 20, 30, 60).map { RefreshOptions(Duration.ofSeconds(it), "$it " + "Seconds".tr()) }).toGdxArray()

private val curRefreshCustomServerOptions =
    (listOf<Long>(3, 5).map { RefreshOptions(Duration.ofSeconds(it), "$it " + "Seconds".tr()) } + curRefreshDropboxOptions).toGdxArray()

private val allRefreshDropboxOptions =
    (listOf<Long>(1, 2, 5, 15).map { RefreshOptions(Duration.ofMinutes(it), "$it " + "Minutes".tr()) }).toGdxArray()

private val allRefreshCustomServerOptions =
    (listOf<Long>(15, 30).map { RefreshOptions(Duration.ofSeconds(it), "$it " + "Seconds".tr()) } + allRefreshDropboxOptions).toGdxArray()

private val turnCheckerDropboxOptions =
    (listOf<Long>(1, 2, 5, 15).map { RefreshOptions(Duration.ofMinutes(it), "$it " + "Minutes".tr()) }).toGdxArray()

private val turnCheckerCustomServerOptions =
    (listOf<Long>(30).map { RefreshOptions(Duration.ofSeconds(it), "$it " + "Seconds".tr()) } + allRefreshDropboxOptions).toGdxArray()

private fun <T> List<T>.toGdxArray(): Array<T> {
    val arr = Array<T>(size)
    for (it in this) {
        arr.add(it)
    }
    return arr
}

private fun addRefreshSelect(
    table: Table,
    settings: GameSettings,
    settingsProperty: KMutableProperty0<Duration>,
    label: Label,
    dropboxOptions: Array<RefreshOptions>,
    customServerOptions: Array<RefreshOptions>
): SelectBox<RefreshOptions> {
    table.add(label).left()

    val refreshSelectBox = SelectBox<RefreshOptions>(table.skin)
    val options = if (OnlineMultiplayer.usesCustomServer()) {
        customServerOptions
    } else {
        dropboxOptions
    }
    refreshSelectBox.items = options

    refreshSelectBox.selected = options.firstOrNull() { it.delay == settingsProperty.get() } ?: options.first()

    table.add(refreshSelectBox).pad(10f).row()

    refreshSelectBox.onChange {
        settingsProperty.set(refreshSelectBox.selected.delay)
        settings.save()
    }

    return refreshSelectBox
}

private fun updateRefreshSelectOptions(
    selectBox: SelectBox<RefreshOptions>,
    isCustomServer: Boolean,
    dropboxOptions: Array<RefreshOptions>,
    customServerOptions: Array<RefreshOptions>
) {
    fun replaceItems(selectBox: SelectBox<RefreshOptions>, options: Array<RefreshOptions>) {
        val prev = selectBox.selected
        selectBox.items = options
        selectBox.selected = prev
    }

    if (isCustomServer && selectBox.items.size != customServerOptions.size) {
        replaceItems(selectBox, customServerOptions)
    } else if (!isCustomServer && selectBox.items.size != dropboxOptions.size) {
        replaceItems(selectBox, dropboxOptions)
    }
}
