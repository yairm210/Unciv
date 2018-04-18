package com.unciv.ui.worldscreen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.UnitType
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.tilegroups.WorldTileGroup
import com.unciv.ui.utils.HexMath

class TileMapHolder(internal val worldScreen: WorldScreen, internal val tileMap: TileMap, internal val civInfo: CivilizationInfo) : ScrollPane(null) {
    internal var selectedTile: TileInfo? = null
    val tileGroups = HashMap<TileInfo, WorldTileGroup>()

    internal fun addTiles() {
        val allTiles = Group()

        var topX = 0f
        var topY = 0f
        var bottomX = 0f
        var bottomY = 0f

        for (tileInfo in tileMap.values) {
            val group = WorldTileGroup(tileInfo)

            group.addClickListener {
                worldScreen.displayTutorials("TileClicked")

                selectedTile = tileInfo
                worldScreen.unitTable.tileSelected(tileInfo)
                worldScreen.update()
            }



            val positionalVector = HexMath.Hex2WorldCoords(tileInfo.position)
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
            group.moveBy(-bottomX + 50, -bottomY + 50)
        }

        // there are tiles "below the zero",
        // so we zero out the starting position of the whole board so they will be displayed as well
        allTiles.setSize(100 + topX - bottomX, 100 + topY - bottomY)


        widget = allTiles
        setFillParent(true)
        setOrigin(worldScreen.stage.width / 2, worldScreen.stage.height / 2)
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
    }

    internal fun updateTiles() {
        for (WG in tileGroups.values){
            WG.hideCircle()
            WG.update(false)
        }

        val civViewableTiles = civInfo.getViewableTiles()
        for (string in civViewableTiles
                .filter { tileGroups.containsKey(it) }) {

            tileGroups[string]!!.run {
                tileInfo.explored = true
                update(true)
            }
        }

        if(worldScreen.unitTable.currentlyExecutingAction!=null)
            for(tile: TileInfo in worldScreen.unitTable.getTilesForCurrentlyExecutingAction())
                tileGroups[tile]!!.showCircle(Color(0f,120/255f,215/255f,1f))

        else if(worldScreen.unitTable.selectedUnit!=null){
            val unit = worldScreen.unitTable.selectedUnit!!
            tileGroups[unit.getTile()]!!.addWhiteCircleAroundUnit()
            val attackableTiles:List<TileInfo>
            when(unit.getBaseUnit().unitType){
                UnitType.Civilian -> return
                UnitType.Melee -> attackableTiles = unit.getDistanceToTiles().keys.toList()
                UnitType.Ranged -> attackableTiles = unit.getTile().getTilesInDistance(2)
            }

            for (tile in attackableTiles.filter { it.unit!=null && it.unit!!.owner != unit.owner && civViewableTiles.contains(it)})
                tileGroups[tile]!!.showCircle(Color(237/255f,41/255f,57/255f,1f))
        }
    }

    fun setCenterPosition(vector: Vector2) {
        val tileGroup = tileGroups.values.first { it.tileInfo.position == vector }
        selectedTile = tileGroup.tileInfo
        worldScreen.unitTable.tileSelected(selectedTile!!)
        layout() // Fit the scroll pane to the contents - otherwise, setScroll won't work!
        // We want to center on the middle of TG (TG.getX()+TG.getWidth()/2)
        // and so the scroll position (== filter the screen starts) needs to be half a screen away
        scrollX = tileGroup.x + tileGroup.width / 2 - worldScreen.stage.width / 2
        // Here it's the same, only the Y axis is inverted - when at 0 we're at the top, not bottom - so we invert it back.
        scrollY = maxY - (tileGroup.y + tileGroup.width / 2 - worldScreen.stage.height / 2)
        updateVisualScroll()
        worldScreen.update()
    }


}
