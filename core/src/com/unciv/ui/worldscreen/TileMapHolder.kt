package com.unciv.ui.worldscreen

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane
import com.badlogic.gdx.scenes.scene2d.utils.ActorGestureListener
import com.unciv.logic.civilization.CivilizationInfo
import com.unciv.logic.map.TileInfo
import com.unciv.logic.map.TileMap
import com.unciv.models.linq.Linq
import com.unciv.models.linq.LinqHashMap
import com.unciv.ui.cityscreen.addClickListener
import com.unciv.ui.tilegroups.WorldTileGroup
import com.unciv.ui.utils.HexMath
import java.util.*

class TileMapHolder(internal val worldScreen: WorldScreen, internal val tileMap: TileMap, internal val civInfo: CivilizationInfo) : ScrollPane(null) {
    internal var selectedTile: TileInfo? = null
    internal var unitTile: TileInfo? = null
    val tileGroups = LinqHashMap<String, WorldTileGroup>()

    internal fun addTiles() {
        val allTiles = Group()

        var topX = 0f
        var topY = 0f
        var bottomX = 0f
        var bottomY = 0f

        for (tileInfo in tileMap.values) {
            val group = WorldTileGroup(tileInfo)

            group.addClickListener {
                val tutorial = Linq<String>()
                tutorial.add("Clicking on a tile selects that tile," +
                        "\r\n and displays information on that tile on the bottom-right," +
                        "\r\n as well as unit actions, if the tile contains a unit")
                worldScreen.displayTutorials("TileClicked", tutorial)

                selectedTile = tileInfo
                if (unitTile != null && group.tileInfo.unit == null) {
                    val distanceToTiles = tileMap.getDistanceToTilesWithinTurn(unitTile!!.position, unitTile!!.unit!!.currentMovement, civInfo.tech.isResearched("Machinery"))
                    if (distanceToTiles.containsKey(selectedTile)) {
                        unitTile!!.moveUnitToTile(group.tileInfo, distanceToTiles[selectedTile]!!)
                    } else {
                        unitTile!!.unit!!.action = "moveTo " + selectedTile!!.position.x.toInt() + "," + selectedTile!!.position.y.toInt()
                        unitTile!!.unit!!.doPreTurnAction(unitTile!!)
                    }

                    unitTile = null
                    selectedTile = group.tileInfo
                }

                worldScreen.update()
            }



            val positionalVector = HexMath.Hex2WorldCoords(tileInfo.position)
            val groupSize = 50
            group.setPosition(worldScreen.stage.width / 2 + positionalVector.x * 0.8f * groupSize.toFloat(),
                    worldScreen.stage.height / 2 + positionalVector.y * 0.8f * groupSize.toFloat())
            tileGroups[tileInfo.position.toString()] = group
            allTiles.addActor(group)
            topX = Math.max(topX, group.x + groupSize)
            topY = Math.max(topY, group.y + groupSize)
            bottomX = Math.min(bottomX, group.x)
            bottomY = Math.min(bottomY, group.y)
        }

        for (group in tileGroups.linqValues()) {
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
                val scale = Math.sqrt((distance / initialDistance).toDouble()).toFloat() * lastScale
                if (scale < 1) return
                setScale(scale)
            }

        })
    }

    internal fun updateTiles() {
        for (WG in tileGroups.linqValues()) WG.update(worldScreen)

        if (unitTile != null)
            return  // While we're in "unit move" mode, no tiles but the tiles the unit can move to will be "visible"

        // YES A TRIPLE FOR, GOT PROBLEMS WITH THAT?
        // Seriously though, there is probably a more efficient way of doing this, probably?
        // The original implementation caused serious lag on android, so efficiency is key, here
        for (WG in tileGroups.linqValues()) WG.setIsViewable(false)
        val veiwableVectorStrings = HashSet<String>()

        // tiles adjacent to city tiles
        for (tileInfo in tileMap.values)
            if (civInfo.civName == tileInfo.owner)
                for (adjacentLocation in HexMath.GetAdjacentVectors(tileInfo.position))
                    veiwableVectorStrings.add(adjacentLocation.toString())

        // Tiles within 2 tiles of units
        for (tile in tileMap.values
                .where { arg0 -> arg0.unit != null })
            for (tileInfo in tileMap.getViewableTiles(tile.position, 2))
                veiwableVectorStrings.add(tileInfo.position.toString())

        for (string in veiwableVectorStrings)
            if (tileGroups.containsKey(string))
                tileGroups[string]!!.setIsViewable(true)
    }


    fun setCenterPosition(vector: Vector2) {
        val tileGroup = tileGroups.linqValues().first { it.tileInfo.position == vector }
        layout() // Fit the scroll pane to the contents - otherwise, setScroll won't work!
        // We want to center on the middle of TG (TG.getX()+TG.getWidth()/2)
        // and so the scroll position (== where the screen starts) needs to be half a screen away
        scrollX = tileGroup!!.x + tileGroup.width / 2 - worldScreen.stage.width / 2
        // Here it's the same, only the Y axis is inverted - when at 0 we're at the top, not bottom - so we invert it back.
        scrollY = maxY - (tileGroup.y + tileGroup.width / 2 - worldScreen.stage.height / 2)
        updateVisualScroll()
    }


}
