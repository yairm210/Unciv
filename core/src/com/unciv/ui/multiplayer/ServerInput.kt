package com.unciv.ui.multiplayer

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.UncivGame
import com.unciv.logic.multiplayer.Multiplayer.ServerData
import com.unciv.logic.multiplayer.Multiplayer.ServerType
import com.unciv.models.translations.tr
import com.unciv.ui.options.SelectItem
import com.unciv.ui.popup.Popup
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.UncivTextField
import com.unciv.ui.utils.extensions.firstUpperRestLowerCase
import com.unciv.ui.utils.extensions.onChange
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.onClickEvent
import com.unciv.ui.utils.extensions.toGdxArray
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.ui.utils.extensions.toTextButton
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread
import kotlin.reflect.KMutableProperty0

/**
 * After creating the server input, use either [standalone] or [addToTable] to add it to the UI.
 *
 * @param serverDataProperty the property that is backing this [ServerInput]. Can be `null`, which means
 * @param default the [ServerData] that should be used as the default. If not set explicitly, will use the value of the property or [ServerData.default] if this is not backed by a property.
 * @param onUpdate function that is called with the current [ServerData] value whenever it changes.
 */
class ServerInput(
    private val serverDataProperty: KMutableProperty0<ServerData>? = null,
    default: ServerData = serverDataProperty?.get() ?: ServerData.default,
    private val onUpdate: (ServerData) -> Unit = { _ -> }
) {
    private lateinit var table: Table
    private lateinit var recreateTable: () -> Unit

    private var serverTypeLabel = "".toLabel()
    private var serverTypeSelect = createServerTypeSelect(default.type)
    private val serverUrlLabel = createServerUrlLabel()
    private val serverUrlTextField = createServerUrlTextField(default.url)
    private var connectionToServerButton = createCheckConnectionButton(serverUrlTextField)

    /** The server data this server input currently contains. */
    var serverData: ServerData = default

    /** Creates a new [Actor] that contains all input elements.
     * @param verticalLayout if this is false, will use a horizontal layout. The horizontal layout is 3 columns wide and 2 rows high, where the first row contains the labels
     *                       and the second row contains the input fields. The vertical one is 2 columns wide and 3 rows high, where the first column contains the labels
     *                       and the second column contains the input fields.
     */
    fun standalone(verticalLayout: Boolean = false): Actor {
        table = Table()
        recreateTable = {
            table.clearChildren()
            addToTable(verticalLayout)
        }
        addToTable(verticalLayout)
        return table
    }

    /**
     * Adds the input elements to an existing [tableToAddTo]. The [recreate] function should be a function that does [Table.clearChildren] and adds all of them again,
     * including calling this function again.
     *
     * @param verticalLayout if this is false, will use a horizontal layout. The horizontal layout is 3 columns wide and 2 rows high, where the first row contains the labels
     *                       and the second row contains the input fields. The vertical one is 2 columns wide and 3 rows high, where the first column contains the labels
     *                       and the second column contains the input fields.
     * @return the defaults for the new row after this element
     */
    fun addToTable(tableToAddTo: Table, verticalLayout: Boolean, recreate: () -> Unit): Cell<Actor> {
        table = tableToAddTo
        recreateTable = recreate

        return addToTable(verticalLayout)
    }

    private fun addToTable(verticalLayout: Boolean): Cell<Actor> {
        return if (verticalLayout) {
            addVerticalLayout()
        } else {
            addHorizontalLayout()
        }
    }

    private fun createServerUrlLabel(): Label {
        val label = "".toLabel()
        label.onClick {
            serverUrlTextField.text = Gdx.app.clipboard.contents
        }
        return label
    }

    private fun createServerUrlTextField(
        initialUrl: String?
    ): TextField {
        val initialText = initialUrl ?: "https://"
        val serverUrlTextField = UncivTextField.create("Server address", initialText)
        serverUrlTextField.setTextFieldFilter { _, c -> c !in " \r\n\t\\" }
        serverUrlTextField.onChange {
            fixTextFieldUrl(serverUrlTextField)
            val newServerData = ServerData(serverUrlTextField.text)
            serverDataChanged(newServerData)
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
        initialSelection: ServerType
    ): SelectBox<SelectItem<ServerType>> {
        val serverTypeSelect = SelectBox<SelectItem<ServerType>>(BaseScreen.skin)
        serverTypeSelect.items = ServerType.values().map { SelectItem(it.toString().firstUpperRestLowerCase(), it) }.toGdxArray()
        serverTypeSelect.selected = serverTypeSelect.items.filter { it.value == initialSelection }.first()

        serverTypeSelect.onChange {
            val selectedType = serverTypeSelect.selected.value
            val newServerData = if (selectedType == ServerType.DROPBOX) {
                ServerData(null)
            } else {
                ServerData(serverUrlTextField.text)
            }
            serverDataChanged(newServerData)

            recreateTable()
        }

        return serverTypeSelect
    }

    private fun serverDataChanged(newServerData: ServerData) {
        serverData = newServerData
        serverDataProperty?.set(newServerData)
        onUpdate(newServerData)
    }

    private fun addVerticalLayout(): Cell<Actor> {
        table.row().spaceBottom(10f)
        serverTypeLabel.setText("{Server type}:".tr())
        table.add(serverTypeLabel).left().spaceRight(5f)
        table.add(serverTypeSelect).growX()
        var lastRow = table.row()
        lastRow.spaceBottom(10f)

        if (serverTypeSelect.selected.value == ServerType.CUSTOM) {
            serverUrlLabel.setText("{Server address}:".tr())
            table.add(serverUrlLabel).left().spaceRight(5f)
            table.add(serverUrlTextField).growX()
            table.row().spaceBottom(10f)

            table.add()
            table.add(connectionToServerButton).growX()
            lastRow = table.row()
        }
        return lastRow
    }

    private fun addHorizontalLayout(): Cell<Actor> {
        serverTypeLabel.setText("Server type".tr())
        val serverTypeCell = table.add(serverTypeLabel).spaceBottom(10f).spaceRight(5f)
        val serverType = serverTypeSelect.selected.value
        if (serverType == ServerType.DROPBOX) {
            serverTypeCell.row()
        } else {
            serverUrlLabel.setText("Server address".tr())
            table.add(serverUrlLabel).spaceBottom(10f).row()
        }

        val typeSelectCell = table.add(serverTypeSelect).spaceRight(5f)
        if (serverType == ServerType.CUSTOM) {
            table.add(serverUrlTextField).growX().spaceRight(5f)
            table.add(connectionToServerButton)
        }
        return table.row()
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
