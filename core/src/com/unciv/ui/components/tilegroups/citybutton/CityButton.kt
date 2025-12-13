package com.unciv.ui.components.tilegroups.citybutton

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.logic.city.City
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
class CityButton(val city: City, private val tileGroup: TileGroup) : Table(BaseScreen.skin) {
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

    val viewingPlayer = GUI.getViewingPlayer()

    @Readonly private fun belongsToViewingCiv() = city.civ == viewingPlayer

    fun update(isCityViewable: Boolean) {
        val selectedPlayer = GUI.getSelectedPlayer()
        isViewable = isCityViewable

        clear()
        setButtonActions()

        // Top-to-bottom layout

        // If any air units in the city - add number indicator
        if (isCityViewable && tileGroup.tile.airUnits.isNotEmpty()) {
            add(AirUnitTable(city, tileGroup.tile.airUnits.size)).padBottom(5f).row()
        }

        // Add City strength table
        add(DefenceTable(city, selectedPlayer)).row()

        // Add City main table: pop, name, religion, construction, nation icon
        cityTable = CityTable(city, viewingPlayer)
        add(cityTable).row()

        // If city state - add influence bar
        if (city.civ.isCityState && city.civ.knows(selectedPlayer)) {
            val diplomacyManager = city.civ.getDiplomacyManager(selectedPlayer)!!
            add(InfluenceTable(diplomacyManager.getInfluence(), diplomacyManager.relationshipLevel())).padTop(1f).row()
        }

        // Add statuses: connection, resistance, puppet, raze, WLTKD
        add(StatusTable(city, selectedPlayer)).padTop(3f)

        pack()

        // If city damaged - add health bar
        if (isCityViewable && city.health < city.getMaxHealth().toFloat()) {
            val healthBar = ImageGetter.getHealthBar(city.health.toFloat(),
                city.getMaxHealth().toFloat(), 100f, 3f)
            addActor(healthBar)
            healthBar.center(this)
            healthBar.y = cityTable.y + cityTable.height - healthBar.height - 1f
        }

        setOrigin(Align.center)
        centerX(tileGroup)

        updateHiddenUnitMarkers(isCityViewable)
    }

    private enum class HiddenUnitMarkerPosition {Left, Center, Right}

    private fun updateHiddenUnitMarkers(isCityViewable: Boolean) {
        // remove obsolete markers
        for (marker in listOfHiddenUnitMarkers)
            cityTable.removeActor(marker)
        listOfHiddenUnitMarkers.clear()

        if (!isCityViewable) return

        // detect civilian in the city center
        if (!isButtonMoved && (tileGroup.tile.civilianUnit != null))
            insertHiddenUnitMarker(HiddenUnitMarkerPosition.Center)

        for (tile in tileGroup.tile.neighbors) {
            val direction = tileGroup.tile.position.minus(tile.position)

            if (isButtonMoved) {
                when {
                    // detect civilian left-below the city
                    (tile.civilianUnit != null) && direction.x == 0 && direction.eq(0, 1) ->
                        insertHiddenUnitMarker(HiddenUnitMarkerPosition.Left)
                    // detect military under the city
                    (tile.militaryUnit != null && !tile.hasEnemyInvisibleUnit(viewingPlayer)) && direction.eq(1, 1) ->
                        insertHiddenUnitMarker(HiddenUnitMarkerPosition.Center)
                    // detect civilian right-below the city
                    (tile.civilianUnit != null) && direction.eq(1, 0) ->
                        insertHiddenUnitMarker(HiddenUnitMarkerPosition.Right)
                }
            } else if (tile.militaryUnit != null && !tile.hasEnemyInvisibleUnit(viewingPlayer)) {
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
            color = city.civ.nation.getInnerColor()
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
            val canEnterCity = DebugUtils.VISIBLE_MAP
                || viewingPlayer.isSpectator()
                || belongsToViewingCiv() && !tileGroup.tile.airUnits.contains(unitTable.selectedUnit)
            if (canEnterCity)
                GUI.pushScreen(CityScreen(city))
            else if (viewingPlayer.knows(city.civ))
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
                    unitTable.citySelected(city)
            }
        }
        onRightClick(action = ::enterCityOrInfoPopup)

        // when deselected, move city button to its original position
        if (unitTable.selectedCity != city && unitTable.selectedUnit?.currentTile != city.getCenterTile() && unitTable.selectedSpy == null)
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
        fun openDiplomacy() = GUI.pushScreen(DiplomacyScreen(viewingPlayer, city.civ))

        val espionageVisible = city.civ.gameInfo.isEspionageEnabled()
                && viewingPlayer.espionageManager.getSpyAssignedToCity(city)?.isSetUp() == true

        // If there's nothing to display cuz no Religion - skip popup
        if (!city.civ.gameInfo.isReligionEnabled() && !espionageVisible) return openDiplomacy()

        val popup = Popup(GUI.getWorldScreen()).apply {
            name = "ForeignCityInfoPopup"
            add(CityTable(city, viewingPlayer, true)).fillX().padBottom(5f).colspan(3).row()
            if (city.civ.gameInfo.isReligionEnabled())
                add(CityReligionInfoTable(city.religion, true)).colspan(3).row()
            addOKButton("Diplomacy") { openDiplomacy() }
            if (espionageVisible) addButton("View") { GUI.pushScreen(CityScreen(city)) }
            add().expandX()
            addCloseButton {
                GUI.getWorldScreen().run { nextTurnButton.update() }
            }
        }
        popup.open()
    }

    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
    }

    override fun act(delta: Float) {
        return // actions should only be for the CityButtonLayerGroup
    }
}
