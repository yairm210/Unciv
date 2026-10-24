package com.unciv.ui.screens.civilopediascreen

import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter

/**
 *  Helper renders a Category header Label flanked by two horizontal lines,
 *  which adjust their width automatically to the total width assigned by the container's layout.
 *
 *  Note this does ***not*** Use [ImageGetter.getWhiteDot] - the [ImageWithCustomSize][com.unciv.ui.images.ImageWithCustomSize]
 *  additions would ruin dynamic layout when resizing to smaller widths.
 */
internal class SubCategoryTable(subCategory: String) : Table() {
    private companion object {
        const val topPad = 10f
        const val lineToTextPad = 6f
        const val lineThickness = 2f
    }

    private fun getImage() = Image(ImageGetter.getWhiteDotDrawable())  // NOT an ImageWithCustomSize!!!
    private fun addLine() = add(getImage()).minWidth(lineToTextPad).height(lineThickness).growX()

    init {
        val label = subCategory.toLabel(fontSize = Constants.headingFontSize)
        addLine()
        add(label).pad(topPad, lineToTextPad, 0f, lineToTextPad)
        addLine()
    }
}
