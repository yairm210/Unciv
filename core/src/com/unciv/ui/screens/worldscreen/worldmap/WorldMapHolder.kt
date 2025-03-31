package com.unciv.ui.screens.worldscreen.worldmap

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Action
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.math.Interpolation
import com.unciv.UncivGame
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.MapPathing
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.movement.UnitMovement
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Spy
import com.unciv.models.UncivSound
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.MapArrowType
import com.unciv.ui.components.MiscArrowTypes
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.isShiftKeyPressed
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.input.ActivationTypes
import com.unciv.ui.components.input.ClickableCircle
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.tilegroups.TileGroupMap
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.components.tilegroups.WorldTileGroup
import com.unciv.ui.components.widgets.UnitIconGroup
import com.unciv.ui.components.widgets.ZoomableScrollPane
import com.unciv.ui.screens.basescreen.UncivStage
import com.unciv.ui.screens.worldscreen.UndoHandler.Companion.recordUndoCheckpoint
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.bottombar.BattleTableHelpers.battleAnimationDeferred
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import com.unciv.utils.launchOnGLThread
import java.lang.Float.max


class WorldMapHolder(
    internal val worldScreen: WorldScreen,
    internal val tileMap: TileMap
) : ZoomableScrollPane(20f, 20f) {
    internal var selectedTile: Tile? = null
    val tileGroups = HashMap<Tile, WorldTileGroup>()

    /** Holds buttons created by [OverlayButtonData] implementations */
    internal val unitActionOverlays: ArrayList<Actor> = ArrayList()

    internal val unitMovementPaths: HashMap<MapUnit, ArrayList<Tile>> = HashMap()

    internal val unitConnectRoadPaths: HashMap<MapUnit, List<Tile>> = HashMap()

    private lateinit var tileGroupMap: TileGroupMap<WorldTileGroup>

    lateinit var currentTileSetStrings: TileSetStrings

    init {
        if (Gdx.app.type == Application.ApplicationType.Desktop) this.setFlingTime(0f)
        continuousScrollingX = tileMap.mapParameters.worldWrap
        setupZoomPanListeners()
    }

    /**
     * When scrolling or zooming the world map, there are three unnecessary (at least currently) things happening that take a decent amount of time:
     *
     * 1. Checking which [Actor]'s bounds the pointer (mouse/finger) entered+exited and sending appropriate events to these actors
     * 2. Running all [Actor.act] methods of all child [Actor]s
     * 3. Running all [Actor.hit] methods of all child [Actor]s
     *
     * Disabling them while panning/zooming increases the frame rate by approximately 100%.
     */
    private fun setupZoomPanListeners() {

        fun setActHit() {
            val isEnabled = !isZooming() && !isPanning
            (stage as UncivStage).performPointerEnterExitEvents = isEnabled
            tileGroupMap.shouldAct = isEnabled
            tileGroupMap.shouldHit = isEnabled
        }

        onPanStartListener = { setActHit() }
        onPanStopListener = { setActHit() }
        onZoomStartListener = { setActHit() }
        onZoomStopListener = { setActHit() }
    }


    internal fun addTiles() {
        val tileSetStrings = TileSetStrings(worldScreen.gameInfo.ruleset, worldScreen.game.settings)
        currentTileSetStrings = tileSetStrings
        val tileGroupsNew = tileMap.values.map { WorldTileGroup(it, tileSetStrings) }
        tileGroupMap = TileGroupMap(this, tileGroupsNew, continuousScrollingX)

        for (tileGroup in tileGroupsNew) {
            tileGroups[tileGroup.tile] = tileGroup
            tileGroup.layerCityButton.onClick(UncivSound.Silent) {
                onTileClicked(tileGroup.tile)
            }
            tileGroup.onClick { onTileClicked(tileGroup.tile) }

            // Right mouse click on desktop / Longpress on Android, and no equivalence mapping between those two,
            // because on 'droid two-finger tap is mapped to right click and dissent has been expressed
            tileGroup.onActivation(
                type = if (Gdx.app.type == Application.ApplicationType.Android)
                    ActivationTypes.Longpress else ActivationTypes.RightClick,
                noEquivalence = true
            ) {
                if (!UncivGame.Current.settings.longTapMove) return@onActivation
                val unit = worldScreen.bottomUnitTable.selectedUnit
                    ?: return@onActivation
                Concurrency.run("WorldScreenClick") {
                    onTileRightClicked(unit, tileGroup.tile)
                }
            }
        }
        actor = tileGroupMap
        setSize(worldScreen.stage.width, worldScreen.stage.height)
        layout() // Fit the scroll pane to the contents - otherwise, setScroll won't work!
    }

    fun onTileClicked(tile: Tile) {

        if (!worldScreen.viewingCiv.hasExplored(tile)
                && tile.neighbors.all { worldScreen.viewingCiv.hasExplored(it) })
            return // This tile doesn't exist for you

        removeUnitActionOverlay()
        selectedTile = tile
        unitMovementPaths.clear()
        unitConnectRoadPaths.clear()

        val unitTable = worldScreen.bottomUnitTable
        val previousSelectedUnits = unitTable.selectedUnits.toList() // create copy
        val previousSelectedCity = unitTable.selectedCity
        val previousSelectedUnitIsSwapping = unitTable.selectedUnitIsSwapping
        val previousSelectedUnitIsConnectingRoad = unitTable.selectedUnitIsConnectingRoad
        val movingSpyOnMap = unitTable.selectedSpy != null
        if (!movingSpyOnMap)
            unitTable.tileSelected(tile)
        val newSelectedUnit = unitTable.selectedUnit

        if (previousSelectedCity != null && tile != previousSelectedCity.getCenterTile() && !movingSpyOnMap)
            tileGroups[previousSelectedCity.getCenterTile()]!!.layerCityButton.moveUp()

        if (previousSelectedUnits.isNotEmpty()) {
            val isTileDifferent = previousSelectedUnits.any { it.getTile() != tile }
            val isPlayerTurn = worldScreen.isPlayersTurn
            val existsUnitNotPreparingAirSweep = previousSelectedUnits.any { !it.isPreparingAirSweep() }

            // Todo: valid tiles for actions should be handled internally, not here.
            val canPerformActionsOnTile = if (previousSelectedUnitIsSwapping) {
                previousSelectedUnits.first().movement.canUnitSwapTo(tile)
            } else if(previousSelectedUnitIsConnectingRoad) {
                true
            } else {
                previousSelectedUnits.any {
                    it.movement.canMoveTo(tile) ||
                        (it.movement.isUnknownTileWeShouldAssumeToBePassable(tile) && !it.baseUnit.movesLikeAirUnits)
                }
            }

            if (isTileDifferent && isPlayerTurn && canPerformActionsOnTile && existsUnitNotPreparingAirSweep) {
                when {
                    previousSelectedUnitIsSwapping -> addTileOverlaysWithUnitSwapping(previousSelectedUnits.first(), tile)
                    previousSelectedUnitIsConnectingRoad -> addTileOverlaysWithUnitRoadConnecting(previousSelectedUnits.first(), tile)
                    else -> addTileOverlaysWithUnitMovement(previousSelectedUnits, tile) // Long-running task
                }
            }
        } else if (movingSpyOnMap) {
            addMovingSpyOverlay(unitTable.selectedSpy!!, tile)
        } else {
            addTileOverlays(tile) // no unit movement but display the units in the tile etc.
        }

        if (newSelectedUnit == null || newSelectedUnit.isCivilian()) {
            val unitsInTile = selectedTile!!.getUnits()
            if (previousSelectedCity != null && previousSelectedCity.canBombard()
                    && selectedTile!!.getTilesInDistance(2).contains(previousSelectedCity.getCenterTile())
                    && unitsInTile.any()
                    && unitsInTile.first().civ.isAtWarWith(worldScreen.viewingCiv)) {
                // try to select the closest city to bombard this guy
                unitTable.citySelected(previousSelectedCity)
            }
        }
        worldScreen.shouldUpdate = true
    }

    private fun onTileRightClicked(unit: MapUnit, tile: Tile) {
        removeUnitActionOverlay()
        selectedTile = tile
        unitMovementPaths.clear()
        unitConnectRoadPaths.clear()
        if (!worldScreen.canChangeState) return

        // Concurrency might open up a race condition window - if worldScreen.shouldUpdate is on too
        // early, concurrent code might possibly call worldScreen.render() and then our request will be
        // 'consumed' prematurely, and worse, the update might update and show the BattleTable for our
        // right-click attack, and leave it visible after we have resolved the battle here in code -
        // including its onClick closures which will be outdated if the user clicks Attack -> crash!
        var localShouldUpdate = worldScreen.shouldUpdate
        worldScreen.shouldUpdate = false
        // Below, there's 4 outcomes, one of which will have done nothing and will restore the old
        // shouldUpdate - maybe overkill done in a "better safe than sorry" mindset.

        if (worldScreen.bottomUnitTable.selectedUnitIsSwapping) {
            /** ****** Right-click Swap ****** */
            if (unit.movement.canUnitSwapTo(tile)) {
                swapMoveUnitToTargetTile(unit, tile)
                localShouldUpdate = true
            }
            /** If we are in unit-swapping mode and didn't find a swap partner, we don't want to move or attack */
        } else {
            // This seems inefficient as the tileToAttack is already known - but the method also calculates tileToAttackFrom
            val attackableTile = TargetHelper
                    .getAttackableEnemies(unit, unit.movement.getDistanceToTiles())
                    .firstOrNull { it.tileToAttack == tile }
            if (unit.canAttack() && attackableTile != null) {
                /** ****** Right-click Attack ****** */
                val attacker = MapUnitCombatant(unit)
                if (!Battle.movePreparingAttack(attacker, attackableTile)) return
                SoundPlayer.play(attacker.getAttackSound())
                val (damageToDefender, damageToAttacker) = Battle.attackOrNuke(attacker, attackableTile)
                if (attackableTile.combatant != null)
                    worldScreen.battleAnimationDeferred(attacker, damageToAttacker, attackableTile.combatant, damageToDefender)
                localShouldUpdate = true
            } else if (unit.movement.canReach(tile)) {
                /** ****** Right-click Move ****** */
                moveUnitToTargetTile(listOf(unit), tile)
                localShouldUpdate = true
            }
        }
        worldScreen.shouldUpdate = localShouldUpdate
    }

    private fun markUnitMoveTutorialComplete(unit: MapUnit) {
        val key = if (unit.baseUnit.movesLikeAirUnits) "Move an air unit" else "Move unit"
        UncivGame.Current.settings.addCompletedTutorialTask(key)
    }

    internal fun moveUnitToTargetTile(selectedUnits: List<MapUnit>, targetTile: Tile) {
        // this can take a long time, because of the unit-to-tile calculation needed, so we put it in a different thread
        // THIS PART IS REALLY ANNOYING
        // So lets say you have 2 units you want to move in the same direction, right
        // But if the first one gets there, and the second one was PLANNING on going there, then now it can't and has to rethink
        // So basically, THE UNIT MOVES HAVE TO BE SEQUENTIAL and not concurrent which is a BITCH
        // So we do this one at a time by getting the list of units to move, MOVING ONE OF THEM with all the yukky threading,
        // and then calling the function again but without the unit that moved.

        val selectedUnit = selectedUnits.first()
        markUnitMoveTutorialComplete(selectedUnit) // not too expensive to have it repeat too often

        Concurrency.run("TileToMoveTo") {
            // these are the heavy parts, finding where we want to go
            // Since this runs in a different thread, even if we check movement.canReach()
            // then it might change until we get to the getTileToMoveTo, so we just try/catch it
            val tileToMoveTo: Tile
            var pathToTile: List<Tile>? = null
            try {
                tileToMoveTo = selectedUnit.movement.getTileToMoveToThisTurn(targetTile)
                if (!selectedUnit.type.isAirUnit() && !selectedUnit.isPreparingParadrop())
                    pathToTile = selectedUnit.movement.getDistanceToTiles().getPathToTile(tileToMoveTo)
            } catch (ex: Exception) {
                when (ex) {
                    is UnitMovement.UnreachableDestinationException -> {
                        // This is normal e.g. when selecting an air unit then right-clicking on an empty tile
                        // Or telling a ship to run onto a coastal land tile.
                        // Do nothing
                    }
                    else -> Log.error("Exception in getTileToMoveToThisTurn", ex)
                }
                return@run // can't move here
            }


            worldScreen.recordUndoCheckpoint()

            launchOnGLThread {
                try {
                    // Because this is darned concurrent (as it MUST be to avoid ANRs),
                    // there are edge cases where the canReach is true,
                    // but until it reaches the headTowards the board has changed and so the headTowards fails.
                    // I can't think of any way to avoid this,
                    // but it's so rare and edge-case-y that ignoring its failure is actually acceptable, hence the empty catch
                    val previousTile = selectedUnit.currentTile
                    selectedUnit.movement.moveToTile(tileToMoveTo)
                    if (selectedUnit.isExploring() || selectedUnit.isMoving())
                        selectedUnit.action = null // remove explore on manual move
                    SoundPlayer.play(UncivSound.Whoosh)
                    if (selectedUnit.currentTile != targetTile)
                        selectedUnit.action =
                                "moveTo ${targetTile.position.x.toInt()},${targetTile.position.y.toInt()}"
                    if (selectedUnit.hasMovement()) worldScreen.bottomUnitTable.selectUnit(selectedUnit)

                    worldScreen.shouldUpdate = true

                    if (pathToTile != null) {
                        animateMovement(previousTile, selectedUnit, tileToMoveTo, pathToTile)
                        if (selectedUnit.isEscorting()) {
                            animateMovement(previousTile, selectedUnit.getOtherEscortUnit()!!, tileToMoveTo, pathToTile)
                        }
                    }

                    if (selectedUnits.size > 1) { // We have more tiles to move
                        moveUnitToTargetTile(selectedUnits.subList(1, selectedUnits.size), targetTile)
                    } else removeUnitActionOverlay() //we're done here

                    if (UncivGame.Current.settings.autoUnitCycle && !selectedUnit.hasMovement())
                        worldScreen.switchToNextUnit()

                } catch (ex: Exception) {
                    Log.error("Exception in moveUnitToTargetTile", ex)
                }
            }
        }
    }

    private fun animateMovement(
        previousTile: Tile,
        selectedUnit: MapUnit,
        targetTile: Tile,
        pathToTile: List<Tile>
    ) {
        val tileGroup = tileGroups[previousTile]!!

        // Steal the current sprites to our new group
        val unitSpriteAndIcon = Group().apply { setPosition(tileGroup.x, tileGroup.y) }
        val unitSpriteSlot = tileGroup.layerUnitArt.getSpriteSlot(selectedUnit) ?: return
        
        for (spriteImage in unitSpriteSlot.spriteGroup.children) unitSpriteAndIcon.addActor(spriteImage)
        tileGroup.parent.addActor(unitSpriteAndIcon)

        

        unitSpriteAndIcon.addAction(
            Actions.sequence(
                Actions.run {
                    // Disable the final tile, so we won't have one image "merging into" the other
                    // Can only be done after the new group has been updated, to get the spriteGroup
                    val targetTileSpriteSlot = tileGroups[targetTile]!!.layerUnitArt.getSpriteSlot(selectedUnit)
                    targetTileSpriteSlot?.spriteGroup?.isVisible = false
                },
                *pathToTile.map { tile ->
                    Actions.moveTo(
                        tileGroups[tile]!!.x,
                        tileGroups[tile]!!.y,
                        0.5f / pathToTile.size
                    )
                }.toTypedArray(),
                Actions.run {
                    // Re-enable the final tile
                    val targetTileSpriteSlot = tileGroups[targetTile]!!.layerUnitArt.getSpriteSlot(selectedUnit)
                    targetTileSpriteSlot?.spriteGroup?.isVisible = true
                    worldScreen.shouldUpdate = true
                },
                Actions.removeActor(),
            )
        )
    }

    internal fun swapMoveUnitToTargetTile(selectedUnit: MapUnit, targetTile: Tile) {
        markUnitMoveTutorialComplete(selectedUnit)
        selectedUnit.movement.swapMoveToTile(targetTile)

        if (selectedUnit.isExploring() || selectedUnit.isMoving())
            selectedUnit.action = null // remove explore on manual swap-move

        // Play something like a swish-swoosh
        SoundPlayer.play(UncivSound.Swap)

        if (selectedUnit.hasMovement()) worldScreen.bottomUnitTable.selectUnit(selectedUnit)

        worldScreen.shouldUpdate = true
        removeUnitActionOverlay()
    }

    private fun addTileOverlaysWithUnitMovement(selectedUnits: List<MapUnit>, tile: Tile) {
        Concurrency.run("TurnsToGetThere") {
            /** LibGdx sometimes has these weird errors when you try to edit the UI layout from 2 separate threads.
             * And so, all UI editing will be done on the main thread.
             * The only "heavy lifting" that needs to be done is getting the turns to get there,
             * so that and that alone will be relegated to the concurrent thread.
             */

            val unitToTurnsToTile = HashMap<MapUnit, Int>()
            for (unit in selectedUnits) {
                val shortestPath = ArrayList<Tile>()
                val turnsToGetThere = if (unit.baseUnit.movesLikeAirUnits) {
                    if (unit.movement.canReach(tile)) 1
                    else 0
                } else if (unit.isPreparingParadrop()) {
                    if (unit.movement.canReach(tile)) 1
                    else 0
                } else {
                    // this is the most time-consuming call
                    shortestPath.addAll(unit.movement.getShortestPath(tile))
                    shortestPath.size
                }
                unitMovementPaths[unit] = shortestPath
                unitToTurnsToTile[unit] = turnsToGetThere
            }

            launchOnGLThread {
                val unitsWhoCanMoveThere = HashMap(unitToTurnsToTile.filter { it.value != 0 })
                if (unitsWhoCanMoveThere.isEmpty()) { // give the regular tile overlays with no unit movement
                    addTileOverlays(tile)
                    worldScreen.shouldUpdate = true
                    return@launchOnGLThread
                }

                val turnsToGetThere = unitsWhoCanMoveThere.values.maxOrNull()!!

                if (UncivGame.Current.settings.singleTapMove && turnsToGetThere == 1) {
                    // single turn instant move
                    val selectedUnit = unitsWhoCanMoveThere.keys.first()
                    for (unit in unitsWhoCanMoveThere.keys) {
                        unit.movement.headTowards(tile)
                    }
                    worldScreen.bottomUnitTable.selectUnit(selectedUnit) // keep moved unit selected
                } else {
                    // add "move to" button if there is a path to tileInfo
                    val moveHereButtonDto = MoveHereOverlayButtonData(unitsWhoCanMoveThere, tile)
                    addTileOverlays(tile, moveHereButtonDto)
                }
                worldScreen.shouldUpdate = true
            }
        }
    }

    private fun addTileOverlaysWithUnitSwapping(selectedUnit: MapUnit, tile: Tile) {
        if (!selectedUnit.movement.canUnitSwapTo(tile)) { // give the regular tile overlays with no unit swapping
            addTileOverlays(tile)
            worldScreen.shouldUpdate = true
            return
        }
        if (UncivGame.Current.settings.singleTapMove) {
            swapMoveUnitToTargetTile(selectedUnit, tile)
        }
        else {
            // Add "swap with" button
            val swapWithButtonDto = SwapWithOverlayButtonData(selectedUnit, tile)
            addTileOverlays(tile, swapWithButtonDto)
        }
        worldScreen.shouldUpdate = true
    }

    private fun addTileOverlaysWithUnitRoadConnecting(selectedUnit: MapUnit, tile: Tile){
        Concurrency.run("ConnectRoad") {
           val validTile = tile.isLand &&
               !tile.isImpassible() &&
                selectedUnit.civ.hasExplored(tile)

            if (validTile) {
                val roadPath: List<Tile>? = MapPathing.getRoadPath(selectedUnit, selectedUnit.currentTile, tile)
                launchOnGLThread {
                    if (roadPath == null) { // give the regular tile overlays with no road connection
                        addTileOverlays(tile)
                        worldScreen.shouldUpdate = true
                        return@launchOnGLThread
                    }
                    unitConnectRoadPaths[selectedUnit] = roadPath
                    val connectRoadButtonDto = ConnectRoadOverlayButtonData(selectedUnit, tile)
                    addTileOverlays(tile, connectRoadButtonDto)
                    worldScreen.shouldUpdate = true
                }
            }
        }
    }

    private fun addMovingSpyOverlay(spy: Spy, tile: Tile) {
        val city: City? = if (tile.isCityCenter() && spy.canMoveTo(tile.getCity()!!)) tile.getCity() else null
        addTileOverlays(tile, MoveSpyOverlayButtonData(spy, city))
        worldScreen.shouldUpdate = true
    }

    private fun addTileOverlays(tile: Tile, buttonDto: OverlayButtonData? = null) {
        val table = Table().apply { defaults().pad(10f) }
        if (buttonDto != null && worldScreen.canChangeState)
            table.add(buttonDto.createButton(this))

        val unitList = ArrayList<MapUnit>()
        if (tile.isCityCenter()
                && (tile.getOwner() == worldScreen.viewingCiv || worldScreen.viewingCiv.isSpectator())) {
            unitList.addAll(tile.getCity()!!.getCenterTile().getUnits())
        } else if (tile.airUnits.isNotEmpty()
                && (tile.airUnits.first().civ == worldScreen.viewingCiv || worldScreen.viewingCiv.isSpectator())) {
            unitList.addAll(tile.getUnits())
        }

        for (unit in unitList) {
            val unitIconGroup = UnitIconGroup(unit, 48f).surroundWithCircle(68f, resizeActor = false)
            unitIconGroup.circle.color = Color.GRAY.cpy().apply { a = 0.5f }
            if (!unit.hasMovement()) unitIconGroup.color.a = 0.66f
            val clickableCircle = ClickableCircle(68f)
            clickableCircle.touchable = Touchable.enabled
            clickableCircle.onClick {
                worldScreen.bottomUnitTable.selectUnit(unit, Gdx.input.isShiftKeyPressed())
                worldScreen.shouldUpdate = true
                removeUnitActionOverlay()
            }
            unitIconGroup.addActor(clickableCircle)
            table.add(unitIconGroup)
        }

        addOverlayOnTileGroup(tileGroups[tile]!!, table)
        if (UncivGame.Current.settings.unitMovementButtonAnimation) {
            table.color.a = 0f
            table.addAction(Actions.moveBy(0f, 48f, 0.15f, Interpolation.smooth))
            table.addAction(Actions.alpha(1f, 0.15f, Interpolation.smooth))
        }
        else
            table.moveBy(0f, 48f)
    }

    fun addOverlayOnTileGroup(group: TileGroup, actor: Actor) {

        actor.center(group)
        actor.x += group.x
        actor.y += group.y
        group.parent.addActor(actor) // Add the overlay to the TileGroupMap - it's what actually displays all the tiles
        actor.toFront()

        actor.y += actor.height
        actor.setOrigin(Align.bottom)
        unitActionOverlays.add(actor)
    }

    /** Returns true when the civ is a human player defeated in singleplayer game */
    fun isMapRevealEnabled(viewingCiv: Civilization) = !viewingCiv.gameInfo.gameParameters.isOnlineMultiplayer
            && viewingCiv.isCurrentPlayer()
            && viewingCiv.isDefeated()

    /** Clear all arrows to be drawn on the next update. */
    fun resetArrows() {
        for (tile in tileGroups.asSequence())
            tile.value.layerMisc.resetArrows()
    }

    /** Add an arrow to draw on the next update. */
    fun addArrow(fromTile: Tile, toTile: Tile, arrowType: MapArrowType) {
        tileGroups[fromTile]?.layerMisc?.addArrow(toTile, arrowType)
    }

    /**
     * Add arrows to show all past and planned movements and attacks, if the options setting to do so is enabled.
     *
     * @param pastVisibleUnits Sequence of [MapUnit]s for which the last turn's movement history can be displayed.
     * @param targetVisibleUnits Sequence of [MapUnit]s for which the active movement target can be displayed.
     * @param visibleAttacks Sequence of pairs of [Vector2] positions of the sources and the targets of all attacks that can be displayed.
     * */
    internal fun updateMovementOverlay(pastVisibleUnits: Sequence<MapUnit>, targetVisibleUnits: Sequence<MapUnit>, visibleAttacks: Sequence<Pair<Vector2, Vector2>>) {
        val selectedUnit = worldScreen.bottomUnitTable.selectedUnit
        for (unit in pastVisibleUnits) {
            if (unit.movementMemories.isEmpty()) continue
            if (selectedUnit != null && selectedUnit != unit) continue // When selecting a unit, show only arrows of that unit
            val stepIter = unit.movementMemories.iterator()
            var previous = stepIter.next()
            while (stepIter.hasNext()) {
                val next = stepIter.next()
                addArrow(tileMap[previous.position], tileMap[next.position], next.type)
                previous = next
            }
            addArrow(tileMap[previous.position], unit.getTile(),  unit.mostRecentMoveType)
        }
        for (unit in targetVisibleUnits) {
            if (!unit.isMoving())
                continue
            val toTile = unit.getMovementDestination()
            addArrow(unit.getTile(), toTile, MiscArrowTypes.UnitMoving)
        }
        for ((from, to) in visibleAttacks) {
            if (selectedUnit != null
                && selectedUnit.currentTile.position != from
                && selectedUnit.currentTile.position != to) continue
            addArrow(tileMap[from], tileMap[to], MiscArrowTypes.UnitHasAttacked)
        }
    }


    var blinkAction: Action? = null

    /** Scrolls the world map to specified coordinates.
     * @param vector Position to center on
     * @param immediately Do so without animation
     * @param selectUnit Select a unit at the destination
     * @return `true` if scroll position was changed, `false` otherwise
     */
    fun setCenterPosition(vector: Vector2, immediately: Boolean = false, selectUnit: Boolean = true, forceSelectUnit: MapUnit? = null): Boolean {
        val tileGroup = tileGroups.values.firstOrNull { it.tile.position == vector } ?: return false
        selectedTile = tileGroup.tile
        if (selectUnit || forceSelectUnit != null)
            worldScreen.bottomUnitTable.tileSelected(selectedTile!!, forceSelectUnit)

        // The Y axis of [scrollY] is inverted - when at 0 we're at the top, not bottom - so we invert it back.
        if (!scrollTo(tileGroup.x + tileGroup.width / 2, maxY - (tileGroup.y + tileGroup.width / 2), immediately))
            return false

        removeAction(blinkAction) // so we don't have multiple blinks at once
        blinkAction = Actions.repeat(3, Actions.sequence(
                Actions.run { tileGroup.layerOverlay.hideHighlight()},
                Actions.delay(.3f),
                Actions.run { tileGroup.layerOverlay.showHighlight()},
                Actions.delay(.3f)
        ))
        addAction(blinkAction) // Don't set it on the group because it's an actionless group

        worldScreen.shouldUpdate = true
        return true
    }

    override fun zoom(zoomScale: Float) {
        super.zoom(zoomScale)
        clampCityButtonSize()
    }

    /** We don't want the city buttons becoming too large when zooming out */
    private fun clampCityButtonSize() {
        // use scaleX instead of zoomScale itself, because zoomScale might have been outside minZoom..maxZoom and thus not applied
        val clampedCityButtonZoom = 1 / scaleX
        if (clampedCityButtonZoom >= 1) {
            for (tileGroup in tileGroups.values) {
                tileGroup.layerCityButton.isTransform = false // to save on rendering time to improve framerate
            }
        } else if (clampedCityButtonZoom >= minZoom) {
            for (tileGroup in tileGroups.values) {
                // ONLY set those groups that have active city buttons as transformable!
                // This is massively framerate-improving!
                if (tileGroup.layerCityButton.hasChildren())
                    tileGroup.layerCityButton.isTransform = true
                tileGroup.layerCityButton.setScale(clampedCityButtonZoom)
            }
        }
    }

    fun removeUnitActionOverlay() {
        for (overlay in unitActionOverlays)
            overlay.remove()
        unitActionOverlays.clear()
    }

    override fun reloadMaxZoom() {
        val maxWorldZoomOut = UncivGame.Current.settings.maxWorldZoomOut
        val mapRadius = tileMap.mapParameters.mapSize.radius

        // Limit max zoom out by the map width
        val enableZoomLimit = (mapRadius < 21 && maxWorldZoomOut < 3f) || (mapRadius > 20 && maxWorldZoomOut < 4f)

        if (enableZoomLimit) {
            // For world-wrap we limit minimal possible zoom to content width + some extra offset
            // to hide one column of tiles so that the player doesn't see it teleporting from side to side
            val pad = if (continuousScrollingX) width / mapRadius * 0.7f else 0f
            minZoom = max(
                (width + pad) * scaleX / maxX,
                1f / maxWorldZoomOut
            )// add some extra padding offset

            // If the window becomes too wide and minZoom > maxZoom, we cannot zoom
            maxZoom = max(2f * minZoom, maxWorldZoomOut)
        }
        else
            super.reloadMaxZoom()
    }

    override fun restrictX(deltaX: Float): Float {
        var result = scrollX - deltaX
        if (worldScreen.viewingCiv.isSpectator()) return result

        val exploredRegion = worldScreen.viewingCiv.exploredRegion
        if (exploredRegion.shouldRecalculateCoords()) exploredRegion.calculateStageCoords(maxX, maxY)
        if (!exploredRegion.shouldRestrictX()) return result

        val leftX = exploredRegion.getLeftX()
        val rightX = exploredRegion.getRightX()

        if (deltaX < 0 && scrollX <= rightX && result > rightX)
            result = rightX
        else if (deltaX > 0 && scrollX >= leftX && result < leftX)
            result = leftX

        return result
    }

    override fun restrictY(deltaY: Float): Float {
        var result = scrollY + deltaY
        if (worldScreen.viewingCiv.isSpectator()) return result

        val exploredRegion = worldScreen.viewingCiv.exploredRegion
        if (exploredRegion.shouldRecalculateCoords()) exploredRegion.calculateStageCoords(maxX, maxY)

        val topY = exploredRegion.getTopY()
        val bottomY = exploredRegion.getBottomY()

        if (result < topY) result = topY
        else if (result > bottomY) result = bottomY

        return result
    }

    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)
    override fun act(delta: Float) = super.act(delta)
    override fun clear() = super.clear()
}
