package com.unciv.ui.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.city.CityInfo
import com.unciv.logic.map.TileInfo
import com.unciv.models.Tutorial
import com.unciv.models.stats.Stat
import com.unciv.models.stats.Stats
import com.unciv.models.translations.tr
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.*
import com.unciv.ui.map.TileGroupMap
import java.util.*
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round

class CityScreen(internal val city: CityInfo) : CameraStageBaseScreen() {
    private var selectedTile: TileInfo? = null

    // Clockwise from the top-left
    /** Displays city stats - sits on the top left side */
    var topCityStatsTable=Table()

    private var razeCityButtonHolder = Table() // sits on the top

    /** Displays buildings, specialists and stats drilldown - sits on the top right of the city screen */
    private var cityInfoTable = CityInfoTable(this)

    /** Displays tile info, sits on the bottom right */
    private var tileTable = CityScreenTileTable(city)

    /** Displays city name, allows switching between cities - sits on the bottom */
    private var cityPickerTable = CityScreenCityPickerTable(this)

    /** Holds production list and current production - sits on the bottom left */
    private var constructionsTable = ConstructionsTable(this)

    private var tileGroups = ArrayList<CityTileGroup>()

    init {
        onBackButtonClicked { game.setWorldScreen() }
        addTiles()
        UncivGame.Current.settings.addCompletedTutorialTask("Enter city screen")

        val tableBackgroundColor = ImageGetter.getBlue().lerp(Color.BLACK,0.5f)

        var buildingsTableContainer = Table()
        buildingsTableContainer.pad(3f)
        buildingsTableContainer.background = ImageGetter.getBackground(tableBackgroundColor)
        cityInfoTable.update()
        val buildingsScroll = ScrollPane(cityInfoTable)
        buildingsTableContainer.add(buildingsScroll)
                .size(stage.width/4,stage.height / 2)

        buildingsTableContainer = buildingsTableContainer.addBorder(2f, Color.WHITE)
        buildingsTableContainer.setPosition(stage.width - buildingsTableContainer.width-5,
                stage.height - buildingsTableContainer.height-5)

        stage.addActor(constructionsTable)
        stage.addActor(tileTable)

        stage.addActor(cityPickerTable)
        stage.addActor(buildingsTableContainer)

        update()
    }

    internal fun update() {
        cityInfoTable.update()

        cityPickerTable.update()
        cityPickerTable.centerX(stage)

        constructionsTable.update()
        updateAnnexAndRazeCityButton()
        tileTable.update(selectedTile)
        tileTable.setPosition(stage.width-5, 5f,Align.bottomRight)
        updateTileGroups()

        topCityStatsTable.remove()
        topCityStatsTable = getCityStatsTable()
        topCityStatsTable.setPosition(5f, stage.height-5, Align.topLeft)
        stage.addActor(topCityStatsTable)
        constructionsTable.height=stage.height-topCityStatsTable.height
        constructionsTable.setPosition(5f, stage.height-5-topCityStatsTable.height, Align.topLeft)

        if (city.getCenterTile().getTilesAtDistance(4).isNotEmpty()){
            displayTutorial(Tutorial.CityRange)
        }
    }

    private fun updateTileGroups() {
        val nextTile = city.expansion.chooseNewTileToOwn()
        for (tileGroup in tileGroups) {

            tileGroup.update()
            if(tileGroup.tileInfo == nextTile){
                tileGroup.showCircle(Color.PURPLE)
                tileGroup.setColor(0f,0f,0f,0.7f)
            }
        }

    }

    fun getCityStatsTable(): Table {
        val table=Table().pad(10f)
        table.defaults().pad(5f)
        table.background=ImageGetter.getBackground(Color.BLACK.cpy().apply { a=0.8f })
        val columns = Stats().toHashMap().size
        table.add(Label("{Unassigned population}:".tr()
                +city.population.getFreePopulation().toString() + "/" + city.population.population,skin))
                .colspan(columns).row()

        val turnsToExpansionString : String
        if (city.cityStats.currentCityStats.culture > 0) {
            var turnsToExpansion = ceil((city.expansion.getCultureToNextTile() - city.expansion.cultureStored)
                    / city.cityStats.currentCityStats.culture).toInt()
            if (turnsToExpansion < 1) turnsToExpansion = 1
            turnsToExpansionString = "[$turnsToExpansion] turns to expansion".tr()
        } else {
            turnsToExpansionString = "Stopped expansion".tr()
        }
        table.add(Label(turnsToExpansionString + " (" + city.expansion.cultureStored + "/" + city.expansion.getCultureToNextTile() + ")",
                skin)).colspan(columns).row()

        val turnsToPopString : String
        if (city.isGrowing()) {
            var turnsToGrowth = city.getNumTurnsToNewPopulation()
            turnsToPopString = "[$turnsToGrowth] turns to new population".tr()
        } else if (city.isStarving()) {
            var turnsToStarvation = city.getNumTurnsToStarvation()
            turnsToPopString = "[$turnsToStarvation] turns to lose population".tr()
        } else if (city.cityConstructions.currentConstruction == Constants.settler) {
            turnsToPopString = "Food converts to production".tr()
        } else {
            turnsToPopString = "Stopped population growth".tr()
        }
        table.add(Label(turnsToPopString + " (" + city.population.foodStored + "/" + city.population.getFoodToNextPopulation() + ")"
                ,skin)).colspan(columns).row()

        if (city.isInResistance()) {
            table.add(Label("In resistance for another [${city.resistanceCounter}] turns".tr(),skin)).colspan(columns).row()
        }

        table.addSeparator()
        val beige = colorFromRGB(194,180,131)
        for(stat in city.cityStats.currentCityStats.toHashMap()) {
            if(stat.key==Stat.Happiness) continue
            val minitable=Table().padRight(5f).padLeft(5f)
            minitable.add(ImageGetter.getStatIcon(stat.key.name)).size(20f).padRight(3f)
            minitable.add(round(stat.value).toInt().toString().toLabel())
            table.add(minitable)
        }
        return table.addBorder(2f, beige)
    }

    private fun updateAnnexAndRazeCityButton() {
        razeCityButtonHolder.clear()

        if(city.isPuppet) {
            val annexCityButton = TextButton("Annex city".tr(), skin)
            annexCityButton.labelCell.pad(10f)
            annexCityButton.onClick {
                city.annexCity()
                update()
            }
            razeCityButtonHolder.add(annexCityButton).colspan(cityPickerTable.columns)
        }
        else if(!city.isBeingRazed) {
            val razeCityButton = TextButton("Raze city".tr(), skin)
            razeCityButton.labelCell.pad(10f)
            razeCityButton.onClick { city.isBeingRazed=true; update() }
            if(!UncivGame.Current.worldScreen.isPlayersTurn) razeCityButton.disable()
            razeCityButtonHolder.add(razeCityButton).colspan(cityPickerTable.columns)
        }
        else {
            val stopRazingCityButton = TextButton("Stop razing city".tr(), skin)
            stopRazingCityButton.labelCell.pad(10f)
            stopRazingCityButton.onClick { city.isBeingRazed=false; update() }
            if(!UncivGame.Current.worldScreen.isPlayersTurn) stopRazingCityButton.disable()
            razeCityButtonHolder.add(stopRazingCityButton).colspan(cityPickerTable.columns)
        }
        razeCityButtonHolder.pack()
        //goToWorldButton.setSize(goToWorldButton.prefWidth, goToWorldButton.prefHeight)
        razeCityButtonHolder.centerX(stage)
        razeCityButtonHolder.y = stage.height - razeCityButtonHolder.height - 20
        stage.addActor(razeCityButtonHolder)
    }

    private fun addTiles() {
        val cityInfo = city

        val tileSetStrings = TileSetStrings()
        val cityTileGroups = cityInfo.getCenterTile().getTilesInDistance(5)
                .filter { city.civInfo.exploredTiles.contains(it.position) }
                .map { CityTileGroup(cityInfo, it, tileSetStrings).apply { unitLayerGroup.isVisible = false } }

        for (tileGroup in cityTileGroups) {
            val tileInfo = tileGroup.tileInfo

            tileGroup.onClick {
                if (!city.isPuppet) {
                    selectedTile = tileInfo
                    if (tileGroup.isWorkable && UncivGame.Current.worldScreen.isPlayersTurn) {
                        if (!tileInfo.isWorked() && city.population.getFreePopulation() > 0) {
                            city.workedTiles.add(tileInfo.position)
                            game.settings.addCompletedTutorialTask("Reassign worked tiles")
                        }
                        else if (tileInfo.isWorked()) city.workedTiles.remove(tileInfo.position)
                        city.cityStats.update()
                    }
                    update()
                }
            }

            tileGroups.add(tileGroup)

            val positionalVector = HexMath.hex2WorldCoords(tileInfo.position.cpy().sub(cityInfo.location))
            val groupSize = 50
            tileGroup.setPosition(stage.width / 2 + positionalVector.x * 0.8f * groupSize.toFloat(),
                    stage.height / 2 + positionalVector.y * 0.8f * groupSize.toFloat())
        }

        val tileMapGroup = TileGroupMap(tileGroups,stage.width/2)
        val scrollPane = ScrollPane(tileMapGroup)
        scrollPane.setSize(stage.width,stage.height)
        scrollPane.setOrigin(stage.width / 2, stage.height / 2)
        scrollPane.center(stage)
        stage.addActor(scrollPane)

        scrollPane.layout() // center scrolling
        scrollPane.scrollPercentX=0.5f
        scrollPane.scrollPercentY=0.5f
        scrollPane.updateVisualScroll()
    }

}