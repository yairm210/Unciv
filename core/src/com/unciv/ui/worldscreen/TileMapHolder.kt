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

class TileMapHolder(internal val worldScreen: WorldScreen, internal val tileMap: TileMap, internal val civInfo: CivilizationInfo) : ScrollPane(null) {
    internal var selectedTile: TileInfo? = null
    //internal var unitTile: TileInfo? = null
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
                worldScreen.unitTable.tileSelected(tileInfo)
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


        for (WG in tileGroups.linqValues()) WG.setIsViewable(false)
        var viewablePositions = emptyList<Vector2>()
        if(worldScreen.unitTable.currentlyExecutingAction == null) {
            viewablePositions += tileMap.values.where { it.owner == civInfo.civName }
                    .flatMap { HexMath.GetAdjacentVectors(it.position) } // tiles adjacent to city tiles
            viewablePositions += tileMap.values.where { it.unit != null }
                    .flatMap { tileMap.getViewableTiles(it.position, 2).select { it.position } } // Tiles within 2 tiles of units
        }

        else
            viewablePositions += worldScreen.unitTable.getViewablePositionsForExecutingAction()

        for (string in viewablePositions.map { it.toString() }.filter { tileGroups.containsKey(it) })
            tileGroups[string]!!.setIsViewable(true)

    }

    fun setCenterPosition(vector: Vector2) {
        val tileGroup = tileGroups.linqValues().first { it.tileInfo.position == vector }
        selectedTile = tileGroup.tileInfo
        if(selectedTile!!.unit!=null) worldScreen.unitTable.selectedUnitTile = selectedTile
        layout() // Fit the scroll pane to the contents - otherwise, setScroll won't work!
        // We want to center on the middle of TG (TG.getX()+TG.getWidth()/2)
        // and so the scroll position (== where the screen starts) needs to be half a screen away
        scrollX = tileGroup!!.x + tileGroup.width / 2 - worldScreen.stage.width / 2
        // Here it's the same, only the Y axis is inverted - when at 0 we're at the top, not bottom - so we invert it back.
        scrollY = maxY - (tileGroup.y + tileGroup.width / 2 - worldScreen.stage.height / 2)
        updateVisualScroll()
        worldScreen.update()
    }


}
