package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

@Suppress("MemberVisibilityCanBePrivate", "unused")  // this is a library offering optional services
object HexMath {

    fun getVectorForAngle(angle: Float): Vector2 {
        return Vector2(sin(angle.toDouble()).toFloat(), cos(angle.toDouble()).toFloat())
    }

    private fun getVectorByClockHour(hour: Int): Vector2 {
        return getVectorForAngle((2 * Math.PI * (hour / 12f)).toFloat())
    }

    /** returns the number of tiles in a hexagonal map of radius size*/
    fun getNumberOfTilesInHexagon(size: Int): Int {
        if (size < 0) return 0
        return 1 + 6 * size * (size + 1) / 2
    }

    /** Almost inverse of [getNumberOfTilesInHexagon] - get equivalent fractional Hexagon radius for an Area */
    fun getHexagonalRadiusForArea(numberOfTiles: Int) =
        if (numberOfTiles < 1) 0f else ((sqrt(12f * numberOfTiles - 3) - 3) / 6)

    // In our reference system latitude, i.e. how distant from equator we are, is proportional to x + y
    fun getLatitude(vector: Vector2): Float {
        return vector.x + vector.y
    }

    fun getLongitude(vector: Vector2): Float {
        return vector.x - vector.y
    }

    /**
     * Convert a latitude and longitude back into a hex coordinate.
     * Inverse function of [getLatitude] and [getLongitude].
     *
     * @param latitude As from [getLatitude].
     * @param longitude As from [getLongitude].
     * @return Hex coordinate. May need to be passed through [roundHexCoords] for further use.
     * */
    fun hexFromLatLong(latitude: Float, longitude: Float): Vector2 {
        val y = (latitude - longitude) / 2f
        val x = longitude + y
        return Vector2(x, y)
    }

    /**
     * Convert hex latitude and longitude into world coordinates.
     */
    fun worldFromLatLong(vector: Vector2, tileRadius: Float): Vector2 {
        val x = vector.x * tileRadius * 1.5f * -1f
        val y = vector.y * tileRadius * sqrt(3f) * 0.5f
        return Vector2(x, y)
    }

    /** returns a vector containing width and height a rectangular map should have to have
     *  approximately the same number of tiles as an hexagonal map given a height/width ratio */
    fun getEquivalentRectangularSize(size: Int, ratio: Float = 0.65f): Vector2 {
        if (size < 0)
            return Vector2.Zero

        val nTiles = getNumberOfTilesInHexagon(size)
        val width = round(sqrt(nTiles.toFloat() / ratio))
        val height = round(width * ratio)
        return Vector2(width, height)
    }

    /** Returns a radius of a hexagonal map that has approximately the same number of
     *  tiles as a rectangular map of a given width/height */
    fun getEquivalentHexagonalRadius(width: Int, height: Int) =
        getHexagonalRadiusForArea(width * height).roundToInt()

    fun getAdjacentVectors(origin: Vector2): ArrayList<Vector2> {
        val vectors = arrayListOf(
                Vector2(1f, 0f),
                Vector2(1f, 1f),
                Vector2(0f, 1f),
                Vector2(-1f, 0f),
                Vector2(-1f, -1f),
                Vector2(0f, -1f)
        )
        for (vector in vectors) vector.add(origin)
        return vectors
    }

    // HexCoordinates are a (x,y) vector, where x is the vector getting us to the top-left hex (e.g. 10 o'clock)
    // and y is the vector getting us to the top-right hex (e.g. 2 o'clock)

    // Each (1,1) vector effectively brings us up a layer.
    // For example, to get to the cell above me, I'll use a (1,1) vector.
    // To get to the cell below the cell to my bottom-right, I'll use a (-1,-2) vector.

    /**
     * @param unwrapHexCoord Hex coordinate to unwrap.
     * @param staticHexCoord Reference hex coordinate.
     * @param longitudinalRadius Maximum longitudinal absolute value of world tiles, such as from [TileMap.maxLongitude]. The total width is assumed one less than twice this.
     *
     * @return The closest hex coordinate to [staticHexCoord] that is equivalent to [unwrapHexCoord]. THIS MAY NOT BE A VALID TILE COORDINATE. It may also require rounding for further use.
     *
     * @see [com.unciv.logic.map.TileMap.getUnWrappedPosition]
     */
    fun getUnwrappedNearestTo(unwrapHexCoord: Vector2, staticHexCoord: Vector2, longitudinalRadius: Number): Vector2 {
        val referenceLong = getLongitude(staticHexCoord)
        val toWrapLat = getLatitude(unwrapHexCoord) // Working in Cartesian space is easier.
        val toWrapLong = getLongitude(unwrapHexCoord)
        val longRadius = longitudinalRadius.toFloat()
        return hexFromLatLong(toWrapLat, (toWrapLong - referenceLong + longRadius).mod(longRadius * 2f) - longRadius + referenceLong)
    }

    fun hex2WorldCoords(hexCoord: Vector2): Vector2 {
        // Distance between cells = 2* normal of triangle = 2* (sqrt(3)/2) = sqrt(3)
        val xVector = getVectorByClockHour(10).scl(sqrt(3.0).toFloat())
        val yVector = getVectorByClockHour(2).scl(sqrt(3.0).toFloat())
        return xVector.scl(hexCoord.x).add(yVector.scl(hexCoord.y))
    }

    @Suppress("LocalVariableName")  // clearer
    fun world2HexCoords(worldCoord: Vector2): Vector2 {
        // D: diagonal, A: antidiagonal versors
        val D = getVectorByClockHour(10).scl(sqrt(3.0).toFloat())
        val A = getVectorByClockHour(2).scl(sqrt(3.0).toFloat())
        val den = D.x * A.y - D.y * A.x
        val x = (worldCoord.x * A.y - worldCoord.y * A.x) / den
        val y = (worldCoord.y * D.x - worldCoord.x * D.y) / den
        return Vector2(x, y)
    }

    // Both x - 10 o'clock - and y - 2 o'clock - increase the row by 0.5
    fun getRow(hexCoord: Vector2): Int = (hexCoord.x/2 + hexCoord.y/2).toInt()

    // y is 2 o'clock - increases column by 1, x in 10 o'clock - decreases by 1
    fun getColumn(hexCoord: Vector2): Int = (hexCoord.y - hexCoord.x).toInt()

    fun getTileCoordsFromColumnRow(column: Int, row: Int): Vector2 {
        // we know that column = y-x in hex coords
        // And we know that row = (y+x)/2 in hex coords
        // Therefore, 2row+column = 2y, 2row-column=2x

        // However, these row numbers only apear on alternating columns.
        // So column 0 will have rows 0,1,2, etc, and column 1 will have rows 0.5,1.5,2.5 etc.
        // you'll need to see a hexmap to see it, and then it will be obvious

        // So for even columns, the row is incremented by half
        var twoRows = row * 2
        if (abs(column) %2==1) twoRows += 1

        return Vector2(((twoRows-column)/2).toFloat(), ((twoRows+column)/2).toFloat())
    }

    /** Todo: find a mathematically equivalent way to round hex coords without cubic magic */
    fun roundHexCoords(hexCoord: Vector2): Vector2 {

        fun roundCubicCoords(cubicCoords: Vector3): Vector3 {
            var rx = round(cubicCoords.x)
            var ry = round(cubicCoords.y)
            var rz = round(cubicCoords.z)

            val deltaX = abs(rx - cubicCoords.x)
            val deltaY = abs(ry - cubicCoords.y)
            val deltaZ = abs(rz - cubicCoords.z)

            if (deltaX > deltaY && deltaX > deltaZ)
                rx = -ry - rz
            else if (deltaY > deltaZ)
                ry = -rx - rz
            else
                rz = -rx - ry

            return Vector3(rx, ry, rz)
        }

        fun hex2CubicCoords(hexCoord: Vector2): Vector3 {
            return Vector3(hexCoord.y - hexCoord.x, hexCoord.x, -hexCoord.y)
        }

        fun cubic2HexCoords(cubicCoord: Vector3): Vector2 {
            return Vector2(cubicCoord.y, -cubicCoord.z)
        }

        return cubic2HexCoords(roundCubicCoords(hex2CubicCoords(hexCoord)))
    }

    fun getVectorsAtDistance(origin: Vector2, distance: Int, maxDistance: Int, worldWrap: Boolean): List<Vector2> {
        val vectors = mutableListOf<Vector2>()
        if (distance == 0) {
            vectors += origin.cpy()
            return vectors
        }
        val current = origin.cpy().sub(distance.toFloat(), distance.toFloat()) // start at 6 o clock
        for (i in 0 until distance) { // From 6 to 8
            vectors += current.cpy()
            vectors += origin.cpy().scl(2f).sub(current) // Get vector on other side of clock
            current.add(1f, 0f)
        }
        for (i in 0 until distance) { // 8 to 10
            vectors += current.cpy()
            if (!worldWrap || distance != maxDistance)
                vectors += origin.cpy().scl(2f).sub(current) // Get vector on other side of clock
            current.add(1f, 1f)
        }
        for (i in 0 until distance) { // 10 to 12
            vectors += current.cpy()
            if (!worldWrap || distance != maxDistance || i != 0)
                vectors += origin.cpy().scl(2f).sub(current) // Get vector on other side of clock
            current.add(0f, 1f)
        }
        return vectors
    }

    fun getVectorsInDistance(origin: Vector2, distance: Int, worldWrap: Boolean): List<Vector2> {
        val hexesToReturn = mutableListOf<Vector2>()
        for (i in 0..distance) {
            hexesToReturn += getVectorsAtDistance(origin, i, distance, worldWrap)
        }
        return hexesToReturn
    }

    /** Get number of hexes from [origin] to [destination] _without respecting world-wrap_ */
    fun getDistance(origin: Vector2, destination: Vector2): Int {
        val relativeX = origin.x - destination.x
        val relativeY = origin.y - destination.y
        return if (relativeX * relativeY >= 0)
            max(abs(relativeX), abs(relativeY)).toInt()
        else
            (abs(relativeX) + abs(relativeY)).toInt()
    }

    private val clockPositionToHexVectorMap: Map<Int, Vector2> = mapOf(
        0 to Vector2(1f, 1f), // This alias of 12 makes clock modulo logic easier
        12 to Vector2(1f, 1f),
        2 to Vector2(0f, 1f),
        4 to Vector2(-1f, 0f),
        6 to Vector2(-1f, -1f),
        8 to Vector2(0f, -1f),
        10 to Vector2(1f, 0f)
    )

    /** Returns the hex-space distance corresponding to [clockPosition], or a zero vector if [clockPosition] is invalid */
    fun getClockPositionToHexVector(clockPosition: Int): Vector2 {
        return clockPositionToHexVectorMap[clockPosition]?: Vector2.Zero
    }

    // Statically allocate the Vectors (in World coordinates)
    // of the 6 clock directions for border and road drawing in TileGroup
    private val clockPositionToWorldVectorMap: Map<Int,Vector2> = mapOf(
        2 to hex2WorldCoords(Vector2(0f, -1f)),
        4 to hex2WorldCoords(Vector2(1f, 0f)),
        6 to hex2WorldCoords(Vector2(1f, 1f)),
        8 to hex2WorldCoords(Vector2(0f, 1f)),
        10 to hex2WorldCoords(Vector2(-1f, 0f)),
        12 to hex2WorldCoords(Vector2(-1f, -1f)) )

    /** Returns the world/screen-space distance corresponding to [clockPosition], or a zero vector if [clockPosition] is invalid */
    fun getClockPositionToWorldVector(clockPosition: Int): Vector2 =
        clockPositionToWorldVectorMap[clockPosition] ?: Vector2.Zero

    fun getDistanceFromEdge(vector: Vector2, mapParameters: MapParameters): Int {
        val x = vector.x.toInt()
        val y = vector.y.toInt()
        if (mapParameters.shape == MapShape.rectangular) {
            val height = mapParameters.mapSize.height
            val width = mapParameters.mapSize.width
            val left = if (mapParameters.worldWrap) Int.MAX_VALUE else width / 2 - (x - y)
            val right = if (mapParameters.worldWrap) Int.MAX_VALUE else (width - 1) / 2 - (y - x)
            val top = height / 2 -  (x + y) / 2
            // kotlin's Int division rounds in different directions depending on sign! Thus 1 extra `-1`
            val bottom = (x + y - 1) / 2 + (height - 1) / 2
            return minOf(left, right, top, bottom)
        } else {
            val radius = mapParameters.mapSize.radius
            if (!mapParameters.worldWrap) return radius - getDistance(vector, Vector2.Zero)

            // The non-wrapping method holds in the upper two and lower two 'triangles' of the hexagon
            // but needs special casing for left and right 'wedges', where only distance from the
            // 'choke points' counts (upper and lower hex at the 'seam' where height is smallest).
            // These are at (radius,0) and (0,-radius)
            if (x.sign == y.sign) return radius - getDistance(vector, Vector2.Zero)
            // left wedge - the 'choke points' are not wrapped relative to us
            if (x > 0) return min(getDistance(vector, Vector2(radius.toFloat(),0f)), getDistance(vector, Vector2(0f, -radius.toFloat())))
            // right wedge - compensate wrap by using a hex 1 off along the edge - same result
            return min(getDistance(vector, Vector2(1f, radius.toFloat())), getDistance(vector, Vector2(-radius.toFloat(), -1f)))
        }
    }
}
