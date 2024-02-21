package com.unciv.ui.components.input

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
}
