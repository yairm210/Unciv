package com.unciv.ui.worldscreen

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.*
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.utils.Align
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.BattleHelper
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.*
import com.unciv.models.AttackableTile
import com.unciv.models.UncivSound
import com.unciv.models.ruleset.unit.UnitType
import com.unciv.ui.map.TileGroupMap
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.tilegroups.WorldTileGroup
import com.unciv.ui.utils.*
import kotlin.concurrent.thread


class WorldMapHolder(internal val worldScreen: WorldScreen, internal val tileMap: TileMap): ZoomableScrollPane() {
    internal var selectedTile: TileInfo? = null
    val tileGroups = HashMap<TileInfo, List<WorldTileGroup>>()
    //allWorldTileGroups exists to easily access all WordTileGroups
    //since tileGroup is a HashMap of Lists and getting all WordTileGroups
    //would need a double for loop
    val allWorldTileGroups = ArrayList<WorldTileGroup>()

    val unitActionOverlays: ArrayList<Actor> = ArrayList()

    init {
        if (Gdx.app.type == Application.ApplicationType.Desktop) this.setFlingTime(0f)
        continousScrollingX = tileMap.mapParameters.worldWrap
    }

    // Used to transfer data on the "move here" button that should be created, from the side thread to the main thread
    class MoveHereButtonDto(val unitToTurnsToDestination: HashMap<MapUnit, Int>, val tileInfo: TileInfo)

    internal fun addTiles() {
        val tileSetStrings = TileSetStrings()
        val daTileGroups = tileMap.values.map { WorldTileGroup(worldScreen, it, tileSetStrings) }
        val tileGroupMap = TileGroupMap(daTileGroups, worldScreen.stage.width, continousScrollingX)
        val mirrorTileGroups = tileGroupMap.getMirrorTiles()

        for (tileGroup in daTileGroups) {
            if (continousScrollingX){
                val mirrorTileGroupLeft = mirrorTileGroups[tileGroup.tileInfo]!!.first
                val mirrorTileGroupRight = mirrorTileGroups[tileGroup.tileInfo]!!.second

                allWorldTileGroups.add(tileGroup)
                allWorldTileGroups.add(mirrorTileGroupLeft)
                allWorldTileGroups.add(mirrorTileGroupRight)

                tileGroups[tileGroup.tileInfo] = listOf(tileGroup, mirrorTileGroupLeft, mirrorTileGroupRight)
            } else {
                tileGroups[tileGroup.tileInfo] = listOf(tileGroup)
                allWorldTileGroups.add(tileGroup)
            }
        }

        for (tileGroup in allWorldTileGroups) {
            tileGroup.cityButtonLayerGroup.onClick(UncivSound.Silent) {
                onTileClicked(tileGroup.tileInfo)
            }
            tileGroup.onClick { onTileClicked(tileGroup.tileInfo) }

            // Right mouse click listener
            tileGroup.addListener(object : ClickListener() {
                init {
                    button = Input.Buttons.RIGHT
                }

                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    val unit = worldScreen.bottomUnitTable.selectedUnit
                    if (unit == null) return
                    thread {
                        val tile = tileGroup.tileInfo

                        val attackableTile = BattleHelper.getAttackableEnemies(unit, unit.movement.getDistanceToTiles())
                                .firstOrNull { it.tileToAttack == tileGroup.tileInfo }
                        if (unit.canAttack() && attackableTile != null) {
                            Battle.moveAndAttack(MapUnitCombatant(unit), attackableTile)
                            worldScreen.shouldUpdate = true
                            return@thread
                        }

                        val canUnitReachTile = unit.movement.canReach(tile)
                        if (canUnitReachTile) {
                            moveUnitToTargetTile(listOf(unit), tile)
                            return@thread
                        }

                    }
                }
            })
        }

        actor = tileGroupMap

        setSize(worldScreen.stage.width * 2, worldScreen.stage.height * 2)
        setOrigin(width / 2, height / 2)
        center(worldScreen.stage)

        layout() // Fit the scroll pane to the contents - otherwise, setScroll won't work!

    }

    private fun onTileClicked(tileInfo: TileInfo) {
        removeUnitActionOverlay()
        selectedTile = tileInfo

        val unitTable = worldScreen.bottomUnitTable
        val previousSelectedUnits = unitTable.selectedUnits.toList() // create copy
        val previousSelectedCity = unitTable.selectedCity
        unitTable.tileSelected(tileInfo)
        val newSelectedUnit = unitTable.selectedUnit

        if (previousSelectedUnits.isNotEmpty() && previousSelectedUnits.any { it.getTile() != tileInfo }
                && worldScreen.isPlayersTurn
                && previousSelectedUnits.any {
                    it.movement.canMoveTo(tileInfo) ||
                            it.movement.isUnknownTileWeShouldAssumeToBePassable(tileInfo) && !it.type.isAirUnit()
                }) {
            // this can take a long time, because of the unit-to-tile calculation needed, so we put it in a different thread
            addTileOverlaysWithUnitMovement(previousSelectedUnits, tileInfo)
        } else addTileOverlays(tileInfo) // no unit movement but display the units in the tile etc.


        if (newSelectedUnit == null || newSelectedUnit.type == UnitType.Civilian) {
            val unitsInTile = selectedTile!!.getUnits()
            if (previousSelectedCity != null && !previousSelectedCity.attackedThisTurn
                    && selectedTile!!.getTilesInDistance(2).contains(previousSelectedCity.getCenterTile())
                    && unitsInTile.any()
                    && unitsInTile.first().civInfo.isAtWarWith(worldScreen.viewingCiv)) {
                // try to select the closest city to bombard this guy
                unitTable.citySelected(previousSelectedCity)
            }
        }

        worldScreen.shouldUpdate = true
    }


    fun moveUnitToTargetTile(selectedUnits: List<MapUnit>, targetTile: TileInfo) {
        // this can take a long time, because of the unit-to-tile calculation needed, so we put it in a different thread
        // THIS PART IS REALLY ANNOYING
        // So lets say you have 2 units you want to move in the same direction, right
        // But if the first one gets there, and the second one was PLANNING on going there, then now it can't and has to rethink
        // So basically, THE UNIT MOVES HAVE TO BE SEQUENTIAL and not concurrent which is a BITCH
        // So we do this one at a time by getting the list of units to move, MOVING ONE OF THEM with all the yukky threading,
        // and then calling the function again but without the unit that moved.

        val selectedUnit = selectedUnits.first()

        thread(name = "TileToMoveTo") {
            // these are the heavy parts, finding where we want to go
            // Since this runs in a different thread, even if we check movement.canReach()
            // then it might change until we get to the getTileToMoveTo, so we just try/catch it
            val tileToMoveTo: TileInfo
            try {
                tileToMoveTo = selectedUnit.movement.getTileToMoveToThisTurn(targetTile)
            } catch (ex: Exception) {
                return@thread
            } // can't move here

            Gdx.app.postRunnable {
                try {
                    // Because this is darned concurrent (as it MUST be to avoid ANRs),
                    // there are edge cases where the canReach is true,
                    // but until it reaches the headTowards the board has changed and so the headTowards fails.
                    // I can't think of any way to avoid this,
                    // but it's so rare and edge-case-y that ignoring its failure is actually acceptable, hence the empty catch
                    selectedUnit.movement.moveToTile(tileToMoveTo)
                    if (selectedUnit.action == Constants.unitActionExplore || selectedUnit.isMoving())
                        selectedUnit.action = null // remove explore on manual move
                    Sounds.play(UncivSound.Whoosh)
                    if (selectedUnit.currentTile != targetTile)
                        selectedUnit.action = "moveTo " + targetTile.position.x.toInt() + "," + targetTile.position.y.toInt()
                    if (selectedUnit.currentMovement > 0) worldScreen.bottomUnitTable.selectUnit(selectedUnit)

                    worldScreen.shouldUpdate = true
                    if (selectedUnits.size > 1) { // We have more tiles to move
                        moveUnitToTargetTile(selectedUnits.subList(1, selectedUnits.size), targetTile)
                    } else removeUnitActionOverlay() //we're done here
                } catch (e: Exception) {
                }
            }
        }
    }


    private fun addTileOverlaysWithUnitMovement(selectedUnits: List<MapUnit>, tileInfo: TileInfo) {
        thread(name = "TurnsToGetThere") {
            /** LibGdx sometimes has these weird errors when you try to edit the UI layout from 2 separate threads.
             * And so, all UI editing will be done on the main thread.
             * The only "heavy lifting" that needs to be done is getting the turns to get there,
             * so that and that alone will be relegated to the concurrent thread.
             */

            val unitToTurnsToTile = HashMap<MapUnit, Int>()
            for (unit in selectedUnits) {
                val turnsToGetThere = if (unit.type.isAirUnit()) {
                    if (unit.movement.canReach(tileInfo)) 1
                    else 0
                } else unit.movement.getShortestPath(tileInfo).size // this is what takes the most time, tbh
                unitToTurnsToTile[unit] = turnsToGetThere
            }

            Gdx.app.postRunnable {
                val unitsWhoCanMoveThere = HashMap(unitToTurnsToTile.filter { it.value != 0 })
                if (unitsWhoCanMoveThere.isEmpty()) { // give the regular tile overlays with no unit movement
                    addTileOverlays(tileInfo)
                    worldScreen.shouldUpdate = true
                    return@postRunnable
                }

                val turnsToGetThere = unitsWhoCanMoveThere.values.max()!!

                if (UncivGame.Current.settings.singleTapMove && turnsToGetThere == 1) {
                    // single turn instant move
                    val selectedUnit = unitsWhoCanMoveThere.keys.first()
                    for (unit in unitsWhoCanMoveThere.keys) {
                        unit.movement.headTowards(tileInfo)
                    }
                    worldScreen.bottomUnitTable.selectUnit(selectedUnit) // keep moved unit selected
                } else {
                    // add "move to" button if there is a path to tileInfo
                    val moveHereButtonDto = MoveHereButtonDto(unitsWhoCanMoveThere, tileInfo)
                    addTileOverlays(tileInfo, moveHereButtonDto)
                }
                worldScreen.shouldUpdate = true
            }

        }
    }

    private fun addTileOverlays(tileInfo: TileInfo, moveHereDto: MoveHereButtonDto? = null) {
        for (group in tileGroups[tileInfo]!!){
            val table = Table().apply { defaults().pad(10f) }
            if (moveHereDto != null && worldScreen.canChangeState)
                table.add(getMoveHereButton(moveHereDto))

            val unitList = ArrayList<MapUnit>()
            if (tileInfo.isCityCenter()
                    && (tileInfo.getOwner() == worldScreen.viewingCiv || worldScreen.viewingCiv.isSpectator())) {
                unitList.addAll(tileInfo.getCity()!!.getCenterTile().getUnits())
            } else if (tileInfo.airUnits.isNotEmpty()
                    && (tileInfo.airUnits.first().civInfo == worldScreen.viewingCiv || worldScreen.viewingCiv.isSpectator())) {
                unitList.addAll(tileInfo.getUnits())
            }

            for (unit in unitList) {
                val unitGroup = UnitGroup(unit, 60f).surroundWithCircle(80f)
                unitGroup.circle.color = Color.GRAY.cpy().apply { a = 0.5f }
                if (unit.currentMovement == 0f) unitGroup.color.a = 0.5f
                unitGroup.touchable = Touchable.enabled
                unitGroup.onClick {
                    worldScreen.bottomUnitTable.selectUnit(unit, Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT))
                    worldScreen.shouldUpdate = true
                    removeUnitActionOverlay()
                }
                table.add(unitGroup)
            }

            addOverlayOnTileGroup(group, table)
            table.moveBy(0f, 60f)
        }
    }

    private fun getMoveHereButton(dto: MoveHereButtonDto): Group {
        val size = 60f
        val moveHereButton = Group().apply { width = size;height = size; }
        moveHereButton.addActor(ImageGetter.getCircle().apply { width = size; height = size })
        moveHereButton.addActor(ImageGetter.getStatIcon("Movement")
                .apply { color = Color.BLACK; width = size / 2; height = size / 2; center(moveHereButton) })

        val numberCircle = ImageGetter.getCircle().apply { width = size / 2; height = size / 2;color = Color.BLUE }
        moveHereButton.addActor(numberCircle)


        moveHereButton.addActor(dto.unitToTurnsToDestination.values.max()!!.toLabel().apply { center(numberCircle) })
        val firstUnit = dto.unitToTurnsToDestination.keys.first()
        val unitIcon = if (dto.unitToTurnsToDestination.size == 1) UnitGroup(firstUnit, size / 2)
        else dto.unitToTurnsToDestination.size.toString().toLabel(fontColor = firstUnit.civInfo.nation.getInnerColor()).apply { setAlignment(Align.center) }
                .surroundWithCircle(size / 2).apply { circle.color = firstUnit.civInfo.nation.getOuterColor() }
        unitIcon.y = size - unitIcon.height
        moveHereButton.addActor(unitIcon)

        val unitsThatCanMove = dto.unitToTurnsToDestination.keys.filter { it.currentMovement > 0 }
        if (unitsThatCanMove.isEmpty()) moveHereButton.color.a = 0.5f
        else {
            moveHereButton.onClick(UncivSound.Silent) {
                UncivGame.Current.settings.addCompletedTutorialTask("Move unit")
                if (unitsThatCanMove.any { it.type.isAirUnit() })
                    UncivGame.Current.settings.addCompletedTutorialTask("Move an air unit")
                moveUnitToTargetTile(unitsThatCanMove, dto.tileInfo)
            }
        }
        return moveHereButton
    }


    private fun addOverlayOnTileGroup(group: TileGroup, actor: Actor) {

        actor.center(group)
        actor.x += group.x
        actor.y += group.y
        group.parent.addActor(actor) // Add the overlay to the TileGroupMap - it's what actually displays all the tiles
        actor.toFront()

        actor.y += actor.height
        unitActionOverlays.add(actor)

    }

    /** Returns true when the civ is a human player defeated in singleplayer game */
    private fun isMapRevealEnabled(viewingCiv: CivilizationInfo) = !viewingCiv.gameInfo.gameParameters.isOnlineMultiplayer
            && viewingCiv.isCurrentPlayer()
            && viewingCiv.isDefeated()

    internal fun updateTiles(viewingCiv: CivilizationInfo) {

        if (isMapRevealEnabled(viewingCiv)) {
            // Only needs to be done once - this is so the minimap will also be revealed
            if (viewingCiv.exploredTiles.size != tileMap.values.size)
                viewingCiv.exploredTiles = tileMap.values.map { it.position }.toHashSet()
            allWorldTileGroups.forEach { it.showEntireMap = true } // So we can see all resources, regardless of tech
        }

        val playerViewableTilePositions = viewingCiv.viewableTiles.map { it.position }.toHashSet()

        for (tileGroup in allWorldTileGroups) {
            tileGroup.update(viewingCiv)

            if (tileGroup.tileInfo.improvement == Constants.barbarianEncampment
                    && tileGroup.tileInfo.position in viewingCiv.exploredTiles)
                tileGroup.showCircle(Color.RED)

            val unitsInTile = tileGroup.tileInfo.getUnits()
            val canSeeEnemy = unitsInTile.any() && unitsInTile.first().civInfo.isAtWarWith(viewingCiv)
                    && tileGroup.showMilitaryUnit(viewingCiv)
            if (tileGroup.isViewable(viewingCiv) && canSeeEnemy)
                tileGroup.showCircle(Color.RED) // Display ALL viewable enemies with a red circle so that users don't need to go "hunting" for enemy units
        }

        val unitTable = worldScreen.bottomUnitTable
        when {
            unitTable.selectedCity != null -> {
                val city = unitTable.selectedCity!!
                updateTilegroupsForSelectedCity(city, playerViewableTilePositions)
            }
            unitTable.selectedUnit != null -> {
                for (unit in unitTable.selectedUnits) {
                    updateTilegroupsForSelectedUnit(unit, playerViewableTilePositions)
                }
            }
            unitActionOverlays.isNotEmpty() -> {
                removeUnitActionOverlay()
            }
        }

        for (group in tileGroups[selectedTile]!!) {
            group.showCircle(Color.WHITE)
        }

        zoom(scaleX) // zoom to current scale, to set the size of the city buttons after "next turn"
    }

    private fun updateTilegroupsForSelectedUnit(unit: MapUnit, playerViewableTilePositions: HashSet<Vector2>) {
        val tileGroup = tileGroups[unit.getTile()]?: return
        // Entirely unclear when this happens, but this seems to happen since version 520 (3.12.9)
        // so maybe has to do with the construction list being asyc?
        for (group in tileGroup){
            group.selectUnit(unit)
        }

        val isAirUnit = unit.type.isAirUnit()
        val tilesInMoveRange =
                if (isAirUnit)
                    unit.getTile().getTilesInDistanceRange(IntRange(1, unit.getRange() * 2))
                else
                    unit.movement.getDistanceToTiles().keys.asSequence()

        for (tile in tilesInMoveRange) {
            for (tileToColor in tileGroups[tile]!!){
                if (isAirUnit)
                    if (tile.aerialDistanceTo(unit.getTile()) <= unit.getRange()) {
                        // The tile is within attack range
                        tileToColor.showCircle(Color.RED, 0.3f)
                    } else {
                        // The tile is within move range
                        tileToColor.showCircle(Color.BLUE, 0.3f)
                    }
                if (unit.movement.canMoveTo(tile) ||
                        unit.movement.isUnknownTileWeShouldAssumeToBePassable(tile) && !unit.type.isAirUnit())
                    tileToColor.showCircle(Color.WHITE,
                            if (UncivGame.Current.settings.singleTapMove || isAirUnit) 0.7f else 0.3f)
            }
        }

        val attackableTiles: List<AttackableTile> = if (unit.type.isCivilian()) listOf()
        else {
            BattleHelper.getAttackableEnemies(unit, unit.movement.getDistanceToTiles())
                    .filter {
                        (UncivGame.Current.viewEntireMapForDebug ||
                                playerViewableTilePositions.contains(it.tileToAttack.position))
                    }
                    .distinctBy { it.tileToAttack }
        }

        for (attackableTile in attackableTiles) {
            for (tileGroup in tileGroups[attackableTile.tileToAttack]!!){
                tileGroup.showCircle(colorFromRGB(237, 41, 57))
                tileGroup.showCrosshair(
                        // the targets which cannot be attacked without movements shown as orange-ish
                        if (attackableTile.tileToAttackFrom != unit.currentTile)
                            colorFromRGB(255, 75, 0)
                        else Color.RED
                )
            }

        }

        // Fade out less relevant images if a military unit is selected
        val fadeout = if (unit.type.isCivilian()) 1f
        else 0.5f
        for (tile in allWorldTileGroups) {
            if (tile.icons.populationIcon != null) tile.icons.populationIcon!!.color.a = fadeout
            if (tile.icons.improvementIcon != null && tile.tileInfo.improvement != Constants.barbarianEncampment
                    && tile.tileInfo.improvement != Constants.ancientRuins)
                tile.icons.improvementIcon!!.color.a = fadeout
            if (tile.resourceImage != null) tile.resourceImage!!.color.a = fadeout
        }
    }

    private fun updateTilegroupsForSelectedCity(city: CityInfo, playerViewableTilePositions: HashSet<Vector2>) {
        if (city.attackedThisTurn) return

        val attackableTiles = UnitAutomation.getBombardTargets(city)
                .filter { (UncivGame.Current.viewEntireMapForDebug || playerViewableTilePositions.contains(it.position)) }
        for (attackableTile in attackableTiles) {
            for (group in tileGroups[attackableTile]!!) {
                group.showCircle(colorFromRGB(237, 41, 57))
                group.showCrosshair(Color.RED)
            }
        }
    }

    var blinkAction: Action? = null
    fun setCenterPosition(vector: Vector2, immediately: Boolean = false, selectUnit: Boolean = true) {
        val tileGroup = allWorldTileGroups.firstOrNull { it.tileInfo.position == vector } ?: return
        selectedTile = tileGroup.tileInfo
        if (selectUnit)
            worldScreen.bottomUnitTable.tileSelected(selectedTile!!)

        val originalScrollX = scrollX
        val originalScrollY = scrollY

        // We want to center on the middle of the tilegroup (TG.getX()+TG.getWidth()/2)
        // and so the scroll position (== filter the screen starts) needs to be half the ScrollMap away
        val finalScrollX = tileGroup.x + tileGroup.width / 2 - width / 2

        // Here it's the same, only the Y axis is inverted - when at 0 we're at the top, not bottom - so we invert it back.
        val finalScrollY = maxY - (tileGroup.y + tileGroup.width / 2 - height / 2)

        if (immediately) {
            scrollX = finalScrollX
            scrollY = finalScrollY
            updateVisualScroll()
        } else {
            val action = object : FloatAction(0f, 1f, 0.4f) {
                override fun update(percent: Float) {
                    scrollX = finalScrollX * percent + originalScrollX * (1 - percent)
                    scrollY = finalScrollY * percent + originalScrollY * (1 - percent)
                    updateVisualScroll()
                }
            }
            action.interpolation = Interpolation.sine
            addAction(action)
        }

        removeAction(blinkAction) // so we don't have multiple blinks at once
        blinkAction = Actions.repeat(3, Actions.sequence(
                Actions.run { tileGroup.circleImage.isVisible = false },
                Actions.delay(.3f),
                Actions.run { tileGroup.circleImage.isVisible = true },
                Actions.delay(.3f)
        ))
        addAction(blinkAction) // Don't set it on the group because it's an actionlss group

        worldScreen.shouldUpdate = true
    }

    override fun zoom(zoomScale: Float) {
        super.zoom(zoomScale)
        val scale = 1 / scaleX  // don't use zoomScale itself, in case it was out of bounds and not applied
        if (scale >= 1)
            for (tileGroup in allWorldTileGroups)
                tileGroup.cityButtonLayerGroup.isTransform = false // to save on rendering time to improve framerate
        if (scale < 1 && scale > 0.5f)
            for (tileGroup in allWorldTileGroups) {
                // ONLY set those groups that have active citybuttons as transformable!
                // This is massively framerate-improving!
                if (tileGroup.cityButtonLayerGroup.hasChildren())
                    tileGroup.cityButtonLayerGroup.isTransform = true
                tileGroup.cityButtonLayerGroup.setScale(scale)
            }
    }

    fun removeUnitActionOverlay(){
        for (overlay in unitActionOverlays)
            overlay.remove()
        unitActionOverlays.clear()
    }

    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)

    override fun act(delta: Float) = super.act(delta)
}