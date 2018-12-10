package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener
import com.unciv.UnCivGame
import com.unciv.logic.HexMath
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.ui.tilegroups.WorldTileGroup
import com.unciv.ui.utils.*
import kotlin.concurrent.thread

class TileMapHolder(internal val worldScreen: WorldScreen, internal val tileMap: TileMap) : ScrollPane(null) {
    internal var selectedTile: TileInfo? = null
    val tileGroups = HashMap<TileInfo, WorldTileGroup>()

    var moveToOverlay :Actor?=null
    val cityButtonOverlays = ArrayList<Actor>()


    // Used to transfer data on the "move here" button that should be created, from the side thread to the main thread
    class MoveHereButtonDto(val unit: MapUnit, val tileInfo: TileInfo, val turnsToGetThere: Int)
    var moveHereButtonDto :MoveHereButtonDto?=null

    internal fun addTiles() {
        val allTiles = Group()
        val groupPadding = 300f // This is so that no tile will be stuck "on the side" and be unreachable or difficult to reach

        var topX = 0f
        var topY = 0f
        var bottomX = 0f
        var bottomY = 0f

        for (tileInfo in tileMap.values) {
            val tileGroup = WorldTileGroup(tileInfo)

            tileGroup.onClick{ onTileClicked(tileInfo, tileGroup)}

            val positionalVector = HexMath().hex2WorldCoords(tileInfo.position)
            val groupSize = 50
            tileGroup.setPosition(worldScreen.stage.width / 2 + positionalVector.x * 0.8f * groupSize.toFloat(),
                    worldScreen.stage.height / 2 + positionalVector.y * 0.8f * groupSize.toFloat())
            tileGroups[tileInfo] = tileGroup
            allTiles.addActor(tileGroup)
            topX = Math.max(topX, tileGroup.x + groupSize)
            topY = Math.max(topY, tileGroup.y + groupSize)
            bottomX = Math.min(bottomX, tileGroup.x)
            bottomY = Math.min(bottomY, tileGroup.y)
        }

        for (group in tileGroups.values) {
            group.moveBy(-bottomX + groupPadding, -bottomY + groupPadding)
        }

        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        allTiles.setSize(topX - bottomX + groupPadding*2, topY - bottomY + groupPadding*2)

        actor = allTiles
        setFillParent(true)
        setOrigin(worldScreen.stage.width/2,worldScreen.stage.height/2)
        setSize(worldScreen.stage.width, worldScreen.stage.height)
        addZoomListener()
        layout() // Fit the scroll pane to the contents - otherwise, setScroll won't work!
    }

    private fun addZoomListener() {
        addListener(object : ActorGestureListener() {
            var lastScale = 1f
            var lastInitialDistance = 0f

            override fun zoom(event: InputEvent?, initialDistance: Float, distance: Float) {
                if (lastInitialDistance != initialDistance) {
                    lastInitialDistance = initialDistance
                    lastScale = scaleX
                }
                val scale: Float = Math.sqrt((distance / initialDistance).toDouble()).toFloat() * lastScale
                if (scale < 1) return
                setScale(scale)
                for (tilegroup in tileGroups.values.filter { it.cityButton != null })
                    tilegroup.cityButton!!.setScale(1 / scale)
            }

        })
    }

    private fun onTileClicked(tileInfo: TileInfo, tileGroup: WorldTileGroup){
            worldScreen.displayTutorials("TileClicked")
            if (moveToOverlay != null) moveToOverlay!!.remove()
            selectedTile = tileInfo

            val selectedUnit = worldScreen.bottomBar.unitTable.selectedUnit
            if (selectedUnit != null && selectedUnit.getTile() != tileInfo
                    && selectedUnit.canMoveTo(tileInfo) && selectedUnit.movementAlgs().canReach(tileInfo)) {
                // this can take a long time, because of the unit-to-tile calculation needed, so we put it in a different thread
                queueAddMoveHereButton(selectedUnit, tileInfo)
            }

            worldScreen.bottomBar.unitTable.tileSelected(tileInfo)
            worldScreen.shouldUpdate=true
    }

    private fun queueAddMoveHereButton(selectedUnit: MapUnit, tileInfo: TileInfo) {
        thread {
            /** LibGdx sometimes has these wierd errors when you try to edit the UI layout from 2 separate thread.
             * And so, all UI editing will be done on the main thread.
             * The only "heavy lifting" that needs to be done is getting the turns to get there,
             * so that and that alone will be relegated to the concurrent thread.
             */
            val turnsToGetThere = selectedUnit.movementAlgs().getShortestPath(tileInfo).size // this is what takes the most time, tbh
            moveHereButtonDto = MoveHereButtonDto(selectedUnit, tileInfo, turnsToGetThere)
            worldScreen.shouldUpdate = true // when the world screen updates, is calls our updateTiles,
            // which will add the move here button *on the main thread*! Problem solved!
        }
    }


    private fun addMoveHereButtonToTile(dto: MoveHereButtonDto, tileGroup: WorldTileGroup) {
        val size = 60f
        val moveHereButton = Group().apply { width = size;height = size; }
        moveHereButton.addActor(ImageGetter.getCircle().apply { width = size; height = size })
        moveHereButton.addActor(ImageGetter.getStatIcon("Movement").apply { width = size / 2; height = size / 2; center(moveHereButton) })

        val numberCircle = ImageGetter.getCircle().apply { width = size / 2; height = size / 2;color = Color.BLUE }
        moveHereButton.addActor(numberCircle)
        moveHereButton.addActor(Label(dto.turnsToGetThere.toString(), CameraStageBaseScreen.skin)
                .apply { center(numberCircle); setFontColor(Color.WHITE) })

        val unitIcon = UnitGroup(dto.unit, size / 2)
        unitIcon.y = size - unitIcon.height
        moveHereButton.addActor(unitIcon)

        if (dto.unit.currentMovement > 0)
            moveHereButton.onClick {
                // this can take a long time, because of the unit-to-tile calculation needed, so we put it in a different thread
                kotlin.concurrent.thread {
                    if (dto.unit.movementAlgs().canReach(dto.tileInfo)) {
                        try {
                            // Because this is darned concurrent (as it MUST be to avoid ANRs),
                            // there are edge cases where the canReach is true,
                            // but until it reaches the headTowards the board has changed and so the headTowards fails.
                            // I can't think of any way to avoid this,
                            // but it's so rare and edge-case-y that ignoring its failure is actually acceptable, hence the empty catch
                            dto.unit.movementAlgs().headTowards(dto.tileInfo)
                            if (dto.unit.currentTile != dto.tileInfo)
                                dto.unit.action = "moveTo " + dto.tileInfo.position.x.toInt() + "," + dto.tileInfo.position.y.toInt()
                        }
                        catch (e:Exception){}
                    }

                    // we don't update it directly because we're on a different thread; instead, we tell it to update itself
                    worldScreen.shouldUpdate = true
                    moveToOverlay?.remove()
                    moveToOverlay = null
                }
            }

        else moveHereButton.color.a = 0.5f
        addOverlayOnTileGroup(tileGroup, moveHereButton).apply { width = size; height = size }
        moveHereButton.y += tileGroup.height
        moveToOverlay = moveHereButton
    }

    private fun addOverlayOnTileGroup(group:WorldTileGroup, actor: Actor) {
        actor.center(group)
        actor.x+=group.x
        actor.y+=group.y
        group.parent.addActor(actor)
        actor.toFront()
    }

    internal fun updateTiles(civInfo: CivilizationInfo) {
        val playerViewableTilePositions = civInfo.viewableTiles.map { it.position }.toHashSet()

        cityButtonOverlays.forEach{it.remove()}
        cityButtonOverlays.clear()

        for (tileGroup in tileGroups.values){
            val canSeeTile = UnCivGame.Current.viewEntireMapForDebug
                    || playerViewableTilePositions.contains(tileGroup.tileInfo.position)
            tileGroup.update(canSeeTile)
            val unitsInTile = tileGroup.tileInfo.getUnits()
            if(canSeeTile && unitsInTile.isNotEmpty() && !unitsInTile.first().civInfo.isPlayerCivilization())
                tileGroup.showCircle(Color.RED) // Display ALL viewable enemies with a red circle so that users don't need to go "hunting" for enemy units
        }

        if(moveHereButtonDto!=null) {
            addMoveHereButtonToTile(moveHereButtonDto!!, tileGroups[moveHereButtonDto!!.tileInfo]!!)
            moveHereButtonDto=null
        }

        if(worldScreen.bottomBar.unitTable.selectedUnit!=null){
            val unit = worldScreen.bottomBar.unitTable.selectedUnit!!
            updateTilegroupsForSelectedUnit(unit, playerViewableTilePositions)
        }

        else if(moveToOverlay!=null){
            moveToOverlay!!.remove()
            moveToOverlay=null
        }

        if(selectedTile!=null)
            tileGroups[selectedTile!!]!!.showCircle(Color.WHITE)
    }

    private fun updateTilegroupsForSelectedUnit(unit: MapUnit, playerViewableTilePositions: HashSet<Vector2>) {
        tileGroups[unit.getTile()]!!.selectUnit(unit)

        for (tile: TileInfo in unit.getDistanceToTiles().keys)
            if (unit.canMoveTo(tile))
                tileGroups[tile]!!.showCircle(colorFromRGB(0, 120, 215))

        val unitType = unit.type
        val attackableTiles: List<TileInfo> = when {
            unitType.isCivilian() -> listOf()
            else -> UnitAutomation().getAttackableEnemies(unit, unit.getDistanceToTiles()).map { it.tileToAttack }
                    .filter { (UnCivGame.Current.viewEntireMapForDebug || playerViewableTilePositions.contains(it.position)) }
        }
        for (attackableTile in attackableTiles) {
            tileGroups[attackableTile]!!.showCircle(colorFromRGB(237, 41, 57))
            tileGroups[attackableTile]!!.showCrosshair()
        }

        // Fadeout less relevant images if a military unit is selected
        val fadeout = if (unit.type.isCivilian()) 1f
        else 0.5f
        for (tile in tileGroups.values) {
            if (tile.populationImage != null) tile.populationImage!!.color.a = fadeout
            if (tile.improvementImage != null) tile.improvementImage!!.color.a = fadeout
            if (tile.resourceImage != null) tile.resourceImage!!.color.a = fadeout
        }
    }

    fun setCenterPosition(vector: Vector2) {
        val tileGroup = tileGroups.values.first { it.tileInfo.position == vector }
        selectedTile = tileGroup.tileInfo
        worldScreen.bottomBar.unitTable.tileSelected(selectedTile!!)
        // We want to center on the middle of TG (TG.getX()+TG.getWidth()/2)
        // and so the scroll position (== filter the screen starts) needs to be half a screen away
        scrollX = tileGroup.x + tileGroup.width / 2 - worldScreen.stage.width / 2
        // Here it's the same, only the Y axis is inverted - when at 0 we're at the top, not bottom - so we invert it back.
        scrollY = maxY - (tileGroup.y + tileGroup.width / 2 - worldScreen.stage.height / 2)
        updateVisualScroll()
        worldScreen.shouldUpdate=true
    }


}