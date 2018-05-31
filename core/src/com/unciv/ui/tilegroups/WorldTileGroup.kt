package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UnCivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.UnitType
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.utils.CameraStageBaseScreen
import com.unciv.ui.utils.ImageGetter
import com.unciv.ui.utils.center
import com.unciv.ui.utils.setFontColor


class WorldTileGroup(tileInfo: TileInfo) : TileGroup(tileInfo) {
    var cityButton: Table? = null

    fun addWhiteHaloAroundUnit(unit: MapUnit) {
        val whiteHalo = if(unit.isFortified())  ImageGetter.getImage("OtherIcons/Shield.png")
        else ImageGetter.getImage("OtherIcons/Circle.png")
        whiteHalo.setSize(30f,30f)
        val unitImage = if(unit.getBaseUnit().unitType==UnitType.Civilian) civilianUnitImage!!
                        else militaryUnitImage!!
        whiteHalo.center(unitImage)
        unitImage.addActor(whiteHalo)
        whiteHalo.toBack()
    }


    override fun update(isViewable: Boolean) {

        val city = tileInfo.getCity()

        removePopulationIcon()
        if (isViewable && tileInfo.isWorked() && UnCivGame.Current.settings.showWorkedTiles
                && city!!.civInfo.isPlayerCivilization())
            addPopulationIcon()

        if (tileInfo.tileMap.gameInfo.getPlayerCivilization().exploredTiles.contains(tileInfo.position)
                || viewEntireMapForDebug) updateCityButton(city) // needs to be before the update so the units will be above the city button

        super.update(isViewable)
    }

    private fun updateCityButton(city: CityInfo?) {
        if(city==null && cityButton!=null)// there used to be a city here but it was razed
        {
            cityButton!!.remove()
            cityButton=null
        }
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
            label.setFontColor(city.civInfo.getCivilization().getColor())
            if (city.civInfo.isPlayerCivilization())
                label.addClickListener {
                    UnCivGame.Current.screen = CityScreen(city)
                }

            cityButton!!.run {
                clear()
                if(city.isCapital()){
                    val starImage = Image(ImageGetter.getDrawable("OtherIcons/Star.png").tint(Color.LIGHT_GRAY))
                    add(starImage).size(20f).padLeft(10f)
                }
                add(label).pad(10f)
                pack()
                setOrigin(Align.center)
                toFront()
            }

            cityButton!!.center(this)

        }
    }


}
