package com.unciv.ui

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.UnCivGame
import com.unciv.ui.utils.*
import kotlin.math.max
import kotlin.math.roundToInt

class EmpireOverviewScreen : CameraStageBaseScreen(){

    val civInfo = UnCivGame.Current.gameInfo.getPlayerCivilization()
    init {
        val topTable = Table().apply { defaults().pad(10f) }
        val centerTable=Table().apply {  defaults().pad(20f) }

        val closeButton = TextButton("Close".tr(), skin)
        closeButton.addClickListener { UnCivGame.Current.setWorldScreen() }
        closeButton.y = stage.height - closeButton.height - 5
        topTable.add(closeButton)

        val setCityInfoButton = TextButton("Cities",skin)
        val setCities = {
            centerTable.clear()
            centerTable.add(getCityInfoTable())
            centerTable.pack()
            centerTable.center(stage)
        }
        setCities()
        setCityInfoButton.addClickListener(setCities)
        topTable.add(setCityInfoButton)

        val setStatsInfoButton = TextButton("Stats",skin)
        setStatsInfoButton.addClickListener {
            centerTable.clear()
            centerTable.add(getHappinessTable())
            centerTable.add(getGoldTable())
            centerTable.pack()
            centerTable.center(stage)
        }
        topTable.add(setStatsInfoButton)

        val setCurrentTradesButton = TextButton("Trades",skin)
        setCurrentTradesButton.addClickListener {
            centerTable.clear()
            centerTable.add(getTradesTable())
            centerTable.pack()
            centerTable.center(stage)
        }
        topTable.add(setCurrentTradesButton)

        topTable.pack()
        topTable.width = stage.width
        topTable.y = stage.height-topTable.height

        stage.addActor(topTable)
        stage.addActor(centerTable)
    }

    private fun getTradesTable(): Table {
        val tradesTable = Table()
        for(diplomacy in civInfo.diplomacy.values)
            for(trade in diplomacy.trades)
                tradesTable.add(createTradeTable(trade,diplomacy.otherCivName)).row()

        return tradesTable
    }

    private fun createTradeTable(trade:Trade, civName:String): Table {
        val table = Table(skin)
        table.defaults().pad(10f)
        table.add(civInfo.civName)
        table.add(civName).row()
        val ourOffersStrings = trade.ourOffers.map { it.amount.toString()+" "+it.name +
                (if (it.duration==0) "" else " ("+it.duration+" turns)") }
        val theirOffersStrings = trade.theirOffers.map { it.amount.toString()+" "+it.name +
                (if (it.duration==0) "" else " ("+it.duration+" turns)") }
        for(i in 0 until max(trade.ourOffers.size,trade.theirOffers.size)){
            if(ourOffersStrings.size>i) table.add(ourOffersStrings[i])
            else table.add()
            if(theirOffersStrings.size>i) table.add(theirOffersStrings[i])
            else table.add()
            table.row()
        }
        return table
    }

    private fun getHappinessTable(): Table {
        val happinessTable = Table(skin)
        happinessTable.defaults().pad(5f)
        happinessTable.add(Label("Happiness", skin).setFont(24)).colspan(2).row()
        for (entry in civInfo.getHappinessForNextTurn()) {
            happinessTable.add(entry.key)
            happinessTable.add(entry.value.toString()).row()
        }
        happinessTable.add("Total")
        happinessTable.add(civInfo.getHappinessForNextTurn().values.sum().toString())
        happinessTable.pack()
        return happinessTable
    }

    private fun getGoldTable(): Table {
        val goldTable = Table(skin)
        goldTable.defaults().pad(5f)
        goldTable.add(Label("Gold", skin).setFont(24)).colspan(2).row()
        var total=0f
        for (entry in civInfo.getStatsForNextTurn()) {
            if(entry.value.gold==0f) continue
            goldTable.add(entry.key)
            goldTable.add(entry.value.gold.toString()).row()
            total += entry.value.gold
        }
        goldTable.add("Total")
        goldTable.add(total.toString())
        goldTable.pack()
        return goldTable
    }

    private fun getCityInfoTable(): Table {
        val iconSize = 20f//if you set this too low, there is a chance that the tables will be misaligned
        val padding = 5f

        val cityInfoTableIcons = Table(skin)
        cityInfoTableIcons.defaults().pad(padding).align(Align.center)

        cityInfoTableIcons.add(Label("Cities", skin).setFont(24)).colspan(8).align(Align.center).row()
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

        for (city in civInfo.cities) {
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

        cityInfoTableTotal.add("Total")
        cityInfoTableTotal.add(civInfo.cities.sumBy { it.population.population }.toString()).actor!!.setAlignment(Align.center)
        cityInfoTableTotal.add()//an intended empty space
        cityInfoTableTotal.add(civInfo.cities.sumBy { it.cityStats.currentCityStats.gold.toInt() }.toString()).actor!!.setAlignment(Align.center)
        cityInfoTableTotal.add(civInfo.cities.sumBy { it.cityStats.currentCityStats.science.toInt() }.toString()).actor!!.setAlignment(Align.center)
        cityInfoTableTotal.add()//an intended empty space
        cityInfoTableTotal.add(civInfo.cities.sumBy { it.cityStats.currentCityStats.culture.toInt() }.toString()).actor!!.setAlignment(Align.center)
        cityInfoTableTotal.add(civInfo.cities.sumBy {  it.cityStats.currentCityStats.happiness.toInt() }.toString()).actor!!.setAlignment(Align.center)

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
}