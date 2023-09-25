package com.unciv.ui.components

import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.EventListener
import com.badlogic.gdx.scenes.scene2d.InputEvent

open class ZoomGestureListener(
    halfTapSquareSize: Float, tapCountInterval: Float, longPressDuration: Float, maxFlingDelay: Float
) : EventListener {
    val detector: GestureDetector
    var event: InputEvent? = null

    constructor() : this(20f, 0.4f, 1.1f, Int.MAX_VALUE.toFloat())

    init {
        detector = GestureDetector(
            halfTapSquareSize,
            tapCountInterval,
            longPressDuration,
            maxFlingDelay,
            object : GestureDetector.GestureAdapter() {

                override fun zoom(initialDistance: Float, distance: Float): Boolean {
                    this@ZoomGestureListener.zoom(initialDistance, distance)
                    return true
                }

                override fun pinch(
                    stageInitialPointer1: Vector2,
                    stageInitialPointer2: Vector2,
                    stagePointer1: Vector2,
                    stagePointer2: Vector2
                ): Boolean {
                    this@ZoomGestureListener.pinch()
                    return true
                }

                override fun pinchStop() {
                    this@ZoomGestureListener.pinchStop()
                }
            })
    }


    override fun handle(event: Event?): Boolean {
        if (event !is InputEvent)
            return false
        when (event.type) {
            InputEvent.Type.touchDown -> {
                detector.touchDown(event.stageX, event.stageY, event.pointer, event.button)
                if (event.touchFocus) event.stage.addTouchFocus(
                    this, event.listenerActor, event.target,
                    event.pointer, event.button
                )
                return true
            }
            InputEvent.Type.touchUp -> {
                if (event.isTouchFocusCancel) {
                    detector.reset()
                    return false
                }
                this.event = event
                detector.touchUp(event.stageX, event.stageY, event.pointer, event.button)
                return true
            }
            InputEvent.Type.touchDragged -> {
                this.event = event
                detector.touchDragged(event.stageX, event.stageY, event.pointer)
                return true
            }
            InputEvent.Type.scrolled -> {
                return scrolled(event.scrollAmountX, event.scrollAmountY)
            }
            else -> return false
        }
    }

    open fun scrolled(amountX: Float, amountY: Float): Boolean { return false }
    open fun zoom(initialDistance: Float, distance: Float) {}
    open fun pinch() {}
    open fun pinchStop() {}
}
