package com.unciv.ui.components.tilegroups

import com.unciv.UncivGame
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.RoadStatus
import com.unciv.models.metadata.GameSettings
import com.unciv.models.ruleset.Ruleset
import com.unciv.models.tilesets.TileSetCache
import com.unciv.models.tilesets.TileSetConfig
import com.unciv.ui.components.tilegroups.layers.EdgeTileImage
import com.unciv.ui.components.tilegroups.layers.NeighborDirection
import com.unciv.ui.images.ImageAttempter
import com.unciv.ui.images.ImageGetter

@Suppress("MemberVisibilityCanBePrivate") // No advandage hiding them

/**
 * Resolver translating more abstract tile data to paint on a map into actual texture names.
 *
 * Deals with variants, e.g. there could be a "City center-asian-Ancient era.png" that would be chosen
 * for a "City center"-containing Tile when it is to be drawn for a Nation defining it's style as "asian"
 * and whose techs say it's still in the first vanilla Era.
 *
 * Instantiated once per [TileGroupMap] and per [TileSet][com.unciv.models.tilesets.TileSet] -
 * typically once for HexaRealm and once for FantasyHex (fallback) at the start of a player turn,
 * and the same two every time they enter a CityScreen.
 *
 * @param tileSet Name of the tileset. Defaults to active at time of instantiation.
 * @param fallbackDepth Maximum number of fallback tilesets to try. Used to prevent infinite recursion.
 * */
class TileSetStrings(
    tileSet: String = UncivGame.Current.settings.tileSet,
    unitSet: String? = UncivGame.Current.settings.unitSet,
    fallbackDepth: Int = 1
) {

    constructor(ruleset: Ruleset, settings: GameSettings) : this(
        ruleset.modOptions.tileset ?: settings.tileSet,
        ruleset.modOptions.unitset ?: settings.unitSet
    )


    /** Separator used to mark variants, e.g. nation style or era specific */
    val tag = "-"

    // this is so that when we have 100s of TileGroups, they won't all individually come up with all these strings themselves,
    // it gets pretty memory-intensive (10s of MBs which is a lot for lower-end phones)
    val tileSetName = tileSet
    val unitSetName = unitSet
    val tileSetLocation = "TileSets/$tileSet/"
    val unitSetLocation = "TileSets/$unitSet/"
    val tileSetConfig = TileSetCache[tileSet]?.config ?: TileSetConfig()

    // These need to be by lazy since the orFallback expects a tileset, which it may not get.
    val hexagon: String by lazy { orFallback { tileSetLocation + "Hexagon"} }
    val hexagonList = listOf(hexagon) 
    val crosshatchHexagon by lazy { orFallback { tileSetLocation + "CrosshatchHexagon" } }
    val unexploredTile by lazy { orFallback { tileSetLocation + "UnexploredTile" } }
    val crosshair by lazy { orFallback { getString(tileSetLocation, "Crosshair") } }
    val highlight by lazy { orFallback { getString(tileSetLocation, "Highlight") } }
    val roadsMap = RoadStatus.entries
        .filterNot { it == RoadStatus.None }
        .associateWith { tileSetLocation + it.name }
    val naturalWonder = tileSetLocation + "Tiles/NaturalWonder"

    val tilesLocation = tileSetLocation + "Tiles/"
    val bottomRightRiver by lazy { orFallback { tilesLocation + "River-BottomRight"} }
    val bottomRiver by lazy { orFallback { tilesLocation + "River-Bottom"} }
    val bottomLeftRiver  by lazy { orFallback { tilesLocation + "River-BottomLeft"} }
    
    val edgeImagesByPosition = ImageGetter.getAllImageNames()
        .filter { it.startsWith(tileSetLocation +"Edges/") }
        .mapNotNull {
            val split = it.split('/').last() // without folder
                .split("-")
            // Comprised of 3 parts: origin tilefilter, destination tilefilter, 
            //   and edge type: Bottom, BottomLeft or BottomRight
            if (split.size != 4) return@mapNotNull null

            // split[0] is name and is unused
            val originTileFilter = split[1]
            val destinationTileFilter = split[2]
            val neighborDirection = split[3]
            val neighborDirectionEnumValue = NeighborDirection.entries
                .firstOrNull { it.name == neighborDirection } ?: return@mapNotNull null

            EdgeTileImage(it, originTileFilter, destinationTileFilter, neighborDirectionEnumValue)
        }
        .groupBy { it.edgeType }

    val unitsLocation = unitSetLocation + "Units/"

    val bordersLocation = tileSetLocation + "Borders/"


    // There aren't that many tile combinations, and so we end up joining the same strings over and over again.
    // On large maps, this can end up as quite a lot of space, some tens of MB!
    // In order to save on space, we have this function that gets several strings and returns their concat,
    //  but is able to retrieve the existing concat if it exists, letting us essentially save each string exactly once.
    private val stringConcatHashmap = HashMap<Pair<String, String>, String>()
    fun getString(vararg strings: String): String {
        var currentString = ""
        for (str in strings) {
            if (currentString == "") {
                currentString = str
                continue
            }
            val pair = Pair(currentString, str)
            if (stringConcatHashmap.containsKey(pair)) currentString = stringConcatHashmap[pair]!!
            else {
                val newString = currentString + str
                stringConcatHashmap[pair] = newString
                currentString = newString
            }
        }
        return currentString
    }

    fun getTile(baseTerrain: String) = getString(tilesLocation, baseTerrain)

    fun getBorder(borderShapeString: String, innerOrOuter: String) = getString(bordersLocation, borderShapeString, innerOrOuter)

    /** Fallback [TileSetStrings] to use when the currently chosen tileset is missing an image. */
    val fallback by lazy {
        if (fallbackDepth <= 0 || tileSetConfig.fallbackTileSet == null)
            null
        else
            TileSetStrings(tileSetConfig.fallbackTileSet!!, tileSetConfig.fallbackTileSet!!, fallbackDepth-1)
    }

    /**
     * @param image An image path string, such as returned from an instance of [TileSetStrings].
     * @param fallbackImage A lambda function that will be run with the [fallback] as its receiver if the original image does not exist according to [ImageGetter.imageExists].
     * @return The original image path string if its image exists, or the return result of the [fallbackImage] lambda if the original image does not exist.
     * */
    fun orFallback(image: String, fallbackImage: TileSetStrings.() -> String): String {
        return if (fallback == null || ImageGetter.imageExists(image))
            image
        else fallbackImage.invoke(fallback!!)
    }

    /** @see orFallback */
    fun orFallback(image: TileSetStrings.() -> String)
            = orFallback(image.invoke(this), image)



    /** For caching image locations based on given parameters (era, style, etc)
     * Based on what the final image would look like if all parameters existed,
     * like "pikeman-France-Medieval era": "pikeman" */
    val imageParamsToImageLocation = HashMap<String,String>()


    val embarkedMilitaryUnitLocation = getString(unitsLocation, "EmbarkedUnit-Military")
    val hasEmbarkedMilitaryUnitImage = ImageGetter.imageExists(embarkedMilitaryUnitLocation)

    val embarkedCivilianUnitLocation = getString(unitsLocation, "EmbarkedUnit-Civilian")
    val hasEmbarkedCivilianUnitImage = ImageGetter.imageExists(embarkedCivilianUnitLocation)

    /**
     * Image fallbacks work by precedence.
     * So currently, if you're france, it's the modern era, and you have a pikeman:
     * - If there's an era+style image of any era, take that
     * - Else, if there's an era-no-style image of any era, take that
     * - Only then check style-only
     * This means that if there's a "pikeman-France" and a "pikeman-Medieval era",
     * The era-based image wins out, even though it's not the current era.
     */
    private fun tryGetUnitImageLocation(unit: MapUnit): String? {

        var baseUnitIconLocation = getString(this.unitsLocation, unit.name)
        if (unit.isEmbarked()) {
            val unitSpecificEmbarkedUnitLocation =
                    getString(unitsLocation, "EmbarkedUnit-${unit.name}")
            baseUnitIconLocation = if (ImageGetter.imageExists(unitSpecificEmbarkedUnitLocation))
                unitSpecificEmbarkedUnitLocation
            else if (unit.isCivilian() && hasEmbarkedCivilianUnitImage)
                embarkedCivilianUnitLocation
            else if (unit.isMilitary() && hasEmbarkedMilitaryUnitImage)
                embarkedMilitaryUnitLocation
            else baseUnitIconLocation // no change
        }

        val civInfo = unit.civ
        val style = civInfo.nation.getStyleOrCivName()

        var imageAttempter = ImageAttempter(baseUnitIconLocation)
            // Era+style image: looks like  "pikeman-France-Medieval era"
            // More advanced eras default to older eras
            .tryEraImage(civInfo, baseUnitIconLocation, style, this)
            // Era-only image: looks like "pikeman-Medieval era"
            .tryEraImage(civInfo, baseUnitIconLocation, null, this)
            // Style era: looks like "pikeman-France" or "pikeman-European"
            .tryImage { getString(baseUnitIconLocation, tag, style) }
            .tryImage { baseUnitIconLocation }

        if (unit.baseUnit.replaces != null)
            imageAttempter = imageAttempter.tryImage { getString(unitsLocation, unit.baseUnit.replaces!!) }

        return imageAttempter.getPathOrNull()
    }

    fun getUnitImageLocation(unit: MapUnit): String {
        val imageKey = getString(
            unit.name, tag,
            unit.civ.getEra().name, tag,
            unit.civ.nation.getStyleOrCivName(), tag,
            unit.isEmbarked().toString()
        )
        // if in cache return that
        val currentImageMapping = imageParamsToImageLocation[imageKey]
        if (currentImageMapping != null) return currentImageMapping

        val imageLocation = tryGetUnitImageLocation(unit)
            ?: fallback?.tryGetUnitImageLocation(unit)
            ?: ""
        imageParamsToImageLocation[imageKey] = imageLocation
        return imageLocation
    }

    private fun tryGetOwnedTileImageLocation(baseLocation: String, owner: Civilization): String? {
        val ownersStyle = owner.nation.getStyleOrCivName()
        return ImageAttempter(baseLocation)
            .tryEraImage(owner, baseLocation, ownersStyle, this)
            .tryEraImage(owner, baseLocation, null, this)
            .tryImage { getString(baseLocation, tag, ownersStyle) }
            .getPathOrNull()
    }

    fun getOwnedTileImageLocation(baseLocation: String, owner: Civilization): String {
        val imageKey = getString(baseLocation, tag,
            owner.getEra().name, tag,
            owner.nation.getStyleOrCivName())
        val currentImageMapping = imageParamsToImageLocation[imageKey]
        if (currentImageMapping != null) return currentImageMapping

        val imageLocation = tryGetOwnedTileImageLocation(baseLocation, owner)
            ?: baseLocation

        imageParamsToImageLocation[imageKey] = imageLocation
        return imageLocation
    }
}
