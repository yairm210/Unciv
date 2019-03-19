package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.unciv.UnCivGame
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.ui.cityscreen.YieldGroup
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.UnitGroup
import com.unciv.ui.utils.center

open class TileGroup(var tileInfo: TileInfo) : Group() {
    val tileSetLocation = "TerrainIcons/"+UnCivGame.Current.settings.tileSet +"/"

    protected var hexagon :Image= ImageGetter.getImage("TerrainIcons/Hexagon.png")
    protected var baseTerrainImage: Image? = null
    protected var baseTerrain:String=""
    protected var terrainFeatureImage: Image? = null
    protected var terrainFeature:String?=null
    protected var cityImage: Image? = null

    var resourceImage: Actor? = null
    var resource:String?=null
    var improvementImage: Actor? = null
    var populationImage: Image? = null //reuse for acquire icon
    private val roadImages = HashMap<TileInfo, RoadImage>()
    private val borderImages = HashMap<TileInfo, List<Image>>() // map of neighboring tile to border images
    protected var civilianUnitImage: UnitGroup? = null
    protected var militaryUnitImage: UnitGroup? = null
    private val circleImage = ImageGetter.getCircle() // for blue and red circles on the tile
    private val crosshairImage = ImageGetter.getImage("OtherIcons/Crosshair.png") // for when a unit is targeted
    protected val fogImage = ImageGetter.getImage("TerrainIcons/CrosshatchHexagon")
    var yieldGroup = YieldGroup()

    var showEntireMap = UnCivGame.Current.viewEntireMapForDebug

    class RoadImage {
        var roadStatus: RoadStatus = RoadStatus.None
        var image: Image? = null
    }

    init {
        val groupSize = 54f
        this.setSize(groupSize, groupSize)

        addHexagon(groupSize)

        addCircleImage()
        addFogImage(groupSize)
        addCrosshairImage()
        isTransform=false // performance helper - nothing here is rotated or scaled
    }


    private fun addCircleImage() {
        circleImage.width = 50f
        circleImage.height = 50f
        circleImage.center(this)
        addActor(circleImage)
        circleImage.isVisible = false
    }

    private fun addFogImage(groupSize: Float) {
        val imageScale = groupSize * 1.5f / fogImage.width
        fogImage.setScale(imageScale)
        fogImage.setOrigin(Align.center)
        fogImage.center(this)
        fogImage.color = Color.WHITE.cpy().apply { a = 0.2f }
        addActor(fogImage)
    }

    private fun addCrosshairImage() {
        crosshairImage.width = 70f
        crosshairImage.height = 70f
        crosshairImage.center(this)
        crosshairImage.isVisible = false
        crosshairImage.color = Color.WHITE.cpy().apply { a = 0.5f }
        addActor(crosshairImage)
    }

    fun showCrosshair() {
        crosshairImage.isVisible = true
    }

    private fun addHexagon(groupSize: Float) {
        val terrainTileLocation = tileSetLocation+tileInfo.baseTerrain
        if(ImageGetter.imageExists(terrainTileLocation))
            hexagon = ImageGetter.getImage(terrainTileLocation)

        val imageScale = groupSize * 1.5f / hexagon.width
        hexagon.setScale(imageScale)
        hexagon.setOrigin(Align.center)
        hexagon.center(this)
        hexagon.zIndex = 0
        addActor(hexagon)
    }

    fun addAcquirableIcon(){
        this.
        populationImage = ImageGetter.getStatIcon("Acquire")
        populationImage!!.run {
            color = Color.GREEN.cpy().lerp(Color.BLACK, 0.5f)
            setSize(20f, 20f)
            center(this@TileGroup)
            x += 20 // right
        }
        addActor(populationImage)
    }

    fun addPopulationIcon() {
        this.
        populationImage = ImageGetter.getStatIcon("Population")
        populationImage!!.run {
            color = Color.GREEN.cpy().lerp(Color.BLACK, 0.5f)
            setSize(20f, 20f)
            center(this@TileGroup)
            x += 20 // right
        }
        addActor(populationImage)
    }

    protected fun removePopulationIcon() {
        if (populationImage != null) {
            populationImage!!.remove()
            populationImage = null
        }
    }


    open fun update(isViewable: Boolean, showResourcesAndImprovements:Boolean, showSubmarine: Boolean) {
        hideCircle()
        if (!showEntireMap
                && !tileInfo.tileMap.gameInfo.getCurrentPlayerCivilization().exploredTiles.contains(tileInfo.position)) {
            hexagon.color = Color.BLACK
            return
        }

        updateTerrainBaseImage()
        updateTerrainFeatureImage()
        updateCityImage()
        updateTileColor(isViewable)

        updateResourceImage(showResourcesAndImprovements)
        updateImprovementImage(showResourcesAndImprovements)


        civilianUnitImage = newUnitImage(tileInfo.civilianUnit, civilianUnitImage, isViewable, -20f)
        militaryUnitImage = newUnitImage(tileInfo.militaryUnit, militaryUnitImage, isViewable && showSubmarine, 20f)

        updateRoadImages()
        updateBorderImages()

        crosshairImage.toFront()
        crosshairImage.isVisible = false

        fogImage.toFront()
        fogImage.isVisible = !(isViewable || showEntireMap)
    }

    private fun updateTerrainBaseImage() {
        if (tileInfo.baseTerrain == baseTerrain) return

        if(baseTerrainImage!=null){
            baseTerrainImage!!.remove()
            baseTerrainImage=null
        }

        val imagePath = tileSetLocation + tileInfo.baseTerrain + "Overlay"
        if (!ImageGetter.imageExists(imagePath)) return
        baseTerrainImage = ImageGetter.getImage(imagePath)
        baseTerrainImage!!.run {
            color.a = 0.25f
            setSize(40f, 40f)
            center(this@TileGroup)
        }
        addActor(baseTerrainImage)
    }

    private fun updateCityImage() {
        if (cityImage == null && tileInfo.isCityCenter()) {
            cityImage = ImageGetter.getImage("OtherIcons/City.png")
            addActor(cityImage)
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

        val civColor = tileInfo.getOwner()!!.getNation().getColor()
        for (neighbor in tileInfo.neighbors) {
            val neigborOwner = neighbor.getOwner()
            if (neigborOwner == tileOwner && borderImages.containsKey(neighbor)) // the neighbor used to not belong to us, but now it's ours
            {
                for (image in borderImages[neighbor]!!)
                    image.remove()
                borderImages.remove(neighbor)
            }
            if (neigborOwner != tileOwner && !borderImages.containsKey(neighbor)) { // there should be a border here but there isn't

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

                    // And now, move it within the hexagon side according to i.
                    // Remember, if from the center of the heagon to the middle of the side is an (a,b) vecctor,
                    // Then within the side, which is of course perpendicular to the (a,b) vector,
                    // we can move with multiples of (b,-a) which is perpendicular to (a,b)
                    image.moveBy(relativeWorldPosition.y * i * 4, -relativeWorldPosition.x * i * 4)

                    image.color = civColor
                    addActor(image)
                    images.add(image)
                }
            }
        }
    }

    private fun updateRoadImages() {
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
            else ImageGetter.getImage("OtherIcons/Railroad.png")
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
            addActor(image)
        }


    }

    private fun updateTileColor(isViewable: Boolean) {
        if (!isViewable) hexagon.color = hexagon.color.lerp(Color.BLACK, 0.6f)
        else if(ImageGetter.imageExists(tileSetLocation+tileInfo.baseTerrain)) return // no need to color it, it's already colored
        else hexagon.color = tileInfo.getBaseTerrain().getColor()
    }

    private fun updateTerrainFeatureImage() {
        if (tileInfo.terrainFeature != terrainFeature) {
            terrainFeature = tileInfo.terrainFeature
            if(terrainFeatureImage!=null) terrainFeatureImage!!.remove()
            terrainFeatureImage = null

            if(terrainFeature!=null) {
                terrainFeatureImage = ImageGetter.getImage(tileSetLocation +"$terrainFeature"+"Overlay")
                addActor(terrainFeatureImage)
                terrainFeatureImage!!.run {
                    setSize(30f, 30f)
                    setColor(1f, 1f, 1f, 0.5f)
                    center(this@TileGroup)
                }
            }
        }
    }

    private fun updateImprovementImage(showResourcesAndImprovements: Boolean) {
        if (improvementImage != null) {
            improvementImage!!.remove()
            improvementImage = null
        }

        if (tileInfo.improvement != null && showResourcesAndImprovements) {
            improvementImage = ImageGetter.getImprovementIcon(tileInfo.improvement!!)
            addActor(improvementImage)
            improvementImage!!.run {
                setSize(20f, 20f)
                center(this@TileGroup)
                this.x -= 22 // left
                this.y -= 10 // bottom
            }
        }
        if (improvementImage != null) {
            improvementImage!!.color = Color.WHITE.cpy().apply { a = 0.7f }
        }
    }

    private fun updateResourceImage(showResourcesAndImprovements: Boolean) {
        val shouldDisplayResource =
            if(showEntireMap) tileInfo.resource!=null
            else showResourcesAndImprovements
                && tileInfo.hasViewableResource(tileInfo.tileMap.gameInfo.getCurrentPlayerCivilization())

        if(resource!=tileInfo.resource){
            resource=tileInfo.resource
            if (resourceImage != null) resourceImage!!.remove()
            resourceImage=null
        }

        if (resourceImage == null && shouldDisplayResource) { // This could happen on any turn, since resources need certain techs to reveal them
            resourceImage = ImageGetter.getResourceImage(tileInfo.resource!!, 20f)
            resourceImage!!.center(this)
            resourceImage!!.x = resourceImage!!.x - 22 // left
            resourceImage!!.y = resourceImage!!.y + 10 // top
            addActor(resourceImage!!)
        }
    }


    protected fun newUnitImage(unit: MapUnit?, oldUnitGroup: UnitGroup?, isViewable: Boolean, yFromCenter: Float): UnitGroup? {
        var newImage: UnitGroup? = null
        // The unit can change within one update - for instance, when attacking, the attacker replaces the defender!
        oldUnitGroup?.remove()

        if (unit != null && isViewable) { // Tile is visible
            newImage = UnitGroup(unit, 25f)
            if(oldUnitGroup?.blackSpinningCircle != null){
                newImage.blackSpinningCircle = ImageGetter.getCircle()
                        .apply { rotation= oldUnitGroup.blackSpinningCircle!!.rotation}
            }
            addActor(newImage)
            newImage.center(this)
            newImage.y += yFromCenter

            if (!unit.isIdle() && unit.civInfo.isPlayerCivilization()) newImage.color.a = 0.5f
        }
        return newImage
    }




    fun showCircle(color: Color) {
        circleImage.isVisible = true
        val colorCopy = color.cpy()
        colorCopy.a = 0.3f
        circleImage.color = colorCopy
    }

    fun hideCircle() {
        circleImage.isVisible = false
    }

}