package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Image
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.logic.battle.CityCombatant
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.CityInfo
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
import com.unciv.ui.utils.extensions.toLabel
import kotlin.math.max
import kotlin.math.min


fun Image.toGroupWithShadow(size: Float): Group {
    val group = Group()

    val shadow = Image(this.drawable)
    shadow.setColor(0f,0f,0f,1f)
    shadow.setSize(size, size)
    setSize(size, size)

    group.setSize(size+1f, size+1f)
    group.addActor(shadow)
    group.addActor(this)
    shadow.x += 1f
    shadow.y -= 1f

    return group
}

fun String.toLabelWithShadow(fontColor: Color = Color.WHITE, fontSize: Int = Constants.defaultFontSize) : Group {

    val group = Group()

    val label = this.toLabel(fontColor, fontSize)
    val shadow = this.toLabel(Color.BLACK, fontSize)

    label.pack()
    shadow.pack()

    group.setSize(label.width+1f, label.height+1f)

    group.addActor(shadow)
    group.addActor(label)

    shadow.x += 1f
    shadow.y -= 1f

    return group
}

class IconTable(borderColor: Color, innerColor: Color, borderSize: Float, borderOnTop:Boolean=true): BorderedTable(
    path = "WorldScreen/CityButton/IconTable",
    defaultInner = BaseScreen.skinStrings.roundedEdgeRectangleMidShape,
    defaultBorder = BaseScreen.skinStrings.roundedEdgeRectangleMidBorderShape,
    borderColor = borderColor,
    innerColor = innerColor,
    borderSize = borderSize,
    borderOnTop = borderOnTop
) {
    override fun draw(batch: Batch?, parentAlpha: Float) { super.draw(batch, parentAlpha) }
}

class CityButton(val city: CityInfo, private val tileGroup: WorldTileGroup): Table(BaseScreen.skin){
    val worldScreen = tileGroup.worldScreen
    val uncivGame = worldScreen.game

    init {
        touchable = Touchable.disabled
    }

    private val listOfHiddenUnitMarkers: MutableList<Actor> = mutableListOf()
    private lateinit var iconTable: Table
    private var isButtonMoved = false
    private var showAdditionalInfoTags = false

    fun update(isCityViewable:Boolean) {
        showAdditionalInfoTags = isCityViewable

        clear()
        setButtonActions()
        addAirUnitTable()

        iconTable = getIconTable()
        add(iconTable).row()

        if (city.civInfo.isCityState() && city.civInfo.knows(worldScreen.viewingCiv)) {
            val diplomacyManager = city.civInfo.getDiplomacyManager(worldScreen.viewingCiv)
            val influenceBar = getInfluenceBar(diplomacyManager.getInfluence(), diplomacyManager.relationshipLevel())
            add(influenceBar).padTop(1f).row()
        }

        add(getStatuses()).padTop(3f)

        pack()

        val defenceTable = Table()
        addActor(defenceTable)
        defenceTable.centerX(this)
        defenceTable.y = iconTable.y + iconTable.height + 5f
        defenceTable.add(getDefenceTable())
        defenceTable.toBack()


        if (showAdditionalInfoTags && city.health < city.getMaxHealth().toFloat()) {
            val healthBar = ImageGetter.getHealthBar(city.health.toFloat(),
                city.getMaxHealth().toFloat(), 100f, 3f)
            addActor(healthBar)
            healthBar.center(this)
            healthBar.y = iconTable.y + iconTable.height-healthBar.height - 2f
        }

        setOrigin(Align.center)
        centerX(tileGroup)

        updateHiddenUnitMarkers()
    }

    private enum class HiddenUnitMarkerPosition {Left, Center, Right}

    private fun updateHiddenUnitMarkers() {
        // remove obsolete markers
        for (marker in listOfHiddenUnitMarkers)
            iconTable.removeActor(marker)
        listOfHiddenUnitMarkers.clear()

        if (!showAdditionalInfoTags) return

        // detect civilian in the city center
        if (!isButtonMoved && (tileGroup.tileInfo.civilianUnit != null))
            insertHiddenUnitMarker(HiddenUnitMarkerPosition.Center)

        val tilesAroundCity = tileGroup.tileInfo.neighbors
        for (tile in tilesAroundCity)
        {
            val direction = tileGroup.tileInfo.position.cpy().sub(tile.position)

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
        val positionX = iconTable.width / 2 + (pos.ordinal-1)*60f

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
        iconTable.addActor(indicator)
        listOfHiddenUnitMarkers.add(indicator)
    }

    private fun addAirUnitTable() {
        if (!showAdditionalInfoTags || tileGroup.tileInfo.airUnits.isEmpty()) return
        val secondaryColor = city.civInfo.nation.getInnerColor()
        val airUnitTable = Table()
        airUnitTable.background = BaseScreen.skinStrings.getUiBackground("WorldScreen/CityButton/AirUnitTable", BaseScreen.skinStrings.roundedEdgeRectangleShape, city.civInfo.nation.getOuterColor()).apply { setMinSize(0f,0f) }
        val aircraftImage = ImageGetter.getImage("OtherIcons/Aircraft")
        aircraftImage.color = secondaryColor
        airUnitTable.add(aircraftImage).size(15f)
        airUnitTable.add(tileGroup.tileInfo.airUnits.size.toString().toLabel(secondaryColor,14))
        add(airUnitTable).row()
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
                    || (belongsToViewingCiv() && !tileGroup.tileInfo.airUnits.contains(unitTable.selectedUnit))) {
                        uncivGame.pushScreen(CityScreen(city))
                } else if (viewingCiv.knows(city.civInfo)) {
                    foreignCityInfoPopup()
                }
            } else {
                moveButtonDown()
                if ((unitTable.selectedUnit == null || unitTable.selectedUnit!!.currentMovement == 0f) &&
                            belongsToViewingCiv())
                    tileGroup.selectCity(city)
            }
        }

        // when deselected, move city button to its original position
        if (isButtonMoved
                && unitTable.selectedCity != city
                && unitTable.selectedUnit?.currentTile != city.getCenterTile()) {

            moveButtonUp()
        }
    }

    private fun getDefenceTable(forPopup: Boolean = false): Group {
        val borderColor = if (city.civInfo == worldScreen.viewingCiv)
            colorFromRGB(255, 237, 200) else Color.BLACK

        val table = BorderedTable(
            path="WorldScreen/CityButton/DefenceTable",
            innerColor = Color.BLACK,
            borderColor = borderColor,
            defaultInner = BaseScreen.skinStrings.roundedEdgeRectangleSmallShape,
            defaultBorder = BaseScreen.skinStrings.roundedEdgeRectangleSmallShape,
            borderSize = 3f)
        table.pad(3f).padTop(2f).padBottom(4f)

        val cityStrength = CityCombatant(city).getDefendingStrength()
        val cityStrengthLabel = "${Fonts.strength}$cityStrength".toLabel(fontSize = 12)
        cityStrengthLabel.setAlignment(Align.center)

        if (!forPopup)
            table.add(cityStrengthLabel).grow().center()
        return table
    }

    private fun getStatuses() : Table {

        val table = Table()

        if (city.isInResistance()) {
            val resistanceImage = ImageGetter.getImage("StatIcons/Resistance")
            table.add(resistanceImage).size(15f)
        }

        if (city.isPuppet) {
            val puppetImage = ImageGetter.getImage("OtherIcons/Puppet")
            puppetImage.color = Color.WHITE
            table.add(puppetImage).size(15f).padLeft(5f)
        }

        if (city.isBeingRazed) {
            val fireImage = ImageGetter.getImage("OtherIcons/Fire")
            table.add(fireImage).size(15f).padLeft(5f)
        }
        return table

    }

    private fun getIconTable(forPopup: Boolean = false): Table {
        val secondaryColor = city.civInfo.nation.getInnerColor()
        val borderColor = if (city.civInfo == worldScreen.viewingCiv)
            colorFromRGB(255, 237, 200) else Color.BLACK
        val borderSize = if (city.civInfo == worldScreen.viewingCiv) 2f else 2f

        val iconTable = IconTable(
            borderColor = borderColor,
            innerColor = city.civInfo.nation.getOuterColor().cpy().apply { a = 0.9f },
            borderSize = borderSize,
        ).apply {
            isTransform = false
        }
        iconTable.padLeft(5f).padRight(2f)
        iconTable.touchable = Touchable.enabled

        val popGroup = getPopulationGroup(uncivGame.viewEntireMapForDebug
                || belongsToViewingCiv()
                || worldScreen.viewingCiv.isSpectator())
        val popGroupCell = iconTable.add(popGroup)

        val labelTable = Table()

        if (city.isCapital()) {
            if (city.civInfo.isCityState()) {
                val cityStateImage = ImageGetter.getNationIcon("CityState")
                    .apply { color = secondaryColor }
                labelTable.add(cityStateImage).size(20f).padRight(5f)
            } else {
                val starImage = ImageGetter.getImage("OtherIcons/Capital")//.toGroupWithShadow(20f)
                labelTable.add(starImage).size(20f).padRight(5f)
            }
        } else if (belongsToViewingCiv() && city.isConnectedToCapital()) {
            val connectionImage = ImageGetter.getStatIcon("CityConnection")
            connectionImage.color = secondaryColor
            labelTable.add(connectionImage).size(20f).padRight(5f)
        }

        val cityButtonText = city.name.tr()
        val label = cityButtonText.toLabel(secondaryColor).apply { setAlignment(Align.center) }
        labelTable.add(label).growY().padTop(1f).center()

        if (!forPopup) {
            val cityReligion = city.religion.getMajorityReligion()
            if (cityReligion != null) {
                val religionImage = ImageGetter.getReligionImage(cityReligion.getIconName()).apply {
                    color = Color.WHITE }.toGroupWithShadow(20f)
                labelTable.add(religionImage).size(20f).padLeft(5f)
            }
        }

        labelTable.pack()
        iconTable.add(labelTable).padLeft(10f).padRight(10f).expandY().center()
        label.toFront()

        if (city.civInfo.isCityState()) {
            val cityStateImage = ImageGetter.getImage("CityStateIcons/" +city.civInfo.cityStateType.name).apply { color = secondaryColor }
            iconTable.padLeft(10f)
            iconTable.add(cityStateImage).size(20f).fillY().padRight(5f)
        }

        if (uncivGame.viewEntireMapForDebug || belongsToViewingCiv() || worldScreen.viewingCiv.isSpectator()) {
            val constructionGroup = getConstructionGroup(city.cityConstructions)
            iconTable.add(constructionGroup)
            //constructionGroup.toBack() // We do this so the construction group is right before the label.
            // What we end up with is construction group > label > population group.
            // Since the label in the construction group is rendered *last* (toFront()),
            // and the two labels in the the population group are rendered *first* (toBack()),
            // What we get is that ALL 4 LABELS are rendered one after the other,
            // and so the glyph texture only needs to be swapped in once rather than 4 times! :)
        } else if (city.civInfo.isMajorCiv()) {
            val nationIcon = ImageGetter.getNationIcon(city.civInfo.nation.name)
            nationIcon.color = secondaryColor
            iconTable.add(nationIcon).size(20f).padRight(5f)
            popGroupCell.padLeft(5f)
        }

        //labelTable.toBack()
        // This is so the label is rendered right before the population group,
        // so we save the font texture and avoid another texture switch

        if (city.civInfo == worldScreen.viewingCiv)
            iconTable.bgBorder.toFront()
        else
            iconTable.bgBorder.toBack()

        iconTable.pack()
        return iconTable
    }

    private fun moveButtonDown() {
        val moveButtonAction = Actions.sequence(
                Actions.moveTo(tileGroup.x, tileGroup.y-height, 0.4f, Interpolation.swingOut),
                Actions.run {
                    isButtonMoved = true
                    updateHiddenUnitMarkers()
                }
        )
        parent.addAction(moveButtonAction) // Move the whole cityButtonLayerGroup down, so the CityButton remains clickable
    }

    private fun moveButtonUp() {
        val floatAction = Actions.sequence(
                Actions.moveTo(tileGroup.x, tileGroup.y, 0.4f, Interpolation.sine),
                Actions.run {
                    isButtonMoved = false
                    updateHiddenUnitMarkers()
                }
        )
        parent.addAction(floatAction)
    }

    private fun getPopulationGroup(showGrowth: Boolean): Group {

        val growthGreen = colorFromRGB(130,225,78)

        val table = Table().apply { isTransform = false }
        val popLabel = city.population.population.toString().toLabelWithShadow(fontSize = 18)
        table.add(popLabel).minHeight(32f).apply {
            if (showGrowth)
                minWidth(27f)
            else
                minWidth(17f)
        }

        table.pack()

        if (showGrowth) {
            var growthPercentage = city.population.foodStored / city.population.getFoodToNextPopulation().toFloat()
            if (growthPercentage < 0) growthPercentage = 0.0f

            // This can happen if the city was just taken, and there was excess food stored.
            // Without it, it caused the growth bar's height to exceed that of the group's.
            if (growthPercentage > 1) growthPercentage = 1.0f

            val growthBar = ImageGetter.getProgressBarVertical(4f, table.height,
                if (city.isStarving()) 1.0f else growthPercentage,
                if (city.isStarving()) Color.RED else growthGreen, Color.BLACK, 1f)
            growthBar.color.a = 0.8f
            table.add(growthBar)

            val turnLabelText = when {
                city.isGrowing() -> {
                    val turnsToGrowth = city.getNumTurnsToNewPopulation()
                    if (turnsToGrowth != null && turnsToGrowth < 100) turnsToGrowth.toString() else "∞"
                }
                city.isStarving() -> {
                    val turnsToStarvation = city.getNumTurnsToStarvation()
                    if (turnsToStarvation != null && turnsToStarvation < 100) turnsToStarvation.toString() else "∞"
                }
                else -> "∞"
            }

            val turnLabel = turnLabelText.toLabelWithShadow(fontColor = growthGreen, fontSize = 12)
            table.add(turnLabel).expandY().bottom().padBottom(4f).padLeft(3f)
            turnLabel.toBack()
        }

        return table
    }

    private fun getConstructionGroup(cityConstructions: CityConstructions): Group {
        val cityCurrentConstruction = cityConstructions.getCurrentConstruction()
        val constructionColor = colorFromRGB(196,140,62)

        val table = Table().apply { isTransform = false }
        table.padRight(0f)
        val tableHeight = 32f

        if (cityConstructions.currentConstructionFromQueue.isNotEmpty()) {

            if (cityCurrentConstruction !is PerpetualConstruction) {
                val turnsToConstruction = cityConstructions.turnsToConstruction(cityCurrentConstruction.name)
                val label = (if (turnsToConstruction < 100) turnsToConstruction.toString() else "∞").toLabelWithShadow(constructionColor, 12)

                table.add(label).expandY().bottom().padRight(3f).padBottom(4f)

                val constructionPercentage = cityConstructions.getWorkDone(cityCurrentConstruction.name) /
                        (cityCurrentConstruction as INonPerpetualConstruction).getProductionCost(cityConstructions.cityInfo.civInfo).toFloat()
                val productionBar = ImageGetter.getProgressBarVertical(4f, tableHeight, constructionPercentage,
                    constructionColor, Color.BLACK, 1f)
                productionBar.color.a = 0.8f

                table.add(productionBar)
            }


            val constructionImage = ImageGetter.getPortraitImage(cityCurrentConstruction.name, 24f)
            table.add(constructionImage).minHeight(32f).minWidth(27f).grow().center().right().pad(0f)
            table.pack()
        }


        return table
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
            add(getIconTable(true)).fillX().padBottom(5f).colspan(3).row()
            add(CityReligionInfoTable(city.religion, true)).colspan(3).row()
            addOKButton("Diplomacy") { openDiplomacy() }
            add().expandX()
            addCloseButton()
        }
        popup.open()
    }

    companion object {
        fun getInfluenceBar(influence: Float, relationshipLevel: RelationshipLevel, width: Float = 100f, height: Float = 5f): Table {
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

            fun getBarPiece(percentage: Float, color: Color, negative: Boolean): Table{
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

            class InfluenceTable:Table() { // for recognition in the profiler
                override fun draw(batch: Batch?, parentAlpha: Float) { super.draw(batch, parentAlpha) }
            }
            val influenceBar = InfluenceTable().apply {
                defaults().pad(1f)
                setSize(width, height)
                background = BaseScreen.skinStrings.getUiBackground(
                    "WorldScreen/CityButton/InfluenceBar",
                    tintColor = Color.BLACK
                )
            }

            for (i in 0..3)
                influenceBar.add(getBarPiece(percentages[i], color, i < 2))

            return influenceBar
        }
    }

    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
    }

    override fun act(delta: Float) {
        return // actions should only be for the CityButtonLayerGroup
    }

}
