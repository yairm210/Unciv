package com.unciv.logic.map.tile

/** What generic map algorithms (e.g. [com.unciv.logic.map.BFS]) need to know about a tile - implemented by both [Tile] and its view. */
interface TileInterface<T : TileInterface<T>> {
    val neighbors: Sequence<T>
}
