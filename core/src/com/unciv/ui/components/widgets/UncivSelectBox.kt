package com.unciv.ui.components.widgets

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.scenes.scene2d.ui.List as GdxList
import com.badlogic.gdx.utils.Array as GdxArray


class UncivSelectBox<ItemT: UncivSelectBox.BoxItem>(
    items: Iterable<ItemT>,
    style: SelectBoxStyle
) : SelectBox<ItemT>(style) {

    constructor(items: Iterable<ItemT>, skin: Skin)
            : this(items, skin[SelectBoxStyle::class.java])
    constructor(items: Iterable<ItemT>, skin: Skin, styleName: String?)
            : this(items, skin[styleName, SelectBoxStyle::class.java])

    interface BoxItem {
        fun getActor(): Actor
        fun getPrefSize(): Pair<Float, Float> =
                (getActor() as? Layout)?.run { prefWidth to prefHeight }
                    ?: (0f to 0f)
    }

    init {
        setPrivateField("scrollPane", newScrollPane()) // Likely fixed in Gdx > 1.11.0

        val newItems = GdxArray<ItemT>(items.count())
        items.forEach { newItems.add(it) }
        setItems(newItems)
        selectedPrefWidth = true
        selected = newItems.firstOrNull()
    }

    override fun newScrollPane(): SelectBoxScrollPane<ItemT> {
        // Gdx BUG: #6930 https://github.com/libgdx/libgdx/pull/6930 isn't released yet in 1.11.0
        return this.ScrollPane()
    }

    private var prefWidth = 0f
    private var prefHeight = 0f

    override fun getPrefWidth(): Float {
        validate()
        return prefWidth
    }
    override fun getPrefHeight(): Float {
        validate()
        return prefHeight
    }
    override fun getMinWidth() = getPrefWidth()
    override fun getMinHeight() = getPrefHeight()

    override fun layout() {
        val (width, height) = selected?.getPrefSize() ?: (80f to 18f)
        style.listStyle?.selection?.run {
            prefWidth =  leftWidth + minWidth.coerceAtLeast(width) + rightWidth
            prefHeight =  topHeight + minHeight.coerceAtLeast(height) + bottomHeight
        } ?: {
            prefWidth = width
            prefHeight = height
        }
        style.background?.run {
            prefWidth = prefWidth.coerceAtLeast(minWidth) + leftWidth + rightWidth
            prefHeight = prefHeight.coerceAtLeast(minHeight) + topHeight + bottomHeight
        }
        if (scrollPane != null && !scrollPane.isScrollingDisabledY) {
            prefWidth += (style.scrollStyle.vScroll?.minWidth ?: 0f)
                .coerceAtLeast(style.scrollStyle.vScrollKnob?.minWidth ?: 0f)
        }
    }

    override fun draw(batch: Batch, parentAlpha: Float) {
        validate()
        var drawX = x
        var drawY = y
        var width = width
        var height = height
        batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)
        val background = backgroundDrawable
        background?.draw(batch, drawX, drawY, width, height)
        val selected = this.selected ?: return
        background?.run {
            drawX += leftWidth
            drawY += bottomHeight
            width -= leftWidth + rightWidth
            height -= bottomHeight + topHeight
        }
        val itemActor = selected.getActor()
        itemActor.setBounds(drawX, drawY, width, height)
        itemActor.draw(batch, 1f)
    }

    inner class ScrollPane : SelectBoxScrollPane<ItemT>(this) {
        override fun newList(): GdxList<ItemT> {
            return this@UncivSelectBox.DropDownList()
        }
    }

    inner class DropDownList : GdxList<ItemT>(style.listStyle) {
        private var itemHeight = 0f
        private var prefWidth = 0f
        private var prefHeight = 0f

        override fun getItemHeight(): Float {
            validate()
            return itemHeight
        }
        override fun getPrefWidth(): Float {
            validate()
            return prefWidth
        }
        override fun getPrefHeight(): Float {
            validate()
            return prefHeight
        }

        override fun layout() {
            var itemWidth = style.selection.run { leftWidth + minWidth + rightWidth }
            itemHeight = style.selection.run { topHeight + minHeight + bottomHeight }
            for (item in items) {
                val (width, height) = item.getPrefSize()
                itemWidth = itemWidth.coerceAtLeast(width)
                itemHeight = itemHeight.coerceAtLeast(height)
            }
            prefWidth = itemWidth
            prefHeight = itemHeight * items.size
            style.background?.also {
                prefWidth = it.minWidth.coerceAtLeast(it.leftWidth + prefWidth + it.rightWidth)
                prefHeight = it.minHeight.coerceAtLeast(it.topHeight + prefHeight + it.bottomHeight)
            }
            setPrivateField("itemHeight", itemHeight) // Or else rewrite the listeners and/or getItemIndexAt
        }

        override fun draw(batch: Batch, parentAlpha: Float) {
            validate()
            drawBackground(batch, parentAlpha)
            batch.setColor(color.r, color.g, color.b, color.a * parentAlpha)

            var drawX = x
            var width = width
            var itemY = height
            style.background?.also {
                drawX += it.leftWidth
                width -= it.leftWidth + it.rightWidth
                itemY -= it.topHeight
            }
            var drawY = y + itemY - itemHeight

            for (item in items) {
                if (cullingArea == null || itemY - itemHeight <= cullingArea.y + cullingArea.height && itemY >= cullingArea.y) {
                    val drawable = when {
                        item == pressedItem && style.down != null -> style.down
                        selection.contains(item) && style.selection != null -> style.selection
                        item == overItem && style.over != null ->
                            style.over
                        else -> null
                    }
                    drawSelection(batch, drawable, drawX, drawY, width, itemHeight)
                    val itemActor = item.getActor()
                    itemActor.setBounds(drawX, drawY, width, itemHeight)
                    itemActor.draw(batch, 1f)
                } else if (itemY < cullingArea.y) {
                    break
                }
                itemY -= itemHeight
                drawY -= itemHeight
            }
        }

        override fun drawItem(batch: Batch?, font: BitmapFont?, index: Int, item: ItemT?, x: Float, y: Float, width: Float): GlyphLayout? {
            throw IllegalStateException("UncivSelectBox.List.drawItem should be inaccessible")
        }
    }

    companion object {
        private fun <T>Any.setPrivateField(name: String, value: T) {
            val fld = javaClass.superclass.getDeclaredField(name)
            fld.isAccessible = true
            fld.set(this, value)
        }
    }
}
