package com.unciv.ui.components.widgets

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.Drawable

/** Allows inserting a scaled Table into another, such that the outer Table "sees" correct inner Table dimensions.
 *
 *  Note: Delegates only the basic Table API: [background], [columns], [rows], [add], [row] and [defaults].
 *  Add to these as needed.
 */
open class ScalingTableWrapper(
    private val minScale: Float = 0.5f
) : WidgetGroup() {
    private val innerTable = Table()

    init {
        isTransform = false
        super.addActor(innerTable)
    }

    //region WidgetGroup overrides

    // I'd like to report "we could, if needed, shrink to innerTable.minWidth * minScale"
    // - but then we get overall a glitch during resizes where the outer Table sets our height
    // to minHeight despite there being room (as far as WorldScreenTopBar is concened) and scale > minScale...
    override fun getMinWidth() = innerTable.minWidth * innerTable.scaleX

    override fun getPrefWidth() = innerTable.prefWidth * innerTable.scaleX
    override fun getMaxWidth() = innerTable.prefWidth

    override fun getMinHeight() = innerTable.minHeight * innerTable.scaleY
    override fun getPrefHeight() = innerTable.prefHeight * innerTable.scaleY
    override fun getMaxHeight() = innerTable.prefHeight

    override fun layout() {
        innerTable.setBounds(0f, 0f, width / innerTable.scaleX, height / innerTable.scaleY)
    }
    //endregion

    //region Table API delegates
    var background: Drawable?
        get() = innerTable.background
        set(value) { innerTable.background = value }
    val columns: Int get() = innerTable.columns
    val rows: Int get() = innerTable.rows

    fun defaults(): Cell<Actor> = innerTable.defaults()
    fun add(actor: Actor): Cell<Actor> = innerTable.add(actor)
    fun add(): Cell<Actor?> = innerTable.add()
    fun row(): Cell<Actor> = innerTable.row()
    //endregion

    fun resetScale() {
        innerTable.setScale(1f)
        innerTable.isTransform = false
    }

    fun scaleTo(maxWidth: Float) {
        innerTable.pack()
        val scale = (maxWidth / innerTable.prefWidth).coerceIn(minScale, 1f)
        if (scale >= 1f) return
        innerTable.isTransform = true
        innerTable.setScale(scale)
        if (!innerTable.needsLayout()) {
            innerTable.invalidate()
            invalidate()
        }
    }
}
