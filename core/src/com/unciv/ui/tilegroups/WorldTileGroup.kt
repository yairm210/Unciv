package com.unciv.ui.tilegroups

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UnCivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.TileInfo
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter


class WorldTileGroup(tileInfo: TileInfo) : TileGroup(tileInfo) {
    var cityButton: Table? = null

    fun addWhiteCircleAroundUnit(){
        val whiteCircle = ImageGetter.getImage("UnitIcons/Circle.png")
        whiteCircle.setSize(25f,25f)
        whiteCircle.setPosition(unitImage!!.width/2 - whiteCircle.width/2,
                unitImage!!.height/2 - whiteCircle.height/2)
        unitImage!!.addActor(whiteCircle)
        whiteCircle.toBack()
    }


    override fun update(isViewable: Boolean) {
        super.update(isViewable)
        if (!tileInfo.tileMap.gameInfo.getPlayerCivilization().exploredTiles.contains(tileInfo.position)) return

        if (populationImage != null) removePopulationIcon()

        val city = tileInfo.getCity()
        if (tileInfo.isWorked() && city!!.civInfo.isPlayerCivilization() && populationImage == null)
            addPopulationIcon()

        updateCityButton(city)
        updateUnitImage(isViewable)
        if(unitImage!=null) {
            unitImage!!.setPosition(width / 2 - unitImage!!.width / 2,
                    height / 2 - unitImage!!.height / 2 + 20) // top
        }
    }

    private fun updateCityButton(city: CityInfo?) {
        if (city != null && tileInfo.isCityCenter()) {
            if (cityButton == null) {
                cityButton = Table()
                cityButton!!.background = ImageGetter.getDrawable("skin/civTableBackground.png")
                cityButton!!.isTransform = true

                addActor(cityButton)
                zIndex = parent.children.size // so this tile is rendered over neighboring tiles
            }

            val cityButtonText = city.name + " (" + city.population.population + ")"
            val label = Label(cityButtonText, CameraStageBaseScreen.skin)
            val labelStyle = Label.LabelStyle(label.style)
            labelStyle.fontColor = city.civInfo.getCivilization().getColor()
            label.style = labelStyle
            if (city.civInfo.isPlayerCivilization())
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

            cityButton!!.setPosition(width / 2 - cityButton!!.width / 2,
                    height / 2 - cityButton!!.height / 2)

        }
    }


}
