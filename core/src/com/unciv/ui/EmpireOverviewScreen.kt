package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.UnCivGame
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.DiplomaticStatus
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeOffersList
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.*
import java.text.DecimalFormat
import kotlin.math.roundToInt

class EmpireOverviewScreen : CameraStageBaseScreen(){

    val playerCivInfo = UnCivGame.Current.gameInfo.getPlayerCivilization()
    init {
        onBackButtonClicked { UnCivGame.Current.setWorldScreen(); dispose() }
        val topTable = Table().apply { defaults().pad(10f) }
        val centerTable=Table().apply {  defaults().pad(20f) }

        val closeButton = TextButton("Close".tr(), skin)
        closeButton.onClick { UnCivGame.Current.setWorldScreen() }
        closeButton.y = stage.height - closeButton.height - 5
        topTable.add(closeButton)

        val setCityInfoButton = TextButton("Cities".tr(),skin)
        val setCities = {
            centerTable.clear()
            centerTable.add(getCityInfoTable())
            centerTable.pack()
            centerTable.center(stage)
        }
        setCities()
        setCityInfoButton.onClick(setCities)
        topTable.add(setCityInfoButton)

        val setStatsInfoButton = TextButton("Stats".tr(),skin)
        setStatsInfoButton.onClick {
            centerTable.clear()
            centerTable.add(getHappinessTable())
            centerTable.add(getGoldTable()).row()
            centerTable.add(getGreatPeopleTable())
            centerTable.pack()
            centerTable.center(stage)
        }
        topTable.add(setStatsInfoButton)

        val setCurrentTradesButton = TextButton("Trades".tr(),skin)
        setCurrentTradesButton.onClick {
            centerTable.clear()
            centerTable.add(ScrollPane(getTradesTable())).height(stage.height*0.8f) // so it doesn't cover the naviagation buttons
            centerTable.pack()
            centerTable.center(stage)
        }
        topTable.add(setCurrentTradesButton)

        val setUnitsButton = TextButton("Units".tr(),skin)
        setUnitsButton .onClick {
            centerTable.clear()
            centerTable.add(ScrollPane(getUnitTable())).height(stage.height*0.8f)
            centerTable.pack()
            centerTable.center(stage)
        }
        topTable.add(setUnitsButton )


        val setDiplomacyButton = TextButton("Diplomacy".tr(),skin)
        setDiplomacyButton.onClick {
            centerTable.clear()
            centerTable.add(createDiplomacyGroup()).height(stage.height*0.8f)
            centerTable.pack()
            centerTable.center(stage)
        }
        topTable.add(setDiplomacyButton )

        topTable.pack()
        topTable.width = stage.width
        topTable.y = stage.height-topTable.height

        stage.addActor(topTable)
        stage.addActor(centerTable)
    }

    private fun getTradesTable(): Table {
        val tradesTable = Table().apply { defaults().pad(10f) }
        for(diplomacy in playerCivInfo.diplomacy.values)
            for(trade in diplomacy.trades)
                tradesTable.add(createTradeTable(trade,diplomacy.otherCiv())).row()

        return tradesTable
    }

    private fun createTradeTable(trade: Trade, otherCiv:CivilizationInfo): Table {
        val generalTable = Table(skin)
        generalTable.add(createOffersTable(playerCivInfo,trade.ourOffers, trade.theirOffers.size))
        generalTable.add(createOffersTable(otherCiv, trade.theirOffers, trade.ourOffers.size))
        return generalTable
    }
    
    private fun createOffersTable(civ: CivilizationInfo, offersList: TradeOffersList, numberOfOtherSidesOffers: Int): Table {
        val table = Table()
        table.defaults().pad(10f)
        table.background = ImageGetter.getBackground(civ.getNation().getColor())
        table.add(Label(civ.civName.tr(),skin).setFontColor(civ.getNation().getSecondaryColor())).row()
        table.addSeparator()
        for(offer in offersList){
            var offerText = offer.amount.toString()+" "+offer.name.tr()
            if(offer.duration>0)offerText += " ("+offer.duration+" {turns})".tr()
            table.add(Label(offerText,skin).setFontColor(civ.getNation().getSecondaryColor())).row()
        }
        for(i in 1..numberOfOtherSidesOffers - offersList.size)
            table.add(Label("",skin)).row() // we want both sides of the general table to have the same number of rows
        return table
    }


    private fun getHappinessTable(): Table {
        val happinessTable = Table(skin)
        happinessTable.defaults().pad(5f)
        happinessTable.add(Label("Happiness".tr(), skin).setFontSize(24)).colspan(2).row()
        happinessTable.addSeparator()
        for (entry in playerCivInfo.getHappinessForNextTurn()) {
            happinessTable.add(entry.key.tr())
            happinessTable.add(entry.value.toString()).row()
        }
        happinessTable.add("Total".tr())
        happinessTable.add(playerCivInfo.getHappinessForNextTurn().values.sum().toString())
        happinessTable.pack()
        return happinessTable
    }

    private fun getGoldTable(): Table {
        val goldTable = Table(skin)
        goldTable.defaults().pad(5f)
        goldTable.add(Label("Gold".tr(), skin).setFontSize(24)).colspan(2).row()
        goldTable.addSeparator()
        var total=0f
        for (entry in playerCivInfo.getStatMapForNextTurn()) {
            if(entry.value.gold==0f) continue
            goldTable.add(entry.key.tr())
            goldTable.add(entry.value.gold.toString()).row()
            total += entry.value.gold
        }
        goldTable.add("Total".tr())
        goldTable.add(total.toString())
        goldTable.pack()
        return goldTable
    }


    private fun getGreatPeopleTable(): Table {
        val greatPeopleTable = Table(skin)

        val greatPersonPoints = playerCivInfo.greatPeople.greatPersonPoints.toHashMap()
        val greatPersonPointsPerTurn = playerCivInfo.getGreatPersonPointsForNextTurn().toHashMap()
        val pointsToGreatPerson = playerCivInfo.greatPeople.pointsForNextGreatPerson

        greatPeopleTable.defaults().pad(5f)
        greatPeopleTable.add(Label("Great person points".tr(), skin).setFontSize(24)).colspan(3).row()
        greatPeopleTable.addSeparator()
        greatPeopleTable.add()
        greatPeopleTable.add("Current points")
        greatPeopleTable.add("Points per turn").row()

        val mapping = playerCivInfo.greatPeople.statToGreatPersonMapping
        for(entry in mapping){
            greatPeopleTable.add(entry.value.tr())
            greatPeopleTable.add(greatPersonPoints[entry.key]!!.toInt().toString()+"/"+pointsToGreatPerson)
            greatPeopleTable.add(greatPersonPointsPerTurn[entry.key]!!.toInt().toString()).row()
        }
        val pointsForGreatGeneral = playerCivInfo.greatPeople.greatGeneralPoints.toInt().toString()
        val pointsForNextGreatGeneral = playerCivInfo.greatPeople.pointsForNextGreatGeneral.toInt().toString()
        greatPeopleTable.add("Great General".tr())
        greatPeopleTable.add(pointsForGreatGeneral+"/"+pointsForNextGreatGeneral).row()
        greatPeopleTable.pack()
        return greatPeopleTable
    }



    private fun getCityInfoTable(): Table {
        val iconSize = 20f//if you set this too low, there is a chance that the tables will be misaligned
        val padding = 5f

        val cityInfoTableIcons = Table(skin)
        cityInfoTableIcons.defaults().pad(padding).align(Align.center)

        cityInfoTableIcons.add(Label("Cities".tr(), skin).setFontSize(24)).colspan(8).align(Align.center).row()
        cityInfoTableIcons.add()
        cityInfoTableIcons.add(ImageGetter.getStatIcon("Population")).size(iconSize)
        cityInfoTableIcons.add(ImageGetter.getStatIcon("Food")).size(iconSize)
        cityInfoTableIcons.add(ImageGetter.getStatIcon("Gold")).size(iconSize)
        cityInfoTableIcons.add(ImageGetter.getStatIcon("Science")).size(iconSize)
        cityInfoTableIcons.add(ImageGetter.getStatIcon("Production")).size(iconSize)
        cityInfoTableIcons.add(ImageGetter.getStatIcon("Culture")).size(iconSize)
        cityInfoTableIcons.add(ImageGetter.getStatIcon("Happiness")).size(iconSize)
        cityInfoTableIcons.pack()

        val cityInfoTableDetails = Table(skin)
        cityInfoTableDetails.defaults().pad(padding).minWidth(iconSize).align(Align.left)//we need the min width so we can align the different tables

        for (city in playerCivInfo.cities) {
            cityInfoTableDetails.add(city.name)
            cityInfoTableDetails.add(city.cityConstructions.getCityProductionTextForCityButton()).actor!!.setAlignment(Align.left)
            cityInfoTableDetails.add(city.population.population.toString()).actor!!.setAlignment(Align.center)
            cityInfoTableDetails.add(city.cityStats.currentCityStats.food.roundToInt().toString()).actor!!.setAlignment(Align.center)
            cityInfoTableDetails.add(city.cityStats.currentCityStats.gold.roundToInt().toString()).actor!!.setAlignment(Align.center)
            cityInfoTableDetails.add(city.cityStats.currentCityStats.science.roundToInt().toString()).actor!!.setAlignment(Align.center)
            cityInfoTableDetails.add(city.cityStats.currentCityStats.production.roundToInt().toString()).actor!!.setAlignment(Align.center)
            cityInfoTableDetails.add(city.cityStats.currentCityStats.culture.roundToInt().toString()).actor!!.setAlignment(Align.center)
            cityInfoTableDetails.add(city.cityStats.currentCityStats.happiness.roundToInt().toString()).actor!!.setAlignment(Align.center)
            cityInfoTableDetails.row()
        }
        cityInfoTableDetails.pack()

        val cityInfoScrollPane = ScrollPane(cityInfoTableDetails)
        cityInfoScrollPane.pack()
        cityInfoScrollPane.setOverscroll(false, false)//I think it feels better with no overscroll

        val cityInfoTableTotal = Table(skin)
        cityInfoTableTotal.defaults().pad(padding).minWidth(iconSize)//we need the min width so we can align the different tables

        cityInfoTableTotal.add("Total".tr())
        cityInfoTableTotal.add(playerCivInfo.cities.sumBy { it.population.population }.toString()).actor!!.setAlignment(Align.center)
        cityInfoTableTotal.add()//an intended empty space
        cityInfoTableTotal.add(playerCivInfo.cities.sumBy { it.cityStats.currentCityStats.gold.toInt() }.toString()).actor!!.setAlignment(Align.center)
        cityInfoTableTotal.add(playerCivInfo.cities.sumBy { it.cityStats.currentCityStats.science.toInt() }.toString()).actor!!.setAlignment(Align.center)
        cityInfoTableTotal.add()//an intended empty space
        cityInfoTableTotal.add(playerCivInfo.cities.sumBy { it.cityStats.currentCityStats.culture.toInt() }.toString()).actor!!.setAlignment(Align.center)
        cityInfoTableTotal.add(playerCivInfo.cities.sumBy {  it.cityStats.currentCityStats.happiness.toInt() }.toString()).actor!!.setAlignment(Align.center)

        cityInfoTableTotal.pack()

        val table = Table(skin)
        //since the names of the cities are on the left, and the length of the names varies
        //we align every row to the right, coz we set the size of the other(number) cells to the image size
        //and thus, we can guarantee that the tables will be aligned
        table.defaults().pad(padding).align(Align.right)

        table.add(cityInfoTableIcons).row()
        val height = if(cityInfoTableDetails.rows > 0) cityInfoTableDetails.getRowHeight(0)*4f else 100f //if there are no cities, set the height of the scroll pane to 100
        table.add(cityInfoScrollPane).width(cityInfoTableDetails.width).height(height).row()
        table.add(cityInfoTableTotal)
        table.pack()

        return table
    }

    fun getUnitTable(): Table {
        val table=Table(skin).apply { defaults().pad(5f) }
        table.add("Name".tr())
        table.add("Strength".tr())
        table.add("Ranged strength".tr())
        table.add("Movement".tr())
        table.add("Closest city".tr())
        table.row()
        table.addSeparator()

        for(unit in playerCivInfo.getCivUnits()){
            val baseUnit = unit.baseUnit()
            table.add(unit.name.tr())
            if(baseUnit.strength>0) table.add(baseUnit.strength.toString()) else table.add()
            if(baseUnit.rangedStrength>0) table.add(baseUnit.rangedStrength.toString()) else table.add()
            table.add(DecimalFormat("0.#").format(unit.currentMovement)+"/"+unit.getMaxMovement())
            val closestCity = unit.getTile().getTilesInDistance(3).firstOrNull{it.isCityCenter()}
            if (closestCity!=null) table.add(closestCity.getCity()!!.name) else table.add()
            table.row()
        }
        table.pack()
        return table
    }


    fun playerKnows(civ:CivilizationInfo) = civ.isPlayerCivilization() ||
            playerCivInfo.diplomacy.containsKey(civ.civName)

    fun createDiplomacyGroup(): Group {
        val relevantCivs = playerCivInfo.gameInfo.civilizations.filter { !it.isBarbarianCivilization() }
        val groupSize = 500f
        val group = Group()
        group.setSize(groupSize,groupSize)
        val civGroups = HashMap<CivilizationInfo,Actor>()
        for(i in 0 until relevantCivs.size){
            val civ = relevantCivs[i]


            val civGroup = Table()
            val civGroupBackground = ImageGetter.getDrawable("OtherIcons/civTableBackground.png")

            val label = Label(civ.civName.tr(), CameraStageBaseScreen.skin)

            if (civ.isDefeated()) {
                civGroup.background = civGroupBackground.tint(Color.LIGHT_GRAY)
            } else if (playerKnows(civ)) {
                civGroup.background = civGroupBackground.tint(civ.getNation().getColor())
                label.setFontColor(civ.getNation().getSecondaryColor())
            } else {
                civGroup.background = civGroupBackground.tint(Color.DARK_GRAY)
                label.setText("???")
            }

            civGroup.add(label).pad(10f)
            civGroup.pack()

            val vector = HexMath().getVectorForAngle(2 * Math.PI.toFloat() *i / relevantCivs.size)
            civGroup.center(group)
            civGroup.moveBy(vector.x*groupSize/3, vector.y*groupSize/3)

            civGroups[civ]=civGroup
            group.addActor(civGroup)
        }


        for(civ in relevantCivs.filter { playerKnows(it) && !it.isDefeated() })
            for(diplomacy in civ.diplomacy.values.filter { !it.otherCiv().isBarbarianCivilization() && playerKnows(it.otherCiv()) && !it.otherCiv().isDefeated()}){
                val civGroup = civGroups[civ]!!
                val otherCivGroup = civGroups[diplomacy.otherCiv()]!!

                val statusLine = ImageGetter.getLine(civGroup.x+civGroup.width/2,civGroup.y+civGroup.height/2,
                        otherCivGroup.x+otherCivGroup.width/2,otherCivGroup.y+otherCivGroup.height/2,3f)

                statusLine.color = if(diplomacy.diplomaticStatus==DiplomaticStatus.War) Color.RED
                else Color.GREEN

                group.addActor(statusLine)
                statusLine.toBack()
            }


        return group
    }
}