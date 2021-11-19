package com.unciv.ui.consolescreen

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.Constants
import com.unciv.scripting.ScriptingBackendBase
import com.unciv.scripting.ScriptingBackendType
import com.unciv.scripting.ScriptingState
import com.unciv.ui.utils.*
import com.unciv.ui.utils.AutoScrollPane as ScrollPane
import kotlin.math.max
import kotlin.math.min


class ConsoleScreen(val scriptingState:ScriptingState, var closeAction: () -> Unit): CameraStageBaseScreen() {

    private val layoutTable: Table = Table()

    private val topBar: Table = Table()
    private var backendsScroll: ScrollPane
    private val backendsAdders: Table = Table()
    private val closeButton: TextButton = Constants.close.toTextButton()

    private var middleSplit: SplitPane
    private var printScroll: ScrollPane
    private val printHistory: Table = Table()
    private val runningContainer: Table = Table()
    private val runningList: Table = Table()

    private val inputBar: Table = Table()
    private val inputField: TextField = TextField("", skin)

    private val inputControls: Table = Table()
    private val tabButton: TextButton = "TAB".toTextButton()
    private val upButton: Image = ImageGetter.getImage("OtherIcons/Up")
    private val downButton: Image = ImageGetter.getImage("OtherIcons/Down")
    private val runButton: TextButton = "ENTER".toTextButton()

    private val layoutUpdators = ArrayList<() -> Unit>()
    private var isOpen = false

    var inputText: String
        get() = inputField.text
        set(value: String) { inputField.setText(value) }

    var cursorPos: Int
        get() = inputField.getCursorPosition()
        set(value: Int) {
            inputField.setCursorPosition(max(0, min(inputText.length, value)))
        }

    init {

        backendsAdders.add("Launch new backend:".toLabel()).padRight(30f).padLeft(20f)
        for (backendtype in ScriptingBackendType.values()) {
            var backendadder = backendtype.metadata.displayName.toTextButton()
            backendadder.onClick({
                echo(scriptingState.spawnBackend(backendtype))
                updateRunning()
            })
            backendsAdders.add(backendadder)
        }
        backendsScroll = ScrollPane(backendsAdders)

        backendsAdders.left()

        val cell_backendsScroll = topBar.add(backendsScroll)
        layoutUpdators.add( { cell_backendsScroll.minWidth(stage.width - closeButton.getPrefWidth()) } )
        topBar.add(closeButton)

        printHistory.left()
        printHistory.bottom()
        printScroll = ScrollPane(printHistory)

        runningContainer.add("Active Backends:".toLabel()).row()
        runningContainer.add(runningList)

        middleSplit = SplitPane(printScroll, runningContainer, false, skin)
        middleSplit.setSplitAmount(0.8f)

        inputControls.add(tabButton)
        inputControls.add(upButton.surroundWithCircle(40f))
        inputControls.add(downButton.surroundWithCircle(40f))
        inputControls.add(runButton)

        val cell_inputField = inputBar.add(inputField)
        layoutUpdators.add( { cell_inputField.minWidth(stage.width - inputControls.getPrefWidth()) } )
        inputBar.add(inputControls)

        layoutUpdators.add( { layoutTable.setSize(stage.width, stage.height) } )

        val cell_topBar = layoutTable.add(topBar)
        layoutUpdators.add( { cell_topBar.minWidth(stage.width) } )
        cell_topBar.row()

        val cell_middleSplit = layoutTable.add(middleSplit)
        layoutUpdators.add( { cell_middleSplit.minWidth(stage.width).minHeight(stage.height - topBar.getPrefHeight() - inputBar.getPrefHeight()) } )
        cell_middleSplit.row()

        layoutTable.add(inputBar)

        runButton.onClick({ run() })
        keyPressDispatcher[Input.Keys.ENTER] = { run() }
        keyPressDispatcher[Input.Keys.NUMPAD_ENTER] = { run() }

        tabButton.onClick({ autocomplete() })
        keyPressDispatcher[Input.Keys.TAB] = { autocomplete() }

        upButton.onClick({ navigateHistory(1) })
        keyPressDispatcher[Input.Keys.UP] = { navigateHistory(1) }
        downButton.onClick({ navigateHistory(-1) })
        keyPressDispatcher[Input.Keys.DOWN] = { navigateHistory(-1) }

        onBackButtonClicked({ closeConsole() })
        closeButton.onClick({ closeConsole() })

        updateLayout()

        stage.addActor(layoutTable)

        echoHistory()

        updateRunning()
    }

    fun updateLayout() {
        for (func in layoutUpdators) {
            func()
        }
    }

    fun openConsole() {
        game.setScreen(this)
        keyPressDispatcher.install(stage)
        this.isOpen = true
    }

    fun closeConsole() {
        closeAction()
        keyPressDispatcher.uninstall()
        this.isOpen = false
    }

    private fun updateRunning() {
        runningList.clearChildren()
        var i = 0
        for (backend in scriptingState.scriptingBackends) {
            var button = backend.metadata.displayName.toTextButton()
            val index = i
            runningList.add(button)
            if (i == scriptingState.activeBackend) {
                button.color = Color.GREEN
            }
            button.onClick({
                scriptingState.switchToBackend(index)
                updateRunning()
            })
            var termbutton = ImageGetter.getImage("OtherIcons/Stop")
            termbutton.onClick({
                val exc: Exception? = scriptingState.termBackend(index)
                updateRunning()
                if (exc != null) {
                	echo("Failed to stop ${backend.metadata.displayName} backend: ${exc.toString()}")
                }
            })
            runningList.add(termbutton.surroundWithCircle(40f)).row()
            i += 1
        }
    }

    private fun clear() {
        printHistory.clearChildren()
    }

    fun setText(text: String, cursormode: SetTextCursorMode=SetTextCursorMode.End) {
        val originaltext = inputText
        val originalcursorpos = cursorPos
        inputText = text
        when (cursormode) {
            (SetTextCursorMode.End) -> { cursorPos = inputText.length }
            (SetTextCursorMode.Unchanged) -> {}
            (SetTextCursorMode.Insert) -> { cursorPos = inputText.length-(originaltext.length-originalcursorpos) }
            (SetTextCursorMode.SelectAll) -> { throw UnsupportedOperationException("NotImplemented.") }
            (SetTextCursorMode.SelectAfter) -> { throw UnsupportedOperationException("NotImplemented.") }
        }
    }

    fun setScroll(x: Float, y: Float, animate: Boolean = true) {
        printScroll.scrollTo(x, y, 1f, 1f)
        if (!animate) {
            printScroll.updateVisualScroll()
        }
    }

    private fun echoHistory() {
        // Doesn't restore autocompletion. I guess that's by design. Autocompletion is a protocol/UI-level feature IMO, and not part of the emulated STDIN/STDOUT. Call `echo()` in `ScriptingState`'s `autocomplete` method if that's a problem.
        for (hist in scriptingState.getOutputHistory()) {
            echo(hist)
        }
    }

    private fun autocomplete() {
        val original = inputText
        val cursorpos = cursorPos
        var results = scriptingState.autocomplete(inputText, cursorpos)
        if (results.isHelpText) {
            echo(results.helpText)
            return
        }
        if (results.matches.size < 1) {
            return
        } else if (results.matches.size == 1) {
            setText(results.matches[0], SetTextCursorMode.Insert)
        } else {
            echo("")
            for (m in results.matches) {
                echo(m)
            }
            //var minmatch = original //Checking against the current input would prevent autoinsertion from working for autocomplete backends that support getting results from the middle of the current input.
            var minmatch = original.slice(0..cursorpos-1)
            var chosenresult = results.matches.first({true})
            for (l in original.length-1..chosenresult.length-1) {
                var longer = chosenresult.slice(0..l)
                if (results.matches.all { it.startsWith(longer) }) {
                    minmatch = longer
                } else {
                    break
                }
            }
            setText(minmatch + original.slice(cursorpos..original.length-1), SetTextCursorMode.Insert)
            // Splice the longest starting substring with the text after the cursor, to let autocomplete implementations work on the middle of current input.
        }
    }

    private fun navigateHistory(increment:Int) {
        setText(scriptingState.navigateHistory(increment), SetTextCursorMode.End)
    }

    private fun echo(text: String) {
        val label = Label(text, skin)
        val width = stage.width * 0.75f
        label.setWidth(width)
        label.setWrap(true)
        printHistory.add(label).left().bottom().width(width).padLeft(15f).row()
        val cells = printHistory.getCells()
        while (cells.size > scriptingState.maxOutputHistory && cells.size > 0) {
            val cell = cells.first()
            cell.getActor().remove()
            cells.removeValue(cell, true)
            //According to printHistory.getRows(), this isn't perfectly clean. The rows count still increases.
        }
        printHistory.invalidate()
        setScroll(0f,0f)
    }

    private fun run() {
        echo(scriptingState.exec(inputText))
        setText("")
    }

    fun clone(): ConsoleScreen {
        return ConsoleScreen(scriptingState, closeAction).also {
            it.inputText = inputText
            it.cursorPos = cursorPos
            it.setScroll(printScroll.getScrollX(), printScroll.getScrollY(), animate = false)
        }
    }

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) { // Right. Actually resizing seems painful.
            game.consoleScreen = clone()
            if (isOpen) {
                game.consoleScreen.openConsole()
                // If this leads to race conditions or some such due to occurring at the same time as other screens' resize methods, then probably close the ConsoleScreen() instead.
            }
        }
    }

    enum class SetTextCursorMode() {
        End(),
        Unchanged(),
        Insert(),
        SelectAll(),
        SelectAfter()
    }
}

// Screen, widget, or popup?
