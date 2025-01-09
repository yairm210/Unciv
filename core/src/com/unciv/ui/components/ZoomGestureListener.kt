package com.unciv.ui.components

import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.EventListener
import com.badlogic.gdx.scenes.scene2d.InputEvent

open class ZoomGestureListener(
    halfTapSquareSize: Float,
    tapCountInterval: Float,
    longPressDuration: Float,
    maxFlingDelay: Float,
    stageSize: () -> Vector2
) : EventListener {
    
    private val detector: GestureDetector

    constructor(stageSize: () -> Vector2) : this(
        20f,
        0.4f,
        1.1f,
        Int.MAX_VALUE.toFloat(),
        stageSize
    )

    init {
        detector = GestureDetector(
            halfTapSquareSize,
            tapCountInterval,
            longPressDuration,
            maxFlingDelay,
            object : GestureDetector.GestureAdapter() {

                // focal point, the center of the two pointers where zooming should be directed to
                private var lastFocus: Vector2? = null
                // distance between the two pointers performing a pinch
                private var lastDistance = 0f
                
                override fun pinch(
                    stageInitialPointer1: Vector2,
                    stageInitialPointer2: Vector2,
                    stagePointer1: Vector2,
                    stagePointer2: Vector2
                ): Boolean {
                    
                    if (lastFocus == null) {
                        lastFocus = stageInitialPointer1.cpy().add(stageInitialPointer2).scl(.5f)
                        lastDistance = stageInitialPointer1.dst(stageInitialPointer2)
                    }
                    
                    // the current focal point is the center of the two pointers
                    val currentFocus = stagePointer1.cpy().add(stagePointer2).scl(.5f)
                    
                    // translation caused by moving the focal point
                    val translation = currentFocus.cpy().sub(lastFocus)
                    lastFocus = currentFocus.cpy()

                    // scale change caused by changing distance of the two pointers
                    val currentDistance = stagePointer1.dst(stagePointer2)
                    val scaleChange = currentDistance / lastDistance
                    lastDistance = currentDistance
                    
                    // Calculate the translation (dx, dy) needed to direct the zoom towards to
                    // current focal point. Without this correction, the zoom would be directed
                    // towards the center of the stage.
                    // - First we calculate the distance from the stage center to the focal point.
                    // - We then calculate how much the scaling affects that distance by multiplying
                    //   with scaleChange - 1.
                    val dx = (stageSize().x / 2 - currentFocus.x) * (scaleChange - 1)
                    val dy = (stageSize().y / 2 - currentFocus.y) * (scaleChange - 1)
                    
                    // Add the translation caused by changing the scale (dx, dy) to the translation
                    // caused by changing the position of the focal point.
                    translation.add(dx, dy)
                    
                    this@ZoomGestureListener.pinch(translation, scaleChange)
                    
                    return true
                }

                override fun pinchStop() {
                    lastFocus = null
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
                detector.touchUp(event.stageX, event.stageY, event.pointer, event.button)
                return true
            }
            InputEvent.Type.touchDragged -> {
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
    /** [translation] in stage coordinates */
    open fun pinch(translation: Vector2, scaleChange: Float) {}
    open fun pinchStop() {}
}
