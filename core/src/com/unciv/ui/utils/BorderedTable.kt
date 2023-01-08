package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.utils.extensions.center

open class BorderedTable(
    val path: String = "",
    val defaultInner: String = BaseScreen.skinStrings.rectangleWithOutlineShape,
    val defaultBorder: String = BaseScreen.skinStrings.rectangleWithOutlineShape,
    val borderColor: Color = Color.WHITE,
    val innerColor: Color = Color.BLACK,
    val borderSize: Float = 5f,
    val borderOnTop: Boolean = false
) : Table() {

    var bgBorder: Image = Image(BaseScreen.skinStrings.getUiBackground(path, defaultBorder,  borderColor))
    var bgInner: Image = Image(BaseScreen.skinStrings.getUiBackground(path, defaultInner,  innerColor))

    init {
        if (borderSize != 0f)
            this.addActor(bgBorder)
        this.addActor(bgInner)


        if (borderOnTop) {
            if (borderSize != 0f)
                bgBorder.toBack()
            bgInner.toBack()
        } else {
            bgInner.toBack()
            if (borderSize != 0f)
                bgBorder.toBack()
        }

    }

    fun setBackgroundColor(color: Color) {
        bgInner.remove()
        bgInner = Image(BaseScreen.skinStrings.getUiBackground(path, defaultInner, color))
        addActor(bgInner)
        if (borderSize != 0f) {
            if (borderOnTop)
                bgBorder.zIndex = bgInner.zIndex + 1
            else
                bgInner.zIndex = bgBorder.zIndex + 1
        }
        sizeChanged()
    }

    override fun sizeChanged() {
        super.sizeChanged()
        if (borderSize != 0f)
            bgBorder.setSize(width + borderSize, height + borderSize)
        bgInner.setSize(width, height)

        if (borderSize != 0f)
            bgBorder.center(this)
        bgInner.center(this)
    }
}
