package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.unciv.UnCivGame
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.center
import com.unciv.ui.utils.centerX


open class TileGroup(var tileInfo: TileInfo, var tileSetStrings:TileSetStrings) : Group() {
    val groupSize = 54f

    /*
    Layers:
    Base image + overlay
    Feature overlay / city overlay
    Misc: Units, improvements, resources, border
    Circle, Crosshair, Fog layer
    City name
     */
    val baseLayerGroup = Group().apply { isTransform = false; setSize(groupSize, groupSize) }
    protected var tileBaseImage: Image = ImageGetter.getImage(tileSetStrings.hexagon)
    var currentTileBaseImageLocation = ""
    protected var baseTerrainOverlayImage: Image? = null
    protected var baseTerrain: String = ""

    val terrainFeatureLayerGroup = Group().apply { isTransform = false; setSize(groupSize, groupSize) }
    protected var terrainFeatureOverlayImage: Image? = null
    protected var terrainFeature: String? = null
    protected var cityImage: Image? = null

    protected var pixelMilitaryUnitImageLocation = ""
    protected var pixelMilitaryUnitImage: Image? = null
    protected var pixelCivilianUnitImageLocation = ""
    protected var pixelCivilianUnitImage: Image? = null

    val miscLayerGroup = Group().apply { isTransform = false; setSize(groupSize, groupSize) }
    var resourceImage: Actor? = null
    var resource: String? = null
    private val roadImages = HashMap<TileInfo, RoadImage>()
    private val borderImages = HashMap<TileInfo, List<Image>>() // map of neighboring tile to border images

    val icons = TileGroupIcons(this)

    val unitLayerGroup = Group().apply { isTransform = false; setSize(groupSize, groupSize);touchable = Touchable.disabled }

    val cityButtonLayerGroup = Group().apply { isTransform = true; setSize(groupSize, groupSize);touchable = Touchable.childrenOnly }

    val circleCrosshairFogLayerGroup = Group().apply { isTransform = false; setSize(groupSize, groupSize) }
    private val circleImage = ImageGetter.getCircle() // for blue and red circles on the tile
    private val crosshairImage = ImageGetter.getImage("OtherIcons/Crosshair") // for when a unit is targete
    protected val fogImage = ImageGetter.getImage(tileSetStrings.crosshatchHexagon)


    var showEntireMap = UnCivGame.Current.viewEntireMapForDebug
    var forMapEditorIcon = false

    class RoadImage {
        var roadStatus: RoadStatus = RoadStatus.None
        var image: Image? = null
    }

    init {
        this.setSize(groupSize, groupSize)
        this.addActor(baseLayerGroup)
        this.addActor(terrainFeatureLayerGroup)
        this.addActor(miscLayerGroup)
        this.addActor(unitLayerGroup)
        this.addActor(cityButtonLayerGroup)
        this.addActor(circleCrosshairFogLayerGroup)

        updateTileImage(false)

        addCircleImage()
        addFogImage(groupSize)
        addCrosshairImage()
        isTransform = false // performance helper - nothing here is rotated or scaled
    }


    //region init functions
    private fun addCircleImage() {
        circleImage.width = 50f
        circleImage.height = 50f
        circleImage.center(this)
        circleCrosshairFogLayerGroup.addActor(circleImage)
        circleImage.isVisible = false
    }

    private fun addFogImage(groupSize: Float) {
        val imageScale = groupSize * 1.5f / fogImage.width
        fogImage.setScale(imageScale)
        fogImage.setOrigin(Align.center)
        fogImage.center(this)
        fogImage.color = Color.WHITE.cpy().apply { a = 0.2f }
        circleCrosshairFogLayerGroup.addActor(fogImage)
    }

    private fun addCrosshairImage() {
        crosshairImage.width = 70f
        crosshairImage.height = 70f
        crosshairImage.center(this)
        crosshairImage.isVisible = false
        crosshairImage.color = Color.WHITE.cpy().apply { a = 0.5f }
        circleCrosshairFogLayerGroup.addActor(crosshairImage)
    }
    //endregion

    fun showCrosshair() {
        crosshairImage.isVisible = true
    }

    fun getTileBaseImageLocation(isRevealed: Boolean): String {
        if (!isRevealed) return tileSetStrings.hexagon
        if (tileInfo.isCityCenter()) {
            val terrainAndCity = tileSetStrings.getCityTile(tileInfo.baseTerrain)
            if (ImageGetter.imageExists(terrainAndCity))
                return terrainAndCity
            if (ImageGetter.imageExists(tileSetStrings.cityTile))
                return tileSetStrings.cityTile
        }
        val baseTerrainTileLocation = tileSetStrings.getBaseTerrainTile(tileInfo.baseTerrain)
        if (tileInfo.terrainFeature != null) {
            val baseTerrainAndFeatureTileLocation = "$baseTerrainTileLocation+${tileInfo.terrainFeature}"
            if (ImageGetter.imageExists(baseTerrainAndFeatureTileLocation))
                return baseTerrainAndFeatureTileLocation
        }

        if (ImageGetter.imageExists(baseTerrainTileLocation)) return baseTerrainTileLocation
        return tileSetStrings.hexagon
    }

    // Used for both the underlying tile and unit overlays, perhaps for other things in the future
    // Parent should already be set when calling
    fun setHexagonImageSize(hexagonImage: Image) {
        val imageScale = groupSize * 1.5f / hexagonImage.width
        // Using "scale" can get really confusing when positioning, how about no
        hexagonImage.setSize(hexagonImage.width * imageScale, hexagonImage.height * imageScale)
        hexagonImage.centerX(hexagonImage.parent)
        hexagonImage.y = -groupSize / 6
    }

    private fun updateTileImage(isRevealed: Boolean) {
        val tileBaseImageLocation = getTileBaseImageLocation(isRevealed)
        if (tileBaseImageLocation == currentTileBaseImageLocation) return

        tileBaseImage.remove()
        tileBaseImage = ImageGetter.getImage(tileBaseImageLocation)
        currentTileBaseImageLocation = tileBaseImageLocation

        baseLayerGroup.addActor(tileBaseImage)
        setHexagonImageSize(tileBaseImage)
        tileBaseImage.toBack()
    }

    fun showMilitaryUnit(viewingCiv: CivilizationInfo) = showEntireMap
            || viewingCiv.viewableInvisibleUnitsTiles.contains(tileInfo)
            || (!tileInfo.hasEnemySubmarine(viewingCiv))

    fun isViewable(viewingCiv: CivilizationInfo) = showEntireMap
            || viewingCiv.viewableTiles.contains(tileInfo)

    open fun update(viewingCiv: CivilizationInfo? = null, showResourcesAndImprovements: Boolean = true) {
        hideCircle()
        if (viewingCiv != null && !showEntireMap
                && !viewingCiv.exploredTiles.contains(tileInfo.position)) {
            tileBaseImage.color = Color.DARK_GRAY
            return
        }

        val tileIsViewable = viewingCiv == null || isViewable(viewingCiv)
        val showMilitaryUnit = viewingCiv == null || showMilitaryUnit(viewingCiv)

        updateTileImage(true)
        updateTerrainBaseImage()
        updateTerrainFeatureImage()

        updatePixelMilitaryUnit(tileIsViewable && showMilitaryUnit)
        updatePixelCivilianUnit(tileIsViewable)

        icons.update(showResourcesAndImprovements, tileIsViewable, showMilitaryUnit)

        updateCityImage()
        updateTileColor(tileIsViewable)

        updateRoadImages()
        updateBorderImages()

        crosshairImage.isVisible = false
        fogImage.isVisible = !(tileIsViewable || showEntireMap)
    }

    private fun updateTerrainBaseImage() {
        if (tileInfo.baseTerrain == baseTerrain) return
        baseTerrain = tileInfo.baseTerrain

        if (baseTerrainOverlayImage != null) {
            baseTerrainOverlayImage!!.remove()
            baseTerrainOverlayImage = null
        }

        val imagePath = tileSetStrings.getBaseTerrainOverlay(baseTerrain)
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

    var previousTileOwner: CivilizationInfo? = null
    private fun updateBorderImages() {
        // This is longer than it could be, because of performance -
        // before fixing, about half (!) the time of update() was wasted on
        // removing all the border images and putting them back again!
        val tileOwner = tileInfo.getOwner()
        if (previousTileOwner != tileOwner) {
            for (images in borderImages.values)
                for (image in images)
                    image.remove()

            borderImages.clear()
        }
        previousTileOwner = tileOwner
        if (tileOwner == null) return

        val civColor = tileInfo.getOwner()!!.nation.getOuterColor()
        for (neighbor in tileInfo.neighbors) {
            val neighborOwner = neighbor.getOwner()
            if (neighborOwner == tileOwner && borderImages.containsKey(neighbor)) // the neighbor used to not belong to us, but now it's ours
            {
                for (image in borderImages[neighbor]!!)
                    image.remove()
                borderImages.remove(neighbor)
            }
            if (neighborOwner != tileOwner && !borderImages.containsKey(neighbor)) { // there should be a border here but there isn't

                val relativeHexPosition = tileInfo.position.cpy().sub(neighbor.position)
                val relativeWorldPosition = HexMath().hex2WorldCoords(relativeHexPosition)

                // This is some crazy voodoo magic so I'll explain.
                val images = mutableListOf<Image>()
                borderImages[neighbor] = images
                for (i in -2..2) {
                    val image = ImageGetter.getCircle()
                    image.setSize(5f, 5f)
                    image.center(this)
                    // in addTiles, we set the position of groups by relative world position *0.8*groupSize, filter groupSize = 50
                    // Here, we want to have the borders start HALFWAY THERE and extend towards the tiles, so we give them a position of 0.8*25.
                    // BUT, we don't actually want it all the way out there, because we want to display the borders of 2 different civs!
                    // So we set it to 0.75
                    image.moveBy(-relativeWorldPosition.x * 0.75f * 25f, -relativeWorldPosition.y * 0.75f * 25f)

                    // And now, move it within the tileBaseImage side according to i.
                    // Remember, if from the center of the heagon to the middle of the side is an (a,b) vecctor,
                    // Then within the side, which is of course perpendicular to the (a,b) vector,
                    // we can move with multiples of (b,-a) which is perpendicular to (a,b)
                    image.moveBy(relativeWorldPosition.y * i * 4, -relativeWorldPosition.x * i * 4)

                    image.color = civColor
                    miscLayerGroup.addActor(image)
                    images.add(image)
                }
            }
        }
    }

    private fun updateRoadImages() {
        if (forMapEditorIcon) return
        for (neighbor in tileInfo.neighbors) {
            if (!roadImages.containsKey(neighbor)) roadImages[neighbor] = RoadImage()
            val roadImage = roadImages[neighbor]!!

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

            val image = if (roadStatus == RoadStatus.Road) ImageGetter.getDot(Color.BROWN)
            else ImageGetter.getImage(tileSetStrings.railroad)
            roadImage.image = image

            val relativeHexPosition = tileInfo.position.cpy().sub(neighbor.position)
            val relativeWorldPosition = HexMath().hex2WorldCoords(relativeHexPosition)

            // This is some crazy voodoo magic so I'll explain.
            image.moveBy(25f, 25f) // Move road to center of tile
            // in addTiles, we set   the position of groups by relative world position *0.8*groupSize, filter groupSize = 50
            // Here, we want to have the roads start HALFWAY THERE and extend towards the tiles, so we give them a position of 0.8*25.
            image.moveBy(-relativeWorldPosition.x * 0.8f * 25f, -relativeWorldPosition.y * 0.8f * 25f)

            image.setSize(10f, 2f)
            image.setOrigin(0f, 1f) // This is so that the rotation is calculated from the middle of the road and not the edge

            image.rotation = (180 / Math.PI * Math.atan2(relativeWorldPosition.y.toDouble(), relativeWorldPosition.x.toDouble())).toFloat()
            terrainFeatureLayerGroup.addActor(image)
        }

    }

    private fun updateTileColor(isViewable: Boolean) {
        tileBaseImage.color =
                if (ImageGetter.imageExists(tileSetStrings.getBaseTerrainTile(tileInfo.baseTerrain)))
                    Color.WHITE // no need to color it, it's already colored
                else tileInfo.getBaseTerrain().getColor()

        if (!isViewable) tileBaseImage.color = tileBaseImage.color.lerp(Color.BLACK, 0.6f)
    }

    private fun updateTerrainFeatureImage() {
        if (tileInfo.terrainFeature != terrainFeature) {
            terrainFeature = tileInfo.terrainFeature
            if (terrainFeatureOverlayImage != null) terrainFeatureOverlayImage!!.remove()
            terrainFeatureOverlayImage = null

            if (terrainFeature != null) {
                val terrainFeatureOverlayLocation = tileSetStrings.getTerrainFeatureOverlay(terrainFeature!!)
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

    fun updatePixelMilitaryUnit(showMilitaryUnit: Boolean) {
        var newImageLocation = ""

        val militaryUnit = tileInfo.militaryUnit
        if (militaryUnit != null && showMilitaryUnit) {
            val unitType = militaryUnit.type
            val specificUnitIconLocation = tileSetStrings.unitsLocation + militaryUnit.name
            newImageLocation = when {
                !UnCivGame.Current.settings.showPixelUnits -> ""
                ImageGetter.imageExists(specificUnitIconLocation) -> specificUnitIconLocation

                militaryUnit.baseUnit.replaces!=null &&
                        ImageGetter.imageExists(tileSetStrings.unitsLocation + militaryUnit.baseUnit.replaces) ->
                    tileSetStrings.unitsLocation + militaryUnit.baseUnit.replaces

                unitType == UnitType.Mounted -> tileSetStrings.unitsLocation + "Horseman"
                unitType == UnitType.Ranged -> tileSetStrings.unitsLocation + "Archer"
                unitType == UnitType.Armor -> tileSetStrings.unitsLocation + "Tank"
                unitType == UnitType.Siege -> tileSetStrings.unitsLocation + "Catapult"
                unitType.isLandUnit() && ImageGetter.imageExists(tileSetStrings.landUnit) -> tileSetStrings.landUnit
                unitType.isWaterUnit() && ImageGetter.imageExists(tileSetStrings.waterUnit) -> tileSetStrings.waterUnit
                else -> ""
            }
        }

        if (pixelMilitaryUnitImageLocation != newImageLocation) {
            pixelMilitaryUnitImage?.remove()
            pixelMilitaryUnitImage = null
            pixelMilitaryUnitImageLocation = newImageLocation

            if (newImageLocation != "") {
                val pixelUnitImage = ImageGetter.getImage(newImageLocation)
                terrainFeatureLayerGroup.addActor(pixelUnitImage)
                setHexagonImageSize(pixelUnitImage)// Treat this as A TILE, which gets overlayed on the base tile.
                pixelMilitaryUnitImage = pixelUnitImage
            }
        }
    }


    fun updatePixelCivilianUnit(tileIsViewable: Boolean) {
        var newImageLocation = ""

        if (tileInfo.civilianUnit != null && tileIsViewable) {
            val specificUnitIconLocation = tileSetStrings.unitsLocation + tileInfo.civilianUnit!!.name
            newImageLocation = when {
                !UnCivGame.Current.settings.showPixelUnits -> ""
                ImageGetter.imageExists(specificUnitIconLocation) -> specificUnitIconLocation
                else -> ""
            }
        }

        if (pixelCivilianUnitImageLocation != newImageLocation) {
            pixelCivilianUnitImage?.remove()
            pixelCivilianUnitImage = null
            pixelCivilianUnitImageLocation = newImageLocation

            if (newImageLocation != "") {
                val pixelUnitImage = ImageGetter.getImage(newImageLocation)
                terrainFeatureLayerGroup.addActor(pixelUnitImage)
                setHexagonImageSize(pixelUnitImage)// Treat this as A TILE, which gets overlayed on the base tile.
                pixelCivilianUnitImage = pixelUnitImage
            }
        }
    }



    fun showCircle(color: Color, alpha: Float = 0.3f) {
        circleImage.isVisible = true
        circleImage.color = color.cpy().apply { a = alpha }
    }

    fun hideCircle() {
        circleImage.isVisible = false
    }
}