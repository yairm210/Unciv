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
 *  - Usage: instantiate, position and overlay on something with [addActor], add listener using [onActivation].
 *  - This is a non-transforming [Group], and adding [children] will not work.
 *  - Does not implement Layout at the moment - usage e.g. in a Table Cell may need that.
 *  - Scene2D debug mode will draw the hit circle and a faint rectangle for the normal bounds.
 *  - Note this is a [Group] not a simple [Actor] so the Scene2D framework knows to call our [hit] method.
 *
 *  @param size Pre-size this and calculate hit detection variables - resizing later is supported, however.
 */
class ClickableCircle(size: Float) : Group() {
    private var center = Vector2()
    private var maxDst2 = 0f

    init {
        touchable = Touchable.enabled
        isTransform = false
        setSize(size, size)
    }

    override fun sizeChanged() {
        center.set(width / 2, height / 2)
        val size = height.coerceAtLeast(width)
        maxDst2 = size * size / 4 // squared radius
    }

    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? {
        return if (center.dst2(x, y) < maxDst2) this else null
    }

    override fun draw(batch: Batch, parentAlpha: Float) {}

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
