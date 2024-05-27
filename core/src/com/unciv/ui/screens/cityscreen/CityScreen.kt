package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.logic.automation.Automation
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import com.unciv.models.TutorialTrigger
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.audio.CityAmbiencePlayer
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.disable
import com.unciv.ui.components.extensions.packIfNeeded
import com.unciv.ui.components.extensions.toTextButton
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyShortcutDispatcherVeto
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onDoubleClick
import com.unciv.ui.components.tilegroups.CityTileGroup
import com.unciv.ui.components.tilegroups.CityTileState
import com.unciv.ui.components.tilegroups.TileGroupMap
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.ConfirmPopup
import com.unciv.ui.popups.ToastPopup
import com.unciv.ui.popups.closeAllPopups
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.RecreateOnResize
import com.unciv.ui.screens.worldscreen.WorldScreen

class CityScreen(
    internal val city: City,
    initSelectedConstruction: IConstruction? = null,
    initSelectedTile: Tile? = null,
    /** City ambience sound player proxies can be passed from one CityScreen instance to the next
     *  to avoid premature stops or rewinds. Only the fresh CityScreen from WorldScreen or Overview
     *  will instantiate a new CityAmbiencePlayer and start playing. */
    ambiencePlayer: CityAmbiencePlayer? = null
): BaseScreen(), RecreateOnResize {
    companion object {
        /** Distance from stage edges to floating widgets */
        const val posFromEdge = 5f

        /** Size of the decoration icons shown besides the raze button */
        const val wltkIconSize = 40f
    }

    private val selectedCiv: Civilization = GUI.getWorldScreen().selectedCiv

    private val isSpying = selectedCiv.gameInfo.isEspionageEnabled() && selectedCiv != city.civ

    /**
     * This is the regular civ city list if we are not spying, if we are spying then it is every foreign city that our spies are in
     */
    val viewableCities = if (isSpying) selectedCiv.espionageManager.getCitiesWithOurSpies()
        .filter { it.civ !=  GUI.getWorldScreen().selectedCiv }
    else city.civ.cities

    /** Toggles or adds/removes all state changing buttons */
    val canChangeState = GUI.isAllowedChangeState() && !isSpying

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

    private var cityVerticalTabs = CityVerticalTabs(this)

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
    var selectedConstruction: IConstruction? = initSelectedConstruction
        private set
    var selectedTile: Tile? = initSelectedTile
        private set
    /** If set, we are waiting for the user to pick a tile for [UniqueType.CreatesOneImprovement] */
    var pickTileData: PickTileForImprovementData? = null
    /** A [Building] with [UniqueType.CreatesOneImprovement] has been selected _in the queue_: show the tile it will place the improvement on */
    private var selectedQueueEntryTargetTile: Tile? = null
    /** Cached city.expansion.chooseNewTileToOwn() */
    // val should be OK as buying tiles is what changes this, and that would re-create the whole CityScreen
    private val nextTileToOwn = city.expansion.chooseNewTileToOwn()

    private var cityAmbiencePlayer: CityAmbiencePlayer?  = ambiencePlayer ?: CityAmbiencePlayer(city)

    init {
        if (city.isWeLoveTheKingDayActive() && UncivGame.Current.settings.citySoundsVolume > 0) {
            SoundPlayer.play(UncivSound("WLTK"))
        }

        UncivGame.Current.settings.addCompletedTutorialTask("Enter city screen")

        addTiles()

        //stage.setDebugTableUnderMouse(true)
        stage.addActor(cityStatsTable)
        // If we are spying then we shoulden't be able to see their construction screen.
        constructionsTable.addActorsToStage()
        stage.addActor(selectedConstructionTable)
        stage.addActor(tileTable)
        stage.addActor(cityPickerTable)  // add late so it's top in Z-order and doesn't get covered in cramped portrait
        stage.addActor(exitCityButton)
        if (isPortrait()) {
            stage.addActor(cityVerticalTabs)
            selectCityPanel()
        }

        update()

        globalShortcuts.add(KeyboardBinding.PreviousCity) { page(-1) }
        globalShortcuts.add(KeyboardBinding.NextCity) { page(1) }
    }

    override fun getCivilopediaRuleset() = selectedCiv.gameInfo.ruleset

    internal fun update() {
        // Recalculate Stats
        city.cityStats.update()

        if (!isPortrait())
            constructionsTable.isVisible = !isSpying
        constructionsTable.update(selectedConstruction)

        updateWithoutConstructionAndMap()

        // Rest of screen: Map of surroundings
        updateTileGroups()
        if (isPortrait()) mapScrollPane.apply {
            // center scrolling so city center sits more to the bottom right
            scrollX = (maxX - constructionsTable.getLowerWidth() - posFromEdge) / 2
            scrollY = (maxY - cityStatsTable.packIfNeeded().height - posFromEdge + cityPickerTable.top) / 2
            updateVisualScroll()
        }
    }

    internal fun updateWithoutConstructionAndMap() {
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
        if (isPortrait()) {
            cityVerticalTabs.setPosition(stage.width / 2, cityVerticalTabs.height / 2, Align.bottom)
        }
        val exitCityButtonHeight = if (isPortrait()) 60f else 20f

        exitCityButton.setPosition(stage.width / 2, exitCityButtonHeight, Align.bottom)
        cityPickerTable.update()
        cityPickerTable.setPosition(stage.width / 2, exitCityButton.top + 10f, Align.bottom)

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
    }

    fun canCityBeChanged(): Boolean {
        return canChangeState && !city.isPuppet
    }

    private fun updateTileGroups() {
        val cityUniqueCache = LocalUniqueCache()
        fun isExistingImprovementValuable(tile: Tile): Boolean {
            if (tile.improvement == null) return false
            val civInfo = city.civ

            val statDiffForNewImprovement = tile.stats.getStatDiffForImprovement(
                tile.getTileImprovement()!!,
                civInfo,
                city,
                cityUniqueCache
            )

            // If stat diff for new improvement is negative/zero utility, current improvement is valuable
            return Automation.rankStatsValue(statDiffForNewImprovement, civInfo) <= 0
        }

        fun getPickImprovementColor(tile: Tile): Pair<Color, Float> {
            val improvementToPlace = pickTileData!!.improvement
            return when {
                tile.isMarkedForCreatesOneImprovement() -> Color.BROWN to 0.7f
                !tile.improvementFunctions.canBuildImprovement(improvementToPlace, city.civ) -> Color.RED to 0.4f
                isExistingImprovementValuable(tile) -> Color.ORANGE to 0.5f
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

        fun addWltkIcon(name: String, apply: Image.()->Unit = {}) =
            razeCityButtonHolder.add(ImageGetter.getImage(name).apply(apply)).size(wltkIconSize)

        if (city.isWeLoveTheKingDayActive()) {
            addWltkIcon("OtherIcons/WLTK LR") { color = Color.GOLD }
            addWltkIcon("OtherIcons/WLTK 1") { color = Color.FIREBRICK }.padRight(10f)
        }

        val canAnnex = !city.civ.hasUnique(UniqueType.MayNotAnnexCities)
        if (city.isPuppet && canAnnex) {
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
            if (!canChangeState || !city.canBeDestroyed() || !canAnnex) {
                razeCityButton.disable()
            }

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
        val tileSetStrings = TileSetStrings()
        val cityTileGroups = city.getCenterTile().getTilesInDistance(5)
                .filter { selectedCiv.hasExplored(it) }
                .map { CityTileGroup(city, it, tileSetStrings) }

        for (tileGroup in cityTileGroups) {
            tileGroup.onClick { tileGroupOnClick(tileGroup, city) }
            tileGroup.layerMisc.onClick { tileWorkedIconOnClick(tileGroup, city) }
            tileGroup.layerMisc.onDoubleClick { tileWorkedIconDoubleClick(tileGroup, city) }
            tileGroups.add(tileGroup)
        }

        val tilesToUnwrap = mutableSetOf<CityTileGroup>()
        for (tileGroup in tileGroups) {
            val xDifference = city.getCenterTile().position.x - tileGroup.tile.position.x
            val yDifference = city.getCenterTile().position.y - tileGroup.tile.position.y
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

    // We contain a map...
    override fun getShortcutDispatcherVetoer() = KeyShortcutDispatcherVeto.createTileGroupMapDispatcherVetoer()

    private fun tileWorkedIconOnClick(tileGroup: CityTileGroup, city: City) {

        if (!canChangeState || city.isPuppet) return
        val tile = tileGroup.tile

        // Cycling as: Not-worked -> Worked  -> Not-worked
        if (tileGroup.tileState == CityTileState.WORKABLE) {
            if (!tile.providesYield() && city.population.getFreePopulation() > 0) {
                city.workedTiles.add(tile.position)
                game.settings.addCompletedTutorialTask("Reassign worked tiles")
            } else {
                city.workedTiles.remove(tile.position)
                city.lockedTiles.remove(tile.position)
            }
            city.cityStats.update()
            update()

        } else if (tileGroup.tileState == CityTileState.PURCHASABLE) {
            askToBuyTile(tile)
        }
    }

    /** Ask whether user wants to buy [selectedTile] for gold.
     *
     * Used from onClick and keyboard dispatch, thus only minimal parameters are passed,
     * and it needs to do all checks and the sound as appropriate.
     */
    internal fun askToBuyTile(selectedTile: Tile) {
        // These checks are redundant for the onClick action, but not for the keyboard binding
        if (!canChangeState || !city.expansion.canBuyTile(selectedTile)) return
        val goldCostOfTile = city.expansion.getGoldCostOfTile(selectedTile)
        if (!city.civ.hasStatToBuy(Stat.Gold, goldCostOfTile)) return

        closeAllPopups()

        val purchasePrompt = "Currently you have [${city.civ.gold}] [Gold].".tr() + "\n\n" +
            "Would you like to purchase [Tile] for [$goldCostOfTile] [${Stat.Gold.character}]?".tr()
        ConfirmPopup(
            this,
            purchasePrompt,
            "Purchase",
            true,
            restoreDefault = { update() }
        ) {
            SoundPlayer.play(UncivSound.Coin)
            city.expansion.buyTile(selectedTile)
            // preselect the next tile on city screen rebuild so bulk buying can go faster
            UncivGame.Current.replaceCurrentScreen(CityScreen(city, initSelectedTile = city.expansion.chooseNewTileToOwn()))
        }.open()
    }


    private fun tileWorkedIconDoubleClick(tileGroup: CityTileGroup, city: City) {
        if (!canChangeState || city.isPuppet || tileGroup.tileState != CityTileState.WORKABLE) return
        val tile = tileGroup.tile

        // Double-click should lead to locked tiles - both for unworked AND worked tiles

        if (!tile.isWorked()) // If not worked, try to work it first
            tileWorkedIconOnClick(tileGroup, city)

        if (tile.isWorked())
            city.lockedTiles.add(tile.position)

        update()
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

    /** Convenience shortcut to [CivConstructions.hasFreeBuilding][com.unciv.logic.civilization.CivConstructions.hasFreeBuilding], nothing more */
    internal fun hasFreeBuilding(building: Building) =
        city.civ.civConstructions.hasFreeBuilding(city, building)

    fun selectConstruction(name: String) {
        selectConstruction(city.cityConstructions.getConstruction(name))
    }
    fun selectConstruction(newConstruction: IConstruction) {
        selectedConstruction = newConstruction
        if (newConstruction is Building && newConstruction.hasCreateOneImprovementUnique()) {
            val improvement = newConstruction.getImprovementToCreate(city.getRuleset(), city.civ)
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
    fun clearSelection() = selectTile(null)

    fun startPickTileForCreatesOneImprovement(construction: Building, stat: Stat, isBuying: Boolean) {
        val improvement = construction.getImprovementToCreate(city.getRuleset(), city.civ) ?: return
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
        val newScreen = game.popScreen()
        if (newScreen is WorldScreen) {
            newScreen.mapHolder.setCenterPosition(city.location, immediately = true)
            newScreen.bottomUnitTable.selectUnit()
        }
    }

    private fun passOnCityAmbiencePlayer(): CityAmbiencePlayer? {
        val player = cityAmbiencePlayer
        cityAmbiencePlayer = null
        return player
    }

    fun page(delta: Int) {
        // Normal order is create new, then dispose old. But CityAmbiencePlayer delegates to a single instance of MusicController,
        // leading to one extra play followed by a stop for the city ambience sounds. To avoid that, we pass our player on and relinquish control.

        val numCities = viewableCities.size
        if (numCities == 0) return
        val indexOfCity = viewableCities.indexOf(city)
        val indexOfNextCity = (indexOfCity + delta + numCities) % numCities
        val newCityScreen = CityScreen(viewableCities[indexOfNextCity], ambiencePlayer = passOnCityAmbiencePlayer())
        newCityScreen.update()
        game.replaceCurrentScreen(newCityScreen)
    }

    fun selectConstructionPanel() {
        constructionsTable.isVisible = true
        selectedConstructionTable.isVisible = true
        cityStatsTable.isVisible = false
        cityPickerTable.isVisible = false
        exitCityButton.isVisible = false
        razeCityButtonHolder.isVisible = false
    }

    fun selectCityPanel() {
        constructionsTable.isVisible = false
        selectedConstructionTable.isVisible = false
        cityStatsTable.isVisible = false
        cityPickerTable.isVisible = true
        exitCityButton.isVisible = true
        razeCityButtonHolder.isVisible = true
    }

    fun selectCityBuildingsPanel() {
        constructionsTable.isVisible = false
        selectedConstructionTable.isVisible = false
        cityStatsTable.isVisible = true
        cityPickerTable.isVisible = false
        exitCityButton.isVisible = false
        razeCityButtonHolder.isVisible = false
    }

    // Don't use passOnCityAmbiencePlayer here - continuing play on the replacement screen would be nice,
    // but the rapid firing of several resize events will get that un-synced, they would no longer stop on leaving.
    override fun recreate(): BaseScreen = CityScreen(city, selectedConstruction, selectedTile)

    override fun dispose() {
        cityAmbiencePlayer?.dispose()
        super.dispose()
    }
}
