package com.unciv.models.skins

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.unciv.UncivGame
import com.unciv.ui.images.ImageGetter

class SkinStrings(skin: String = UncivGame.Current.settings.skin) {
    private val skinLocation = "Skins/$skin/"
    val skinConfig = SkinCache[skin] ?: SkinConfig()
    private val fallbackSkinLocation = if (skinConfig.fallbackSkin != null) "Skins/${skinConfig.fallbackSkin}/" else null
    private val fallbackSkinConfig = SkinCache[skinConfig.fallbackSkin]

    // Default shapes must always end with "Shape" so the UiElementDocsWriter can identify them
    val roundedEdgeRectangleSmallShape = "roundedEdgeRectangle-small"
    val roundedTopEdgeRectangleSmallShape = "roundedTopEdgeRectangle-small"
    val roundedTopEdgeRectangleSmallBorderShape = "roundedTopEdgeRectangle-small-border"
    val roundedEdgeRectangleMidShape = "roundedEdgeRectangle-mid"
    val roundedEdgeRectangleMidBorderShape = "roundedEdgeRectangle-mid-border"
    val roundedEdgeRectangleShape = "roundedEdgeRectangle"
    val rectangleWithOutlineShape = "rectangleWithOutline"
    val selectBoxShape = "select-box"
    val selectBoxPressedShape = "select-box-pressed"
    val checkboxShape = "checkbox"
    val checkboxPressedShape = "checkbox-pressed"
    val sliderBarShape = "slider-bar"
    val layerContainerShape = "layer-container"
    val tabShape = "tab"
    val tabActiveShape = "tab-active"

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
        val locationForDefault = skinLocation + default
        val locationByName = skinLocation + path
        val skinVariant = skinConfig.skinVariants[path]
        val locationByConfigVariant = if (skinVariant?.image != null) skinLocation + skinVariant.image else null
        val tint = (skinVariant?.tint ?: skinConfig.defaultVariantTint ?: tintColor)?.run {
            if (skinVariant?.alpha == null) this
            else cpy().apply { a = skinVariant.alpha }
        }
        
        val location = when {
            locationByConfigVariant != null && ImageGetter.ninePatchImageExists(locationByConfigVariant) ->
                locationByConfigVariant
            ImageGetter.ninePatchImageExists(locationByName) ->
                locationByName
            default != null && ImageGetter.ninePatchImageExists(locationForDefault) ->
                locationForDefault
            else ->
                null
        }
        
        if (location != null) {
            return ImageGetter.getNinePatch(location, tint)
        }

        val fallbackLocationForDefault = fallbackSkinLocation + default
        val fallbackLocationByName = fallbackSkinLocation + path
        val fallbackSkinVariant = fallbackSkinConfig?.skinVariants?.get(path)
        val fallbackLocationByConfigVariant = if (fallbackSkinVariant?.image != null)
            fallbackSkinLocation + fallbackSkinVariant.image 
        else 
            null
        val fallbackTint = (fallbackSkinVariant?.tint ?: tintColor)?.run {
            if (fallbackSkinVariant?.alpha == null) this
            else cpy().apply { a = fallbackSkinVariant.alpha }
        }

        val fallbackLocation = when {
            fallbackLocationByConfigVariant != null && ImageGetter.ninePatchImageExists(fallbackLocationByConfigVariant) ->
                fallbackLocationByConfigVariant
            ImageGetter.ninePatchImageExists(fallbackLocationByName) ->
                fallbackLocationByName
            default != null && ImageGetter.ninePatchImageExists(fallbackLocationForDefault) ->
                fallbackLocationForDefault
            else ->
                null
        }
        return ImageGetter.getNinePatch(fallbackLocation, fallbackTint)
    }

    fun getUIColor(path: String, default: Color? = null) =
            skinConfig.skinVariants[path]?.tint
                ?: default
                ?: skinConfig.clearColor

    
    fun getUIFontColor(path: String) = skinConfig.skinVariants[path]?.foregroundColor
    
    fun getUIIconColor(path: String) = 
        skinConfig.skinVariants[path]?.iconColor ?: skinConfig.skinVariants[path]?.foregroundColor

}
