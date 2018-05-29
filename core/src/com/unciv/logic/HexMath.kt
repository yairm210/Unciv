package com.unciv.logic

import com.badlogic.gdx.math.Vector2
import java.util.*

class HexMath {

    fun GetVectorForAngle(angle: Float): Vector2 {
        return Vector2(Math.sin(angle.toDouble()).toFloat(), Math.cos(angle.toDouble()).toFloat())
    }

    fun GetVectorByClockHour(hour: Int): Vector2 {
        return GetVectorForAngle((2 * Math.PI * (hour / 12f)).toFloat())
    }

    // HexCoordinates are a (x,y) vector, where x is the vector getting us to the top-left hex (e.g. 10 o'clock)
    // and y is the vector getting us to the top-right hex (e.g. 2 o'clock)

    // Each (1,1) vector effectively brings us up a layer.
    // For example, to get to the cell above me, I'll use a (1,1) vector.
    // To get to the cell below the cell to my bottom-right, I'll use a (-1,-2) vector.

    fun Hex2WorldCoords(hexCoord: Vector2): Vector2 {
        // Distance between cells = 2* normal of triangle = 2* (sqrt(3)/2) = sqrt(3)
        val xVector = GetVectorByClockHour(10).scl(Math.sqrt(3.0).toFloat())
        val yVector = GetVectorByClockHour(2).scl(Math.sqrt(3.0).toFloat())
        return xVector.scl(hexCoord.x).add(yVector.scl(hexCoord.y))
    }

    fun GetAdjacentVectors(origin: Vector2): ArrayList<Vector2> {
        val vectors = ArrayList<Vector2>()
        vectors.add(Vector2(1f, 0f))
        vectors.add(Vector2(1f, 1f))
        vectors.add(Vector2(0f, 1f))
        vectors.add(Vector2(-1f, 0f))
        vectors.add(Vector2(-1f, -1f))
        vectors.add(Vector2(0f, -1f))
        for (vector in vectors) vector.add(origin)
        return vectors
    }

    fun GetVectorsAtDistance(origin: Vector2, distance: Int): List<Vector2> {
        val vectors = mutableListOf<Vector2>()
        if (distance == 0) {
            vectors.add(origin.cpy())
            return vectors
        }
        val Current = origin.cpy().sub(distance.toFloat(), distance.toFloat()) // start at 6 o clock
        for (i in 0 until distance) { // From 6 to 8
            vectors.add(Current.cpy())
            vectors.add(origin.cpy().scl(2f).sub(Current)) // Get vector on other side of cloick
            Current.add(1f, 0f)
        }
        for (i in 0 until distance) { // 8 to 10
            vectors.add(Current.cpy())
            vectors.add(origin.cpy().scl(2f).sub(Current)) // Get vector on other side of cloick
            Current.add(1f, 1f)
        }
        for (i in 0 until distance) { // 10 to 12
            vectors.add(Current.cpy())
            vectors.add(origin.cpy().scl(2f).sub(Current)) // Get vector on other side of cloick
            Current.add(0f, 1f)
        }
        return vectors
    }

    fun GetVectorsInDistance(origin: Vector2, distance: Int): List<Vector2> {
        val hexesToReturn = mutableListOf<Vector2>()
        for (i in 0 until distance + 1) {
            hexesToReturn.addAll(GetVectorsAtDistance(origin, i))
        }
        return hexesToReturn
    }

    fun GetDistance(origin: Vector2, destination: Vector2): Int { // Yes, this is a dumb implementation. But I can't be arsed to think of a better one right now, other stuff to do.
        var distance = 0

        while (true) {
            if (GetVectorsAtDistance(origin, distance).contains(destination)) return distance
            distance++
        }
    }

}
