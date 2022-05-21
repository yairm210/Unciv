package com.unciv.ui.cityscreen

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.automation.Automation
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.IConstruction
import com.unciv.logic.city.INonPerpetualConstruction
import com.unciv.logic.map.TileInfo
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.map.TileGroupMap
import com.unciv.ui.popup.ToastPopup
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.utils.*

class CityScreen(
    internal val city: CityInfo,
    initSelectedConstruction: IConstruction? = null,
    initSelectedTile: TileInfo? = null
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

    /** Displays reset locks button - sits on BOT RIGHT */
    private var resetCitizensButtonHolder = Table()

    /** Displays reset locks button - sits on BOT RIGHT */
    private var citizenManagementButtonHolder = Table()

    /** Displays city stats info */
    private var cityStatsTable = CityStatsTable(this)

    /** Displays tile info, alternate with selectedConstructionTable - sits on BOTTOM RIGHT */
    private var tileTable = CityScreenTileTable(this)

    /** Displays selected construction info, alternate with tileTable - sits on BOTTOM RIGHT */
    private var selectedConstructionTable = ConstructionInfoTable(this)

    /** Displays selected construction info, alternate with tileTable - sits on BOTTOM RIGHT */
    private var citizenManagementTable = CitizenManagementTable(this)
    var citizenManagementVisible = false

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

    /** Support for [UniqueType.CreatesOneImprovement] - need user to pick a tile */
    class PickTileForImprovementData (
        val building: Building,
        val improvement: TileImprovement,
        val isBuying: Boolean,
        val buyStat: Stat
    )

    // The following fields control what the user selects
    var selectedConstruction: IConstruction? = initSelectedConstruction
        private set
    var selectedTile: TileInfo? = initSelectedTile
        private set
    /** If set, we are waiting for the user to pick a tile for [UniqueType.CreatesOneImprovement] */
    var pickTileData: PickTileForImprovementData? = null
    /** A [Building] with [UniqueType.CreatesOneImprovement] has been selected _in the queue_: show the tile it will place the improvement on */
    private var selectedQueueEntryTargetTile: TileInfo? = null
    /** Cached city.expansion.chooseNewTileToOwn() */
    // val should be OK as buying tiles is what changes this, and that would re-create the whole CityScreen
    private val nextTileToOwn = city.expansion.chooseNewTileToOwn()

    init {
        onBackButtonClicked { game.setWorldScreen() }
        UncivGame.Current.settings.addCompletedTutorialTask("Enter city screen")

        addTiles()

        //stage.setDebugTableUnderMouse(true)
        stage.addActor(cityStatsTable)
        val resetCitizensButton = "Reset Citizens".toTextButton()
        resetCitizensButton.labelCell.pad(5f)
        resetCitizensButton.onClick { city.reassignPopulation(resetLocked = true); update() }
        resetCitizensButtonHolder.add(resetCitizensButton)
        resetCitizensButtonHolder.pack()
        stage.addActor(resetCitizensButtonHolder)
        val citizenManagementButton = "Citizen Management".toTextButton()
        citizenManagementButton.labelCell.pad(5f)
        citizenManagementButton.onClick {
            clearSelection()
            citizenManagementVisible = true
            update()
        }
        citizenManagementButtonHolder.add(citizenManagementButton)
        citizenManagementButtonHolder.pack()
        stage.addActor(citizenManagementButtonHolder)
        constructionsTable.addActorsToStage()
        stage.addActor(cityInfoTable)
        stage.addActor(selectedConstructionTable)
        stage.addActor(tileTable)
        stage.addActor(citizenManagementTable)
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
        citizenManagementTable.update(citizenManagementVisible)
        citizenManagementTable.setPosition(stage.width - posFromEdge, posFromEdge, Align.bottomRight)
        if (selectedTile == null && selectedConstruction == null && !citizenManagementVisible)
            resetCitizensButtonHolder.setPosition(stage.width - posFromEdge,
                    posFromEdge, Align.bottomRight)
        else if (selectedConstruction != null)
            resetCitizensButtonHolder.setPosition(stage.width - posFromEdge,
                    posFromEdge + selectedConstructionTable.height + 10f, Align.bottomRight)
        else if (selectedTile != null)
            resetCitizensButtonHolder.setPosition(stage.width - posFromEdge,
                    posFromEdge + tileTable.height + 10f, Align.bottomRight)
        else
            resetCitizensButtonHolder.setPosition(stage.width - posFromEdge,
                    posFromEdge + citizenManagementTable.height + 10f, Align.bottomRight)
        citizenManagementButtonHolder.isVisible = !citizenManagementVisible
        citizenManagementButtonHolder.setPosition(stage.width - posFromEdge,
                posFromEdge + resetCitizensButtonHolder.y + resetCitizensButtonHolder.height + 10f, Align.bottomRight)
            
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
        fun isExistingImprovementValuable(tileInfo: TileInfo, improvementToPlace: TileImprovement): Boolean {
            if (tileInfo.improvement == null) return false
            val civInfo = city.civInfo
            val existingStats = tileInfo.getImprovementStats(tileInfo.getTileImprovement()!!, civInfo, city)
            val replacingStats = tileInfo.getImprovementStats(improvementToPlace, civInfo, city)
            return Automation.rankStatsValue(existingStats, civInfo) > Automation.rankStatsValue(replacingStats, civInfo)
        }
        fun getPickImprovementColor(tileInfo: TileInfo): Pair<Color, Float> {
            val improvementToPlace = pickTileData!!.improvement
            return when {
                tileInfo.isMarkedForCreatesOneImprovement() -> Color.BROWN to 0.7f
                !tileInfo.canBuildImprovement(improvementToPlace, city.civInfo) -> Color.RED to 0.4f
                isExistingImprovementValuable(tileInfo, improvementToPlace) -> Color.ORANGE to 0.5f
                tileInfo.improvement != null -> Color.YELLOW to 0.6f
                tileInfo.turnsToImprovement > 0 -> Color.YELLOW to 0.6f
                else -> Color.GREEN to 0.5f
            }
        }
        for (tileGroup in tileGroups) {
            tileGroup.update()
            tileGroup.hideHighlight()
            when {
                tileGroup.tileInfo == nextTileToOwn -> {
                    tileGroup.showHighlight(Color.PURPLE)
                    tileGroup.setColor(0f, 0f, 0f, 0.7f)
                }
                /** Support for [UniqueType.CreatesOneImprovement] */
                tileGroup.tileInfo == selectedQueueEntryTargetTile ->
                    tileGroup.showHighlight(Color.BROWN, 0.7f)
                pickTileData != null && city.tiles.contains(tileGroup.tileInfo.position) ->
                    getPickImprovementColor(tileGroup.tileInfo).run { tileGroup.showHighlight(first, second) }
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
                .filter { cityInfo.civInfo.exploredTiles.contains(it.position) }
                .map { CityTileGroup(cityInfo, it, tileSetStrings) }

        for (tileGroup in cityTileGroups) {
            tileGroup.onClick {
                tileGroupOnClick(tileGroup, cityInfo)
            }
            tileGroups.add(tileGroup)
        }

        val tilesToUnwrap = ArrayList<CityTileGroup>()
        for (tileGroup in tileGroups) {
            val xDifference = cityInfo.getCenterTile().position.x - tileGroup.tileInfo.position.x
            val yDifference = cityInfo.getCenterTile().position.y - tileGroup.tileInfo.position.y
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

    private fun tileGroupOnClick(tileGroup: CityTileGroup, cityInfo: CityInfo) {
        if (cityInfo.isPuppet) return
        val tileInfo = tileGroup.tileInfo

        /** [UniqueType.CreatesOneImprovement] support - select tile for improvement */
        if (pickTileData != null) {
            val pickTileData = this.pickTileData!!
            this.pickTileData = null
            val improvement = pickTileData.improvement
            if (tileInfo.canBuildImprovement(improvement, cityInfo.civInfo)) {
                if (pickTileData.isBuying) {
                    constructionsTable.askToBuyConstruction(pickTileData.building, pickTileData.buyStat, tileInfo)
                } else {
                    // This way to store where the improvement a CreatesOneImprovement Building will create goes
                    // might get a bit fragile if several buildings constructing the same improvement type
                    // were to be allowed in the queue - or a little nontransparent to the user why they
                    // won't reorder - maybe one day redesign to have the target tiles attached to queue entries.
                    tileInfo.markForCreatesOneImprovement(improvement.name)
                    cityInfo.cityConstructions.addToQueue(pickTileData.building.name)
                }
            }
            update()
            return
        }

        selectTile(tileInfo)
        if (tileGroup.isWorkable && canChangeState) {
            if (!tileInfo.providesYield() && cityInfo.population.getFreePopulation() > 0) {
                cityInfo.workedTiles.add(tileInfo.position)
                cityInfo.lockedTiles.add(tileInfo.position)
                game.settings.addCompletedTutorialTask("Reassign worked tiles")
            } else if (tileInfo.isWorked()) {
                cityInfo.workedTiles.remove(tileInfo.position)
                cityInfo.lockedTiles.remove(tileInfo.position)
            }
            cityInfo.cityStats.update()
        }
        update()
    }

    fun selectConstruction(name: String) {
        selectConstruction(city.cityConstructions.getConstruction(name))
    }
    fun selectConstruction(newConstruction: IConstruction) {
        selectedConstruction = newConstruction
        if (newConstruction is Building && newConstruction.hasCreateOneImprovementUnique()) {
            val improvement = newConstruction.getImprovementToCreate(city.getRuleset())
            selectedQueueEntryTargetTile = if (improvement == null) null
                else city.cityConstructions.getTileForImprovement(improvement.name)
        } else {
            selectedQueueEntryTargetTile = null
            pickTileData = null
        }
        selectedTile = null
        citizenManagementVisible = false
    }
    private fun selectTile(newTile: TileInfo?) {
        selectedConstruction = null
        selectedQueueEntryTargetTile = null
        pickTileData = null
        citizenManagementVisible = false
        selectedTile = newTile
    }
    fun clearSelection() = selectTile(null)

    fun startPickTileForCreatesOneImprovement(construction: Building, stat: Stat, isBuying: Boolean) {
        val improvement = construction.getImprovementToCreate(city.getRuleset()) ?: return
        pickTileData = PickTileForImprovementData(construction, improvement, isBuying, stat)
        updateTileGroups()
        ToastPopup("Please select a tile for this building's [${improvement.name}]", this)
    }
    fun stopPickTileForCreatesOneImprovement() {
        if (pickTileData == null) return
        pickTileData = null
        updateTileGroups()
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
