package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.map.TileInfo
import com.unciv.ui.UnCivGame
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter


class WorldTileGroup(tileInfo: TileInfo) : TileGroup(tileInfo) {
    var cityButton: Table? = null
    private var unitImage: Group? = null

    private var circleImage = ImageGetter.getImage("UnitIcons/Circle.png")

    init{
        circleImage.width = 50f
        circleImage.height = 50f
        circleImage.setPosition(width/2-circleImage.width/2,
                height/2-circleImage.height/2)
        addActor(circleImage)
        circleImage.isVisible = false
    }

    fun showCircle(color:Color){
        circleImage.isVisible = true
        color.a = 0.3f
        circleImage.setColor(color)
    }

    fun hideCircle(){circleImage.isVisible=false}

    fun setIsViewable(isViewable: Boolean) {
        if (isViewable) {
            setColor(0f, 0f, 0f, 1f) // Only alpha really changes anything
            tileInfo.explored = true
            update()
        } else{
            setColor(0f, 0f, 0f, 0.6f)
            update()
        }
    }

    override fun update() {
        super.update()

        if (populationImage != null) removePopulationIcon()
        if (tileInfo.workingCity != null && !tileInfo.isCityCenter && populationImage == null) addPopulationIcon()


        val city = tileInfo.city
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

        if (tileInfo.unit != null && color.a==1f) { // Tile is visible
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
