package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UnCivGame
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.SpecialConstruction
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.RoadStatus
import com.unciv.logic.map.TileInfo
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.utils.*


class WorldTileGroup(tileInfo: TileInfo) : TileGroup(tileInfo) {
    var cityButton: Table? = null

    fun addWhiteHaloAroundUnit(unit: MapUnit) {
        val whiteHalo = if(unit.isFortified())  ImageGetter.getImage("OtherIcons/Shield.png")
        else ImageGetter.getImage("OtherIcons/Circle.png")
        whiteHalo.setSize(30f,30f)
        val unitImage = if(unit.baseUnit().unitType== UnitType.Civilian) civilianUnitImage!!
                        else militaryUnitImage!!
        whiteHalo.center(unitImage)
        unitImage.addActor(whiteHalo)
        whiteHalo.toBack()
    }

    init{
        yieldGroup.center(this)
        yieldGroup.moveBy(-22f,0f)
    }


    override fun update(isViewable: Boolean) {
        val city = tileInfo.getCity()

        removePopulationIcon()
        if (isViewable && tileInfo.isWorked() && UnCivGame.Current.settings.showWorkedTiles
                && city!!.civInfo.isPlayerCivilization())
            addPopulationIcon()

        if (tileInfo.tileMap.gameInfo.getPlayerCivilization().exploredTiles.contains(tileInfo.position)
                || UnCivGame.Current.viewEntireMapForDebug)
            updateCityButton(city, isViewable || UnCivGame.Current.viewEntireMapForDebug) // needs to be before the update so the units will be above the city button

        super.update(isViewable || UnCivGame.Current.viewEntireMapForDebug)

        yieldGroup.isVisible = !UnCivGame.Current.settings.showResourcesAndImprovements
        if(yieldGroup.isVisible)
            yieldGroup.setStats(tileInfo.getTileStats(UnCivGame.Current.gameInfo.getPlayerCivilization()))

        // order by z index!
        cityImage?.toFront()
        terrainFeatureImage?.toFront()
        yieldGroup.toFront()
        improvementImage?.toFront()
        resourceImage?.toFront()
        cityButton?.toFront()
        civilianUnitImage?.toFront()
        militaryUnitImage?.toFront()
        fogImage.toFront()
    }

    private fun updateCityButton(city: CityInfo?, viewable: Boolean) {
        if(city==null && cityButton!=null)// there used to be a city here but it was razed
        {
            cityButton!!.remove()
            cityButton=null
        }
        if (city != null && tileInfo.isCityCenter()) {
            if (cityButton == null) {
                cityButton = Table()
                cityButton!!.background = ImageGetter.getDrawable("OtherIcons/civTableBackground.png")
                        .tint(city.civInfo.getNation().getColor())
                cityButton!!.isTransform = true // If this is not set then the city button won't scale!

                addActor(cityButton)
                toFront() // so this tile is rendered over neighboring tiles
            }

            val cityButtonText = city.population.population.toString() +" | " +city.name
            val label = Label(cityButtonText, CameraStageBaseScreen.skin)
            label.setFontColor(city.civInfo.getNation().getSecondaryColor())
            if (city.civInfo.isPlayerCivilization())
                label.onClick {
                    UnCivGame.Current.screen = CityScreen(city)
                }

            cityButton!!.run {
                clear()
                if(viewable && city.health<city.getMaxHealth().toFloat()) {
                    val healthBar = getHealthBar(city.health.toFloat(),city.getMaxHealth().toFloat(),100f)
                    add(healthBar).colspan(3).row()
                }

                if(city.isBeingRazed){
                    val fireImage = ImageGetter.getImage("OtherIcons/Fire.png")
                    add(fireImage).size(20f).pad(2f).padLeft(5f)
                }
                if(city.isCapital()){
                    val starImage = ImageGetter.getImage("OtherIcons/Star.png").apply { color = Color.LIGHT_GRAY}
                    add(starImage).size(20f).pad(2f).padLeft(5f)
                }
                else if (city.civInfo.isPlayerCivilization() && city.cityStats.isConnectedToCapital(RoadStatus.Road)){
                    val connectionImage = ImageGetter.getStatIcon("CityConnection")
                    add(connectionImage).size(20f).pad(2f).padLeft(5f)
                }

                else{add()} // this is so the health bar is always 2 columns wide
                add(label).pad(10f)
                if(city.civInfo.isPlayerCivilization()) {
                    add(getConstructionGroup(city.cityConstructions)).padRight(5f)
                }
                pack()
                setOrigin(Align.center)
                toFront()
                touchable = Touchable.enabled
            }

            cityButton!!.center(this)

        }
    }

    private fun getConstructionGroup(cityConstructions: CityConstructions):Group{
        val group= Group()
        val groupHeight = 25f
        group.setSize(40f,groupHeight)

        val circle = ImageGetter.getImage("OtherIcons/Circle")
        circle.setSize(25f,25f)
        val image = ImageGetter.getConstructionImage(cityConstructions.currentConstruction)
        image.setSize(20f,20f)
        image.centerY(group)
        image.x = group.width-image.width

        // center the circle on thee production image
        circle.x = image.x + (image.width-circle.width)/2
        circle.y = image.y + (image.height-circle.height)/2

        group.addActor(circle)
        group.addActor(image)

        val secondaryColor = cityConstructions.cityInfo.civInfo.getNation().getSecondaryColor()
        if(cityConstructions.getCurrentConstruction() !is SpecialConstruction) {
            val turnsToConstruction = cityConstructions.turnsToConstruction(cityConstructions.currentConstruction)
            val label = Label(turnsToConstruction.toString(),CameraStageBaseScreen.skin)
            label.setFontColor(secondaryColor)
            label.setFont(10)
            label.pack()
            group.addActor(label)

            val adoptedPolicies = cityConstructions.cityInfo.civInfo.policies.adoptedPolicies
            val constructionPercentage = cityConstructions.getWorkDone(cityConstructions.currentConstruction) /
                    cityConstructions.getCurrentConstruction().getProductionCost(adoptedPolicies).toFloat()
            val productionBar = Table()
            val heightOfProductionBar = (constructionPercentage * groupHeight)
            productionBar.add(ImageGetter.getImage(ImageGetter.WhiteDot).apply { color = Color.BLACK}).width(2f).height(groupHeight - heightOfProductionBar).row()
            productionBar.add(ImageGetter.getImage(ImageGetter.WhiteDot).apply { color = Color.BROWN.cpy().lerp(Color.WHITE,0.5f)}).width(2f).height(heightOfProductionBar)
            productionBar.pack()
            productionBar.x = 10f
            label.x = productionBar.x - label.width - 3
            group.addActor(productionBar)
        }
        return group
    }

}
