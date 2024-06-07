package com.unciv.ui.screens.devconsole

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.unciv.Constants
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.components.extensions.toCheckBox
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.devconsole.CliInput.Companion.splitToCliInput
import com.unciv.ui.screens.worldscreen.WorldScreen


class DevConsolePopup(val screen: WorldScreen) : Popup(screen) {
    companion object {
        private const val maxHistorySize = 42
    }
    private val history by screen.game.settings::consoleCommandHistory
    private var keepOpen by screen.game.settings::keepConsoleOpen

    private var currentHistoryEntry = history.size

    private val textField = UncivTextField.create("", "") // always has focus, so a hint won't show
    private val responseLabel = "".toLabel(Color.RED).apply { wrap = true }

    private val commandRoot = ConsoleCommandRoot()
    internal val gameInfo = screen.gameInfo

    init {
        // Use untranslated text here! The entire console, including messages, should stay English.
        // But "Developer Console" *has* a translation from KeyboardBinding.DeveloperConsole.
        // The extensions still help, even with a "don't translate" kludge ("Keep open" has no template but might in the future).
        add("Developer Console {}".toLabel(fontSize = Constants.headingFontSize)).growX()  // translation template is automatic via the keybinding
        add("Keep open {}".toCheckBox(keepOpen) { keepOpen = it }).right().row()

        add(textField).width(stageToShowOn.width / 2).colspan(2).row()
        textField.keyShortcuts.add(Input.Keys.ENTER, ::onEnter)

        // Without this, console popup will always contain the key used to open it - won't work perfectly if it's configured to a "dead key"
        textField.addAction(Actions.delay(0.05f, Actions.run { textField.text = "" }))

        add(responseLabel).colspan(2).maxWidth(screen.stage.width * 0.8f)

        keyShortcuts.add(KeyCharAndCode.BACK) { close() }
        clickBehindToClose = true

        textField.keyShortcuts.add(KeyCharAndCode.TAB) {
            getAutocomplete()?.also {
                fun String.removeFromEnd(n: Int) = substring(0, (length - n).coerceAtLeast(0))
                textField.text = textField.text.removeFromEnd(it.first) + it.second
                textField.cursorPosition = Int.MAX_VALUE // because the setText implementation actively resets it after the paste it uses (auto capped at length)
            }
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
            addHistory()
            if (!keepOpen) close() else textField.text = ""
            return
        }
        showResponse(handleCommandResponse.message, handleCommandResponse.color)
    }

    private fun addHistory() {
        val text = textField.text
        if (text.isBlank()) return
        if (history.isNotEmpty() && history.last().equals(text, true)) return
        if (history.size >= maxHistorySize) {
            history.removeAll { it.equals(text, true) }
            if (history.size >= maxHistorySize)
                history.removeAt(0)
        }
        history.add(textField.text)
        currentHistoryEntry = history.size
    }

    internal fun showResponse(message: String?, color: Color) {
        responseLabel.setText(message)
        responseLabel.style.fontColor = color
    }

    private fun handleCommand(): DevConsoleResponse {
        val params = textField.text.splitToCliInput()
        return commandRoot.handle(this, params)
    }

    private fun getAutocomplete(): Pair<Int,String>? {
        val params = textField.text.splitToCliInput()
        val autoCompleteString = commandRoot.autocomplete(this, params)
            ?: return null
        val replaceLength = params.lastOrNull()?.originalLength() ?: 0
        return replaceLength to autoCompleteString
    }

    internal fun getCivByName(name: CliInput) =
        getCivByNameOrNull(name)
        ?: throw ConsoleErrorException("Unknown civ: $name")
    internal fun getCivByNameOrSelected(name: CliInput?) =
        name?.let { getCivByName(it) }
        ?: screen.selectedCiv
    internal fun getCivByNameOrNull(name: CliInput): Civilization? =
        gameInfo.civilizations.firstOrNull { name.equals(it.civName) }

    internal fun getSelectedTile() = screen.mapHolder.selectedTile
        ?: throw ConsoleErrorException("Select tile")

    /** Gets city by selected tile */
    internal fun getSelectedCity() = getSelectedTile().getCity()
        ?: throw ConsoleErrorException("Select tile belonging to city")

    internal fun getCity(cityName: CliInput) = gameInfo.getCities().firstOrNull { cityName.equals(it.name) }
        ?: throw ConsoleErrorException("Unknown city: $cityName")

    internal fun getSelectedUnit(): MapUnit {
        val selectedTile = getSelectedTile()
        if (selectedTile.getFirstUnit() == null) throw ConsoleErrorException("Select tile with units")
        val units = selectedTile.getUnits().toList()
        val selectedUnit = screen.bottomUnitTable.selectedUnit
        return if (selectedUnit != null && selectedUnit.getTile() == selectedTile) selectedUnit
        else units.first()
    }
}
