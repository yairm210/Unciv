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
import com.unciv.ui.crashhandling.launchCrashHandling
import com.unciv.ui.crashhandling.postCrashHandlingRunnable
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.*

fun multiplayerTab(
    optionsPopup: OptionsPopup
): Table = Table(BaseScreen.skin).apply {
    pad(10f)
    defaults().pad(5f)

    val settings = optionsPopup.settings

    // at the moment the notification service only exists on Android
    if (Gdx.app.type == Application.ApplicationType.Android) {
        optionsPopup.addCheckbox(
            this, "Enable out-of-game turn notifications",
            settings.multiplayerTurnCheckerEnabled
        ) {
            settings.multiplayerTurnCheckerEnabled = it
            settings.save()
        }

        if (settings.multiplayerTurnCheckerEnabled) {
            addMultiplayerTurnCheckerDelayBox(this, settings)

            optionsPopup.addCheckbox(
                this, "Show persistent notification for turn notifier service",
                settings.multiplayerTurnCheckerPersistentNotificationEnabled
            )
            { settings.multiplayerTurnCheckerPersistentNotificationEnabled = it }
        }
    }

    val connectionToServerButton = "Check connection to server".toTextButton()

    val textToShowForMultiplayerAddress =
        if (settings.multiplayerServer != Constants.dropboxMultiplayerServer) settings.multiplayerServer
        else "https://..."
    val multiplayerServerTextField = TextField(textToShowForMultiplayerAddress, BaseScreen.skin)
    multiplayerServerTextField.setTextFieldFilter { _, c -> c !in " \r\n\t\\" }
    multiplayerServerTextField.programmaticChangeEvents = true
    val serverIpTable = Table()

    serverIpTable.add("Server address".toLabel().onClick {
        multiplayerServerTextField.text = Gdx.app.clipboard.contents
    }).row()
    multiplayerServerTextField.onChange {
        connectionToServerButton.isEnabled = multiplayerServerTextField.text != Constants.dropboxMultiplayerServer
        if (connectionToServerButton.isEnabled) {
            fixTextFieldUrlOnType(multiplayerServerTextField)
            // we can't trim on 'fixTextFieldUrlOnType' for reasons
            settings.multiplayerServer = multiplayerServerTextField.text.trimEnd('/')
        } else {
            settings.multiplayerServer = multiplayerServerTextField.text
        }
        settings.save()
    }

    val screen = optionsPopup.screen
    serverIpTable.add(multiplayerServerTextField).minWidth(screen.stage.width / 2).growX()
    add(serverIpTable).fillX().row()

    add("Reset to Dropbox".toTextButton().onClick {
        multiplayerServerTextField.text = Constants.dropboxMultiplayerServer
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
        SimpleHttp.sendGetRequest("${settings.multiplayerServer}/isalive") {
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

private fun addMultiplayerTurnCheckerDelayBox(table: Table, settings: GameSettings) {
    table.add("Time between turn checks out-of-game (in minutes)".toLabel()).left().fillX()

    val checkDelaySelectBox = SelectBox<Int>(table.skin)
    val possibleDelaysArray = Array<Int>()
    possibleDelaysArray.addAll(1, 2, 5, 15)
    checkDelaySelectBox.items = possibleDelaysArray
    checkDelaySelectBox.selected = settings.multiplayerTurnCheckerDelayInMinutes

    table.add(checkDelaySelectBox).pad(10f).row()

    checkDelaySelectBox.onChange {
        settings.multiplayerTurnCheckerDelayInMinutes = checkDelaySelectBox.selected
        settings.save()
    }
}
