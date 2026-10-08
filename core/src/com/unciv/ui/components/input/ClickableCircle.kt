package com.unciv.ui.components.input

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable

/**
 *  Invisible Widget that supports detecting clicks in a circular area.
 *
 *  (An Image Actor does not respect alpha for its hit area, it's always square, but we want a clickable _circle_)
 *
 *  Usage: instantiate, position and overlay on something with [addActor], add listener using [onActivation].
 *  Does not implement Layout at the moment - usage e.g. in a Table Cell may need that.
 *
 *  Note this is a [Group] that is supposed to have no [children] - as a simple [Actor] the Scene2D framework won't know to call our [hit] method.
 */
class ClickableCircle(size: Float) : Group() {
    private val center = Vector2(size / 2, size / 2)
    private val maxDst2 = size * size / 4 // squared radius

    init {
        touchable = Touchable.enabled
        setSize(size, size)
    }

    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? {
        return if (center.dst2(x, y) < maxDst2) this else null
    }

    override fun drawDebugBounds(shapes: ShapeRenderer?) {
        if (!debug || shapes == null || stage == null) return
        shapes.set(ShapeType.Line)
        shapes.color = stage.debugColor
        shapes.circle(x + width * 0.5f, y + height * 0.5f, width * 0.5f)
        shapes.color = Color.GRAY
        shapes.color.a = 0.33f
        shapes.rect(x, y, width, height)
    }
}
