package com.unciv.models.skins

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.unciv.UncivGame
import com.unciv.ui.images.ImageGetter

class SkinStrings(skin: String = UncivGame.Current.settings.skin) {
    private val skinLocation = "Skins/$skin/"
    val tileSetConfig = /* TODO SkinCache[skin] ?:*/ SkinConfig()

    val roundedEdgeRectangle = skinLocation + "roundedEdgeRectangle"
    val rectangleWithOutline = skinLocation + "rectangleWithOutline"
    val selectBox = skinLocation + "select-box"
    val selectBoxPressed = skinLocation + "select-box-pressed"
    val checkbox = skinLocation + "checkbox"
    val checkBoxPressed = skinLocation + "checkbox-pressed"

    fun getUiElement(name: String, default: String? = null, tintColor: Color? = null): NinePatchDrawable {
        val locationByName = skinLocation + name
        val locationByConfigVariant = tileSetConfig.skinVariants[name]?.image
        val tint = tileSetConfig.skinVariants[name]?.tint ?: tintColor
        return when {
            locationByConfigVariant != null && ImageGetter.imageExists(locationByConfigVariant) -> ImageGetter.getNinePatch(locationByConfigVariant, tint)
            ImageGetter.imageExists(locationByName) -> ImageGetter.getNinePatch(locationByName, tint)
            else -> ImageGetter.getNinePatch(default, tint)
        }
    }
}
