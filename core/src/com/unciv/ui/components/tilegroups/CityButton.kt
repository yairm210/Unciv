package com.unciv.ui.components.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.GUI
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.City
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.models.TutorialTrigger
import com.unciv.models.ruleset.INonPerpetualConstruction
import com.unciv.models.ruleset.PerpetualConstruction
import com.unciv.models.translations.tr
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.centerX
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.toGroup
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.fonts.Fonts
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.input.onRightClick
import com.unciv.ui.components.widgets.BorderedTable
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.images.padTopDescent
import com.unciv.ui.popups.Popup
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.cityscreen.CityReligionInfoTable
import com.unciv.ui.screens.cityscreen.CityScreen
import com.unciv.ui.screens.diplomacyscreen.DiplomacyScreen
import com.unciv.utils.DebugUtils
import yairm210.purity.annotations.Readonly
import kotlin.math.max
import kotlin.math.min

class InfluenceTable(
    influence: Float,
    relationshipLevel: RelationshipLevel,
    width: Float=100f,
    height: Float=5f) : Table() {

    override fun draw(batch: Batch?, parentAlpha: Float) { super.draw(batch, parentAlpha) }

    init {

        defaults().pad(1f)
        setSize(width, height)
        background = BaseScreen.skinStrings.getUiBackground(
            "WorldScreen/CityButton/InfluenceBar",
            tintColor = ImageGetter.CHARCOAL)

        val normalizedInfluence = max(-60f, min(influence, 60f)) / 30f

        val color = when (relationshipLevel) {
            RelationshipLevel.Unforgivable -> Color.RED
            RelationshipLevel.Enemy -> Color.ORANGE
            RelationshipLevel.Afraid -> Color.YELLOW
            RelationshipLevel.Neutral, RelationshipLevel.Friend -> Color.LIME
            RelationshipLevel.Ally -> Color.SKY
            else -> Color.DARK_GRAY
        }

        val percentages = arrayListOf(0f, 0f, 0f, 0f)
        when {
            normalizedInfluence < -1f -> {
                percentages[0] = -normalizedInfluence - 1f
                percentages[1] = 1f
            }
            normalizedInfluence < 0f -> percentages[1] = -normalizedInfluence
            normalizedInfluence < 1f -> percentages[2] = normalizedInfluence
            else -> {
                percentages[2] = 1f
                percentages[3] = (normalizedInfluence - 1f)
            }
        }

        for (i in 0..3)
            add(getBarPiece(percentages[i], color, i < 2))

    }

    private fun getBarPiece(percentage: Float, color: Color, negative: Boolean): Table{
        val barPieceSize = width / 4f
        val barPiece = Table()
        val full = ImageGetter.getWhiteDot()
        val empty = ImageGetter.getWhiteDot()

        full.color = color
        empty.color = Color.DARK_GRAY

        if (negative) {
            barPiece.add(empty).size((1f - percentage) * barPieceSize, height)
            barPiece.add(full).size(percentage * barPieceSize, height)
        } else {
            barPiece.add(full).size(percentage * barPieceSize, height)
            barPiece.add(empty).size((1f - percentage) * barPieceSize, height)
        }

        return barPiece
    }

}

private class DefenceTable(city: City) : BorderedTable(

    path="WorldScreen/CityButton/DefenceTable",
    defaultBgShape = BaseScreen.skinStrings.roundedTopEdgeRectangleSmallShape,
    defaultBgBorder = BaseScreen.skinStrings.roundedTopEdgeRectangleSmallBorderShape) {

    init {

        val selectedCiv = GUI.getSelectedPlayer()
        borderSize = 4f
        bgColor = ImageGetter.CHARCOAL
        bgBorderColor = when {
            city.civ == selectedCiv -> colorFromRGB(255, 237, 200)
            city.civ.isAtWarWith(selectedCiv) -> Color.RED
            else -> ImageGetter.CHARCOAL
        }

        pad(2f, 3f, 0f, 3f)

        val cityStrength = CityCombatant(city).getDefendingStrength()
        val cityStrengthLabel = "${Fonts.strength}$cityStrength"
            .toLabel(fontSize = 12, alignment = Align.center)
        add(cityStrengthLabel).colspan(2).grow().center()
    }

}

class AirUnitTable(city: City, numberOfUnits: Int, size: Float=14f) : BorderedTable(
    path="WorldScreen/CityButton/AirUnitTable",
    defaultBgShape = BaseScreen.skinStrings.roundedEdgeRectangleSmallShape,
    defaultBgBorder = BaseScreen.skinStrings.roundedEdgeRectangleSmallShape) {

    init {

        padTop(2f)
        padBottom(2f)

        padLeft(10f)
        padRight(10f)

        val textColor = city.civ.nation.getInnerColor()

        bgColor = city.civ.nation.getOuterColor()
        bgBorderColor = city.civ.nation.getOuterColor()

        val aircraftImage = ImageGetter.getImage("OtherIcons/Aircraft")
        aircraftImage.color = textColor
        aircraftImage.setSize(size, size)

        add(aircraftImage)
        add(numberOfUnits.tr().toLabel(textColor, size.toInt()))
    }

}

private class StatusTable(city: City, iconSize: Float = 18f) : Table() {

    init {

        val padBetween = 2f
        val selectedCiv = GUI.getSelectedPlayer()

        if (city.civ == selectedCiv) {
            if (city.isBlockaded()) {
                val connectionImage = ImageGetter.getImage("OtherIcons/Blockade")
                add(connectionImage).size(iconSize)
                GUI.getWorldScreen().displayTutorial(TutorialTrigger.CityBlockade)
            } else if (!city.isCapital() && city.isConnectedToCapital()) {
                val connectionImage = ImageGetter.getStatIcon("CityConnection")
                add(connectionImage).size(iconSize)
            }
        }

        if (city.isInResistance()) {
            val resistanceImage = ImageGetter.getImage("StatIcons/Resistance")
            add(resistanceImage).size(iconSize).padLeft(padBetween)
        }

        if (city.isPuppet) {
            val puppetImage = ImageGetter.getImage("OtherIcons/Puppet")
            add(puppetImage).size(iconSize).padLeft(padBetween)
        }

        if (city.isBeingRazed) {
            val fireImage = ImageGetter.getImage("OtherIcons/Fire")
            add(fireImage).size(iconSize).padLeft(padBetween)
        }

        if (city.civ == selectedCiv && city.isWeLoveTheKingDayActive()) {
            val wltkdImage = ImageGetter.getImage("OtherIcons/WLTKD")
            add(wltkdImage).size(iconSize).padLeft(padBetween)
        }
    }

}

private class CityTable(city: City, forPopup: Boolean = false) : BorderedTable(
    path = "WorldScreen/CityButton/IconTable",
    defaultBgShape = BaseScreen.skinStrings.roundedEdgeRectangleMidShape,
    defaultBgBorder = BaseScreen.skinStrings.roundedEdgeRectangleMidBorderShape) {

    init {
        isTransform = false
        touchable = Touchable.enabled

        val selectedCiv = GUI.getSelectedPlayer()
        val viewingCiv = GUI.getViewingPlayer()

        bgBorderColor = when {
            city.civ == selectedCiv -> colorFromRGB(233, 233, 172)
            city.civ.isAtWarWith(selectedCiv) -> colorFromRGB(230, 51, 0)
            else -> ImageGetter.CHARCOAL
        }
        borderSize = when {
            city.civ == selectedCiv -> 4f
            city.civ.isAtWarWith(selectedCiv) -> 4f
            else -> 2f
        }
        bgColor = city.civ.nation.getOuterColor().cpy().apply { a = 0.9f }
        borderOnTop = city.civ == selectedCiv

        pad(0f)
        defaults().pad(0f)

        val isShowDetailedInfo = DebugUtils.VISIBLE_MAP
                || city.civ == selectedCiv
                || viewingCiv.isSpectator()

        addCityPopNumber(city)

        if (isShowDetailedInfo)
            addCityGrowthBar(city)

        addCityText(city, forPopup)

        if (isShowDetailedInfo)
            addCityConstruction(city)

        if (city.civ != viewingCiv)
            addCivIcon(city)

        cells.first().padLeft(4f)
        cells.last().padRight(4f)
    }

    private fun addCityPopNumber(city: City) {
        val textColor = city.civ.nation.getInnerColor()
        val popLabel = city.population.population.tr()
            .toLabel(fontColor = textColor, fontSize = 18, alignment = Align.center)
        add(popLabel).minWidth(26f)
    }

    private fun addCityGrowthBar(city: City) {

        val textColor = city.civ.nation.getInnerColor()

        val table = Table()

        var growthPercentage = city.population.foodStored / city.population.getFoodToNextPopulation().toFloat()
        if (growthPercentage < 0) growthPercentage = 0.0f
        if (growthPercentage > 1) growthPercentage = 1.0f

        val growthBar = ImageGetter.getProgressBarVertical(4f, 30f,
            if (city.isStarving()) 1.0f else growthPercentage,
            if (city.isStarving()) Color.RED else CityButton.ColorGrowth, ImageGetter.CHARCOAL, 1f)
        growthBar.color.a = 0.8f

        val turnLabelText = when {
            city.isGrowing() -> {
                val turnsToGrowth = city.population.getNumTurnsToNewPopulation()
                if (turnsToGrowth != null && turnsToGrowth < 100) turnsToGrowth.tr() else Fonts.infinity.toString()
            }
            city.isStarving() -> {
                val turnsToStarvation = city.population.getNumTurnsToStarvation()
                if (turnsToStarvation != null && turnsToStarvation < 100) turnsToStarvation.tr() else Fonts.infinity.toString()
            }
            else -> "-"
        }

        if (city.isGrowing()) {
            var nextTurnPercentage = (city.foodForNextTurn() + city.population.foodStored) / city.population.getFoodToNextPopulation().toFloat()
            if (nextTurnPercentage < 0) nextTurnPercentage = 0.0f
            if (nextTurnPercentage > 1) nextTurnPercentage = 1.0f

            growthBar.setSemiProgress(CityButton.ColorGrowth.cpy().darken(0.4f), nextTurnPercentage, 1f)
        }

        val turnLabel = turnLabelText.toLabel(fontColor = textColor, fontSize = 13)

        table.add(growthBar).padRight(2f)
        table.add(turnLabel).expandY().bottom()
        add(table).minWidth(6f).padLeft(2f)
    }

    private fun addCityText(city: City, forPopup: Boolean) {

        val textColor = city.civ.nation.getInnerColor()
        val table = Table().apply { isTransform = false }

        if (city.isCapital()) {
            val capitalIcon = when {
                city.civ.isCityState -> ImageGetter.getNationIcon("CityState")
                    .apply { color = textColor }
                else -> ImageGetter.getImage("OtherIcons/Capital")
            }
            table.add(capitalIcon).size(20f).padRight(5f)
        }

        val cityName = city.name.toLabel(fontColor = textColor, alignment = Align.center, hideIcons = true)
        table.add(cityName).growY().center().padTopDescent()

        if (!forPopup) {
            val cityReligion = city.religion.getMajorityReligion()
            if (cityReligion != null) {
                val religionImage = ImageGetter.getReligionIcon(cityReligion.getIconName()).apply {
                    color = textColor }.toGroup(20f)
                table.add(religionImage).size(20f).padLeft(5f)
            }
        }

        table.pack()
        add(table)
            .minHeight(34f)
            .padLeft(10f)
            .padRight(10f)
            .expandY().center()

    }

    private fun addCityConstruction(city: City) {

        val textColor = city.civ.nation.getInnerColor()

        val cityConstructions = city.cityConstructions
        val cityCurrentConstruction = cityConstructions.getCurrentConstruction()

        val progressTable = Table()

        var nextTurnPercentage = 0f
        var percentage = 0f
        var turns = "-"
        var icon: Group? = null

        if (cityConstructions.currentConstructionFromQueue.isNotEmpty()) {
            if (cityCurrentConstruction !is PerpetualConstruction) {
                val turnsToConstruction = cityConstructions.turnsToConstruction(cityCurrentConstruction.name)
                if (turnsToConstruction < 100)
                    turns = turnsToConstruction.tr()
                percentage = cityConstructions.getWorkDone(cityCurrentConstruction.name) /
                        (cityCurrentConstruction as INonPerpetualConstruction).getProductionCost(cityConstructions.city.civ, cityConstructions.city).toFloat()
                nextTurnPercentage = (cityConstructions.getWorkDone(cityCurrentConstruction.name) + city.cityStats.currentCityStats.production) /
                        cityCurrentConstruction.getProductionCost(cityConstructions.city.civ, cityConstructions.city).toFloat()

                if (nextTurnPercentage > 1f) nextTurnPercentage = 1f
                if (nextTurnPercentage < 0f) nextTurnPercentage = 0f
            } else {
                turns = Fonts.infinity.toString()
            }
            icon = ImageGetter.getConstructionPortrait(cityCurrentConstruction.name, 24f)
        }

        val productionBar = ImageGetter.getProgressBarVertical(4f, 30f, percentage,
            CityButton.ColorConstruction, ImageGetter.CHARCOAL, 1f)
        productionBar.setSemiProgress(CityButton.ColorConstruction.cpy().darken(0.4f), nextTurnPercentage, 1f)
        productionBar.color.a = 0.8f

        progressTable.add(turns.toLabel(textColor, 13)).expandY().bottom()
        progressTable.add(productionBar).padLeft(2f)

        add(progressTable).minWidth(6f).padRight(2f)
        add(icon).minWidth(26f)

    }

    private fun addCivIcon(city: City) {

        val icon = when {
            city.civ.isMajorCiv() -> ImageGetter.getNationIcon(city.civ.nation.name)
            else -> ImageGetter.getImage("CityStateIcons/" + city.civ.cityStateType.name)
        }
        icon.color = city.civ.nation.getInnerColor()

        add(icon.toGroup(20f)).minWidth(26f)
    }
}

class CityButton(val city: City, private val tileGroup: TileGroup) : Table(BaseScreen.skin) {

    init {
        touchable = Touchable.disabled
    }

    private lateinit var cityTable: CityTable

    private val listOfHiddenUnitMarkers: MutableList<Actor> = mutableListOf()
    private var isButtonMoved = false
    private var isViewable = true

    val viewingPlayer = GUI.getViewingPlayer()

    fun update(isCityViewable: Boolean) {

        isViewable = isCityViewable

        clear()
        setButtonActions()

        // Top-to-bottom layout

        // If any air units in the city - add number indicator
        if (isCityViewable && tileGroup.tile.airUnits.isNotEmpty()) {
            add(AirUnitTable(city, tileGroup.tile.airUnits.size)).padBottom(5f).row()
        }

        // Add City strength table
        add(DefenceTable(city)).row()

        // Add City main table: pop, name, religion, construction, nation icon
        cityTable = CityTable(city)
        add(cityTable).row()

        val selectedPlayer = GUI.getSelectedPlayer()
        // If city state - add influence bar
        if (city.civ.isCityState && city.civ.knows(selectedPlayer)) {
            val diplomacyManager = city.civ.getDiplomacyManager(selectedPlayer)!!
            add(InfluenceTable(diplomacyManager.getInfluence(), diplomacyManager.relationshipLevel())).padTop(1f).row()
        }

        // Add statuses: connection, resistance, puppet, raze, WLTKD
        add(StatusTable(city)).padTop(3f)

        pack()

        // If city damaged - add health bar
        if (isCityViewable && city.health < city.getMaxHealth().toFloat()) {
            val healthBar = ImageGetter.getHealthBar(city.health.toFloat(),
                city.getMaxHealth().toFloat(), 100f, 3f)
            addActor(healthBar)
            healthBar.center(this)
            healthBar.y = cityTable.y + cityTable.height-healthBar.height - 1f
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

        val tilesAroundCity = tileGroup.tile.neighbors
        for (tile in tilesAroundCity)
        {
            val direction = tileGroup.tile.position.cpy().sub(tile.position)

            if (isButtonMoved) {
                when {
                    // detect civilian left-below the city
                    (tile.civilianUnit != null) && direction.epsilonEquals(0f, 1f) ->
                        insertHiddenUnitMarker(HiddenUnitMarkerPosition.Left)
                    // detect military under the city
                    (tile.militaryUnit != null && !tile.hasEnemyInvisibleUnit(viewingPlayer)) && direction.epsilonEquals(1f, 1f) ->
                        insertHiddenUnitMarker(HiddenUnitMarkerPosition.Center)
                    // detect civilian right-below the city
                    (tile.civilianUnit != null) && direction.epsilonEquals(1f, 0f) ->
                        insertHiddenUnitMarker(HiddenUnitMarkerPosition.Right)
                }
            } else if (tile.militaryUnit != null && !tile.hasEnemyInvisibleUnit(viewingPlayer)) {
                when {
                    // detect military left from the city
                    direction.epsilonEquals(0f, 1f) ->
                        insertHiddenUnitMarker(HiddenUnitMarkerPosition.Left)
                    // detect military right from the city
                    direction.epsilonEquals(1f, 0f) ->
                        insertHiddenUnitMarker(HiddenUnitMarkerPosition.Right)
                }
            }
        }
    }

    private fun insertHiddenUnitMarker(pos: HiddenUnitMarkerPosition) {
        // center of the city button +/- size of the 1.5 tiles
        val positionX = cityTable.width / 2 + (pos.ordinal-1)*60f

        val indicator = ImageGetter.getTriangle().apply {
            color = city.civ.nation.getInnerColor()
            setSize(12f, 8f)
            setOrigin(Align.center)
            if (!isButtonMoved) {
                rotation = 180f
                setPosition(positionX - width/2, -height)
            } else
                setPosition(positionX - width/2, -height) // height compensation because of asymmetrical icon
        }
        cityTable.addActor(indicator)
        listOfHiddenUnitMarkers.add(indicator)
    }

    @Readonly private fun belongsToViewingCiv() = city.civ == viewingPlayer

    private fun setButtonActions() {

        val unitTable = GUI.getUnitTable()

        // So you can click anywhere on the button to go to the city
        touchable = Touchable.childrenOnly

        fun enterCityOrInfoPopup() {
            // second tap on the button will go to the city screen
            // if this city belongs to you and you are not iterating though the air units
            if (DebugUtils.VISIBLE_MAP || viewingPlayer.isSpectator()
                || belongsToViewingCiv() && !tileGroup.tile.airUnits.contains(unitTable.selectedUnit)) {
                GUI.pushScreen(CityScreen(city))
            } else if (viewingPlayer.knows(city.civ)) {
                foreignCityInfoPopup()
            }
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
        if (unitTable.selectedCity != city
                && unitTable.selectedUnit?.currentTile != city.getCenterTile() && unitTable.selectedSpy == null) {

            moveButtonUp()
        }
    }

    fun moveButtonDown() {
        if (isButtonMoved)
            return
        val moveButtonAction = Actions.sequence(
                Actions.moveTo(tileGroup.x, tileGroup.y-height, 0.4f, Interpolation.swingOut),
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
        val floatAction = Actions.sequence(
                Actions.moveTo(tileGroup.x, tileGroup.y, 0.4f, Interpolation.sine),
                Actions.run {
                    isButtonMoved = false
                    updateHiddenUnitMarkers(isViewable)
                }
        )
        parent.addAction(floatAction)
    }

    private fun foreignCityInfoPopup() {
        fun openDiplomacy() = GUI.pushScreen(DiplomacyScreen(viewingPlayer, city.civ))

        val espionageVisible = city.civ.gameInfo.isEspionageEnabled()
                && viewingPlayer.espionageManager.getSpyAssignedToCity(city)?.isSetUp() == true
        
        // If there's nothing to display cuz no Religion - skip popup
        if (!city.civ.gameInfo.isReligionEnabled() && !espionageVisible) return openDiplomacy()

        val popup = Popup(GUI.getWorldScreen()).apply {
            name = "ForeignCityInfoPopup"
            add(CityTable(city, true)).fillX().padBottom(5f).colspan(3).row()
            if (city.civ.gameInfo.isReligionEnabled())
                add(CityReligionInfoTable(city.religion, true)).colspan(3).row()
            addOKButton("Diplomacy") { openDiplomacy() }
            if (espionageVisible) addButton("View") { GUI.pushScreen(CityScreen(city)) }
            add().expandX()
            addCloseButton() {
                GUI.getWorldScreen().run { nextTurnButton.update() }
            }
        }
        popup.open()
    }

    companion object {
        val ColorConstruction = colorFromRGB(196,140,62)
        val ColorGrowth =  colorFromRGB(130,225,78)
    }

    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
    }

    override fun act(delta: Float) {
        return // actions should only be for the CityButtonLayerGroup
    }

}
