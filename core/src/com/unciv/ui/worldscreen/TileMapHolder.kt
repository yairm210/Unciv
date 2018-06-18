package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener
import com.unciv.logic.HexMath
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.gamebasics.unit.UnitType
import com.unciv.ui.tilegroups.WorldTileGroup
import com.unciv.ui.utils.addClickListener
import com.unciv.ui.utils.colorFromRGB

class TileMapHolder(internal val worldScreen: WorldScreen, internal val tileMap: TileMap, internal val civInfo: CivilizationInfo) : ScrollPane(null) {
    internal var selectedTile: TileInfo? = null
    val tileGroups = HashMap<TileInfo, WorldTileGroup>()

    internal fun addTiles() {
        val allTiles = Group()
        val groupPadding = 300f // This is so that no tile will be stuck "on the side" and be unreachable or difficult to reach

        var topX = 0f
        var topY = 0f
        var bottomX = 0f
        var bottomY = 0f

        for (tileInfo in tileMap.values) {
            val group = WorldTileGroup(tileInfo)

            group.addClickListener {
                worldScreen.displayTutorials("TileClicked")

                selectedTile = tileInfo
                worldScreen.bottomBar.unitTable.tileSelected(tileInfo)
                worldScreen.update()
            }



            val positionalVector = HexMath().Hex2WorldCoords(tileInfo.position)
            val groupSize = 50
            group.setPosition(worldScreen.stage.width / 2 + positionalVector.x * 0.8f * groupSize.toFloat(),
                    worldScreen.stage.height / 2 + positionalVector.y * 0.8f * groupSize.toFloat())
            tileGroups[tileInfo] = group
            allTiles.addActor(group)
            topX = Math.max(topX, group.x + groupSize)
            topY = Math.max(topY, group.y + groupSize)
            bottomX = Math.min(bottomX, group.x)
            bottomY = Math.min(bottomY, group.y)
        }

        for (group in tileGroups.values) {
            group.moveBy(-bottomX + groupPadding, -bottomY + groupPadding)
        }

        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        allTiles.setSize(topX - bottomX + groupPadding*2, topY - bottomY + groupPadding*2)


        widget = allTiles
        setFillParent(true)
        setOrigin(worldScreen.stage.width/2,worldScreen.stage.height/2)
        setSize(worldScreen.stage.width, worldScreen.stage.height)
        addListener(object : ActorGestureListener() {
            var lastScale = 1f
            internal var lastInitialDistance = 0f

            override fun zoom(event: InputEvent?, initialDistance: Float, distance: Float) {
                if (lastInitialDistance != initialDistance) {
                    lastInitialDistance = initialDistance
                    lastScale = scaleX
                }
                val scale: Float = Math.sqrt((distance / initialDistance).toDouble()).toFloat() * lastScale
                if (scale < 1) return
                setScale(scale)
                for(tilegroup in tileGroups.values.filter { it.cityButton!=null })
                    tilegroup.cityButton!!.setScale(1/scale)
            }

        })
        layout() // Fit the scroll pane to the contents - otherwise, setScroll won't work!
    }

    internal fun updateTiles() {
        val playerViewableTiles = civInfo.getViewableTiles().toHashSet()

        for (WG in tileGroups.values){
            WG.update(playerViewableTiles.contains(WG.tileInfo))
            val unitsInTile = WG.tileInfo.getUnits()
            if(playerViewableTiles.contains(WG.tileInfo)
                    && unitsInTile.isNotEmpty() && unitsInTile.first().civInfo!=civInfo)
                WG.showCircle(Color.RED)
        } // Display ALL viewable enemies ewith a red circle so that users don't need to go "hunting" for enemy units

        if(worldScreen.bottomBar.unitTable.selectedUnit!=null){
            val unit = worldScreen.bottomBar.unitTable.selectedUnit!!
            tileGroups[unit.getTile()]!!.addWhiteHaloAroundUnit(unit)

            for(tile: TileInfo in unit.getDistanceToTiles().keys)
                tileGroups[tile]!!.showCircle(colorFromRGB(0, 120, 215))

            val unitType = unit.getBaseUnit().unitType
            val attackableTiles: List<TileInfo> = when{
                unitType==UnitType.Civilian -> unit.getDistanceToTiles().keys.toList()
                unit.getBaseUnit().unitType.isMelee() -> unit.getDistanceToTiles().keys.toList()
                unitType.isRanged() -> unit.getTile().getTilesInDistance(2)
                else -> throw Exception("UnitType isn't Civilian, Melee or Ranged???")
            }


            for (tile in attackableTiles.filter {
                it.getUnits().isNotEmpty()
                        && it.getUnits().first().owner != unit.owner
                        && playerViewableTiles.contains(it)}) {
                if(unit.getBaseUnit().unitType== UnitType.Civilian) tileGroups[tile]!!.hideCircle()
                else tileGroups[tile]!!.showCircle(colorFromRGB(237, 41, 57))
            }
        }

        if(selectedTile!=null)
            tileGroups[selectedTile!!]!!.showCircle(Color.WHITE)

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
        worldScreen.update()
    }


}
