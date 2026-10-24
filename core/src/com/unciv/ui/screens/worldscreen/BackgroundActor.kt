package com.unciv.ui.screens.worldscreen

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.utils.Align
import com.unciv.ui.images.ImageGetter

/** An Actor that just draws a Drawable [background], preferably a [NinePatchDrawable] created
 *  by [ImageGetter.getRoundedEdgeRectangle], meant to work in Table Cells and to be overlaid with other Widgets.
 *  The drawable's center can be moved to any of the corners or vertex centers using `align`, which will also scale the
 *  drawable up by factor 2 and clip to the original rectangle. This can be used to draw rectangles with one or two rounded corners.
 *  @param align An [Align] constant - In which corner of the [BackgroundActor] rectangle the center of the [background] should be.
 */
class BackgroundActor(val background: Drawable, align: Int) : Actor() {
    private val widthMultiplier = if (Align.isCenterHorizontal(align)) 1f else 2f
    private val heightMultiplier = if (Align.isCenterVertical(align)) 1f else 2f
    private val noClip = Align.isCenterHorizontal(align) && Align.isCenterVertical(align)
    private val xOffset = if (Align.isLeft(align)) 0.5f else 0f
    private val yOffset = if (Align.isBottom(align)) 0.5f else 0f

    init {
        touchable = Touchable.disabled
    }

    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? = null

    override fun draw(batch: Batch?, parentAlpha: Float) {
        if (batch == null) return
        if (noClip) return drawBackground(batch, parentAlpha)

        batch.flush()
        if (!clipBegin()) return
        val w = width * widthMultiplier
        val h = height * heightMultiplier
        drawBackground(batch, parentAlpha, x - xOffset * w, y - yOffset * h, w, h)
        batch.flush()
        clipEnd()
    }

    private fun drawBackground(batch: Batch, parentAlpha: Float) =
            drawBackground(batch, parentAlpha, x, y, width, height)
    private fun drawBackground(batch: Batch, parentAlpha: Float, x: Float, y: Float, w: Float, h: Float) {
        val color = color
        batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
        background.draw(batch, x, y, w, h)
    }
}
