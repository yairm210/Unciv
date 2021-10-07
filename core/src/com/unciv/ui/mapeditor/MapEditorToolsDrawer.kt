package com.unciv.ui.mapeditor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.addSeparatorVertical
import kotlin.math.abs

class MapEditorToolsDrawer(
    tabs: MapEditorMainTabs,
    initStage: Stage
): Table(CameraStageBaseScreen.skin) {
    // Distances measured in fractions of stage width from right edge
    var minAmount = 0f
    var maxAmount = 0.67f
    var clickMinAmount = minAmount
    var clickMaxAmount = 0.33f

    var splitAmount = clickMinAmount
        set(value) {
            field = value
            reposition()
        }

    private val handle: Image
    private val tabsCell: Cell<MapEditorMainTabs>

    init {
        touchable = Touchable.enabled
        handle = addSeparatorVertical(Color.CLEAR, 10f).actor
        tabsCell = add(tabs)
            .width(initStage.width * clickMaxAmount)
            .height(initStage.height)
            .fill().top()
        pack()
        setPosition(initStage.width, 0f, Align.bottomRight)
        initStage.addActor(this)

        handle.addListener(getListener(this))
    }

    private class SplitAmountAction(
        private val drawer: MapEditorToolsDrawer,
        endAmount: Float
    ): FloatAction(drawer.splitAmount, endAmount, 0.333f) {
        override fun act(delta: Float): Boolean {
            val result = super.act(delta)
            drawer.splitAmount = value
            return result
        }
    }

    private fun getListener(drawer: MapEditorToolsDrawer) = object : InputListener() {
        private var draggingPointer = -1
        private var oldSplitAmount = -1f
        private var lastX = 0f
        private var handleX = 0f

        override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int ): Boolean {
            if (draggingPointer != -1) return false
            if (pointer == 0 && button != 0) return false
            draggingPointer = pointer
            lastX = x
            handleX = stage.width * (1f - splitAmount) - 10f
            oldSplitAmount = splitAmount
            return true
        }
        override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int) {
            if (pointer != draggingPointer) return
            draggingPointer = -1
            if (oldSplitAmount < 0f) return
            if (abs(oldSplitAmount - splitAmount) >= 0.0001f) return
            val newSplit = if (splitAmount > (clickMaxAmount + clickMinAmount) / 2) clickMinAmount else clickMaxAmount
            addAction(SplitAmountAction(drawer, newSplit))
        }
        override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
            if (pointer != draggingPointer) return
            val delta = x - lastX
            val availWidth = stage.width - 10f
            val dragX = handleX + delta
            handleX = dragX
            lastX = x
            splitAmount = ((availWidth - dragX) / availWidth).coerceIn(minAmount, maxAmount)
        }
    }

    fun reposition() {
        if (stage == null) return
        tabsCell.width(stage.width * splitAmount)
        setPosition(stage.width, 0f, Align.bottomRight)
    }
}
