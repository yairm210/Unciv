package com.unciv.logic

import com.badlogic.gdx.math.Vector2
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class HexMath {

    fun getVectorForAngle(angle: Float): Vector2 {
        return Vector2(Math.sin(angle.toDouble()).toFloat(), Math.cos(angle.toDouble()).toFloat())
    }

    private fun getVectorByClockHour(hour: Int): Vector2 {
        return getVectorForAngle((2 * Math.PI * (hour / 12f)).toFloat())
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

    fun getAdjacentVectors(origin: Vector2): ArrayList<Vector2> {
        val vectors = ArrayList<Vector2>()
        vectors += Vector2(1f, 0f)
        vectors += Vector2(1f, 1f)
        vectors += Vector2(0f, 1f)
        vectors += Vector2(-1f, 0f)
        vectors += Vector2(-1f, -1f)
        vectors += Vector2(0f, -1f)
        for (vector in vectors) vector.add(origin)
        return vectors
    }

    fun getVectorsAtDistance(origin: Vector2, distance: Int): List<Vector2> {
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
            vectors += origin.cpy().scl(2f).sub(current) // Get vector on other side of clock
            current.add(1f, 1f)
        }
        for (i in 0 until distance) { // 10 to 12
            vectors += current.cpy()
            vectors += origin.cpy().scl(2f).sub(current) // Get vector on other side of clock
            current.add(0f, 1f)
        }
        return vectors
    }

    fun getVectorsInDistance(origin: Vector2, distance: Int): List<Vector2> {
        val hexesToReturn = mutableListOf<Vector2>()
        for (i in 0 .. distance) {
            hexesToReturn += getVectorsAtDistance(origin, i)
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
