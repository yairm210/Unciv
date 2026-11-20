package com.unciv.view

import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.Readonly

/** Road pathing maps by the [TileView] they start from, for [TileView.getRoadPathTo] and [TileView.getRoadPathToAny].
 *  Meant to live no longer than one round of automation, so that it cannot go stale. */
class RoadPathingCache {
    @Cache private val pathingMaps = HashMap<TileView, PathingMapView>()

    @Readonly @Suppress("purity")
    internal fun getOrPut(start: TileView, create: () -> PathingMapView): PathingMapView = pathingMaps.getOrPut(start, create)
}
