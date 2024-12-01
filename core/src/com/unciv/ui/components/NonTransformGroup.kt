package com.unciv.ui.components

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Group

/** Performance optimized for groups with no transform */
open class NonTransformGroup: Group(){
    init {
        isTransform = false
    }
    // Reduces need to check isTransform twice in every draw call
    override fun draw(batch: Batch?, parentAlpha: Float) = this.drawChildren(batch, parentAlpha)
}
