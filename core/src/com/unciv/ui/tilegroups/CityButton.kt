package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.UncivGame
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.City
import com.unciv.logic.city.INonPerpetualConstruction
import com.unciv.logic.city.PerpetualConstruction
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.models.translations.tr
import com.unciv.ui.cityscreen.CityReligionInfoTable
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.popup.Popup
import com.unciv.ui.trade.DiplomacyScreen
import com.unciv.ui.utils.BaseScreen
import com.unciv.ui.utils.BorderedTable
import com.unciv.ui.utils.Fonts
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.centerX
import com.unciv.ui.utils.extensions.colorFromRGB
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.toGroup
import com.unciv.ui.utils.extensions.toLabel
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

        val viewingCiv = UncivGame.Current.worldScreen!!.viewingCiv

        borderSize = 4f
        bgColor = Color.BLACK
        bgBorderColor = when {
            city.civInfo == viewingCiv -> colorFromRGB(255, 237, 200)
            city.civInfo.isAtWarWith(viewingCiv) -> Color.RED
            else -> Color.BLACK
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

        val textColor = city.civInfo.nation.getInnerColor()

        bgColor = city.civInfo.nation.getOuterColor()
        bgBorderColor = city.civInfo.nation.getOuterColor()

        val aircraftImage = ImageGetter.getImage("OtherIcons/Aircraft")
        aircraftImage.color = textColor
        aircraftImage.setSize(size, size)

        add(aircraftImage)
        add(numberOfUnits.toString().toLabel(textColor, size.toInt()))
    }

}

private class StatusTable(city: City) : Table() {

    init {

        val viewingCiv = UncivGame.Current.worldScreen!!.viewingCiv

        if (city.civInfo == viewingCiv && city.isConnectedToCapital() && !city.isCapital()) {
            val connectionImage = ImageGetter.getStatIcon("CityConnection")
            add(connectionImage).size(18f)
        }

        if (city.isInResistance()) {
            val resistanceImage = ImageGetter.getImage("StatIcons/Resistance")
            add(resistanceImage).size(18f).padLeft(2f)
        }

        if (city.isPuppet) {
            val puppetImage = ImageGetter.getImage("OtherIcons/Puppet")
            add(puppetImage).size(18f).padLeft(2f)
        }

        if (city.isBeingRazed) {
            val fireImage = ImageGetter.getImage("OtherIcons/Fire")
            add(fireImage).size(18f).padLeft(2f)
        }

        if (city.civInfo == viewingCiv && city.isWeLoveTheKingDayActive()) {
            val wltkdImage = ImageGetter.getImage("OtherIcons/WLTKD")
            add(wltkdImage).size(18f).padLeft(2f)
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

        val viewingCiv = UncivGame.Current.worldScreen!!.viewingCiv

        bgBorderColor = when {
            city.civInfo == viewingCiv -> colorFromRGB(233, 233, 172)
            city.civInfo.isAtWarWith(viewingCiv) -> colorFromRGB(230, 51, 0)
            else -> Color.BLACK
        }
        borderSize = when {
            city.civInfo == viewingCiv -> 4f
            city.civInfo.isAtWarWith(viewingCiv) -> 4f
            else -> 2f
        }
        bgColor = city.civInfo.nation.getOuterColor().cpy().apply { a = 0.9f }
        borderOnTop = city.civInfo == viewingCiv

        pad(0f)
        defaults().pad(0f)

        val isShowDetailedInfo = UncivGame.Current.viewEntireMapForDebug
                || city.civInfo == viewingCiv
                || viewingCiv.isSpectator()

        addCityPopNumber(city)

        if (isShowDetailedInfo)
            addCityGrowthBar(city)

        addCityText(city, forPopup)

        if (isShowDetailedInfo)
            addCityConstruction(city)

        if (city.civInfo != viewingCiv)
            addCivIcon(city)

        cells.first().padLeft(4f)
        cells.last().padRight(4f)
    }

    private fun addCityPopNumber(city: City) {
        val textColor = city.civInfo.nation.getInnerColor()
        val popLabel = city.population.population.toString()
            .toLabel(fontColor = textColor, fontSize = 18, alignment = Align.center)
        add(popLabel).minWidth(26f)
    }

    private fun addCityGrowthBar(city: City) {

        val textColor = city.civInfo.nation.getInnerColor()

        val table = Table()

        var growthPercentage = city.population.foodStored / city.population.getFoodToNextPopulation().toFloat()
        if (growthPercentage < 0) growthPercentage = 0.0f
        if (growthPercentage > 1) growthPercentage = 1.0f

        val growthBar = ImageGetter.getProgressBarVertical(4f, 30f,
            if (city.isStarving()) 1.0f else growthPercentage,
            if (city.isStarving()) Color.RED else CityButton.ColorGrowth, Color.BLACK, 1f)
        growthBar.color.a = 0.8f

        val turnLabelText = when {
            city.isGrowing() -> {
                val turnsToGrowth = city.population.getNumTurnsToNewPopulation()
                if (turnsToGrowth != null && turnsToGrowth < 100) turnsToGrowth.toString() else "∞"
            }
            city.isStarving() -> {
                val turnsToStarvation = city.population.getNumTurnsToStarvation()
                if (turnsToStarvation != null && turnsToStarvation < 100) turnsToStarvation.toString() else "∞"
            }
            else -> "∞"
        }

        val turnLabel = turnLabelText.toLabel(fontColor = textColor, fontSize = 13)

        table.add(growthBar).padRight(2f)
        table.add(turnLabel).expandY().bottom()
        add(table).minWidth(6f).padLeft(2f)
    }

    private fun addCityText(city: City, forPopup: Boolean) {

        val textColor = city.civInfo.nation.getInnerColor()
        val table = Table().apply { isTransform = false }

        if (city.isCapital()) {
            val capitalIcon = when {
                city.civInfo.isCityState() -> ImageGetter.getNationIcon("CityState")
                    .apply { color = textColor }
                else -> ImageGetter.getImage("OtherIcons/Capital")
            }
            table.add(capitalIcon).size(20f).padRight(5f)
        }

        val cityName = city.name.tr().toLabel(fontColor = textColor, alignment = Align.center)
        table.add(cityName).growY().center()

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

        val textColor = city.civInfo.nation.getInnerColor()

        val cityConstructions = city.cityConstructions
        val cityCurrentConstruction = cityConstructions.getCurrentConstruction()

        val progressTable = Table()

        var percentage = 0f
        var turns = "∞"
        var icon: Group? = null

        if (cityConstructions.currentConstructionFromQueue.isNotEmpty()) {
            if (cityCurrentConstruction !is PerpetualConstruction) {
                val turnsToConstruction = cityConstructions.turnsToConstruction(cityCurrentConstruction.name)
                if (turnsToConstruction < 100)
                    turns = turnsToConstruction.toString()
                percentage = cityConstructions.getWorkDone(cityCurrentConstruction.name) /
                        (cityCurrentConstruction as INonPerpetualConstruction).getProductionCost(cityConstructions.city.civInfo).toFloat()
            }
            icon = ImageGetter.getConstructionPortrait(cityCurrentConstruction.name, 24f)
        }

        val productionBar = ImageGetter.getProgressBarVertical(4f, 30f, percentage,
            CityButton.ColorConstruction, Color.BLACK, 1f)
        productionBar.color.a = 0.8f

        progressTable.add(turns.toLabel(textColor, 13)).expandY().bottom()
        progressTable.add(productionBar).padLeft(2f)

        add(progressTable).minWidth(6f).padRight(2f)
        add(icon).minWidth(26f)

    }

    private fun addCivIcon(city: City) {

        val icon = when {
            city.civInfo.isMajorCiv() -> ImageGetter.getNationIcon(city.civInfo.nation.name)
            else -> ImageGetter.getImage("CityStateIcons/" + city.civInfo.cityStateType.name)
        }
        icon.color = city.civInfo.nation.getInnerColor()

        add(icon.toGroup(20f)).minWidth(26f)
    }
}

class CityButton(val city: City, private val tileGroup: TileGroup): Table(BaseScreen.skin){

    val worldScreen = UncivGame.Current.worldScreen!!
    val uncivGame = worldScreen.game

    init {
        touchable = Touchable.disabled
    }

    private lateinit var cityTable: CityTable

    private val listOfHiddenUnitMarkers: MutableList<Actor> = mutableListOf()
    private var isButtonMoved = false
    private var isViewable = true

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

        // If city state - add influence bar
        if (city.civInfo.isCityState() && city.civInfo.knows(worldScreen.viewingCiv)) {
            val diplomacyManager = city.civInfo.getDiplomacyManager(worldScreen.viewingCiv)
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
                    (tile.militaryUnit != null && !tile.hasEnemyInvisibleUnit(worldScreen.viewingCiv)) && direction.epsilonEquals(1f, 1f) ->
                        insertHiddenUnitMarker(HiddenUnitMarkerPosition.Center)
                    // detect civilian right-below the city
                    (tile.civilianUnit != null) && direction.epsilonEquals(1f, 0f) ->
                        insertHiddenUnitMarker(HiddenUnitMarkerPosition.Right)
                }
            } else if (tile.militaryUnit != null && !tile.hasEnemyInvisibleUnit(worldScreen.viewingCiv)) {
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
            color = city.civInfo.nation.getInnerColor()
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

    private fun belongsToViewingCiv() = city.civInfo == worldScreen.viewingCiv

    private fun setButtonActions() {

        val unitTable = worldScreen.bottomUnitTable

        // So you can click anywhere on the button to go to the city
        touchable = Touchable.childrenOnly

        onClick {
            // clicking swings the button a little down to allow selection of units there.
            // this also allows to target selected units to move to the city tile from elsewhere.
            if (isButtonMoved) {
                val viewingCiv = worldScreen.viewingCiv
                // second tap on the button will go to the city screen
                // if this city belongs to you and you are not iterating though the air units
                if (uncivGame.viewEntireMapForDebug || viewingCiv.isSpectator()
                    || (belongsToViewingCiv() && !tileGroup.tile.airUnits.contains(unitTable.selectedUnit))) {
                        uncivGame.pushScreen(CityScreen(city))
                } else if (viewingCiv.knows(city.civInfo)) {
                    foreignCityInfoPopup()
                }
            } else {
                moveButtonDown()
                if ((unitTable.selectedUnit == null || unitTable.selectedUnit!!.currentMovement == 0f) && belongsToViewingCiv())
                    unitTable.citySelected(city)
            }
        }

        // when deselected, move city button to its original position
        if (isButtonMoved
                && unitTable.selectedCity != city
                && unitTable.selectedUnit?.currentTile != city.getCenterTile()) {

            moveButtonUp()
        }
    }

    private fun moveButtonDown() {
        val moveButtonAction = Actions.sequence(
                Actions.moveTo(tileGroup.x, tileGroup.y-height, 0.4f, Interpolation.swingOut),
                Actions.run {
                    isButtonMoved = true
                    updateHiddenUnitMarkers(isViewable)
                }
        )
        parent.addAction(moveButtonAction) // Move the whole cityButtonLayerGroup down, so the CityButton remains clickable
    }

    private fun moveButtonUp() {
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
        fun openDiplomacy() {
            // If city doesn't belong to you, go directly to its owner's diplomacy screen.
            worldScreen.game.pushScreen(DiplomacyScreen(worldScreen.viewingCiv, city.civInfo))
        }

        // If there's nothing to display cuz no Religion - skip popup
        if (!city.civInfo.gameInfo.isReligionEnabled()) return openDiplomacy()

        val popup = Popup(worldScreen).apply {
            name = "ForeignCityInfoPopup"
            add(CityTable(city, true)).fillX().padBottom(5f).colspan(3).row()
            add(CityReligionInfoTable(city.religion, true)).colspan(3).row()
            addOKButton("Diplomacy") { openDiplomacy() }
            add().expandX()
            addCloseButton()
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
