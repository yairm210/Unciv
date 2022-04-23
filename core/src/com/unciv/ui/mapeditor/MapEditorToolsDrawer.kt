package com.unciv.ui.mapeditor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.addSeparatorVertical
import kotlin.math.abs

class MapEditorToolsDrawer(
    tabs: MapEditorMainTabs,
    initStage: Stage
): Table(BaseScreen.skin) {
    companion object {
        const val handleWidth = 10f
    }

    var splitAmount = 1f
        set(value) {
            field = value
            reposition()
        }

    init {
        touchable = Touchable.childrenOnly
        addSeparatorVertical(Color.CLEAR, handleWidth) // the "handle"
        add(tabs)
            .height(initStage.height)
            .fill().top()
        pack()
        setPosition(initStage.width, 0f, Align.bottomRight)
        initStage.addActor(this)
        initStage.addListener(getListener(this))
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
            if (x !in drawer.x..(drawer.x + handleWidth)) return false
            draggingPointer = pointer
            lastX = x
            handleX = drawer.x
            oldSplitAmount = splitAmount
            return true
        }
        override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int) {
            if (pointer != draggingPointer) return
            draggingPointer = -1
            if (oldSplitAmount < 0f) return
            addAction(SplitAmountAction(drawer, if (splitAmount > 0.5f) 0f else 1f))
        }
        override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
            if (pointer != draggingPointer) return
            val delta = x - lastX
            val availWidth = stage.width - handleWidth
            handleX += delta
            lastX = x
            splitAmount = ((availWidth - handleX) / drawer.width).coerceIn(0f, 1f)
            if (oldSplitAmount >= 0f && abs(oldSplitAmount - splitAmount) >= 0.0001f) oldSplitAmount = -1f
        }
    }

    fun reposition() {
        if (stage == null) return
        val dx = stage.width + (1f - splitAmount) * (width - handleWidth)
        setPosition(dx, 0f, Align.bottomRight)
    }
}
