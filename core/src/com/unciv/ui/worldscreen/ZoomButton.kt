package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.models.translations.tr
import com.unciv.ui.mapeditor.EditorMapHolder
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.onClick
import com.unciv.ui.utils.setFontSize

/**
 * Creates a zoom button
 * Sign should be "+" or "-"
 *
 * @param sign
 */
class ZoomButton(sign: String): TextButton("", BaseScreen.skin) {
    private lateinit var zoomAction: ZoomAction
    private val sign = sign
    init {
        label.setFontSize(30)
        label.setAlignment(Align.center)
        val action = { zoomAction.action() }
        onClick(action)
    }

    fun update(zoomAction: ZoomAction?) {
        if (zoomAction != null) {
            this.zoomAction = zoomAction
            setText(sign)
            label.color = Color.WHITE
            pack()
        }
    }
}

class ZoomAction(val action: () -> Unit)
