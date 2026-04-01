package com.unciv.ui.screens.pickerscreens

import com.badlogic.gdx.graphics.Color
import com.unciv.ui.images.ImageGetter


/** Colours used on the [PromotionPickerScreen]
 *
 *  These are backed by Skin.json
 */
class PromotionScreenColors {
    val default: Color = ImageGetter.CHARCOAL
    val selected: Color = Color(0.2824f, 0.5765f, 0.6863f, 1f)          // colorFromRGB(72, 147, 175)
    val pathToSelection: Color = Color(0.1882f, 0.3843f, 0.4575f, 1f)   // selected.darken(0.33f)
    val promoted: Color = Color(0.8f, 0.6745f, 0f, 1f)                  // colorFromRGB(255, 215, 0).darken(0.2f)
    val promotedText: Color = Color(0.16f, 0.1349f, 0f, 1f)             // promoted.darken(0.8f)
    val pickable: Color = Color(0.1098f, 0.3137f, 0f, 1f)               // colorFromRGB(28, 80, 0)
    val prerequisite: Color = Color(0.4f, 0.5f, 0.8f, 1f)               // HSV(225,50,80): muted Royal
    val groupLines: Color = Color.WHITE
    val otherLines: Color = Color.CLEAR
}
