package com.unciv.ui.tilegroups

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.unciv.logic.city.CityConstructions
import com.unciv.logic.city.CityInfo
import com.unciv.logic.city.PerpetualConstruction
import com.unciv.logic.civilization.diplomacy.RelationshipLevel
import com.unciv.ui.cityscreen.CityScreen
import com.unciv.ui.trade.DiplomacyScreen
import com.unciv.ui.utils.*
import kotlin.math.max
import kotlin.math.min

class CityButton(val city: CityInfo, private val tileGroup: WorldTileGroup): Table(CameraStageBaseScreen.skin){
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

        if (showAdditionalInfoTags && city.health < city.getMaxHealth().toFloat()) {
            val healthBar = ImageGetter.getHealthBar(city.health.toFloat(), city.getMaxHealth().toFloat(), 100f)
            add(healthBar).row()
        }

        iconTable = getIconTable()
        add(iconTable).row()

        if (city.civInfo.isCityState() && city.civInfo.knows(worldScreen.viewingCiv)) {
            val diplomacyManager = city.civInfo.getDiplomacyManager(worldScreen.viewingCiv)
            val influenceBar = getInfluenceBar(diplomacyManager.influence, diplomacyManager.relationshipLevel())
            add(influenceBar).row()
        }

        pack()
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
                setPosition(positionX - width/2, 0f)
            } else
                setPosition(positionX - width/2, -height/4) // height compensation because of asymmetrical icon
        }
        iconTable.addActor(indicator)
        listOfHiddenUnitMarkers.add(indicator)
    }

    private fun addAirUnitTable() {
        if (!showAdditionalInfoTags || tileGroup.tileInfo.airUnits.isEmpty()) return
        val secondarycolor = city.civInfo.nation.getInnerColor()
        val airUnitTable = Table()
        airUnitTable.background = ImageGetter.getRoundedEdgeTableBackground(city.civInfo.nation.getOuterColor()).apply { setMinSize(0f,0f) }
        val aircraftImage = ImageGetter.getImage("OtherIcons/Aircraft")
        aircraftImage.color = secondarycolor
        airUnitTable.add(aircraftImage).size(15f)
        airUnitTable.add(tileGroup.tileInfo.airUnits.size.toString().toLabel(secondarycolor,14))
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
                // if this city belongs to you
                if (uncivGame.viewEntireMapForDebug || belongsToViewingCiv() || viewingCiv.isSpectator()) {
                    uncivGame.setScreen(CityScreen(city))
                } else if (viewingCiv.knows(city.civInfo)) {
                    // If city doesn't belong to you, go directly to its owner's diplomacy screen.
                    val screen = DiplomacyScreen(viewingCiv)
                    screen.updateRightSide(city.civInfo)
                    uncivGame.setScreen(screen)
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

    private fun getIconTable(): Table {
        val secondaryColor = city.civInfo.nation.getInnerColor()
        class IconTable:Table(){
            override fun draw(batch: Batch?, parentAlpha: Float) { super.draw(batch, parentAlpha) }
        }
        val iconTable = IconTable()
        iconTable.touchable=Touchable.enabled
        iconTable.background = ImageGetter.getRoundedEdgeTableBackground(city.civInfo.nation.getOuterColor())

        if (city.isInResistance()) {
            val resistanceImage = ImageGetter.getImage("StatIcons/Resistance")
            iconTable.add(resistanceImage).size(20f).padLeft(5f)
        }

        if (city.isPuppet) {
            val puppetImage = ImageGetter.getImage("OtherIcons/Puppet")
            puppetImage.color = secondaryColor
            iconTable.add(puppetImage).size(20f).padLeft(5f)
        }

        if (city.isBeingRazed) {
            val fireImage = ImageGetter.getImage("OtherIcons/Fire")
            iconTable.add(fireImage).size(20f).padLeft(5f)
        }
        if (city.isCapital()) {
            if (city.civInfo.isCityState()) {
                val cityStateImage = ImageGetter.getNationIcon("CityState")
                        .apply { color = secondaryColor }
                iconTable.add(cityStateImage).size(20f).padLeft(5f)
            } else {
                val starImage = ImageGetter.getImage("OtherIcons/Star").apply { color = Color.LIGHT_GRAY }
                iconTable.add(starImage).size(20f).padLeft(5f)
            }
        } else if (belongsToViewingCiv() && city.isConnectedToCapital()) {
            val connectionImage = ImageGetter.getStatIcon("CityConnection")
            connectionImage.color = secondaryColor
            iconTable.add(connectionImage).size(20f).padLeft(5f)
        }

        val populationGroup = getPopulationGroup(uncivGame.viewEntireMapForDebug
                || belongsToViewingCiv()
                || worldScreen.viewingCiv.isSpectator())
        iconTable.add(populationGroup).padLeft(5f)
        populationGroup.toBack()

        val cityButtonText = city.name
        val label = cityButtonText.toLabel(secondaryColor)
        iconTable.add(label).padRight(20f).padLeft(20f) // sufficient horizontal padding
                .fillY() // provide full-height clicking area
        label.toBack() // this is so the label is rendered right before the population group,
        //  so we save the font texture and avoid another texture switch

        if (uncivGame.viewEntireMapForDebug || belongsToViewingCiv() || worldScreen.viewingCiv.isSpectator()) {
            val constructionGroup = getConstructionGroup(city.cityConstructions)
            iconTable.add(constructionGroup)
            constructionGroup.toBack() // We do this so the construction group is right before the label.
            // What we end up with is construction group > label > population group.
            // Since the label in the construction group is rendered *last* (toFront()),
            // and the two labels in the the population group are rendered *first* (toBack()),
            // What we get is that ALL 4 LABELS are rendered one after the other,
            // and so the glyph texture only needs to be swapped in once rather than 4 times! :)
        }
        else if (city.civInfo.isMajorCiv()) {
            val nationIcon = ImageGetter.getNationIcon(city.civInfo.nation.name)
            nationIcon.color = secondaryColor
            iconTable.add(nationIcon).size(20f)
        }
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
        parent.addAction(moveButtonAction) // Move the whole cityButtonLayerGroup down, so the citybutton remains clickable
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
        val growthGreen = Color(0.0f, 0.5f, 0.0f, 1.0f)


        class PopulationGroup:Group() { // for recognition in the profiler
            override fun draw(batch: Batch?, parentAlpha: Float) { super.draw(batch, parentAlpha) }
        }
        val group = PopulationGroup().apply { isTransform=false }

        val populationLabel = city.population.population.toLabel()
        populationLabel.color = city.civInfo.nation.getInnerColor()

        group.addActor(populationLabel)

        val groupHeight = 25f
        var groupWidth = populationLabel.width
        if (showGrowth) groupWidth += 12f
        group.setSize(groupWidth, groupHeight)

        if (showGrowth) {
            var growthPercentage = city.population.foodStored / city.population.getFoodToNextPopulation().toFloat()
            if (growthPercentage < 0) growthPercentage = 0.0f

            // This can happen if the city was just taken, and there was excess food stored.
            // Without it, it caused the growth bar's height to exceed that of the group's.
            if (growthPercentage > 1) growthPercentage = 1.0f

            val growthBar = ImageGetter.getProgressBarVertical(2f, groupHeight,
                    if (city.isStarving()) 1.0f else growthPercentage,
                    if (city.isStarving()) Color.RED else growthGreen, Color.BLACK)
            growthBar.x = populationLabel.width + 3
            growthBar.centerY(group)

            group.addActor(growthBar)

            val turnLabel : Label
            when {
                city.isGrowing() -> {
                    val turnsToGrowth = city.getNumTurnsToNewPopulation()
                    turnLabel = if (turnsToGrowth != null && turnsToGrowth < 100) turnsToGrowth.toString().toLabel() else "∞".toLabel()
                }
                city.isStarving() -> {
                    val turnsToStarvation = city.getNumTurnsToStarvation()
                    turnLabel = if (turnsToStarvation != null && turnsToStarvation < 100) turnsToStarvation.toString().toLabel() else "∞".toLabel()
                }
                else -> turnLabel = "∞".toLabel()
            }
            turnLabel.color = city.civInfo.nation.getInnerColor()
            turnLabel.setFontSize(14)
            turnLabel.pack()

            group.addActor(turnLabel)
            turnLabel.toBack() // this is so both labels are rendered next to each other -
            // this is important because when switching to a label, we switch out the texture we're using to use the font texture,
            //  so this has a direct impact on framerate!
            turnLabel.x = growthBar.x + growthBar.width + 1
        }

        populationLabel.centerY(group)

        return group
    }

    private fun getConstructionGroup(cityConstructions: CityConstructions): Group {
        val cityCurrentConstruction = cityConstructions.getCurrentConstruction()

        class ConstructionGroup : Group() { // for recognition in the profiler
            override fun draw(batch: Batch?, parentAlpha: Float) {
                super.draw(batch, parentAlpha)
            }
        }

        val group = ConstructionGroup().apply { isTransform = false }
        val groupHeight = 25f
        val groupWidth = if (cityCurrentConstruction is PerpetualConstruction) 15f else 40f
        group.setSize(groupWidth, groupHeight)

        val circle = ImageGetter.getCircle()
        circle.setSize(25f, 25f)
        val constructionImage = ImageGetter.getConstructionImage(cityConstructions.currentConstructionFromQueue)
        constructionImage.setSize(18f, 18f)
        constructionImage.centerY(group)
        constructionImage.x = group.width - constructionImage.width

        // center the circle on the production image
        circle.x = constructionImage.x + (constructionImage.width - circle.width) / 2
        circle.y = constructionImage.y + (constructionImage.height - circle.height) / 2

        group.addActor(circle)
        group.addActor(constructionImage)

        val secondaryColor = cityConstructions.cityInfo.civInfo.nation.getInnerColor()
        if (cityCurrentConstruction !is PerpetualConstruction) {
            val turnsToConstruction = cityConstructions.turnsToConstruction(cityCurrentConstruction.name)
            val label = (if (turnsToConstruction < 100) turnsToConstruction.toString() else "∞").toLabel(secondaryColor, 14)
            label.pack()
            group.addActor(label)

            val constructionPercentage = cityConstructions.getWorkDone(cityCurrentConstruction.name) /
                    cityCurrentConstruction.getProductionCost(cityConstructions.cityInfo.civInfo).toFloat()
            val productionBar = ImageGetter.getProgressBarVertical(2f, groupHeight, constructionPercentage,
                    Color.BROWN.cpy().lerp(Color.WHITE, 0.5f), Color.BLACK)
            productionBar.x = 10f
            label.x = productionBar.x - label.width - 3
            group.addActor(productionBar)
            productionBar.toBack() // Since the production bar is based on whiteDot.png in the MAIN texture,
            // and the constructionImage may be a building or unit which have their own textures,
            // we move the production bar's rendering to be next to the circle's rendering,
            // so we have circle - bar - constructionImage - label (2 texture switches and ending with label)
            // which is the minimal amount of switches we can have here
            label.toFront()
        }
        return group
    }

    companion object {
        fun getInfluenceBar(influence: Float, relationshipLevel: RelationshipLevel, width: Float = 100f, height: Float = 5f): Table {
            val normalizedInfluence = max(-60f, min(influence, 60f)) / 30f

            val color = when (relationshipLevel) {
                RelationshipLevel.Unforgivable -> Color.RED
                RelationshipLevel.Enemy -> Color.ORANGE
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
                background = ImageGetter.getBackground(Color.BLACK)
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

}