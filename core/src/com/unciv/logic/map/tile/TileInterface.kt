package com.unciv.logic.map.tile

import yairm210.purity.annotations.Readonly

/** What generic map algorithms (e.g. [com.unciv.logic.map.BFS]) need to know about a tile - implemented by both [Tile] and its view. */
interface TileInterface<T : TileInterface<T>> {
    @Readonly fun getNeighbors(): Sequence<T>
}
