package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Skin
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
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
        if (tileGroup.tileInfo.airUnits.isEmpty()) return
        val secondarycolor = city.civInfo.nation.getInnerColor()
        val airUnitTable = Table().apply { defaults().pad(5f) }
        airUnitTable.background = ImageGetter.getRoundedEdgeTableBackground(city.civInfo.nation.getOuterColor())
        val aircraftImage = ImageGetter.getImage("OtherIcons/Aircraft")
        aircraftImage.color = secondarycolor
        airUnitTable.add(aircraftImage).size(15f)
        airUnitTable.add(tileGroup.tileInfo.airUnits.size.toString().toLabel(secondarycolor,14))
        add(airUnitTable).row()
    }

    private fun belongsToViewingCiv() = city.civInfo == UncivGame.Current.worldScreen.viewingCiv

    private fun setButtonActions() {

        val unitTable = tileGroup.worldScreen.bottomUnitTable
        if (UncivGame.Current.viewEntireMapForDebug || belongsToViewingCiv()) {

            // So you can click anywhere on the button to go to the city
            touchable = Touchable.childrenOnly

            // clicking swings the button a little down to allow selection of units there.
            // this also allows to target selected units to move to the city tile from elsewhere.
            // second tap on the button will go to the city screen
            onClick {
                if (isButtonMoved) {
                    UncivGame.Current.setScreen(CityScreen(city))
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
                && unitTable.selectedUnit?.currentTile != city.getCenterTile()) {

            moveButtonUp()
        }
    }

    private fun getIconTable(): Table {
        val secondaryColor = city.civInfo.nation.getInnerColor()
        val iconTable = Table()
        iconTable.touchable=Touchable.enabled
        iconTable.background = ImageGetter.getRoundedEdgeTableBackground(city.civInfo.nation.getOuterColor())

        if (city.isInResistance()) {
            val resistanceImage = ImageGetter.getImage("StatIcons/Resistance")
            iconTable.add(resistanceImage).size(20f).pad(2f).padLeft(5f)
        }

        if (city.isPuppet) {
            val puppetImage = ImageGetter.getImage("OtherIcons/Puppet")
            puppetImage.color = secondaryColor
            iconTable.add(puppetImage).size(20f).pad(2f).padLeft(5f)
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
        } else if (belongsToViewingCiv() && city.isConnectedToCapital()) {
            val connectionImage = ImageGetter.getStatIcon("CityConnection")
            connectionImage.color = secondaryColor
            iconTable.add(connectionImage).size(20f).pad(2f).padLeft(5f)
        }

        iconTable.add(getPopulationGroup(UncivGame.Current.viewEntireMapForDebug || belongsToViewingCiv()))
                .padLeft(10f)

        val cityButtonText = city.name
        val label = cityButtonText.toLabel(secondaryColor)
        iconTable.add(label).pad(10f) // sufficient horizontal padding
                .fillY() // provide full-height clicking area

        if (UncivGame.Current.viewEntireMapForDebug || belongsToViewingCiv())
            iconTable.add(getConstructionGroup(city.cityConstructions)).padRight(10f).padLeft(10f)
        else if (city.civInfo.isMajorCiv()) {
            val nationIcon = ImageGetter.getNationIcon(city.civInfo.nation.name)
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

    private fun getPopulationGroup(showGrowth: Boolean): Group {
        val growthGreen = Color(0.0f, 0.5f, 0.0f, 1.0f)

        val group = Group()

        val populationLabel = city.population.population.toString().toLabel()
        populationLabel.color = city.civInfo.nation.getInnerColor()

        group.addActor(populationLabel)

        val groupHeight = 25f
        var groupWidth = populationLabel.width
        if (showGrowth) groupWidth += 20f
        group.setSize(groupWidth, groupHeight)

        if (showGrowth) {
            var growthPercentage = city.population.foodStored / city.population.getFoodToNextPopulation().toFloat()
            if (growthPercentage < 0) growthPercentage = 0.0f

            // This can happen if the city was just taken, and there was excess food stored.
            // Without it, it caused the growth bar's height to exceed that of the group's.
            if (growthPercentage > 1) growthPercentage = 1.0f

            val growthBar = ImageGetter.getProgressBarVertical(2f, groupHeight,
                    if (city.isStarving()) 1.0f else growthPercentage,
                    if (city.isStarving()) Color.RED else growthGreen, Color.BLACK)
            growthBar.x = populationLabel.width + 3
            growthBar.centerY(group)

            group.addActor(growthBar)

            val turnLabel : Label
            if (city.isGrowing()) {
                val turnsToGrowth = city.getNumTurnsToNewPopulation()
                if (turnsToGrowth != null) {
                    if (turnsToGrowth < 100) {
                        turnLabel = turnsToGrowth.toString().toLabel()
                    } else {
                        turnLabel = "∞".toLabel()
                    }
                } else {
                    turnLabel = "∞".toLabel()
                }
            } else if (city.isStarving()) {
                val turnsToStarvation = city.getNumTurnsToStarvation()
                if (turnsToStarvation != null) {
                    if (turnsToStarvation < 100) {
                        turnLabel = turnsToStarvation.toString().toLabel()
                    } else {
                        turnLabel = "∞".toLabel()
                    }
                } else {
                    turnLabel = "∞".toLabel()
                }
            } else {
                turnLabel = "∞".toLabel()
            }
            turnLabel.color = city.civInfo.nation.getInnerColor()
            turnLabel.setFontSize(14)
            turnLabel.pack()

            group.addActor(turnLabel)
            turnLabel.x = growthBar.x + growthBar.width + 1
        }

        populationLabel.centerY(group)

        return group
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

        val secondaryColor = cityConstructions.cityInfo.civInfo.nation.getInnerColor()
        val cityCurrentConstruction = cityConstructions.getCurrentConstruction()
        if(cityCurrentConstruction !is SpecialConstruction) {
            val turnsToConstruction = cityConstructions.turnsToConstruction(cityCurrentConstruction.name)
            val label = turnsToConstruction.toString().toLabel(secondaryColor,14)
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