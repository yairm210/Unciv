package com.unciv.logic

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import kotlin.math.*

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

    /* In our reference system latitude, i.e. how distant from equator we are is proportional to x + y*/
    fun getLatitude(vector: Vector2): Float {
        return vector.x + vector.y
    }
    fun getLongitude(vector: Vector2): Float {
        return vector.x - vector.y
    }

    /** returns a vector containing width and height a rectangular map should have to have
     * approximately the same number of tiles as an hexagonal map given a height/width ratio */
    fun getEquivalentRectangularSize(size: Int, ratio: Float = 0.65f): Vector2 {
        if (size < 0)
            return Vector2.Zero

        val nTiles = getNumberOfTilesInHexagon(size)
        val width = round(sqrt(nTiles.toFloat()/ratio))
        val height = round(width * ratio)
        return Vector2(width, height)
    }

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

    fun hex2WorldCoords(hexCoord: Vector2): Vector2 {
        // Distance between cells = 2* normal of triangle = 2* (sqrt(3)/2) = sqrt(3)
        val xVector = getVectorByClockHour(10).scl(sqrt(3.0).toFloat())
        val yVector = getVectorByClockHour(2).scl(sqrt(3.0).toFloat())
        return xVector.scl(hexCoord.x).add(yVector.scl(hexCoord.y))
    }

    fun world2HexCoords(worldCoord: Vector2): Vector2 {
        // D: diagonal, A: antidiagonal versors
        val D = getVectorByClockHour(10).scl(sqrt(3.0).toFloat())
        val A = getVectorByClockHour(2).scl(sqrt(3.0).toFloat())
        val den = D.x * A.y - D.y * A.x
        val x = (worldCoord.x * A.y - worldCoord.y * A.x) / den
        val y = (worldCoord.y * D.x - worldCoord.x * D.y) / den
        return Vector2(x, y)
    }

    fun hex2CubicCoords(hexCoord: Vector2): Vector3 {
        return Vector3(hexCoord.y - hexCoord.x, hexCoord.x, -hexCoord.y)
    }

    fun cubic2HexCoords(cubicCoord: Vector3): Vector2 {
        return Vector2(cubicCoord.y, -cubicCoord.z)
    }

    fun cubic2EvenQCoords(cubicCoord: Vector3): Vector2 {
        return Vector2(cubicCoord.x, cubicCoord.z + (cubicCoord.x + (cubicCoord.x.toInt() and 1)) / 2)
    }
    fun evenQ2CubicCoords(evenQCoord: Vector2): Vector3 {
        val x = evenQCoord.x
        val z = evenQCoord.y - (evenQCoord.x + (evenQCoord.x.toInt() and 1)) / 2
        val y = -x-z
        return Vector3(x,y,z)
    }

    fun evenQ2HexCoords(evenQCoord: Vector2): Vector2 {
        return cubic2HexCoords(evenQ2CubicCoords(evenQCoord))
    }

    fun roundCubicCoords(cubicCoords: Vector3): Vector3 {
        var rx = round(cubicCoords.x)
        var ry = round(cubicCoords.y)
        var rz = round(cubicCoords.z)

        val deltaX = abs(rx - cubicCoords.x)
        val deltaY = abs(ry - cubicCoords.y)
        val deltaZ = abs(rz - cubicCoords.z)

        if (deltaX > deltaY && deltaX > deltaZ)
            rx = -ry-rz
        else if (deltaY > deltaZ)
            ry = -rx-rz
        else
            rz = -rx-ry

        return Vector3(rx, ry, rz)
    }

    fun roundHexCoords(hexCoord: Vector2): Vector2 {
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
            vectors += origin.cpy().scl(2f).sub(current) // Get vector on other side of cloick
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
            if (!worldWrap || distance != maxDistance ||  i != 0)
                vectors += origin.cpy().scl(2f).sub(current) // Get vector on other side of clock
            current.add(0f, 1f)
        }
        return vectors
    }

    fun getVectorsInDistance(origin: Vector2, distance: Int, worldWrap: Boolean): List<Vector2> {
        val hexesToReturn = mutableListOf<Vector2>()
        for (i in 0 .. distance) {
            hexesToReturn += getVectorsAtDistance(origin, i, distance, worldWrap)
        }
        return hexesToReturn
    }

    fun getDistance(origin: Vector2, destination: Vector2): Int {
        val relative_x = origin.x-destination.x
        val relative_y = origin.y-destination.y
        if (relative_x * relative_y >= 0)
            return max(abs(relative_x),abs(relative_y)).toInt()
        else
            return (abs(relative_x) + abs(relative_y)).toInt()
    }
}
