package com.unciv.ui.consolescreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextField // Ooh. TextArea could be nice. (Probably overkill and horrible if done messily, though.)
import com.unciv.Constants
import com.unciv.models.modscripting.*
import com.unciv.scripting.ScriptingBackendType
import com.unciv.scripting.ScriptingState
import com.unciv.scripting.utils.ScriptingErrorHandling
import com.unciv.scripting.sync.makeScriptingRunName
import com.unciv.ui.utils.*
import com.unciv.ui.utils.AutoScrollPane as ScrollPane
import kotlin.math.max
import kotlin.math.min

// Could also abstract this entire class as an extension on Table, letting it be used as screen, popup, or random widget, but don't really need to.

class ConsoleScreen(var closeAction: () -> Unit): BaseScreen() {

    private val layoutTable = Table()

    private val topBar = Table()
    private val backendsScroll: ScrollPane
    private val backendsAdders = Table()
    private val closeButton = Constants.close.toTextButton()

    private val middleSplit: SplitPane
    private val printScroll: ScrollPane
    private val printHistory = Table()
    private val runningScroll: ScrollPane
    private val runningContainer = Table()
    private val runningList = Table() // TODO: Scroll.

    private val inputBar = Table()
    private val inputField = TextField("", skin)

    private val inputControls = Table()
    private val tabButton = "TAB".toTextButton() // TODO: Translation.
    private val upButton = ImageGetter.getImage("OtherIcons/Up")
    private val downButton = ImageGetter.getImage("OtherIcons/Down")
    private val runButton = "ENTER".toTextButton() // TODO: Translation.

    private val layoutUpdators = ArrayList<() -> Unit>()
    private var isOpen = false

    private var warningAccepted = false

    var inputText: String
        get() = inputField.text
        set(value: String) { inputField.setText(value) }

    var cursorPos: Int
        get() = inputField.getCursorPosition()
        set(value: Int) {
            inputField.setCursorPosition(max(0, min(inputText.length, value)))
        }

    private var lastSeenScriptingBackendCount = 0

    init {

        backendsAdders.add("Launch new backend:".toLabel()).padRight(30f).padLeft(20f) // TODO: Translation.
        for (backendtype in ScriptingBackendType.values()) {
            var backendadder = backendtype.metadata.displayName.toTextButton() // Hm. Should this be translated/translatable? I suppose it already is translatable in OptionsPopup too. And in the running list— So basically everywhere it's shown.
            backendadder.onClick {
                val spawned = ScriptingState.spawnBackend(backendtype)
//                echo(spawned.motd)
                val startup = game.settings.scriptingConsoleStartups[backendtype.metadata.displayName]!!
                if (startup.isNotBlank()) {
                    ScriptingState.switchToBackend(spawned.backend)
                    exec(startup)
                }
                updateRunning()
            }
            backendsAdders.add(backendadder)
        }
        backendsScroll = ScrollPane(backendsAdders)

        backendsAdders.left()

        val cell_backendsScroll = topBar.add(backendsScroll)
        layoutUpdators.add { cell_backendsScroll.minWidth(stage.width - closeButton.getPrefWidth()) }
        topBar.add(closeButton)

        printHistory.left()
        printHistory.bottom()
        printScroll = ScrollPane(printHistory)

        runningContainer.add("Active Backends:".toLabel()).padBottom(5f).row() // TODO: Translation.
        runningContainer.add(runningList)
        runningScroll = ScrollPane(runningContainer)

        middleSplit = SplitPane(printScroll, runningScroll, false, skin)
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

        runButton.onClick(::run)
        keyPressDispatcher[Input.Keys.ENTER] = ::run
        keyPressDispatcher[Input.Keys.NUMPAD_ENTER] = ::run

        tabButton.onClick(::autocomplete)
        keyPressDispatcher[Input.Keys.TAB] = ::autocomplete

        upButton.onClick { navigateHistory(1) }
        keyPressDispatcher[Input.Keys.UP] = { navigateHistory(1) }
        downButton.onClick { navigateHistory(-1) }
        keyPressDispatcher[Input.Keys.DOWN] = { navigateHistory(-1) }

        onBackButtonClicked(::closeConsole)
        closeButton.onClick(::closeConsole)

        updateLayout()

        stage.addActor(layoutTable)

        echoHistory()

        updateRunning()

        layoutTable.addListener( // Check whenever receiving input event if there's any super obvious need to update the running backends list. Really I don't see the list changing on its own except in the mod manager screen, so this shouldn't ever be used in that case, but as I'm developing the mod script loader I'm noticing some missed backends, and a single equality check every couple hundred seconds on average doesn't feel expensive to make sure it's always up to date.
            object: InputListener() {
                override fun handle(e: Event): Boolean {
                    val size = ScriptingState.scriptingBackends.size
                    if (lastSeenScriptingBackendCount != size) {
                        lastSeenScriptingBackendCount = size
                        updateRunning()
                    }
                    return false
                }
            }
        )
    }

    private fun updateLayout() {
        for (func in layoutUpdators) {
            func()
        }
    }

    private fun showWarningPopup() {
        YesNoPopup(
            "{WARNING}\n\n{The Unciv scripting API is a HIGHLY EXPERIMENTAL feature intended for advanced users!}\n{It may be possible to damage your device and files by running malicious or poorly designed code!}\n\n{Do you wish to continue?}",
            { // TODO: Translation.
                warningAccepted = true
            },
            this,
            {
                closeConsole()
                game.settings.enableScriptingConsole = false
                game.settings.save()
            }
        ).open(true)
    }

    fun openConsole() {
        game.setScreen(this)
        keyPressDispatcher.install(stage) //TODO: Can this be moved to UncivGame.setScreen?
        ScriptingState.consoleScreenListener = {
            Gdx.app.postRunnable { echo(it) } // Calling ::echo directly triggers OpenGL stuff, which means crashes when ScriptingState isn't running in the main thread.
        }
        stage.setKeyboardFocus(inputField)
        inputField.getOnscreenKeyboard().show(true)
        updateRunning()
        this.isOpen = true
        if (game.settings.showScriptingConsoleWarning && !warningAccepted) {
            showWarningPopup()
        }
        ModScriptingRegistrationManager.activeMods
//        ModScriptingRunManager.runHandler(HANDLER_DEFINITIONS[ContextId.ConsoleScreen, HandlerId.after_open], this)
    }

    fun closeConsole() {
//        ModScriptingRunManager.runHandler(HANDLER_DEFINITIONS[ContextId.ConsoleScreen, HandlerId.before_close], this)
        closeAction()
        keyPressDispatcher.uninstall()
        this.isOpen = false
    }

    private fun updateRunning() {
        runningList.clearChildren()
        for (backend in ScriptingState.scriptingBackends) {
            var button = backend.metadata.displayName.toTextButton()
            if (backend.displayNote != null) {
                button.cells.last().row()
                button.add(backend.displayNote.toString().toLabel(fontColor = Color.LIGHT_GRAY, fontSize = 16))
            }
            runningList.add(button)
            if (backend === ScriptingState.activeBackend) {
                button.color = Color.GREEN
            }
            button.onClick {
                ScriptingState.switchToBackend(backend)
                updateRunning()
            }
            var termbutton = ImageGetter.getImage("OtherIcons/Stop")
            val terminable = backend.userTerminable // Grey out if not terminable.
            if (!terminable) { // TODO: There's a Button.disable() extension function.
                termbutton.setColor(0.7f, 0.7f, 0.7f, 0.3f)
//                termbutton.color.a = 0.3f
            }
            termbutton.onClick {
                if (backend.userTerminable) {
                    val exc: Exception? = ScriptingState.termBackend(backend)
                    if (exc != null) {
                        echo("Failed to stop ${backend.metadata.displayName} backend: ${exc.toString()}") // TODO: Translation? I mean, probably not, really.
                    }
                }
                updateRunning()
            }
            runningList.add(termbutton.surroundWithCircle(40f, color = if (terminable) Color.WHITE else Color(1f, 1f, 1f, 0.5f))).row()
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
        for (hist in ScriptingState.getOutputHistory()) {
            echo(hist)
        }
    }

    private fun autocomplete() {
        val original = inputText
        val cursorpos = cursorPos
        var results = ScriptingState.autocomplete(inputText, cursorpos)
        if (results.helpText != null) {
            echo(results.helpText!!)
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
            for (l in original.lastIndex..chosenresult.lastIndex) {
                var longer = chosenresult.slice(0..l)
                if (results.matches.all { it.startsWith(longer) }) {
                    minmatch = longer
                } else {
                    break
                }
            }
            setText(minmatch + original.slice(cursorpos..original.lastIndex), SetTextCursorMode.Insert)
            // TODO: Splice the longest starting substring with the text after the cursor, to let autocomplete implementations work on the middle of current input.
        }
    }

    private fun navigateHistory(increment: Int) {
        setText(ScriptingState.navigateHistory(increment), SetTextCursorMode.End)
    }

    private fun echo(text: String) {
        val label = Label(text, skin)
        val width = stage.width * 0.75f
        label.setWidth(width)
        label.setWrap(true)
        printHistory.add(label).left().bottom().width(width).padLeft(15f).row()
        val cells = printHistory.getCells()
        while (cells.size > ScriptingState.maxOutputHistory && cells.size > 0) { // TODO: Move to separate method.
            val cell = cells.first()
            cell.getActor().remove()
            cells.removeValue(cell, true)
            //According to printHistory.getRows(), this isn't perfectly clean. The rows count still increases.
        }
        printHistory.invalidate()
        setScroll(0f,0f)
    }

    private fun exec(command: String) {
        val name = makeScriptingRunName(this::class.simpleName, ScriptingState.activeBackend)
        val execResult = ScriptingState.exec(command, asName = name)
//        echo(execResult.resultPrint)
        setText("")
        if (execResult.isException) {
            ScriptingErrorHandling.printConsolePlayerScriptFailure(execResult.resultPrint, asName = name)
            ToastPopup("{Exception in} ${name}.", this) // TODO: Translation.
        }
    }

    private fun run() {
        exec(inputText)
    }

    fun clone(): ConsoleScreen {
        return ConsoleScreen(closeAction).also {
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

    enum class SetTextCursorMode {
        End,
        Unchanged,
        Insert,
        SelectAll,
        SelectAfter
    }
}
