package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UnCivGame
import com.unciv.logic.map.TileInfo
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter


class WorldTileGroup(tileInfo: TileInfo) : TileGroup(tileInfo) {
    var cityButton: Table? = null
    private var unitImage: Group? = null
    private var circleImage = ImageGetter.getImage("UnitIcons/Circle.png") // for blue and red circles on the tile

    init{
        circleImage.width = 50f
        circleImage.height = 50f
        circleImage.setPosition(width/2-circleImage.width/2,
                height/2-circleImage.height/2)
        addActor(circleImage)
        circleImage.isVisible = false
    }

    fun addWhiteCircleAroundUnit(){
        val whiteCircle = ImageGetter.getImage("UnitIcons/Circle.png")
        whiteCircle.setSize(25f,25f)
        whiteCircle.setPosition(unitImage!!.width/2 - whiteCircle.width/2,
                unitImage!!.height/2 - whiteCircle.height/2)
        unitImage!!.addActor(whiteCircle)
        whiteCircle.toBack()
    }

    fun showCircle(color:Color){
        circleImage.isVisible = true
        color.a = 0.3f
        circleImage.color = color
    }

    fun hideCircle(){circleImage.isVisible=false}


    override fun update(isViewable: Boolean) {
        super.update(isViewable)
        if (!tileInfo.explored) return

        if (populationImage != null) removePopulationIcon()

        val city = tileInfo.city
        if (city != null && city.civInfo.isPlayerCivilization() && !tileInfo.isCityCenter  && populationImage == null)
            addPopulationIcon()

        if (city != null && tileInfo.isCityCenter) {
            if (cityButton == null) {
                cityButton = Table()
                cityButton!!.background = ImageGetter.getDrawable("skin/civTableBackground.png")
                cityButton!!.isTransform=true

                addActor(cityButton)
                zIndex = parent.children.size // so this tile is rendered over neighboring tiles
            }

            val cityButtonText = city.name + " (" + city.population.population + ")"
            val label = Label(cityButtonText, CameraStageBaseScreen.skin)
            val labelStyle = Label.LabelStyle(label.style)
            labelStyle.fontColor= city.civInfo.getCivilization().getColor()
            label.style=labelStyle
            if(city.civInfo.isPlayerCivilization())
                label.addClickListener {
                    UnCivGame.Current.screen = CityScreen(city)
                }

            cityButton!!.run {
                clear()
                add(label).pad(10f)
                pack()
                setOrigin(Align.center)
                toFront()
            }

            cityButton!!.setPosition(width/2 - cityButton!!.width / 2,
                    height/2 - cityButton!!.height/2)

        }


        if (unitImage != null) { // The unit can change within one update - for instance, when attacking, the attacker replaces the defender!
            unitImage!!.remove()
            unitImage = null
        }

        if (tileInfo.unit != null && isViewable) { // Tile is visible
            val unit = tileInfo.unit!!
            unitImage = getUnitImage(unit.name, unit.civInfo.getCivilization().getColor())
            addActor(unitImage!!)
            unitImage!!.setSize(20f, 20f)
            unitImage!!.setPosition(width/2 - unitImage!!.width/2,
                    height/2 - unitImage!!.height/2 +20) // top
        }


        if (unitImage != null) {
            if (!tileInfo.hasIdleUnit())
                unitImage!!.color = Color(1f,1f,1f,0.5f)
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
}
