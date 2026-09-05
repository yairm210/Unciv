package com.unciv.view

import com.unciv.logic.GameInfo
import com.unciv.logic.city.City
import com.unciv.logic.civilization.Civilization
import com.unciv.logic.map.MapVisualization
import com.unciv.logic.map.mapunit.MapUnit
import com.unciv.logic.map.tile.Tile
import yairm210.purity.annotations.Readonly

/** View of a [GameInfo] from the perspective of [viewer]. */
class GameView(gameInfo: GameInfo, override val viewer: Civilization, spectatorMode: Boolean = false) : View<GameInfo>(gameInfo, viewer, spectatorMode) {
    val civView: CivView = CivView(viewer, viewer, spectatorMode, this)
    val tileMapView: TileMapView = TileMapView(gameInfo.tileMap, viewer, spectatorMode, this)
    val attackEventsView: AttackEventsView = gameInfo.createAttackEventsView(viewer, spectatorMode)
    private val mapVisualization = MapVisualization(gameInfo, viewer)

    // Navigation
    // These can be cached in the future if we see a need, for now - simplicity
    @Readonly fun getCivView(civ: Civilization): CivView = CivView(civ, viewer, spectatorMode, this)
    @Readonly fun getCityView(city: City): CityView = CityView(city, viewer, spectatorMode, this)
    @Readonly fun getForeignCityView(city: City): ForeignCityView = ForeignCityView(city, viewer, spectatorMode, this)
    @Readonly fun getForeignMapUnitView(unit: MapUnit): ForeignMapUnitView = ForeignMapUnitView(unit, viewer, spectatorMode, this)
    @Readonly fun getMapUnitView(unit: MapUnit): MapUnitView = MapUnitView(unit, viewer, spectatorMode, this)
    @Readonly fun getForeignCivView(civ: Civilization): ForeignCivView = ForeignCivView(civ, viewer, spectatorMode, this)

    // Data retrieval
    @Readonly fun getTile(tile: Tile): TileView = tileMapView.getTile(tile)
    /** Resolves the same tile from this game's viewing perspective, e.g. after a spectator toggles fog of war. */
    @Readonly fun getTile(tileView: TileView): TileView = tileMapView.getTile(tileView.unwrap())

    /** Units whose past movements may be displayed from this view's perspective. */
    @Readonly fun getUnitsWithVisibleMovementHistory(): Sequence<ForeignMapUnitView> =
        wrapped.civilizations.asSequence()
            .flatMap { it.units.getCivUnits() }
            .filter(mapVisualization::isUnitPastVisible)
            .map { getForeignMapUnitView(it) }
}
