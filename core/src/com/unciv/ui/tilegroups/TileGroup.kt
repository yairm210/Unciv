package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Container
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.linq.LinqHashMap
import com.unciv.ui.utils.HexMath
import com.unciv.ui.utils.ImageGetter

open class TileGroup(var tileInfo: TileInfo) : Group() {
    protected var terrainImage: Image
    private var terrainType: String
    protected var resourceImage: Image? = null
    protected var unitImage: Image? = null
    protected var improvementImage: Image? =null
    private var improvementType: String? = null
    var populationImage: Image? = null
    private var roadImages = LinqHashMap<String, Image>()
    protected var hexagon: Image? = null

    protected var cityButton: Container<TextButton>? = null

    init {

        terrainType = tileInfo.lastTerrain.name
        val terrainFileName = "TerrainIcons/" + terrainType.replace(' ', '_') + "_(Civ5).png"
        terrainImage = ImageGetter.getImage(terrainFileName)
        val groupSize = 50
        terrainImage.setSize(groupSize.toFloat(), groupSize.toFloat())
        this.setSize(groupSize.toFloat(), groupSize.toFloat())
        this.addActor(terrainImage)
    }

    fun addPopulationIcon() {
        populationImage = ImageGetter.getImage("StatIcons/populationGreen.png")
        populationImage!!.run {
            setSize(20f, 20f)
            moveBy(0f, terrainImage.height - populationImage!!.height)
        } // top left
        addActor(populationImage!!)
    }

    protected fun removePopulationIcon() {
        populationImage!!.remove()
        populationImage = null
    }


    open fun update() {
        if (!tileInfo.explored) {
            terrainImage.color = Color.BLACK
            return
        }

        terrainImage.color = Color.WHITE

        if (terrainType != tileInfo.lastTerrain.name) {
            terrainType = tileInfo.lastTerrain.name
            val terrainFileName = "TerrainIcons/" + terrainType.replace(' ', '_') + "_(Civ5).png"
            terrainImage.drawable = ImageGetter.getDrawable(terrainFileName) // In case we e.g. removed a jungle
        }

        if (tileInfo.hasViewableResource(tileInfo.tileMap!!.gameInfo!!.getPlayerCivilization()) && resourceImage == null) { // Need to add the resource image!
            val fileName = "ResourceIcons/" + tileInfo.resource + "_(Civ5).png"
            resourceImage = ImageGetter.getImage(fileName)
            resourceImage!!.setSize(20f, 20f)
            resourceImage!!.moveBy(terrainImage.width - resourceImage!!.width, 0f) // bottom right
            addActor(resourceImage!!)
        }

        if (tileInfo.unit != null && unitImage == null) {
            unitImage = ImageGetter.getImage("UnitIcons/" + tileInfo.unit!!.name!!.replace(" ", "_") + "_(Civ5).png")
            addActor(unitImage!!)
            unitImage!!.setSize(20f, 20f) // not moved - is at bottom left
        }

        if (tileInfo.unit == null && unitImage != null) {
            unitImage!!.remove()
            unitImage = null
        }

        if (unitImage != null) {
            if (!tileInfo.hasIdleUnit())
                unitImage!!.color = Color.GRAY
            else
                unitImage!!.color = Color.WHITE
        }


        if (tileInfo.improvement != null && tileInfo.improvement != improvementType) {
            improvementImage = ImageGetter.getImage("ImprovementIcons/" + tileInfo.improvement!!.replace(' ', '_') + "_(Civ5).png")
            addActor(improvementImage)
            improvementImage!!.run {
                setSize(20f, 20f)
                moveBy(terrainImage.width - width,
                        terrainImage.height - height)
            } // top right
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
                    // in addTiles, we set the position of groups by relative world position *0.8*groupSize, where groupSize = 50
                    // Here, we want to have the roads start HALFWAY THERE and extend towards the tiles, so we give them a position of 0.8*25.
                    image.moveBy(-relativeWorldPosition.x * 0.8f * 25f, -relativeWorldPosition.y * 0.8f * 25f)
                    image.setSize(10f, 2f)
                    addActor(image)
                    image.setOrigin(0f, 1f) // This is so that the rotation is calculated from the middle of the road and not the edge
                    image.rotation = (180 / Math.PI * Math.atan2(relativeWorldPosition.y.toDouble(), relativeWorldPosition.x.toDouble())).toFloat()
                }

                if (tileInfo.roadStatus === RoadStatus.Railroad && neighbor.roadStatus === RoadStatus.Railroad)
                    roadImages[neighbor.position.toString()]!!.color = Color.GRAY // railroad
                else
                    roadImages[neighbor.position.toString()]!!.color = Color.BROWN // road
            }
        }

    }
}

