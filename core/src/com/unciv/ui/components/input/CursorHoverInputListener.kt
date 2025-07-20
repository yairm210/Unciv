package com.unciv.ui.components.input

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener

/**
 * Provides an input listener that will change the cursor when the element is hovered.
 */
class CursorHoverInputListener(
    private val hoverCursor: Cursor.SystemCursor = Cursor.SystemCursor.Hand,
    private val exitCursor: Cursor.SystemCursor = Cursor.SystemCursor.Arrow
) : InputListener() {
    override fun enter(event: InputEvent?, x: Float, y: Float, pointer: Int, fromActor: Actor?) {
        Gdx.graphics.setSystemCursor(hoverCursor)
    }
    override fun exit(event: InputEvent?, x: Float, y: Float, pointer: Int, toActor: Actor?) {
        Gdx.graphics.setSystemCursor(exitCursor)
    }
}
