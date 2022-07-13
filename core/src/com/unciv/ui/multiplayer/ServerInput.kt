package com.unciv.ui.multiplayer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.Multiplayer.ServerData
import com.unciv.logic.multiplayer.Multiplayer.ServerType
import com.unciv.ui.options.SelectItem
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.UncivTextField
import com.unciv.ui.utils.extensions.firstUpperRestLowerCase
import com.unciv.ui.utils.extensions.onChange
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.onClickEvent
import com.unciv.ui.utils.extensions.pad
import com.unciv.ui.utils.extensions.toGdxArray
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
import kotlin.reflect.KMutableProperty0

object ServerInput {
    fun create(
        serverDataProperty: KMutableProperty0<ServerData>? = null,
        default: ServerData = serverDataProperty?.get() ?: ServerData.default,
        onUpdate: (ServerData) -> Unit = { _ -> }
    ): Actor {
        val table = Table()
        table.defaults().pad(0f, 5f)

        val serverUrlTextField = createServerUrlTextField(serverDataProperty, default.url, onUpdate)
        val serverAddressLabel = createServerAddressLabel(serverUrlTextField)
        val connectionToServerButton = createCheckConnectionButton(serverUrlTextField)
        val serverTypeLabel = "Server type".toLabel()
        val selectBox = createServerTypeSelect(default.type, serverDataProperty, onUpdate, table, serverTypeLabel, serverAddressLabel, serverUrlTextField, connectionToServerButton)

        addElementsToTable(table, default.type, serverTypeLabel, serverAddressLabel, selectBox, serverUrlTextField, connectionToServerButton)

        return table
    }

    private fun addElementsToTable(
        table: Table,
        serverType: ServerType,
        serverTypeLabel: Label,
        serverAddressLabel: Label,
        selectBox: SelectBox<SelectItem<ServerType>>,
        serverUrlTextField: TextField,
        connectionToServerButton: TextButton
    ) {
        val serverTypeCell = table.add(serverTypeLabel).padBottom(10f)
        if (serverType == ServerType.DROPBOX) {
            serverTypeCell.row()
        } else {
            table.add(serverAddressLabel).padBottom(10f).row()
        }

        val typeSelectCell = table.add(selectBox)
        if (serverType == ServerType.DROPBOX) {
            typeSelectCell.row()
        } else {
            table.add(serverUrlTextField).growX().minWidth(300f)
            table.add(connectionToServerButton).row()
        }
    }

    private fun createServerAddressLabel(serverUrlTextField: TextField): Label {
        val label = "Server address".toLabel()
        label.onClick {
            serverUrlTextField.text = Gdx.app.clipboard.contents
        }
        return label
    }

    private fun createServerUrlTextField(
        serverDataProperty: KMutableProperty0<ServerData>?,
        initialUrl: String?,
        onUpdate: (ServerData) -> Unit
    ): TextField {
        val initialText = initialUrl ?: "https://"
        val serverUrlTextField = UncivTextField.create("Server address", initialText)
        serverUrlTextField.setTextFieldFilter { _, c -> c !in " \r\n\t\\" }
        serverUrlTextField.onChange {
            fixTextFieldUrl(serverUrlTextField)
            val newServerData = ServerData(serverUrlTextField.text)
            serverDataProperty?.set(newServerData)
            onUpdate(newServerData)
        }
        return serverUrlTextField
    }

    private fun createCheckConnectionButton(serverTextField: TextField): TextButton {
        val connectionToServerButton = "Check connection".toTextButton()
        connectionToServerButton.onClickEvent { event, x, y ->
            val popup = Popup(event.stage).apply {
                addGoodSizedLabel("Awaiting response...").row()
            }
            popup.open(true)

            Concurrency.run {
                val success = UncivGame.Current.multiplayer.checkConnection(serverTextField.text)
                launchOnGLThread {
                    popup.addGoodSizedLabel(if (success) "Success!" else "Failed!").row()
                    popup.addCloseButton()
                }
            }
        }
        return connectionToServerButton
    }

    private fun createServerTypeSelect(
        initialSelection: ServerType,
        serverTypeProperty: KMutableProperty0<ServerData>?,
        onUpdate: (ServerData) -> Unit,
        table: Table,
        serverTypeLabel: Label,
        serverAddressLabel: Label,
        serverTextField: TextField,
        connectionToServerButton: TextButton
    ): SelectBox<SelectItem<ServerType>> {
        val selectBox = SelectBox<SelectItem<ServerType>>(BaseScreen.skin)
        selectBox.items = ServerType.values().map { SelectItem(it.toString().firstUpperRestLowerCase(), it) }.toGdxArray()
        selectBox.selected = selectBox.items.filter { it.value == initialSelection }.first()

        selectBox.onChange {
            val selectedType = selectBox.selected.value
            val newServerData = if (selectedType == ServerType.DROPBOX) {
                ServerData(null)
            } else {
                ServerData(serverTextField.text)
            }
            serverTypeProperty?.set(newServerData)
            onUpdate(newServerData)

            table.clearChildren()
            addElementsToTable(
                table,
                selectedType,
                serverTypeLabel,
                serverAddressLabel,
                selectBox,
                serverTextField,
                connectionToServerButton
            )
        }

        return selectBox
    }
}

private fun fixTextFieldUrl(TextField: TextField) {
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
