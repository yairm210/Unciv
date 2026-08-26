package com.unciv.view

import com.unciv.logic.GameInfo
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.Readonly

/** View of a [GameInfo] from the perspective of [viewer]. */
class GameView(gameInfo: GameInfo, viewer: Civilization, spectatorMode: Boolean = false) : GameBasedView<GameInfo>(gameInfo, viewer, spectatorMode) {
    val civView: CivView = CivView(viewer, viewer, spectatorMode, this)
    val tileMapView: TileMapView = TileMapView(gameInfo.tileMap, viewer, spectatorMode, this)

    // Navigation
    // These can be cached in the future if we see a need, for now - simplicity
    @Readonly fun getCivView(civ: Civilization): CivView = CivView(civ, viewer, spectatorMode, this)
    @Readonly fun getCityView(city: City): CityView = CityView(city, viewer, spectatorMode, this)
    @Readonly fun getForeignCityView(city: City): ForeignCityView = ForeignCityView(city, viewer, spectatorMode, this)
    @Readonly fun getForeignMapUnitView(unit: MapUnit): ForeignMapUnitView = ForeignMapUnitView(unit, viewer, spectatorMode, this)
    @Readonly fun getForeignCivView(civ: Civilization): ForeignCivView = ForeignCivView(civ, viewer, spectatorMode)

    // Data retrieval
    @Readonly fun getTile(tile: Tile): TileView = tileMapView.getTile(tile)
}
