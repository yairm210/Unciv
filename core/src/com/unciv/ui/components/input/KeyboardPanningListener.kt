package com.unciv.ui.components.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.RepeatAction
import com.badlogic.gdx.scenes.scene2d.ui.TextField
import com.badlogic.gdx.utils.Disposable
import com.unciv.ui.components.extensions.isControlKeyPressed
import com.unciv.ui.components.widgets.ZoomableScrollPane

class KeyboardPanningListener(
    private val mapHolder: ZoomableScrollPane,
    allowWASD: Boolean
) : InputListener(), Disposable {
    companion object {
        /** The delay between panning steps */
        const val deltaTime = 0.01f
    }

    private val pressedKeys = mutableSetOf<Int>()
    private var infiniteAction: RepeatAction? = null

    private val keycodeUp = KeyboardBindings[KeyboardBinding.PanUp].code
    private val keycodeLeft = KeyboardBindings[KeyboardBinding.PanLeft].code
    private val keycodeDown = KeyboardBindings[KeyboardBinding.PanDown].code
    private val keycodeRight = KeyboardBindings[KeyboardBinding.PanRight].code
    private val keycodeUpAlt = KeyboardBindings[KeyboardBinding.PanUpAlternate].code
    private val keycodeLeftAlt = KeyboardBindings[KeyboardBinding.PanLeftAlternate].code
    private val keycodeDownAlt = KeyboardBindings[KeyboardBinding.PanDownAlternate].code
    private val keycodeRightAlt = KeyboardBindings[KeyboardBinding.PanRightAlternate].code

    private val allowedKeys =
            setOf(keycodeUp, keycodeLeft, keycodeDown, keycodeRight) + (
                if (allowWASD) setOf(keycodeUpAlt, keycodeLeftAlt, keycodeDownAlt, keycodeRightAlt)
                else setOf()
            )

    override fun keyDown(event: InputEvent, keycode: Int): Boolean {
        if (event.target is TextField) return false
        if (keycode !in allowedKeys) return false
        // Without the following Ctrl-S would leave WASD map scrolling stuck
        // _Not_ obsolete with keyboard shortcut refactoring
        if (Gdx.input.isControlKeyPressed()) return false
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
                keycodeUp, keycodeUpAlt -> deltaY -= 1f
                keycodeDown, keycodeDownAlt -> deltaY += 1f
                keycodeLeft, keycodeLeftAlt -> deltaX += 1f
                keycodeRight, keycodeRightAlt -> deltaX -= 1f
            }
        }
        mapHolder.doKeyOrMousePanning(deltaX, deltaY)
    }

    override fun dispose() {
        stopLoop()
    }
}
