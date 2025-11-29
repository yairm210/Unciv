package com.unciv.logic.map.mapunit.movement

import com.badlogic.gdx.utils.IntIntMap
import com.unciv.logic.map.FixedPointMovement
import com.unciv.logic.map.FixedPointMovement.Companion.fpmFromFixedPointBits
import com.unciv.logic.map.FixedPointMovement.Companion.fpmFromMovement
import com.unciv.logic.map.TileMap
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import com.unciv.utils.ImmutableIntIntArrayMap
import com.unciv.utils.ImmutableIntIntArrayMap.Companion.AbstractBuilder
import com.unciv.utils.ImmutableIntIntArrayMap.Companion.Entry
import com.unciv.utils.Log
import yairm210.purity.annotations.LocalState
import yairm210.purity.annotations.Pure
import yairm210.purity.annotations.Readonly

// map of tileZeroBasedIndex to ParentTileAndTotalMovement
interface PathsToTilesWithinTurn {
    @Readonly fun getPathToTile(tile: Tile): List<Tile>

    @Readonly fun isEmpty(): Boolean
    @Readonly fun isNotEmpty(): Boolean
    val size: Int
    @Readonly operator fun contains(tile: Tile): Boolean
    @Readonly fun containsKey(tile: Tile): Boolean
    @Readonly fun getValue(tile: Tile): ParentTileAndTotalMovement
    // no operator fun get(tile:Tile) because of a compiler bug that causes JVM to crash when PathsToTilesWithinTurnMap is loaded

    @Readonly fun forEachTile(op: (Tile, ParentTileAndTotalMovement)->Unit)
    @Readonly fun filter(predicate: (Tile, ParentTileAndTotalMovement) -> Boolean): PathsToTilesWithinTurn
    @Readonly fun asTileSequence(): Sequence<Tile>
    @Readonly fun tilesSortedBy(selector: (Tile, ParentTileAndTotalMovement) -> Float): Sequence<Tile>
    @Readonly fun tilesSortedByDescending(selector: (Tile, ParentTileAndTotalMovement) -> Float) = tilesSortedBy {tile,path -> -selector(tile,path) }

    @Readonly fun any(): Boolean
    @Readonly fun anyTile(predicate: (Tile, ParentTileAndTotalMovement)->Boolean): Boolean
    @Readonly fun <R>firstNotNullTileOfOrNull(mapping: (Tile, ParentTileAndTotalMovement) -> R?): R?
    @Readonly fun <R> firstNotNullTileOf(mapping: (Tile, ParentTileAndTotalMovement) -> R?) = firstNotNullTileOfOrNull(mapping)!!
    @Readonly fun minTileByOrNull(selector: (Tile, ParentTileAndTotalMovement) -> Float): Tile?
    @Readonly fun minTileBy(selector: (Tile, ParentTileAndTotalMovement) -> Float) = minTileByOrNull(selector)!!
    @Readonly fun maxTileByOrNull(selector: (Tile, ParentTileAndTotalMovement) -> Float) = minTileByOrNull {tile,path -> -selector(tile,path) }
    @Readonly fun maxTileBy(selector: (Tile, ParentTileAndTotalMovement) -> Float) = maxTileByOrNull(selector)!!
    fun randomTile(): Tile
    
    interface Builder {
        fun reserve(minCapacity: Int): Builder
        operator fun set(tile: Tile, value: ParentTileAndTotalMovement): Builder
        fun build(): PathsToTilesWithinTurn
    }
    
    companion object {
        @Readonly fun of(tile: Tile, value: ParentTileAndTotalMovement) = PathsToTilesWithinTurnArrayMap.of(tile, value)
        @Pure fun newBuilder(tileMap: TileMap, initialCapacity: Int = 32): Builder = PathsToTilesWithinTurnArrayMap.Builder(tileMap)
    }
}

class PathsToTilesWithinTurnHashMap(initialCapacity: Int = 16): LinkedHashMap<Tile, ParentTileAndTotalMovement>(initialCapacity), PathsToTilesWithinTurn {
    override fun getPathToTile(tile: Tile): List<Tile> {
        if (!containsKey(tile)) {
            Log.debug("PathsToTilesWithinTurn#getPathToTile does not contain $tile: $this")
            throw Exception("Can't reach $tile")
        }
        val tileMap = tile.tileMap
        val reversePathList = ArrayList<Tile>()
        var currentTile = tile
        while (get(currentTile)!!.parentTileIdx != currentTile.zeroBasedIndex) {
            reversePathList.add(currentTile)
            currentTile = get(currentTile)!!.parentTile(tileMap)
        }
        return reversePathList.reversed()
    }
    override fun any(): Boolean = isNotEmpty()
    override fun isNotEmpty(): Boolean = size > 0
    override fun contains(tile: Tile): Boolean
        = (this as LinkedHashMap<Tile, ParentTileAndTotalMovement>).contains(tile)

    override fun getValue(tile: Tile): ParentTileAndTotalMovement = super.get(tile)!!

    override fun forEachTile(op: (Tile, ParentTileAndTotalMovement)->Unit): Unit = super.forEach {k,v-> op(k, v) }

    override fun filter(predicate: (Tile, ParentTileAndTotalMovement) -> Boolean): PathsToTilesWithinTurn {
        @LocalState
        val r = PathsToTilesWithinTurnHashMap()
        r.putAll((this as LinkedHashMap<Tile, ParentTileAndTotalMovement>).filter{predicate(it.key, it.value)})
        return r
    }

    override fun asTileSequence(): Sequence<Tile> = keys.asSequence()

    override fun tilesSortedBy(selector: (Tile, ParentTileAndTotalMovement) -> Float): Sequence<Tile>
        = (this as LinkedHashMap<Tile, ParentTileAndTotalMovement>).asSequence().sortedBy{selector(it.key, it.value)}.map { it.key }

    override fun anyTile(predicate: (Tile, ParentTileAndTotalMovement) -> Boolean): Boolean
        = (this as LinkedHashMap<Tile, ParentTileAndTotalMovement>).any {predicate(it.key, it.value)}

    override fun <R>firstNotNullTileOfOrNull(mapping: (Tile, ParentTileAndTotalMovement) -> R?): R?
        = (this as LinkedHashMap<Tile, ParentTileAndTotalMovement>).firstNotNullOfOrNull {mapping(it.key, it.value)}

    override fun minTileByOrNull(selector: (Tile, ParentTileAndTotalMovement) -> Float): Tile?
        = (this as LinkedHashMap<Tile, ParentTileAndTotalMovement>).minByOrNull{selector(it.key, it.value)}?.key

    override fun randomTile(): Tile
        = (this as LinkedHashMap<Tile, ParentTileAndTotalMovement>).keys.random()

    class Builder(initialCapacity: Int = 16) : PathsToTilesWithinTurn.Builder {
        private val map = PathsToTilesWithinTurnHashMap(initialCapacity + initialCapacity/4)
        override fun reserve(minCapacity: Int): PathsToTilesWithinTurn.Builder = this
        override fun set(tile: Tile, value: ParentTileAndTotalMovement): Builder {
            map[tile] = value
            return this
        }

        override fun build(): PathsToTilesWithinTurn = map
    }
    
    companion object {
        @Pure
        fun of(tile: Tile, value: ParentTileAndTotalMovement): PathsToTilesWithinTurnHashMap {
            @LocalState
            val stayOnTileSingleton = PathsToTilesWithinTurnHashMap()
            stayOnTileSingleton[tile] = value
            return stayOnTileSingleton
        }
    }
}

class PathsToTilesWithinTurnArrayMap(
    map: LongArray, size: Int, val tileMap: TileMap, private val unit: MapUnit?=null)
    : ImmutableIntIntArrayMap(map, size), PathsToTilesWithinTurn
{
    @Readonly private fun Entry.getEntryTile() = tileMap.tileList[key]
    @Readonly private fun Entry.getEntryPath() = ParentTileAndTotalMovement(value)


    override fun getPathToTile(tile: Tile): List<Tile> {
        if (!containsKey(tile)) {
            Log.debug("PathsToTilesWithinTurn#getPathToTile does not contain $tile: $this")
            throw Exception("$unit Can't reach $tile")
        }
        val reversePathList = ArrayList<Tile>()
        val tileMap = tile.tileMap
        var currentTile = tile
        while (getValue(currentTile).parentTileIdx != currentTile.zeroBasedIndex) {
            reversePathList.add(currentTile)
            currentTile = getValue(currentTile).parentTile(tileMap)
        }
        return reversePathList.reversed()
    }

    override fun contains(tile: Tile): Boolean
        = (this as ImmutableIntIntArrayMap).containsKey(tile.zeroBasedIndex)

    override fun containsKey(tile: Tile): Boolean
        = (this as ImmutableIntIntArrayMap).containsKey(tile.zeroBasedIndex)

    override fun getValue(tile: Tile): ParentTileAndTotalMovement
        = ParentTileAndTotalMovement((this as ImmutableIntIntArrayMap).getValue(tile.zeroBasedIndex))

    @Suppress("OVERRIDE_BY_INLINE")
    override inline fun forEachTile(op: (Tile, ParentTileAndTotalMovement) -> Unit) 
        = forEach { e,_ -> op(tileMap.tileList[e.key], ParentTileAndTotalMovement(e.value)) }

    override fun filter(predicate: (Tile, ParentTileAndTotalMovement) -> Boolean): PathsToTilesWithinTurn {
        @LocalState
        val builder = Builder(tileMap, unit, size)
        builder.filter(this) { e, _ -> predicate(e.getEntryTile(), e.getEntryPath()) }
        return builder.build()
    }
    
    override fun asTileSequence(): Sequence<Tile>
        = sequence { forEach {e,_ -> yield(e.getEntryTile()) } }

    override fun tilesSortedBy(selector: (Tile, ParentTileAndTotalMovement) -> Float): Sequence<Tile> {
        @LocalState
        val copy = LongArray(size)
        var i=0
        forEachTile { tile, movement ->
            copy[i] = (tile.zeroBasedIndex.toLong() shl 32) or movement.bits.toLong()
            i++
        }

        return copy
            .sortedBy { selector(Entry(it).getEntryTile(), Entry(it).getEntryPath()) }
            .map { Entry(it).getEntryTile() }
            .asSequence()
    }

    override fun any(): Boolean = (this as ImmutableIntIntArrayMap).any()

    override fun anyTile(predicate: (Tile, ParentTileAndTotalMovement) -> Boolean): Boolean 
        =  any { e,_ -> predicate(tileMap.tileList[e.key], ParentTileAndTotalMovement(e.value)) }

    override fun <R> firstNotNullTileOfOrNull(mapping: (Tile, ParentTileAndTotalMovement) -> R?): R? 
        = firstNotNullOfOrNull {e,_ -> mapping(tileMap.tileList[e.key], ParentTileAndTotalMovement(e.value)) }

    override fun minTileByOrNull(selector: (Tile, ParentTileAndTotalMovement) -> Float): Tile?
        = if (size == 0) null
        else tileMap.tileList[minByDouble { e,_ -> selector(tileMap.tileList[e.key], ParentTileAndTotalMovement(e.value)).toDouble() }.key]

    override fun randomTile(): Tile = atIndex((Math.random() * size).toInt()).getEntryTile()

    class Builder(val tileMap: TileMap, val unit: MapUnit?=null, initialCapacity: Int = 16)
        : AbstractBuilder<Builder, PathsToTilesWithinTurnArrayMap>(initialCapacity), PathsToTilesWithinTurn.Builder {
        override val self get() = this
        override fun build(map: LongArray, size: Int) = PathsToTilesWithinTurnArrayMap(map, size, tileMap, unit)
        override fun set(tile: Tile, value: ParentTileAndTotalMovement): PathsToTilesWithinTurn.Builder {
            require(tile.tileMap === tileMap)
            return set(tile.zeroBasedIndex, value.bits)
        }
    }
    
    companion object {
        @Readonly
        fun of(tile: Tile, value: ParentTileAndTotalMovement, unit: MapUnit?=null)
            = PathsToTilesWithinTurnArrayMap(longArrayOf(Entry(tile.zeroBasedIndex, value.bits).bits), 1, tile.tileMap, unit)
    }
}


@JvmInline
value class ParentTileAndTotalMovement(val bits: Int) {
    constructor(parentTileIdx: Int, totalMovement: FixedPointMovement): this(
        (parentTileIdx shl 14) or totalMovement.bits) {
        require(totalMovement <= MAX_MOVEMENT)
    }
    constructor(parentTile: Tile, totalMovement: FixedPointMovement): this(parentTile.zeroBasedIndex, totalMovement)
    constructor(parentTile: Tile, totalMovement: Float): this(parentTile.zeroBasedIndex,
        fpmFromMovement(totalMovement)
    )
    
    init {
        require(parentTileIdx >= 0)
    }
    
    val parentTileIdx get() = (bits shr 14)
    val totalMovementBits get() = fpmFromFixedPointBits(bits and 0x3FFF)
    val totalMovement get() = totalMovementBits.toFloat()
    
    @Readonly
    fun parentTile(tileMap: TileMap) = tileMap.tileList[parentTileIdx]
    
    override fun toString() = "${javaClass.simpleName}[parentTileIdx=$parentTileIdx totalMovement=$totalMovement]"
    
    companion object {
        val MAX_MOVEMENT = fpmFromFixedPointBits(0x3FFF)
    }
}
