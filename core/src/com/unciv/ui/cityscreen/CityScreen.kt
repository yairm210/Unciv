package com.unciv.ui.cityscreen

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.IConstruction
import com.unciv.logic.map.TileInfo
import com.unciv.ui.map.TileGroupMap
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.*
import java.util.*
import com.unciv.ui.utils.AutoScrollPane as ScrollPane

class CityScreen(internal val city: CityInfo): CameraStageBaseScreen() {
    var selectedTile: TileInfo? = null
    var selectedConstruction: IConstruction? = null

    /** Toggles or adds/removes all state changing buttons */
    val canChangeState = UncivGame.Current.worldScreen.canChangeState

    /** Toggle between Constructions and cityInfo (buildings, specialists etc. */
    var showConstructionsTable = true

    // Clockwise from the top-left

    /** Displays current production, production queue and available productions list - sits on LEFT */
    private var constructionsTable = CityConstructionsTable(this)

    /** Displays stats, buildings, specialists and stats drilldown - sits on TOP LEFT, can be toggled to */
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

    /** Button for exiting the city - sits on BOTTOM CENTER */
    private val exitCityButton = "Exit city".toTextButton().apply {
        labelCell.pad(10f)
        onClick { exit() }
    }

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
        stage.addActor(exitCityButton)
        stage.addActor(cityInfoTable)
        update()

        keyPressDispatcher[Input.Keys.LEFT] = { page(-1) }
        keyPressDispatcher[Input.Keys.RIGHT] = { page(1) }
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

        exitCityButton.centerX(stage)
        exitCityButton.y = 10f
        cityPickerTable.update()
        cityPickerTable.centerX(stage)
        cityPickerTable.setY(exitCityButton.top + 10f, Align.bottom)

        tileTable.update(selectedTile)
        tileTable.setPosition(stage.width - 5f, 5f, Align.bottomRight)

        selectedConstructionTable.update(selectedConstruction)
        selectedConstructionTable.setPosition(stage.width - 5f, 5f, Align.bottomRight)

        cityStatsTable.update()
        cityStatsTable.setPosition(stage.width - 5f, stage.height - 5f, Align.topRight)

        updateAnnexAndRazeCityButton()
        updateTileGroups()
    }

    private fun updateTileGroups() {
        val nextTile = city.expansion.chooseNewTileToOwn()
        for (tileGroup in tileGroups) {
            tileGroup.update()
            tileGroup.hideCircle()
            if (city.tiles.contains(tileGroup.tileInfo.position)
                    && constructionsTable.improvementBuildingToConstruct != null) {
                val improvement = constructionsTable.improvementBuildingToConstruct!!.getImprovement(city.getRuleset())!!
                if (tileGroup.tileInfo.canBuildImprovement(improvement, city.civInfo))
                    tileGroup.showCircle(Color.GREEN)
                else tileGroup.showCircle(Color.RED)
            }
            if (tileGroup.tileInfo == nextTile) {
                tileGroup.showCircle(Color.PURPLE)
                tileGroup.setColor(0f, 0f, 0f, 0.7f)
            }
        }
    }

    private fun updateAnnexAndRazeCityButton() {
        razeCityButtonHolder.clear()

        if (city.isPuppet) {
            val annexCityButton = "Annex city".toTextButton()
            annexCityButton.labelCell.pad(10f)
            annexCityButton.onClick {
                city.annexCity()
                update()
            }
            if (!canChangeState) annexCityButton.disable()
            razeCityButtonHolder.add(annexCityButton).colspan(cityPickerTable.columns)
        } else if (!city.isBeingRazed) {
            val razeCityButton = "Raze city".toTextButton()
            razeCityButton.labelCell.pad(10f)
            razeCityButton.onClick { city.isBeingRazed = true; update() }
            if (!canChangeState || !city.canBeDestroyed())
                razeCityButton.disable()

            razeCityButtonHolder.add(razeCityButton).colspan(cityPickerTable.columns)
        } else {
            val stopRazingCityButton = "Stop razing city".toTextButton()
            stopRazingCityButton.labelCell.pad(10f)
            stopRazingCityButton.onClick { city.isBeingRazed = false; update() }
            if (!canChangeState) stopRazingCityButton.disable()
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
                .map { CityTileGroup(cityInfo, it, tileSetStrings) }

        for (tileGroup in cityTileGroups) {
            val tileInfo = tileGroup.tileInfo

            tileGroup.onClick {
                if (city.isPuppet) return@onClick

                if (constructionsTable.improvementBuildingToConstruct != null) {
                    val improvement = constructionsTable.improvementBuildingToConstruct!!.getImprovement(city.getRuleset())!!
                    if (tileInfo.canBuildImprovement(improvement, cityInfo.civInfo)) {
                        tileInfo.improvementInProgress = improvement.name
                        tileInfo.turnsToImprovement = -1
                        constructionsTable.improvementBuildingToConstruct = null
                        cityInfo.cityConstructions.addToQueue(improvement.name)
                        update()
                    } else {
                        constructionsTable.improvementBuildingToConstruct = null
                        update()
                    }
                    return@onClick
                }

                selectedTile = tileInfo
                selectedConstruction = null
                if (tileGroup.isWorkable && canChangeState) {
                    if (!tileInfo.providesYield() && city.population.getFreePopulation() > 0) {
                        city.workedTiles.add(tileInfo.position)
                        game.settings.addCompletedTutorialTask("Reassign worked tiles")
                    } else if (tileInfo.isWorked() && !tileInfo.isLocked())
                        city.workedTiles.remove(tileInfo.position)
                    city.cityStats.update()
                }
                update()
            }

            tileGroups.add(tileGroup)
        }

        val tilesToUnwrap = ArrayList<CityTileGroup>()
        for (tileGroup in tileGroups) {
            val xDifference = city.getCenterTile().position.x - tileGroup.tileInfo.position.x
            val yDifference = city.getCenterTile().position.y - tileGroup.tileInfo.position.y
            //if difference is bigger than 5 the tileGroup we are looking for is on the other side of the map
            if (xDifference > 5 || xDifference < -5 || yDifference > 5 || yDifference < -5) {
                //so we want to unwrap its position
                tilesToUnwrap.add(tileGroup)
            }
        }

        val tileMapGroup = TileGroupMap(tileGroups, stage.width / 2, stage.height / 2, tileGroupsToUnwrap = tilesToUnwrap)
        val scrollPane = ScrollPane(tileMapGroup)
        scrollPane.setSize(stage.width, stage.height)
        scrollPane.setOrigin(stage.width / 2, stage.height / 2)
        scrollPane.center(stage)
        stage.addActor(scrollPane)

        scrollPane.layout() // center scrolling
        scrollPane.scrollPercentX = 0.5f
        scrollPane.scrollPercentY = 0.5f
        scrollPane.updateVisualScroll()
    }

    fun exit() {
        game.setWorldScreen()
        game.worldScreen.mapHolder.setCenterPosition(city.location)
        game.worldScreen.bottomUnitTable.selectUnit()
    }

    fun page(delta: Int) {
        val civInfo = city.civInfo
        val numCities = civInfo.cities.size
        if (numCities == 0) return
        val indexOfCity = civInfo.cities.indexOf(city)
        val indexOfNextCity = (indexOfCity + delta + numCities) % numCities
        val newCityScreen = CityScreen(civInfo.cities[indexOfNextCity])
        newCityScreen.showConstructionsTable = showConstructionsTable // stay on stats drilldown between cities
        newCityScreen.update()
        game.setScreen(newCityScreen)
    }

    override fun resize(width: Int, height: Int) {
        if (stage.viewport.screenWidth != width || stage.viewport.screenHeight != height) {
            game.setScreen(CityScreen(city))
        }
    }
}
