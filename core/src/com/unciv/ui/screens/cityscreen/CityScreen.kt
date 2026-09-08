package com.unciv.ui.screens.cityscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.UncivGame
import com.unciv.models.TutorialTrigger
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.Building
import com.unciv.models.ruleset.IConstruction
import com.unciv.models.ruleset.tile.TileImprovement
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.models.stats.Stat
import com.unciv.models.translations.tr
import com.unciv.ui.audio.CityAmbiencePlayer
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.ParticleEffectMapFireworks
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
import com.unciv.utils.Concurrency
import com.unciv.view.CityView
import com.unciv.view.CivView
import com.unciv.view.TileView
import kotlin.math.max

class CityScreen(
    val cityView: CityView,
    initSelectedConstruction: IConstruction? = null,
    initSelectedTile: TileView? = null,
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

    private val viewingCiv: CivView = cityView.gameView.civView

    internal val isSpying = cityView.isEspionageEnabled() && !cityView.isOwnedByViewer() && !viewingCiv.isSpectator()

    /**
     * This is the regular civ city list if we are not spying, if we are spying then it is every foreign city that our spies are in
     */
    val viewableCities: List<CityView> = cityView.getViewableCities()

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
    var selectedTile: TileView? = initSelectedTile
        private set
    /** If set, we are waiting for the user to pick a tile for [UniqueType.CreatesOneImprovement] */
    var pickTileData: PickTileForImprovementData? = null
    /** A [Building] with [UniqueType.CreatesOneImprovement] has been selected _in the queue_: show the tile it will place the improvement on */
    private var selectedQueueEntryTargetTile: TileView? = null
    var selectedQueueEntry
        get() = constructionsTable.selectedQueueEntry
        set(value) { constructionsTable.selectedQueueEntry = value }
    /** Cached city.expansion.chooseNewTileToOwn() */
    // val should be OK as buying tiles is what changes this, and that would re-create the whole CityScreen
    private val nextTileToOwn = cityView.chooseNewTileToOwn()

    private var cityAmbiencePlayer: CityAmbiencePlayer?  = ambiencePlayer ?: CityAmbiencePlayer(cityView)

    /** Particle effects for WLTK day decoration */
    private val isWLTKday = cityView.isWeLoveTheKingDayActive()
    private val fireworks: ParticleEffectMapFireworks?
    internal var pauseFireworks = false

    init {
        if (isWLTKday && UncivGame.Current.settings.citySoundsVolume > 0) {
            SoundPlayer.play(UncivSound("WLTK"))
        }
        fireworks = if (isWLTKday) ParticleEffectMapFireworks.create(game, mapScrollPane) else null

        UncivGame.Current.settings.addCompletedTutorialTask("Enter city screen")

        addTiles()

        // If we are spying then we shoulden't be able to see their construction screen.
        constructionsTable.addActorsToStage()
        stage.addActor(cityStatsTable)
        stage.addActor(selectedConstructionTable)
        stage.addActor(tileTable)
        stage.addActor(cityPickerTable)  // add late so it's top in Z-order and doesn't get covered in cramped portrait
        stage.addActor(exitCityButton)

        cityView.updateCityStats()
        updateSync() // NOT async since that gives a "visual flash" when entering the city

        globalShortcuts.add(KeyboardBinding.PreviousCity) { page(-1) }
        globalShortcuts.add(KeyboardBinding.NextCity) { page(1) }

        if (isPortrait()) mapScrollPane.apply {
            // center scrolling so city center sits more to the bottom right
            scrollX = (maxX - constructionsTable.getLowerWidth() - posFromEdge) / 2
            scrollY = (maxY - cityStatsTable.packIfNeeded().height - posFromEdge + cityPickerTable.top) / 2
            updateVisualScroll()
        }

        globalShortcuts.add(KeyboardBinding.Civilopedia) { openCivilopedia() }
    }

    override fun getCivilopediaRuleset() = cityView.getRuleset()

    /** Async */
    internal fun updateAsync() {
        Concurrency.run {
            // Recalculate Stats
            cityView.updateCityStats()
            Concurrency.runOnGLThread { updateSync() }
        }
    }
    
    internal fun updateSync(){
        constructionsTable.isVisible = !isSpying
        constructionsTable.update(selectedConstruction)
        updateWithoutConstructionAndMap()

        // Rest of screen: Map of surroundings
        updateTileGroups()
    }

    internal fun updateWithoutConstructionAndMap() {
        // Bottom right: Tile or selected construction info
        tileTable.update(selectedTile)
        tileTable.setPosition(stage.width - posFromEdge, posFromEdge, Align.bottomRight)
        selectedConstructionTable.update(selectedConstruction)
        selectedConstructionTable.setPosition(stage.width - posFromEdge, posFromEdge, Align.bottomRight)

        // In portrait mode only: calculate already occupied horizontal space
        val rightMargin = when {
            !isPortrait() || isCrampedPortrait() -> 0f
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
        updateCityStats()

        // Top center: Annex/Raze button
        updateAnnexAndRazeCityButton()

    }

    private fun updateCityStats() {
        var statsHeight = stage.height - posFromEdge * 2
        if (selectedTile != null)
            statsHeight -= tileTable.top + 10f
        if (selectedConstruction != null)
            statsHeight -= selectedConstructionTable.top + 10f
        cityStatsTable.update(statsHeight)
        cityStatsTable.setPosition(
            stage.width - posFromEdge,
            stage.height - posFromEdge,
            Align.topRight
        )
    }

    fun canCityBeChanged(): Boolean {
        return canChangeState && !cityView.isPuppet()
    }

    private fun updateTileGroups() {
        fun isExistingImprovementValuable(tileView: TileView): Boolean {
            val improvement = tileView.tileImprovement ?: return false
            val statDiffForNewImprovement = cityView.getStatDiffForImprovement(tileView, improvement)
            // If stat diff for new improvement is negative/zero utility, current improvement is valuable
            return cityView.rankStatsValue(statDiffForNewImprovement) <= 0
        }

        fun getPickImprovementColor(tileView: TileView): Pair<Color, Float> {
            val improvementToPlace = pickTileData!!.improvement
            return when {
                tileView.isMarkedForCreatesOneImprovement() -> Color.BROWN to 0.7f
                !cityView.constructions.canPlaceCreateOneImprovementOn(improvementToPlace, tileView) -> Color.RED to 0.4f
                isExistingImprovementValuable(tileView) -> Color.ORANGE to 0.5f
                tileView.improvement != null -> Color.YELLOW to 0.6f
                tileView.turnsToImprovement > 0 -> Color.YELLOW to 0.6f
                else -> Color.GREEN to 0.5f
            }
        }

        for (tileGroup in tileGroups) {
            tileGroup.update(cityView.viewingCiv())
            tileGroup.layerMisc.removeHexOutline()
            if (isSpying) continue // the rest is only for own cities

            if (tileGroup.tileState == CityTileState.BLOCKADED)
                displayTutorial(TutorialTrigger.CityTileBlockade)

            when {
                tileGroup.tileView == nextTileToOwn ->
                    tileGroup.layerMisc.addHexOutline(colorFromRGB(200, 20, 220))
                /** Support for [UniqueType.CreatesOneImprovement] */
                tileGroup.tileView == selectedQueueEntryTargetTile ->
                    tileGroup.layerMisc.addHexOutline(Color.BROWN)
                pickTileData != null && cityView.isOwnedTile(tileGroup.tileView) && cityView.isInRange(tileGroup.tileView) ->
                    getPickImprovementColor(tileGroup.tileView).run {
                        tileGroup.layerMisc.addHexOutline(first.cpy().apply { this.a = second }) }
            }

            if (fireworks != null && tileGroup.tileView.position() == cityView.location)
                fireworks.setActorBounds(tileGroup)
        }
    }

    private fun updateAnnexAndRazeCityButton() {
        razeCityButtonHolder.clear()

        fun addWltkIcon(name: String, apply: Image.()->Unit = {}) =
            razeCityButtonHolder.add(ImageGetter.getImage(name).apply(apply)).size(wltkIconSize)

        if (isWLTKday && fireworks == null) {
            addWltkIcon("OtherIcons/WLTK LR") { color = Color.GOLD }
            addWltkIcon("OtherIcons/WLTK 1") { color = Color.FIREBRICK }.padRight(10f)
        }

        val canAnnex = !cityView.viewingCiv().hasUnique(UniqueType.MayNotAnnexCities)
        if (cityView.isPuppet() && canAnnex) {
            val annexCityButton = "Annex city".toTextButton()
            annexCityButton.labelCell.pad(10f)
            annexCityButton.onClick {
                cityView.tryAnnexCity()
                updateAsync()
            }
            if (!canChangeState) annexCityButton.disable()
            razeCityButtonHolder.add(annexCityButton) //.colspan(cityPickerTable.columns)
        } else if (!cityView.isBeingRazed()) {
            val razeCityButton = "Raze city".toTextButton()
            razeCityButton.labelCell.pad(10f)
            razeCityButton.onClick { cityView.trySetRazing(true); updateAsync() }
            if (!canChangeState || !cityView.canBeDestroyed() || !canAnnex) {
                razeCityButton.disable()
            }

            razeCityButtonHolder.add(razeCityButton) //.colspan(cityPickerTable.columns)
        } else {
            val stopRazingCityButton = "Stop razing city".toTextButton()
            stopRazingCityButton.labelCell.pad(10f)
            stopRazingCityButton.onClick { cityView.trySetRazing(false); updateAsync() }
            if (!canChangeState) stopRazingCityButton.disable()
            razeCityButtonHolder.add(stopRazingCityButton) //.colspan(cityPickerTable.columns)
        }

        if (isWLTKday && fireworks == null) {
            addWltkIcon("OtherIcons/WLTK 2") { color = Color.FIREBRICK }.padLeft(10f)
            addWltkIcon("OtherIcons/WLTK LR") {
                color = Color.GOLD
                scaleX = -scaleX
                originX = wltkIconSize * 0.5f
            }
        }

        razeCityButtonHolder.pack()
        if(isCrampedPortrait()) {
            // cramped portrait: move raze button down to city picker
            val centerX = cityPickerTable.x + cityPickerTable.width / 2 - razeCityButtonHolder.width / 2
            razeCityButtonHolder.setPosition(centerX, cityPickerTable.y + cityPickerTable.height + 10)
            // and also re-position the tooltips, which would otherwise be covered
            tileTable.setPosition(stage.width - posFromEdge, razeCityButtonHolder.top + 10f, Align.bottomRight)
            selectedConstructionTable.setPosition(stage.width - posFromEdge, razeCityButtonHolder.top + 10f, Align.bottomRight)
            updateCityStats() // limit city stats height according to the tooltips
        } else {
            val centerX = if (isPortrait())
                constructionsTable.getUpperWidth().let { it + (stage.width - cityStatsTable.width - it) / 2 }
            else
                stage.width / 2
            razeCityButtonHolder.setPosition(centerX, stage.height - 20f, Align.top)
        }
        stage.addActor(razeCityButtonHolder)
    }

    private fun addTiles() {
        val viewRange = max(cityView.getExpandRange(), cityView.getWorkRange())
        val tileSetStrings = TileSetStrings(cityView.getRuleset(), game.settings)
        val cityTileGroups = cityView.centerTile().getVisibleTilesInDistance(viewRange)
                .filter { viewingCiv.hasExplored(it) }
                .map { CityTileGroup(cityView, it, tileSetStrings, false, isSpying) }

        for (tileGroup in cityTileGroups) {
            tileGroup.onClick { tileGroupOnClick(tileGroup) }
            tileGroup.layerMisc.onWorkedIconClick = {
                tileWorkedIconOnClick(tileGroup)
                tileGroupOnClick(tileGroup)
            }
            tileGroup.layerMisc.onWorkedIconDoubleClick = { tileWorkedIconDoubleClick(tileGroup) }
            tileGroups.add(tileGroup)
        }

        val tilesToUnwrap = mutableSetOf<CityTileGroup>()
        for (tileGroup in tileGroups) {
            val xDifference = cityView.centerTile().position().x - tileGroup.tileView.position().x
            val yDifference = cityView.centerTile().position().y - tileGroup.tileView.position().y
            //if difference is bigger than the expansion range the tileGroup we are looking for is on the other side of the map
            if (xDifference > viewRange || xDifference < -viewRange || yDifference > viewRange || yDifference < -viewRange) {
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

    private fun tileWorkedIconOnClick(tileGroup: CityTileGroup) {

        if (!canChangeState || cityView.isPuppet()) return

        // Cycling as: Not-worked -> Worked  -> Not-worked
        if (tileGroup.tileState == CityTileState.WORKABLE) {
            if (!tileGroup.tileView.providesYield() && cityView.getFreePopulation() > 0) {
                cityView.tryWorkTile(tileGroup.tileView)
                game.settings.addCompletedTutorialTask("Reassign worked tiles")
            } else {
                cityView.tryStopWorkingTile(tileGroup.tileView)
            }
            cityView.updateCityStats()
            updateAsync()

        } else if (tileGroup.tileState == CityTileState.PURCHASABLE) {
            askToBuyTile(tileGroup.tileView)
        }
    }

    /** Ask whether user wants to buy [selectedTile] for gold.
     *
     * Used from onClick and keyboard dispatch, thus only minimal parameters are passed,
     * and it needs to do all checks and the sound as appropriate.
     */
    internal fun askToBuyTile(selectedTile: TileView) {
        // These checks are redundant for the onClick action, but not for the keyboard binding
        if (!canChangeState || !cityView.canBuyTile(selectedTile)) return
        val goldCostOfTile = cityView.getGoldCostOfTile(selectedTile)
        if (!cityView.viewingCiv().hasStatToBuy(Stat.Gold, goldCostOfTile)) return

        closeAllPopups()

        val purchasePrompt = "Currently you have [${cityView.viewingCiv().gold}] [Gold].".tr() + "\n\n" +
            "Would you like to purchase [Tile] for [$goldCostOfTile] [${Stat.Gold.character}]?".tr()
        ConfirmPopup(
            this,
            purchasePrompt,
            "Purchase",
            true,
            restoreDefault = { updateAsync() }
        ) {
            Concurrency.run {
                val success = cityView.tryBuyTile(selectedTile)
                if (!success){
                    updateAsync()
                    return@run
                }
                Concurrency.runOnGLThread {
                    SoundPlayer.play(UncivSound.Coin)
                    // preselect the next tile on city screen rebuild so bulk buying can go faster
                    game.replaceCurrentScreen { CityScreen(cityView, initSelectedTile = cityView.chooseNewTileToOwn()) }
                }
            }
        }.open()
    }


    private fun tileWorkedIconDoubleClick(tileGroup: CityTileGroup) {
        if (!canChangeState || cityView.isPuppet() || tileGroup.tileState != CityTileState.WORKABLE) return

        // Double-click should lead to locked tiles - both for unworked AND worked tiles

        if (!tileGroup.tileView.isWorked()) // If not worked, try to work it first
            tileWorkedIconOnClick(tileGroup)

        if (tileGroup.tileView.isWorked())
            cityView.tryLockTile(tileGroup.tileView)

        updateAsync()
    }

    private fun tileGroupOnClick(tileGroup: CityTileGroup) {
        if (cityView.isPuppet()) return
        val tileInfo = tileGroup.tileView

        /** [UniqueType.CreatesOneImprovement] support - select tile for improvement */
        if (pickTileData != null) {
            val pickTileData = this.pickTileData!!
            this.pickTileData = null
            val improvement = pickTileData.improvement
            if (cityView.constructions.canPlaceCreateOneImprovementOn(improvement, tileInfo)) {

                if (pickTileData.isBuying) {
                    BuyButtonFactory(this).askToBuyConstruction(pickTileData.building, pickTileData.buyStat, tileInfo)
                } else {
                    cityView.tryAddToQueueWithTile(pickTileData.building, tileInfo)
                }
            }
            updateAsync()
            return
        }

        selectTile(tileGroup.tileView)
        updateAsync()
    }

    /** Convenience shortcut to [CivConstructions.hasFreeBuilding][com.unciv.logic.civilization.CivConstructions.hasFreeBuilding], nothing more */
    internal fun hasFreeBuilding(building: Building) = cityView.hasFreeBuilding(building)

    fun selectConstructionFromQueue(index: Int) {
        val constructionName = cityView.constructions.constructionQueue.getOrNull(index) ?: return
        selectConstruction(constructionName)
    }
    fun selectConstruction(name: String) {
        selectConstruction(cityView.constructions.getConstruction(name))
    }
    fun selectConstruction(newConstruction: IConstruction) {
        selectedConstruction = newConstruction
        if (newConstruction is Building && newConstruction.hasCreateOneImprovementUnique()) {
            val improvement = cityView.getImprovementToCreate(newConstruction)
            selectedQueueEntryTargetTile = if (improvement == null) null
                else cityView.constructions.getTileForImprovement(improvement.name)
        } else {
            selectedQueueEntryTargetTile = null
            pickTileData = null
        }
        selectedTile = null
    }
    private fun selectTile(newTile: TileView?) {
        selectedConstruction = null
        selectedQueueEntryTargetTile = null
        pickTileData = null
        selectedTile = newTile
    }
    fun clearSelection() = selectTile(null)

    fun startPickTileForCreatesOneImprovement(construction: Building, stat: Stat, isBuying: Boolean) {
        val improvement = cityView.getImprovementToCreate(construction) ?: return
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
            newScreen.mapHolder.setCenterPosition(cityView.location.toHexCoord(), immediately = true)
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
        val indexOfCity = viewableCities.indexOfFirst { it == cityView }
        val indexOfNextCity = (indexOfCity + delta + numCities) % numCities
        game.replaceCurrentScreen {
            val newCityScreen = CityScreen(viewableCities[indexOfNextCity], ambiencePlayer = passOnCityAmbiencePlayer())
            newCityScreen.mapScrollPane.zoom(mapScrollPane.scaleX) // Retain zoom
            newCityScreen.updateAsync()
            newCityScreen
        }
    }

    // Don't use passOnCityAmbiencePlayer here - continuing play on the replacement screen would be nice,
    // but the rapid firing of several resize events will get that un-synced, they would no longer stop on leaving.
    override fun recreate(): BaseScreen = CityScreen(cityView, selectedConstruction, selectedTile)

    override fun dispose() {
        cityAmbiencePlayer?.dispose()
        fireworks?.dispose()
        super.dispose()
    }

    override fun render(delta: Float) {
        super.render(delta)
        if (pauseFireworks) return
        fireworks?.render(stage, delta)
    }
}
