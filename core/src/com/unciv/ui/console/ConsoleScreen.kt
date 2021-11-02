package com.unciv.ui.console

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.Constants
import com.unciv.scripting.ScriptingBackend
import com.unciv.scripting.ScriptingBackendType
import com.unciv.scripting.ScriptingState
import com.unciv.ui.utils.*
import com.unciv.ui.utils.AutoScrollPane as ScrollPane


class ConsoleScreen(val scriptingState:ScriptingState, var closeAction: () -> Unit): CameraStageBaseScreen() {

    private val lineHeight = 30f

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

    init {
        
        backendsAdders.add("Launch new backend:".toLabel()).padRight(30f)
        for (backendtype in ScriptingBackendType.values()) {
            var backendadder = backendtype.metadata.displayname.toTextButton()
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
            var button = backend.metadata.displayname.toTextButton()
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
                scriptingState.termBackend(index)
                updateRunning()
            })
            runningList.add(termbutton.surroundWithCircle(40f)).row()
            i += 1
        }
    }
    
    private fun clear() {
        printHistory.clearChildren()
    }
    
    private fun setText(text:String) {
        inputField.setText(text)
        inputField.setCursorPosition(inputField.text.length)
    }
    
    private fun echoHistory() {
        for (hist in scriptingState.outputHistory) {
            echo(hist)
        }
    }
    
    private fun autocomplete() {
        var input = inputField.text
        var results = scriptingState.getAutocomplete(input)
        if (results.isHelpText) {
            echo(results.helpText)
            return
        }
        if (results.matches.size < 1) {
            return
        } else if (results.matches.size == 1) {
            setText(results.matches[0])
        } else {
            echo("")
            for (m in results.matches) {
                echo(m)
            }
            var minmatch = input
            var chosenresult = results.matches.first({true})
            for (l in input.length..chosenresult.length) {
                var longer = chosenresult.slice(0..l)
                if (results.matches.all { it.startsWith(longer) }) {
                    minmatch = longer
                } else {
                    break
                }
            }
            setText(minmatch)
        }
    }
    
    private fun navigateHistory(increment:Int) {
        setText(scriptingState.navigateHistory(increment))
    }
    
    private fun echo(text: String) {
        var label = Label(text, skin)
        var width = stage.width * 0.75f
        label.setWidth(width)
        label.setWrap(true)
        printHistory.add(label).left().bottom().width(width).padLeft(15f).row()
        printScroll.scrollTo(0f,0f,1f,1f)
    }
    
    private fun run() {
        echo(scriptingState.exec(inputField.text))
        setText("")
    }
    
    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) { // Right. Actually resizing seems painful.
            game.consoleScreen = ConsoleScreen(scriptingState, closeAction)
            if (isOpen) {
                game.consoleScreen.openConsole()
            }
        }
    }
}

// Screen, widget, or popup?
