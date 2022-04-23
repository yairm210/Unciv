package com.unciv.ui.cityscreen

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.IConstruction
import com.unciv.logic.city.INonPerpetualConstruction
import com.unciv.logic.map.TileInfo
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.map.TileGroupMap
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.*
import java.util.*
import kotlin.math.max

class CityScreen(
    internal val city: CityInfo,
    var selectedConstruction: IConstruction? = null,
    var selectedTile: TileInfo? = null
): BaseScreen() {
    companion object {
        /** Distance from stage edges to floating widgets */
        const val posFromEdge = 5f

        /** Size of the decoration icons shown besides the raze button */
        const val wltkIconSize = 40f
    }

    /** Toggles or adds/removes all state changing buttons */
    val canChangeState = UncivGame.Current.worldScreen.canChangeState

    /** Toggle between Constructions and cityInfo (buildings, specialists etc. */
    var showConstructionsTable = true

    // Clockwise from the top-left

    /** Displays current production, production queue and available productions list
     *  Not a widget, but manages two: construction queue, info toggle button, buy buttons
     *  in a Table holder on upper LEFT, and available constructions in a ScrollPane lower LEFT.
     */
    private var constructionsTable = CityConstructionsTable(this)

    /** Displays stats, buildings, specialists and stats drilldown - sits on TOP LEFT, can be toggled to */
    private var cityInfoTable = CityInfoTable(this)

    /** Displays raze city button - sits on TOP CENTER */
    private var razeCityButtonHolder = Table()

    /** Displays reset population button - sits on TOP CENTER */
    private var resetTilesButtonHolder = Table()

    /** Displays city stats info */
    private var cityStatsTable = CityStatsTable(this)

    /** Displays tile info, alternate with selectedConstructionTable - sits on BOTTOM RIGHT */
    private var tileTable = CityScreenTileTable(this)

    /** Displays selected construction info, alternate with tileTable - sits on BOTTOM RIGHT */
    private var selectedConstructionTable = ConstructionInfoTable(this)

    /** Displays city name, allows switching between cities - sits on BOTTOM CENTER */
    private var cityPickerTable = CityScreenCityPickerTable(this)

    /** Button for exiting the city - sits on BOTTOM CENTER */
    private val exitCityButton = "Exit city".toTextButton().apply {
        labelCell.pad(10f)
        onClick { exit() }
    }

    /** Holds City tiles group*/
    private var tileGroups = ArrayList<CityTileGroup>()

    /** The ScrollPane for the background map view of the city surroundings */
    private val mapScrollPane = ZoomableScrollPane()

    init {
        onBackButtonClicked { game.setWorldScreen() }
        UncivGame.Current.settings.addCompletedTutorialTask("Enter city screen")

        addTiles()

        //stage.setDebugTableUnderMouse(true)
        stage.addActor(cityStatsTable)
        val resetTilesButton = "Reset Tiles".toTextButton()
        resetTilesButton.labelCell.pad(5f)
        resetTilesButton.onClick { city.reassignPopulation(resetLocked = true); update() }
        resetTilesButtonHolder.add(resetTilesButton)
        resetTilesButtonHolder.pack()
        stage.addActor(resetTilesButtonHolder)
        constructionsTable.addActorsToStage()
        stage.addActor(cityInfoTable)
        stage.addActor(selectedConstructionTable)
        stage.addActor(tileTable)
        stage.addActor(cityPickerTable)  // add late so it's top in Z-order and doesn't get covered in cramped portrait
        stage.addActor(exitCityButton)
        update()

        keyPressDispatcher[Input.Keys.LEFT] = { page(-1) }
        keyPressDispatcher[Input.Keys.RIGHT] = { page(1) }
        keyPressDispatcher['T'] = {
            if (selectedTile != null)
                tileTable.askToBuyTile(selectedTile!!)
        }
        keyPressDispatcher['B'] = {
            if (selectedConstruction is INonPerpetualConstruction)
                constructionsTable.askToBuyConstruction(selectedConstruction as INonPerpetualConstruction)
        }
    }

    internal fun update() {
        // Recalculate Stats
        city.cityStats.update()

        // Left side, top and bottom: Construction queue / details
        if (showConstructionsTable) {
            constructionsTable.isVisible = true
            cityInfoTable.isVisible = false
            constructionsTable.update(selectedConstruction)
        } else {
            constructionsTable.isVisible = false
            cityInfoTable.isVisible = true
            cityInfoTable.update()
            // CityInfoTable sets its relative position itself
        }

        // Bottom right: Tile or selected construction info
        tileTable.update(selectedTile)
        tileTable.setPosition(stage.width - posFromEdge, posFromEdge, Align.bottomRight)
        selectedConstructionTable.update(selectedConstruction)
        selectedConstructionTable.setPosition(stage.width - posFromEdge, posFromEdge, Align.bottomRight)
        resetTilesButtonHolder.setPosition(stage.width - posFromEdge,
                posFromEdge + max(tileTable.height, selectedConstructionTable.height) + 10f, Align.bottomRight)

        // In portrait mode only: calculate already occupied horizontal space
        val rightMargin = when {
            !isPortrait() -> 0f
            selectedTile != null -> tileTable.packIfNeeded().width
            selectedConstruction != null -> selectedConstructionTable.packIfNeeded().width
            else -> posFromEdge
        }
        val leftMargin = when {
            !isPortrait() -> 0f
            showConstructionsTable -> constructionsTable.getLowerWidth()
            else -> cityInfoTable.packIfNeeded().width
        }

        // Bottom center: Name, paging, exit city button
        val centeredX = (stage.width - leftMargin - rightMargin) / 2 + leftMargin
        exitCityButton.setPosition(centeredX, 10f, Align.bottom)
        cityPickerTable.update()
        cityPickerTable.setPosition(centeredX, exitCityButton.top + 10f, Align.bottom)

        // Top right of screen: Stats / Specialists
        cityStatsTable.update()
        cityStatsTable.setPosition(stage.width - posFromEdge, stage.height - posFromEdge, Align.topRight)

        // Top center: Annex/Raze button
        updateAnnexAndRazeCityButton()

        // Rest of screen: Map of surroundings
        updateTileGroups()
        if (isPortrait()) mapScrollPane.apply {
            // center scrolling so city center sits more to the bottom right
            scrollX = (maxX - constructionsTable.getLowerWidth() - posFromEdge) / 2
            scrollY = (maxY - cityStatsTable.packIfNeeded().height - posFromEdge + cityPickerTable.top) / 2
            updateVisualScroll()
        }
    }

    private fun updateTileGroups() {
        val nextTile = city.expansion.chooseNewTileToOwn()
        for (tileGroup in tileGroups) {
            tileGroup.update()
            tileGroup.hideHighlight()
            if (city.tiles.contains(tileGroup.tileInfo.position)
                    && constructionsTable.improvementBuildingToConstruct != null) {
                val improvement = constructionsTable.improvementBuildingToConstruct!!.getImprovement(city.getRuleset())!!
                if (tileGroup.tileInfo.canBuildImprovement(improvement, city.civInfo))
                    tileGroup.showHighlight(Color.GREEN)
                else tileGroup.showHighlight(Color.RED)
            }
            if (tileGroup.tileInfo == nextTile) {
                tileGroup.showHighlight(Color.PURPLE)
                tileGroup.setColor(0f, 0f, 0f, 0.7f)
            }
        }
    }

    private fun updateAnnexAndRazeCityButton() {
        razeCityButtonHolder.clear()

        fun addWltkIcon(name: String, apply: Image.()->Unit = {}) =
            razeCityButtonHolder.add(ImageGetter.getImage(name).apply(apply)).size(wltkIconSize)

        if (city.isWeLoveTheKingDayActive()) {
            addWltkIcon("OtherIcons/WLTK LR") { color = Color.GOLD }
            addWltkIcon("OtherIcons/WLTK 1") { color = Color.FIREBRICK }.padRight(10f)
        }

        if (city.isPuppet) {
            val annexCityButton = "Annex city".toTextButton()
            annexCityButton.labelCell.pad(10f)
            annexCityButton.onClick {
                city.annexCity()
                update()
            }
            if (!canChangeState) annexCityButton.disable()
            razeCityButtonHolder.add(annexCityButton) //.colspan(cityPickerTable.columns)
        } else if (!city.isBeingRazed) {
            val razeCityButton = "Raze city".toTextButton()
            razeCityButton.labelCell.pad(10f)
            razeCityButton.onClick { city.isBeingRazed = true; update() }
            if (!canChangeState || !city.canBeDestroyed())
                razeCityButton.disable()

            razeCityButtonHolder.add(razeCityButton) //.colspan(cityPickerTable.columns)
        } else {
            val stopRazingCityButton = "Stop razing city".toTextButton()
            stopRazingCityButton.labelCell.pad(10f)
            stopRazingCityButton.onClick { city.isBeingRazed = false; update() }
            if (!canChangeState) stopRazingCityButton.disable()
            razeCityButtonHolder.add(stopRazingCityButton) //.colspan(cityPickerTable.columns)
        }

        if (city.isWeLoveTheKingDayActive()) {
            addWltkIcon("OtherIcons/WLTK 2") { color = Color.FIREBRICK }.padLeft(10f)
            addWltkIcon("OtherIcons/WLTK LR") {
                color = Color.GOLD
                scaleX = -scaleX
                originX = wltkIconSize * 0.5f
            }
        }

        razeCityButtonHolder.pack()
        val centerX = if (!isPortrait()) stage.width / 2
            else constructionsTable.getUpperWidth().let { it + (stage.width - cityStatsTable.width - it) / 2 }
        razeCityButtonHolder.setPosition(centerX, stage.height - 20f, Align.top)
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
        mapScrollPane.actor = tileMapGroup
        mapScrollPane.setSize(stage.width, stage.height)
        mapScrollPane.setOrigin(stage.width / 2, stage.height / 2)
        mapScrollPane.center(stage)
        stage.addActor(mapScrollPane)

        mapScrollPane.layout() // center scrolling
        mapScrollPane.scrollPercentX = 0.5f
        mapScrollPane.scrollPercentY = 0.5f
        mapScrollPane.updateVisualScroll()
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
