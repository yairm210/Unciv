package com.unciv.ui.console

import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.unciv.Constants
import com.unciv.console.ConsoleState
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.ui.utils.*
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class ConsoleScreen(val consoleState:ConsoleState, closeAction: ()->Unit): CameraStageBaseScreen() {

    private val lineHeight = 30f

    private val layoutTable: Table = Table()
    private val topBar: Table = Table()
    private val printHistory: Table = Table()//ScrollPane = ScrollPane()
    private var printScroll: ScrollPane
    private val inputBar: Table = Table()
    private val inputField: TextField = TextField("", skin)
    private val runButton: TextButton = "Enter".toTextButton()
    private val closeButton: TextButton = Constants.close.toTextButton()

    init {
        onBackButtonClicked(closeAction)
        closeButton.onClick(closeAction)
        
        topBar.add(closeButton)
        
        printHistory.left()
        printHistory.bottom()
        printScroll = ScrollPane(printHistory)
        
        inputBar.add(inputField).minWidth(stage.width - runButton.getPrefWidth())
        inputBar.add(runButton)
        
        layoutTable.setSize(stage.width, stage.height)
        
        layoutTable.add(topBar).minWidth(stage.width).row()
        
        layoutTable.add(printScroll).minWidth(stage.width).minHeight(stage.height - topBar.getPrefHeight() - inputBar.getPrefHeight()).row()
        
        layoutTable.add(inputBar)
        
        runButton.onClick({ this.run() })
        keyPressDispatcher[Input.Keys.ENTER] = { this.run() }
        keyPressDispatcher[Input.Keys.NUMPAD_ENTER] = { this.run() }
        
        stage.addActor(layoutTable)
        
        echoHistory()
    }
    
    private fun clear() {
        printHistory.clearChildren()
    }
    
    private fun echoHistory() {
        for (hist in consoleState.outputHistory) {
            echo(hist)
        }
    }
    
    private fun echo(text: String) {
        printHistory.add(text.toLabel()).left().bottom().padLeft(15f).row()
    }
    
    private fun run() {
        echo(consoleState.exec(inputField.text))
        inputField.setText("")
    }
}

// Screen, or widget? Screen would create whole new context for hotkeys and such, I think? Widget would be nice for 
