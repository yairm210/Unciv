package com.unciv.view

import yairm210.purity.annotations.Cache
import yairm210.purity.annotations.Readonly

/** Road pathing maps by the city-center [TileView] they start from, for [TileView.getRoadPath] and [TileView.getRoadPathToAny].
 *  Meant to live no longer than one round of automation, so that it cannot go stale. */
class RoadPathingCache {
    @Cache private val pathingMaps = HashMap<TileView, PathingMapView>()

    @Readonly @Suppress("purity")
    internal fun getOrPut(cityCenter: TileView, create: () -> PathingMapView): PathingMapView = pathingMaps.getOrPut(cityCenter, create)
}
