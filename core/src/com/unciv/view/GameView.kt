package com.unciv.view

import com.unciv.logic.GameInfo
import com.unciv.logic.battle.AttackParticipant
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

    /**
     * The endpoints this civilization observed when an attack happened, regardless of current visibility.
     * A selected unit matches a known endpoint or a participant identified when the attack happened.
     */
    @Readonly fun getObservedAttacks(selectedUnit: MapUnitView? = null): Sequence<ObservedAttack> {
        val selectedPosition = selectedUnit?.getTile()?.position()
        val selectedUnitId = selectedUnit?.unit?.id
        return wrapped.attackEvents.asSequence().mapNotNull { attack ->
            val source = attack.source.takeIf { viewer.isSpectator() || viewer.civID in attack.knowsSource }
            val target = attack.target.takeIf { viewer.isSpectator() || viewer.civID in attack.knowsTarget }
            if (source == null && target == null) return@mapNotNull null
            if (selectedUnitId != null && selectedPosition != source && selectedPosition != target
                && !isKnownParticipant(attack.attacker, selectedUnitId)
                && attack.targets.none { isKnownParticipant(it, selectedUnitId) }) return@mapNotNull null
            ObservedAttack(attack.turn, source, target)
        }
    }

    @Readonly private fun isKnownParticipant(participant: AttackParticipant?, unitId: Int): Boolean =
        participant != null && participant.unitId == unitId
            && (viewer.isSpectator() || viewer.civID in participant.knownBy)
}
