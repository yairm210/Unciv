package com.unciv.ui.options

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Array
import com.unciv.Constants
import com.unciv.logic.multiplayer.storage.SimpleHttp
import com.unciv.models.metadata.GameSettings
import com.unciv.models.translations.tr
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.onChange
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toGdxArray
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlin.reflect.KMutableProperty0

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

    val curRefreshSelect = RefreshSelect(
        "Update status of currently played game every:",
        createRefreshOptions(ChronoUnit.SECONDS, 3, 5),
        createRefreshOptions(ChronoUnit.SECONDS, 10, 20, 30, 60),
        settings.multiplayer::currentGameRefreshDelay,
        settings
    )
    curRefreshSelect.addTo(tab)

    val allRefreshSelect = RefreshSelect(
        "In-game, update status of all games every:",
        createRefreshOptions(ChronoUnit.SECONDS, 15, 30),
        createRefreshOptions(ChronoUnit.MINUTES, 1, 2, 5, 15),
        settings.multiplayer::allGameRefreshDelay,
        settings
    )
    allRefreshSelect.addTo(tab)

    val turnCheckerSelect = addTurnCheckerOptions(tab, optionsPopup)

    addMultiplayerServerOptions(tab, optionsPopup, listOf(curRefreshSelect, allRefreshSelect, turnCheckerSelect).filterNotNull())

    return tab
}

private fun addMultiplayerServerOptions(
    tab: Table,
    optionsPopup: OptionsPopup,
    toUpdate: Iterable<RefreshSelect>
) {
    val settings = optionsPopup.settings

    val connectionToServerButton = "Check connection to server".toTextButton()

    val textToShowForMultiplayerAddress = if (usesDropbox(settings)) {
        "https://"
    } else {
        settings.multiplayer.server
    }
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

    val screen = optionsPopup.screen
    serverIpTable.add(multiplayerServerTextField).minWidth(screen.stage.width / 2).growX()
    tab.add(serverIpTable).colspan(2).fillX().row()

    tab.add("Reset to Dropbox".toTextButton().onClick {
        multiplayerServerTextField.text = Constants.dropboxMultiplayerServer
        for (refreshSelect in toUpdate) refreshSelect.update(false)
        settings.save()
    }).colspan(2).row()

    tab.add(connectionToServerButton.onClick {
        val popup = Popup(screen).apply {
            addGoodSizedLabel("Awaiting response...").row()
        }
        popup.open(true)

        successfullyConnectedToServer(settings) { success, _, _ ->
            popup.addGoodSizedLabel(if (success) "Success!" else "Failed!").row()
            popup.addCloseButton()
        }
    }).colspan(2).row()
}

private fun addTurnCheckerOptions(
    tab: Table,
    optionsPopup: OptionsPopup
): RefreshSelect? {
    // at the moment the notification service only exists on Android
    if (Gdx.app.type != Application.ApplicationType.Android) return null

    val settings = optionsPopup.settings

    optionsPopup.addCheckbox(tab, "Enable out-of-game turn notifications", settings.multiplayer::turnCheckerEnabled)

    if (!settings.multiplayer.turnCheckerEnabled) return null

    val turnCheckerSelect = RefreshSelect(
        "Out-of-game, update status of all games every:",
        createRefreshOptions(ChronoUnit.SECONDS, 30),
        createRefreshOptions(ChronoUnit.MINUTES, 1, 2, 5, 15),
        settings.multiplayer::turnCheckerDelay,
        settings
    )
    turnCheckerSelect.addTo(tab)


    optionsPopup.addCheckbox(
        tab, "Show persistent notification for turn notifier service",
        settings.multiplayer::turnCheckerPersistentNotificationEnabled
    )

    return turnCheckerSelect
}

private fun successfullyConnectedToServer(settings: GameSettings, action: (Boolean, String, Int?) -> Unit) {
    launchCrashHandling("TestIsAlive") {
        SimpleHttp.sendGetRequest("${settings.multiplayer.server}/isalive") { success, result, code ->
            postCrashHandlingRunnable {
                action(success, result, code)
            }
        }
    }
}

private class RefreshSelect(
    labelText: String,
    extraCustomServerOptions: List<RefreshOptions>,
    dropboxOptions: List<RefreshOptions>,
    private val settingsProperty: KMutableProperty0<Duration>,
    settings: GameSettings
) {
    private val customServerOptions = (extraCustomServerOptions + dropboxOptions).toGdxArray()
    private val dropboxOptions = dropboxOptions.toGdxArray()
    private val refreshSelectBox = createSelectBox(settings)
    private val label = labelText.toLabel()

    private fun createSelectBox(settings: GameSettings): SelectBox<RefreshOptions> {
        val refreshSelectBox = SelectBox<RefreshOptions>(BaseScreen.skin)
        val options = if (usesDropbox(settings)) {
            dropboxOptions
        } else {
            customServerOptions
        }
        refreshSelectBox.items = options

        refreshSelectBox.selected = options.firstOrNull() { it.delay == settingsProperty.get() } ?: options.first()
        refreshSelectBox.onChange {
            settingsProperty.set(refreshSelectBox.selected.delay)
            settings.save()
        }

        return refreshSelectBox
    }

    fun addTo(table: Table) {
        table.add(label).left()
        table.add(refreshSelectBox).pad(10f).row()
    }

    fun update(isCustomServer: Boolean) {
        if (isCustomServer && refreshSelectBox.items.size != customServerOptions.size) {
            replaceItems(customServerOptions)
        } else if (!isCustomServer && refreshSelectBox.items.size != dropboxOptions.size) {
            replaceItems(dropboxOptions)
        }
    }

    private fun replaceItems(options: Array<RefreshOptions>) {
        val prev = refreshSelectBox.selected
        refreshSelectBox.items = options
        refreshSelectBox.selected = prev
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

private fun createRefreshOptions(unit: ChronoUnit, vararg options: Long): List<RefreshOptions> {
    return options.map { RefreshOptions(Duration.of(it, unit), "$it " + unit.tr()) }
}

private fun <T> List<T>.toGdxArray(): Array<T> {
    val arr = Array<T>(size)
    for (it in this) {
        arr.add(it)
    }
    return arr
}

private fun usesDropbox(settings: GameSettings) = settings.multiplayer.server == Constants.dropboxMultiplayerServer

