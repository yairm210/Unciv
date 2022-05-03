package com.unciv.ui.images

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.Drawable

/**
 * Image that is constrained by the size of its parent. Instead of spilling over if it is larger than the parent, the spilling parts simply get clipped off.
 */
class ClippingImage(drawable: Drawable) : Image(drawable) {
    override fun draw(batch: Batch, parentAlpha: Float) {
        batch.flush()
        if (clipBegin(0f, 0f, parent.width, parent.height)) {
            super.draw(batch, parentAlpha)
            batch.flush()
            clipEnd()
        }
    }
}
