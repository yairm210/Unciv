package com.unciv.view

import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.pathingmap.PathingMap
import yairm210.purity.annotations.Readonly

/** View of a [PathingMap] over [TileView]s - held in a [RoadPathingCache]. */
class PathingMapView internal constructor(
    private val pathingMap: PathingMap,
    private val tileMapView: TileMapView,
    viewer: Civilization?,
) : View<PathingMap>(pathingMap, viewer) {
    @Readonly @Suppress("purity")
    fun getShortestPath(destination: TileView, maxTurns: Int = PathingMap.MAX_VALID_TURNS): List<TileView>? =
        pathingMap.getShortestPath(destination.unwrap(), maxTurns)?.map { tileMapView.getTile(it) }

    /** @return The first tile in reach (within [maxTurns]) that fulfills [predicate], or `null` if there is none */
    @Readonly @Suppress("purity")
    fun bfsUntilMatchingTile(maxTurns: Int, predicate: (TileView) -> Boolean): TileView? =
        pathingMap.bfsUntilMatchingTile(maxTurns) { tile, _ -> predicate(tileMapView.getTile(tile)) }
            ?.let { tileMapView.getTile(it) }
}
