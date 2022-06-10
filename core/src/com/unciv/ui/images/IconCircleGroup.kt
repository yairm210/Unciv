package com.unciv.ui.images

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.utils.Align
import com.unciv.ui.utils.extensions.center

class IconCircleGroup(size: Float, val actor: Actor, resizeActor: Boolean = true, color: Color = Color.WHITE): Group(){
    val circle = ImageGetter.getCircle().apply {
        setSize(size, size)
        setColor(color)
    }

    init {
        isTransform = false // performance helper - nothing here is rotated or scaled
        setSize(size, size)
        addActor(circle)
        if (resizeActor) actor.setSize(size * 0.75f, size * 0.75f)
        actor.center(this)
        actor.setOrigin(Align.center)
        addActor(actor)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
}
