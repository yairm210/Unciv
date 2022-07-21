package com.unciv.ui.multiplayer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.Multiplayer
import com.unciv.logic.multiplayer.storage.SimpleHttp
import com.unciv.models.metadata.GameSettings
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.UncivTextField
import com.unciv.ui.utils.extensions.isEnabled
import com.unciv.ui.utils.extensions.onChange
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread

object ServerInput {
    fun create(
        onUpdate: (Boolean, String?) -> Unit
    ): Table {
        val table = Table()

        val connectionToServerButton = "Check connection to server".toTextButton()

        val textToShowForMultiplayerAddress = if (Multiplayer.usesCustomServer()) {
            UncivGame.Current.settings.multiplayer.server
        } else {
            "https://"
        }
        val multiplayerServerTextField = UncivTextField.create("Server address", textToShowForMultiplayerAddress)
        multiplayerServerTextField.setTextFieldFilter { _, c -> c !in " \r\n\t\\" }
        multiplayerServerTextField.programmaticChangeEvents = true
        val serverIpTable = Table()

        serverIpTable.add("Server address".toLabel().onClick {
            multiplayerServerTextField.text = Gdx.app.clipboard.contents
        }).row()
        multiplayerServerTextField.onChange {
            val isCustomServer = Multiplayer.usesCustomServer()
            connectionToServerButton.isEnabled = isCustomServer

            if (isCustomServer) {
                fixTextFieldUrlOnType(multiplayerServerTextField)
                // we can't trim on 'fixTextFieldUrlOnType' for reasons
                UncivGame.Current.settings.multiplayer.server = multiplayerServerTextField.text.trimEnd('/')
            } else {
                UncivGame.Current.settings.multiplayer.server = multiplayerServerTextField.text
            }
            UncivGame.Current.settings.save()
            onUpdate(Multiplayer.usesCustomServer(), UncivGame.Current.settings.multiplayer.server)
        }

        serverIpTable.add(multiplayerServerTextField).growX()
        table.add(serverIpTable).fillX().row()

        table.add("Reset to Dropbox".toTextButton().onClick {
            multiplayerServerTextField.text = Constants.dropboxMultiplayerServer
            UncivGame.Current.settings.save()
        }).row()

        table.add(connectionToServerButton.onClick {
            val popup = Popup(table.stage).apply {
                addGoodSizedLabel("Awaiting response...").row()
            }
            popup.open(true)

            successfullyConnectedToServer(UncivGame.Current.settings) { success, _, _ ->
                popup.addGoodSizedLabel(if (success) "Success!" else "Failed!").row()
                popup.addCloseButton()
            }
        }).row()
        return table
    }
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

private fun successfullyConnectedToServer(settings: GameSettings, action: (Boolean, String, Int?) -> Unit) {
    Concurrency.run("TestIsAlive") {
        SimpleHttp.sendGetRequest("${settings.multiplayer.server}/isalive") { success, result, code ->
            launchOnGLThread {
                action(success, result, code)
            }
        }
    }
}
