package com.unciv.ui.screens.devconsole

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.unciv.Constants
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.ui.components.UncivTextField
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.worldscreen.WorldScreen


class DevConsolePopup(val screen: WorldScreen) : Popup(screen) {
    private val history by screen.game.settings::consoleCommandHistory
    private var keepOpen by screen.game.settings::keepConsoleOpen

    private var currentHistoryEntry = history.size

    private val textField = UncivTextField.create("", "") // always has focus, so a hint won't show
    private val responseLabel = "".toLabel(Color.RED).apply { wrap = true }

    private val commandRoot = ConsoleCommandRoot()
    internal val gameInfo = screen.gameInfo

    init {
        add("Developer Console".toLabel(fontSize = Constants.headingFontSize)).growX()  // translation template is automatic via the keybinding
        add("Keep open".toCheckBox(keepOpen) { keepOpen = it }).right().row()

        add(textField).width(stageToShowOn.width / 2).colspan(2).row()
        textField.keyShortcuts.add(Input.Keys.ENTER, ::onEnter)

        // Without this, console popup will always contain the key used to open it - won't work perfectly if it's configured to a "dead key"
        textField.addAction(Actions.delay(0.05f, Actions.run { textField.text = "" }))

        add(responseLabel).colspan(2).maxWidth(screen.stage.width * 0.8f)

        keyShortcuts.add(KeyCharAndCode.BACK) { close() }
        clickBehindToClose = true

        keyShortcuts.add(KeyCharAndCode.TAB) {
            val textToAdd = getAutocomplete()
            textField.appendText(textToAdd)
        }

        keyShortcuts.add(Input.Keys.UP) { navigateHistory(-1) }
        keyShortcuts.add(Input.Keys.DOWN) { navigateHistory(1) }

        open(true)

        screen.stage.keyboardFocus = textField
    }

    private fun navigateHistory(delta: Int) {
        if (history.isEmpty()) return
        currentHistoryEntry = (currentHistoryEntry + delta).coerceIn(history.indices)
        textField.text = history[currentHistoryEntry]
        textField.cursorPosition = textField.text.length
    }

    private fun onEnter() {
        val handleCommandResponse = handleCommand()
        if (handleCommandResponse.isOK) {
            screen.shouldUpdate = true
            if (history.isEmpty() || history.last() != textField.text)
                history.add(textField.text)
            if (!keepOpen) close() else textField.text = ""
            return
        }
        showResponse(handleCommandResponse.message, handleCommandResponse.color)
    }

    internal fun showResponse(message: String?, color: Color) {
        responseLabel.setText(message)
        responseLabel.style.fontColor = color
    }

    private val splitStringRegex = Regex("\"([^\"]+)\"|\\S+") // Read: "(phrase)" OR non-whitespace
    private fun getParams(text: String): List<String> {
        return splitStringRegex.findAll(text).map { it.value.removeSurrounding("\"") }.filter { it.isNotEmpty() }.toList()
    }

    private fun handleCommand(): DevConsoleResponse {
        val params = getParams(textField.text)
        return commandRoot.handle(this, params)
    }

    private fun getAutocomplete(): String {
        val params = getParams(textField.text)
        return commandRoot.autocomplete(this, params)
    }

    internal fun getCivByName(name: String) = gameInfo.civilizations.firstOrNull { it.civName.toCliInput() == name.toCliInput() }
        ?: throw ConsoleErrorException("Unknown civ: $name")

    internal fun getSelectedTile() = screen.mapHolder.selectedTile ?: throw ConsoleErrorException("Select tile")

    /** Gets city by selected tile */
    internal fun getSelectedCity() = getSelectedTile().getCity() ?: throw ConsoleErrorException("Select tile belonging to city")

    internal fun getCity(cityName: String) = gameInfo.getCities().firstOrNull { it.name.toCliInput() == cityName.toCliInput() }
        ?: throw ConsoleErrorException("Unknown city: $cityName")

    internal fun getSelectedUnit(): MapUnit {
        val selectedTile = getSelectedTile()
        if (selectedTile.getFirstUnit() == null) throw ConsoleErrorException("Select tile with units")
        val units = selectedTile.getUnits().toList()
        val selectedUnit = screen.bottomUnitTable.selectedUnit
        return if (selectedUnit != null && selectedUnit.getTile() == selectedTile) selectedUnit
        else units.first()
    }

    internal fun getInt(param: String) = param.toIntOrNull() ?: throw ConsoleErrorException("$param is not a valid number")

}
