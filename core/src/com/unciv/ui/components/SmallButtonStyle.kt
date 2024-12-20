package com.unciv.ui.components

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen

class SmallButtonStyle : TextButton.TextButtonStyle(BaseScreen.skin[TextButton.TextButtonStyle::class.java]) {
    /** Modify NinePatch geometry so the roundedEdgeRectangleMidShape button is 38f high instead of 48f,
     *  Otherwise this excercise would be futile - normal roundedEdgeRectangleShape based buttons are 50f high.
     */
    private fun NinePatchDrawable.reduce(): NinePatchDrawable {
        val patch = NinePatch(this.patch)
        patch.padTop = 10f
        patch.padBottom = 10f
        patch.topHeight = 10f
        patch.bottomHeight = 10f
        return NinePatchDrawable(this).also { it.patch = patch }
    }

    init {
        val upColor = BaseScreen.skin.getColor("base-40")
        val downColor = BaseScreen.skin.getColor("base-60")
        val overColor = BaseScreen.skin.getColor("base-80")
        val disabledColor = BaseScreen.skin.getColor("base-40")
        // UiElementDocsWriter inspects source, which is why this isn't prettified better
        val shape = BaseScreen.run {
            // Let's use _one_ skinnable background lookup but with different tints
            val skinned = skinStrings.getUiBackground("AnimatedMenu/Button", skinStrings.roundedEdgeRectangleMidShape)
            // Reduce height only if not skinned
            val default = ImageGetter.getNinePatch(skinStrings.roundedEdgeRectangleMidShape)
            if (skinned === default) default.reduce() else skinned
        }
        // Now get the tinted variants
        up = shape.tint(upColor)
        down = shape.tint(downColor)
        over = shape.tint(overColor)
        disabled = shape.tint(disabledColor)
        disabledFontColor = Color.GRAY
    }
}
