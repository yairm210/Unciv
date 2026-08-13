package com.unciv.view

import com.unciv.logic.GameInfo
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.Readonly

/** View of a [GameInfo] from the perspective of [viewer]. */
class GameView(gameInfo: GameInfo, internal val viewer: Civilization, val spectatorMode: Boolean = false) {
    val civView: CivView = CivView(viewer, viewer, spectatorMode, this)
    val tileMapView: TileMapView = TileMapView(gameInfo.tileMap, viewer, spectatorMode, this)
    
    // Navigation
    // These can be cached in the future if we see a need, for now - simplicity
    @Readonly fun getCivView(civ: Civilization): CivView = CivView(civ, viewer, spectatorMode, this)
    /** Unchecked factory — throws if the viewer cannot see this city's internals. Prefer [tryGetCityView]. */
    @Readonly fun getCityView(city: City): CityView = CityView(city, viewer, spectatorMode, this)
    @Readonly fun tryGetCityView(city: City): CityView? = getForeignCityView(city).tryGetCityView()
    @Readonly fun getForeignCityView(city: City): ForeignCityView = ForeignCityView(city, viewer, spectatorMode, this)

    // Data retrieval
    @Readonly fun getTile(tile: Tile): TileView = tileMapView.getTile(tile)
}
