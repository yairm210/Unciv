package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.BFS
import yairm210.purity.annotations.ModifiesInternalStateOnly
import yairm210.purity.annotations.Readonly

/** View of a [BFS] over [TileView]s - create via [TileMapView.createBFS]. */
@ModifiesInternalStateOnly
class BFSView internal constructor(
    private val bfs: BFS,
    private val tileMapView: TileMapView,
    viewer: Civilization?,
) : View<BFS>(bfs, viewer) {
    /** @return The tile that was checked, or `null` if there was nothing to do */
    @Suppress("purity")
    fun nextStep(): TileView? = bfs.nextStep()?.let { tileMapView.getTile(it) }

    /** @return a Sequence from [destination] back to the starting point, including both, or empty if [destination] has not been reached */
    @Readonly fun getPathTo(destination: TileView): Sequence<TileView> =
        bfs.getPathTo(destination.unwrap()).map { tileMapView.getTile(it) }
}
