package com.unciv.ui

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.*
import com.badlogic.gdx.utils.Align
import com.unciv.UnCivGame
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.civilization.diplomacy.DiplomaticStatus
import com.unciv.logic.trade.Trade
import com.unciv.logic.trade.TradeOffersList
import com.unciv.models.gamebasics.tile.ResourceType
import com.unciv.models.gamebasics.tr
import com.unciv.ui.utils.*
import java.text.DecimalFormat
import kotlin.math.roundToInt

class EmpireOverviewScreen : CameraStageBaseScreen(){

    val currentPlayerCivInfo = UnCivGame.Current.gameInfo.getCurrentPlayerCivilization()

    init {
        onBackButtonClicked { UnCivGame.Current.setWorldScreen() }
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
        }
        setCities()
        setCityInfoButton.onClick(setCities)
        topTable.add(setCityInfoButton)

        val setStatsInfoButton = TextButton("Stats".tr(),skin)
        setStatsInfoButton.onClick {
            centerTable.clear()
            centerTable.add(ScrollPane(HorizontalGroup().apply {
                space(40f)
                top()
                addActor(getHappinessTable())
                addActor(getGoldTable())
                addActor(getGreatPeopleTable())
            }))
            centerTable.pack()
        }
        topTable.add(setStatsInfoButton)

        val setCurrentTradesButton = TextButton("Trades".tr(),skin)
        setCurrentTradesButton.onClick {
            centerTable.clear()
            centerTable.add(ScrollPane(getTradesTable())).height(stage.height*0.8f) // so it doesn't cover the navigation buttons
            centerTable.pack()
        }
        topTable.add(setCurrentTradesButton)
        if(currentPlayerCivInfo.diplomacy.values.all { it.trades.isEmpty() })
            setCurrentTradesButton.disable()

        val setUnitsButton = TextButton("Units".tr(),skin)
        setUnitsButton.onClick {
            centerTable.clear()
            centerTable.add(ScrollPane(getUnitTable())).height(stage.height*0.8f)
            centerTable.pack()
        }
        topTable.add(setUnitsButton )


        val setDiplomacyButton = TextButton("Diplomacy".tr(),skin)
        setDiplomacyButton.onClick {
            centerTable.clear()
            centerTable.add(getDiplomacyGroup()).height(stage.height*0.8f)
            centerTable.pack()
        }
        topTable.add(setDiplomacyButton)

        val setResourcesButton = TextButton("Resources".tr(),skin)
        setResourcesButton.onClick {
            centerTable.clear()
            centerTable.add(getResourcesTable()).height(stage.height*0.8f)
            centerTable.pack()
        }
        topTable.add(setResourcesButton)
        if(currentPlayerCivInfo.getDetailedCivResources().isEmpty())
            setResourcesButton.disable()

        topTable.pack()

        val table = Table()
        table.add(topTable).row()
        table.add(centerTable).expand().row()
        table.setFillParent(true)
        stage.addActor(table)

    }


    private fun getTradesTable(): Table {
        val tradesTable = Table().apply { defaults().pad(10f) }
        for(diplomacy in currentPlayerCivInfo.diplomacy.values)
            for(trade in diplomacy.trades)
                tradesTable.add(createTradeTable(trade,diplomacy.otherCiv())).row()

        return tradesTable
    }

    private fun createTradeTable(trade: Trade, otherCiv:CivilizationInfo): Table {
        val generalTable = Table(skin)
        generalTable.add(createOffersTable(currentPlayerCivInfo,trade.ourOffers, trade.theirOffers.size))
        generalTable.add(createOffersTable(otherCiv, trade.theirOffers, trade.ourOffers.size))
        return generalTable
    }

    private fun createOffersTable(civ: CivilizationInfo, offersList: TradeOffersList, numberOfOtherSidesOffers: Int): Table {
        val table = Table()
        table.defaults().pad(10f)
        table.background = ImageGetter.getBackground(civ.getNation().getColor())
        table.add(civ.civName.toLabel().setFontColor(civ.getNation().getSecondaryColor())).row()
        table.addSeparator()
        for(offer in offersList){
            val offerText = offer.getOfferText()
            table.add(offerText.toLabel().setFontColor(civ.getNation().getSecondaryColor())).row()
        }
        for(i in 1..numberOfOtherSidesOffers - offersList.size)
            table.add("".toLabel()).row() // we want both sides of the general table to have the same number of rows
        return table
    }


    private fun getHappinessTable(): Table {
        val happinessTable = Table(skin)
        happinessTable.defaults().pad(5f)
        happinessTable.add("Happiness".toLabel().setFontSize(24)).colspan(2).row()
        happinessTable.addSeparator()
        for (entry in currentPlayerCivInfo.getHappinessForNextTurn()) {
            happinessTable.add(entry.key.tr())
            happinessTable.add(entry.value.roundToInt().toString()).row()
        }
        happinessTable.add("Total".tr())
        happinessTable.add(currentPlayerCivInfo.getHappinessForNextTurn().values.sum().roundToInt().toString())
        happinessTable.pack()
        return happinessTable
    }

    private fun getGoldTable(): Table {
        val goldTable = Table(skin)
        goldTable.defaults().pad(5f)
        goldTable.add("Gold".toLabel().setFontSize(24)).colspan(2).row()
        goldTable.addSeparator()
        var total=0f
        for (entry in currentPlayerCivInfo.getStatMapForNextTurn()) {
            if(entry.value.gold==0f) continue
            goldTable.add(entry.key.tr())
            goldTable.add(entry.value.gold.roundToInt().toString()).row()
            total += entry.value.gold
        }
        goldTable.add("Total".tr())
        goldTable.add(total.roundToInt().toString())
        goldTable.pack()
        return goldTable
    }


    private fun getGreatPeopleTable(): Table {
        val greatPeopleTable = Table(skin)

        val greatPersonPoints = currentPlayerCivInfo.greatPeople.greatPersonPoints.toHashMap()
        val greatPersonPointsPerTurn = currentPlayerCivInfo.getGreatPersonPointsForNextTurn().toHashMap()
        val pointsToGreatPerson = currentPlayerCivInfo.greatPeople.pointsForNextGreatPerson

        greatPeopleTable.defaults().pad(5f)
        greatPeopleTable.add("Great person points".toLabel().setFontSize(24)).colspan(3).row()
        greatPeopleTable.addSeparator()
        greatPeopleTable.add()
        greatPeopleTable.add("Current points".tr())
        greatPeopleTable.add("Points per turn".tr()).row()

        val mapping = currentPlayerCivInfo.greatPeople.statToGreatPersonMapping
        for(entry in mapping){
            greatPeopleTable.add(entry.value.tr())
            greatPeopleTable.add(greatPersonPoints[entry.key]!!.toInt().toString()+"/"+pointsToGreatPerson)
            greatPeopleTable.add(greatPersonPointsPerTurn[entry.key]!!.toInt().toString()).row()
        }
        val pointsForGreatGeneral = currentPlayerCivInfo.greatPeople.greatGeneralPoints.toString()
        val pointsForNextGreatGeneral = currentPlayerCivInfo.greatPeople.pointsForNextGreatGeneral.toString()
        greatPeopleTable.add("Great General".tr())
        greatPeopleTable.add("$pointsForGreatGeneral/$pointsForNextGreatGeneral").row()
        greatPeopleTable.pack()
        return greatPeopleTable
    }



    private fun getCityInfoTable(): Table {
        val iconSize = 20f//if you set this too low, there is a chance that the tables will be misaligned
        val padding = 5f

        val cityInfoTableIcons = Table(skin)
        cityInfoTableIcons.defaults().pad(padding).align(Align.center)

        cityInfoTableIcons.add("Cities".toLabel().setFontSize(24)).colspan(8).align(Align.center).row()
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

        for (city in currentPlayerCivInfo.cities.sortedBy { it.name }) {
            val button = Button(Label(city.name, skin), skin)
            button.onClick {
                UnCivGame.Current.setWorldScreen()
                UnCivGame.Current.worldScreen.tileMapHolder.setCenterPosition(city.ccenterTile.position)
            }
            cityInfoTableDetails.add(button)
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
        cityInfoTableTotal.add(currentPlayerCivInfo.cities.sumBy { it.population.population }.toString()).actor!!.setAlignment(Align.center)
        cityInfoTableTotal.add()//an intended empty space
        cityInfoTableTotal.add(currentPlayerCivInfo.cities.sumBy { it.cityStats.currentCityStats.gold.toInt() }.toString()).actor!!.setAlignment(Align.center)
        cityInfoTableTotal.add(currentPlayerCivInfo.cities.sumBy { it.cityStats.currentCityStats.science.toInt() }.toString()).actor!!.setAlignment(Align.center)
        cityInfoTableTotal.add()//an intended empty space
        cityInfoTableTotal.add(currentPlayerCivInfo.cities.sumBy { it.cityStats.currentCityStats.culture.toInt() }.toString()).actor!!.setAlignment(Align.center)
        cityInfoTableTotal.add(currentPlayerCivInfo.cities.sumBy {  it.cityStats.currentCityStats.happiness.toInt() }.toString()).actor!!.setAlignment(Align.center)

        cityInfoTableTotal.pack()

        val table = Table(skin)
        //since the names of the cities are on the left, and the length of the names varies
        //we align every row to the right, coz we set the size of the other(number) cells to the image size
        //and thus, we can guarantee that the tables will be aligned
        table.defaults().pad(padding).align(Align.right)

        table.add(cityInfoTableIcons).row()
        table.add(cityInfoScrollPane).width(cityInfoTableDetails.width).row()
        table.add(cityInfoTableTotal)
        table.pack()

        return table
    }

    fun getUnitTable(): Table {
        val table=Table(skin).apply { defaults().pad(5f) }
        table.add("Name".tr())
        table.add("Action".tr())
        table.add("Strength".tr())
        table.add("Ranged strength".tr())
        table.add("Movement".tr())
        table.add("Closest city".tr())
        table.row()
        table.addSeparator()

        for(unit in currentPlayerCivInfo.getCivUnits().sortedBy { it.name }){
            val baseUnit = unit.baseUnit()
            val button = TextButton(unit.name.tr(), skin)
            button.onClick {
                UnCivGame.Current.setWorldScreen()
                UnCivGame.Current.worldScreen.tileMapHolder.setCenterPosition(unit.currentTile.position)
            }
            table.add(button).left()
            val mapUnitAction = unit.mapUnitAction
            if (mapUnitAction != null) table.add(mapUnitAction.name().tr()) else table.add()
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


    fun playerKnows(civ:CivilizationInfo) = civ==currentPlayerCivInfo ||
            currentPlayerCivInfo.diplomacy.containsKey(civ.civName)

    fun getDiplomacyGroup(): Group {
        val relevantCivs = currentPlayerCivInfo.gameInfo.civilizations.filter { !it.isBarbarianCivilization() && !it.isCityState() }
        val groupSize = 500f
        val group = Group()
        group.setSize(groupSize,groupSize)
        val civGroups = HashMap<String, Actor>()
        for(i in 0..relevantCivs.lastIndex){
            val civ = relevantCivs[i]

            val civGroup = getCivGroup(civ, "", currentPlayerCivInfo)

            val vector = HexMath().getVectorForAngle(2 * Math.PI.toFloat() *i / relevantCivs.size)
            civGroup.center(group)
            civGroup.moveBy(vector.x*groupSize/3, vector.y*groupSize/3)

            civGroups[civ.civName]=civGroup
            group.addActor(civGroup)
        }

        for(civ in relevantCivs.filter { playerKnows(it) && !it.isDefeated() })
            for(diplomacy in civ.diplomacy.values.
                    filter { !it.otherCiv().isBarbarianCivilization() && !it.otherCiv().isCityState()
                            && playerKnows(it.otherCiv()) && !it.otherCiv().isDefeated()}){
                val civGroup = civGroups[civ.civName]!!
                val otherCivGroup = civGroups[diplomacy.otherCivName]!!

                val statusLine = ImageGetter.getLine(civGroup.x+civGroup.width/2,civGroup.y+civGroup.height/2,
                        otherCivGroup.x+otherCivGroup.width/2,otherCivGroup.y+otherCivGroup.height/2,3f)

                statusLine.color = if(diplomacy.diplomaticStatus== DiplomaticStatus.War) Color.RED
                else Color.GREEN

                group.addActor(statusLine)
                statusLine.toBack()
            }

        return group
    }


    private fun getResourcesTable(): Table {
        val resourcesTable=Table().apply { defaults().pad(10f) }
        val resourceDrilldown = currentPlayerCivInfo.getDetailedCivResources()

        // First row of table has all the icons
        resourcesTable.add()
        val resources = resourceDrilldown.map { it.resource }
                .filter { it.resourceType!=ResourceType.Bonus }.distinct().sortedBy { it.resourceType }

        for(resource in resources)
            resourcesTable.add(ImageGetter.getResourceImage(resource.name,30f))
        resourcesTable.addSeparator()

        val origins = resourceDrilldown.map { it.origin }.distinct()
        for(origin in origins){
            resourcesTable.add(origin.toLabel())
            for(resource in resources){
                val resourceSupply = resourceDrilldown.firstOrNull { it.resource==resource && it.origin==origin }
                if(resourceSupply==null) resourcesTable.add()
                else resourcesTable.add(resourceSupply.amount.toString().toLabel())
            }
            resourcesTable.row()
        }

        resourcesTable.add("Total".toLabel())
        for(resource in resources){
            val sum = resourceDrilldown.filter { it.resource==resource }.sumBy { it.amount }
            resourcesTable.add(sum.toString().toLabel())
        }

        return resourcesTable
    }

    companion object {
        fun getCivGroup(civ: CivilizationInfo, afterCivNameText:String,currentPlayer:CivilizationInfo): Table {
            val civGroup = Table()
            val civGroupBackground = ImageGetter.getDrawable("OtherIcons/civTableBackground.png")

            val civNameText = civ.civName.tr()+afterCivNameText
            val label = civNameText.toLabel()
            label.setAlignment(Align.center)

            if (civ.isDefeated()) {
                civGroup.add(ImageGetter.getImage("OtherIcons/DisbandUnit")).size(30f)
                civGroup.background = civGroupBackground.tint(Color.LIGHT_GRAY)
                label.setFontColor(Color.BLACK)
            } else if (currentPlayer==civ || currentPlayer.knows(civ)) {
                civGroup.add(ImageGetter.getNationIndicator(civ.getNation(), 30f))
                civGroup.background = civGroupBackground.tint(civ.getNation().getColor())
                label.setFontColor(civ.getNation().getSecondaryColor())
            } else {
                civGroup.background = civGroupBackground.tint(Color.DARK_GRAY)
                label.setText("???")
            }

            civGroup.add(label).pad(10f)
            civGroup.pack()
            return civGroup
        }
    }
}