package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.unciv.ui.images.ImageGetter

/**
 * Promotion picker colors intentionally defined in code for deterministic web behavior.
 */
class PromotionScreenColors(
    val normal: Color = ImageGetter.CHARCOAL,
    val selected: Color = Color(0.2824f, 0.5765f, 0.6863f, 1f),
    val pathToSelection: Color = Color(0.1882f, 0.3843f, 0.4575f, 1f),
    val promoted: Color = Color(0.8f, 0.6745f, 0f, 1f),
    val promotedText: Color = Color(0.16f, 0.1349f, 0f, 1f),
    val pickable: Color = Color(0.1098f, 0.3137f, 0f, 1f),
    val prerequisite: Color = Color(0.4f, 0.5f, 0.8f, 1f),
    val groupLines: Color = Color.WHITE,
    val otherLines: Color = Color.CLEAR,
)
