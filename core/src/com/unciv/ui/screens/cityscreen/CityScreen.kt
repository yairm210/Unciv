package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.automation.Automation
import com.unciv.logic.city.City
import com.unciv.logic.city.IConstruction
import com.unciv.logic.map.tile.Tile
import com.unciv.models.TutorialTrigger
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.audio.CityAmbiencePlayer
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.components.tilegroups.TileGroupMap
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.components.tilegroups.CityTileGroup
import com.unciv.ui.components.tilegroups.CityTileState
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.components.KeyCharAndCode
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.keyShortcuts
import com.unciv.ui.components.extensions.onActivation
import com.unciv.ui.components.extensions.onClick
import com.unciv.ui.components.extensions.packIfNeeded
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.screens.worldscreen.WorldScreen

class CityScreen(
    internal val city: City,
    initSelectedConstruction: IConstruction? = null,
    initSelectedTile: Tile? = null,
    internal val isAnnexPreview: Boolean = false
): BaseScreen(), RecreateOnResize {
    companion object {
        /** Distance from stage edges to floating widgets */
        const val posFromEdge = 5f

        /** Size of the decoration icons shown besides the raze button */
        const val wltkIconSize = 40f
    }

    /** Toggles or adds/removes all state changing buttons */
    private val canChangeState = GUI.isAllowedChangeState() && !isAnnexPreview

    // Clockwise from the top-left

    /** Displays current production, production queue and available productions list
     *  Not a widget, but manages two: construction queue + buy buttons
     *  in a Table holder on TOP LEFT, and available constructions in a ScrollPane BOTTOM LEFT.
     */
    private var constructionsTable = CityConstructionsTable(this)

    /** Displays raze city button - sits on TOP CENTER */
    private var razeCityButtonHolder = Table()

    /** Displays city stats, population management, religion, built buildings info - TOP RIGHT */
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
        keyShortcuts.add(KeyCharAndCode.BACK)
        onActivation {
            exit()
        }
    }


    /** Holds City tiles group*/
    private var tileGroups = ArrayList<CityTileGroup>()

    /** The ScrollPane for the background map view of the city surroundings */
    private val mapScrollPane = CityMapHolder()

    /** Support for [UniqueType.CreatesOneImprovement] - need user to pick a tile */
    class PickTileForImprovementData (
        val building: Building,
        val improvement: TileImprovement,
        val isBuying: Boolean,
        val buyStat: Stat
    )

    // The following fields control what the user selects
    internal var selectedConstruction: IConstruction? = initSelectedConstruction
        private set
    private var selectedTile: Tile? = initSelectedTile

    /** If set, we are waiting for the user to pick a tile for [UniqueType.CreatesOneImprovement] */
    private var pickTileData: PickTileForImprovementData? = null
    /** A [Building] with [UniqueType.CreatesOneImprovement] has been selected _in the queue_: show the tile it will place the improvement on */
    private var selectedQueueEntryTargetTile: Tile? = null
    /** Cached city.expansion.chooseNewTileToOwn() */
    // val should be OK as buying tiles is what changes this, and that would re-create the whole CityScreen
    private val nextTileToOwn = city.expansion.chooseNewTileToOwn()

    private val cityAmbiencePlayer = CityAmbiencePlayer(city)

    init {
        if (city.isWeLoveTheKingDayActive() && UncivGame.Current.settings.citySoundsVolume > 0) {
            SoundPlayer.play(UncivSound("WLTK"))
        }

        UncivGame.Current.settings.addCompletedTutorialTask("Enter city screen")

        addTiles()

        //stage.setDebugTableUnderMouse(true)
        stage.addActor(cityStatsTable)
        constructionsTable.addActorsToStage()
        stage.addActor(selectedConstructionTable)
        stage.addActor(tileTable)
        stage.addActor(cityPickerTable)  // add late so it's top in Z-order and doesn't get covered in cramped portrait
        stage.addActor(exitCityButton)
        update()

        if (!isAnnexPreview) {
            globalShortcuts.add(Input.Keys.LEFT) { page(-1) }
            globalShortcuts.add(Input.Keys.RIGHT) { page(1) }
        }
    }

    internal fun update() {
        // Recalculate Stats
        city.cityStats.update()

        constructionsTable.isVisible = true
        constructionsTable.update(selectedConstruction)

        // Bottom right: Tile or selected construction info
        tileTable.update(selectedTile)
        tileTable.setPosition(stage.width - posFromEdge, posFromEdge, Align.bottomRight)
        selectedConstructionTable.update(selectedConstruction)
        selectedConstructionTable.setPosition(stage.width - posFromEdge, posFromEdge, Align.bottomRight)

        // In portrait mode only: calculate already occupied horizontal space
        val rightMargin = when {
            !isPortrait() -> 0f
            selectedTile != null -> tileTable.packIfNeeded().width
            selectedConstruction != null -> selectedConstructionTable.packIfNeeded().width
            else -> posFromEdge
        }
        val leftMargin = when {
            !isPortrait() -> 0f
            else -> constructionsTable.getLowerWidth()
        }

        // Bottom center: Name, paging, exit city button
        val centeredX = (stage.width - leftMargin - rightMargin) / 2 + leftMargin
        exitCityButton.setPosition(centeredX, 10f, Align.bottom)
        cityPickerTable.update()
        cityPickerTable.setPosition(centeredX, exitCityButton.top + 10f, Align.bottom)

        // Top right of screen: Stats / Specialists
        var statsHeight = stage.height - posFromEdge * 2
        if (selectedTile != null)
            statsHeight -= tileTable.height + 10f
        if (selectedConstruction != null)
            statsHeight -= selectedConstructionTable.height + 10f
        cityStatsTable.update(statsHeight)
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

    internal fun canCityBeChanged(): Boolean {
        return canChangeState && !city.isPuppet
    }

    private fun updateTileGroups() {
        val cityUniqueCache = LocalUniqueCache()
        fun isExistingImprovementValuable(tile: Tile, improvementToPlace: TileImprovement): Boolean {
            if (tile.improvement == null) return false
            val civInfo = city.civ
            val existingStats = tile.stats.getImprovementStats(
                tile.getTileImprovement()!!,
                civInfo,
                city,
                cityUniqueCache
            )
            val replacingStats = tile.stats.getImprovementStats(
                improvementToPlace,
                civInfo,
                city,
                cityUniqueCache
            )
            return Automation.rankStatsValue(existingStats, civInfo) > Automation.rankStatsValue(replacingStats, civInfo)
        }

        fun getPickImprovementColor(tile: Tile): Pair<Color, Float> {
            val improvementToPlace = pickTileData!!.improvement
            return when {
                tile.isMarkedForCreatesOneImprovement() -> Color.BROWN to 0.7f
                !tile.improvementFunctions.canBuildImprovement(improvementToPlace, city.civ) -> Color.RED to 0.4f
                isExistingImprovementValuable(tile, improvementToPlace) -> Color.ORANGE to 0.5f
                tile.improvement != null -> Color.YELLOW to 0.6f
                tile.turnsToImprovement > 0 -> Color.YELLOW to 0.6f
                else -> Color.GREEN to 0.5f
            }
        }
        for (tileGroup in tileGroups) {
            tileGroup.update()
            tileGroup.layerMisc.removeHexOutline()

            if (tileGroup.tileState == CityTileState.BLOCKADED)
                displayTutorial(TutorialTrigger.CityTileBlockade)

            when {
                tileGroup.tile == nextTileToOwn ->
                    tileGroup.layerMisc.addHexOutline(colorFromRGB(200, 20, 220))
                /** Support for [UniqueType.CreatesOneImprovement] */
                tileGroup.tile == selectedQueueEntryTargetTile ->
                    tileGroup.layerMisc.addHexOutline(Color.BROWN)
                pickTileData != null && city.tiles.contains(tileGroup.tile.position) ->
                    getPickImprovementColor(tileGroup.tile).run {
                        tileGroup.layerMisc.addHexOutline(first.cpy().apply { this.a = second }) }
            }
        }
    }

    private fun updateAnnexAndRazeCityButton() {
        razeCityButtonHolder.clear()
        if (isAnnexPreview) return

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
                .filter { cityInfo.civ.hasExplored(it) }
                .map { CityTileGroup(cityInfo, it, tileSetStrings) }

        for (tileGroup in cityTileGroups) {
            tileGroup.onClick { tileGroupOnClick(tileGroup, cityInfo) }
            tileGroup.layerMisc.onClick { tileWorkedIconOnClick(tileGroup, cityInfo) }
            tileGroups.add(tileGroup)
        }

        val tilesToUnwrap = mutableSetOf<CityTileGroup>()
        for (tileGroup in tileGroups) {
            val xDifference = cityInfo.getCenterTile().position.x - tileGroup.tile.position.x
            val yDifference = cityInfo.getCenterTile().position.y - tileGroup.tile.position.y
            //if difference is bigger than 5 the tileGroup we are looking for is on the other side of the map
            if (xDifference > 5 || xDifference < -5 || yDifference > 5 || yDifference < -5) {
                //so we want to unwrap its position
                tilesToUnwrap.add(tileGroup)
            }
        }

        val tileMapGroup = TileGroupMap(mapScrollPane, tileGroups, tileGroupsToUnwrap = tilesToUnwrap)
        mapScrollPane.actor = tileMapGroup
        mapScrollPane.setSize(stage.width, stage.height)
        stage.addActor(mapScrollPane)

        mapScrollPane.layout() // center scrolling
        mapScrollPane.scrollPercentX = 0.5f
        mapScrollPane.scrollPercentY = 0.5f
        mapScrollPane.updateVisualScroll()
    }

    private fun tileWorkedIconOnClick(tileGroup: CityTileGroup, city: City) {

        if (!canCityBeChanged()) return
        val tile = tileGroup.tile

        // Cycling as: Not-worked -> Worked -> Locked -> Not-worked
        if (tileGroup.tileState == CityTileState.WORKABLE) {
            if (!tile.providesYield() && city.population.getFreePopulation() > 0) {
                city.workedTiles.add(tile.position)
                game.settings.addCompletedTutorialTask("Reassign worked tiles")
            } else if (tile.isWorked() && !tile.isLocked()) {
                city.lockedTiles.add(tile.position)
            } else if (tile.isLocked()) {
                city.workedTiles.remove(tile.position)
                city.lockedTiles.remove(tile.position)
            }
            city.cityStats.update()
            update()

        } else if (tileGroup.tileState == CityTileState.PURCHASABLE) {

            val price = city.expansion.getGoldCostOfTile(tile)
            val purchasePrompt = "Currently you have [${city.civ.gold}] [Gold].".tr() + "\n\n" +
                    "Would you like to purchase [Tile] for [$price] [${Stat.Gold.character}]?".tr()
            ConfirmPopup(
                this,
                purchasePrompt,
                "Purchase",
                true,
                restoreDefault = { update() }
            ) {
                SoundPlayer.play(UncivSound.Coin)
                city.expansion.buyTile(tile)
                // preselect the next tile on city screen rebuild so bulk buying can go faster
                UncivGame.Current.replaceCurrentScreen(CityScreen(city, initSelectedTile = city.expansion.chooseNewTileToOwn()))
            }.open()
        }
    }

    private fun tileGroupOnClick(tileGroup: CityTileGroup, city: City) {
        if (city.isPuppet) return
        val tileInfo = tileGroup.tile

        /** [UniqueType.CreatesOneImprovement] support - select tile for improvement */
        if (pickTileData != null) {
            val pickTileData = this.pickTileData!!
            this.pickTileData = null
            val improvement = pickTileData.improvement
            if (tileInfo.improvementFunctions.canBuildImprovement(improvement, city.civ)) {
                if (pickTileData.isBuying) {
                    constructionsTable.askToBuyConstruction(pickTileData.building, pickTileData.buyStat, tileInfo)
                } else {
                    // This way to store where the improvement a CreatesOneImprovement Building will create goes
                    // might get a bit fragile if several buildings constructing the same improvement type
                    // were to be allowed in the queue - or a little nontransparent to the user why they
                    // won't reorder - maybe one day redesign to have the target tiles attached to queue entries.
                    tileInfo.improvementFunctions.markForCreatesOneImprovement(improvement.name)
                    city.cityConstructions.addToQueue(pickTileData.building.name)
                }
            }
            update()
            return
        }

        selectTile(tileInfo)
        update()
    }

    internal fun selectConstruction(name: String) {
        selectConstruction(city.cityConstructions.getConstruction(name))
    }
    internal fun selectConstruction(newConstruction: IConstruction) {
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
    }
    private fun selectTile(newTile: Tile?) {
        selectedConstruction = null
        selectedQueueEntryTargetTile = null
        pickTileData = null
        selectedTile = newTile
    }
    internal fun clearSelection() = selectTile(null)

    internal fun startPickTileForCreatesOneImprovement(construction: Building, stat: Stat, isBuying: Boolean) {
        val improvement = construction.getImprovementToCreate(city.getRuleset()) ?: return
        pickTileData = PickTileForImprovementData(construction, improvement, isBuying, stat)
        updateTileGroups()
        ToastPopup("Please select a tile for this building's [${improvement.name}]", this)
    }
    internal fun stopPickTileForCreatesOneImprovement() {
        if (pickTileData == null) return
        pickTileData = null
        updateTileGroups()
    }

    private fun exit() {
        val newScreen = game.popScreen()
        if (newScreen is WorldScreen) {
            newScreen.mapHolder.setCenterPosition(city.location, immediately = true)
            newScreen.bottomUnitTable.selectUnit()
        }
    }

    internal fun page(delta: Int) {
        val civInfo = city.civ
        val numCities = civInfo.cities.size
        if (numCities == 0) return
        val indexOfCity = civInfo.cities.indexOf(city)
        val indexOfNextCity = (indexOfCity + delta + numCities) % numCities
        val newCityScreen = CityScreen(civInfo.cities[indexOfNextCity])
        newCityScreen.update()
        game.replaceCurrentScreen(newCityScreen)
    }

    override fun recreate(): BaseScreen = CityScreen(city, isAnnexPreview = isAnnexPreview)

    override fun dispose() {
        cityAmbiencePlayer.dispose()
        super.dispose()
    }
}
