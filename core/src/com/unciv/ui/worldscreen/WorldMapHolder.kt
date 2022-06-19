package com.unciv.ui.worldscreen

import com.badlogic.gdx.Application
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Action
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions
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
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.AttackableTile
import com.unciv.models.UncivSound
import com.unciv.models.helpers.MapArrowType
import com.unciv.models.helpers.MiscArrowTypes
import com.unciv.ui.UncivStage
import com.unciv.ui.audio.SoundPlayer
import com.unciv.ui.images.ImageGetter
import com.unciv.ui.map.TileGroupMap
import com.unciv.ui.tilegroups.TileGroup
import com.unciv.ui.tilegroups.TileSetStrings
import com.unciv.ui.tilegroups.WorldTileGroup
import com.unciv.ui.utils.KeyCharAndCode
import com.unciv.ui.utils.UnitGroup
import com.unciv.ui.utils.ZoomableScrollPane
import com.unciv.ui.utils.extensions.center
import com.unciv.ui.utils.extensions.colorFromRGB
import com.unciv.ui.utils.extensions.darken
import com.unciv.ui.utils.extensions.keyShortcuts
import com.unciv.ui.utils.extensions.onActivation
import com.unciv.ui.utils.extensions.onClick
import com.unciv.ui.utils.extensions.surroundWithCircle
import com.unciv.ui.utils.extensions.toLabel
import com.unciv.utils.Log
import com.unciv.utils.concurrency.Concurrency
import com.unciv.utils.concurrency.launchOnGLThread


class WorldMapHolder(
    internal val worldScreen: WorldScreen,
    internal val tileMap: TileMap
) : ZoomableScrollPane(20f, 20f) {
    internal var selectedTile: TileInfo? = null
    val tileGroups = HashMap<TileInfo, List<WorldTileGroup>>()

    //allWorldTileGroups exists to easily access all WordTileGroups
    //since tileGroup is a HashMap of Lists and getting all WordTileGroups
    //would need a double for loop
    private val allWorldTileGroups = ArrayList<WorldTileGroup>()

    private val unitActionOverlays: ArrayList<Actor> = ArrayList()

    private val unitMovementPaths: HashMap<MapUnit, ArrayList<TileInfo>> = HashMap()

    private lateinit var tileGroupMap: TileGroupMap<WorldTileGroup>

    init {
        if (Gdx.app.type == Application.ApplicationType.Desktop) this.setFlingTime(0f)
        continuousScrollingX = tileMap.mapParameters.worldWrap
        reloadMaxZoom()
        disablePointerEventsAndActionsOnPan()
    }

    /**
     * When scrolling the world map, there are two unnecessary (at least currently) things happening that take a decent amount of time:
     *
     * 1. Checking which [Actor]'s bounds the pointer (mouse/finger) entered+exited and sending appropriate events to these actors
     * 2. Running all [Actor.act] methods of all child [Actor]s
     *
     * Disabling them while panning increases the frame rate while panning by approximately 100%.
     */
    private fun disablePointerEventsAndActionsOnPan() {
        onPanStartListener = {
            Log.debug("Disable pointer enter/exit events & TileGroupMap.act()")
            (stage as UncivStage).performPointerEnterExitEvents = false
            tileGroupMap.shouldAct = false
        }
        onPanStopListener = {
            Log.debug("Enable pointer enter/exit events & TileGroupMap.act()")
            (stage as UncivStage).performPointerEnterExitEvents = true
            tileGroupMap.shouldAct = true
        }
    }

    internal fun reloadMaxZoom() {
        maxZoom = UncivGame.Current.settings.maxWorldZoomOut
        minZoom = 1f / maxZoom
        if (scaleX < minZoom) zoom(1f)   // since normally min isn't reached exactly, only powers of 0.8
    }

    // Interface for classes that contain the data required to draw a button
    interface ButtonDto
    // Contains the data required to draw a "move here" button
    class MoveHereButtonDto(val unitToTurnsToDestination: HashMap<MapUnit, Int>, val tileInfo: TileInfo) : ButtonDto
    // Contains the data required to draw a "swap with" button
    class SwapWithButtonDto(val unit: MapUnit, val tileInfo: TileInfo) : ButtonDto

    internal fun addTiles() {
        val tileSetStrings = TileSetStrings()
        val daTileGroups = tileMap.values.map { WorldTileGroup(worldScreen, it, tileSetStrings) }
        tileGroupMap = TileGroupMap(
            daTileGroups,
            continuousScrollingX)
        val mirrorTileGroups = tileGroupMap.getMirrorTiles()

        for (tileGroup in daTileGroups) {
            if (continuousScrollingX) {
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

            // On 'droid two-finger tap is mapped to right click and dissent has been expressed
            if (Gdx.app.type == Application.ApplicationType.Android) continue

            // Right mouse click listener
            tileGroup.addListener(object : ClickListener() {
                init {
                    button = Input.Buttons.RIGHT
                }

                override fun clicked(event: InputEvent?, x: Float, y: Float) {
                    val unit = worldScreen.bottomUnitTable.selectedUnit
                        ?: return
                    Concurrency.run("WorldScreenClick") {
                        onTileRightClicked(unit, tileGroup.tileInfo)
                    }
                }
            })
        }

        actor = tileGroupMap

        setSize(worldScreen.stage.width, worldScreen.stage.height)

        layout() // Fit the scroll pane to the contents - otherwise, setScroll won't work!
    }

    private fun onTileClicked(tileInfo: TileInfo) {
        removeUnitActionOverlay()
        selectedTile = tileInfo
        unitMovementPaths.clear()

        val unitTable = worldScreen.bottomUnitTable
        val previousSelectedUnits = unitTable.selectedUnits.toList() // create copy
        val previousSelectedCity = unitTable.selectedCity
        val previousSelectedUnitIsSwapping = unitTable.selectedUnitIsSwapping
        unitTable.tileSelected(tileInfo)
        val newSelectedUnit = unitTable.selectedUnit

        if (previousSelectedUnits.isNotEmpty() && previousSelectedUnits.any { it.getTile() != tileInfo }
                && worldScreen.isPlayersTurn
                && (
                    if (previousSelectedUnitIsSwapping)
                        previousSelectedUnits.first().movement.canUnitSwapTo(tileInfo)
                    else
                        previousSelectedUnits.any {
                            it.movement.canMoveTo(tileInfo) ||
                                    it.movement.isUnknownTileWeShouldAssumeToBePassable(tileInfo) && !it.baseUnit.movesLikeAirUnits()
                        }
                )) {
            if (previousSelectedUnitIsSwapping) {
                addTileOverlaysWithUnitSwapping(previousSelectedUnits.first(), tileInfo)
            }
            else {
                // this can take a long time, because of the unit-to-tile calculation needed, so we put it in a different thread
                addTileOverlaysWithUnitMovement(previousSelectedUnits, tileInfo)

            }
        } else addTileOverlays(tileInfo) // no unit movement but display the units in the tile etc.


        if (newSelectedUnit == null || newSelectedUnit.isCivilian()) {
            val unitsInTile = selectedTile!!.getUnits()
            if (previousSelectedCity != null && previousSelectedCity.canBombard()
                    && selectedTile!!.getTilesInDistance(2).contains(previousSelectedCity.getCenterTile())
                    && unitsInTile.any()
                    && unitsInTile.first().civInfo.isAtWarWith(worldScreen.viewingCiv)) {
                // try to select the closest city to bombard this guy
                unitTable.citySelected(previousSelectedCity)
            }
        }

        worldScreen.shouldUpdate = true
    }

    private fun onTileRightClicked(unit: MapUnit, tile: TileInfo) {
        if (UncivGame.Current.gameInfo!!.currentPlayerCiv.isSpectator()) {
            return
        }
        removeUnitActionOverlay()
        selectedTile = tile
        unitMovementPaths.clear()
        worldScreen.shouldUpdate = true

        if (worldScreen.bottomUnitTable.selectedUnitIsSwapping) {
            if (unit.movement.canUnitSwapTo(tile)) {
                swapMoveUnitToTargetTile(unit, tile)
            }
            // If we are in unit-swapping mode, we don't want to move or attack
            return
        }

        val attackableTile = BattleHelper.getAttackableEnemies(unit, unit.movement.getDistanceToTiles())
            .firstOrNull { it.tileToAttack == tile }
        if (unit.canAttack() && attackableTile != null) {
            worldScreen.shouldUpdate = true
            val attacker = MapUnitCombatant(unit)
            if (!Battle.movePreparingAttack(attacker, attackableTile)) return
            SoundPlayer.play(attacker.getAttackSound())
            Battle.attackOrNuke(attacker, attackableTile)
            return
        }

        val canUnitReachTile = unit.movement.canReach(tile)
        if (canUnitReachTile) {
            moveUnitToTargetTile(listOf(unit), tile)
            return
        }
    }

    private fun moveUnitToTargetTile(selectedUnits: List<MapUnit>, targetTile: TileInfo) {
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
            val tileToMoveTo: TileInfo
            try {
                tileToMoveTo = selectedUnit.movement.getTileToMoveToThisTurn(targetTile)
            } catch (ex: Exception) {
                Log.error("Exception in getTileToMoveToThisTurn", ex)
                return@run
            } // can't move here

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
                        selectedUnit.action = "moveTo " + targetTile.position.x.toInt() + "," + targetTile.position.y.toInt()
                    if (selectedUnit.currentMovement > 0) worldScreen.bottomUnitTable.selectUnit(selectedUnit)

                    worldScreen.shouldUpdate = true
                    if (selectedUnits.size > 1) { // We have more tiles to move
                        moveUnitToTargetTile(selectedUnits.subList(1, selectedUnits.size), targetTile)
                    } else removeUnitActionOverlay() //we're done here
                } catch (ex: Exception) {
                    Log.error("Exception in moveUnitToTargetTile", ex)
                }
            }
        }
    }

    private fun swapMoveUnitToTargetTile(selectedUnit: MapUnit, targetTile: TileInfo) {
        selectedUnit.movement.swapMoveToTile(targetTile)

        if (selectedUnit.isExploring() || selectedUnit.isMoving())
            selectedUnit.action = null // remove explore on manual swap-move

        // Play something like a swish-swoosh
        SoundPlayer.play(UncivSound.Swap)

        if (selectedUnit.currentMovement > 0) worldScreen.bottomUnitTable.selectUnit(selectedUnit)

        worldScreen.shouldUpdate = true
        removeUnitActionOverlay()
    }

    private fun addTileOverlaysWithUnitMovement(selectedUnits: List<MapUnit>, tileInfo: TileInfo) {
        Concurrency.run("TurnsToGetThere") {
            /** LibGdx sometimes has these weird errors when you try to edit the UI layout from 2 separate threads.
             * And so, all UI editing will be done on the main thread.
             * The only "heavy lifting" that needs to be done is getting the turns to get there,
             * so that and that alone will be relegated to the concurrent thread.
             */

            /** LibGdx sometimes has these weird errors when you try to edit the UI layout from 2 separate threads.
             * And so, all UI editing will be done on the main thread.
             * The only "heavy lifting" that needs to be done is getting the turns to get there,
             * so that and that alone will be relegated to the concurrent thread.
             */

            val unitToTurnsToTile = HashMap<MapUnit, Int>()
            for (unit in selectedUnits) {
                val shortestPath = ArrayList<TileInfo>()
                val turnsToGetThere = if (unit.baseUnit.movesLikeAirUnits()) {
                    if (unit.movement.canReach(tileInfo)) 1
                    else 0
                } else if (unit.isPreparingParadrop()) {
                    if (unit.movement.canReach(tileInfo)) 1
                    else 0
                } else {
                    // this is the most time-consuming call
                    shortestPath.addAll(unit.movement.getShortestPath(tileInfo))
                    shortestPath.size
                }
                unitMovementPaths[unit] = shortestPath
                unitToTurnsToTile[unit] = turnsToGetThere
            }

            launchOnGLThread {
                val unitsWhoCanMoveThere = HashMap(unitToTurnsToTile.filter { it.value != 0 })
                if (unitsWhoCanMoveThere.isEmpty()) { // give the regular tile overlays with no unit movement
                    addTileOverlays(tileInfo)
                    worldScreen.shouldUpdate = true
                    return@launchOnGLThread
                }

                val turnsToGetThere = unitsWhoCanMoveThere.values.maxOrNull()!!

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

    private fun addTileOverlaysWithUnitSwapping(selectedUnit: MapUnit, tileInfo: TileInfo) {
        if (!selectedUnit.movement.canUnitSwapTo(tileInfo)) { // give the regular tile overlays with no unit swapping
            addTileOverlays(tileInfo)
            worldScreen.shouldUpdate = true
            return
        }
        if (UncivGame.Current.settings.singleTapMove) {
            swapMoveUnitToTargetTile(selectedUnit, tileInfo)
        }
        else {
            // Add "swap with" button
            val swapWithButtonDto = SwapWithButtonDto(selectedUnit, tileInfo)
            addTileOverlays(tileInfo, swapWithButtonDto)
        }
        worldScreen.shouldUpdate = true
    }

    private fun addTileOverlays(tileInfo: TileInfo, buttonDto: ButtonDto? = null) {
        for (group in tileGroups[tileInfo]!!) {
            val table = Table().apply { defaults().pad(10f) }
            if (buttonDto != null && worldScreen.canChangeState)
                table.add(
                    when (buttonDto) {
                        is MoveHereButtonDto -> getMoveHereButton(buttonDto)
                        is SwapWithButtonDto -> getSwapWithButton(buttonDto)
                        else -> null
                    }
                )

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

    val buttonSize = 60f
    val smallerCircleSizes = 25f

    private fun getMoveHereButton(dto: MoveHereButtonDto): Group {
        val moveHereButton = ImageGetter.getStatIcon("Movement")
            .apply { color = Color.BLACK; width = buttonSize / 2; height = buttonSize / 2 }
            .surroundWithCircle(buttonSize-2, false)
            .surroundWithCircle(buttonSize, false, Color.BLACK)


        val numberCircle = dto.unitToTurnsToDestination.values.maxOrNull()!!.toString().toLabel(fontSize = 14)
            .apply { setAlignment(Align.center) }
            .surroundWithCircle(smallerCircleSizes-2, color = ImageGetter.getBlue().darken(0.3f))
            .surroundWithCircle(smallerCircleSizes,false)

        moveHereButton.addActor(numberCircle)

        val firstUnit = dto.unitToTurnsToDestination.keys.first()
        val unitIcon = if (dto.unitToTurnsToDestination.size == 1) UnitGroup(firstUnit, smallerCircleSizes)
        else dto.unitToTurnsToDestination.size.toString().toLabel(fontColor = firstUnit.civInfo.nation.getInnerColor()).apply { setAlignment(Align.center) }
                .surroundWithCircle(smallerCircleSizes).apply { circle.color = firstUnit.civInfo.nation.getOuterColor() }
        unitIcon.y = buttonSize - unitIcon.height
        moveHereButton.addActor(unitIcon)

        val unitsThatCanMove = dto.unitToTurnsToDestination.keys.filter { it.currentMovement > 0 }
        if (unitsThatCanMove.isEmpty()) moveHereButton.color.a = 0.5f
        else {
            moveHereButton.onActivation(UncivSound.Silent) {
                UncivGame.Current.settings.addCompletedTutorialTask("Move unit")
                if (unitsThatCanMove.any { it.baseUnit.movesLikeAirUnits() })
                    UncivGame.Current.settings.addCompletedTutorialTask("Move an air unit")
                moveUnitToTargetTile(unitsThatCanMove, dto.tileInfo)
            }
            moveHereButton.keyShortcuts.add(KeyCharAndCode.TAB)
        }
        return moveHereButton
    }

    private fun getSwapWithButton(dto: SwapWithButtonDto): Group {
        val swapWithButton = Group().apply { width = buttonSize;height = buttonSize; }
        swapWithButton.addActor(ImageGetter.getCircle().apply { width = buttonSize; height = buttonSize })
        swapWithButton.addActor(
            ImageGetter.getImage("OtherIcons/Swap")
            .apply { color = Color.BLACK; width = buttonSize / 2; height = buttonSize / 2; center(swapWithButton) })

        val unitIcon = UnitGroup(dto.unit, smallerCircleSizes)
        unitIcon.y = buttonSize - unitIcon.height
        swapWithButton.addActor(unitIcon)

        swapWithButton.onActivation(UncivSound.Silent) {
            UncivGame.Current.settings.addCompletedTutorialTask("Move unit")
            if (dto.unit.baseUnit.movesLikeAirUnits())
                UncivGame.Current.settings.addCompletedTutorialTask("Move an air unit")
            swapMoveUnitToTargetTile(dto.unit, dto.tileInfo)
        }
        swapWithButton.keyShortcuts.add(KeyCharAndCode.TAB)

        return swapWithButton
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

    /** Clear all arrows to be drawn on the next update. */
    fun resetArrows() {
        for (tile in tileGroups.values) {
            for (group in tile) {
                group.resetArrows()
            }
        } // Inefficient?
    }

    /** Add an arrow to draw on the next update. */
    fun addArrow(fromTile: TileInfo, toTile: TileInfo, arrowType: MapArrowType) {
        val tile = tileGroups[fromTile]
        if (tile != null) for (group in tile) {
            group.addArrow(toTile, arrowType)
        }
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


            if (tileGroup.tileInfo.getShownImprovement(viewingCiv) == Constants.barbarianEncampment
                    && tileGroup.tileInfo.position in viewingCiv.exploredTiles)
                tileGroup.showHighlight(Color.RED)

            val unitsInTile = tileGroup.tileInfo.getUnits()
            val canSeeEnemy = unitsInTile.any() && unitsInTile.first().civInfo.isAtWarWith(viewingCiv)
                    && tileGroup.showMilitaryUnit(viewingCiv)
            if (tileGroup.isViewable(viewingCiv) && canSeeEnemy)
                tileGroup.showHighlight(Color.RED) // Display ALL viewable enemies with a red circle so that users don't need to go "hunting" for enemy units
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

        // Same as below - randomly, tileGroups doesn't seem to contain the selected tile, and this doesn't seem reproducible
        val worldTileGroupsForSelectedTile = tileGroups[selectedTile]
        if (worldTileGroupsForSelectedTile != null)
            for (group in worldTileGroupsForSelectedTile)
                group.showHighlight(Color.WHITE)

        zoom(scaleX) // zoom to current scale, to set the size of the city buttons after "next turn"
    }

    private fun updateTilegroupsForSelectedUnit(unit: MapUnit, playerViewableTilePositions: HashSet<Vector2>) {
        val tileGroup = tileGroups[unit.getTile()] ?: return
        // Entirely unclear when this happens, but this seems to happen since version 520 (3.12.9)
        // so maybe has to do with the construction list being async?
        for (group in tileGroup) {
            group.selectUnit(unit)
        }

        // Fade out less relevant images if a military unit is selected
        val fadeout = if (unit.isCivilian()) 1f
        else 0.5f
        for (tile in allWorldTileGroups) {
            if (tile.icons.populationIcon != null) tile.icons.populationIcon!!.color.a = fadeout

            val shownImprovement = unit.civInfo.lastSeenImprovement[tile.tileInfo.position]
            if (tile.icons.improvementIcon != null
                && shownImprovement != null && shownImprovement != Constants.barbarianEncampment
                && unit.civInfo.gameInfo.ruleSet.tileImprovements[shownImprovement]!!.isAncientRuinsEquivalent())
                tile.icons.improvementIcon!!.color.a = fadeout
            if (tile.resourceImage != null) tile.resourceImage!!.color.a = fadeout
        }

        if (worldScreen.bottomUnitTable.selectedUnitIsSwapping) {
            val unitSwappableTiles = unit.movement.getUnitSwappableTiles()
            val swapUnitsTileOverlayColor = Color.PURPLE
            for (tile in unitSwappableTiles)  {
                for (tileToColor in tileGroups[tile]!!) {
                    tileToColor.showHighlight(swapUnitsTileOverlayColor,
                        if (UncivGame.Current.settings.singleTapMove) 0.7f else 0.3f)
                }
            }
            return // We don't want to show normal movement or attack overlays in unit-swapping mode
        }

        val isAirUnit = unit.baseUnit.movesLikeAirUnits()
        val moveTileOverlayColor = if (unit.isPreparingParadrop()) Color.BLUE else Color.WHITE
        val tilesInMoveRange = unit.movement.getReachableTilesInCurrentTurn()

        for (tile in tilesInMoveRange) {
            for (tileToColor in tileGroups[tile]!!) {
                if (isAirUnit)
                    if (tile.aerialDistanceTo(unit.getTile()) <= unit.getRange()) {
                        // The tile is within attack range
                        tileToColor.showHighlight(Color.RED, 0.3f)
                    } else {
                        // The tile is within move range
                        tileToColor.showHighlight(Color.BLUE, 0.3f)
                    }
                if (unit.movement.canMoveTo(tile) ||
                        unit.movement.isUnknownTileWeShouldAssumeToBePassable(tile) && !unit.baseUnit.movesLikeAirUnits())
                    tileToColor.showHighlight(moveTileOverlayColor,
                            if (UncivGame.Current.settings.singleTapMove || isAirUnit) 0.7f else 0.3f)
            }
        }

        // Movement paths
        if (unitMovementPaths.containsKey(unit)) {
            for (tile in unitMovementPaths[unit]!!) {
                for (tileToColor in tileGroups[tile]!!)
                    tileToColor.showHighlight(Color.SKY, 0.8f)
            }
        }

        if (unit.isMoving()) {
            val destinationTileGroups = tileGroups[unit.getMovementDestination()]!!
            for (destinationTileGroup in destinationTileGroups)
                destinationTileGroup.showHighlight(Color.WHITE, 0.7f)
        }

        val attackableTiles: List<AttackableTile> = if (unit.isCivilian()) listOf()
        else {
            BattleHelper.getAttackableEnemies(unit, unit.movement.getDistanceToTiles())
                    .filter {
                        (UncivGame.Current.viewEntireMapForDebug ||
                                playerViewableTilePositions.contains(it.tileToAttack.position))
                    }
                    .distinctBy { it.tileToAttack }
        }

        for (attackableTile in attackableTiles) {
            for (tileGroupToAttack in tileGroups[attackableTile.tileToAttack]!!) {
                tileGroupToAttack.showHighlight(colorFromRGB(237, 41, 57))
                tileGroupToAttack.showCrosshair(
                        // the targets which cannot be attacked without movements shown as orange-ish
                        if (attackableTile.tileToAttackFrom != unit.currentTile)
                            0.5f
                        else 1f
                )
            }
        }
    }

    private fun updateTilegroupsForSelectedCity(city: CityInfo, playerViewableTilePositions: HashSet<Vector2>) {
        if (!city.canBombard()) return

        val attackableTiles = UnitAutomation.getBombardTargets(city)
                .filter { (UncivGame.Current.viewEntireMapForDebug || playerViewableTilePositions.contains(it.position)) }
        for (attackableTile in attackableTiles) {
            for (group in tileGroups[attackableTile]!!) {
                group.showHighlight(colorFromRGB(237, 41, 57))
                group.showCrosshair()
            }
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
        val tileGroup = allWorldTileGroups.firstOrNull { it.tileInfo.position == vector } ?: return false
        selectedTile = tileGroup.tileInfo
        if (selectUnit || forceSelectUnit != null)
            worldScreen.bottomUnitTable.tileSelected(selectedTile!!, forceSelectUnit)

        // The Y axis of [scrollY] is inverted - when at 0 we're at the top, not bottom - so we invert it back.
        if (!scrollTo(tileGroup.x + tileGroup.width / 2, maxY - (tileGroup.y + tileGroup.width / 2), immediately))
            return false

        removeAction(blinkAction) // so we don't have multiple blinks at once
        blinkAction = Actions.repeat(3, Actions.sequence(
                Actions.run { tileGroup.highlightImage.isVisible = false },
                Actions.delay(.3f),
                Actions.run { tileGroup.highlightImage.isVisible = true },
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
            for (tileGroup in allWorldTileGroups) {
                tileGroup.cityButtonLayerGroup.isTransform = false // to save on rendering time to improve framerate
            }
        }
        if (clampedCityButtonZoom < 1 && clampedCityButtonZoom >= minZoom) {
            for (tileGroup in allWorldTileGroups) {
                // ONLY set those groups that have active city buttons as transformable!
                // This is massively framerate-improving!
                if (tileGroup.cityButtonLayerGroup.hasChildren())
                    tileGroup.cityButtonLayerGroup.isTransform = true
                tileGroup.cityButtonLayerGroup.setScale(clampedCityButtonZoom)
            }
        }
    }

    fun removeUnitActionOverlay() {
        for (overlay in unitActionOverlays)
            overlay.remove()
        unitActionOverlays.clear()
    }

    // For debugging purposes
    override fun draw(batch: Batch?, parentAlpha: Float) = super.draw(batch, parentAlpha)

    override fun act(delta: Float) = super.act(delta)
}
