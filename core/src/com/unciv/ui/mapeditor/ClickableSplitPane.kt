package com.unciv.ui.mapeditor

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane.SplitPaneStyle
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import kotlin.math.abs

class ClickableSplitPane(
    private val widget: Actor? = null,
    skin: Skin
): Table() {
    var minAmount = 0.4f
    var maxAmount = 1f
    var clickMinAmount = 0.67f
    var clickMaxAmount = maxAmount
    var cursorOverHandle = false

    var style: SplitPaneStyle = skin["default-horizontal", SplitPaneStyle::class.java]!!
        set(value) {
            field = value
            invalidateHierarchy()
            handleWidth = style.handle.minWidth
        }

    var splitAmount = clickMinAmount
        set(value) {
            field = value
            invalidate()
        }

    private val widgetBounds = Rectangle()
    private val handleBounds = Rectangle()
    private var handleWidth = 0f

    private var lastX = 0f
    private var handleX = 0f

    private val widgetCell: Cell<Actor>

    private class SplitAmountAction(
        private val splitPane: ClickableSplitPane,
        endAmount: Float
    ): FloatAction(splitPane.splitAmount, endAmount, 0.333f) {
        override fun act(delta: Float): Boolean {
            val result = super.act(delta)
            splitPane.splitAmount = value
            return result
        }
    }

    init {
        style.handle.minWidth = 10f
        handleWidth = 10f
        add().apply {
            width = handleWidth
            background = style.handle
        }
        widgetCell = add(widget)

        addListener(object : InputListener() {
            var draggingPointer = -1
            var oldSplitAmount = -1f

            override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int ): Boolean {
                if (draggingPointer != -1) return false
                if (pointer == 0 && button != 0) return false
                if (!handleBounds.contains(x, y)) return false
                draggingPointer = pointer
                lastX = x
                handleX = handleBounds.x
                oldSplitAmount = splitAmount
                return true
            }

            override fun touchUp(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int) {
                if (pointer != draggingPointer) return
                draggingPointer = -1
                if (oldSplitAmount < 0f) return
                if (abs(oldSplitAmount - splitAmount) >= 0.0001f) return
                val newSplit = if (splitAmount > (clickMaxAmount + clickMinAmount) / 2) clickMinAmount else clickMaxAmount
                addAction(SplitAmountAction(this@ClickableSplitPane, newSplit))
            }

            override fun touchDragged(event: InputEvent, x: Float, y: Float, pointer: Int) {
                if (pointer != draggingPointer) return
                val delta = x - lastX
                val availWidth = width - handleWidth
                val dragX = handleX + delta
                handleX = dragX
                lastX = x
                splitAmount = dragX.coerceIn(0f, availWidth) / availWidth
            }

            override fun mouseMoved(event: InputEvent, x: Float, y: Float): Boolean {
                cursorOverHandle = handleBounds.contains(x, y)
                return false
            }
        })
    }

    override fun layout() {
        clampSplitAmount()
        calculateBoundsAndPositions()
        (widget as? Layout)?.validate()
        widgetCell.actorWidth = widgetBounds.width
        widgetCell.actorHeight = widgetBounds.height
        setBounds(handleBounds.x, handleBounds.y, handleWidth + widgetBounds.width, widgetBounds.height)
    }

    private fun calculateBoundsAndPositions() {
        val availWidth = stage.width - handleWidth
        val leftAreaWidth = availWidth * splitAmount
        val rightAreaWidth = availWidth - leftAreaWidth
        widgetBounds.set(leftAreaWidth + handleWidth, 0f, rightAreaWidth, stage.height)
        handleBounds.set(leftAreaWidth, 0f, handleWidth, stage.height)
    }

/*
    override fun draw(batch: Batch, parentAlpha: Float) {
        val stage = stage ?: return
        validate()
        val alpha = color.a * parentAlpha
        applyTransform(batch, computeTransform())
        if (widget != null && widget.isVisible) {
            batch.flush()
            stage.calculateScissors(widgetBounds, tempScissors)
            if (ScissorStack.pushScissors(tempScissors)) {
                widget.draw(batch, alpha)
                batch.flush()
                ScissorStack.popScissors()
            }
        }
        batch.setColor(color.r, color.g, color.b, alpha)
        style.handle.draw(batch, handleBounds.x, handleBounds.y, handleBounds.width, handleBounds.height)
        resetTransform(batch)
    }
*/

    private fun clampSplitAmount() {
        splitAmount = splitAmount.coerceIn(minAmount, maxAmount)
    }
}
