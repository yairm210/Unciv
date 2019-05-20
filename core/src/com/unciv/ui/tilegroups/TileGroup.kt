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
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.ui.cityscreen.YieldGroup
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.UnitGroup
import com.unciv.ui.utils.center
import com.unciv.ui.utils.centerX



open class TileGroup(var tileInfo: TileInfo) : Group() {
    val groupSize = 54f
    val tileSetLocation = "TileSets/"+UnCivGame.Current.settings.tileSet +"/"

    /*
    Layers:
    Base image + overlay
    Feature overlay / city overlay
    Misc: Units, improvements, resources, border
    Circle, Crosshair, Fog layer
    City name
     */
    val baseLayerGroup = Group().apply { isTransform=false; setSize(groupSize,groupSize) }
    protected var tileBaseImage :Image= ImageGetter.getImage(tileSetLocation+"Hexagon")
    var currentTileBaseImageLocation = ""
    protected var baseTerrainOverlayImage: Image? = null
    protected var baseTerrain:String=""

    val featureLayerGroup = Group().apply { isTransform=false; setSize(groupSize,groupSize) }
    protected var terrainFeatureOverlayImage: Image? = null
    protected var terrainFeature:String?=null
    protected var cityImage: Image? = null

    val miscLayerGroup = Group().apply { isTransform=false; setSize(groupSize,groupSize) }
    var resourceImage: Actor? = null
    var resource:String?=null
    var improvementImage: Actor? = null
    var populationImage: Image? = null //reuse for acquire icon
    private val roadImages = HashMap<TileInfo, RoadImage>()
    private val borderImages = HashMap<TileInfo, List<Image>>() // map of neighboring tile to border images

    val unitLayerGroup = Group().apply { isTransform=false; setSize(groupSize,groupSize);touchable=Touchable.disabled }
    protected var civilianUnitImage: UnitGroup? = null
    protected var militaryUnitImage: UnitGroup? = null

    val cityButtonLayerGroup = Group().apply { isTransform=true; setSize(groupSize,groupSize);touchable=Touchable.childrenOnly }

    val circleCrosshairFogLayerGroup = Group().apply { isTransform=false; setSize(groupSize,groupSize) }
    private val circleImage = ImageGetter.getCircle() // for blue and red circles on the tile
    private val crosshairImage = ImageGetter.getImage("OtherIcons/Crosshair.png") // for when a unit is targete
    protected val fogImage = ImageGetter.getImage(tileSetLocation+"CrosshatchHexagon")

    var yieldGroup = YieldGroup()

    var showEntireMap = UnCivGame.Current.viewEntireMapForDebug
    var forMapEditorIcon = false

    class RoadImage {
        var roadStatus: RoadStatus = RoadStatus.None
        var image: Image? = null
    }

    init {
        this.setSize(groupSize, groupSize)
        this.addActor(baseLayerGroup)
        this.addActor(featureLayerGroup)
        this.addActor(miscLayerGroup)
        this.addActor(unitLayerGroup)
        this.addActor(cityButtonLayerGroup)
        this.addActor(circleCrosshairFogLayerGroup)

        updateTileImage(false)

        addCircleImage()
        addFogImage(groupSize)
        addCrosshairImage()
        isTransform=false // performance helper - nothing here is rotated or scaled
    }


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

    fun showCrosshair() {
        crosshairImage.isVisible = true
    }

    fun getTileBaseImageLocation(isRevealed: Boolean): String {
        if(!isRevealed) return tileSetLocation+"Hexagon"
        if(tileInfo.isCityCenter()){
            if(ImageGetter.imageExists(tileSetLocation+tileInfo.baseTerrain+"+City"))
                return tileSetLocation+tileInfo.baseTerrain+"+City"
            if(ImageGetter.imageExists(tileSetLocation+"City"))
                return tileSetLocation+"City"
        }
        val baseTerrainTileLocation = tileSetLocation+tileInfo.baseTerrain
        val baseTerrainAndFeatureTileLocation = baseTerrainTileLocation+"+"+tileInfo.terrainFeature
        if(tileInfo.terrainFeature!=null && ImageGetter.imageExists(baseTerrainAndFeatureTileLocation))
            return baseTerrainAndFeatureTileLocation
        if(ImageGetter.imageExists(baseTerrainTileLocation)) return baseTerrainTileLocation
        return tileSetLocation+"Hexagon"
    }

    private fun updateTileImage(isRevealed: Boolean) {
        val tileBaseImageLocation = getTileBaseImageLocation(isRevealed)
        if(tileBaseImageLocation==currentTileBaseImageLocation) return

        tileBaseImage.remove()
        tileBaseImage = ImageGetter.getImage(tileBaseImageLocation)
        currentTileBaseImageLocation = tileBaseImageLocation

        val imageScale = groupSize * 1.5f / tileBaseImage.width
        // Using "scale" can get really confusing when positioning, how about no
        tileBaseImage.setSize(tileBaseImage.width*imageScale, tileBaseImage.height*imageScale)
        tileBaseImage.centerX(this)

        tileBaseImage.y = -groupSize/6
        tileBaseImage.toBack()
        baseLayerGroup.addActor(tileBaseImage)
    }

    fun addAcquirableIcon(){
        populationImage = ImageGetter.getStatIcon("Acquire")
        populationImage!!.run {
            color = Color.GREEN.cpy().lerp(Color.BLACK, 0.5f)
            setSize(20f, 20f)
            center(this@TileGroup)
            x += 20 // right
        }
        miscLayerGroup.addActor(populationImage)
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
        miscLayerGroup.addActor(populationImage)
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
            tileBaseImage.color = Color.BLACK
            return
        }

        updateTileImage(true)
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

        crosshairImage.isVisible = false
        fogImage.isVisible = !(isViewable || showEntireMap)
    }

    private fun updateTerrainBaseImage() {
        if (tileInfo.baseTerrain == baseTerrain) return

        if(baseTerrainOverlayImage!=null){
            baseTerrainOverlayImage!!.remove()
            baseTerrainOverlayImage=null
        }

        val imagePath = tileSetLocation + tileInfo.baseTerrain + "Overlay"
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
        if(!ImageGetter.imageExists(tileSetLocation+"CityOverlay")) // have a city tile, don't need an overlay
            return

        if (cityImage == null && tileInfo.isCityCenter()) {
            cityImage = ImageGetter.getImage(tileSetLocation+"CityOverlay")
            featureLayerGroup.addActor(cityImage)
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
        if(forMapEditorIcon) return
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
            else ImageGetter.getImage(tileSetLocation+"Railroad.png")
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
            featureLayerGroup.addActor(image)
        }


    }

    private fun updateTileColor(isViewable: Boolean) {
        tileBaseImage.color =
                if (ImageGetter.imageExists(tileSetLocation + tileInfo.baseTerrain)) Color.WHITE // no need to color it, it's already colored
                else tileInfo.getBaseTerrain().getColor()

        if (!isViewable) tileBaseImage.color = tileBaseImage.color.lerp(Color.BLACK, 0.6f)
    }

    private fun updateTerrainFeatureImage() {
        if (tileInfo.terrainFeature != terrainFeature) {
            terrainFeature = tileInfo.terrainFeature
            if(terrainFeatureOverlayImage!=null) terrainFeatureOverlayImage!!.remove()
            terrainFeatureOverlayImage = null

            if(terrainFeature!=null) {
                val terrainFeatureOverlayLocation = tileSetLocation +"$terrainFeature"+"Overlay"
                if(!ImageGetter.imageExists(terrainFeatureOverlayLocation)) return
                terrainFeatureOverlayImage = ImageGetter.getImage(terrainFeatureOverlayLocation)
                featureLayerGroup.addActor(terrainFeatureOverlayImage)
                terrainFeatureOverlayImage!!.run {
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
            miscLayerGroup.addActor(improvementImage)
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
        if(resource!=tileInfo.resource){
            resource=tileInfo.resource
            if (resourceImage != null) resourceImage!!.remove()
            if (resource==null) resourceImage=null
            else {
                resourceImage = ImageGetter.getResourceImage(tileInfo.resource!!, 20f)
                resourceImage!!.center(this)
                resourceImage!!.x = resourceImage!!.x - 22 // left
                resourceImage!!.y = resourceImage!!.y + 10 // top
                miscLayerGroup.addActor(resourceImage!!)
            }
        }

        if (resourceImage != null) { // This could happen on any turn, since resources need certain techs to reveal them
            val shouldDisplayResource =
                    if(showEntireMap) tileInfo.resource!=null
                    else showResourcesAndImprovements
                            && tileInfo.hasViewableResource(tileInfo.tileMap.gameInfo.getCurrentPlayerCivilization())
            resourceImage!!.isVisible = shouldDisplayResource
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
            unitLayerGroup.addActor(newImage)
            newImage.center(this)
            newImage.y += yFromCenter

            if (!unit.isIdle() && unit.civInfo.isPlayerCivilization()) newImage.color.a = 0.5f
        }
        return newImage
    }




    fun showCircle(color: Color) {
        circleImage.isVisible = true
        circleImage.color = color.cpy().apply { a=0.3f }
    }

    fun hideCircle() {
        circleImage.isVisible = false
    }

}