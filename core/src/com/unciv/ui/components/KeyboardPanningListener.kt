package com.unciv.ui.components

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.RepeatAction
import com.badlogic.gdx.scenes.scene2d.ui.TextField

class KeyboardPanningListener(
    private val mapHolder: ZoomableScrollPane,
    allowWASD: Boolean
) : InputListener() {
    companion object {
        /** The delay between panning steps */
        const val deltaTime = 0.01f
    }

    private val pressedKeys = mutableSetOf<Int>()
    private var infiniteAction: RepeatAction? = null
    private val allowedKeys =
            setOf(Input.Keys.UP, Input.Keys.DOWN, Input.Keys.LEFT, Input.Keys.RIGHT) + (
                if (allowWASD) setOf(Input.Keys.W, Input.Keys.S, Input.Keys.A, Input.Keys.D)
                else setOf()
            )

    override fun keyDown(event: InputEvent, keycode: Int): Boolean {
        if (event.target is TextField) return false
        if (keycode !in allowedKeys) return false
        // Without the following Ctrl-S would leave WASD map scrolling stuck
        // Might be obsolete with keyboard shortcut refactoring
        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) return false
        if (Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT)) return false
        pressedKeys.add(keycode)
        startLoop()
        return true
    }

    override fun keyUp(event: InputEvent?, keycode: Int): Boolean {
        if (keycode !in allowedKeys) return false
        pressedKeys.remove(keycode)
        if (pressedKeys.isEmpty()) stopLoop()
        return true
    }

    private fun startLoop() {
        if (infiniteAction != null) return
        // create a copy of the action, because removeAction() will destroy this instance
        infiniteAction = Actions.forever(
            Actions.delay(
                deltaTime,
                Actions.run { whileKeyPressedLoop() })
        )
        mapHolder.addAction(infiniteAction)
    }

    private fun stopLoop() {
        if (infiniteAction == null) return
        // stop the loop otherwise it keeps going even after removal
        infiniteAction?.finish()
        // remove and nil the action
        mapHolder.removeAction(infiniteAction)
        infiniteAction = null
    }

    private fun whileKeyPressedLoop() {
        var deltaX = 0f
        var deltaY = 0f
        for (keycode in pressedKeys) {
            when (keycode) {
                Input.Keys.W, Input.Keys.UP -> deltaY -= 1f
                Input.Keys.S, Input.Keys.DOWN -> deltaY += 1f
                Input.Keys.A, Input.Keys.LEFT -> deltaX += 1f
                Input.Keys.D, Input.Keys.RIGHT -> deltaX -= 1f
            }
        }
        mapHolder.doKeyOrMousePanning(deltaX, deltaY)
    }
}
