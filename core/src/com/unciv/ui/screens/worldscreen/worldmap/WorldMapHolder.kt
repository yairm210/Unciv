package com.unciv.ui.screens.worldscreen.worldmap

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.actions.Actions
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.*
import com.unciv.UncivGame
import com.unciv.logic.battle.Battle
import com.unciv.logic.battle.MapUnitCombatant
import com.unciv.logic.battle.TargetHelper
import com.unciv.logic.city.City
import com.unciv.logic.map.*
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.mapunit.movement.UnitMovement
import com.unciv.logic.map.tile.Tile
import com.unciv.models.Spy
import com.unciv.models.UncivSound
import com.unciv.view.CivView
import com.unciv.view.ForeignMapUnitView
import com.unciv.view.MapUnitView
import com.unciv.view.TileView
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.MapArrowType
import com.unciv.ui.components.MiscArrowTypes
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.isShiftKeyPressed
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.input.*
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.tilegroups.TileGroupMap
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.components.tilegroups.WorldTileGroup
import com.unciv.ui.components.tilegroups.citybutton.CityButton
import com.unciv.ui.components.widgets.UnitIconGroup
import com.unciv.ui.components.widgets.ZoomableScrollPane
import com.unciv.ui.screens.basescreen.UncivStage
import com.unciv.ui.screens.worldscreen.UndoHandler.Companion.recordUndoCheckpoint
import com.unciv.ui.screens.worldscreen.WorldScreen
import com.unciv.ui.screens.worldscreen.bottombar.BattleTableHelpers.battleAnimationDeferred
import com.unciv.utils.Concurrency
import com.unciv.utils.Log
import com.unciv.utils.launchOnGLThread
import yairm210.purity.annotations.Readonly
import java.lang.Float.max


class WorldMapHolder(
    internal val worldScreen: WorldScreen,
    internal val tileMap: TileMap
) : ZoomableScrollPane(20f, 20f) {
    internal var selectedTile: TileView? = null
    val tileGroups = HashMap<TileView, WorldTileGroup>()

    /** Holds buttons created by [OverlayButtonData] implementations */
    internal val unitActionOverlays: ArrayList<Actor> = ArrayList()

    internal val unitMovementPaths: HashMap<MapUnitView, ArrayList<TileView>> = HashMap()

    internal val unitConnectRoadPaths: HashMap<MapUnitView, List<TileView>> = HashMap()

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
        val tileMapView = worldScreen.selectedGameView.tileMapView
        val tileGroupsNew = tileMap.values.map { WorldTileGroup(tileMapView.getTile(it), tileSetStrings) }
        tileGroupMap = TileGroupMap(this, tileGroupsNew, continuousScrollingX)

        for (tileGroup in tileGroupsNew) tileGroups[tileGroup.tileView] = tileGroup

        addClickListener()

        actor = tileGroupMap
        setSize(worldScreen.stage.width, worldScreen.stage.height)
        layout() // Fit the scroll pane to the contents - otherwise, setScroll won't work!
    }

    private fun addClickListener() {
        // ActivationListener-like listener to allow us to create only one listener for the entire worldmapholder instead of one per tile
        val listener = object : UncivActorGestureListener() {
            override fun tap(event: InputEvent?, x: Float, y: Float, count: Int, button: Int) {
                val child = tileGroupMap.hit(x, y, true) ?: return

                if (child is CityButton) { // the city button can be below the tilegroup, since it moves down when first clicked
                    onTileClicked(child.foreignCityView.getCenterTile())
                    return
                }
                if (child is WorldTileGroup) {
                    Concurrency.runOnGLThread("Sound") { SoundPlayer.play(UncivSound.Click) }

                    if (button == 0) onTileClicked(child.tileView) // Regular click
                    else if (button == 1) { // Right button click = move unit to tile
                        if (!UncivGame.Current.settings.longTapMove) return
                        val unit = worldScreen.bottomUnitTable.selectedUnit
                            ?: return
                        onTileRightClicked(unit, child.tileView)
                    }
                }
            }

            override fun longPress(actor: Actor?, x: Float, y: Float): Boolean {
                if (actor == null) return false
                // See #10050 - when a tap discards its actor or ascendants, Gdx can't cancel the longpress timer
                if (actor.stage == null) return false

                if (!UncivGame.Current.settings.longTapMove) return false
                val unit = worldScreen.bottomUnitTable.selectedUnit
                    ?: return false
                if (Gdx.app.type != Application.ApplicationType.Android) return false

                val child = tileGroupMap.hit(x, y, true) ?: return false
                if (child !is WorldTileGroup) return false

                Concurrency.run("WorldScreenClick") {
                    onTileRightClicked(unit, child.tileView)
                }
                return true
            }
        }

        tileGroupMap.addListener(listener)
    }

    fun onTileClicked(tileView: TileView) {
        removeUnitActionOverlay()
        selectedTile = tileView
        unitMovementPaths.clear()
        unitConnectRoadPaths.clear()

        val unitTable = worldScreen.bottomUnitTable
        val previousSelectedUnitViews = unitTable.selectedUnits.toList() // create copy
        val previousSelectedCity = unitTable.selectedCity
        val previousSelectedUnitIsSwapping = unitTable.selectedUnitIsSwapping
        val previousSelectedUnitIsConnectingRoad = unitTable.selectedUnitIsConnectingRoad
        val movingSpyOnMap = unitTable.selectedSpy != null
        if (!movingSpyOnMap)
            unitTable.tileSelected(tileView)
        val newSelectedUnit = unitTable.selectedUnit

        if (previousSelectedCity != null && tileView != previousSelectedCity.getCenterTile() && !movingSpyOnMap)
            tileGroups[previousSelectedCity.getCenterTile()]!!.layerCityButton.moveUp()

        if (previousSelectedUnitViews.isNotEmpty()) {
            val isTileDifferent = previousSelectedUnitViews.any { it.getTile() != tileView }
            val isPlayerTurn = worldScreen.isPlayersTurn
            val existsUnitNotPreparingAirSweep = previousSelectedUnitViews.any { !it.isPreparingAirSweep() }

            // Todo: valid tiles for actions should be handled internally, not here.
            val canPerformActionsOnTile = if (previousSelectedUnitIsSwapping) {
                previousSelectedUnitViews.first().canSwapTo(tileView)
            } else if(previousSelectedUnitIsConnectingRoad) {
                true
            } else {
                previousSelectedUnitViews.any {
                    it.canMoveTo(tileView) ||
                        (it.isUnknownTileWeShouldAssumeToBePassable(tileView) && !it.isAirUnit())
                }
            }

            if (isTileDifferent && isPlayerTurn && canPerformActionsOnTile && existsUnitNotPreparingAirSweep) {
                when {
                    previousSelectedUnitIsSwapping -> addTileOverlaysWithUnitSwapping(previousSelectedUnitViews.first(), tileView)
                    previousSelectedUnitIsConnectingRoad -> addTileOverlaysWithUnitRoadConnecting(previousSelectedUnitViews.first(), tileView)
                    else -> addTileOverlaysWithUnitMovement(previousSelectedUnitViews, tileView) // Long-running task
                }
            }
        } else if (movingSpyOnMap) {
            addMovingSpyOverlay(unitTable.selectedSpy!!, tileView)
        } else {
            addTileOverlays(tileView) // no unit movement but display the units in the tile etc.
        }

        if (newSelectedUnit == null || newSelectedUnit.isCivilian()) {
            val unitsInTile = tileView.getVisibleUnits()
            if (previousSelectedCity != null && previousSelectedCity.canBombard()
                    && tileView.getVisibleTilesInDistance(2).contains(previousSelectedCity.getCenterTile())
                    && unitsInTile.any()
                    && unitsInTile.first().civ().isAtWarWith(worldScreen.selectedGameView.civView)) {
                // try to select the closest city to bombard this guy
                unitTable.citySelected(previousSelectedCity.getCity())
            }
        }
        worldScreen.shouldUpdate = true
    }

    private fun onTileRightClicked(unitView: MapUnitView, tileView: TileView) {
        val unit = unitView.getUnit()
        val tile = tileView.getTile()
        if (unitView.getTile() == tileView) return
        removeUnitActionOverlay()
        selectedTile = tileView
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
            if (unitView.canSwapTo(tileView)) {
                swapMoveUnitToTargetTile(unitView, tileView)
                localShouldUpdate = true
            }
            /** If we are in unit-swapping mode and didn't find a swap partner, we don't want to move or attack */
        } else {
            // This seems inefficient as the tileToAttack is already known - but the method also calculates tileToAttackFrom
            val attackableTile = TargetHelper
                    .getAttackableEnemies(unit, unit.movement.getDistanceToTiles())
                    .firstOrNull { it.tileToAttack == tile }
            if (unitView.canAttack() && attackableTile != null) {
                /** ****** Right-click Attack ****** */
                val attacker = MapUnitCombatant(unit)
                if (!Battle.movePreparingAttack(attacker, attackableTile)) return
                if (!SoundPlayer.play(UncivSound(attacker.getName())))
                    SoundPlayer.play(attacker.getAttackSound())
                val (damageToDefender, damageToAttacker) = Battle.attackOrNuke(attacker, attackableTile)
                if (attackableTile.combatant != null)
                    worldScreen.battleAnimationDeferred(attacker, damageToAttacker, attackableTile.combatant, damageToDefender)
                localShouldUpdate = true
            } else if (unitView.canReach(tileView)) {
                /** ****** Right-click Move ****** */
                moveUnitToTargetTile(listOf(unitView), tileView)
                localShouldUpdate = true
            }
        }
        worldScreen.shouldUpdate = localShouldUpdate
    }

    private fun markUnitMoveTutorialComplete(unitView: MapUnitView) {
        val key = if (unitView.isAirUnit()) "Move an air unit" else "Move unit"
        UncivGame.Current.settings.addCompletedTutorialTask(key)
    }

    internal fun moveUnitToTargetTile(selectedUnits: List<MapUnitView>, targetTileView: TileView) {
        val targetTile = targetTileView.getTile()
        // this can take a long time, because of the unit-to-tile calculation needed, so we put it in a different thread
        // THIS PART IS REALLY ANNOYING
        // So lets say you have 2 units you want to move in the same direction, right
        // But if the first one gets there, and the second one was PLANNING on going there, then now it can't and has to rethink
        // So basically, THE UNIT MOVES HAVE TO BE SEQUENTIAL and not concurrent which is a BITCH
        // So we do this one at a time by getting the list of units to move, MOVING ONE OF THEM with all the yukky threading,
        // and then calling the function again but without the unit that moved.

        val selectedUnitView = selectedUnits.first()
        val selectedUnit = selectedUnitView.getUnit()
        markUnitMoveTutorialComplete(selectedUnitView) // not too expensive to have it repeat too often

        Concurrency.run("TileToMoveTo") {
            // these are the heavy parts, finding where we want to go
            // Since this runs in a different thread, even if we check movement.canReach()
            // then it might change until we get to the getTileToMoveTo, so we just try/catch it
            val tileToMoveTo: Tile
            var pathToTile: List<Tile>? = null
            try {
                tileToMoveTo = selectedUnit.movement.getTileToMoveToThisTurn(targetTile)
                if (!selectedUnitView.isAirUnit() && !selectedUnitView.isPreparingParadrop())
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
                    val tileMapView = worldScreen.selectedGameView.tileMapView
                    val previousTileView = selectedUnitView.getTile()
                    selectedUnit.movement.moveToTile(tileToMoveTo)

                    // If you try to send a unit to a tile that it can't even get nearer to, then this is actualy a dud
                    if (previousTileView == selectedUnitView.getTile()){
                        removeUnitActionOverlay() // so the user knows the action 'has been performed'
                        return@launchOnGLThread
                    }

                    if (selectedUnitView.isExploring() || selectedUnitView.isMoving())
                        selectedUnitView.tryResetAction() // remove explore on manual move
                    SoundPlayer.play(UncivSound.Whoosh)
                    if (selectedUnitView.getTile() != targetTileView)
                        selectedUnitView.trySetMoveToAction(targetTileView)
                    if (selectedUnitView.hasMovement()) worldScreen.bottomUnitTable.selectUnit(selectedUnitView)

                    worldScreen.shouldUpdate = true

                    if (pathToTile != null) {
                        val tileToMoveToView = tileMapView.getTile(tileToMoveTo)
                        val pathToTileViews = pathToTile.map { tileMapView.getTile(it) }
                        animateMovement(previousTileView, selectedUnitView, tileToMoveToView, pathToTileViews)
                        if (selectedUnitView.isEscorting()) {
                            animateMovement(previousTileView, selectedUnitView.getOtherEscortUnit()!!, tileToMoveToView, pathToTileViews)
                        }
                    }

                    if (selectedUnits.size > 1) { // We have more tiles to move
                        moveUnitToTargetTile(selectedUnits.subList(1, selectedUnits.size), targetTileView)
                    } else removeUnitActionOverlay() //we're done here

                    if (UncivGame.Current.settings.autoUnitCycle && !selectedUnitView.hasMovement())
                        worldScreen.switchToNextUnit()

                } catch (ex: Exception) {
                    Log.error("Exception in moveUnitToTargetTile", ex)
                }
            }
        }
    }

    private fun animateMovement(
        previousTileView: TileView,
        selectedUnit: MapUnitView,
        targetTileView: TileView,
        pathToTile: List<TileView>
    ) {
        val tileGroup = tileGroups[previousTileView]!!

        // Steal the current sprites to our new group
        val unitSpriteAndIcon = Group().apply { setPosition(tileGroup.x, tileGroup.y) }
        val unitSpriteSlot = tileGroup.layerUnitArt.getSpriteSlot(selectedUnit.getUnit()) ?: return

        for (spriteImage in unitSpriteSlot.spriteGroup.children.toList()) // toList because actors added remove themselves from previous parent
            unitSpriteAndIcon.addActor(spriteImage)
        tileGroupMap.addActor(unitSpriteAndIcon)



        unitSpriteAndIcon.addAction(
            Actions.sequence(
                Actions.run {
                    // Disable the final tile, so we won't have one image "merging into" the other
                    // Can only be done after the new group has been updated, to get the spriteGroup
                    val targetTileSpriteSlot = tileGroups[targetTileView]!!.layerUnitArt.getSpriteSlot(selectedUnit.getUnit())
                    targetTileSpriteSlot?.spriteGroup?.isVisible = false
                },
                *pathToTile.map { tileView ->
                    Actions.moveTo(
                        tileGroups[tileView]!!.x,
                        tileGroups[tileView]!!.y,
                        0.5f / pathToTile.size
                    )
                }.toTypedArray(),
                Actions.run {
                    // Re-enable the final tile
                    val targetTileSpriteSlot = tileGroups[targetTileView]!!.layerUnitArt.getSpriteSlot(selectedUnit.getUnit())
                    targetTileSpriteSlot?.spriteGroup?.isVisible = true
                    worldScreen.shouldUpdate = true
                },
                Actions.removeActor(),
            )
        )
    }

    internal fun swapMoveUnitToTargetTile(selectedUnitView: MapUnitView, targetTileView: TileView) {
        markUnitMoveTutorialComplete(selectedUnitView)
        selectedUnitView.trySwapMoveToTile(targetTileView, keepEscorting = true)

        if (selectedUnitView.isExploring() || selectedUnitView.isMoving())
            selectedUnitView.tryResetAction() // remove explore on manual swap-move

        // Play something like a swish-swoosh
        SoundPlayer.play(UncivSound.Swap)

        if (selectedUnitView.hasMovement()) worldScreen.bottomUnitTable.selectUnit(selectedUnitView)

        worldScreen.shouldUpdate = true
        removeUnitActionOverlay()
    }

    private fun addTileOverlaysWithUnitMovement(selectedUnits: List<MapUnitView>, tileView: TileView) {
        val tile = tileView.getTile()
        Concurrency.run("TurnsToGetThere") {
            /** LibGdx sometimes has these weird errors when you try to edit the UI layout from 2 separate threads.
             * And so, all UI editing will be done on the main thread.
             * The only "heavy lifting" that needs to be done is getting the turns to get there,
             * so that and that alone will be relegated to the concurrent thread.
             */

            val unitToTurnsToTile = HashMap<MapUnitView, Int>()
            for (unitView in selectedUnits) {
                val shortestPath = ArrayList<TileView>()
                val turnsToGetThere = if (unitView.isAirUnit()) {
                    if (unitView.canReach(tileView)) 1
                    else 0
                } else if (unitView.isPreparingParadrop()) {
                    if (unitView.canReach(tileView)) 1
                    else 0
                } else {
                    // this is the most time-consuming call
                    shortestPath.addAll(unitView.getShortestPath(tileView))
                    shortestPath.size
                }
                unitMovementPaths[unitView] = shortestPath
                unitToTurnsToTile[unitView] = turnsToGetThere
            }

            launchOnGLThread {
                val unitsWhoCanMoveThere = HashMap(unitToTurnsToTile.filter { it.value != 0 })
                if (unitsWhoCanMoveThere.isEmpty()) { // give the regular tile overlays with no unit movement
                    addTileOverlays(tileView)
                    worldScreen.shouldUpdate = true
                    return@launchOnGLThread
                }

                val turnsToGetThere = unitsWhoCanMoveThere.values.maxOrNull()!!

                if (UncivGame.Current.settings.singleTapMove && turnsToGetThere == 1) {
                    // single turn instant move
                    val selectedUnitView = unitsWhoCanMoveThere.keys.first()
                    for (unitView in unitsWhoCanMoveThere.keys) {
                        unitView.tryHeadTowards(tileView)
                    }
                    worldScreen.bottomUnitTable.selectUnit(selectedUnitView) // keep moved unit selected
                } else {
                    // add "move to" button if there is a path to tileInfo
                    val moveHereButtonDto = MoveHereOverlayButtonData(unitsWhoCanMoveThere, tile)
                    addTileOverlays(tileView, moveHereButtonDto)
                }
                worldScreen.shouldUpdate = true
            }
        }
    }

    private fun addTileOverlaysWithUnitSwapping(selectedUnitView: MapUnitView, tileView: TileView) {
        if (!selectedUnitView.canSwapTo(tileView)) { // give the regular tile overlays with no unit swapping
            addTileOverlays(tileView)
            worldScreen.shouldUpdate = true
            return
        }
        if (UncivGame.Current.settings.singleTapMove) {
            swapMoveUnitToTargetTile(selectedUnitView, tileView)
        }
        else {
            // Add "swap with" button
            val swapWithButtonDto = SwapWithOverlayButtonData(selectedUnitView, tileView.getTile())
            addTileOverlays(tileView, swapWithButtonDto)
        }
        worldScreen.shouldUpdate = true
    }

    private fun addTileOverlaysWithUnitRoadConnecting(selectedUnitView: MapUnitView, tileView: TileView){
        val selectedUnit = selectedUnitView.getUnit()
        val tile = tileView.getTile()
        val tileMapView = worldScreen.selectedGameView.tileMapView
        Concurrency.run("ConnectRoad") {
           val validTile = tileView.isLand &&
               !tileView.isImpassible() &&
                selectedUnitView.isExplored(tileView)

            if (validTile) {
                val roadPath: List<Tile>? =
                    if (UncivGame.Current.settings.useAStarPathfinding) selectedUnit.movement.getRoadPath(selectedUnit.getTile())
                    else MapPathing.getRoadPath(selectedUnit.civ, selectedUnit.getTile(), tile)
                launchOnGLThread {
                    if (roadPath == null) { // give the regular tile overlays with no road connection
                        addTileOverlays(tileView)
                        worldScreen.shouldUpdate = true
                        return@launchOnGLThread
                    }
                    unitConnectRoadPaths[selectedUnitView] = roadPath.map { tileMapView.getTile(it) }
                    val connectRoadButtonDto = ConnectRoadOverlayButtonData(selectedUnitView, tile)
                    addTileOverlays(tileView, connectRoadButtonDto)
                    worldScreen.shouldUpdate = true
                }
            }
        }
    }

    private fun addMovingSpyOverlay(spy: Spy, tileView: TileView) {
        val cityView = tileView.owningCity()
        val city: City? = if (tileView.isCityCenter() && cityView != null && spy.canMoveTo(cityView.getCity())) cityView.getCity() else null
        addTileOverlays(tileView, MoveSpyOverlayButtonData(spy, city))
        worldScreen.shouldUpdate = true
    }

    private fun addTileOverlays(tileView: TileView, buttonDto: OverlayButtonData? = null) {
        val table = Table().apply { defaults().pad(10f) }
        if (buttonDto != null && worldScreen.canChangeState)
            table.add(buttonDto.createButton(this))

        val unitList = ArrayList<MapUnitView>()
        val visibleOwnedUnits = tileView.getVisibleUnits()
            .mapNotNull { it.tryGetMapUnitView() }
        
        if (tileView.isCityCenter() || unitList.any { it.isAirUnit() }) {
            unitList.addAll(visibleOwnedUnits)
        }

        for (unitView in unitList) {
            val unitIconGroup = UnitIconGroup(unitView.getUnit(), 48f).surroundWithCircle(68f, resizeActor = false)
            unitIconGroup.circle.color = Color.GRAY.cpy().apply { a = 0.5f }
            if (!unitView.hasMovement()) unitIconGroup.color.a = 0.66f
            val clickableCircle = ClickableCircle(68f)
            clickableCircle.onClickSuppressive {
                worldScreen.bottomUnitTable.selectUnit(unitView, Gdx.input.isShiftKeyPressed())
                worldScreen.shouldUpdate = true
                removeUnitActionOverlay()
            }
            unitIconGroup.addActor(clickableCircle)
            table.add(unitIconGroup)
        }

        addOverlayOnTileGroup(tileGroups[tileView]!!, table)
        if (UncivGame.Current.settings.unitMovementButtonAnimation) {
            table.color.a = 0f
            table.addAction(Actions.moveBy(0f, 48f, 0.15f, Interpolation.smooth))
            table.addAction(Actions.alpha(1f, 0.15f, Interpolation.smooth))
        }
        else
            table.moveBy(0f, 48f)
    }

    /** Adds [actor] as a direct child of the TileGroupMap, rendered above all layer groups. */
    fun addActorToTileGroupMap(actor: Actor) = tileGroupMap.addActor(actor)

    fun addOverlayOnTileGroup(group: TileGroup, actor: Actor) {

        actor.center(group)
        actor.x += group.x
        actor.y += group.y
        tileGroupMap.addActor(actor) // Add directly to TileGroupMap so toFront() places it above all layer groups
        actor.toFront()

        actor.y += actor.height
        actor.setOrigin(Align.bottom)
        unitActionOverlays.add(actor)
    }

    /** Returns true when the civ is a human player defeated in singleplayer game */
    @Readonly
    fun isMapRevealEnabled(civView: CivView): Boolean {
        val viewingCiv = civView.getCiv()
        return !viewingCiv.gameInfo.gameParameters.isOnlineMultiplayer
            && viewingCiv.isCurrentPlayer()
            && viewingCiv.isDefeated()
    }

    /** Clear all arrows to be drawn on the next update. */
    fun resetArrows() {
        for (tile in tileGroups.asSequence())
            tile.value.layerMisc.resetArrows()
    }

    /** Add an arrow to draw on the next update. */
    fun addArrow(fromTileView: TileView, toTileView: TileView, arrowType: MapArrowType) {
        tileGroups[fromTileView]?.layerMisc?.addArrow(toTileView.getTile(), arrowType)
    }

    /**
     * Add arrows to show all past and planned movements and attacks, if the options setting to do so is enabled.
     *
     * @param pastVisibleUnits Sequence of units for which the last turn's movement history can be displayed.
     * @param targetVisibleUnits Sequence of units for which the active movement target can be displayed.
     * @param visibleAttacks Sequence of pairs of [Vector2] positions of the sources and the targets of all attacks that can be displayed.
     * */
    internal fun updateMovementOverlay(pastVisibleUnits: Sequence<ForeignMapUnitView>, targetVisibleUnits: Sequence<MapUnitView>, visibleAttacks: Sequence<Pair<HexCoord, HexCoord>>) {
        val tileMapView = worldScreen.selectedGameView.tileMapView
        val selectedUnit = worldScreen.bottomUnitTable.selectedUnit
        for (unitView in pastVisibleUnits) {
            val movementMemories = unitView.getMovementMemories()
            if (movementMemories.isEmpty()) continue
            if (selectedUnit != null && selectedUnit != unitView) continue // When selecting a unit, show only arrows of that unit
            val stepIter = movementMemories.iterator()
            var previous = stepIter.next()
            while (stepIter.hasNext()) {
                val next = stepIter.next()
                val fromTileView = tileMapView.getTile(previous.position)
                val toTileView = tileMapView.getTile(next.position)
                if (fromTileView != null && toTileView != null) addArrow(fromTileView, toTileView, next.type)
                previous = next
            }
            val fromTileView = tileMapView.getTile(previous.position)
            val unitTileView = tileMapView.getTile(unitView.getTile().position())
            if (fromTileView != null && unitTileView != null) addArrow(fromTileView, unitTileView, unitView.getMostRecentMoveType())
        }
        for (unitView in targetVisibleUnits) {
            if (!unitView.isMoving())
                continue
            val toTileView = unitView.getMovementDestination()
            val fromTileView = tileMapView.getTile(unitView.getTile().position()) ?: continue
            addArrow(fromTileView, toTileView, MiscArrowTypes.UnitMoving)
        }
        for ((from, to) in visibleAttacks) {
            if (selectedUnit != null
                && selectedUnit.getTile().position() != from
                && selectedUnit.getTile().position() != to) continue
            val fromTileView = tileMapView.getTile(from) ?: continue
            val toTileView = tileMapView.getTile(to) ?: continue
            addArrow(fromTileView, toTileView, MiscArrowTypes.UnitHasAttacked)
        }
    }


    var blinkAction: Action? = null

    /** Scrolls the world map to specified coordinates.
     * @param vector Position to center on
     * @param immediately Do so without animation
     * @param selectUnit Select a unit at the destination
     * @return `true` if scroll position was changed, `false` otherwise
     */
    fun setCenterPosition(vector: HexCoord, immediately: Boolean = false, selectUnit: Boolean = true, forceSelectUnit: MapUnit? = null): Boolean {
        val tileGroup = tileGroups.values.firstOrNull { it.tileView.position() == vector } ?: return false
        selectedTile = tileGroup.tileView
        if (selectUnit || forceSelectUnit != null)
            worldScreen.bottomUnitTable.tileSelected(selectedTile!!, forceSelectUnit?.let { worldScreen.selectedGameView.getForeignMapUnitView(it).tryGetMapUnitView() })

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
                tileGroup.layerCityButton.setButtonTransform(false) // save rendering time at normal zoom
            }
        } else if (clampedCityButtonZoom >= minZoom) {
            for (tileGroup in tileGroups.values) {
                // ONLY set those groups that have active city buttons as transformable!
                // This is massively framerate-improving!
                if (tileGroup.layerCityButton.hasButton())
                    tileGroup.layerCityButton.setButtonTransform(true)
                tileGroup.layerCityButton.setButtonScale(clampedCityButtonZoom)
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
        if (worldScreen.selectedGameView.civView.isSpectator()) return result

        val exploredRegion = worldScreen.selectedGameView.civView.getCiv().exploredRegion
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
        if (worldScreen.selectedGameView.civView.isSpectator()) return result

        val exploredRegion = worldScreen.selectedGameView.civView.getCiv().exploredRegion
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
