package com.unciv.ui.components

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.ImageButton
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.UncivTooltip.Companion.addTooltip
import com.unciv.ui.images.ImageGetter

/** A button that captures keyboard keys and reports them through [onKeyHit] */
class KeyCapturingButton(
    private val onKeyHit: (keyCode: Int, control: Boolean) -> Unit
) : ImageButton(getStyle()) {
    companion object {
        private const val buttonSize = 36f
        private const val buttonImage = "OtherIcons/Keyboard"
        private val controlKeys = setOf(Input.Keys.CONTROL_LEFT, Input.Keys.CONTROL_RIGHT)

        private fun getStyle() = ImageButtonStyle().apply {
            val image = ImageGetter.getDrawable(buttonImage)
            imageUp = image
            imageOver = image.tint(Color.LIME)
        }
    }

    private var savedFocus: Actor? = null

    init {
        setSize(buttonSize, buttonSize)
        addTooltip("Hit the desired key now", 18f, targetAlign = Align.bottomRight)
        addListener(ButtonListener(this))
    }

    private class ButtonListener(private val myButton: KeyCapturingButton) : ClickListener() {
        private var controlDown = false

        override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
            if (myButton.stage == null) return
            myButton.savedFocus = myButton.stage.keyboardFocus
            myButton.stage.keyboardFocus = myButton
        }

        override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
            if (myButton.stage == null) return
            myButton.stage.keyboardFocus = myButton.savedFocus
            myButton.savedFocus = null
        }

        override fun keyDown(event: InputEvent?, keycode: Int): Boolean {
            if (keycode == Input.Keys.ESCAPE) return false
            if (keycode in controlKeys) {
                controlDown = true
            } else {
                myButton.onKeyHit(keycode, controlDown)
            }
            return true
        }

        override fun keyUp(event: InputEvent?, keycode: Int): Boolean {
            if (keycode == Input.Keys.ESCAPE) return false
            if (keycode in controlKeys)
                controlDown = false
            return true
        }
    }
}
