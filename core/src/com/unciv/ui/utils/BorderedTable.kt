package com.unciv.ui.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.ui.utils.extensions.center

open class BorderedTable(
    val style: String = BaseScreen.skinStrings.rectangleWithOutlineShape,
    val innerColor: Color = Color.BLACK,
    val borderSize: Float = 5f
) : Table() {

    private var bgBorder: Image = Image(BaseScreen.skinStrings.getUiBackground("", style,  Color.WHITE))
    private var bgInner: Image = Image(BaseScreen.skinStrings.getUiBackground("", style,  innerColor))

    init {
        this.addActor(bgBorder)
        this.addActor(bgInner)

        bgInner.toBack()
        bgBorder.toBack()

    }

    fun setBackgroundColor(color: Color) {
        bgInner.remove()
        bgInner = Image(BaseScreen.skinStrings.getUiBackground("", style,  color))
        addActor(bgInner)
        bgInner.zIndex = bgBorder.zIndex + 1
        sizeChanged()

    }

    override fun sizeChanged() {
        super.sizeChanged()
        bgBorder.setSize(width + borderSize, height + borderSize)
        bgInner.setSize(width, height)

        bgBorder.center(this)
        bgInner.center(this)

    }
}
