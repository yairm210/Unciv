package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.*
import com.unciv.models.helpers.MapArrowType
import com.unciv.models.helpers.MiscArrowTypes
import com.unciv.models.helpers.TintedMapArrow
import com.unciv.models.helpers.UnitMovementMemoryType
import com.unciv.ui.cityscreen.YieldGroup
import com.unciv.ui.utils.*
import java.lang.IllegalStateException
import kotlin.math.*
import kotlin.random.Random

/** A lot of the render time was spent on snapshot arrays of the TileGroupMap's groups, in the act() function.
 * This class is to avoid the overhead of useless act() calls. */
open class ActionlessGroup(val checkHit:Boolean=false):Group() {
    override fun act(delta: Float) {}
    override fun hit(x: Float, y: Float, touchable: Boolean): Actor? {
        if (checkHit)
            return super.hit(x, y, touchable)
        return null
    }
}

open class TileGroup(var tileInfo: TileInfo, val tileSetStrings:TileSetStrings, private val groupSize: Float = 54f) : ActionlessGroup(true) {
    /*
    Layers:
    Base image (+ overlay)
    Feature overlay / city overlay
    Misc: Units, improvements, resources, border, arrows
    Highlight, Crosshair, Fog layer
    City name
    */

    /** Cache simple but frequent calculations. */
    private val hexagonImageWidth = groupSize * 1.5f
    /** Cache simple but frequent calculations. */
    private val hexagonImageOrigin = Pair(hexagonImageWidth/2f, sqrt((hexagonImageWidth/2f).pow(2) - (hexagonImageWidth/4f).pow(2))) // Pair, not Vector2, for immutability. Second number is triangle height for hex center.
    /** Cache simple but frequent calculations. */
    private val hexagonImagePosition = Pair(-hexagonImageOrigin.first/3f, -hexagonImageOrigin.second/4f) // Honestly, I got these numbers empirically by printing `.x` and `.y` after `.center()`, and I'm not totally clear on the stack of transformations that makes them work. But they are still exact ratios, AFAICT.

    // For recognizing the group in the profiler
    class BaseLayerGroupClass:ActionlessGroup()
    val baseLayerGroup = BaseLayerGroupClass().apply { isTransform = false; setSize(groupSize, groupSize)  }

    val tileBaseImages: ArrayList<Image> = ArrayList()
    /** List of image locations comprising the layers so we don't need to change images all the time */
    private var tileImageIdentifiers = listOf<String>()

    // This is for OLD tiles - the "mountain" symbol on mountains for instance
    private  var baseTerrainOverlayImage: Image? = null
    private  var baseTerrain: String = ""

    class TerrainFeatureLayerGroupClass:ActionlessGroup()
    val terrainFeatureLayerGroup = TerrainFeatureLayerGroupClass()
            .apply { isTransform = false; setSize(groupSize, groupSize) }

    // These are for OLD tiles - for instance the "forest" symbol on the forest
    private var terrainFeatureOverlayImage: Image? = null
    private  val terrainFeatures: ArrayList<String> = ArrayList()
    protected var cityImage: Image? = null
    private var naturalWonderImage: Image? = null

    private  var pixelMilitaryUnitImageLocation = ""
    private  var pixelMilitaryUnitGroup = ActionlessGroup().apply { isTransform = false; setSize(groupSize, groupSize) }
    private  var pixelCivilianUnitImageLocation = ""
    private  var pixelCivilianUnitGroup = ActionlessGroup().apply { isTransform = false; setSize(groupSize, groupSize) }

    class MiscLayerGroupClass:ActionlessGroup(){
        override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
    }
    val miscLayerGroup = MiscLayerGroupClass().apply { isTransform = false; setSize(groupSize, groupSize) }

    var tileYieldGroup = YieldGroup() // JN

    var resourceImage: Actor? = null
    var resource: String? = null

    class RoadImage {
        var roadStatus: RoadStatus = RoadStatus.None
        var image: Image? = null
    }
    data class BorderSegment(
        var images: List<Image>,
        var isLeftConcave: Boolean = false,
        var isRightConcave: Boolean = false,
    )

    private val roadImages = HashMap<TileInfo, RoadImage>()
    /** map of neighboring tile to border segments */
    private val borderSegments = HashMap<TileInfo, BorderSegment>()
    private val arrows = HashMap<TileInfo, ArrayList<Actor>>()

    @Suppress("LeakingThis")    // we trust TileGroupIcons not to use our `this` in its constructor except storing it for later
    val icons = TileGroupIcons(this)

    class UnitLayerGroupClass:Group(){
        override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
    }

    class UnitImageLayerGroupClass:ActionlessGroup(){
        override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
    }
    // We separate the units from the units' backgrounds, because all the background elements are in the same texture, and the units' aren't
    val unitLayerGroup = UnitLayerGroupClass().apply { isTransform = false; setSize(groupSize, groupSize);touchable = Touchable.disabled }
    val unitImageLayerGroup = UnitImageLayerGroupClass().apply { isTransform = false; setSize(groupSize, groupSize);touchable = Touchable.disabled }

    val cityButtonLayerGroup = Group().apply { isTransform = false; setSize(groupSize, groupSize)
        touchable = Touchable.childrenOnly; setOrigin(Align.center) }

    val highlightCrosshairFogLayerGroup = ActionlessGroup().apply { isTransform = false; setSize(groupSize, groupSize) }
    val highlightImage = ImageGetter.getImage(tileSetStrings.highlight) // for blue and red circles/emphasis on the tile
    private val crosshairImage = ImageGetter.getImage(tileSetStrings.crosshair) // for when a unit is targeted
    private val fogImage = ImageGetter.getImage(tileSetStrings.crosshatchHexagon )

    /**
     * Class for representing an arrow to add to the map at this tile.
     *
     * @property targetTile The tile that arrow should stretch to.
     * @property arrowType Style of the arrow.
     * @property tileSetStrings Helper for getting the paths of images in the current tileset.
     * */
    private class MapArrow(val targetTile: TileInfo, val arrowType: MapArrowType, val tileSetStrings: TileSetStrings) {
        /** @return An Image from a named arrow texture. */
        private fun getArrow(imageName: String): Image {
            val imagePath = tileSetStrings.orFallback { getString(tileSetLocation, "Arrows/", imageName) }
            return ImageGetter.getImage(imagePath)
        }
        /** @return An actor for the arrow, based on the type of the arrow. */
        fun getImage(): Image = when (arrowType) {
            is UnitMovementMemoryType -> getArrow(arrowType.name)
            is MiscArrowTypes -> getArrow(arrowType.name)
            is TintedMapArrow -> getArrow("Generic").apply { color = arrowType.color }
            else -> getArrow("Generic")
        }
    }

    /** Array list of all arrows to draw from this tile on the next update. */
    private val arrowsToDraw = ArrayList<MapArrow>()

    var showEntireMap = UncivGame.Current.viewEntireMapForDebug
    var forMapEditorIcon = false

    init {
        this.setSize(groupSize, groupSize)
        this.addActor(baseLayerGroup)
        this.addActor(terrainFeatureLayerGroup)
        this.addActor(miscLayerGroup)
        this.addActor(unitLayerGroup)
        this.addActor(cityButtonLayerGroup)
        this.addActor(highlightCrosshairFogLayerGroup)

        terrainFeatureLayerGroup.addActor(pixelMilitaryUnitGroup)
        terrainFeatureLayerGroup.addActor(pixelCivilianUnitGroup)

        updateTileImage(null)

        addHighlightImage()
        addFogImage()
        addCrosshairImage()
        isTransform = false // performance helper - nothing here is rotated or scaled
    }

    open fun clone(): TileGroup = TileGroup(tileInfo, tileSetStrings)


    //region init functions
    private fun addHighlightImage() {
        highlightCrosshairFogLayerGroup.addActor(highlightImage)
        setHexagonImageSize(highlightImage)
        highlightImage.isVisible = false
    }

    private fun addFogImage() {
        fogImage.color = Color.WHITE.cpy().apply { a = 0.2f }
        highlightCrosshairFogLayerGroup.addActor(fogImage)
        setHexagonImageSize(fogImage)
    }

    private fun addCrosshairImage() {
        crosshairImage.isVisible = false
        highlightCrosshairFogLayerGroup.addActor(crosshairImage)
        setHexagonImageSize(crosshairImage)
    }
    //endregion

    fun showCrosshair(alpha: Float = 1f) {
        crosshairImage.color.a = alpha
        crosshairImage.isVisible = true
    }


    private fun getTileBaseImageLocations(viewingCiv: CivilizationInfo?): List<String> {
        if (viewingCiv == null && !showEntireMap) return tileSetStrings.hexagonList
        if (tileInfo.naturalWonder != null) return listOf(tileSetStrings.orFallback { getTile(tileInfo.naturalWonder!!) })

        val shownImprovement = tileInfo.getShownImprovement(viewingCiv)
        val shouldShowImprovement = shownImprovement != null && UncivGame.Current.settings.showPixelImprovements

        val shouldShowResource = UncivGame.Current.settings.showPixelImprovements && tileInfo.resource != null &&
                (showEntireMap || viewingCiv == null || tileInfo.hasViewableResource(viewingCiv))

        val resourceAndImprovementSequence = sequence {
            if (shouldShowResource)  yield(tileInfo.resource!!)
            if (shouldShowImprovement) yield(shownImprovement!!)
        }

        val terrainImages = sequenceOf(tileInfo.baseTerrain) + tileInfo.terrainFeatures.asSequence()
        val allTogether = (terrainImages + resourceAndImprovementSequence).joinToString("+")
        val allTogetherLocation = tileSetStrings.getTile(allTogether)

        return when {
            tileSetStrings.tileSetConfig.ruleVariants[allTogether] != null -> tileSetStrings.tileSetConfig.ruleVariants[allTogether]!!.map { tileSetStrings.getTile(it) }
            ImageGetter.imageExists(allTogetherLocation) -> listOf(allTogetherLocation)
            else -> getTerrainImageLocations(terrainImages) + getImprovementAndResourceImages(resourceAndImprovementSequence)
        }
    }

    private fun getTerrainImageLocations(terrainSequence: Sequence<String>): List<String> {
        val allTerrains = terrainSequence.joinToString("+")
        if (tileSetStrings.tileSetConfig.ruleVariants.containsKey(allTerrains))
            return tileSetStrings.tileSetConfig.ruleVariants[allTerrains]!!.map { tileSetStrings.getTile(it) }
        val allTerrainTile = tileSetStrings.getTile(allTerrains)
        return if (ImageGetter.imageExists(allTerrainTile)) listOf(allTerrainTile)
        else terrainSequence.map { tileSetStrings.orFallback { getTile(it) } }.toList()
    }

    private fun getImprovementAndResourceImages(resourceAndImprovementSequence: Sequence<String>): List<String> {
        val altogether = resourceAndImprovementSequence.joinToString("+").let { tileSetStrings.getTile(it) }
        return if (ImageGetter.imageExists(altogether)) listOf(altogether)
        else resourceAndImprovementSequence.map { tileSetStrings.orFallback { getTile(it) } }.toList()
    }

    /** Used for: Underlying tile, unit overlays, border images, perhaps for other things in the future.
     Parent should already be set when calling. */
    private fun setHexagonImageSize(hexagonImage: Image) {
        hexagonImage.setSize(hexagonImageWidth, hexagonImage.height * hexagonImageWidth / hexagonImage.width)
        hexagonImage.setOrigin(hexagonImageOrigin.first, hexagonImageOrigin.second)
        hexagonImage.x = hexagonImagePosition.first
        hexagonImage.y = hexagonImagePosition.second
        hexagonImage.setScale(tileSetStrings.tileSetConfig.tileScale)
    }

    private fun updateTileImage(viewingCiv: CivilizationInfo?) {
        val tileBaseImageLocations = getTileBaseImageLocations(viewingCiv)

        if (tileBaseImageLocations.size == tileImageIdentifiers.size) {
            if (tileBaseImageLocations.withIndex().all { (i, imageLocation) -> tileImageIdentifiers[i] == imageLocation })
                return // All image identifiers are the same as the current ones, no need to change anything
        }
        tileImageIdentifiers = tileBaseImageLocations

        for (image in tileBaseImages.asReversed()) image.remove()
        tileBaseImages.clear()
        for (baseLocation in tileBaseImageLocations.asReversed()) { // reversed because we send each one to back
            // Here we check what actual tiles exist, and pick one - not at random, but based on the tile location,
            // so it stays consistent throughout the game
            if (!ImageGetter.imageExists(baseLocation)) continue

            var locationToCheck = baseLocation
            if (tileInfo.owningCity != null) {
                val ownersEra = tileInfo.getOwner()!!.getEra()
                val eraSpecificLocation = tileSetStrings.getString(locationToCheck, tileSetStrings.tag, ownersEra.name)
                if (ImageGetter.imageExists(eraSpecificLocation))
                    locationToCheck = eraSpecificLocation
            }

            val existingImages = ArrayList<String>()
            existingImages.add(locationToCheck)
            var i = 2
            while (true) {
                val tileVariant = locationToCheck + i
                if (ImageGetter.imageExists(tileVariant)) existingImages.add(tileVariant)
                else break
                i += 1
            }
            val finalLocation = existingImages.random(Random(tileInfo.position.hashCode() + locationToCheck.hashCode()))

            val image = ImageGetter.getImage(finalLocation)
            tileBaseImages.add(image)
            baseLayerGroup.addActorAt(0,image)
            setHexagonImageSize(image)
        }

        if (tileBaseImages.isEmpty()) { // Absolutely nothing! This is for the 'default' tileset
            val image = ImageGetter.getImage(tileSetStrings.hexagon)
            tileBaseImages.add(image)
            baseLayerGroup.addActor(image)
            setHexagonImageSize(image)
        }
    }

    fun showMilitaryUnit(viewingCiv: CivilizationInfo) = showEntireMap
            || viewingCiv.viewableInvisibleUnitsTiles.contains(tileInfo)
            || !tileInfo.hasEnemyInvisibleUnit(viewingCiv)

    fun isViewable(viewingCiv: CivilizationInfo) = showEntireMap
            || viewingCiv.viewableTiles.contains(tileInfo)
            || viewingCiv.isSpectator()

    fun isExplored(viewingCiv: CivilizationInfo) = showEntireMap
            || viewingCiv.exploredTiles.contains(tileInfo.position)
            || viewingCiv.isSpectator()

    open fun update(viewingCiv: CivilizationInfo? = null, showResourcesAndImprovements: Boolean = true, showTileYields: Boolean = true) {

        @Suppress("BooleanLiteralArgument")  // readable enough as is
        fun clearUnexploredTiles() {
            updateTileImage(null)
            updateRivers(false,false, false)

            updatePixelMilitaryUnit(false)
            updatePixelCivilianUnit(false)

            if (borderSegments.isNotEmpty()) clearBorders()

            icons.update(false,false ,false, false, null)

            fogImage.isVisible = true
        }

        hideHighlight()
        if (viewingCiv != null && !isExplored(viewingCiv)) {
            clearUnexploredTiles()
            for(image in tileBaseImages) image.color = tileSetStrings.tileSetConfig.unexploredTileColor
            return
        }

        val tileIsViewable = viewingCiv == null || isViewable(viewingCiv)
        val showMilitaryUnit = viewingCiv == null || showMilitaryUnit(viewingCiv)

        removeMissingModReferences()

        updateTileImage(viewingCiv)
        updateRivers(tileInfo.hasBottomRightRiver, tileInfo.hasBottomRiver, tileInfo.hasBottomLeftRiver)
        updateTerrainBaseImage()
        updateTerrainFeatureImage()

        updatePixelMilitaryUnit(tileIsViewable && showMilitaryUnit)
        updatePixelCivilianUnit(tileIsViewable)

        icons.update(showResourcesAndImprovements,showTileYields, tileIsViewable, showMilitaryUnit,viewingCiv)

        updateCityImage()
        updateNaturalWonderImage()
        updateTileColor(tileIsViewable)

        updateRoadImages()
        updateBorderImages()
        updateArrows()

        crosshairImage.isVisible = false
        fogImage.isVisible = !(tileIsViewable || showEntireMap)
    }

    private fun removeMissingModReferences() {
        for (unit in tileInfo.getUnits())
            if (!tileInfo.ruleset.nations.containsKey(unit.owner)) unit.removeFromTile()
    }

    private fun updateTerrainBaseImage() {
        if (tileInfo.baseTerrain == baseTerrain) return
        baseTerrain = tileInfo.baseTerrain

        if (baseTerrainOverlayImage != null) {
            baseTerrainOverlayImage!!.remove()
            baseTerrainOverlayImage = null
        }

        val imagePath = tileSetStrings.orFallback { getBaseTerrainOverlay(baseTerrain) }
        if (!ImageGetter.imageExists(imagePath)) return
        baseTerrainOverlayImage = ImageGetter.getImage(imagePath)
        baseTerrainOverlayImage!!.run {
            color.a = 0.25f
            setSize(40f, 40f)
            center(this@TileGroup)
        }
        baseLayerGroup.addActor(baseTerrainOverlayImage)
    }

    private fun updateCityImage() {
        if (cityImage == null && tileInfo.isCityCenter()) {
            val cityOverlayLocation = tileSetStrings.cityOverlay
            if (!ImageGetter.imageExists(cityOverlayLocation)) // have a city tile, don't need an overlay
                return

            cityImage = ImageGetter.getImage(cityOverlayLocation)
            terrainFeatureLayerGroup.addActor(cityImage)
            cityImage!!.run {
                setSize(60f, 60f)
                center(this@TileGroup)
            }
        }
        if (cityImage != null && !tileInfo.isCityCenter()) {
            cityImage!!.remove()
            cityImage = null
        }
    }

    private fun updateNaturalWonderImage() {
        if (naturalWonderImage == null && tileInfo.isNaturalWonder()) {
            val naturalWonderOverlay = tileSetStrings.naturalWonderOverlay
            if (!ImageGetter.imageExists(naturalWonderOverlay)) // Assume no natural wonder overlay = dedicated tile image
                return

            if (baseTerrainOverlayImage != null) {
                baseTerrainOverlayImage!!.remove()
                baseTerrainOverlayImage = null
            }

            naturalWonderImage = ImageGetter.getImage(naturalWonderOverlay)
            terrainFeatureLayerGroup.addActor(naturalWonderImage)
            naturalWonderImage!!.run {
                color.a = 0.25f
                setSize(40f, 40f)
                center(this@TileGroup)
            }
        }

        // Is this possible?
        if (naturalWonderImage != null && !tileInfo.isNaturalWonder()) {
            naturalWonderImage!!.remove()
            naturalWonderImage = null
        }
    }

    private fun clearBorders() {
        for (borderSegment in borderSegments.values)
            for (image in borderSegment.images)
                image.remove()

        borderSegments.clear()
    }

    private var previousTileOwner: CivilizationInfo? = null
    private fun updateBorderImages() {
        // This is longer than it could be, because of performance -
        // before fixing, about half (!) the time of update() was wasted on
        // removing all the border images and putting them back again!
        val tileOwner = tileInfo.getOwner()

        if (previousTileOwner != tileOwner) clearBorders()

        previousTileOwner = tileOwner
        if (tileOwner == null) return

        val civOuterColor = tileInfo.getOwner()!!.nation.getOuterColor()
        val civInnerColor = tileInfo.getOwner()!!.nation.getInnerColor()
        for (neighbor in tileInfo.neighbors) {
            var shouldRemoveBorderSegment = false
            var shouldAddBorderSegment = false

            var borderSegmentShouldBeLeftConcave = false
            var borderSegmentShouldBeRightConcave = false

            val neighborOwner = neighbor.getOwner()
            if (neighborOwner == tileOwner && borderSegments.containsKey(neighbor)) { // the neighbor used to not belong to us, but now it's ours
                shouldRemoveBorderSegment = true
            }
            else if (neighborOwner != tileOwner) {
                val leftSharedNeighbor = tileInfo.getLeftSharedNeighbor(neighbor)
                val rightSharedNeighbor = tileInfo.getRightSharedNeighbor(neighbor)

                // If a shared neighbor doesn't exist (because it's past a map edge), we act as if it's our tile for border concave/convex-ity purposes.
                // This is because we do not draw borders against non-existing tiles either.
                borderSegmentShouldBeLeftConcave = leftSharedNeighbor == null || leftSharedNeighbor.getOwner() == tileOwner
                borderSegmentShouldBeRightConcave = rightSharedNeighbor == null || rightSharedNeighbor.getOwner() == tileOwner

                if (!borderSegments.containsKey(neighbor)) { // there should be a border here but there isn't
                    shouldAddBorderSegment = true
                }
                else if (
                    borderSegmentShouldBeLeftConcave != borderSegments[neighbor]!!.isLeftConcave ||
                    borderSegmentShouldBeRightConcave != borderSegments[neighbor]!!.isRightConcave
                ) { // the concave/convex-ity of the border here is wrong
                    shouldRemoveBorderSegment = true
                    shouldAddBorderSegment = true
                }
            }

            if (shouldRemoveBorderSegment) {
                for (image in borderSegments[neighbor]!!.images)
                    image.remove()
                borderSegments.remove(neighbor)
            }
            if (shouldAddBorderSegment) {
                val images = mutableListOf<Image>()
                val borderSegment = BorderSegment(images, borderSegmentShouldBeLeftConcave, borderSegmentShouldBeRightConcave)
                borderSegments[neighbor] = borderSegment

                val borderShapeString = when {
                    borderSegment.isLeftConcave && borderSegment.isRightConcave -> "Concave"
                    !borderSegment.isLeftConcave && !borderSegment.isRightConcave -> "Convex"
                    !borderSegment.isLeftConcave && borderSegment.isRightConcave -> "ConvexConcave"
                    borderSegment.isLeftConcave && !borderSegment.isRightConcave -> "ConcaveConvex"
                    else -> throw IllegalStateException("This shouldn't happen?")
                }

                val relativeWorldPosition = tileInfo.tileMap.getNeighborTilePositionAsWorldCoords(tileInfo, neighbor)

                val sign = if (relativeWorldPosition.x < 0) -1 else 1
                val angle = sign * (atan(sign * relativeWorldPosition.y / relativeWorldPosition.x) * 180 / PI - 90.0).toFloat()
                
                val innerBorderImage = ImageGetter.getImage(
                        tileSetStrings.orFallback { getBorder("${borderShapeString}Inner") }
                )
                miscLayerGroup.addActor(innerBorderImage)
                images.add(innerBorderImage)
                setHexagonImageSize(innerBorderImage)
                innerBorderImage.rotateBy(angle)
                innerBorderImage.color = civOuterColor

                val outerBorderImage = ImageGetter.getImage(
                        tileSetStrings.orFallback { getBorder("${borderShapeString}Outer") }
                )
                miscLayerGroup.addActor(outerBorderImage)
                images.add(outerBorderImage)
                setHexagonImageSize(outerBorderImage)
                outerBorderImage.rotateBy(angle)
                outerBorderImage.color = civInnerColor
            }
        }
    }

    /** Create and setup Actors for all arrows to be drawn from this tile. */
    private fun updateArrows() {
        for (actorList in arrows.values) {
            for (actor in actorList) {
                actor.remove()
            }
        }
        arrows.clear()

        val tileScale = 50f * 0.8f // See notes in updateRoadImages.

        for (arrowToAdd in arrowsToDraw) {
            val targetTile = arrowToAdd.targetTile
            var targetCoord = Vector2(targetTile.position)
            if (tileInfo.tileMap.mapParameters.worldWrap)
                targetCoord = HexMath.getUnwrappedNearestTo(targetCoord, tileInfo.position, tileInfo.tileMap.maxLongitude)
            val targetRelative = HexMath.hex2WorldCoords(targetCoord)
                .sub(HexMath.hex2WorldCoords(tileInfo.position))

            val targetDistance = sqrt(targetRelative.x.pow(2) + targetRelative.y.pow(2))
            val targetAngle = atan2(targetRelative.y, targetRelative.x)

            if (targetTile !in arrows) {
                arrows[targetTile] = ArrayList()
            }

            val arrowImage = arrowToAdd.getImage()
            arrowImage.moveBy(25f, -5f) // Move to tile center— Y is +25f too, but subtract half the image height. Based on updateRoadImages.

            arrowImage.setSize(tileScale * targetDistance, 60f)
            arrowImage.setOrigin(0f, 30f)

            arrowImage.rotation = targetAngle / Math.PI.toFloat() * 180

            arrows[targetTile]!!.add(arrowImage)
            miscLayerGroup.addActor(arrowImage)
            // FIXME: Culled when too large and panned away.
            // https://libgdx.badlogicgames.com/ci/nightlies/docs/api/com/badlogic/gdx/scenes/scene2d/utils/Cullable.html
            // .getCullingArea returns null for both miscLayerGroup and worldMapHolder. Don't know where it's happening. Somewhat rare, and fixing it may have a hefty performance cost.
        }
    }

    private fun updateRoadImages() {
        if (forMapEditorIcon) return
        for (neighbor in tileInfo.neighbors) {
            val roadImage = roadImages[neighbor] ?: RoadImage().also { roadImages[neighbor] = it }

            val roadStatus = when {
                tileInfo.roadStatus == RoadStatus.None || neighbor.roadStatus === RoadStatus.None -> RoadStatus.None
                tileInfo.roadStatus == RoadStatus.Road || neighbor.roadStatus === RoadStatus.Road -> RoadStatus.Road
                else -> RoadStatus.Railroad
            }
            if (roadImage.roadStatus == roadStatus) continue // the image is correct

            roadImage.roadStatus = roadStatus

            if (roadImage.image != null) {
                roadImage.image!!.remove()
                roadImage.image = null
            }
            if (roadStatus == RoadStatus.None) continue // no road image

            val image = ImageGetter.getImage(tileSetStrings.orFallback { roadsMap[roadStatus]!! })
            roadImage.image = image

            val relativeWorldPosition = tileInfo.tileMap.getNeighborTilePositionAsWorldCoords(tileInfo, neighbor)

            // This is some crazy voodoo magic so I'll explain.
            image.moveBy(25f, 25f) // Move road to center of tile
            // in addTiles, we set   the position of groups by relative world position *0.8*groupSize, filter groupSize = 50
            // Here, we want to have the roads start HALFWAY THERE and extend towards the tiles, so we give them a position of 0.8*25.
            image.moveBy(-relativeWorldPosition.x * 0.8f * 25f, -relativeWorldPosition.y * 0.8f * 25f)

            image.setSize(10f, 6f)
            image.setOrigin(0f, 3f) // This is so that the rotation is calculated from the middle of the road and not the edge

            image.rotation = (180 / Math.PI * atan2(relativeWorldPosition.y.toDouble(),relativeWorldPosition.x.toDouble())).toFloat()
            terrainFeatureLayerGroup.addActor(image)
        }

    }

    private fun updateTileColor(isViewable: Boolean) {
        var color =
                if (tileSetStrings.tileSetConfig.useColorAsBaseTerrain)
                    tileInfo.getBaseTerrain().getColor()
                else Color.WHITE // no need to color it, it's already colored

        if (!isViewable) color = color.cpy().lerp(tileSetStrings.tileSetConfig.fogOfWarColor, 0.6f)
        for(image in tileBaseImages) image.color = color
    }

    private fun updateTerrainFeatureImage() {
        if (tileInfo.terrainFeatures != terrainFeatures) {
            terrainFeatures.clear()
            terrainFeatures.addAll(tileInfo.terrainFeatures)
            if (terrainFeatureOverlayImage != null) terrainFeatureOverlayImage!!.remove()
            terrainFeatureOverlayImage = null

            for (terrainFeature in terrainFeatures) {
                val terrainFeatureOverlayLocation = tileSetStrings.orFallback { getTerrainFeatureOverlay(terrainFeature) }
                if (!ImageGetter.imageExists(terrainFeatureOverlayLocation)) return
                terrainFeatureOverlayImage = ImageGetter.getImage(terrainFeatureOverlayLocation)
                terrainFeatureLayerGroup.addActor(terrainFeatureOverlayImage)
                terrainFeatureOverlayImage!!.run {
                    setSize(30f, 30f)
                    setColor(1f, 1f, 1f, 0.5f)
                    center(this@TileGroup)
                }
            }
        }
    }
    private fun updatePixelMilitaryUnit(showMilitaryUnit: Boolean) {
        var newImageLocation = ""

        val militaryUnit = tileInfo.militaryUnit
        if (militaryUnit != null && showMilitaryUnit) {
            fun TileSetStrings.getThisUnit(): String? {
                val specificUnitIconLocation = this.unitsLocation + militaryUnit.name
                return ImageAttempter(militaryUnit)
                        .forceImage { if (!UncivGame.Current.settings.showPixelUnits) "" else null }
                        .tryImage { if (civInfo.nation.style.isEmpty()) specificUnitIconLocation else null }
                        .tryImage { "$specificUnitIconLocation-${civInfo.nation.style}" }
                        .tryImage { specificUnitIconLocation }
                        .tryImage { if (baseUnit.replaces != null) "$unitsLocation${baseUnit.replaces}" else null }
                        .tryImages(
                                militaryUnit.civInfo.gameInfo.ruleSet.units.values.asSequence().map {
                                    fun MapUnit.() = if (it.unitType == militaryUnit.type.name)
                                        "$unitsLocation${it.name}"
                                    else
                                        null
                                } // .tryImage/.tryImages takes functions as parameters, for lazy eval. Include the check as part of the .tryImage's lazy candidate parameter, and *not* as part of the .map's transform parameter, so even the name check will be skipped by ImageAttempter if an image has already been found.
                        )
                        .tryImage { if (type.isLandUnit()) landUnit else null }
                        .tryImage { if (type.isWaterUnit()) waterUnit else null }
                        .getPathOrNull()
            }
            newImageLocation = tileSetStrings.getThisUnit() ?: tileSetStrings.fallback?.getThisUnit() ?: ""
        }

        if (pixelMilitaryUnitImageLocation != newImageLocation) {
            pixelMilitaryUnitGroup.clear()
            pixelMilitaryUnitImageLocation = newImageLocation

            if (newImageLocation != "" && ImageGetter.imageExists(newImageLocation)) {
                val nation = militaryUnit!!.civInfo.nation
                val pixelUnitImages = ImageGetter.getLayeredImageColored(newImageLocation, null, nation.getInnerColor(), nation.getOuterColor())
                for (pixelUnitImage in pixelUnitImages) {
                    pixelMilitaryUnitGroup.addActor(pixelUnitImage)
                    setHexagonImageSize(pixelUnitImage)// Treat this as A TILE, which gets overlayed on the base tile.
                }
            }
        }
    }


    private fun updatePixelCivilianUnit(tileIsViewable: Boolean) {
        var newImageLocation = ""
        val civilianUnit = tileInfo.civilianUnit

        if (civilianUnit != null && tileIsViewable) {
            fun TileSetStrings.getThisUnit(): String? {
                val specificUnitIconLocation = this.unitsLocation + civilianUnit.name
                return ImageAttempter(civilianUnit)
                        .forceImage { if (!UncivGame.Current.settings.showPixelUnits) "" else null }
                        .tryImage { if (civInfo.nation.style.isNotEmpty()) "$specificUnitIconLocation-${civInfo.nation.style}" else null }
                        .tryImage { specificUnitIconLocation }
                        .tryImage { civilianLandUnit }
                        .getPathOrNull()
            }
            newImageLocation = tileSetStrings.getThisUnit() ?: tileSetStrings.fallback?.getThisUnit() ?: ""
        }

        if (pixelCivilianUnitImageLocation != newImageLocation) {
            pixelCivilianUnitGroup.clear()
            pixelCivilianUnitImageLocation = newImageLocation

            if (newImageLocation != "" && ImageGetter.imageExists(newImageLocation)) {
                val nation = civilianUnit!!.civInfo.nation
                val pixelUnitImages = ImageGetter.getLayeredImageColored(newImageLocation, null, nation.getInnerColor(), nation.getOuterColor())
                for (pixelUnitImage in pixelUnitImages) {
                    pixelCivilianUnitGroup.addActor(pixelUnitImage)
                    setHexagonImageSize(pixelUnitImage)// Treat this as A TILE, which gets overlayed on the base tile.
                }
            }
        }
    }


    private var bottomRightRiverImage :Image?=null
    private var bottomRiverImage :Image?=null
    private var bottomLeftRiverImage :Image?=null

    private fun updateRivers(displayBottomRight:Boolean, displayBottom:Boolean, displayBottomLeft:Boolean){
        bottomRightRiverImage = updateRiver(bottomRightRiverImage,displayBottomRight, tileSetStrings.bottomRightRiver)
        bottomRiverImage = updateRiver(bottomRiverImage, displayBottom, tileSetStrings.bottomRiver)
        bottomLeftRiverImage = updateRiver(bottomLeftRiverImage, displayBottomLeft, tileSetStrings.bottomLeftRiver)
    }

    private fun updateRiver(currentImage:Image?, shouldDisplay:Boolean,imageName:String): Image? {
        if (!shouldDisplay) {
            currentImage?.remove()
            return null
        } else {
            if (currentImage != null) return currentImage
            if (!ImageGetter.imageExists(imageName)) return null // Old "Default" tileset gets no rivers.
            val newImage = ImageGetter.getImage(imageName)
            baseLayerGroup.addActor(newImage)
            setHexagonImageSize(newImage)
            return newImage
        }
    }

    /**
     * Add an arrow to be drawn from this tile.
     * Similar to [showHighlight].
     *
     * Zero-length arrows are ignored.
     *
     * @param targetTile The tile the arrow should stretch to.
     * @param type Style of the arrow.
     * */
    fun addArrow(targetTile: TileInfo, type: MapArrowType) {
        if (targetTile.position != tileInfo.position) {
            arrowsToDraw.add(
                MapArrow(targetTile, type, tileSetStrings)
            )
        }
    }

    /**
     * Clear all arrows to be drawn from this tile.
     * Similar to [hideHighlight].
     */
    fun resetArrows() {
        arrowsToDraw.clear()
    }

    fun showHighlight(color: Color, alpha: Float = 0.3f) {
        highlightImage.isVisible = true
        highlightImage.color = color.cpy().apply { a = alpha }
    }

    fun hideHighlight() { highlightImage.isVisible = false }

    /** This exists so we can easily find the TileGroup draw method in the android profiling, otherwise it's just a mass of Group.draw->drawChildren->Group.draw etc. */
    override fun draw(batch: Batch?, parentAlpha: Float) { super.draw(batch, parentAlpha) }
    @Suppress("RedundantOverride")  // intentional
    override fun act(delta: Float) { super.act(delta) }
}
