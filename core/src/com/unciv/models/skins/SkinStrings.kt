package com.unciv.models.skins

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.unciv.UncivGame
import com.unciv.ui.images.ImageGetter

class SkinStrings(skin: String = UncivGame.Current.settings.skin) {
    private val skinLocation = "Skins/$skin/"
    val skinConfig = /* TODO SkinCache[skin] ?:*/ SkinConfig()

    val roundedEdgeRectangle = skinLocation + "roundedEdgeRectangle"
    val rectangleWithOutline = skinLocation + "rectangleWithOutline"
    val selectBox = skinLocation + "select-box"
    val selectBoxPressed = skinLocation + "select-box-pressed"
    val checkbox = skinLocation + "checkbox"
    val checkboxPressed = skinLocation + "checkbox-pressed"

    /**
     * Gets either a drawable which was defined inside skinConfig for the given path or the drawable
     * found at path itself or the default drawable to be applied as the background for an UI element.
     *
     * @param path      The path of the UI background in UpperCamelCase. Should be the location of the
     *                  UI element inside the UI tree e.g. WorldScreen/TopBar/StatsTable.
     *
     *                  If the UI element is used in multiple Screens start the path with General
     *                  e.g. General/Tooltip
     *
     *
     * @param default   The path to the background which should be used if path is not available.
     *                  Should be one of the predefined ones inside SkinStrings or null to get a
     *                  solid background.
     */
    fun getUiBackground(path: String, default: String? = null, tintColor: Color? = null): NinePatchDrawable {
        val locationByName = skinLocation + path
        val locationByConfigVariant = skinConfig.skinVariants[path]?.image
        val tint = skinConfig.skinVariants[path]?.tint ?: tintColor
        return when {
            locationByConfigVariant != null && ImageGetter.imageExists(locationByConfigVariant) -> ImageGetter.getNinePatch(locationByConfigVariant, tint)
            ImageGetter.imageExists(locationByName) -> ImageGetter.getNinePatch(locationByName, tint)
            else -> ImageGetter.getNinePatch(default, tint)
        }
    }
}
