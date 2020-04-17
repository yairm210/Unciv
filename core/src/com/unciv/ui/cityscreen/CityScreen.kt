package com.unciv.ui.cityscreen

import com.badlogic.gdx.Input
import com.unciv.ui.utils.AutoScrollPane as ScrollPane
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.ui.TextButton
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.HexMath
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.IConstruction
import com.unciv.logic.map.TileInfo
import com.unciv.models.Tutorial
import com.unciv.models.translations.tr
import com.unciv.ui.map.TileGroupMap
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.*
import java.util.*

class CityScreen(internal val city: CityInfo): CameraStageBaseScreen() {
    var selectedTile: TileInfo? = null
    var selectedConstruction: IConstruction? = null
    var keyListener: InputListener? = null

    /** Toggle between Constructions and cityInfo (buildings, specialists etc. */
    var showConstructionsTable = true

    // Clockwise from the top-left

    /** Displays current production, production queue and available productions list - sits on LEFT */
    private var constructionsTable = ConstructionsTable(this)

    /** Displays stats, buildings, specialists and stats drilldown - sits on TOP RIGHT */
    private var cityInfoTable = CityInfoTable(this)

    /** Displays raze city button - sits on TOP CENTER */
    private var razeCityButtonHolder = Table()

    /** Displays city stats info */
    private var cityStatsTable = CityStatsTable(this)

    /** Displays tile info, alternate with selectedConstructionTable - sits on BOTTOM RIGHT */
    private var tileTable = CityScreenTileTable(this)

    /** Displays selected construction info, alternate with tileTable - sits on BOTTOM RIGHT */
    private var selectedConstructionTable = ConstructionInfoTable(this.city)

    /** Displays city name, allows switching between cities - sits on BOTTOM CENTER */
    private var cityPickerTable = CityScreenCityPickerTable(this)

    /** Holds City tiles group*/
    private var tileGroups = ArrayList<CityTileGroup>()

    init {
        onBackButtonClicked { game.setWorldScreen() }
        UncivGame.Current.settings.addCompletedTutorialTask("Enter city screen")

        addTiles()

        //stage.setDebugTableUnderMouse(true)
        stage.addActor(cityStatsTable)
        stage.addActor(constructionsTable)
        stage.addActor(tileTable)
        stage.addActor(selectedConstructionTable)
        stage.addActor(cityPickerTable)
        stage.addActor(cityInfoTable)
        update()

        keyListener = getKeyboardListener()
        stage.addListener(keyListener)
    }

    internal fun update() {
        if (showConstructionsTable) {
            constructionsTable.isVisible = true
            cityInfoTable.isVisible = false
        } else {
            constructionsTable.isVisible = false
            cityInfoTable.isVisible = true
        }

        city.cityStats.update()

        constructionsTable.update(selectedConstruction)
        constructionsTable.setPosition(5f, stage.height - 5f, Align.topLeft)

        cityInfoTable.update()
        cityInfoTable.setPosition(5f, stage.height - 5f, Align.topLeft)

        cityPickerTable.update()
        cityPickerTable.centerX(stage)

        tileTable.update(selectedTile)
        tileTable.setPosition(stage.width - 5f, 5f, Align.bottomRight)

        selectedConstructionTable.update(selectedConstruction)
        selectedConstructionTable.setPosition(stage.width - 5f, 5f, Align.bottomRight)

        cityStatsTable.update()
        cityStatsTable.setPosition( stage.width - 5f, stage.height - 5f, Align.topRight)

        updateAnnexAndRazeCityButton()
        updateTileGroups()
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

    private fun updateAnnexAndRazeCityButton() {
        razeCityButtonHolder.clear()

        if(city.isPuppet) {
            val annexCityButton = "Annex city".toTextButton()
            annexCityButton.labelCell.pad(10f)
            annexCityButton.onClick {
                city.annexCity()
                update()
            }
            razeCityButtonHolder.add(annexCityButton).colspan(cityPickerTable.columns)
        } else if(!city.isBeingRazed) {
            val razeCityButton = "Raze city".toTextButton()
            razeCityButton.labelCell.pad(10f)
            razeCityButton.onClick { city.isBeingRazed=true; update() }
            if(!UncivGame.Current.worldScreen.isPlayersTurn || city.isOriginalCapital)
                razeCityButton.disable()

            razeCityButtonHolder.add(razeCityButton).colspan(cityPickerTable.columns)
        } else {
            val stopRazingCityButton = "Stop razing city".toTextButton()
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
                if (city.isPuppet) return@onClick

                selectedTile = tileInfo
                selectedConstruction = null
                if (tileGroup.isWorkable && UncivGame.Current.worldScreen.isPlayersTurn) {
                    if (!tileInfo.isWorked() && city.population.getFreePopulation() > 0) {
                        city.workedTiles.add(tileInfo.position)
                        game.settings.addCompletedTutorialTask("Reassign worked tiles")
                    } else if (tileInfo.isWorked() && !tileInfo.isLocked())
                        city.workedTiles.remove(tileInfo.position)
                    city.cityStats.update()
                }
                update()
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

    fun exit() {
        stage.removeListener(keyListener)
        game.setWorldScreen()
        game.worldScreen.mapHolder.setCenterPosition(city.location)
        game.worldScreen.bottomUnitTable.selectedUnit=null
    }
    fun page(delta: Int) {
        val civInfo = city.civInfo
        val numCities = civInfo.cities.size
        if (numCities == 0) return
        val indexOfCity = civInfo.cities.indexOf(city)
        val indexOfNextCity = (indexOfCity + delta + numCities) % numCities
        stage.removeListener(keyListener)
        game.setScreen(CityScreen(civInfo.cities[indexOfNextCity]))
    }

    private fun getKeyboardListener(): InputListener = object : InputListener() {
        override fun keyTyped(event: InputEvent?, character: Char): Boolean {
            if (character != 0.toChar() || event == null) return super.keyTyped(event, character)
            if (event.keyCode == Input.Keys.LEFT) page(-1)
            if (event.keyCode == Input.Keys.RIGHT) page(1)
            return true
        }
    }
}