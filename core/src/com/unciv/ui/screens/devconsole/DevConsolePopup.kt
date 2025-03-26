package com.unciv.ui.screens.devconsole

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Scaling
import com.unciv.Constants
import com.unciv.GUI
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.ui.components.extensions.*
import com.unciv.ui.components.widgets.UncivTextField
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.devconsole.CliInput.Companion.splitToCliInput
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.utils.Concurrency


class DevConsolePopup(val screen: WorldScreen) : Popup(screen) {
    companion object {
        private const val maxHistorySize = 42
    }
    private val history by screen.game.settings::consoleCommandHistory
    private var keepOpen by screen.game.settings::keepConsoleOpen

    private var currentHistoryEntry = history.size

    private val textField = UncivTextField("") // always has focus, so a hint won't show
    private val responseLabel = "".toLabel(Color.RED).apply { wrap = true }
    private val inputWrapper = Table()

    private val commandRoot = ConsoleCommandRoot()
    internal val gameInfo = screen.gameInfo

    init {
        // Use untranslated text here! The entire console, including messages, should stay English.
        // But "Developer Console" *has* a translation from KeyboardBinding.DeveloperConsole.
        // The extensions still help, even with a "don't translate" kludge
        add("Developer Console {}".toLabel(fontSize = Constants.headingFontSize)).growX()
        add("Keep open {}".toCheckBox(keepOpen) { keepOpen = it }).right().row()

        inputWrapper.defaults().space(5f)
        if (!GUI.keyboardAvailable) inputWrapper.add(getAutocompleteButton())
        inputWrapper.add(textField).growX()
        if (!GUI.keyboardAvailable) inputWrapper.add(getHistoryButtons())

        add(inputWrapper).minWidth(stageToShowOn.width / 2).growX().colspan(2).row()

        // Without this, console popup will always contain the key used to open it - won't work perfectly if it's configured to a "dead key"
        textField.addAction(Actions.delay(0.05f, Actions.run { textField.text = "" }))

        add(responseLabel).colspan(2).maxWidth(screen.stage.width * 0.8f)

        keyShortcuts.add(KeyCharAndCode.BACK) { close() }
        clickBehindToClose = true

        textField.keyShortcuts.add(Input.Keys.ENTER, ::onEnter)
        textField.keyShortcuts.add(KeyCharAndCode.TAB, ::onAutocomplete)
        textField.keyShortcuts.add(KeyCharAndCode.ctrlFromCode(Input.Keys.BACKSPACE), ::onAltDelete)
        textField.keyShortcuts.add(KeyCharAndCode.ctrlFromCode(Input.Keys.RIGHT), ::onAltRight)
        textField.keyShortcuts.add(KeyCharAndCode.ctrlFromCode(Input.Keys.LEFT), ::onAltLeft)

        keyShortcuts.add(Input.Keys.UP) { navigateHistory(-1) }
        keyShortcuts.add(Input.Keys.DOWN) { navigateHistory(1) }

        open(true)

        screen.stage.keyboardFocus = textField
    }

    private fun getAutocompleteButton() = ImageGetter.getArrowImage()
        .surroundWithCircle(50f, color = Color.DARK_GRAY).onClick(::onAutocomplete)

    private fun getHistoryButtons(): Actor {
        val group = Table()
        fun getArrow(rotation: Float, delta: Int) = ImageGetter.getImage("OtherIcons/ForwardArrow").apply {
            name = if (delta > 0) "down" else "up"
            setScaling(Scaling.fillX)
            setSize(36f, 16f)
            scaleX = 0.75f  // no idea why this works
            setOrigin(18f, 8f)
            this.rotation = rotation
            onClick {
                navigateHistory(delta)
            }
        }
        group.add(getArrow(90f, -1)).size(36f, 16f).padBottom(4f).row()
        group.add(getArrow(-90f, 1)).size(36f, 16f)
        group.setSize(40f, 40f)
        return group.surroundWithCircle(50f, false, Color.DARK_GRAY).onRightClick(action = ::showHistory)
    }

    private fun onAutocomplete() {
        val (toRemove, toAdd) = getAutocomplete() ?: return
        fun String.removeFromEnd(n: Int) = substring(0, (length - n).coerceAtLeast(0))
        textField.text = textField.text.removeFromEnd(toRemove) + toAdd
        textField.cursorPosition = Int.MAX_VALUE // because the setText implementation actively resets it after the paste it uses (auto capped at length)
    }
    
    private fun onAltDelete() {
        if (!Gdx.input.isAltKeyPressed()) return
        
        Concurrency.runOnGLThread {
            val text = textField.text
            // textField.cursorPosition-1 to avoid the case where we're currently on a space catching the space we're on
            val lastSpace = text.lastIndexOf(' ', textField.cursorPosition-1) 
            if (lastSpace == -1) {
                textField.text = text.removeRange(0, textField.cursorPosition)
                return@runOnGLThread
            }
            // KEEP the last space
            textField.text = text.removeRange(lastSpace + 1, textField.cursorPosition)
            textField.cursorPosition = lastSpace + 1
        }
    }

    private fun onAltRight() {
        if (!Gdx.input.isAltKeyPressed()) return

        Concurrency.runOnGLThread {
            val text = textField.text
            val nextSpace = text.indexOf(' ', textField.cursorPosition)
            if (nextSpace == -1) {
                textField.cursorPosition = text.length
                return@runOnGLThread
            }
            textField.cursorPosition = nextSpace
        }
    }

    private fun onAltLeft() {
        if (!Gdx.input.isAltKeyPressed()) return

        Concurrency.runOnGLThread {
            val text = textField.text
            val previousSpace = text.lastIndexOf(' ', textField.cursorPosition-1)
            if (previousSpace == -1) {
                textField.cursorPosition = 0
                return@runOnGLThread
            }
            textField.cursorPosition = previousSpace
        }
    }

    private fun navigateHistory(delta: Int) {
        if (history.isEmpty()) return
        currentHistoryEntry = (currentHistoryEntry + delta).coerceIn(history.indices)
        textField.text = history[currentHistoryEntry]
        textField.cursorPosition = textField.text.length
    }

    internal fun showHistory() {
        if (history.isEmpty()) return
        val popup = object : Popup(stageToShowOn) {
            init {
                for ((index, entry) in history.withIndex()) {
                    val label = Label(entry, skin)
                    label.onClick {
                        currentHistoryEntry = index
                        navigateHistory(0)
                        close()
                    }
                    add(label).row()
                }
                clickBehindToClose = true
                if (screen.game.settings.forbidPopupClickBehindToClose) addCloseButton()
                showListeners.add {
                    getScrollPane()?.run { scrollY = maxY }
                }
            }
        }
        popup.open(true)
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
        val params = textField.text.trim().splitToCliInput()
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
