package com.unciv.view

import com.unciv.logic.GameInfo
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.tile.Tile

/** View of a [GameInfo] from the perspective of [viewer]. */
class GameView(gameInfo: GameInfo, internal val viewer: Civilization, val spectatorMode: Boolean = false) {
    val civView: CivView = CivView(viewer, viewer, spectatorMode, this)
    val tileMapView: TileMapView = TileMapView(gameInfo.tileMap, viewer, spectatorMode, this)

    fun getTile(tile: Tile): TileView = tileMapView.getTile(tile)
    fun getCityView(city: City): CityView = civView.getCity(city)
    fun getForeignCityView(city: City): ForeignCityView = ForeignCityView(city, viewer, spectatorMode, civView)
}
