package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.NonTransformGroup
import com.unciv.ui.components.extensions.center
import com.unciv.ui.images.ImageGetter

open class IconCircleGroup<T: Actor>(
    size: Float,
    val actor: T,
    resizeActor: Boolean = true,
    color: Color = Color.WHITE,
    circleImage: String = "OtherIcons/Circle"
): NonTransformGroup() {

    val circle = ImageGetter.getImage(circleImage).apply {
        setSize(size, size)
        setColor(color)
    }

    init {
        setSize(size, size)
        addActor(circle)
        if (resizeActor) actor.setSize(size * 0.75f, size * 0.75f)
        actor.center(this)
        actor.setOrigin(Align.center)
        addActor(actor)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha * color.a)
}
