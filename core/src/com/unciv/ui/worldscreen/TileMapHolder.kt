package com.unciv.ui.worldscreen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.FloatAction
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener
import com.unciv.Constants
import com.unciv.UnCivGame
import com.unciv.logic.automation.UnitAutomation
import com.unciv.logic.city.CityInfo
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.MapUnit
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.ui.tilegroups.WorldTileGroup
import com.unciv.ui.utils.*
import com.unciv.ui.worldscreen.unit.UnitContextMenu
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
            tileGroup.addListener (object: ActorGestureListener() {
                override fun tap(event: InputEvent?, x: Float, y: Float, count: Int, button: Int) {
                    onTileClicked(tileGroup.tileInfo)
                }
                override fun longPress(actor: Actor?, x: Float, y: Float): Boolean {
                    return onTileLongClicked(tileGroup.tileInfo)
                }

            })

            tileGroup.cityButtonLayerGroup.onClick("") {
                onTileClicked(tileGroup.tileInfo)
            }
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
                // deselect any unit, as zooming occasionally forwards clicks on to the map
                worldScreen.bottomBar.unitTable.selectedUnit = null
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
        unitActionOverlay?.remove()
        selectedTile = tileInfo

        val unitTable = worldScreen.bottomBar.unitTable
        val previousSelectedUnit = unitTable.selectedUnit
        unitTable.tileSelected(tileInfo)
        val newSelectedUnit = unitTable.selectedUnit

        if (previousSelectedUnit != null && previousSelectedUnit.getTile() != tileInfo
                && previousSelectedUnit.canMoveTo(tileInfo) && previousSelectedUnit.movementAlgs().canReach(tileInfo)) {
            // this can take a long time, because of the unit-to-tile calculation needed, so we put it in a different thread
            addTileOverlaysWithUnitMovement(previousSelectedUnit, tileInfo)
        }
        else addTileOverlays(tileInfo) // no unit movement but display the units in the tile etc.


        if(newSelectedUnit==null || newSelectedUnit.type==UnitType.Civilian){
            val unitsInTile = selectedTile!!.getUnits()
            if(unitsInTile.isNotEmpty() && unitsInTile.first().civInfo.isAtWarWith(worldScreen.currentPlayerCiv)){
                // try to select the closest city to bombard this guy
                val citiesThatCanBombard = selectedTile!!.getTilesInDistance(2)
                        .filter { it.isCityCenter() }.map { it.getCity()!! }
                        .filter { !it.attackedThisTurn }
                if(citiesThatCanBombard.isNotEmpty())
                    unitTable.citySelected(citiesThatCanBombard.first())
            }
        }

        worldScreen.shouldUpdate = true
    }

    private fun addTileOverlaysWithUnitMovement(selectedUnit: MapUnit, tileInfo: TileInfo) {
        thread {
            /** LibGdx sometimes has these weird errors when you try to edit the UI layout from 2 separate threads.
             * And so, all UI editing will be done on the main thread.
             * The only "heavy lifting" that needs to be done is getting the turns to get there,
             * so that and that alone will be relegated to the concurrent thread.
             */
            val turnsToGetThere = if(selectedUnit.type.isAirUnit()) 1
                else selectedUnit.movementAlgs().getShortestPath(tileInfo).size // this is what takes the most time, tbh

            Gdx.app.postRunnable {
                if(UnCivGame.Current.settings.singleTapMove && turnsToGetThere==1) {
                    // single turn instant move
                    selectedUnit.movementAlgs().headTowards(tileInfo)
                    worldScreen.bottomBar.unitTable.selectedUnit = selectedUnit // keep moved unit selected
                } else {
                    // add "move to" button
                    val moveHereButtonDto = MoveHereButtonDto(selectedUnit, tileInfo, turnsToGetThere)
                    addTileOverlays(tileInfo, moveHereButtonDto)
                }
                worldScreen.shouldUpdate = true
            }

        }
    }

    private fun addTileOverlays(tileInfo: TileInfo, moveHereDto:MoveHereButtonDto?=null){
        val table = Table().apply { defaults().pad(10f) }
        if(moveHereDto!=null)
            table.add(getMoveHereButton(moveHereDto))

        if (tileInfo.isCityCenter() && tileInfo.getOwner()==worldScreen.currentPlayerCiv) {
            for (unit in tileInfo.getCity()!!.getCenterTile().getUnits()) {
                val unitGroup = UnitGroup(unit, 60f).surroundWithCircle(80f)
                unitGroup.circle.color = Color.GRAY.cpy().apply { a = 0.5f }
                if (unit.currentMovement == 0f) unitGroup.color.a = 0.5f
                unitGroup.touchable = Touchable.enabled
                unitGroup.onClick {
                    worldScreen.bottomBar.unitTable.selectedUnit = unit
                    worldScreen.bottomBar.unitTable.selectedCity = null
                    worldScreen.shouldUpdate = true
                    removeUnitActionOverlay = true
                }
                table.add(unitGroup)
            }
        }

        addOverlayOnTileGroup(tileInfo, table)
        table.moveBy(0f,60f)
    }

    private fun getMoveHereButton(dto: MoveHereButtonDto): Group {
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
            moveHereButton.onClick(""){
                UnitContextMenu(this, dto.unit, dto.tileInfo).onMoveButtonClick()
            }

        else moveHereButton.color.a = 0.5f
        return moveHereButton
    }



    fun onTileLongClicked(tileInfo: TileInfo): Boolean {

        unitActionOverlay?.remove()
        selectedTile = tileInfo
        val selectedUnit = worldScreen.bottomBar.unitTable.selectedUnit
        worldScreen.bottomBar.unitTable.tileSelected(tileInfo)
        worldScreen.shouldUpdate = true

        if (selectedUnit != null) {
            addOverlayOnTileGroup(tileInfo, UnitContextMenu(this, selectedUnit, tileInfo))
            return true
        }

        return false
    }

    private fun addOverlayOnTileGroup(tileInfo: TileInfo, actor: Actor) {

        val group = tileGroups[tileInfo]!!

        actor.center(group)
        actor.x+=group.x
        actor.y+=group.y
        group.parent.addActor(actor) // Add the overlay to the TileGroupMap - it's what actually displays all the tiles
        actor.toFront()

        actor.y += actor.height
        unitActionOverlay = actor

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

            if(tileGroup.tileInfo.improvement==Constants.barbarianEncampment
                    && tileGroup.tileInfo.position in civInfo.exploredTiles)
                tileGroup.showCircle(Color.RED)

            val unitsInTile = tileGroup.tileInfo.getUnits()
            val canSeeEnemy = unitsInTile.isNotEmpty() && unitsInTile.first().civInfo.isAtWarWith(civInfo)
                    && (showSubmarine || unitsInTile.firstOrNull {!it.isInvisible()}!=null)
            if(canSeeTile && canSeeEnemy)
                tileGroup.showCircle(Color.RED) // Display ALL viewable enemies with a red circle so that users don't need to go "hunting" for enemy units
        }

        val unitTable = worldScreen.bottomBar.unitTable
        when {
            unitTable.selectedCity!=null -> {
                val city = unitTable.selectedCity!!
                updateTilegroupsForSelectedCity(city, playerViewableTilePositions)
            }
            unitTable.selectedUnit!=null -> {
                val unit = unitTable.selectedUnit!!
                updateTilegroupsForSelectedUnit(unit, playerViewableTilePositions)
            }
            unitActionOverlay!=null      -> {
                unitActionOverlay!!.remove()
                unitActionOverlay=null
            }
        }

        tileGroups[selectedTile]?.showCircle(Color.WHITE)
    }

    private fun updateTilegroupsForSelectedUnit(unit: MapUnit, playerViewableTilePositions: HashSet<Vector2>) {

        tileGroups[unit.getTile()]!!.selectUnit(unit)

        val isAirUnit = unit.type.isAirUnit()
        val tilesInMoveRange = if(isAirUnit) unit.getTile().getTilesInDistance(unit.getRange())
        else unit.getDistanceToTiles().keys

        if(isAirUnit)
            for(tile in tilesInMoveRange)
                tileGroups[tile]!!.showCircle(Color.BLUE,0.3f)

        for (tile: TileInfo in tilesInMoveRange)
            if (unit.canMoveTo(tile))
                tileGroups[tile]!!.showCircle(Color.WHITE,
                        if (UnCivGame.Current.settings.singleTapMove || isAirUnit) 0.7f else 0.3f)


        val unitType = unit.type
        val attackableTiles: List<TileInfo> = if (unitType.isCivilian()) listOf()
        else {
            val tiles = UnitAutomation().getAttackableEnemies(unit, unit.getDistanceToTiles()).map { it.tileToAttack }
            tiles.filter { (UnCivGame.Current.viewEntireMapForDebug || playerViewableTilePositions.contains(it.position)) }
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
            if (tile.improvementImage != null && tile.tileInfo.improvement!=Constants.barbarianEncampment
                    && tile.tileInfo.improvement!=Constants.ancientRuins)
                tile.improvementImage!!.color.a = fadeout
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


    fun setCenterPosition(vector: Vector2, immediately: Boolean = false, selectUnit: Boolean = true) {
        val tileGroup = tileGroups.values.first { it.tileInfo.position == vector }
        selectedTile = tileGroup.tileInfo
        if(selectUnit)
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

        worldScreen.shouldUpdate=true
    }

}