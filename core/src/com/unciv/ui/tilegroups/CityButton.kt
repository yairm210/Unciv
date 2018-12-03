package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UnCivGame
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.SpecialConstruction
import com.unciv.logic.map.RoadStatus
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.utils.*

class CityButton(val city: CityInfo, skin: Skin): Table(skin){
    init{
        background = ImageGetter.getDrawable("OtherIcons/civTableBackground.png")
                .tint(city.civInfo.getNation().getColor())
        isTransform = true // If this is not set then the city button won't scale!
        touchable= Touchable.enabled // So you can click anywhere on the button to go to the city
    }

    fun update(isCityViewable:Boolean) {
        val cityButtonText = city.population.population.toString() + " | " + city.name
        background = ImageGetter.getDrawable("OtherIcons/civTableBackground.png")
                .tint(city.civInfo.getNation().getColor())
        val label = Label(cityButtonText, CameraStageBaseScreen.skin)
        label.setFontColor(city.civInfo.getNation().getSecondaryColor())

        clear()
        if (city.civInfo.isPlayerCivilization()) {
            onClick {
                UnCivGame.Current.screen = CityScreen(city)
            }
        }

        if (isCityViewable && city.health < city.getMaxHealth().toFloat()) {
            val healthBar = ImageGetter.getHealthBar(city.health.toFloat(), city.getMaxHealth().toFloat(), 100f)
            add(healthBar).colspan(3).row()
        }

        if (city.isBeingRazed) {
            val fireImage = ImageGetter.getImage("OtherIcons/Fire.png")
            add(fireImage).size(20f).pad(2f).padLeft(5f)
        }
        if (city.isCapital()) {
            val starImage = ImageGetter.getImage("OtherIcons/Star.png").apply { color = Color.LIGHT_GRAY }
            add(starImage).size(20f).pad(2f).padLeft(5f)
        } else if (city.civInfo.isPlayerCivilization() && city.cityStats.isConnectedToCapital(RoadStatus.Road)) {
            val connectionImage = ImageGetter.getStatIcon("CityConnection")
            add(connectionImage).size(20f).pad(2f).padLeft(5f)
        } else {
            add()
        } // this is so the health bar is always 2 columns wide
        add(label).pad(10f)
        if (city.civInfo.isPlayerCivilization()) {
            add(getConstructionGroup(city.cityConstructions)).padRight(5f)
        }
        pack()
        setOrigin(Align.center)
        toFront()
        touchable = Touchable.enabled

    }


    private fun getConstructionGroup(cityConstructions: CityConstructions): Group {
        val group= Group()
        val groupHeight = 25f
        group.setSize(40f,groupHeight)

        val circle = ImageGetter.getImage("OtherIcons/Circle")
        circle.setSize(25f,25f)
        val image = ImageGetter.getConstructionImage(cityConstructions.currentConstruction)
        image.setSize(18f,18f)
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
            val label = Label(turnsToConstruction.toString(), CameraStageBaseScreen.skin)
            label.setFontColor(secondaryColor)
            label.setFontSize(10)
            label.pack()
            group.addActor(label)

            val adoptedPolicies = cityConstructions.cityInfo.civInfo.policies.adoptedPolicies
            val constructionPercentage = cityConstructions.getWorkDone(cityConstructions.currentConstruction) /
                    cityConstructions.getCurrentConstruction().getProductionCost(adoptedPolicies).toFloat()
            val productionBar = ImageGetter.getProgressBarVertical(2f, groupHeight, constructionPercentage
                    , Color.BROWN.cpy().lerp(Color.WHITE, 0.5f), Color.BLACK)
            productionBar.x = 10f
            label.x = productionBar.x - label.width - 3
            group.addActor(productionBar)
        }
        return group
    }

}