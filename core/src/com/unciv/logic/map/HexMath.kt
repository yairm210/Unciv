package com.unciv.logic.map

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.utils.Json
import com.badlogic.gdx.utils.JsonValue
import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.Immutable
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly
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

    @Pure
    fun getVectorForAngle(angle: Float): Vector2 {
        return Vector2(sin(angle.toDouble()).toFloat(), cos(angle.toDouble()).toFloat())
    }

    @Pure
    private fun getVectorByClockHour(hour: Int): Vector2 {
        return getVectorForAngle((2 * Math.PI * (hour / 12f)).toFloat())
    }

    /**
     * @param size Radius around origin tile
     * @return The number of tiles in a hexagonal map including origin tile
     */
    @Pure
    fun getNumberOfTilesInHexagon(size: Int): Int {
        if (size < 0) return 0
        return 1 + 6 * size * (size + 1) / 2
    }

    /** Almost inverse of [getNumberOfTilesInHexagon] - get equivalent fractional Hexagon radius for an Area */
    @Pure
    fun getHexagonalRadiusForArea(numberOfTiles: Int) =
        if (numberOfTiles < 1) 0f else ((sqrt(12f * numberOfTiles - 3) - 3) / 6)

    // In our reference system latitude, i.e. how distant from equator we are, is proportional to x + y
    @Readonly fun getLatitude(hexCoord: HexCoord) = hexCoord.x + hexCoord.y
    
    @Readonly fun getLongitude(hexCoord: HexCoord) = hexCoord.x - hexCoord.y
    

    /**
     * Convert a latitude and longitude back into a hex coordinate.
     * Inverse function of [getLatitude] and [getLongitude].
     *
     * @param latitude As from [getLatitude].
     * @param longitude As from [getLongitude].
     * @return Hex coordinate. May need to be passed through [roundHexCoords] for further use.
     * */
    @Pure
    fun hexFromLatLong(latitude: Int, longitude: Int): Vector2 {
        val y = (latitude - longitude) / 2f
        val x = longitude + y
        return Vector2(x, y)
    }

    /**
     * Convert hex latitude and longitude into world coordinates.
     */
    @Readonly
    fun worldFromLatLong(vector: Vector2, tileRadius: Float): Vector2 {
        val x = vector.x * tileRadius * 1.5f * -1f
        val y = vector.y * tileRadius * sqrt(3f) * 0.5f
        return Vector2(x, y)
    }

    /** returns a vector containing width and height a rectangular map should have to have
     *  approximately the same number of tiles as an hexagonal map given a height/width ratio */
    @Pure
    fun getEquivalentRectangularSize(size: Int, ratio: Float = 0.65f): HexCoord {
        if (size < 0)
            return HexCoord.of(0,0)

        val nTiles = getNumberOfTilesInHexagon(size)
        val width = (sqrt(nTiles.toFloat() / ratio)).roundToInt()
        val height = (width * ratio).roundToInt()
        return HexCoord.of(width, height)
    }

    /** Returns a radius of a hexagonal map that has approximately the same number of
     *  tiles as a rectangular map of a given width/height */
    @Pure
    fun getEquivalentHexagonalRadius(width: Int, height: Int) =
        getHexagonalRadiusForArea(width * height).roundToInt()
    

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
     * @see [com.unciv.logic.map.TileMap.getUnwrappedPosition]
     */
    @Readonly
    fun getUnwrappedNearestTo(unwrapHexCoord: HexCoord, staticHexCoord: HexCoord, longitudinalRadius: Int): Vector2 {
        val referenceLong = getLongitude(staticHexCoord)
        val toWrapLat = getLatitude(unwrapHexCoord) // Working in Cartesian space is easier.
        val toWrapLong = getLongitude(unwrapHexCoord)
        return hexFromLatLong(toWrapLat, (toWrapLong - referenceLong + longitudinalRadius).mod(longitudinalRadius * 2)
                - longitudinalRadius + referenceLong)
    }

    @Readonly
    fun hex2WorldCoords(hexCoord: HexCoord): Vector2 {
        // Distance between cells = 2* normal of triangle = 2* (sqrt(3)/2) = sqrt(3)
        val xVector = getVectorByClockHour(10)
        xVector.scl(sqrt(3.0).toFloat() * hexCoord.x)
        
        val yVector = getVectorByClockHour(2)
        yVector.scl(sqrt(3.0).toFloat() * hexCoord.y)
        
        return xVector.add(yVector)
    }

    @Suppress("LocalVariableName")  // clearer
    @Readonly
    fun world2HexCoords(worldCoord: Vector2): Vector2 {
        // D: diagonal, A: antidiagonal versors
        val D = getVectorByClockHour(10)
        D.scl(sqrt(3.0).toFloat())
        val A = getVectorByClockHour(2)
        A.scl(sqrt(3.0).toFloat())
        val den = D.x * A.y - D.y * A.x
        val x = (worldCoord.x * A.y - worldCoord.y * A.x) / den
        val y = (worldCoord.y * D.x - worldCoord.x * D.y) / den
        return Vector2(x, y)
    }

    // Both x - 10 o'clock - and y - 2 o'clock - increase the row by 0.5
    @Readonly fun getRow(hexCoord: HexCoord): Int = (hexCoord.x + hexCoord.y)/2

    // y is 2 o'clock - increases column by 1, x in 10 o'clock - decreases by 1
    @Readonly fun getColumn(hexCoord: HexCoord): Int = hexCoord.y - hexCoord.x

    @Pure
    fun getTileCoordsFromColumnRow(column: Int, row: Int): HexCoord {
        // we know that column = y-x in hex coords
        // And we know that row = (y+x)/2 in hex coords
        // Therefore, 2row+column = 2y, 2row-column=2x

        // However, these row numbers only apear on alternating columns.
        // So column 0 will have rows 0,1,2, etc, and column 1 will have rows 0.5,1.5,2.5 etc.
        // you'll need to see a hexmap to see it, and then it will be obvious

        // So for even columns, the row is incremented by half
        var twoRows = row * 2
        if (abs(column) %2==1) twoRows += 1

        return HexCoord.of((twoRows-column)/2, (twoRows+column)/2)
    }

    /** Todo: find a mathematically equivalent way to round hex coords without cubic magic */
    @Readonly
    fun roundHexCoords(hexCoord: Vector2): Vector2 {

        @Readonly
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

        @Readonly
        fun hex2CubicCoords(hexCoord: Vector2): Vector3 {
            return Vector3(hexCoord.y - hexCoord.x, hexCoord.x, -hexCoord.y)
        }

        @Readonly
        fun cubic2HexCoords(cubicCoord: Vector3): Vector2 {
            return Vector2(cubicCoord.y, -cubicCoord.z)
        }

        return cubic2HexCoords(roundCubicCoords(hex2CubicCoords(hexCoord)))
    }

    @Readonly
    fun getHexCoordsAtDistance(origin: HexCoord, distance: Int, maxDistance: Int, worldWrap: Boolean): List<HexCoord> {
        val vectors = mutableListOf<HexCoord>()
        if (distance == 0) {
            return listOf(origin.cpy())
        }
        
        @Readonly
        fun getHexcoordOnOtherSideOfClock(vector: HexCoord): HexCoord = 
            origin.times(2).minus(vector)
        
        @LocalState
        var current = origin.minus(HexCoord.of(distance, distance))  // start at 6 o clock
        for (i in 0 until distance) { // From 6 to 8
            vectors += current
            vectors += getHexcoordOnOtherSideOfClock(current)
            current = current.plus(1, 0)
        }
        for (i in 0 until distance) { // 8 to 10
            vectors += current.cpy()
            if (!worldWrap || distance != maxDistance)
                vectors += getHexcoordOnOtherSideOfClock(current)
            current = current.plus(1, 1)
        }
        for (i in 0 until distance) { // 10 to 12
            vectors += current.cpy()
            if (!worldWrap || distance != maxDistance || i != 0)
                vectors += getHexcoordOnOtherSideOfClock(current)
            current = current.plus(0, 1)
        }
        return vectors
    }

    @Readonly
    fun getHexCoordsInDistance(origin: HexCoord, distance: Int, worldWrap: Boolean): List<HexCoord> {
        val hexesToReturn = mutableListOf<HexCoord>()
        for (i in 0..distance) {
            hexesToReturn += getHexCoordsAtDistance(origin, i, distance, worldWrap)
        }
        return hexesToReturn
    }



    /** Get number of hexes from [origin] to [destination] _without respecting world-wrap_ */
    @Pure
    fun getDistance(origin: Vector2, destination: Vector2): Int {
        return getDistance(origin.x.toInt(), origin.y.toInt(), destination.x.toInt(), destination.y.toInt())
    }
    @Pure
    fun getDistance(origin: HexCoord, destination: HexCoord): Int {
        return getDistance(origin.x, origin.y, destination.x, destination.y)
    }

    @Pure
    fun getDistance(originX: Int, originY: Int, destinationX: Int, destinationY: Int): Int {
        val relativeX = originX - destinationX
        val relativeY = originY - destinationY
        return if (relativeX * relativeY >= 0)
            max(abs(relativeX), abs(relativeY))
        else
            (abs(relativeX) + abs(relativeY))
    }
    
    @Immutable
    private val clockPositionToHexcoordMap: Map<Int, HexCoord> = mapOf(
        0 to HexCoord.of(1, 1), // This alias of 12 makes clock modulo logic easier
        12 to HexCoord.of(1, 1),
        2 to HexCoord.of(0, 1),
        4 to HexCoord.of(-1, 0),
        6 to HexCoord.of(-1, -1),
        8 to HexCoord.of(0, -1),
        10 to HexCoord.of(1, 0)
    )
    /** Returns the hex-space distance corresponding to [clockPosition], or a zero vector if [clockPosition] is invalid */
    @Pure
    fun getClockPositionToHexcoord(clockPosition: Int): HexCoord =
        clockPositionToHexcoordMap[clockPosition]?: HexCoord.Zero


    // Statically allocate the Vectors (in World coordinates)
    // of the 6 clock directions for border and road drawing in TileGroup
    @Immutable
    private val clockPositionToWorldVectorMap: Map<Int,Vector2> = mapOf(
        2 to hex2WorldCoords(HexCoord.of(0, -1)),
        4 to hex2WorldCoords(HexCoord.of(1, 0)),
        6 to hex2WorldCoords(HexCoord.of(1, 1)),
        8 to hex2WorldCoords(HexCoord.of(0, 1)),
        10 to hex2WorldCoords(HexCoord.of(-1, 0)),
        12 to hex2WorldCoords(HexCoord.of(-1, -1)) )

    /** Returns the world/screen-space distance corresponding to [clockPosition], or a zero vector if [clockPosition] is invalid */
    @Pure
    fun getClockPositionToWorldVector(clockPosition: Int): Vector2 =
        clockPositionToWorldVectorMap[clockPosition] ?: Vector2.Zero

    @Readonly
    fun getDistanceFromEdge(position: HexCoord, mapParameters: MapParameters): Int {
        val x = position.x
        val y = position.y
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
            if (!mapParameters.worldWrap) return radius - getDistance(position, HexCoord.Zero)

            // The non-wrapping method holds in the upper two and lower two 'triangles' of the hexagon
            // but needs special casing for left and right 'wedges', where only distance from the
            // 'choke points' counts (upper and lower hex at the 'seam' where height is smallest).
            // These are at (radius,0) and (0,-radius)
            if (x.sign == y.sign) return radius - getDistance(position, HexCoord.Zero)
            // left wedge - the 'choke points' are not wrapped relative to us
            if (x > 0) return min(getDistance(position, HexCoord.of(radius,0)), getDistance(position, HexCoord.of(0, -radius)))
            // right wedge - compensate wrap by using a hex 1 off along the edge - same result
            return min(getDistance(position, HexCoord.of(1, radius)), getDistance(position, HexCoord.of(-radius, -1)))
        }
    }

    /**
     * The goal here is to map from hexagonal positions (centered on 0,0) to positive integers (starting from 0) so we can replace hashmap/hashset with arrays/bitsets
     * Places 1-6 are ring 1, 7-18 are ring 2, etc.
     */
    @Pure
    fun getZeroBasedIndex(x: Int, y: Int): Int {
        if (x == 0 && y == 0) return 0
        val ring = getDistance(0,0, x, y)
        val ringStart = 1 + 6 * ring * (ring - 1) / 2 // 1 for the center tile, then 6 for each ring
        
        // total number of elements in the ring is 6 * ring
        
        // We divide the ring into its 6 edges, each of which can be determined by an equality comparison
        // Each edge has a start index, a variable from 0 to the number of elements in that edge
        val positionInRing = when (ring) {
            y -> 0 /* start index*/ + x /*variable*/    // contains `ring+1` elements
            x -> ring + 1 /* start index */ + y /*variable*/ // contains `ring` elements - 1 already taken by x=y=ring above
            -x -> 2 * ring + 1 /* start index */ -y /*variable*/ // contains `ring+1` elements
            -y -> 3 * ring + 2 /*start index*/ -x /*variable*/ // contains `ring` elements - 1 already taken by -x=-y=ring above
            x-y -> 4 * ring + 2 /* start index */ +x-1 /*variable*/ // contains `ring-1` elements. -1 because x=0 is already taken by ring=-y above
            y-x -> 5 * ring + 1 /* start index */ +y-1 /*variable*/ // contains `ring-1` elements. -1 because y=0 is already taken by ring=-x above
            else -> throw Exception("How???")
        }
        return ringStart + positionInRing
    }
    
    // Much simpler to understand, passes same tests, but ~5x slower than the above
    fun mapRelativePositionToPositiveIntRedblob(x: Int, y: Int): Int {
        if (x == 0 && y == 0) return 0
        val ring = getDistance(0,0, x, y)
        val ringStart = 1 + 6 * ring * (ring - 1) / 2 // 1 for the center tile, then 6 for each ring
        val vectorsInRing = getHexCoordsAtDistance(HexCoord.Zero, ring, ring, false)
        val positionInRing = vectorsInRing.indexOf(HexCoord.of(x, y))
        return ringStart + positionInRing
    }

    fun tilesAndNeighborUniqueIndex(tile: Tile, neighbor: Tile): Int {
        return tile.zeroBasedIndex * 6 +  // each tile has 6 neighbors 
                tile.tileMap.getNeighborTileClockPosition(tile, neighbor) / 2 - 1 // min: 2, max: 12, step 2; Divide by 2 and it's numbers 1-6, -1 to get 0-5
    }

}

//interface HexCoord{
//    val x: Int
//    val y: Int
//    
//}

/** Required for ser/deser since the stupid json parser can't handle the inline ints -_-  */
data class HexCoord(val x:Int=0, val y:Int=0){

    @Pure fun plus(hexCoord: HexCoord): HexCoord = HexCoord.of(x + hexCoord.x, y + hexCoord.y)
    @Pure fun plus(plusX: Int, plusY: Int): HexCoord = HexCoord.of(x + plusX, y + plusY)
    @Pure fun minus(hexCoord: HexCoord): HexCoord = HexCoord.of(x - hexCoord.x, y - hexCoord.y)
    @Pure fun times(int: Int): HexCoord = HexCoord.of(x * int, y * int)

    @Pure fun toVector2(): Vector2 = Vector2(x.toFloat(), y.toFloat())

    @Pure fun eq(x:Int,y:Int): Boolean = this.x == x && this.y == y

    // Conversion helpers for 1:1 Vector2 compatibility
    @Pure fun cpy() = this
    @Pure fun toHexCoord() = this
    @Pure fun asSerializable() = HexCoord(x,y)

    fun toPrettyString(): String = "($x,$y)"

    companion object {
        val Zero = HexCoord.of(0,0)
        @Pure
        fun of(x: Int, y: Int): HexCoord = HexCoord(x,y)
    }
    /** Ser/deser to be 1:1 with Vector2, to allow us to replace Vector2 in game saves with HexCoord */
    class Serializer : Json.Serializer<HexCoord> {
        override fun write(json: Json, coord: HexCoord, knownType: Class<*>?) {
            json.writeValue(mapOf("x" to coord.x.toFloat(), "y" to coord.y.toFloat()))
        }

        override fun read(json: Json, jsonData: JsonValue, type: Class<*>?): HexCoord {
            val x = json.readValue(Float::class.java, jsonData["x"]) ?: 0f
            val y = json.readValue(Float::class.java, jsonData["y"]) ?: 0f
            return HexCoord(x.toInt(), y.toInt())
        }
    }
}

@JvmInline
/** When we need to create a lot of coords we prefer to use this implementation - since it's inline it's passed around as as int
    and doesn't require memory allocation or memory access */
value class InlineHexCoord(val coords: Int = 0) {
    // x value is stored in the 16 bits, and y value in the bottom 16 bits, allowing range -32768 to 32767

    val x: Int
        get() = coords shr 16

    // Extract bottom 16 bits and sign-extend - required to keep negative numbers
    val y: Int
        get() = (coords shl 16) shr 16
    
    override fun toString(): String = "HexCoord(x=${x}, y=${y})"

    companion object {
        @Pure
        fun of(x: Int, y: Int): InlineHexCoord {
            require(x in -32768..32767) { "X value must be in range -32768..32767" }
            require(y in -32768..32767) { "Y value must be in range -32768..32767" }
            return InlineHexCoord((x shl 16) or (y and 0xFFFF))
        }
    }
}

@Pure fun Vector2.toHexCoord() = HexCoord.of(this.x.toInt(), this.y.toInt())
@Pure fun Vector2.toVector2() = this // compatibility
@Pure fun Vector2.asSerializable() = HexCoord(x.toInt(),y.toInt())
