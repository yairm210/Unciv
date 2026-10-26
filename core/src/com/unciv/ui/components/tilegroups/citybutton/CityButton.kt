package com.unciv.ui.components.tilegroups.citybutton

import com.unciv.view.getCivInnerColor
import com.unciv.view.getCurrentConstruction
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.ui.components.tilegroups.mix
import com.unciv.view.hasMovement
import com.unciv.view.isAirUnit
import com.unciv.view.isCityState
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.centerX
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.cityscreen.CityReligionInfoTable
import com.unciv.ui.screens.cityscreen.CityScreen
import com.unciv.view.ForeignCityView
import com.unciv.ui.screens.diplomacyscreen.DiplomacyScreen
import com.unciv.utils.DebugUtils
import yairm210.purity.annotations.Readonly

/**
 *  The city "button" with decorations on the WorldScreen map.
 *  - Manages moving down when selected ([moveButtonDown], [moveButtonUp]).
 *  - Does not include the garrison / air unit list when the city is selected.
 *
 *  Top to bottom:
 *  - [AirUnitTable] (if there are any)
 *  - [DefenceTable]
 *  - [CityTable]
 *  - [InfluenceTable] (city-states only)
 *  - [StatusTable]
 *  Floating:
 *  - Health bar (if damaged and viewable), positioned just inside the upper border of [CityTable]
 *  - Hidden unit markers (as little triangles just below [CityTable])
 */
class CityButton(val foreignCityView: ForeignCityView, private val tileGroup: TileGroup) : Table(BaseScreen.skin) {
    companion object {
        val ColorConstruction: Color = Color.valueOf("#C48C3E")
        val ColorGrowth: Color = Color.valueOf("#82E14E")
    }

    init {
        touchable = Touchable.disabled // setButtonActions will override this
    }

    private lateinit var cityTable: CityTable

    private val listOfHiddenUnitMarkers: MutableList<Actor> = mutableListOf()
    private var isButtonMoved = false
    private var isViewable = true

    val viewingPlayer = foreignCityView.getViewingCiv()

    @Readonly private fun belongsToViewingCiv() = foreignCityView.isOwnedByViewer()

    /** Hash of everything the button's tables are built from. If unchanged, the (expensive) rebuild is skipped */
    private var lastUpdateKey: Long? = null

    private fun getUpdateKey(isCityViewable: Boolean): Long {
        val city = foreignCityView
        val selectedCiv = GUI.getSelectedPlayer()
        var key = 1L
            .mix(isCityViewable).mix(selectedCiv.civName).mix(viewingPlayer.civName)
            // Common to all tables
            .mix(city.name).mix(city.owningCiv().civName)
            .mix(city.getPopulationCount()).mix(city.isCapital())
            .mix(city.getHealth()).mix(city.getMaxHealth()).mix(city.getDefendingStrength())
            .mix(city.isAtWarWith(selectedCiv))
            .mix(city.getMajorityReligion()?.getIconName())
            // StatusTable
            .mix(city.isBlockaded()).mix(city.isConnectedToCapital()).mix(city.isInResistance())
            .mix(city.isPuppet()).mix(city.isBeingRazed()).mix(city.isWeLoveTheKingDayActive())

        if (isCityViewable)
            key = key.mix(tileGroup.tileView.getVisibleUnits().count { it.isAirUnit() })

        // InfluenceTable
        if (city.isCityState() && city.civKnows(selectedCiv)) {
            val diplomacyManager = city.getDiplomacyManagerWith(selectedCiv)!!
            key = key.mix(diplomacyManager.getInfluence()).mix(diplomacyManager.relationshipLevel())
        }

        // Growth and construction bars - only for cities we're allowed to see inside
        val detailedCityView = city.tryGetCityView()
        key = key.mix(detailedCityView != null)
        if (detailedCityView != null) {
            key = key.mix(detailedCityView.getFoodStored()).mix(detailedCityView.getFoodToNextPopulation())
                .mix(detailedCityView.foodForNextTurn())
            val constructions = detailedCityView.constructions
            key = key.mix(constructions.currentConstructionName())
            val construction = constructions.getCurrentConstruction()
            if (construction is INonPerpetualConstruction)
                key = key.mix(constructions.getWorkDone(construction.name))
                    .mix(detailedCityView.getConstructionProductionCost(construction))
                    .mix(detailedCityView.getCurrentCityStats().production)
        }
        return key
    }

    fun update(isCityViewable: Boolean) {
        isViewable = isCityViewable

        moveButtonUpIfDeselected()

        val key = getUpdateKey(isCityViewable)
        if (key != lastUpdateKey) {
            lastUpdateKey = key
            rebuild(isCityViewable)
        }

        // Depends on the neighbors' units, which our key doesn't cover - cheap, so always done
        updateHiddenUnitMarkers(isCityViewable)
    }

    private fun rebuild(isCityViewable: Boolean) {
        val selectedPlayer = foreignCityView.getViewingCiv()

        clear()
        setButtonActions()

        // Top-to-bottom layout

        // If any air units in the city - add number indicator
        val visibleAirUnits = tileGroup.tileView.getVisibleUnits().filter { it.isAirUnit() }
        if (isCityViewable && visibleAirUnits.isNotEmpty()) {
            add(AirUnitTable(foreignCityView, visibleAirUnits.size)).padBottom(5f).row()
        }

        // Add City strength table
        add(DefenceTable(foreignCityView, selectedPlayer)).row()

        // Add City main table: pop, name, religion, construction, nation icon
        cityTable = CityTable(foreignCityView)
        add(cityTable).row()

        // If city state - add influence bar
        if (foreignCityView.isCityState() && foreignCityView.civKnows(selectedPlayer)) {
            val diplomacyManager = foreignCityView.getDiplomacyManagerWith(selectedPlayer)!!
            add(InfluenceTable(diplomacyManager.getInfluence(), diplomacyManager.relationshipLevel())).padTop(1f).row()
        }

        // Add statuses: connection, resistance, puppet, raze, WLTKD
        add(StatusTable(foreignCityView)).padTop(3f)

        pack()

        // If city damaged - add health bar
        if (isCityViewable && foreignCityView.getHealth() < foreignCityView.getMaxHealth().toFloat()) {
            val healthBar = ImageGetter.getHealthBar(foreignCityView.getHealth().toFloat(),
                foreignCityView.getMaxHealth().toFloat(), 100f, 3f)
            addActor(healthBar)
            healthBar.center(this)
            healthBar.y = cityTable.y + cityTable.height - healthBar.height - 1f
        }

        setOrigin(Align.center)
        centerX(tileGroup)
    }

    private enum class HiddenUnitMarkerPosition {Left, Center, Right}

    private fun updateHiddenUnitMarkers(isCityViewable: Boolean) {
        // remove obsolete markers
        for (marker in listOfHiddenUnitMarkers)
            cityTable.removeActor(marker)
        listOfHiddenUnitMarkers.clear()

        if (!isCityViewable) return

        // detect civilian in the city center
        if (!isButtonMoved && (tileGroup.tileView.civilianUnit != null))
            insertHiddenUnitMarker(HiddenUnitMarkerPosition.Center)

        for (neighbor in tileGroup.tileView.getVisibleNeighbors()) {
            val direction = tileGroup.tileView.position().minus(neighbor.position())

            if (isButtonMoved) {
                when {
                    // detect civilian left-below the city
                    (neighbor.civilianUnit != null) && direction.x == 0 && direction.eq(0, 1) ->
                        insertHiddenUnitMarker(HiddenUnitMarkerPosition.Left)
                    // detect military under the city
                    neighbor.militaryUnit != null && direction.eq(1, 1) ->
                        insertHiddenUnitMarker(HiddenUnitMarkerPosition.Center)
                    // detect civilian right-below the city
                    (neighbor.civilianUnit != null) && direction.eq(1, 0) ->
                        insertHiddenUnitMarker(HiddenUnitMarkerPosition.Right)
                }
            } else if (neighbor.militaryUnit != null) {
                when {
                    // detect military left from the city
                    direction.eq(0, 1) ->
                        insertHiddenUnitMarker(HiddenUnitMarkerPosition.Left)
                    // detect military right from the city
                    direction.eq(1, 0) ->
                        insertHiddenUnitMarker(HiddenUnitMarkerPosition.Right)
                }
            }
        }
    }

    private fun insertHiddenUnitMarker(pos: HiddenUnitMarkerPosition) {
        // center of the city button +/- size of the 1.5 tiles
        val positionX = cityTable.width / 2 + (pos.ordinal - 1) * 60f

        val indicator = ImageGetter.getTriangle().apply {
            color = foreignCityView.getCivInnerColor()
            setSize(12f, 8f)
            setOrigin(Align.center)
            if (!isButtonMoved) {
                rotation = 180f
                setPosition(positionX - width / 2, -height)
            } else
                setPosition(positionX - width / 2, -height) // height compensation because of asymmetrical icon
        }
        cityTable.addActor(indicator)
        listOfHiddenUnitMarkers.add(indicator)
    }

    private fun setButtonActions() {
        val unitTable = GUI.getUnitTable()

        // So you can click anywhere on the button to go to the city
        touchable = Touchable.childrenOnly

        fun enterCityOrInfoPopup() {
            // second tap on the button will go to the city screen
            // if this city belongs to you and you are not iterating though the air units
            val cityView = foreignCityView.tryGetCityView()
            val selectedUnit = unitTable.selectedUnit
            val isIteratingAirUnits = selectedUnit != null && selectedUnit.isAirUnit()
                && selectedUnit.getTile() == foreignCityView.getCenterTile()
            // Foreign cities with a spy should offer both diplomacy and inspection.
            val openCityDirectly = belongsToViewingCiv() || viewingPlayer.isSpectator || DebugUtils.VISIBLE_MAP
            if (cityView != null && !isIteratingAirUnits && openCityDirectly) {
                GUI.pushScreen{ CityScreen(cityView) }
            }
            else if (foreignCityView.isKnownTo(viewingPlayer))
                foreignCityInfoPopup()
        }

        onClick {
            // clicking swings the button a little down to allow selection of units there.
            // this also allows to target selected units to move to the city tile from elsewhere.
            if (isButtonMoved) {
                enterCityOrInfoPopup()
            } else {
                moveButtonDown()
                if ((unitTable.selectedUnit == null || !unitTable.selectedUnit!!.hasMovement()) && belongsToViewingCiv())
                    unitTable.citySelected(foreignCityView)
            }
        }
        onRightClick(action = ::enterCityOrInfoPopup)
    }

    private fun moveButtonUpIfDeselected() {
        val unitTable = GUI.getUnitTable()
        // when deselected, move city button to its original position
        if (unitTable.selectedCity != foreignCityView && unitTable.selectedUnit?.getTile() != foreignCityView.getCenterTile() && unitTable.selectedSpy == null)
            moveButtonUp()
    }

    fun moveButtonDown() {
        if (isButtonMoved)
            return
        val moveButtonAction = Actions.sequence(
            Actions.moveTo(tileGroup.x, tileGroup.y - height, 0.4f, Interpolation.swingOut),
            Actions.run {
                isButtonMoved = true
                updateHiddenUnitMarkers(isViewable)
            }
        )
        parent.addAction(moveButtonAction) // Move the whole cityButtonLayerGroup down, so the CityButton remains clickable
    }

    fun moveButtonUp() {
        if (!isButtonMoved)
            return
        val moveButtonAction = Actions.sequence(
            Actions.moveTo(tileGroup.x, tileGroup.y, 0.4f, Interpolation.sine),
            Actions.run {
                isButtonMoved = false
                updateHiddenUnitMarkers(isViewable)
            }
        )
        parent.addAction(moveButtonAction)
    }

    private fun foreignCityInfoPopup() {
        fun openDiplomacy() = GUI.pushScreen{ 
            DiplomacyScreen(foreignCityView.gameView.civView, foreignCityView.owningCiv())
        }

        val espionageVisible = foreignCityView.isEspionageEnabled()
                && foreignCityView.spyIsSetUpAtCity(viewingPlayer)
        val espionageCityView = if (espionageVisible) foreignCityView.tryGetCityView() else null

        // If there's nothing to display cuz no Religion - skip popup
        if (!foreignCityView.isReligionEnabled() && !espionageVisible) return openDiplomacy()

        val popup = Popup(GUI.getWorldScreen()).apply {
            name = "ForeignCityInfoPopup"
            add(CityTable(foreignCityView, true)).fillX().padBottom(5f).colspan(3).row()
            if (foreignCityView.isReligionEnabled())
                add(CityReligionInfoTable(foreignCityView, true)).colspan(3).row()
            addOKButton("Diplomacy") { openDiplomacy() }
            if (espionageCityView != null) addButton("View") { GUI.pushScreen{
                CityScreen(espionageCityView)
            } }
            add().expandX()
            addCloseButton {
                GUI.getWorldScreen().run { nextTurnButton.update() }
            }
        }
        popup.open()
    }

    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)

    override fun act(delta: Float) {
        return // actions should only be for the CityButtonLayerGroup
    }
}
