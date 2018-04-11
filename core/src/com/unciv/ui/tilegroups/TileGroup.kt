package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.ui.utils.HexMath
import com.unciv.ui.utils.ImageGetter

open class TileGroup(var tileInfo: TileInfo) : Group() {

    protected var hexagon: Image
    protected var terrainFeatureImage:Image?=null

    protected var resourceImage: Image? = null
    protected var improvementImage: Image? =null
    private var improvementType: String? = null
    var populationImage: Image? = null
    private var roadImages = HashMap<String, Image>()
    private var borderImages = ArrayList<Image>()


    init {
        val groupSize = 50f
        this.setSize(groupSize,groupSize)
        hexagon = ImageGetter.getImage("TerrainIcons/Hexagon.png")
        val imageScale = groupSize * 1.5f / hexagon.width
        hexagon.setScale(imageScale)
        hexagon.setOrigin(Align.center)
        hexagon.setPosition((width - hexagon.width) / 2,
                (height - hexagon.height) / 2)
        this.addActor(hexagon)
        hexagon.zIndex = 0
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
        if (!tileInfo.explored) {
            hexagon.color = Color.BLACK
            return
        }

        if(terrainFeatureImage==null && tileInfo.terrainFeature!=null){
            terrainFeatureImage = ImageGetter.getImage("TerrainIcons/${tileInfo.terrainFeature}.png")
            addActor(terrainFeatureImage)
            terrainFeatureImage!!.run {
                setSize(30f,30f)
                setColor(1f,1f,1f,0.5f)
                setPosition(this@TileGroup.width /2-width/2,
                        this@TileGroup.height/2-height/2)
            }
        }

        if(terrainFeatureImage!=null && tileInfo.terrainFeature==null){
            terrainFeatureImage!!.remove()
            terrainFeatureImage=null
        }


        val RGB= tileInfo.getBaseTerrain().RGB!!
        hexagon.color = Color(RGB[0]/255f,RGB[1]/255f,RGB[2]/255f,1f)
        if(!isViewable) hexagon.color = hexagon.color.lerp(Color.BLACK,0.6f)


        if (tileInfo.hasViewableResource(tileInfo.tileMap!!.gameInfo!!.getPlayerCivilization()) && resourceImage == null) { // Need to add the resource image!
            val fileName = "ResourceIcons/" + tileInfo.resource + "_(Civ5).png"
            resourceImage = ImageGetter.getImage(fileName)
            resourceImage!!.setSize(20f, 20f)
            resourceImage!!.setPosition(width/2 - resourceImage!!.width/2-20f,
                    height/2 - resourceImage!!.height/2) // left
            addActor(resourceImage!!)
        }



        if (tileInfo.improvement != null && tileInfo.improvement != improvementType) {
            improvementImage = ImageGetter.getImage("ImprovementIcons/" + tileInfo.improvement!!.replace(' ', '_') + "_(Civ5).png")
            addActor(improvementImage)
            improvementImage!!.run {
                setSize(20f, 20f)

                setPosition(this@TileGroup.width/2 - width/2+20f,
                        this@TileGroup.height/2 - height/2) // right
            }
            improvementType = tileInfo.improvement
        }

        if (populationImage != null) {
            if (tileInfo.workingCity != null)
                populationImage!!.color = Color.WHITE
            else
                populationImage!!.color = Color.GRAY
        }

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

        // Borders
        if(tileInfo.owner!=null){
            for (border in borderImages) border.remove()
            for (neighbor in tileInfo.neighbors.filter { it.owner!=tileInfo.owner }){
                val image = ImageGetter.getImage(ImageGetter.WhiteDot)

                val relativeHexPosition = tileInfo.position.cpy().sub(neighbor.position)
                val relativeWorldPosition = HexMath.Hex2WorldCoords(relativeHexPosition)

                // This is some crazy voodoo magic so I'll explain.

                image.setSize(20f, 2f)
                image.moveBy(this.width/2-image.width/2,
                        this.height/2-image.height/2)
                // in addTiles, we set the position of groups by relative world position *0.8*groupSize, filter groupSize = 50
                // Here, we want to have the roads start HALFWAY THERE and extend towards the tiles, so we give them a position of 0.8*25.
                image.moveBy(-relativeWorldPosition.x * 0.8f * 25f, -relativeWorldPosition.y * 0.8f * 25f)

                image.color = tileInfo.getOwner()!!.getCivilization().getColor()
                image.setOrigin(image.width/2, image.height/2) // This is so that the rotation is calculated from the middle of the road and not the edge
                image.rotation = (90 + 180 / Math.PI * Math.atan2(relativeWorldPosition.y.toDouble(), relativeWorldPosition.x.toDouble())).toFloat()
                addActor(image)
                borderImages.add(image)
            }

        }

    }

}

