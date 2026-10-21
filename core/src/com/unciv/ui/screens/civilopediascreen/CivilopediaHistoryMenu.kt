package com.unciv.ui.screens.civilopediascreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Stage
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.ui.components.SmallButtonStyle
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.popups.ScrollableAnimatedMenuPopup

class CivilopediaHistoryMenu(
    stage: Stage,
    button: Actor,
    private val action: (CivilopediaHistory.Entry) -> Unit
) : ScrollableAnimatedMenuPopup(stage, button, Align.bottomRight) {
    override fun createFixedContent(): Table? = null
    override fun createScrollableContent() = Table().apply {
        defaults().space(2f)
        val current = CivilopediaHistory.position
        val currentStyle = SmallButtonStyle().apply {
            disabledFontColor = Color.FOREST
        }
        for ((index, entry) in CivilopediaHistory.withIndex().reversed()) {
            val button = getButton("{${entry.category.label}} - {${entry.entry}}", KeyboardBinding.None) {
                CivilopediaHistory.navigateTo(index, entry, action)
            }
            if (index == current) {
                button.style = currentStyle
                button.isDisabled = true
            }
            add(button).row()
        }
    }
}
