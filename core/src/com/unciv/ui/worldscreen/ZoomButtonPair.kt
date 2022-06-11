package com.unciv.ui.worldscreen

import com.badlogic.gdx.scenes.scene2d.ui.Cell
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.ZoomableScrollPane
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.setFontSize

class ZoomButtonPair(private val mapHolder: ZoomableScrollPane) : Table(BaseScreen.skin) {
    init {
        addButton("+") {
            mapHolder.zoomIn()
        }.padRight(10f)
        addButton("–") {  // figure dash U+2013, not minus, looks better
            mapHolder.zoomOut()
        }
        pack()
    }

    private fun addButton(text: String, action: () -> Unit): Cell<TextButton> {
        val button = TextButton(text, skin)
        button.label.setFontSize(30)
        button.label.setAlignment(Align.center)
        button.onClick(action)
        return add(button)
    }
}
