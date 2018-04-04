package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.unciv.logic.map.TileInfo
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.worldscreen.WorldScreen


class WorldTileGroup(tileInfo: TileInfo) : TileGroup(tileInfo) {
    private var cityButton: TextButton? = null
    private var unitImage: Group? = null

    fun setIsViewable(isViewable: Boolean) {
        if (isViewable) {
            setColor(0f, 0f, 0f, 1f) // Only alpha really changes anything
            tileInfo.explored = true
            update()
        } else
            setColor(0f, 0f, 0f, 0.6f)
    }

    fun update(worldScreen: WorldScreen) {
        super.update()

        if (tileInfo.workingCity != null && populationImage == null) addPopulationIcon()
        if (tileInfo.workingCity == null && populationImage != null) removePopulationIcon()


        val city = tileInfo.city
        if (tileInfo.isCityCenter) {
            val buttonScale = 0.7f
            if (cityButton == null) {
                cityButton =  TextButton("", CameraStageBaseScreen.skin)
                cityButton!!.label.setFontScale(buttonScale)

                val game = worldScreen.game
                cityButton!!.addClickListener { game.screen = CityScreen(city!!)}

                addActor(cityButton)
                zIndex = parent.children.size // so this tile is rendered over neighboring tiles
            }

            val cityButtonText = city!!.name + " (" + city.population.population + ")"
            cityButton!!.setText(cityButtonText)
            cityButton!!.setSize(cityButton!!.prefWidth, cityButton!!.prefHeight)

            cityButton!!.setPosition((width - cityButton!!.width) / 2,
                    height * 0.9f)
            cityButton!!.zIndex = cityButton!!.parent.children.size // so city button is rendered over everything else in this tile
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
