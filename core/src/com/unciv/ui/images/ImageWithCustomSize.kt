package com.unciv.ui.images

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable

/**
 * Either [Image] or [com.badlogic.gdx.scenes.scene2d.utils.Drawable] is badly written, as the [Image.drawable] always has its texture width/height
 * as min width/height, and [Image.getPrefWidth]/Height is always equal to its [Image.drawable]'s minWidth/Height.
 *
 * This results in an [Image.getPrefWidth]/Height call to completely ignore the width/height that was set by [setSize]. To fix this, this class provides a
 * custom implementation of [getPrefWidth] and [getPrefHeight].
 */
class ImageWithCustomSize(drawable: Drawable) : Image(drawable) {

    constructor(region: TextureRegion) : this(TextureRegionDrawable(region))

    override fun getPrefWidth(): Float {
        return if (width > 0f) {
            width
        } else if (drawable != null) {
            drawable.minWidth
        } else 0f
    }

    override fun getPrefHeight(): Float {
        return if (height > 0f) {
            height
        } else if (drawable != null) {
            drawable.minHeight
        } else 0f
    }
}
