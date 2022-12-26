package com.unciv.models.skins

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.unciv.UncivGame
import com.unciv.ui.images.ImageGetter

class SkinStrings(skin: String = UncivGame.Current.settings.skin) {
    private val skinLocation = "Skins/$skin/"
    val skinConfig = SkinCache[skin] ?: SkinConfig()

    // Default shapes must always end with "Shape" so the UiElementDocsWriter can identify them
    val roundedEdgeRectangleSmallShape = skinLocation + "roundedEdgeRectangle-small"
    val roundedEdgeRectangleMidShape = skinLocation + "roundedEdgeRectangle-mid"
    val roundedEdgeRectangleMidBorderShape = skinLocation + "roundedEdgeRectangle-mid-border"
    val roundedEdgeRectangleShape = skinLocation + "roundedEdgeRectangle"
    val rectangleWithOutlineShape = skinLocation + "rectangleWithOutline"
    val selectBoxShape = skinLocation + "select-box"
    val selectBoxPressedShape = skinLocation + "select-box-pressed"
    val checkboxShape = skinLocation + "checkbox"
    val checkboxPressedShape = skinLocation + "checkbox-pressed"

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
     *                  If the UI element has multiple states with different tints use a distinct
     *                  name for every state e.g.
     *                  - CityScreen/CityConstructionTable/QueueEntry
     *                  - CityScreen/CityConstructionTable/QueueEntrySelected
     *
     * @param default   The path to the background which should be used if path is not available.
     *                  Should be one of the predefined ones inside SkinStrings or null to get a
     *                  solid background.
     */
    fun getUiBackground(path: String, default: String? = null, tintColor: Color? = null): NinePatchDrawable {
        val locationByName = skinLocation + path
        val locationByConfigVariant = skinLocation + skinConfig.skinVariants[path]?.image
        val tint = (skinConfig.skinVariants[path]?.tint ?: tintColor)?.apply {
            a = skinConfig.skinVariants[path]?.alpha ?: a
        }

        return when {
            ImageGetter.ninePatchImageExists(locationByConfigVariant) -> ImageGetter.getNinePatch(locationByConfigVariant, tint)
            ImageGetter.ninePatchImageExists(locationByName) -> ImageGetter.getNinePatch(locationByName, tint)
            else -> ImageGetter.getNinePatch(default, tint)
        }
    }
}
