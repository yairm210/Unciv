package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UnCivGame
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.SpecialConstruction
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.utils.*

class CityButton(val city: CityInfo, internal val tileGroup: WorldTileGroup, skin: Skin): Table(skin){
    init{
        isTransform = true // If this is not set then the city button won't scale!
        touchable= Touchable.disabled
    }

    var isButtonMoved=false

    fun update(isCityViewable:Boolean) {
        clear()

        setButtonActions()

        addAirUnitTable()

        if (isCityViewable && city.health < city.getMaxHealth().toFloat()) {
            val healthBar = ImageGetter.getHealthBar(city.health.toFloat(), city.getMaxHealth().toFloat(), 100f)
            add(healthBar).row()
        }

        val iconTable = getIconTable()

        add(iconTable).row()
        pack()
        setOrigin(Align.center)
        centerX(tileGroup)
    }

    private fun addAirUnitTable() {
        if (!tileGroup.tileInfo.airUnits.isNotEmpty()) return
        val secondarycolor = city.civInfo.getNation().getSecondaryColor()
        val airUnitTable = Table().apply { defaults().pad(5f) }
        airUnitTable.background = ImageGetter.getDrawable("OtherIcons/civTableBackground")
                .tint(city.civInfo.getNation().getColor())
        val aircraftImage = ImageGetter.getImage("OtherIcons/Aircraft")
        aircraftImage.color = secondarycolor
        airUnitTable.add(aircraftImage).size(15f)
        airUnitTable.add(tileGroup.tileInfo.airUnits.size.toString().toLabel()
                .setFontColor(secondarycolor).setFontSize(15))
        add(airUnitTable).row()
    }

    private fun setButtonActions() {

        val unitTable = tileGroup.worldScreen.bottomBar.unitTable
        if (UnCivGame.Current.viewEntireMapForDebug || city.civInfo.isCurrentPlayer()) {

            // So you can click anywhere on the button to go to the city
            touchable = Touchable.childrenOnly

            // clicking swings the button a little down to allow selection of units there.
            // this also allows to target selected units to move to the city tile from elsewhere.
            // second tap on the button will go to the city screen
            onClick {
                if (isButtonMoved) {
                    UnCivGame.Current.screen = CityScreen(city)
                } else {
                    moveButtonDown()
                    if (unitTable.selectedUnit == null || unitTable.selectedUnit!!.currentMovement == 0f)
                        tileGroup.selectCity(city)
                }
            }
        }

        // when deselected, move city button to its original position
        if (isButtonMoved
                && unitTable.selectedCity != city
                && unitTable.selectedUnit?.currentTile != city.ccenterTile) {

            moveButtonUp()
        }
    }

    private fun getIconTable(): Table {
        val secondaryColor = city.civInfo.getNation().getSecondaryColor()
        val iconTable = Table()
        iconTable.touchable=Touchable.enabled
        iconTable.background = ImageGetter.getDrawable("OtherIcons/civTableBackground")
                .tint(city.civInfo.getNation().getColor())

        if (city.resistanceCounter > 0) {
            val resistanceImage = ImageGetter.getImage("StatIcons/Resistance")
            iconTable.add(resistanceImage).size(20f).pad(2f).padLeft(5f)
        }

        if (city.isBeingRazed) {
            val fireImage = ImageGetter.getImage("OtherIcons/Fire")
            iconTable.add(fireImage).size(20f).pad(2f).padLeft(5f)
        }
        if (city.isCapital()) {
            if (city.civInfo.isCityState()) {
                val cityStateImage = ImageGetter.getNationIcon("CityState")
                        .apply { color = secondaryColor }
                iconTable.add(cityStateImage).size(20f).pad(2f).padLeft(10f)
            } else {
                val starImage = ImageGetter.getImage("OtherIcons/Star").apply { color = Color.LIGHT_GRAY }
                iconTable.add(starImage).size(20f).pad(2f).padLeft(10f)
            }
        } else if (city.civInfo.isCurrentPlayer() && city.isConnectedToCapital()) {
            val connectionImage = ImageGetter.getStatIcon("CityConnection")
            connectionImage.color = secondaryColor
            iconTable.add(connectionImage).size(20f).pad(2f).padLeft(10f)
        }

        val cityButtonText = city.population.population.toString() + " | " + city.name
        val label = cityButtonText.toLabel()
        label.setFontColor(secondaryColor)
        iconTable.add(label).pad(10f) // sufficient horizontal padding
                .fillY() // provide full-height clicking area

        if (UnCivGame.Current.viewEntireMapForDebug || city.civInfo.isCurrentPlayer())
            iconTable.add(getConstructionGroup(city.cityConstructions)).padRight(10f)
        else if (city.civInfo.isMajorCiv()) {
            val nationIcon = ImageGetter.getNationIcon(city.civInfo.getNation().name)
            nationIcon.color = secondaryColor
            iconTable.add(nationIcon).size(20f).padRight(10f)
        }
        return iconTable
    }

    private fun moveButtonDown() {
        val moveButtonAction = Actions.sequence(
                Actions.moveTo(tileGroup.x, tileGroup.y-height, 0.4f, Interpolation.swingOut),
                Actions.run { isButtonMoved=true }
        )
        parent.addAction(moveButtonAction) // Move the whole cityButtonLayerGroup down, so the citybutton remains clickable
    }

    private fun moveButtonUp() {
        val floatAction = Actions.sequence(
                Actions.moveTo(tileGroup.x, tileGroup.y, 0.4f, Interpolation.sine),
                Actions.run {isButtonMoved=false}
        )
        parent.addAction(floatAction)
    }

    private fun getConstructionGroup(cityConstructions: CityConstructions): Group {
        val group= Group()
        val groupHeight = 25f
        group.setSize(40f,groupHeight)

        val circle = ImageGetter.getCircle()
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
        val cityCurrentConstruction = cityConstructions.getCurrentConstruction()
        if(cityCurrentConstruction !is SpecialConstruction) {
            val turnsToConstruction = cityConstructions.turnsToConstruction(cityCurrentConstruction.name)
            val label = turnsToConstruction.toString().toLabel()
            label.setFontColor(secondaryColor)
            label.setFontSize(10)
            label.pack()
            group.addActor(label)

            val constructionPercentage = cityConstructions.getWorkDone(cityCurrentConstruction.name) /
                    cityCurrentConstruction.getProductionCost(cityConstructions.cityInfo.civInfo).toFloat()
            val productionBar = ImageGetter.getProgressBarVertical(2f, groupHeight, constructionPercentage,
                    Color.BROWN.cpy().lerp(Color.WHITE, 0.5f), Color.BLACK)
            productionBar.x = 10f
            label.x = productionBar.x - label.width - 3
            group.addActor(productionBar)
        }
        return group
    }

}