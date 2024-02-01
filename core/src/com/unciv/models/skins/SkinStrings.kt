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
    val roundedTopEdgeRectangleSmallShape = skinLocation + "roundedTopEdgeRectangle-small"
    val roundedTopEdgeRectangleSmallBorderShape = skinLocation + "roundedTopEdgeRectangle-small-border"
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
     *
     * @param tintColor Default tint color if the UI Skin doesn't specify one. If both not specified,
     *                  the returned background will not be tinted. If the UI Skin specifies a
     *                  separate alpha value, it will be applied to a clone of either color.
     */
    fun getUiBackground(path: String, default: String? = null, tintColor: Color? = null): NinePatchDrawable {
        val skinVariant = skinConfig.skinVariants[path]
        val tint = (skinVariant?.tint ?: tintColor)?.run {
            if (skinVariant?.alpha == null) this
            else cpy().apply { a = skinVariant.alpha }
        }
        val location = getLocation(path, default)
        return ImageGetter.getNinePatch(location, tint)
    }

    fun getUIColor(path: String, default: Color? = null) =
            skinConfig.skinVariants[path]?.tint
                ?: default
                ?: skinConfig.clearColor

    /** Returns whether there is a skinned Ninepatch for [path], if `false` then [getUiBackground] would return a solid background. */
    fun hasUiBackground(path: String) = getLocation(path, null) != null

    private fun getLocation(path: String, default: String?): String? {
        val locationByName = skinLocation + path
        val skinVariant = skinConfig.skinVariants[path]
        val locationByConfigVariant = if (skinVariant?.image != null) skinLocation + skinVariant.image else null
        return when {
            locationByConfigVariant != null && ImageGetter.ninePatchImageExists(locationByConfigVariant) ->
                locationByConfigVariant
            ImageGetter.ninePatchImageExists(locationByName) ->
                locationByName
            else ->
                default
        }
    }
}
