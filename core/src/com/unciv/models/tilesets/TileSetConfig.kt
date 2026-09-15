package com.unciv.models.tilesets

import com.badlogic.gdx.graphics.Color
import com.unciv.Constants
import com.unciv.ui.images.ImageGetter

class TileSetConfig {
    var useColorAsBaseTerrain = false
    var useSummaryImages = false
    var unexploredTileColor: Color = Color.DARK_GRAY
    var fogOfWarColor: Color = ImageGetter.CHARCOAL
    /** Colour used to tint roads when [com.unciv.models.metadata.GameSettings.showRoadHighlight] is on.
     *  Moddable per tileset so it can be tuned to stay visible against tilesets with different
     *  palettes (e.g. Deciv, Rivers of Lava) instead of a single colour hardcoded for the default tileset. */
    var roadHighlightColor: Color = Color(0.05f, 0.85f, 0.75f, 1f) // teal
    /** Colour used to tint railroads when [com.unciv.models.metadata.GameSettings.showRoadHighlight] is on.
     *  See [roadHighlightColor]. */
    var railroadHighlightColor: Color = Color(0.95f, 0.55f, 0.05f, 1f) // orange
    /** Name of the tileset to use when this one is missing images. Null to disable. */
    var fallbackTileSet: String? = Constants.defaultFallbackTileset
    /** Scale factor for hex images, with hex center as origin. */
    var tileScale: Float = 1f
    var tileScales: HashMap<String, Float> = HashMap()
    var ruleVariants: HashMap<String, Array<String>> = HashMap()

    fun clone(): TileSetConfig {
        val toReturn = TileSetConfig()
        toReturn.useColorAsBaseTerrain = useColorAsBaseTerrain
        toReturn.useSummaryImages = useSummaryImages
        toReturn.unexploredTileColor = unexploredTileColor
        toReturn.fogOfWarColor = fogOfWarColor
        toReturn.roadHighlightColor = roadHighlightColor
        toReturn.railroadHighlightColor = railroadHighlightColor
        toReturn.fallbackTileSet = fallbackTileSet
        toReturn.tileScale = tileScale
        toReturn.tileScales = tileScales
        toReturn.ruleVariants.putAll(ruleVariants.map { Pair(it.key, it.value.clone()) })
        return toReturn
    }

    fun updateConfig(other: TileSetConfig) {
        useColorAsBaseTerrain = other.useColorAsBaseTerrain
        useSummaryImages = other.useSummaryImages
        unexploredTileColor = other.unexploredTileColor
        fogOfWarColor = other.fogOfWarColor
        roadHighlightColor = other.roadHighlightColor
        railroadHighlightColor = other.railroadHighlightColor
        fallbackTileSet = other.fallbackTileSet
        tileScale = other.tileScale
        for ((tileString, scale) in other.tileScales) {
            tileScales[tileString] = scale
        }
        for ((tileSetString, renderOrder) in other.ruleVariants) {
            ruleVariants[tileSetString] = renderOrder
        }
    }
}
