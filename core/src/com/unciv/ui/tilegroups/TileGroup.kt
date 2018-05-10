package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.HexMath
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.fromRGB

open class TileGroup(var tileInfo: TileInfo) : Group() {

    protected val hexagon = ImageGetter.getImage("TerrainIcons/Hexagon.png")
    protected var terrainFeatureImage:Image?=null

    protected var resourceImage: Image? = null
    protected var improvementImage: Image? =null
    private var improvementType: String? = null
    var populationImage: Image? = null
    private val roadImages = HashMap<String, Image>()
    private val borderImages = ArrayList<Image>()
    protected var unitImage: Group? = null
    private val circleImage = ImageGetter.getImage("UnitIcons/Circle.png") // for blue and red circles on the tile

    init {
        val groupSize = 50f
        this.setSize(groupSize,groupSize)
        addHexagon(groupSize)
        addCircleImage()
    }

    private fun addCircleImage() {
        circleImage.width = 50f
        circleImage.height = 50f
        circleImage.setPosition(width / 2 - circleImage.width / 2,
                height / 2 - circleImage.height / 2)
        addActor(circleImage)
        circleImage.isVisible = false
    }

    private fun addHexagon(groupSize: Float) {
        val imageScale = groupSize * 1.5f / hexagon.width
        hexagon.setScale(imageScale)
        hexagon.setOrigin(Align.center)
        hexagon.setPosition((width - hexagon.width) / 2,
                (height - hexagon.height) / 2)
        hexagon.zIndex = 0
        addActor(hexagon)
    }

    fun addPopulationIcon() {
        populationImage = ImageGetter.getImage("StatIcons/populationGreen.png")
        populationImage!!.run {
            setSize(20f, 20f)
            setPosition(this@TileGroup.width/2 - width/2,
                    this@TileGroup.height/2 - height/2 - 20)
        } // top left
        addActor(populationImage)
    }

    protected fun removePopulationIcon() {
        populationImage!!.remove()
        populationImage = null
    }


    open fun update(isViewable: Boolean) {
        hideCircle()
        if (!tileInfo.tileMap.gameInfo.getPlayerCivilization().exploredTiles.contains(tileInfo.position)) {
            hexagon.color = Color.BLACK
            return
        }

        updateTerrainFeatureImage()
        updateTileColor(isViewable)
        updateResourceImage()
        updateImprovementImage()

        updateRoadImages()
        updateBorderImages()
    }

    private fun updateBorderImages() {
        for (border in borderImages) border.remove() //clear
        borderImages.clear()

        if (tileInfo.getOwner() != null) {
            for (neighbor in tileInfo.neighbors.filter { it.getOwner() != tileInfo.getOwner() }) {
                val image = ImageGetter.getImage(ImageGetter.WhiteDot)

                val relativeHexPosition = tileInfo.position.cpy().sub(neighbor.position)
                val relativeWorldPosition = HexMath.Hex2WorldCoords(relativeHexPosition)

                // This is some crazy voodoo magic so I'll explain.

                image.setSize(35f, 2f)
                image.moveBy(width / 2 - image.width / 2, // center
                        height / 2 - image.height / 2)
                // in addTiles, we set the position of groups by relative world position *0.8*groupSize, filter groupSize = 50
                // Here, we want to have the borders start HALFWAY THERE and extend towards the tiles, so we give them a position of 0.8*25.
                // BUT, we don't actually want it all the way out there, because we want to display the borders of 2 different civs!
                // So we set it to 0.75
                image.moveBy(-relativeWorldPosition.x * 0.75f * 25f, -relativeWorldPosition.y * 0.75f * 25f)

                image.color = tileInfo.getOwner()!!.getCivilization().getColor()
                image.setOrigin(image.width / 2, image.height / 2) // This is so that the rotation is calculated from the middle of the road and not the edge
                image.rotation = (90 + 180 / Math.PI * Math.atan2(relativeWorldPosition.y.toDouble(), relativeWorldPosition.x.toDouble())).toFloat()
                addActor(image)
                borderImages.add(image)
            }

        }
    }

    private fun updateRoadImages() {
        if (tileInfo.roadStatus !== RoadStatus.None) {
            for (neighbor in tileInfo.neighbors) {
                if (neighbor.roadStatus === RoadStatus.None) continue
                if (!roadImages.containsKey(neighbor.position.toString())) {
                    val image = ImageGetter.getImage(ImageGetter.WhiteDot)
                    roadImages[neighbor.position.toString()] = image

                    val relativeHexPosition = tileInfo.position.cpy().sub(neighbor.position)
                    val relativeWorldPosition = HexMath.Hex2WorldCoords(relativeHexPosition)

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

                if (tileInfo.roadStatus === RoadStatus.Railroad && neighbor.roadStatus === RoadStatus.Railroad)
                    roadImages[neighbor.position.toString()]!!.color = Color.GRAY // railroad
                else
                    roadImages[neighbor.position.toString()]!!.color = Color.BROWN // road
            }
        }
    }

    private fun updateTileColor(isViewable: Boolean) {
        val RGB = tileInfo.getBaseTerrain().RGB!!
        hexagon.color = Color().fromRGB(RGB[0], RGB[1],RGB[2])
        if (!isViewable) hexagon.color = hexagon.color.lerp(Color.BLACK, 0.6f)
    }

    private fun updateTerrainFeatureImage() {
        if (terrainFeatureImage == null && tileInfo.terrainFeature != null) {
            terrainFeatureImage = ImageGetter.getImage("TerrainIcons/${tileInfo.terrainFeature}.png")
            addActor(terrainFeatureImage)
            terrainFeatureImage!!.run {
                setSize(30f, 30f)
                setColor(1f, 1f, 1f, 0.5f)
                setPosition(this@TileGroup.width / 2 - width / 2,
                        this@TileGroup.height / 2 - height / 2)
            }
        }

        if (terrainFeatureImage != null && tileInfo.terrainFeature == null) {
            terrainFeatureImage!!.remove()
            terrainFeatureImage = null
        }
    }

    private fun updateImprovementImage() {
        if (tileInfo.improvement != null && tileInfo.improvement != improvementType) {
            improvementImage = ImageGetter.getImage("ImprovementIcons/" + tileInfo.improvement!!.replace(' ', '_') + "_(Civ5).png")
            addActor(improvementImage)
            improvementImage!!.run {
                setSize(20f, 20f)

                setPosition(this@TileGroup.width / 2 - width / 2 + 20f,
                        this@TileGroup.height / 2 - height / 2) // right
            }
            improvementType = tileInfo.improvement
        }
    }

    private fun updateResourceImage() {
        if (tileInfo.hasViewableResource(tileInfo.tileMap.gameInfo.getPlayerCivilization()) && resourceImage == null) { // Need to add the resource image!
            val fileName = "ResourceIcons/" + tileInfo.resource + "_(Civ5).png"
                resourceImage = ImageGetter.getImage(fileName)
                resourceImage!!.setSize(20f, 20f)
            resourceImage!!.setPosition(width / 2 - resourceImage!!.width / 2 - 20f,
                    height / 2 - resourceImage!!.height / 2) // left
            addActor(resourceImage!!)
        }
    }


    protected fun updateUnitImage(isViewable: Boolean) {
        if (unitImage != null) { // The unit can change within one update - for instance, when attacking, the attacker replaces the defender!
            unitImage!!.remove()
            unitImage = null
        }

        if (tileInfo.unit != null && isViewable) { // Tile is visible
            val unit = tileInfo.unit!!
            unitImage = getUnitImage(unit.name, unit.civInfo.getCivilization().getColor())
            addActor(unitImage!!)
            unitImage!!.setSize(20f, 20f)
        }


        if (unitImage != null) {
            if (!tileInfo.hasIdleUnit())
                unitImage!!.color = Color(1f, 1f, 1f, 0.5f)
            else
                unitImage!!.color = Color.WHITE
        }
    }


    private fun getUnitImage(unitType:String, color:Color): Group {
        val unitBaseImage = ImageGetter.getImage("UnitIcons/$unitType.png")
                .apply { setSize(15f,15f) }
        val background = ImageGetter.getImage("UnitIcons/Circle.png").apply {
            this.color = color
            setSize(20f,20f)
        }
        val group = Group().apply {
            setSize(background.width,background.height)
            addActor(background)
        }
        unitBaseImage.setPosition(group.width/2-unitBaseImage.width/2,
                group.height/2-unitBaseImage.height/2)
        group.addActor(unitBaseImage)
        return group
    }


    fun showCircle(color:Color){
        circleImage.isVisible = true
        color.a = 0.3f
        circleImage.color = color
    }

    fun hideCircle(){circleImage.isVisible=false}
}