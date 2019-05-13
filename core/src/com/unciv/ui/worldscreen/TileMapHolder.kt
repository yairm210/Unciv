package com.unciv.ui.worldscreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener
import com.unciv.UnCivGame
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.city.CityInfo
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

    var unitActionOverlay :Actor?=null
    var removeUnitActionOverlay=false

    // Used to transfer data on the "move here" button that should be created, from the side thread to the main thread
    class MoveHereButtonDto(val unit: MapUnit, val tileInfo: TileInfo, val turnsToGetThere: Int)

    internal fun addTiles() {

        val daTileGroups = tileMap.values.map { WorldTileGroup(worldScreen, it) }

        for(tileGroup in daTileGroups) tileGroups[tileGroup.tileInfo]=tileGroup

        val allTiles = TileGroupMap(daTileGroups,worldScreen.stage.width)

        for(tileGroup in tileGroups.values){
            tileGroup.onClick{ onTileClicked(tileGroup.tileInfo)}
        }

        actor = allTiles

        setSize(worldScreen.stage.width*2, worldScreen.stage.height*2)
        setOrigin(width/2,height/2)
        center(worldScreen.stage)
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
                if (scale < 0.5f) return
                setScale(scale)
                for (tilegroup in tileGroups.values.filter { it.cityButton != null })
                    tilegroup.cityButton!!.setScale(1 / scale)
            }

        })
    }

    private fun onTileClicked(tileInfo: TileInfo) {
        worldScreen.displayTutorials("TileClicked")
        if (unitActionOverlay != null) unitActionOverlay!!.remove()
        selectedTile = tileInfo

        val selectedUnit = worldScreen.bottomBar.unitTable.selectedUnit
        worldScreen.bottomBar.unitTable.tileSelected(tileInfo)

        if (selectedUnit != null && selectedUnit.getTile() != tileInfo
                && selectedUnit.canMoveTo(tileInfo) && selectedUnit.movementAlgs().canReach(tileInfo)) {
            // this can take a long time, because of the unit-to-tile calculation needed, so we put it in a different thread
            moveHere(selectedUnit, tileInfo)
            worldScreen.bottomBar.unitTable.selectedUnit = selectedUnit // keep moved unit selected
        }

        worldScreen.shouldUpdate = true
    }

    private fun moveHere(selectedUnit: MapUnit, tileInfo: TileInfo) {
        thread {
            /** LibGdx sometimes has these weird errors when you try to edit the UI layout from 2 separate threads.
             * And so, all UI editing will be done on the main thread.
             * The only "heavy lifting" that needs to be done is getting the turns to get there,
             * so that and that alone will be relegated to the concurrent thread.
             */
            val turnsToGetThere = selectedUnit.movementAlgs().getShortestPath(tileInfo).size // this is what takes the most time, tbh

            Gdx.app.postRunnable {
                if(turnsToGetThere==1) {
                    // single turn instant move
                    selectedUnit.movementAlgs().headTowards(tileInfo)
                } else {
                    // add "move to" button
                    val moveHereButtonDto = MoveHereButtonDto(selectedUnit, tileInfo, turnsToGetThere)
                    addMoveHereButtonToTile(moveHereButtonDto, tileGroups[moveHereButtonDto.tileInfo]!!)
                }
                worldScreen.shouldUpdate = true
            }

        }
    }


    private fun addMoveHereButtonToTile(dto: MoveHereButtonDto, tileGroup: WorldTileGroup) {
        val size = 60f
        val moveHereButton = Group().apply { width = size;height = size; }
        moveHereButton.addActor(ImageGetter.getCircle().apply { width = size; height = size })
        moveHereButton.addActor(ImageGetter.getStatIcon("Movement")
                .apply { color= Color.BLACK; width = size / 2; height = size / 2; center(moveHereButton) })

        val numberCircle = ImageGetter.getCircle().apply { width = size / 2; height = size / 2;color = Color.BLUE }
        moveHereButton.addActor(numberCircle)
        moveHereButton.addActor(dto.turnsToGetThere.toString().toLabel()
                .apply { center(numberCircle); setFontColor(Color.WHITE) })

        val unitIcon = UnitGroup(dto.unit, size / 2)
        unitIcon.y = size - unitIcon.height
        moveHereButton.addActor(unitIcon)

        if (dto.unit.currentMovement > 0)
            moveHereButton.onClick(""){onMoveButtonClick(dto)}

        else moveHereButton.color.a = 0.5f
        addOverlayOnTileGroup(tileGroup, moveHereButton)
        moveHereButton.y += tileGroup.height
        unitActionOverlay = moveHereButton
    }

    private fun onMoveButtonClick(dto: MoveHereButtonDto) {
        // this can take a long time, because of the unit-to-tile calculation needed, so we put it in a different thread
        thread {
            if (dto.unit.movementAlgs().canReach(dto.tileInfo)) {
                try {
                    // Because this is darned concurrent (as it MUST be to avoid ANRs),
                    // there are edge cases where the canReach is true,
                    // but until it reaches the headTowards the board has changed and so the headTowards fails.
                    // I can't think of any way to avoid this,
                    // but it's so rare and edge-case-y that ignoring its failure is actually acceptable, hence the empty catch
                    dto.unit.movementAlgs().headTowards(dto.tileInfo)
                    Sounds.play("whoosh")
                    if (dto.unit.currentTile != dto.tileInfo)
                        dto.unit.action = "moveTo " + dto.tileInfo.position.x.toInt() + "," + dto.tileInfo.position.y.toInt()
                } catch (e: Exception) {
                }
            }

            // we don't update it directly because we're on a different thread; instead, we tell it to update itself
            worldScreen.shouldUpdate = true

            removeUnitActionOverlay=true
        }
    }

    private fun addOverlayOnTileGroup(group:WorldTileGroup, actor: Actor) {
        actor.center(group)
        actor.x+=group.x
        actor.y+=group.y
        group.parent.addActor(actor)
        actor.toFront()
    }

    internal fun updateTiles(civInfo: CivilizationInfo) {
        if(removeUnitActionOverlay){
            removeUnitActionOverlay=false
            unitActionOverlay?.remove()
        }

        val playerViewableTilePositions = civInfo.viewableTiles.map { it.position }.toHashSet()
        val playerViewableInvisibleUnitsTilePositions = civInfo.viewableInvisibleUnitsTiles.map { it.position }.toHashSet()

        for (tileGroup in tileGroups.values){
            val canSeeTile = UnCivGame.Current.viewEntireMapForDebug
                    || playerViewableTilePositions.contains(tileGroup.tileInfo.position)

            val showSubmarine = UnCivGame.Current.viewEntireMapForDebug
                    || playerViewableInvisibleUnitsTilePositions.contains(tileGroup.tileInfo.position)
                    || (!tileGroup.tileInfo.hasEnemySubmarine())
            tileGroup.update(canSeeTile, showSubmarine)

            val unitsInTile = tileGroup.tileInfo.getUnits()
            val canSeeEnemy = unitsInTile.isNotEmpty() && unitsInTile.first().civInfo.isAtWarWith(civInfo)
                    && (showSubmarine || unitsInTile.firstOrNull {!it.isInvisible()}!=null)
            if(canSeeTile && canSeeEnemy)
                tileGroup.showCircle(Color.RED) // Display ALL viewable enemies with a red circle so that users don't need to go "hunting" for enemy units
        }

        if (worldScreen.bottomBar.unitTable.selectedCity!=null){
            val city = worldScreen.bottomBar.unitTable.selectedCity!!
            updateTilegroupsForSelectedCity(city, playerViewableTilePositions)
        } else if(worldScreen.bottomBar.unitTable.selectedUnit!=null){
            val unit = worldScreen.bottomBar.unitTable.selectedUnit!!
            updateTilegroupsForSelectedUnit(unit, playerViewableTilePositions)
        }
        else if(unitActionOverlay!=null){
            unitActionOverlay!!.remove()
            unitActionOverlay=null
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

        // Fade out less relevant images if a military unit is selected
        val fadeout = if (unit.type.isCivilian()) 1f
        else 0.5f
        for (tile in tileGroups.values) {
            if (tile.populationImage != null) tile.populationImage!!.color.a = fadeout
            if (tile.improvementImage != null) tile.improvementImage!!.color.a = fadeout
            if (tile.resourceImage != null) tile.resourceImage!!.color.a = fadeout
        }
    }

    private fun updateTilegroupsForSelectedCity(city: CityInfo, playerViewableTilePositions: HashSet<Vector2>) {
        val attackableTiles: List<TileInfo> = UnitAutomation().getBombardTargets(city)
                    .filter { (UnCivGame.Current.viewEntireMapForDebug || playerViewableTilePositions.contains(it.position)) }
        for (attackableTile in attackableTiles) {
            tileGroups[attackableTile]!!.showCircle(colorFromRGB(237, 41, 57))
            tileGroups[attackableTile]!!.showCrosshair()
        }
    }

    fun setCenterPosition(vector: Vector2, immediately: Boolean =false) {
        val tileGroup = tileGroups.values.first { it.tileInfo.position == vector }
        selectedTile = tileGroup.tileInfo
        worldScreen.bottomBar.unitTable.tileSelected(selectedTile!!)

        val originalScrollX = scrollX
        val originalScrollY = scrollY

        // We want to center on the middle of the tilegroup (TG.getX()+TG.getWidth()/2)
        // and so the scroll position (== filter the screen starts) needs to be half the ScrollMap away
        val finalScrollX = tileGroup.x + tileGroup.width / 2 - width / 2
        
        // Here it's the same, only the Y axis is inverted - when at 0 we're at the top, not bottom - so we invert it back.
        val finalScrollY = maxY - (tileGroup.y + tileGroup.width / 2 - height / 2)

        if(immediately){
            scrollX = finalScrollX
            scrollY = finalScrollY
            updateVisualScroll()
        }
        else {
            addAction(object : FloatAction(0f, 1f, 0.4f) {
                override fun update(percent: Float) {
                    scrollX = finalScrollX * percent + originalScrollX * (1 - percent)
                    scrollY = finalScrollY * percent + originalScrollY * (1 - percent)
                    updateVisualScroll()
                }
            })
        }

        worldScreen.shouldUpdate=true
    }

}