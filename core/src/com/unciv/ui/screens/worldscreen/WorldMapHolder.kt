package com.unciv.ui.screens.worldscreen

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
import com.unciv.Constants
import com.unciv.UncivGame
import com.unciv.logic.automation.unit.CityLocationTileRanker
import com.unciv.logic.automation.unit.UnitAutomation
import com.unciv.logic.battle.AttackableTile
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
import com.unciv.models.UncivSound
import com.unciv.models.UnitActionType
import com.unciv.models.ruleset.unique.LocalUniqueCache
import com.unciv.models.ruleset.unique.UniqueType
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.components.MapArrowType
import com.unciv.ui.components.MiscArrowTypes
import com.unciv.ui.components.extensions.center
import com.unciv.ui.components.extensions.colorFromRGB
import com.unciv.ui.components.extensions.darken
import com.unciv.ui.components.extensions.isShiftKeyPressed
import com.unciv.ui.components.extensions.setSize
import com.unciv.ui.components.extensions.surroundWithCircle
import com.unciv.ui.components.extensions.toLabel
import com.unciv.ui.components.input.ActivationTypes
import com.unciv.ui.components.input.ClickableCircle
import com.unciv.ui.components.input.KeyCharAndCode
import com.unciv.ui.components.input.KeyboardBinding
import com.unciv.ui.components.input.keyShortcuts
import com.unciv.ui.components.input.onActivation
import com.unciv.ui.components.input.onClick
import com.unciv.ui.components.tilegroups.TileGroup
import com.unciv.ui.components.tilegroups.TileGroupMap
import com.unciv.ui.components.tilegroups.TileSetStrings
import com.unciv.ui.components.tilegroups.WorldTileGroup
import com.unciv.ui.components.widgets.UnitGroup
import com.unciv.ui.components.widgets.ZoomableScrollPane
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.screens.basescreen.BaseScreen
import com.unciv.ui.screens.basescreen.UncivStage
import com.unciv.ui.screens.worldscreen.UndoHandler.Companion.recordUndoCheckpoint
import com.unciv.ui.screens.worldscreen.bottombar.BattleTableHelpers.battleAnimation
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

    private val unitActionOverlays: ArrayList<Actor> = ArrayList()

    private val unitMovementPaths: HashMap<MapUnit, ArrayList<Tile>> = HashMap()

    private val unitConnectRoadPaths: HashMap<MapUnit, List<Tile>> = HashMap()

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

    // Interface for classes that contain the data required to draw a button
    interface ButtonDto
    // Contains the data required to draw a "move here" button
    class MoveHereButtonDto(val unitToTurnsToDestination: HashMap<MapUnit, Int>, val tile: Tile) :
        ButtonDto
    // Contains the data required to draw a "swap with" button
    class SwapWithButtonDto(val unit: MapUnit, val tile: Tile) : ButtonDto

    // Contains the data required to draw a "connect road" button
    class ConnectRoadButtonDto(val unit: MapUnit, val tile: Tile) : ButtonDto


    internal fun addTiles() {
        val tileSetStrings = TileSetStrings()
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
        unitTable.tileSelected(tile)
        val newSelectedUnit = unitTable.selectedUnit

        if (previousSelectedCity != null && tile != previousSelectedCity.getCenterTile())
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
                        (it.movement.isUnknownTileWeShouldAssumeToBePassable(tile) && !it.baseUnit.movesLikeAirUnits())
                }
            }

            if (isTileDifferent && isPlayerTurn && canPerformActionsOnTile && existsUnitNotPreparingAirSweep) {
                when {
                    previousSelectedUnitIsSwapping -> addTileOverlaysWithUnitSwapping(previousSelectedUnits.first(), tile)
                    previousSelectedUnitIsConnectingRoad -> addTileOverlaysWithUnitRoadConnecting(previousSelectedUnits.first(), tile)
                    else -> addTileOverlaysWithUnitMovement(previousSelectedUnits, tile) // Long-running task
                }
            }
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
        worldScreen.viewingCiv.tacticalAI.showZonesDebug(tile)
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
                    worldScreen.battleAnimation(attacker, damageToAttacker, attackableTile.combatant, damageToDefender)
                localShouldUpdate = true
            } else if (unit.movement.canReach(tile)) {
                /** ****** Right-click Move ****** */
                moveUnitToTargetTile(listOf(unit), tile)
                localShouldUpdate = true
            }
        }
        worldScreen.shouldUpdate = localShouldUpdate
    }

    private fun moveUnitToTargetTile(selectedUnits: List<MapUnit>, targetTile: Tile) {
        // this can take a long time, because of the unit-to-tile calculation needed, so we put it in a different thread
        // THIS PART IS REALLY ANNOYING
        // So lets say you have 2 units you want to move in the same direction, right
        // But if the first one gets there, and the second one was PLANNING on going there, then now it can't and has to rethink
        // So basically, THE UNIT MOVES HAVE TO BE SEQUENTIAL and not concurrent which is a BITCH
        // So we do this one at a time by getting the list of units to move, MOVING ONE OF THEM with all the yukky threading,
        // and then calling the function again but without the unit that moved.

        val selectedUnit = selectedUnits.first()

        Concurrency.run("TileToMoveTo") {
            // these are the heavy parts, finding where we want to go
            // Since this runs in a different thread, even if we check movement.canReach()
            // then it might change until we get to the getTileToMoveTo, so we just try/catch it
            val tileToMoveTo: Tile
            try {
                tileToMoveTo = selectedUnit.movement.getTileToMoveToThisTurn(targetTile)
            } catch (ex: Exception) {
                when (ex) {
                    is UnitMovement.UnreachableDestinationException -> {
                        // This is normal e.g. when selecting an air unit then right-clicking on an empty tile
                        // Or telling a ship to run onto a coastal land tile.
                        // Do nothing
                    }
                    else -> {
                        Log.error("Exception in getTileToMoveToThisTurn", ex)
                    }
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
                    selectedUnit.movement.moveToTile(tileToMoveTo)
                    if (selectedUnit.isExploring() || selectedUnit.isMoving())
                        selectedUnit.action = null // remove explore on manual move
                    SoundPlayer.play(UncivSound.Whoosh)
                    if (selectedUnit.currentTile != targetTile)
                        selectedUnit.action =
                                "moveTo ${targetTile.position.x.toInt()},${targetTile.position.y.toInt()}"
                    if (selectedUnit.currentMovement > 0) worldScreen.bottomUnitTable.selectUnit(selectedUnit)

                    worldScreen.shouldUpdate = true
                    if (selectedUnits.size > 1) { // We have more tiles to move
                        moveUnitToTargetTile(selectedUnits.subList(1, selectedUnits.size), targetTile)
                    } else removeUnitActionOverlay() //we're done here

                    if (UncivGame.Current.settings.autoUnitCycle && selectedUnit.currentMovement == 0f)
                        worldScreen.switchToNextUnit()

                } catch (ex: Exception) {
                    Log.error("Exception in moveUnitToTargetTile", ex)
                }
            }
        }
    }

    private fun swapMoveUnitToTargetTile(selectedUnit: MapUnit, targetTile: Tile) {
        selectedUnit.movement.swapMoveToTile(targetTile)

        if (selectedUnit.isExploring() || selectedUnit.isMoving())
            selectedUnit.action = null // remove explore on manual swap-move

        // Play something like a swish-swoosh
        SoundPlayer.play(UncivSound.Swap)

        if (selectedUnit.currentMovement > 0) worldScreen.bottomUnitTable.selectUnit(selectedUnit)

        worldScreen.shouldUpdate = true
        removeUnitActionOverlay()
    }

    private fun connectRoadToTargetTile(selectedUnit: MapUnit, targetTile: Tile) {
        selectedUnit.automatedRoadConnectionDestination = targetTile.position
        selectedUnit.automatedRoadConnectionPath = null
        selectedUnit.action = UnitActionType.ConnectRoad.value
        selectedUnit.automated = true
        UnitAutomation.automateUnitMoves(selectedUnit)

        SoundPlayer.play(UncivSound("wagon"))

        worldScreen.shouldUpdate = true
        removeUnitActionOverlay()

        // Make highlighting go away
        worldScreen.bottomUnitTable.selectedUnitIsConnectingRoad = false

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
                val turnsToGetThere = if (unit.baseUnit.movesLikeAirUnits()) {
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
                    val moveHereButtonDto = MoveHereButtonDto(unitsWhoCanMoveThere, tile)
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
            val swapWithButtonDto = SwapWithButtonDto(selectedUnit, tile)
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
                    val connectRoadButtonDto = ConnectRoadButtonDto(selectedUnit, tile)
                    addTileOverlays(tile, connectRoadButtonDto)
                    worldScreen.shouldUpdate = true
                }
            }
        }
    }
    private fun addTileOverlays(tile: Tile, buttonDto: ButtonDto? = null) {
        val table = Table().apply { defaults().pad(10f) }
        if (buttonDto != null && worldScreen.canChangeState)
            table.add(
                when (buttonDto) {
                    is MoveHereButtonDto -> getMoveHereButton(buttonDto)
                    is SwapWithButtonDto -> getSwapWithButton(buttonDto)
                    is ConnectRoadButtonDto -> getConnectRoadButton(buttonDto)
                    else -> null
                }
            )

        val unitList = ArrayList<MapUnit>()
        if (tile.isCityCenter()
                && (tile.getOwner() == worldScreen.viewingCiv || worldScreen.viewingCiv.isSpectator())) {
            unitList.addAll(tile.getCity()!!.getCenterTile().getUnits())
        } else if (tile.airUnits.isNotEmpty()
                && (tile.airUnits.first().civ == worldScreen.viewingCiv || worldScreen.viewingCiv.isSpectator())) {
            unitList.addAll(tile.getUnits())
        }

        for (unit in unitList) {
            val unitGroup = UnitGroup(unit, 48f).surroundWithCircle(68f, resizeActor = false)
            unitGroup.circle.color = Color.GRAY.cpy().apply { a = 0.5f }
            if (unit.currentMovement == 0f) unitGroup.color.a = 0.66f
            val clickableCircle = ClickableCircle(68f)
            clickableCircle.touchable = Touchable.enabled
            clickableCircle.onClick {
                worldScreen.bottomUnitTable.selectUnit(unit, Gdx.input.isShiftKeyPressed())
                worldScreen.shouldUpdate = true
                removeUnitActionOverlay()
            }
            unitGroup.addActor(clickableCircle)
            table.add(unitGroup)
        }

        addOverlayOnTileGroup(tileGroups[tile]!!, table)
        table.moveBy(0f, 48f)

    }

    val buttonSize = 60f
    val smallerCircleSizes = 25f

    private fun getMoveHereButton(dto: MoveHereButtonDto): Group {
        val isParadrop = dto.unitToTurnsToDestination.keys.all { it.isPreparingParadrop() }
        val image = if (isParadrop)
                ImageGetter.getUnitActionPortrait("Paradrop", buttonSize / 2)
            else ImageGetter.getStatIcon("Movement")
                .apply { color = Color.BLACK; width = buttonSize / 2; height = buttonSize / 2 }
        val moveHereButton = image
            .surroundWithCircle(buttonSize - 2, false)
            .surroundWithCircle(buttonSize, false, Color.BLACK)

        if (!isParadrop) {
            val numberCircle = dto.unitToTurnsToDestination.values.maxOrNull()!!.toString().toLabel(fontSize = 14)
                .apply { setAlignment(Align.center) }
                .surroundWithCircle(smallerCircleSizes - 2, color = BaseScreen.skinStrings.skinConfig.baseColor.darken(0.3f))
                .surroundWithCircle(smallerCircleSizes, false)
            moveHereButton.addActor(numberCircle)
        }

        val firstUnit = dto.unitToTurnsToDestination.keys.first()
        val unitIcon = if (dto.unitToTurnsToDestination.size == 1) UnitGroup(firstUnit, smallerCircleSizes)
        else dto.unitToTurnsToDestination.size.toString().toLabel(fontColor = firstUnit.civ.nation.getInnerColor()).apply { setAlignment(Align.center) }
                .surroundWithCircle(smallerCircleSizes).apply { circle.color = firstUnit.civ.nation.getOuterColor() }
        unitIcon.y = buttonSize - unitIcon.height
        moveHereButton.addActor(unitIcon)

        val unitsThatCanMove = dto.unitToTurnsToDestination.keys.filter { it.currentMovement > 0 }
        if (unitsThatCanMove.isEmpty()) moveHereButton.color.a = 0.5f
        else {
            moveHereButton.onActivation(UncivSound.Silent) {
                UncivGame.Current.settings.addCompletedTutorialTask("Move unit")
                if (unitsThatCanMove.any { it.baseUnit.movesLikeAirUnits() })
                    UncivGame.Current.settings.addCompletedTutorialTask("Move an air unit")
                moveUnitToTargetTile(unitsThatCanMove, dto.tile)
            }
            moveHereButton.keyShortcuts.add(KeyCharAndCode.TAB)
        }
        return moveHereButton
    }

    private fun getSwapWithButton(dto: SwapWithButtonDto): Group {
        val swapWithButton = Group()
        swapWithButton.setSize(buttonSize, buttonSize)
        swapWithButton.addActor(ImageGetter.getCircle(size = buttonSize))
        swapWithButton.addActor(
            ImageGetter.getImage("OtherIcons/Swap").apply {
                color = Color.BLACK
                setSize(buttonSize / 2)
                center(swapWithButton)
            }
        )

        val unitIcon = UnitGroup(dto.unit, smallerCircleSizes)
        unitIcon.y = buttonSize - unitIcon.height
        swapWithButton.addActor(unitIcon)

        swapWithButton.onActivation(UncivSound.Silent) {
            UncivGame.Current.settings.addCompletedTutorialTask("Move unit")
            if (dto.unit.baseUnit.movesLikeAirUnits())
                UncivGame.Current.settings.addCompletedTutorialTask("Move an air unit")
            swapMoveUnitToTargetTile(dto.unit, dto.tile)
        }
        swapWithButton.keyShortcuts.add(KeyCharAndCode.TAB)

        return swapWithButton
    }

    private fun getConnectRoadButton(dto: ConnectRoadButtonDto): Group {
        val connectRoadButton = Group()
        connectRoadButton.setSize(buttonSize, buttonSize)
        connectRoadButton.addActor(ImageGetter.getUnitActionPortrait("RoadConnection", buttonSize * 0.8f).apply {
                center(connectRoadButton)
            }
        )

        val unitIcon = UnitGroup(dto.unit, smallerCircleSizes)
        unitIcon.y = buttonSize - unitIcon.height
        connectRoadButton.addActor(unitIcon)

        connectRoadButton.onActivation(UncivSound.Silent) {
            connectRoadToTargetTile(dto.unit, dto.tile)
        }
        connectRoadButton.keyShortcuts.add(KeyboardBinding.ConnectRoad)

        return connectRoadButton
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
    private fun isMapRevealEnabled(viewingCiv: Civilization) = !viewingCiv.gameInfo.gameParameters.isOnlineMultiplayer
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
        for (unit in pastVisibleUnits) {
            if (unit.movementMemories.isEmpty()) continue
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
            addArrow(tileMap[from], tileMap[to], MiscArrowTypes.UnitHasAttacked)
        }
    }

    internal fun updateTiles(viewingCiv: Civilization) {

        if (isMapRevealEnabled(viewingCiv)) {
            // Only needs to be done once - this is so the minimap will also be revealed
            tileGroups.values.forEach {
                it.tile.setExplored(viewingCiv, true)
                it.isForceVisible = true } // So we can see all resources, regardless of tech
        }

        // General update of all tiles
        val uniqueCache =  LocalUniqueCache(true)
        for (tileGroup in tileGroups.values)
            tileGroup.update(viewingCiv, uniqueCache)

        // Update tiles according to selected unit/city
        val unitTable = worldScreen.bottomUnitTable
        when {
            unitTable.selectedCity != null -> {
                val city = unitTable.selectedCity!!
                updateBombardableTilesForSelectedCity(city)
                // We still want to show road paths to the selected city if they are present
                if (unitTable.selectedUnitIsConnectingRoad) {
                    updateTilesForSelectedUnit(unitTable.selectedUnits[0])
                }
            }
            unitTable.selectedUnit != null -> {
                for (unit in unitTable.selectedUnits) {
                    updateTilesForSelectedUnit(unit)
                }
            }
            unitActionOverlays.isNotEmpty() -> {
                removeUnitActionOverlay()
            }
        }

        // Same as below - randomly, tileGroups doesn't seem to contain the selected tile, and this doesn't seem reproducible
        tileGroups[selectedTile]?.layerOverlay?.showHighlight(Color.WHITE)

        zoom(scaleX) // zoom to current scale, to set the size of the city buttons after "next turn"
    }

    private fun updateTilesForSelectedUnit(unit: MapUnit) {

        val tileGroup = tileGroups[unit.getTile()] ?: return

        // Update flags for units which have them
        if (!unit.baseUnit.movesLikeAirUnits()) {
            tileGroup.layerUnitFlag.selectFlag(unit)
        }

        // Fade out less relevant images if a military unit is selected
        if (unit.isMilitary()) {
            for (group in tileGroups.values) {

                // Fade out population icons
                group.layerMisc.dimPopulation(true)

                val shownImprovement = unit.civ.lastSeenImprovement[group.tile.position]

                // Fade out improvement icons (but not barb camps or ruins)
                if (shownImprovement != null && shownImprovement != Constants.barbarianEncampment
                        && !unit.civ.gameInfo.ruleset.tileImprovements[shownImprovement]!!.isAncientRuinsEquivalent())
                    group.layerMisc.dimImprovement(true)
            }
        }

        // Z-Layer: 0
        // Highlight suitable tiles in swapping-mode
        if (worldScreen.bottomUnitTable.selectedUnitIsSwapping) {
            val unitSwappableTiles = unit.movement.getUnitSwappableTiles()
            val swapUnitsTileOverlayColor = Color.PURPLE
            for (tile in unitSwappableTiles)  {
                tileGroups[tile]!!.layerOverlay.showHighlight(swapUnitsTileOverlayColor,
                    if (UncivGame.Current.settings.singleTapMove) 0.7f else 0.3f)
            }
            // In swapping-mode we don't want to show other overlays
            return
        }

        // Z-Layer: 0
        // Highlight suitable tiles in road connecting mode
        if (worldScreen.bottomUnitTable.selectedUnitIsConnectingRoad) {
            if (unit.currentTile.ruleset.roadImprovement == null) return
            val validTiles = unit.civ.gameInfo.tileMap.tileList.filter {
                MapPathing.isValidRoadPathTile(unit, it)
            }
            val connectRoadTileOverlayColor = Color.RED
            for (tile in validTiles)  {
                tileGroups[tile]!!.layerOverlay.showHighlight(connectRoadTileOverlayColor, 0.3f)
            }

            if (unitConnectRoadPaths.containsKey(unit)) {
                for (tile in unitConnectRoadPaths[unit]!!) {
                    tileGroups[tile]!!.layerOverlay.showHighlight(Color.ORANGE, 0.8f)
                }
            }

            // In road connecting mode we don't want to show other overlays
            return
        }

        val isAirUnit = unit.baseUnit.movesLikeAirUnits()
        val moveTileOverlayColor = if (unit.isPreparingParadrop()) Color.BLUE else Color.WHITE
        val tilesInMoveRange = unit.movement.getReachableTilesInCurrentTurn()
        // Prepare special Nuke blast radius display
        val nukeBlastRadius = if (unit.baseUnit.isNuclearWeapon() && selectedTile != null && selectedTile != unit.getTile())
            unit.getNukeBlastRadius() else -1

        // Z-Layer: 1
        // Highlight tiles within movement range
        for (tile in tilesInMoveRange) {
            val group = tileGroups[tile]!!

            // Air-units have additional highlights
            if (isAirUnit && !unit.isPreparingAirSweep()) {
                if (nukeBlastRadius >= 0 && tile.aerialDistanceTo(selectedTile!!) <= nukeBlastRadius) {
                    // The tile is within the nuke blast radius
                    group.layerMisc.overlayTerrain(Color.FIREBRICK, 0.6f)
                } else if (tile.aerialDistanceTo(unit.getTile()) <= unit.getRange()) {
                    // The tile is within attack range
                    group.layerMisc.overlayTerrain(Color.RED)
                } else if (tile.isExplored(worldScreen.viewingCiv) && tile.aerialDistanceTo(unit.getTile()) <= unit.getRange()*2) {
                    // The tile is within move range
                    group.layerMisc.overlayTerrain(if (unit.movement.canMoveTo(tile)) Color.WHITE else Color.BLUE)
                }
            }

            // Highlight tile unit can move to
            if (unit.movement.canMoveTo(tile) ||
                    unit.movement.isUnknownTileWeShouldAssumeToBePassable(tile) && !unit.baseUnit.movesLikeAirUnits()) {
                val alpha = if (UncivGame.Current.settings.singleTapMove) 0.7f else 0.3f
                group.layerOverlay.showHighlight(moveTileOverlayColor, alpha)
            }

        }

        // Z-Layer: 2
        // Add back in the red markers for Air Unit Attack range since they can't move, but can still attack
        if (unit.cache.cannotMove && isAirUnit && !unit.isPreparingAirSweep()) {
            val tilesInAttackRange = unit.getTile().getTilesInDistanceRange(IntRange(1, unit.getRange()))
            for (tile in tilesInAttackRange) {
                // The tile is within attack range
                tileGroups[tile]!!.layerOverlay.showHighlight(Color.RED, 0.3f)
            }
        }

        // Z-Layer: 3
        // Movement paths
        if (unitMovementPaths.containsKey(unit)) {
            for (tile in unitMovementPaths[unit]!!) {
                tileGroups[tile]!!.layerOverlay.showHighlight(Color.SKY, 0.8f)
            }
        }

        // Z-Layer: 4
        // Highlight road path for workers currently connecting roads
        if (unit.isAutomatingRoadConnection()) {
            if (unit.automatedRoadConnectionPath == null) return
            val currTileIndex = unit.automatedRoadConnectionPath!!.indexOf(unit.currentTile.position)
            if (currTileIndex != -1) {
                val futureTiles = unit.automatedRoadConnectionPath!!.filterIndexed { index, _ ->
                    index > currTileIndex
                }.map { tilePos ->
                    tileMap[tilePos]
                }
                for (tile in futureTiles) {
                    tileGroups[tile]!!.layerOverlay.showHighlight(Color.ORANGE, if (UncivGame.Current.settings.singleTapMove) 0.7f else 0.3f)
                }
            }
        }

        // Z-Layer: 5
        // Highlight movement destination tile
        if (unit.isMoving()) {
            tileGroups[unit.getMovementDestination()]!!.layerOverlay.showHighlight(Color.WHITE, 0.7f)
        }

        // Z-Layer: 6
        // Highlight attackable tiles
        if (unit.isMilitary()) {

            val attackableTiles: List<AttackableTile> =
                if (nukeBlastRadius >= 0)
                    selectedTile!!.getTilesInDistance(nukeBlastRadius)
                        // Should not display invisible submarine units even if the tile is visible.
                        .filter { targetTile -> (targetTile.isVisible(unit.civ) && targetTile.getUnits().any { !it.isInvisible(unit.civ) })
                            || (targetTile.isCityCenter() && unit.civ.hasExplored(targetTile)) }
                        .map { AttackableTile(unit.getTile(), it, 1f, null) }
                        .toList()
                else TargetHelper.getAttackableEnemies(unit, unit.movement.getDistanceToTiles())
                    .filter { it.tileToAttack.isVisible(unit.civ) }
                    .distinctBy { it.tileToAttack }

            for (attackableTile in attackableTiles) {
                val tileGroupToAttack = tileGroups[attackableTile.tileToAttack]!!
                tileGroupToAttack.layerOverlay.showHighlight(colorFromRGB(237, 41, 57))
                tileGroupToAttack.layerOverlay.showCrosshair(
                    // the targets which cannot be attacked without movements shown as orange-ish
                    if (attackableTile.tileToAttackFrom != unit.currentTile)
                        0.5f
                    else 1f
                )
            }
        }

        // Z-Layer: 7
        // Highlight best tiles for city founding
        if (unit.hasUnique(UniqueType.FoundCity)
                && UncivGame.Current.settings.showSettlersSuggestedCityLocations) {
            CityLocationTileRanker.getBestTilesToFoundCity(unit).tileRankMap.asSequence()
                .filter { it.key.isExplored(unit.civ) }.sortedByDescending { it.value }.take(3).forEach {
                tileGroups[it.key]!!.layerOverlay.showGoodCityLocationIndicator()
            }
        }
    }

    private fun updateBombardableTilesForSelectedCity(city: City) {
        if (!city.canBombard()) return
        for (attackableTile in TargetHelper.getBombardableTiles(city)) {
            val group = tileGroups[attackableTile]!!
            group.layerOverlay.showHighlight(colorFromRGB(237, 41, 57))
            group.layerOverlay.showCrosshair()
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

        if (scaleX == minZoom)
            for (tileGroup in tileGroups.values){
                val tile = tileGroup.tile
                if (!worldScreen.selectedCiv.hasExplored(tile)) continue
                val owner = tile.getOwner()
                if (owner != null){
                    val color = if (tile.isCityCenter()) owner.nation.getInnerColor() else owner.nation.getOuterColor()
                    tileGroup.layerMisc.overlayTerrain(color, 0.7f)
                }
            }
//         else
//             for (tileGroup in tileGroups.values)
//                 tileGroup.layerMisc.hideTerrainOverlay()

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
