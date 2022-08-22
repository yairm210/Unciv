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
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.helpers.MapArrowType
import com.unciv.models.helpers.MiscArrowTypes
import com.unciv.models.helpers.TintedMapArrow
import com.unciv.models.helpers.UnitMovementMemoryType
import com.unciv.ui.cityscreen.YieldGroup
import com.unciv.ui.images.ImageGetter
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

open class TileGroup(
    var tileInfo: TileInfo,
    val tileSetStrings: TileSetStrings,
    groupSize: Float = 54f
) : ActionlessGroupWithHit() {
    /*
        Layers (reordered in TileGroupMap):
        Base image (+ overlay)
        Terrain Feature overlay (including roads)
        Misc: improvements, resources, yields, worked, resources, border, arrows, and starting locations in editor
        Pixel Units
        Highlight, Fog, Crosshair layer (in that order)
        Units
        City button
        City name
    */

    /** Cache simple but frequent calculations. */
    private val hexagonImageWidth = groupSize * 1.5f
    /** Cache simple but frequent calculations. */
    private val hexagonImageOrigin = Pair(hexagonImageWidth / 2f, sqrt((hexagonImageWidth / 2f).pow(2) - (hexagonImageWidth / 4f).pow(2)))
    // Pair, not Vector2, for immutability. Second number is triangle height for hex center.
    /** Cache simple but frequent calculations. */
    private val hexagonImagePosition = Pair(-hexagonImageOrigin.first / 3f, -hexagonImageOrigin.second / 4f)
    // Honestly, I got these numbers empirically by printing `.x` and `.y` after `.center()`, and I'm not totally
    // clear on the stack of transformations that makes them work. But they are still exact ratios, AFAICT.

    // For recognizing the group in the profiler
    class BaseLayerGroupClass(groupSize: Float) : ActionlessGroup(groupSize)
    val baseLayerGroup = BaseLayerGroupClass(groupSize)

    val tileBaseImages: ArrayList<Image> = ArrayList()
    /** List of image locations comprising the layers so we don't need to change images all the time */
    private var tileImageIdentifiers = listOf<String>()

    class TerrainFeatureLayerGroupClass(groupSize: Float) : ActionlessGroup(groupSize)
    val terrainFeatureLayerGroup = TerrainFeatureLayerGroupClass(groupSize)

    private var pixelMilitaryUnitImageLocation = ""
    var pixelMilitaryUnitGroup = ActionlessGroup(groupSize)
    private var pixelCivilianUnitImageLocation = ""
    var pixelCivilianUnitGroup = ActionlessGroup(groupSize)

    class MiscLayerGroupClass(groupSize: Float) : ActionlessGroup(groupSize) {
        override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
    }
    val borderLayerGroup = MiscLayerGroupClass(groupSize)
    val miscLayerGroup = MiscLayerGroupClass(groupSize)

    var tileYieldGroupInitialized = false
    val tileYieldGroup: YieldGroup by lazy { YieldGroup() }

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

    class UnitLayerGroupClass(groupSize: Float) : Group() {
        init {
            isTransform = false
            touchable = Touchable.disabled
            setSize(groupSize, groupSize)
        }

        override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
        override fun act(delta: Float) { // No 'snapshotting' since we trust it will remain the same
            for (child in children)
                child.act(delta)
        }
    }

    class UnitImageLayerGroupClass(groupSize: Float) : ActionlessGroup(groupSize) {
        override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
        init {
            touchable = Touchable.disabled
        }
    }
    // We separate the units from the units' backgrounds, because all the background elements are in the same texture, and the units' aren't
    val unitLayerGroup = UnitLayerGroupClass(groupSize)
    val unitImageLayerGroup = UnitImageLayerGroupClass(groupSize)

    class CityButtonLayerGroupClass(val tileInfo: TileInfo, groupSize: Float) : Group() {
        override fun draw(batch: Batch?, parentAlpha: Float) {
            if (!tileInfo.isCityCenter()) return
            super.draw(batch, parentAlpha)
        }
        override fun act(delta: Float) {
            if (!tileInfo.isCityCenter()) return
            super.act(delta)
        }
        override fun hit(x: Float, y: Float, touchable: Boolean): Actor? {
            if (!tileInfo.isCityCenter()) return null
            return super.hit(x, y, touchable)
        }
        init {
            isTransform = false
            setSize(groupSize, groupSize)
            touchable = Touchable.childrenOnly
            setOrigin(Align.center)
        }
    }

    val cityButtonLayerGroup = CityButtonLayerGroupClass(tileInfo, groupSize)

    val highlightFogCrosshairLayerGroup = ActionlessGroup(groupSize)
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
        this.addActor(borderLayerGroup)
        this.addActor(miscLayerGroup)
        this.addActor(pixelMilitaryUnitGroup)
        this.addActor(pixelCivilianUnitGroup)
        this.addActor(unitLayerGroup)
        this.addActor(cityButtonLayerGroup)
        this.addActor(highlightFogCrosshairLayerGroup)

        updateTileImage(null)

        addHighlightImage()
        addFogImage()
        addCrosshairImage()
        isTransform = false // performance helper - nothing here is rotated or scaled
    }

    open fun clone() = TileGroup(tileInfo, tileSetStrings)


    //region init functions
    private fun addHighlightImage() {
        highlightFogCrosshairLayerGroup.addActor(highlightImage)
        setHexagonImageSize(highlightImage)
        highlightImage.isVisible = false
    }

    private fun addFogImage() {
        fogImage.color = Color.WHITE.cpy().apply { a = 0.2f }
        highlightFogCrosshairLayerGroup.addActor(fogImage)
        setHexagonImageSize(fogImage)
    }

    private fun addCrosshairImage() {
        crosshairImage.isVisible = false
        highlightFogCrosshairLayerGroup.addActor(crosshairImage)
        setHexagonImageSize(crosshairImage)
    }
    //endregion

    fun showCrosshair(alpha: Float = 1f) {
        crosshairImage.color.a = alpha
        crosshairImage.isVisible = true
    }


    private fun getTileBaseImageLocations(viewingCiv: CivilizationInfo?): List<String> {
        if (viewingCiv == null && !showEntireMap) return tileSetStrings.hexagonList

        val baseHexagon = if (tileSetStrings.tileSetConfig.useColorAsBaseTerrain)
            listOf(tileSetStrings.hexagon)
        else listOf()

        if (tileInfo.naturalWonder != null)
            return if (tileSetStrings.tileSetConfig.useSummaryImages) baseHexagon + tileSetStrings.naturalWonder
            else baseHexagon + tileSetStrings.orFallback{ getTile(tileInfo.naturalWonder!!) }

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
            tileSetStrings.tileSetConfig.ruleVariants[allTogether] != null -> baseHexagon + tileSetStrings.tileSetConfig.ruleVariants[allTogether]!!.map { tileSetStrings.getTile(it) }
            ImageGetter.imageExists(allTogetherLocation) -> baseHexagon + allTogetherLocation
            else -> baseHexagon + getTerrainImageLocations(terrainImages) + getImprovementAndResourceImages(resourceAndImprovementSequence)
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

    /**
     * Used for: Underlying tile, unit overlays, border images, perhaps for other things in the future.
     * Parent should already be set when calling.
     *
     * Uses tileSetStrings.tileSetConfig.tileScale as default if scale is null
    */
    private fun setHexagonImageSize(hexagonImage: Image, scale: Float? = null) {
        hexagonImage.setSize(hexagonImageWidth, hexagonImage.height * hexagonImageWidth / hexagonImage.width)
        hexagonImage.setOrigin(hexagonImageOrigin.first, hexagonImageOrigin.second)
        hexagonImage.x = hexagonImagePosition.first
        hexagonImage.y = hexagonImagePosition.second
        hexagonImage.setScale(scale ?: tileSetStrings.tileSetConfig.tileScale)
    }

    private fun updateTileImage(viewingCiv: CivilizationInfo?) {
        val tileBaseImageLocations = getTileBaseImageLocations(viewingCiv)

        if (tileBaseImageLocations.size == tileImageIdentifiers.size) {
            if (tileBaseImageLocations.withIndex().all { (i, imageLocation) -> tileImageIdentifiers[i] == imageLocation })
                return // All image identifiers are the same as the current ones, no need to change anything
        }
        tileImageIdentifiers = tileBaseImageLocations

        for (image in tileBaseImages) image.remove()
        tileBaseImages.clear()
        for (baseLocation in tileBaseImageLocations) {
            // Here we check what actual tiles exist, and pick one - not at random, but based on the tile location,
            // so it stays consistent throughout the game
            if (!ImageGetter.imageExists(baseLocation)) continue

            val locationToCheck =
                    if (tileInfo.owningCity != null)
                        tileSetStrings.getOwnedTileImageLocation(baseLocation, tileInfo.getOwner()!!)
                    else baseLocation

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
            baseLayerGroup.addActor(image)

            if (tileSetStrings.tileSetConfig.tileScales.isNotEmpty()) {
                val scale = tileSetStrings.tileSetConfig.tileScales[baseLocation.takeLastWhile { it != '/' }]
                setHexagonImageSize(image, scale)
            } else {
                setHexagonImageSize(image)
            }
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

        updatePixelMilitaryUnit(tileIsViewable && showMilitaryUnit)
        updatePixelCivilianUnit(tileIsViewable)

        icons.update(showResourcesAndImprovements,showTileYields, tileIsViewable, showMilitaryUnit,viewingCiv)

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
                        tileSetStrings.orFallback { getBorder(borderShapeString,"Inner") }
                )
                borderLayerGroup.addActor(innerBorderImage)
                images.add(innerBorderImage)
                setHexagonImageSize(innerBorderImage)
                innerBorderImage.rotateBy(angle)
                innerBorderImage.color = civOuterColor

                val outerBorderImage = ImageGetter.getImage(
                        tileSetStrings.orFallback { getBorder(borderShapeString, "Outer") }
                )
                borderLayerGroup.addActor(outerBorderImage)
                images.add(outerBorderImage)
                setHexagonImageSize(outerBorderImage)
                outerBorderImage.rotateBy(angle)
                outerBorderImage.color = civInnerColor
            }
        }
    }

    /** Create and setup Actors for all arrows to be drawn from this tile. */
    private fun updateArrows() {
        for (actorList in arrows.values)
            for (actor in actorList)
                actor.remove()
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
        val baseTerrainColor = when {
            tileSetStrings.tileSetConfig.useColorAsBaseTerrain && !isViewable -> tileInfo.getBaseTerrain().getColor().lerp(tileSetStrings.tileSetConfig.fogOfWarColor, 0.6f)
            tileSetStrings.tileSetConfig.useColorAsBaseTerrain -> tileInfo.getBaseTerrain().getColor()
            !isViewable -> Color.WHITE.cpy().lerp(tileSetStrings.tileSetConfig.fogOfWarColor, 0.6f)
            else -> Color.WHITE.cpy()
        }

        val color = if (!isViewable) Color.WHITE.cpy().lerp(tileSetStrings.tileSetConfig.fogOfWarColor, 0.6f)
        else Color.WHITE.cpy()

        for((index, image) in tileBaseImages.withIndex())
            image.color = if (index == 0) baseTerrainColor else color
    }

    private fun updatePixelMilitaryUnit(showMilitaryUnit: Boolean) {
        var newImageLocation = ""

        val militaryUnit = tileInfo.militaryUnit
        if (militaryUnit != null && showMilitaryUnit && UncivGame.Current.settings.showPixelUnits) {
            newImageLocation = tileSetStrings.getUnitImageLocation(militaryUnit)
        }

        val nationName = if (militaryUnit != null) "${militaryUnit.civInfo.civName}-" else ""
        if (pixelMilitaryUnitImageLocation != "$nationName$newImageLocation") {
            pixelMilitaryUnitGroup.clear()
            pixelMilitaryUnitImageLocation = "$nationName$newImageLocation"

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

        if (civilianUnit != null && tileIsViewable && UncivGame.Current.settings.showPixelUnits) {
            newImageLocation = tileSetStrings.getUnitImageLocation(civilianUnit)
        }

        val nationName = if (civilianUnit != null) "${civilianUnit.civInfo.civName}-" else ""
        if (pixelCivilianUnitImageLocation != "$nationName$newImageLocation") {
            pixelCivilianUnitGroup.clear()
            pixelCivilianUnitImageLocation = "$nationName$newImageLocation"

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
