package com.unciv.logic.civilization

import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.unciv.logic.IsPartOfGameInfoSerialization
import com.unciv.logic.map.HexMath.getLatitude
import com.unciv.logic.map.HexMath.getLongitude
import com.unciv.logic.map.HexMath.worldFromLatLong
import com.unciv.logic.map.MapParameters
import com.unciv.logic.map.MapShape
import com.unciv.ui.components.tilegroups.TileGroupMap
import yairm210.purity.annotations.Readonly
import kotlin.math.abs
import kotlin.math.sqrt

class ExploredRegion : IsPartOfGameInfoSerialization {

    @Transient
    private var worldWrap = false

    @Transient
    private var evenMapWidth = false

    @Transient
    private var rectangularMap = false

    @Transient
    private var mapRadius = 0f

    @Transient
    private val tileRadius = TileGroupMap.groupSize * 0.8f

    @Transient
    private var shouldRecalculateCoords = true

    @Transient
    private var shouldUpdateMinimap = true

    // Rectangle for positioning the camera viewport on the minimap
    @Transient
    private val exploredRectangle = Rectangle()

    @Transient
    private var shouldRestrictX = false

    // Top left point of the explored region in stage (x;y) starting from the top left corner
    @Transient
    private var topLeftStage = Vector2()

    // Bottom right point of the explored region in stage (x;y) starting from the top left corner
    @Transient
    private var bottomRightStage = Vector2()

    // Top left point of the explored region in hex (long;lat) from the center of the map
    private var topLeft = Vector2()

    // Bottom right point of the explored region in hex (long;lat) from the center of the map
    private var bottomRight = Vector2()

    // Getters
    @Readonly fun shouldRecalculateCoords(): Boolean = shouldRecalculateCoords
    @Readonly fun shouldUpdateMinimap(): Boolean = shouldUpdateMinimap
    @Readonly fun getRectangle(): Rectangle = exploredRectangle
    @Readonly fun shouldRestrictX(): Boolean = shouldRestrictX
    @Readonly fun getLeftX(): Float = topLeftStage.x
    @Readonly fun getRightX(): Float = bottomRightStage.x
    @Readonly fun getTopY(): Float = topLeftStage.y
    @Readonly fun getBottomY(): Float = bottomRightStage.y

    fun clone(): ExploredRegion {
        val toReturn = ExploredRegion()
        toReturn.topLeft = topLeft
        toReturn.bottomRight = bottomRight
        return toReturn
    }

    fun setMapParameters(mapParameters: MapParameters) {
        this.worldWrap = mapParameters.worldWrap
        evenMapWidth = worldWrap

        if (mapParameters.shape == MapShape.rectangular) {
            mapRadius = (mapParameters.mapSize.width / 2).toFloat()
            evenMapWidth = mapParameters.mapSize.width % 2 == 0 || evenMapWidth
            rectangularMap = true
        }
        else
            mapRadius = mapParameters.mapSize.radius.toFloat()
    }

    // Check if tilePosition is beyond explored region
    fun checkTilePosition(tilePosition: Vector2, explorerPosition: Vector2?) {
        var mapExplored = false
        var longitude = getLongitude(tilePosition)
        val latitude = getLatitude(tilePosition)

        // First time call
        if (topLeft == Vector2.Zero && bottomRight == Vector2.Zero) {
            topLeft = Vector2(longitude, latitude)
            bottomRight = Vector2(longitude, latitude)
            return
        }

        // Check X coord
        if (topLeft.x >= bottomRight.x) {
            if (longitude > topLeft.x) {
                // For world wrap maps when the maximumX is reached, we move to a minimumX - 1f
                if (worldWrap && longitude == mapRadius) longitude = mapRadius * -1f
                topLeft.x = longitude
                mapExplored = true
            } else if (longitude < bottomRight.x) {
                // For world wrap maps when the minimumX is reached, we move to a maximumX + 1f
                if (worldWrap && longitude == (mapRadius * -1f + 1f)) longitude = mapRadius + 1f
                bottomRight.x = longitude
                mapExplored = true
            }
        } else {
            // When we cross the map edge with world wrap, the vectors are swapped along the x-axis
            if (longitude < bottomRight.x && longitude > topLeft.x) {
                val rightSideDistance: Float
                val leftSideDistance: Float

                // If we have explorerPosition, get distance to explorer
                // This solves situations when a newly explored cell is in the middle of an unexplored area
                if(explorerPosition != null) {
                    val explorerLongitude = getLongitude(explorerPosition)

                    rightSideDistance = if(explorerLongitude < 0 && bottomRight.x > 0)
                            // The explorer is still on the right edge of the map, but has explored over the edge
                            mapRadius * 2f + explorerLongitude - bottomRight.x
                        else
                            abs(explorerLongitude - bottomRight.x)

                    leftSideDistance = if(explorerLongitude > 0 && topLeft.x < 0)
                            // The explorer is still on the left edge of the map, but has explored over the edge
                            mapRadius * 2f - explorerLongitude + topLeft.x
                        else
                            abs(topLeft.x - explorerLongitude)
                } else {
                    // If we don't have explorerPosition, we calculate the distance to the edges of the explored region
                    // e.g. when capitals are revealed
                    rightSideDistance = bottomRight.x - longitude
                    leftSideDistance = longitude - topLeft.x
                }

                // Expand region from the nearest edge
                if (rightSideDistance > leftSideDistance)
                    topLeft.x = longitude
                else
                    bottomRight.x = longitude

                mapExplored = true
            }
        }

        // Check Y coord
        if (latitude > topLeft.y) {
            topLeft.y = latitude
            mapExplored = true
        } else if (latitude < bottomRight.y) {
            bottomRight.y = latitude
            mapExplored = true
        }

        if(mapExplored) {
            shouldRecalculateCoords = true
            shouldUpdateMinimap = true
        }
    }

    fun calculateStageCoords(mapMaxX: Float, mapMaxY: Float) {
        shouldRecalculateCoords = false

        // Check if we explored the whole world wrap map horizontally
        shouldRestrictX = bottomRight.x - topLeft.x != 1f

        // Get world (x;y)
        val topLeftWorld = worldFromLatLong(topLeft, tileRadius)
        val bottomRightWorld = worldFromLatLong(bottomRight, tileRadius)

        // Convert X to the stage coords
        val mapCenterX = if (evenMapWidth) (mapMaxX + TileGroupMap.groupSize + 4f) * 0.5f else mapMaxX * 0.5f
        var left = mapCenterX + topLeftWorld.x
        var right = mapCenterX + bottomRightWorld.x

        // World wrap over edge check
        if (left > mapMaxX) left = 10f
        if (right < 0f) right = mapMaxX - 10f

        // Convert Y to the stage coords
        val mapCenterY = if (rectangularMap) mapMaxY * 0.5f + TileGroupMap.groupSize * 0.25f else mapMaxY * 0.5f
        val top = mapCenterY-topLeftWorld.y
        val bottom = mapCenterY-bottomRightWorld.y

        topLeftStage = Vector2(left, top)
        bottomRightStage = Vector2(right, bottom)

        // Calculate rectangle for positioning the camera viewport on the minimap
        val yOffset = tileRadius * sqrt(3f) * 0.5f
        exploredRectangle.x = left - tileRadius
        exploredRectangle.y = mapMaxY - bottom - yOffset * 0.5f
        exploredRectangle.width = getWidth() * tileRadius * 1.5f
        exploredRectangle.height = getHeight() * yOffset
    }

    @Readonly
    fun isPositionInRegion(postition: Vector2): Boolean {
        val long = getLongitude(postition)
        val lat = getLatitude(postition)
        return if (topLeft.x > bottomRight.x)
                (long <= topLeft.x && long >= bottomRight.x && lat <= topLeft.y && lat >= bottomRight.y)
            else
                (((long >= topLeft.x && long >= bottomRight.x) || (long <= topLeft.x && long <= bottomRight.x)) && lat <= topLeft.y && lat >= bottomRight.y)
    }

    @Readonly
    fun getWidth(): Int {
        val result: Float
        if (topLeft.x > bottomRight.x) result = topLeft.x - bottomRight.x
        else result = mapRadius * 2f - (bottomRight.x - topLeft.x)
        return result.toInt() + 1
    }

    @Readonly fun getHeight(): Int = (topLeft.y - bottomRight.y).toInt() + 1

    fun getMinimapLeft(tileSize: Float): Float {
        shouldUpdateMinimap = false
        return (topLeft.x + 1f) * tileSize * -0.75f
    }
}
