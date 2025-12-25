package com.unciv.ui.screens.civilopediascreen

import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.unciv.Constants
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.images.ImageGetter

/**
 *  Kludge helper renders a Category header Label flanked by two horizontal lines,
 *  which adjust their width automatically to the total width assigned by the container's layout.
 *
 *  Doing that directly will break when the containing Table is part of SplitPane and the SplitPane handle is dragged:
 *  Table will never relinquish any column width it has allocated, thus the "Header" will grow ever wider and never shrink back down.
 *  This is due to the `private float[] columnMinWidth, rowMinHeight, columnPrefWidth, rowPrefHeight, columnWidth, rowHeight;` fields,
 *  Which do not get cleared for a layout() run, and the artificial two-level validation using `private boolean sizeInvalid` on top of
 *  `WidgetGroup.needsLayout`.
 *
 *  The only way to get measurements to dynamically follow growing **and** shrinking is to clear the entire Table...
 */
internal class SubCategoryTable(subCategory: String) : Table() {
    private val label = subCategory.toLabel(fontSize = Constants.headingFontSize)
    private val leftImage = ImageGetter.getWhiteDot()
    private val rightImage = ImageGetter.getWhiteDot()

    override fun invalidate() {
        if (!cells.isEmpty)
            clear()
        super.invalidate()
    }

    override fun validate() {
        if (cells.isEmpty) {
            super.childrenChanged()
            add(leftImage).height(2f).growX()
            add(label).pad(10f, 6f, 0f, 6f)
            add(rightImage).height(2f).growX()
        }
        super.validate()
    }

    override fun childrenChanged() {
        /** This is the crux of the kludge! Blocking `super.childrenChanged` avoids
         *  both endless invalidate recursion and the add in validate clearing the content.
         *  That we can block it permanently is potentially sensitive to Gdx changes,
         *  if in doubt introduce a blocking flag and set it during clear and the add block.
         */
    }

    // These are also indispensable for the kludge to function,
    // pref measurements need to return the final values while the Table is still empty.
    override fun getMinHeight() = label.minHeight + 10f
    override fun getMinWidth() = label.minWidth + 12f
    override fun getPrefHeight() = label.prefHeight + 10f
    override fun getPrefWidth() = label.prefWidth + 12f
}
